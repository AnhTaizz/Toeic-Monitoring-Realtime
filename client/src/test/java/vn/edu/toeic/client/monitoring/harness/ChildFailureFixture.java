package vn.edu.toeic.client.monitoring.harness;

/** REAL child failures invoked only from tests, not CLI configuration or the production JAR. */
public final class ChildFailureFixture {
    public static void main(String[] args)throws Exception {
        String mode=args[0];if(mode.equals("FAIL")){System.exit(23);return;}
        if(mode.equals("TREE")) {
            String javaCommand=java.nio.file.Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
            String cp=java.nio.file.Path.of(ChildFailureFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            Process child=new ProcessBuilder(javaCommand,"-cp",cp,ChildFailureFixture.class.getName(),"NO_READY",args[1],args[2],args[3]).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[4]),Long.toString(child.pid()));
        }
        if(mode.equals("HANG")||mode.equals("TREE")){System.out.println("READY "+args[1]+" "+args[2]+" "+ProcessHandle.current().pid()+" "+args[3]);System.out.flush();}
        if(mode.equals("TREE")){new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();return;}
        while(true)Thread.sleep(100);
    }
}
