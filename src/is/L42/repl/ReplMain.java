package is.L42.repl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import is.L42.common.Constants;
import is.L42.common.Parse;
import is.L42.main.Main;
import javafx.application.Application;
import javafx.application.Platform;

class AbsPath{ //absolutePath
  final Path inner;
  public AbsPath(Path inner) {
    assert inner.isAbsolute();
    this.inner=inner;
    }
  public Path resolve(String other) {
    Path res=this.inner.resolve(other);
    assert res.isAbsolute();
    return res;
    }
  public Path resolve(Path other) {
    assert !other.isAbsolute();
    Path res=this.inner.resolve(other);
    assert res.isAbsolute();
    return res;
    }
  public String toString() {
    return this.inner.toAbsolutePath().toUri().toString();
    }
  public Path relativize(Path other) {
    Path res=inner.relativize(other);
    assert !res.isAbsolute();
    return res;
    }
  public boolean isChild(Path other) {
    assert other.isAbsolute();
    return other.startsWith(inner);
    }
  public File toFile() {return this.inner.toFile();}
  }

@SuppressWarnings("serial")
class DialogError extends Exception{}
public class ReplMain {
  static ReplGui gui;//TODO: may be swap them so it is a singleton pattern?
  static ExecutorService executor = Executors.newSingleThreadExecutor();
  
  public static void main(String []arg) throws IOException {
    URL url = ReplMain.class.getResource("textArea.xhtml");
    if(!url.toString().startsWith("jar:")){
      Constants.localhost=Paths.get("..","L42","localhost");
      Main.l42IsRepoVersion=Main.testingRepoVersion;
      }
    if (arg.length!=0) {Main.main(arg);return;}
    ReplGui.main=new ReplMain();
    Application.launch(ReplGui.class,arg);
    }
  public void eventStart(){Platform.runLater(gui.loadProjectBtn::fire);}

