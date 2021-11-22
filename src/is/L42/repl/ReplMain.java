package is.L42.repl;

import static is.L42.tools.General.L;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import is.L42.common.Constants;
import is.L42.common.Parse;
import is.L42.flyweight.CoreL;
import is.L42.generated.Core;
import is.L42.main.Main;
import is.L42.platformSpecific.javaTranslation.Resources;
import is.L42.top.CachedTop;
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
public class ReplMain {
  static ReplGui gui;//TODO: may be swap them so it is a singleton pattern?
  static AbsPath l42Root=new AbsPath(Path.of(".").toAbsolutePath());
  static ExecutorService executor = Executors.newFixedThreadPool(1);
  
  
  static CachedInference infer=new CachedInference();
  {Resources.inferenceHandler(infer);}  

  CachedTop cache=null;
  public static void main(String []arg) throws IOException {
    //NOTE TO EXPORT
    //option 1
    //-We export runnable jar (with jars in a folder next)
    //-We put the bin folder next
    //-We put a localhost folder containing adamTowel, sifo and also the textArea.xhtml and js and css folders
    //option 2
    //-We export runnable jar (with jars expanded)
    //-We put the bin folder content directly inside the jar toplevel
    if (arg.length!=0) {Main.main(arg);return;}
    URL url = ReplMain.class.getResource("textArea.xhtml");
    if(url.toString().startsWith("jar:")){Constants.localhost=Paths.get("localhost");}
    else{Constants.localhost=Paths.get("..","L42","localhost");}
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
  List<Path> makeNewProject(Path path, String content, Path thisFile){
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Files.createFile(thisFile);//create an empty This.L42 file in the selected folder
      Files.write(thisFile, content.getBytes());
      }
    catch(IOException e) {throw new Error(e);}
    return Collections.singletonList(thisFile); //only This.L42 file	    
    }
  List<Path> openProject(Path path){
    try {
      return Files.walk(path)
        .filter(Files::isRegularFile)
        .filter(p->p.toFile().getName().endsWith(".L42"))
        .collect(Collectors.toList());
      }
    catch(IOException e){throw new Error(e);}
    }
  void loadProject(Path path) {
    Path thisFile=path.resolve("This.L42");
    List<Path> filesToOpen=Files.exists(thisFile)?
        openProject(path):makeNewProject(path,Main.defaultMain,thisFile);
    l42Root=new AbsPath(path);
    gui.rootPathSet=true;
    for(Path file:filesToOpen){openFileInNewTab(file);}
    cache=CachedTop.loadCache(path);
    Platform.runLater(()->gui.enableRunB());
    }
  void clearCache(){
    infer.files.clear();
    try {Files.delete(l42Root.resolve("cache.L42Bytes"));}
    catch(java.nio.file.NoSuchFileException e){/*ignored*/}
    catch (IOException e) {throw new Error(e);}
    this.cache=CachedTop.loadCache(l42Root.inner);
    }
  void openFile(Path file) {
    if(Files.exists(file) && l42Root.isChild(file)){openFileInNewTab(file);}
    else{displayError("File not in Project","The selected file is not in the current project");}
    }
  void displayError(String title,String msg){Platform.runLater(()->gui.makeAlert(title,msg));}
  void openFileInNewTab(Path file) {
    assert file!=null && Files.exists(file);
    String content; try {content = new String(Files.readAllBytes(file));}
    catch (IOException e) {
      displayError("Invalid Project content","Lost contact with project folder");
      return;
      }
    String openFileName = l42Root.relativize(file).toString();
    makeReplTextArea(openFileName,content);
    }
  void openOverview(){
    var top=this.cache.lastTopL();//not always working, cache may not "store" the last step?
    if(top.isEmpty()){makeReplTextArea("OVERVIEW","{}");return;}
    var v=new is.L42.introspection.FullS(){
      @Override public void visitInfo(Core.Info info){}
      @Override public boolean headerNewLine(){return true;}
      @Override public void visitL(CoreL l){
        var mwts=L(l.mwts().stream().sorted((m1,m2)->m1.key().toString().compareTo(m2.key().toString())));
        var ncs=L(l.ncs().stream().sorted((m1,m2)->m1.key().inner().compareTo(m2.key().inner())));
        super.visitL(l.withMwts(mwts).withNcs(ncs));
        }
      @Override public void visitDoc(Core.Doc doc){
        c("@");
        if(doc._pathSel()!=null){visitPathSel(doc._pathSel());}
        if(doc.texts().isEmpty()){return;}
        assert doc.texts().size()==doc.docs().size()+1;
        c("{");nl();
        seq(i->c(doc.texts().get(i)),doc.docs(),"");
        c(doc.texts().get(doc.texts().size()-1));
        nl();c("}");
        }
      };
    top.get().accept(v);
    var area=makeReplTextArea("OVERVIEW",v.result().toString());
    Platform.runLater(area.htmlFx::foldAll);
    }
  private URL makeUrl(){
    try {return new URL("http://L42.is/jsIDE/textArea.xhtml");}
    catch(MalformedURLException e){throw new Error(e);}
    }
  private ReplTextArea makeReplTextArea(String fileName,String tabContent) {
    //URL url = makeUrl();
    //assert url!=null:"";
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
    ReplTextArea editor=ReplGui.runAndWait(4,l->new ReplTextArea(l,fileName,contentStart+baseTag+contentEnd));
    Platform.runLater(()->gui.openTab(editor,tabContent));
    return editor;
    }
  String jarUrlToOutside(URL url){
    System.out.println(url);
    String res=url.toExternalForm();
    res=res.substring(4);
    System.out.println(url);
    //res=res.replace("L42.jar!/is/L42/repl/","");
    int i=res.lastIndexOf("L42.jar!/is/L42/repl/", res.length()-1);
    assert i!=-1: res+" of unexpected form";
    res=res.substring(0,i)+res.substring(i+"L42.jar!/is/L42/repl/".length());
    return res;
    //file:/am/roxy/home/servetto/git/L42IDE/bin/is/L42/repl/textArea.xhtml
//file:/home/servetto/git/L42DeployScripts/L42PortableLinux/L42Internals/L42.jar!/is/L42/repl/textArea.xhtml
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
    try{Main.run(l42Root.resolve("This.L42"),cache);}
    catch (IOException e) {throw new Error(e);}
    finally{
      cache=cache.toNextCache();
      //TODO: re-enable later CacheSaver.saveCache(cache);
      Resources.clearResKeepReuse();
      Platform.runLater(()->gui.updateTextFields());
      }
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
  Path processIntermediate(Path res,String current,String next){
    if(!checkValidC(current)) {displayError("Invalid File name","Invalid File name: "+current);}
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
  Path processLast(Path res,String current){
    if(!current.equals("This") && !checkValidC(current)) {displayError("Invalid File name","Invalid File name: "+current);}
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
    var res=ReplMain.l42Root.inner;
    int count=0;
    while(count<ns.size()){
      boolean isLast=count+1==ns.size();
      var current=ns.get(count).trim();
      if(!isLast) {res=processIntermediate(res,current, ns.get(count+1).trim());}
      else {res=processLast(res,current);}
      count+=1;
      }
    }
  }