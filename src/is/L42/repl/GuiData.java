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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import safeNativeCode.slave.Slave;
import safeNativeCode.slave.host.ProcessSlave;

public class GuiData {
  static Slave s=null;
  static Slave makeSlave(){
    return new ProcessSlave(-1,new String[]{},ClassLoader.getPlatformClassLoader()){
      @Override protected List<String> getJavaArgs(String libLocation){
        var res=super.getJavaArgs(libLocation);
        //res.add(0,"-ea");
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
      String out=limitLines(pingedData.out(),ReplMain.gui.output.getText());
      String err=limitLines(pingedData.err(),ReplMain.gui.errors.getText());
      var tooMuchOut=out.length()+ReplMain.gui.output.getText().length()>max;
      var tooMuchErr=err.length()+ReplMain.gui.errors.getText().length()>max;
      if(tooMuchOut){
        ReplMain.gui.output.clear();
        ReplMain.gui.output.appendText(
          cutted(ReplMain.gui.output.getText(),out));
        }
      else { ReplMain.gui.output.appendText(out); }
      if(tooMuchErr){
        ReplMain.gui.errors.clear();
        ReplMain.gui.errors.appendText(
            cutted(ReplMain.gui.errors.getText(),err));
        }
      else { ReplMain.gui.errors.appendText(err); }
      ReplMain.gui.tests.handle(pingedData.tests());
      var someErr=!pingedData.err().isEmpty();
      var someOut=!pingedData.out().isEmpty();
      var someTests=!pingedData.tests().isEmpty();
      if(someErr){ ReplMain.gui.selectErr(); return; }
      if(someOut){ ReplMain.gui.selectOut(); return; }
      if(!someTests){ return; }
      var someFail=!pingedData.tests().stream().allMatch(DisplayTests::isPass);
      if(someFail){ ReplMain.gui.selectFail(); return; }
      if(ReplMain.gui.tests.failed==0){ ReplMain.gui.selectPass(); }
      });
    }
  private static int nextNl(String s,int c) { 
    int i=s.indexOf("\n",c);
    if(i==-1){ return s.length(); }
    return i+1;
    }
  private static String limitLines(String a,String pre){
    int i=pre.lastIndexOf("\n");
    if(i==-1) {i=pre.length();}
    else { i=pre.length()-i; }
    StringBuilder b=new StringBuilder();
    int fl=0;
    for(int nl=nextNl(a,fl);fl!=a.length();fl=nl,nl=nextNl(a,nl)){
      String currLine=a.substring(fl,nl); //...\n or ...EOS
      int size=currLine.length();
      if(fl==0){ size+=i; }
      if(size<maxLine){ b.append(currLine); continue;}
      b.append("**Line size limit reached, tail of line removed**<|");
      if(fl==0){ b.append(currLine,0,maxLine-i); }
      else{ b.append(currLine,0,maxLine); }
      }
    return b.toString();
    }
  private static String cutted(String a,String b){
    StringBuilder sb = new StringBuilder();
    sb.append("  **Text size limit reached. Some former text has been removed**\n------------------\n");
    int al = a.length(); 
    int bl = a.length();
    int howMuchOver = al+bl - max/2;
    if (al < howMuchOver) { sb.append(b,0,howMuchOver-al);}
    else {
      sb.append(a,0,howMuchOver);
      sb.append(b);
    }
    return sb.toString();
  }
  private static int max=200_000;
  private static int maxLine=40_000;
  public static int maxTest=20_000;
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
    if(ping!=null){
      ping.cancel(false);
      ping=null;
      ReplMain.gui.errors.appendText("Execution terminated");
      ReplMain.gui.selectErr();
      }
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
