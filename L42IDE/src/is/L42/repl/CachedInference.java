package is.L42.repl;

import static is.L42.tools.General.range;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import is.L42.common.Program;
import is.L42.generated.Core;
import is.L42.generated.Core.EX;
import is.L42.generated.Core.L.MWT;
import is.L42.generated.Full.CsP;
import is.L42.platformSpecific.javaTranslation.Resources;
import is.L42.top.Init;

class CachedInference implements Resources.InferenceHandler{
  public void ex(EX ex, Program p) {
    var fName = ex.pos().fileName();
    var name=ReplMain.l42Root.relativize(Paths.get(fName)).toString();
    var fi = files.computeIfAbsent(name,n->new FileInference());
    var ep = fi.xs.computeIfAbsent(ex.x().inner(),n->new EProg());
    ep.add(ex, p);
    }
  public void mwt(MWT mwt, Program p) {
    if(mwt._e()==null) {return;}
    var fName = mwt._e().pos().fileName();
    var name=ReplMain.l42Root.relativize(Paths.get(fName)).toString();
    var fi = files.computeIfAbsent(name,n->new FileInference());
    fi.forPath.add(mwt._e(), p);
    }
  public void nc(Core.E e, Program p) {
    var fName = e.pos().fileName();
    var name=ReplMain.l42Root.relativize(Paths.get(fName)).toString();
    var fi = files.computeIfAbsent(name,n->new FileInference());
    fi.forPath.add(e, p);
  }
  HashMap<String,FileInference> files = new HashMap<>();
  public String toString() {return files.toString();}
  record EProg(ArrayList<Core.E>es,ArrayList<Program>ps){
    EProg(){this(new ArrayList<>(),new ArrayList<>());}
    EProg{assert es.size()==ps.size();}
    void add(Core.E a,Program b){es.add(a);ps.add(b);}
    static int value(int source,int in){//the smallest the better
      int delta = in-source;
      if(delta<0) {delta*=3;}
      return delta;
      }
    int bestIndex(int lineNum){
      if(es.isEmpty()) {return -1;}
      int bestI = 0;
      int bestV = Integer.MAX_VALUE;
      for(int i : range(es)) {
        int currentV = value(es.get(i).pos().line(),lineNum);
        if (currentV>bestV){continue;}
        bestV = currentV;
        bestI = i;
        }
      return bestI;
      }
    }
  record FileInference(
    HashMap<String,EProg>xs,
    EProg forPath
    ) {
    FileInference(){this(new HashMap<>(),new EProg());}
    Program _forX(String x,int lineNum){
      var forX = xs.get(x);
      if(forX==null) {return null;}
      int i = forX.bestIndex(lineNum);
      if(i==-1) {return null;}
      return forX.ps.get(i);
      }
    Program _forPath(CsP path,int lineNum) {
      int i = forPath.bestIndex(lineNum);
      if(i==-1) {return null;}
      Program p=forPath.ps.get(i);
      try {return p.navigate(Init.resolveCsP(p,path).toNCs());}
      catch(Throwable t) {return null;}
      }
    }
  }