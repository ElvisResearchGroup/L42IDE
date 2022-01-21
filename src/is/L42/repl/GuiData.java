package is.L42.repl;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import is.L42.common.Constants;
import is.L42.common.EndError;
import is.L42.common.Parse;
import is.L42.main.Main;
import is.L42.main.Settings;
import is.L42.platformSpecific.javaTranslation.Resources;
import javafx.application.Platform;
import safeNativeCode.slave.Slave;
import safeNativeCode.slave.host.ProcessSlave;

public class GuiData {
  public static AbsPath l42Root=new AbsPath(Path.of(".").toAbsolutePath());
  static Slave s=null;
  static Settings settings=null;
  static void updateSettings(){
    var currentSettings=settingsOrPrintErr();
    if(!currentSettings.equals(GuiData.settings)){ GuiData.terminate42(); }
    GuiData.settings=currentSettings;
    }
  static private Settings settingsOrPrintErr(){
    try{ return Parse.sureSettings(GuiData.l42Root.resolve("Setti.ngs")); }
    catch(EndError ee) {
      ReplMain.gui.errors.appendText("-------------------------\n");
      ReplMain.gui.errors.appendText("Settings error:\n");
      ReplMain.gui.errors.appendText("-------------------------\n");
      ReplMain.gui.errors.appendText(Main.fixMessage(ee.getMessage())+"\n");
      ReplMain.gui.selectErr();
      throw ee;
      }
    }
  static Slave makeSlave(Settings currentSettings) throws RemoteException, ExecutionException, InterruptedException{
    Slave s=new ProcessSlave(-1,new String[]{},ClassLoader.getPlatformClassLoader()){
      @Override protected List<String> getJavaArgs(String libLocation){
        var res=super.getJavaArgs(libLocation);
        if(Main.isTesting()){ res.add(0,"-ea"); }
        res.add(0,"--enable-preview");
        currentSettings.options().addOptions(res);
        return res;
        }
      };
    if(Main.isTesting()){
      s.run(()->{
        Constants.localhost=Paths.get("..","L42","localhost");
        Main.l42IsRepoVersion=Main.testingRepoVersion;
        });
      }
    return s;
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
      String currOut=ReplMain.gui.output.getText();
      String currErr=ReplMain.gui.errors.getText();
      String out=limitLines(pingedData.out(),currOut);
      String err=limitLines(pingedData.err(),currErr);
      var tooMuchOut=out.length()+currOut.length()>max;
      var tooMuchErr=err.length()+currErr.length()>max;
      if(tooMuchOut){
        ReplMain.gui.output.setText(cutted(currOut,out));
        }
      else { ReplMain.gui.output.appendText(out); }
      if(tooMuchErr){
        ReplMain.gui.errors.setText(cutted(currErr,err));
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
    int bl = b.length();
    if (bl >= max/2) { sb.append(b,bl-max/2,bl); }
    else {
      sb.append(a,(al-(max/2))+bl,al);
      sb.append(b);
    }
    return sb.toString();
  }
  private static final int max=200_000;
  private static final int maxLine=40_000;
  public static final int maxTest=20_000;
  public static void clear(){
    Platform.runLater(()->{
      ReplMain.gui.output.clear();
      ReplMain.gui.errors.clear();
      ReplMain.gui.tests.reset();
      });
    }
  public static void auxStart42(URI top)throws InterruptedException, RemoteException, ExecutionException{
    clear();
    if(s==null){
      try{s=makeSlave(settings);}
      catch(Throwable t) {t.printStackTrace();throw t;}  
    }
    ping = scheduler.scheduleAtFixedRate(()->GuiData.ping42(), 200, 100, TimeUnit.MILLISECONDS);
    s.run(()->{
    try {  RunningData.start42(Path.of(top)); }
    catch(Throwable t) { 
      t.printStackTrace();
      throw t;
      }
    });
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
