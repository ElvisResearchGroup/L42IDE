package is.L42.repl;

import java.net.URI;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import safeNativeCode.slave.Slave;
import safeNativeCode.slave.host.ProcessSlave;

public class GuiData {
  static Slave s=null;
  static Slave makeSlave(){
    return new ProcessSlave(-1,new String[]{},ClassLoader.getPlatformClassLoader()){
      @Override protected List<String> getJavaArgs(String libLocation){
        var res=super.getJavaArgs(libLocation);
        res.add(0,"-ea");
        res.add(0,"--enable-preview");
        return res;
        }
      };
    }
  private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private static ScheduledFuture<?> ping=null;
  
  private static void ping42(){
    PingedData pingedData;try{pingedData=s.call(()->RunningData.pingedData()).get();}
    catch (RemoteException e){ throw new CompletionException(e); }
    if(pingedData.over()){
      ping.cancel(false);
      ping=null;
      Platform.runLater(()-> ReplMain.gui.enableRunB());
      ReplGui.main.loadOverview();
      }
    Platform.runLater(()->{
      var tooMuchOut=pingedData.out().length()+ReplMain.gui.output.getText().length()>max;
      var tooMuchErr=pingedData.err().length()+ReplMain.gui.errors.getText().length()>max;
      if(tooMuchOut){ReplMain.gui.output.clear();}
      if(tooMuchErr){ReplMain.gui.errors.clear();}
      ReplMain.gui.output.appendText(pingedData.out());
      ReplMain.gui.errors.appendText(pingedData.err());
      ReplMain.gui.tests.handle(pingedData.tests());
      });
    }
  private static int max=200_000;
  public static void clear(){
    Platform.runLater(()->{
      ReplMain.gui.output.clear();
      ReplMain.gui.errors.clear();
      ReplMain.gui.tests.reset();
      });
    }
  public static void auxStart42(URI top)throws InterruptedException, RemoteException, ExecutionException{
    clear();
    if(s==null){ s=makeSlave(); }
    ping = scheduler.scheduleAtFixedRate(()->GuiData.ping42(), 200, 100, TimeUnit.MILLISECONDS);
    s.run(()->RunningData.start42(Path.of(top)));
    }
  public static void terminate42(){
    if(s!=null){ s.terminate(); }
    if(ping!=null){ ping.cancel(false); ping=null; }
    s=null;
    ReplMain.gui.enableRunB();
    }
  public static void start42(Path top){
    assert ping==null;
    try {auxStart42(top.toUri());}
    catch (RemoteException e) { throw new Error(e); }
    catch (InterruptedException e) { throw new Error(e); } 
    catch (ExecutionException e) { throw new Error(e); }
    }
  public static String computeHint(int row, int col, char last, String filename, String parsabletext){
    if(s==null){return "";}
    try{ return s.call(()->RunningData.computeHint(row, col, last, filename, parsabletext)).get(); }
    catch (RemoteException e) { throw new Error(e); }
    }
  public static String overviewString(){
    if(s==null){return null;}
    try{ return s.call(()->RunningData.overviewString()).get(); }
    catch (RemoteException e) { throw new Error(e); }
    }
  }
