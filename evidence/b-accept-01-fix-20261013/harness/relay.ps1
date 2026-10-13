param([int]$ListenPort, [int]$TargetPort, [string]$ControlDir)
# Loopback TCP relay for the acceptance run. It forwards bytes unchanged to the real server and
# generates no response of its own. When the file drop-ws.request appears it closes only the
# sockets whose first request line was the WebSocket handshake, so HTTP keeps working.
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
public class AcceptRelay : IDisposable {
    private readonly TcpListener listener;
    private readonly int target;
    private readonly string control;
    private readonly ConcurrentDictionary<int, TcpClient[]> webSockets = new ConcurrentDictionary<int, TcpClient[]>();
    private Timer timer;
    private int sequence;
    private volatile bool stopped;
    public AcceptRelay(int listenPort, int targetPort, string controlDir) {
        listener = new TcpListener(IPAddress.Loopback, listenPort); target = targetPort; control = controlDir;
    }
    public void Start() {
        listener.Start();
        Console.WriteLine(DateTime.UtcNow.ToString("o") + " relay started; forwards bytes unchanged, generates no response");
        timer = new Timer(delegate {
            string drop = Path.Combine(control, "drop-ws.request");
            if (File.Exists(drop)) {
                File.Delete(drop); int count = 0;
                foreach (var pair in webSockets.Values) { pair[0].Close(); pair[1].Close(); count++; }
                Console.WriteLine(DateTime.UtcNow.ToString("o") + " closed WebSocket sockets on request; count=" + count);
            }
        }, null, 100, 100);
        Task.Run(async delegate {
            while (!stopped) { try { var c = await listener.AcceptTcpClientAsync(); Task.Run(() => Relay(c)); } catch { if (!stopped) throw; } }
        });
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
                Console.WriteLine(DateTime.UtcNow.ToString("o") + " WebSocket opened through relay id=" + id);
            }
            await output.WriteAsync(buffer, 0, used);
            await Task.WhenAny(input.CopyToAsync(output), output.CopyToAsync(input));
        } catch (Exception e) { Console.WriteLine(DateTime.UtcNow.ToString("o") + " relay transport outcome=" + e.GetType().Name); }
        finally { TcpClient[] unused; webSockets.TryRemove(id, out unused); client.Close(); upstream.Close(); }
    }
    public void Dispose() { stopped = true; listener.Stop(); timer.Dispose(); foreach (var pair in webSockets.Values) { pair[0].Close(); pair[1].Close(); } }
}
'@
$relay = [AcceptRelay]::new($ListenPort, $TargetPort, $ControlDir)
try { $relay.Start(); while (-not (Test-Path (Join-Path $ControlDir 'stop-relay.request'))) { Start-Sleep -Milliseconds 200 } }
finally { $relay.Dispose() }