  public static void runLater(Runnable r) {
    assert Platform.isFxApplicationThread();
    executor.execute(r);
    }
  /*public static void runAndWait(Runnable r){//Note: blocks the application
    assert Platform.isFxApplicationThread();
    CompletableFuture.runAsync(r,executor).join();
    }*/
  public void stop() {
    Platform.runLater(()->{
      try{ReplMain.gui.stop();}
      catch(Exception e){throw new Error(e);}
    });
  }
  void ensureSettings(Path path, String content,Path settingsFile) {
    if(Files.exists(settingsFile)) {return;}
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Files.createFile(settingsFile);//create an empty Setti.ngs file in the selected folder
      Files.write(settingsFile, content.getBytes());
      }
    catch(IOException e) {throw new Error(e);}    
    }
  void makeNewProject(Path path, String content, Path thisFile){
    if(Files.exists(thisFile)) {return;}
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Files.createFile(thisFile);//create an empty This.L42 file in the selected folder
      Files.write(thisFile, content.getBytes());
      }
    catch(IOException e) {throw new Error(e);}	    
    }
  private static final List<String> extensions=List.of(".L42",".ngs");
  boolean validExtension(String name){
    return extensions.stream().anyMatch(e->name.endsWith(e));
    }
  List<Path> openProject(Path path){
    try {
      return Files.walk(path)
        .filter(Files::isRegularFile)
        .filter(p->validExtension(p.toFile().getName()))
        .toList();
      }
    catch(IOException e){throw new Error(e);}
    }
  static final String defaultIntro="""
      /*
        *** Welcome to the 42 IDE ***
        - Start pressing the 'Run!' button.
        - 'Overview' will show everything compiled by the last run.
        - the hints tab will show available method names for class
          and binding names available in the last run. 
      */
      """;
  static final String defaultSettings="""
      /*
        *** 42 settings ***
        You can change the stack and memory limitations and add security mappings
      */
      maxStackSize = 1G
      initialMemorySize = 256M
      maxMemorySize = 2G
      //For example, this would allow MyMain to call #$of() on an FS.Real
      //coming from L42.is/FileSystem
      //MyMain = [L42.is/FileSystem]
      """;
  void loadProject(Path path) {
    Path thisFile=path.resolve("This.L42");
    Path settingsFile=path.resolve("Setti.ngs");
    makeNewProject(path,Main.defaultMain+defaultIntro,thisFile);
    ensureSettings(path,defaultSettings,settingsFile);
    List<Path> filesToOpen=Files.exists(thisFile)?
      openProject(path):
      List.of(thisFile,settingsFile);
    GuiData.l42Root=new AbsPath(path);
    gui.rootPathSet=true;
    for(Path file:filesToOpen){openFileInNewTab(file);}
    //cache=CachedTop.loadCache(path);
    Platform.runLater(()->gui.enableRunB());
    }
  
  /*void clearCache(){
    infer.files.clear();
    try {Files.delete(l42Root.resolve("cache.L42Bytes"));}
    catch(java.nio.file.NoSuchFileException e){}//ignored
    catch (IOException e) {throw new Error(e);}
    this.cache=CachedTop.loadCache(l42Root.inner);
    }*/
  void openFile(Path file) {
    if(Files.exists(file) && GuiData.l42Root.isChild(file)){openFileInNewTab(file);return;}
    try{displayError("File not in Project","The selected file is not in the current project");} 
    catch(DialogError e){}
    }
  void displayError(String title,String msg) throws DialogError{
    Platform.runLater(()->gui.makeAlert(title,msg));
    throw new DialogError();
    }
  void openFileInNewTab(Path file) {
    assert file!=null && Files.exists(file);
    String openFileName = GuiData.l42Root.relativize(file).toString();
    openFileInNewTab(file,openFileName);
    }
  void openFileInNewTab(Path file,String tabName) {
    assert file!=null && Files.exists(file);
    try{
      String content; try {content = new String(Files.readAllBytes(file));}
      catch (IOException e){displayError("Invalid Project content","Lost contact with project folder");return;}
      makeReplTextArea(file,tabName,content);
      }
    catch(DialogError eTab){}
    }
  String overviewText=null;
  void loadOverview(){
    var newOverviewText=GuiData.overviewString();
    if(overviewText==null){ overviewText="\n"+newOverviewText; }
    if(newOverviewText!=null){ overviewText="\n"+newOverviewText; }
    }
  void openOverview(){
    loadOverview();
    var area=makeReplTextArea(null,"OVERVIEW",overviewText==null?"":overviewText);
    Platform.runLater(area.htmlFx::foldAll);
    }
  void openStyle(){
    openFileInNewTab(Path.of("L42Internals","js","editorStyle.js"),"IDE Style");
    }
  private ReplTextArea makeReplTextArea(Path tabPath,String tabName,String tabContent) {
    URL url = getClass().getResource("textArea.xhtml");
    String base;
    if(url.toExternalForm().startsWith("jar:")){ base=jarUrlToOutside(url); }
    else { base=url.toExternalForm(); }
    String content;try{content=new String(Files.readAllBytes(Paths.get(URI.create(base))));}
    catch (IOException e1){ throw new Error(e1); }
    int i=content.indexOf("<head>\n");
    String contentStart=content.substring(0,i+8);
    String contentEnd=content.substring(i+8);    
    String baseTag="<base href=\""+base+"\" target=\"_blank\">";
    ReplTextArea editor=ReplGui.runAndWait(4,l->new ReplTextArea(l,tabName,tabPath,contentStart+baseTag+contentEnd));
    Platform.runLater(()->gui.openTab(editor,tabContent));
    return editor;
    }
  String jarUrlToOutside(URL url){
    String res=url.toExternalForm();
    res=res.substring(4);
    //res=res.replace("L42.jar!/is/L42/repl/","");//This would be risky if the string happens multiple times
    int i=res.lastIndexOf("L42.jar!/is/L42/repl/", res.length()-1);
    assert i!=-1: res+" of unexpected form";
    res=res.substring(0,i)+res.substring(i+"L42.jar!/is/L42/repl/".length());
    return res;
    //IN  jar:file:/home/servetto/git/L42DeployScripts/L42PortableLinux/L42Internals/L42.jar!/is/L42/repl/textArea.xhtml
    //OUT file:/home/servetto/git/L42DeployScripts/L42PortableLinux/L42Internals/textArea.xhtml
    // remove 4, search and remove "L42.jar!/is/L42/repl/"
    }
  void runCode(){
    assert gui.running:"not running-runCode";
    Platform.runLater(()->{
      gui.errors.setText("");
      gui.output.setText("");
      });
    Path topPath=GuiData.l42Root.resolve("This.L42");
    //CachedTop cache=return new CachedTop(L(),L());//CachedTop.loadCache(path);
    try{GuiData.start42(topPath);}
    catch(CancellationException ce){}
    }
  @SuppressWarnings("unused")
  private static void copyEntireDirectory(Path src, AbsPath dest, String... doNotCopyFiles) {
    try (Stream<Path> stream = Files.list(src)) {
      stream
      .filter(x -> !Arrays.asList(doNotCopyFiles).contains(x.getName(x.getNameCount()-1).toString()))
      .forEach(sourcePath -> {
        try {
          Path target= dest.resolve(sourcePath.getName(sourcePath.getNameCount()-1));
          Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {throw new Error(e);}
      });
    } catch (IOException e1) {throw new Error(e1);}
  }
  boolean checkValidC(String current){
    var csP = Parse.csP(Constants.dummy, current);
    if(csP.hasErr() || csP.res._p() != null){return false;}
    var cs=csP.res.cs();
    if(cs.size()!=1){return false;}
    if(cs.get(0).hasUniqueNum()){return false;}
    assert current.indexOf(".")==-1;
    return true;
    }
  String fileNameError(String current){
    if(current.isEmpty()){ return "Don't start your file name with a '/'";};
    if(current.charAt(0) != current.toUpperCase().charAt(0)){ 
      return "Invalid File name \n Make sure the first character is Uppercase";
      }
    return "Invalid File name: "+current + "\nFile names should be valid 42 class names.";
    }
  Path processIntermediate(Path res,String current,String next)throws DialogError{
    if(!checkValidC(current)) {displayError("Invalid File name",fileNameError(current));}
    res=res.resolve(current);
    try {
      if(Files.exists(res)){return res;}
      Files.createDirectory(res);
      var tmp=res.resolve("This.L42");
      Files.createFile(tmp);
      if(!next.equals("This")){Files.write(tmp,("\n"+next+"={...}\n").getBytes());}
      else{Files.write(tmp,"\n\n".getBytes());}
      openFile(tmp);
      return res;
      }
    catch (IOException e) {throw new Error(e);}          
    }
  Path processLast(Path res,String current) throws DialogError{
    if(!current.equals("This") && !checkValidC(current)) {displayError("Invalid File name",fileNameError(current));}
    current+=".L42";
    res=res.resolve(current);
    try {
      if(Files.exists(res)){return res;}
      Files.createFile(res);
      Files.write(res,"\n\n".getBytes());
      openFile(res);
      return res;
      }
    catch (IOException e) {throw new Error(e);}
    }
  void makeNewFile(String r) {
    boolean isFolder=r.endsWith("/");
    if(isFolder){r=r+"This";}
    var ns=List.of(r.split("/"));
    if(ns.isEmpty()) {return;}
    var res=GuiData.l42Root.inner;
    int count=0;
    try {
      while(count<ns.size()){
        boolean isLast=count+1==ns.size();
        var current=ns.get(count).trim();
        if(!isLast) {res=processIntermediate(res,current, ns.get(count+1).trim());}
        else {res=processLast(res,current);}
        count+=1;
        }
      }
    catch(DialogError e){}
    }
  }