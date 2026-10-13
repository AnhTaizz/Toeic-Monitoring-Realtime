param([int]$ListenPort, [int]$TargetPort, [string]$ControlDir)
# Loopback TCP relay for the acceptance run. It forwards bytes unchanged to the real server and
# generates no response of its own.
#  - drop-ws.request: closes only the sockets whose first request line was the WebSocket handshake.
#  - drop-submit-response.request: arms a one-shot fault. The next "POST .../submit" request is still
#    forwarded to the server in full; the server's response to it is read and discarded and the
#    client socket is closed, so the client sees a lost response for a request the server handled.
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
#pragma warning disable 4014
using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
public class AcceptRelay3 : IDisposable {
    private readonly TcpListener listener;
    private readonly int target;
    private readonly string control;
    private readonly ConcurrentDictionary<int, TcpClient[]> webSockets = new ConcurrentDictionary<int, TcpClient[]>();
    private Timer timer;
    private int sequence;
    private int submitArmed;
    private volatile bool stopped;
    public AcceptRelay3(int listenPort, int targetPort, string controlDir) {
        listener = new TcpListener(IPAddress.Loopback, listenPort); target = targetPort; control = controlDir;
    }
    private static void Log(string text) { Console.WriteLine(DateTime.UtcNow.ToString("o") + " " + text); Console.Out.Flush(); }
    public void Start() {
        listener.Start();
        Log("relay started; forwards bytes unchanged, generates no response");
        timer = new Timer(delegate {
            string drop = Path.Combine(control, "drop-ws.request");
            if (File.Exists(drop)) {
                File.Delete(drop); int count = 0;
                foreach (var pair in webSockets.Values) { pair[0].Close(); pair[1].Close(); count++; }
                Log("closed WebSocket sockets on request; count=" + count);
            }
            string arm = Path.Combine(control, "drop-submit-response.request");
            if (File.Exists(arm)) { File.Delete(arm); Interlocked.Exchange(ref submitArmed, 1); Log("armed: discard the response of the next POST .../submit"); }
        }, null, 100, 100);
        Task.Run(async delegate {
            while (!stopped) { try { var c = await listener.AcceptTcpClientAsync(); Task.Run(() => Relay(c)); } catch { if (!stopped) throw; } }
        });
    }
    private static bool IsSubmit(byte[] buffer, int count) {
        string head = Encoding.ASCII.GetString(buffer, 0, Math.Min(count, 512));
        int end = head.IndexOf("\r\n", StringComparison.Ordinal);
        string line = end < 0 ? head : head.Substring(0, end);
        return line.StartsWith("POST /api/v1/attempts/", StringComparison.Ordinal) && line.Contains("/submit ");
    }
    private async Task Relay(TcpClient client) {
        var upstream = new TcpClient(); int id = Interlocked.Increment(ref sequence);
        try {
            await upstream.ConnectAsync(IPAddress.Loopback, target);
            var input = client.GetStream(); var output = upstream.GetStream();
            byte[] buffer = new byte[65536]; int used = 0;
            while (used < buffer.Length) {
                int n = await input.ReadAsync(buffer, used, buffer.Length - used); if (n == 0) return; used += n;
                if (Encoding.ASCII.GetString(buffer, 0, used).Contains("\r\n\r\n")) break;
            }
            if (Encoding.ASCII.GetString(buffer, 0, used).StartsWith("GET /ws/")) {
                webSockets[id] = new[] { client, upstream };
                Log("WebSocket opened through relay id=" + id);
                await output.WriteAsync(buffer, 0, used);
                await Task.WhenAny(input.CopyToAsync(output), output.CopyToAsync(input));
                return;
            }
            int swallow = 0;
            Func<byte[], int, Task> forward = async delegate (byte[] data, int count) {
                if (IsSubmit(data, count) && Interlocked.CompareExchange(ref submitArmed, 0, 1) == 1) {
                    Volatile.Write(ref swallow, 1);
                    Log("submit request forwarded to server; its response will be discarded, connection id=" + id);
                }
                await output.WriteAsync(data, 0, count);
            };
            await forward(buffer, used);
            Task up = Task.Run(async delegate {
                byte[] chunk = new byte[65536];
                while (true) { int n = await input.ReadAsync(chunk, 0, chunk.Length); if (n == 0) break; await forward(chunk, n); }
            });
            Task down = Task.Run(async delegate {
                byte[] chunk = new byte[65536];
                while (true) {
                    int n = await output.ReadAsync(chunk, 0, chunk.Length); if (n == 0) break;
                    if (Volatile.Read(ref swallow) == 1) {
                        string status = Encoding.ASCII.GetString(chunk, 0, Math.Min(n, 15));
                        Log("server response discarded, not delivered to client; first bytes='" + status.Trim() + "' length=" + n + "; closing client socket id=" + id);
                        client.Close(); break;
                    }
                    await input.WriteAsync(chunk, 0, n);
                }
            });
            await Task.WhenAny(up, down);
        } catch (Exception e) { Log("relay transport outcome=" + e.GetType().Name); }
        finally { TcpClient[] unused; webSockets.TryRemove(id, out unused); client.Close(); upstream.Close(); }
    }
    public void Dispose() { stopped = true; listener.Stop(); timer.Dispose(); foreach (var pair in webSockets.Values) { pair[0].Close(); pair[1].Close(); } }
}
'@
$relay = [AcceptRelay3]::new($ListenPort, $TargetPort, $ControlDir)
try { $relay.Start(); while (-not (Test-Path (Join-Path $ControlDir 'stop-relay.request'))) { Start-Sleep -Milliseconds 200 } }
finally { $relay.Dispose() }
