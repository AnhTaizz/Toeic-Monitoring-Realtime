public final class ExtendedInstalledDriver {
 public static void main(String[] args)throws Throwable{
  Class<?> c=Class.forName("vn.edu.toeic.server.audio.ListeningRuntimePostgresTest");var ctor=c.getDeclaredConstructor();ctor.setAccessible(true);Object test=ctor.newInstance();
  try{call(c,test,"startRuntime");call(c,test,"installedProductionClientUsesBundledRuntimeAndRealAudioLifecycle");System.out.println("PASS EXTENDED INSTALLED REAL PG/HTTP/WS/production EXE/History/B4 reorganization; TEST fixture, simulated clicks/hearing/socket fault; no LAN");}
  finally{call(c,test,"cleanup");}
 }
 static void call(Class<?> c,Object o,String name)throws Throwable{var m=c.getDeclaredMethod(name);m.setAccessible(true);try{m.invoke(o);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}}
}