package is.L42.repl;

import static is.L42.tools.General.L;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import is.L42.common.Constants;
import is.L42.common.Program;
import is.L42.generated.Core;
import is.L42.main.Main;
import is.L42.platformSpecific.javaTranslation.Resources;
import is.L42.tests.TestCachingCases;
import is.L42.top.CachedTop;
import javafx.application.Application;
import javafx.application.Platform;

public class ReplMain {
  static ReplGui gui;//TODO: may be swap them so it is a singleton pattern?
  static AbsPath l42Root=new AbsPath(Path.of(".").toAbsolutePath());
  static ExecutorService executor = Executors.newFixedThreadPool(1);
  CachedTop cache=null;
  public static void main(String []arg) {
    Constants.localhost=Paths.get("..","..","L42","L42","localhost");
    //from    \git\L42IDE\L42IDE\localhost
    //to      \git\L42\L42\localhost
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
  public static final String defaultMain="""
      reuse [AdamTowel]
      Main=(
        Debug(S"Hello world")
        )
      """;
  void loadProject(Path path) {
    System.out.println(path.toAbsolutePath());
    Path thisFile=path.resolve("This.L42");
    List<Path> filesToOpen=Files.exists(thisFile)?
        openProject(path):makeNewProject(path,defaultMain,thisFile);
    l42Root=new AbsPath(path);
    gui.rootPathSet=true;
    for(Path file:filesToOpen){openFileInNewTab(file);}
    cache=CachedTop.loadCache(path);
    Platform.runLater(()->gui.enableRunB());
    }
  void clearCache(){
    try {Files.delete(l42Root.resolve("cache.L42Bytes"));}
    catch(java.nio.file.NoSuchFileException e){/*ignored*/}
    catch (IOException e) {throw new Error(e);}
    this.cache=CachedTop.loadCache(l42Root.inner);
    }
  void openFile(Path file) {
    if(Files.exists(file) && l42Root.isChild(file)){openFileInNewTab(file);}
    else{Platform.runLater(()->gui.makeAlert("File not in Project","The selected file is not in the current project"));}
    }
  void openFileInNewTab(Path file) {
    assert file!=null && Files.exists(file);
    String content; try {content = new String(Files.readAllBytes(file));}
    catch (IOException e) {
      Platform.runLater(()->gui.makeAlert("Invalid Project content","Lost contact with project folder"));
      return;
      }
    String openFileName = l42Root.relativize(file).toString();
    makeReplTextArea(openFileName,content);
    }
  void openOverview(){
    var top=this.cache.lastTopL();//not always working, cache may not "store" the last step?
    if(top.isEmpty()){makeReplTextArea("OVERVIEW","{}");return;}
    var v=new is.L42.introspection.FullS(){
      @Override public void visitInfo(Core.L.Info info){}
      @Override public boolean headerNewLine(){return true;}
      @Override public void visitL(Core.L l){
        var mwts=L(l.mwts().stream().sorted((m1,m2)->m1.key().toString().compareTo(m2.key().toString())));
        var ncs=L(l.ncs().stream().sorted((m1,m2)->m1.key().inner().compareTo(m2.key().inner())));
        super.visitL(l.withMwts(mwts).withNcs(ncs));
        }
      };
    top.get().accept(v);
    var area=makeReplTextArea("OVERVIEW",v.result().toString());
    Platform.runLater(area.htmlFx::foldAll);
    }
  private URL makeUrl(){
    URL url = getClass().getResource("textArea.xhtml");
    if(url.toString().startsWith("jar:")){
      try{return Constants.localhost.resolve("textArea.xhtml").toUri().toURL();}
      catch(MalformedURLException e){throw new Error(e);}
      }
    return url;
    }
  private ReplTextArea makeReplTextArea(String fileName,String tabContent) {
    URL url = makeUrl();
    assert url!=null:"";
    ReplTextArea editor=ReplGui.runAndWait(4,l->new ReplTextArea(l,fileName,url));
    Platform.runLater(()->gui.openTab(editor,tabContent));
    return editor;
    }

  void runCode(){
    assert gui.running:"not running-runCode";
    Platform.runLater(()->{
      gui.errors.setText("");
      gui.output.setText("");
      });
    try{
      long start1=System.currentTimeMillis();
      TestCachingCases.last=start1;
      Main.run(l42Root.resolve("This.L42"),cache);
      }
    catch (IOException e) {throw new Error(e);}
    finally{
      cache=cache.toNextCache();
      CacheSaver.saveCache(cache);
      System.out.println(Resources.notifiedCompiledNC());
      Resources.clearResKeepReuse();
      Platform.runLater(()->gui.updateTextFields());
      }
    }
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
}
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