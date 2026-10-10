package vn.edu.toeic.client.monitoring.harness;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** No OS name spoofing. Child clock is used only for its own hold, never exported to the parent. */
public final class ControlledProcessChild {
    private ControlledProcessChild(){ }
    public static void main(String[] args) throws Exception {
        if(args.length!=3||!args[0].matches("[a-zA-Z0-9-]{1,64}")||!args[1].matches("trial-[0-9]{3}"))throw new IllegalArgumentException();
        int duration=Integer.parseInt(args[2]);if(duration!=200&&duration!=800&&duration!=3000)throw new IllegalArgumentException();
        System.out.println("READY "+args[0]+" "+args[1]+" "+ProcessHandle.current().pid()+" "+duration);System.out.flush();
        var bytes=new java.io.ByteArrayOutputStream();int value;
        while((value=System.in.read())!=-1&&value!='\n'){if(bytes.size()>=128)throw new IllegalArgumentException();bytes.write(value);}
        if(!bytes.toString(StandardCharsets.US_ASCII).equals("GO "+args[0]+" "+args[1]))throw new IllegalArgumentException();
        long started=System.nanoTime(),hold=TimeUnit.MILLISECONDS.toNanos(duration);
        while(System.nanoTime()-started<hold) {
            long remaining=hold-(System.nanoTime()-started);if(remaining>0)TimeUnit.NANOSECONDS.sleep(remaining);
        }
    }
}
