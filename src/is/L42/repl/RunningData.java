package is.L42.repl;

import static is.L42.tools.General.L;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import is.L42.common.Constants;
import is.L42.common.EndError;
import is.L42.common.ErrMsg;
import is.L42.common.NameMangling;
import is.L42.common.Parse;
import is.L42.common.Program;
import is.L42.flyweight.X;
import is.L42.generated.Core;
import is.L42.generated.Full;
import is.L42.generated.LDom;
import is.L42.generated.S;
import is.L42.generated.Full.E;
import is.L42.generated.Full.Par;
import is.L42.introspection.OverviewVisitor;
import is.L42.main.Main;
import is.L42.platformSpecific.javaTranslation.L42Error;
import is.L42.platformSpecific.javaTranslation.L42Exception;
import is.L42.platformSpecific.javaTranslation.Resources;
import is.L42.repl.CachedInference.FileInference;
import is.L42.tools.General;
import is.L42.top.CachedTop;
import is.L42.visitors.UndefinedCollectorVisitor;

/*
 All the code in this class is going to run from the SlaveVM running 42.
 It persists until 42 crashes or it is terminated (loop)
 */
record PingedData(List<String> tests,String out,String err,boolean over)implements Serializable{}
public class RunningData {
  static CachedTop cache;
  static CachedInference infer;
  static StringBuilder output;
  static StringBuilder error;
  static List<String> tests;
  static boolean started=false;
  static CompletableFuture<?> ended;
  static AbsPath l42Root;
  static void firstStart(Path top){
    output=new StringBuilder();
    error=new StringBuilder();
    tests=new ArrayList<String>();
    infer=new CachedInference();
    l42Root=new AbsPath(top.getParent());
    Resources.inferenceHandler(infer);
    Resources.setOutHandler(s->{synchronized(RunningData.class){output.append(s);}});
    Resources.setErrHandler(s->{synchronized(RunningData.class){error.append(s);}});
    Resources.setTestHandler(s->{synchronized(RunningData.class){tests.add(s);}});
    cache=new CachedTop(List.of(),List.of());
    }
  synchronized public static PingedData pingedData(){
    boolean done=ended!=null && ended.isDone();
    if(done && ended.isCompletedExceptionally()){
      Throwable zeus = ended.handle((a,err)->err).join();
      zeus.printStackTrace();
      String outputS="";
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      zeus.printStackTrace(pw);
      String errorS=sw.toString();
      output.setLength(0);
      error.setLength(0);
      tests.clear();
      return new PingedData(L(),outputS,errorS,done);
      }
    String outputS=output.toString();
    String errorS=error.toString();
    List<String> testsSs=List.copyOf(tests);
    output.setLength(0);
    error.setLength(0);
    tests.clear();
    return new PingedData(testsSs,outputS,errorS,done);
    }
  public static void parallelStart42(Path top) {
    try{Main.run(top,cache);}
    catch (IOException e){ 
      System.out.println("Ignored IOE");
      e.printStackTrace();
      throw new Error(e); }
    catch (EndError|L42Error| L42Exception e){}//correctly ignoring it since it is already printed on 'err'
    catch (Throwable e){ 
      System.out.println("Ignored Zeus");
      e.printStackTrace();
      throw new Error(e); }
    finally{
      cache=cache.toNextCache();
      Resources.clearResKeepReuse();
      }
    }
  public static void start42(Path top) {
    if(!started){ firstStart(top);}
    ended=CompletableFuture.runAsync(()->parallelStart42(top));
    }
  private static final S aaaHint=S.parse("aaa()");
  static public String computeHint(int row, int col, char last,String filename,String parsabletext) {
    var fi = infer.files.get(filename);
    if (fi==null){
      return "Row: "+row+" Col: "+col+"\n"+parsabletext+
        "\nUnknown type; try to recompile";
      }
    S currentHint=aaaHint;
    if(last!='.'){
      try {
        int i=parsabletext.lastIndexOf('.');
        String hint=parsabletext.substring(i+1);
        parsabletext=parsabletext.substring(0,i);
        var options=List.of(hint,"_"+hint,hint+"()","_"+hint+"()");
        for (var s : options){
          try{currentHint=S.parse(s);break;}
          catch(Throwable t){}
          }
        }
      catch(Throwable t){}
      }
    try{
      Full.E e = Parse.e(Constants.dummy, parsabletext).res;
      Program p=inferP(fi,row,e);
      var meths = ErrMsg.options(currentHint, p.topCore().mwts());
      var h="";
      if(currentHint!=aaaHint){h="    hint="+currentHint;}
      return
        "Row: "+row+
        " Col: "+col+
        "\n"+parsabletext+
        h+
        "\n"+meths;
      }
    catch(Throwable t) {
      return "Row: "+row+" Col: "+col+"\n"+parsabletext+
            "\nUnknown type; try to recompile";
      }  
    }
  private static Program inferP(FileInference fi,int row, E e) {
    Program res[]={null};
    e.visitable().accept(new UndefinedCollectorVisitor() {
      void progateSel(Full.E e,S s) {
        e.visitable().accept(this);
        var mwt=LDom._elem(res[0].topCore().mwts(),s);
        res[0]=res[0]._navigate(mwt.mh().t().p().toNCs());
        }
      @Override public void visitEX(Core.EX x){
        res[0]=fi._forX(x.x().inner(), row);
        }
      @Override public void visitCsP(Full.CsP csP){
        res[0]=fi._forPath(csP,row);
        }
      @Override public void visitEString(Full.EString e){
        var e0=e.es().get(0);
        S s=S.parse("#from(stringLiteral)");
        progateSel(e0,s);
        }
      @Override public void visitUOp(Full.UOp uOp){
        S s;
        if(uOp._num()!=null) {s=S.parse("#from(stringLiteral)");}
        else{
          String name=NameMangling.methName(uOp._op(),0);
          s=new S(name,L(),-1);
          }
        progateSel(uOp.e(),s);
        }
      @Override public void visitBinOp(Full.BinOp binOp){
        uc();}//TODO:
      @Override public void visitCast(Full.Cast cast){
        res[0]=fi._forPath(new Full.CsP(cast.pos(), cast.t().cs(),cast.t()._p()),row);        
        }
      @Override public void visitCall(Full.Call call){
        S s = call._s();
        if (s==null) {s=S.parse("#apply()");}
        if(call.isSquare()){s=s.withXs(L(X.of("squareBuilder")));}         
        else{
          Par p=call.pars().get(0);
          var xs=p.xs();
          if (p._that()!=null) {xs=General.pushL(X.thatX,xs);}
          s=s.withXs(xs);
          }
        progateSel(call.e(),s);
        }
      @Override public void visitBlock(Full.Block block){
        block._e().visitable().accept(this);
        }
    });
    return res[0];
    }
  static String overviewString(){
    var top=cache.lastTopL();
    if(top.isEmpty()){ return null; }
    return OverviewVisitor.makeOverview(top.get());
    }
  }