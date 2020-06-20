package is.L42.repl;

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

import is.L42.common.Program;
import is.L42.generated.Core;
import is.L42.main.Main;
import is.L42.platformSpecific.javaTranslation.Resources;
import is.L42.tests.TestCachingCases;
import is.L42.top.CachedTop;
import is.L42.visitors.CloneVisitor;
import is.L42.visitors.ToSVisitor;
import javafx.application.Application;
import javafx.application.Platform;

public class ReplMain {
  static ReplGui gui;//TODO: may be swap them so it is a singleton pattern?
  static AbsPath l42Root=new AbsPath(Path.of(".").toAbsolutePath());
  static ExecutorService executor = Executors.newFixedThreadPool(1);
  Core.L topL;
  Program p=Program.emptyP;//TODO:
  CachedTop cache=null;
  public static void main(String []arg) {
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
      };
    top.get().accept(v);
    var area=makeReplTextArea("OVERVIEW",v.result().toString());
    Platform.runLater(area.htmlFx::foldAll);
    }
  private URL makeUrl(){
    URL url = getClass().getResource("textArea.xhtml");
    if(url.toString().startsWith("jar:")){
      try{return Paths.get("localhost","textArea.xhtml").toUri().toURL();}
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
    try{
      long start1=System.currentTimeMillis();
      TestCachingCases.last=start1;
      this.topL=Main.run(l42Root.resolve("This.L42"),cache);
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
  /* public static ReplState copyResetKVCthenRun(Function<Path, Loader> loaderFactory, String fileContent, String... doNotCopyFiles) throws IOException {
    return copyResetKVCthenRun(true, loaderFactory, fileContent, doNotCopyFiles);
  }
*/
  /*
  public static ReplState copyResetKVCthenRun(boolean runAll, Function<Path, Loader> loaderFactory, String fileContent, String... doNotCopyFiles) throws IOException {
    ReplState repl=null;

    //check first 2 line of This.l42
    System.out.println("READ FIRST2LINE...");
    CodeInfo res=new CodeInfo(fileContent);

    //check if library already cached in L42IDE folder
    final AbsPath currentRoot=L42.root;

    L42.setRootPath(Paths.get("L42IDE").toAbsolutePath()); //TODO: check later where is created?
    final Path dirPath=L42.root.resolve(res.cacheLibName);

    boolean cachedBefore= validSystemCache(dirPath);
    if (!cachedBefore){ //if not already cached before
      System.out.println("CREATING CACHE IN L42IDE FOR FIRST2LINE...");
      if(!Files.exists(dirPath)) {Files.createDirectories(dirPath);}
      L42.setRootPath(dirPath);
      L42.cacheK.setFileName("This.L42","{"+res.first2Line+"}");
      repl=ReplState.start("{"+res.first2Line+"}", loaderFactory.apply(dirPath.resolve("This.C42"))); //create the cache
    }

    //Copy the files into the current project
    if(cachedBefore) {
      System.out.println("COPYING CACHE FROM L42IDE TO CURRENT ROOT (WITHOUT This.C42)...");
      ReplMain.copyEntireDirectory(dirPath,currentRoot,doNotCopyFiles);
    } else {
      System.out.println("COPYING CACHE FROM L42IDE TO CURRENT ROOT (WITH This.C42)...");
      ReplMain.copyEntireDirectory(dirPath,currentRoot);
    }

    L42.root=currentRoot; //go back to project folder
    //if(repl==null) {
      L42.cacheK.setFileName("This.L42","{"+res.first2Line+"}");
      System.out.println("RE-CREATING REPL FROM CACHE (FIRST2LINE ONLY)...");
      repl=ReplState.start("{"+res.first2Line+"}", loaderFactory.apply(currentRoot.resolve("This.C42")));
    //} else {
    //  repl.reduction.loader.updateCachePath(pathC); //TODO: see why does not cache C properly (saved not in right place?)
    //}

    if(runAll) {
      L42.cacheK.setFileName("This.L42",res.restOfCode);
      System.out.println("ReplState.add(RESTOFCODE) RUNNING...");
      ReplState newR=repl.add(res.restOfCode);
      if(newR!=null){repl=newR;}
    }

    return repl;
  }
*/
  protected static class CodeInfo{
    String first2Line;
    String cacheLibUrl;
    String cacheLibName;
    String restOfCode;
    CodeInfo(String string){
      try(Scanner sc = new Scanner(string)) {
        Pattern delimit= Pattern.compile("(\\n| |,)*"); //newline, spaces and comma
        sc.skip(delimit);
        if(!sc.next().equals("reuse")) {throw new IllegalArgumentException();}
        sc.skip(delimit);
        this.cacheLibUrl=sc.next();
        this.cacheLibName=URLEncoder.encode(this.cacheLibUrl, "UTF-8");
        sc.skip(delimit);
        String[] secondLine=sc.nextLine().split(":");
        if(secondLine.length!=2) {throw new IllegalArgumentException();}
        String className=secondLine[0];
        String lastPart=secondLine[1];
        //TODO: if(!PathAux.isValidClassName(className)) {throw new IllegalArgumentException();}
        if(!lastPart.equals("Load.cacheTowel()")) {throw new IllegalArgumentException();}
        this.first2Line="reuse "+cacheLibUrl+"\n"+className+":"+"Load.cacheTowel()";
        sc.useDelimiter("\\Z"); //rest of the content
        this.restOfCode=sc.next();
        }
      catch (UnsupportedEncodingException e){throw new IllegalArgumentException(e);}
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