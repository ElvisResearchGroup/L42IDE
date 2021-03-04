package is.L42.repl;

import static is.L42.tools.General.L;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import is.L42.common.Constants;
import is.L42.common.ErrMsg;
import is.L42.common.NameMangling;
import is.L42.common.Parse;
import is.L42.common.Program;
import is.L42.generated.Core;
import is.L42.generated.Full;
import is.L42.generated.Full.E;
import is.L42.generated.Full.Par;
import is.L42.generated.LDom;
import is.L42.generated.S;
import is.L42.generated.X;
import is.L42.repl.CachedInference.FileInference;
import is.L42.tools.General;
import is.L42.visitors.UndefinedCollectorVisitor;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

public class HtmlFx extends StackPane{
  public WebEngine webEngine;
  public WebView browser;
  Region outerPanel;

  public HtmlFx(Region outer) {
    super();
    this.outerPanel=outer;
  }

  public final Events events=new Events();

  private Void initWeb(CountDownLatch latch,Consumer<WebEngine> load){
    this.browser = new WebView();
    this.webEngine = browser.getEngine();
    this.webEngine.getLoadWorker().stateProperty().addListener(
      (ov, oldState,newState)->{
        if (newState == Worker.State.SUCCEEDED) {latch.countDown();}
        });
    load.accept(this.webEngine);
//    this.webEngine.load(url.toExternalForm());
    this.webEngine.setOnAlert(event->{
      Alert alert = new Alert(AlertType.INFORMATION);
      alert.setTitle("Information Dialog");
      alert.setHeaderText(null);
      alert.setContentText(event.getData());
      alert.showAndWait();
      //alert.setOnCloseRequest(e->{  alert.close(); });
      });

    browser.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPress);

    // retrieve copy event via javascript:alert
    /*webEngine.setOnAlert((WebEvent<String> we) -> {
      if(we.getData()!=null && we.getData().startsWith("copy: ")){
         // COPY
         final Clipboard clipboard = Clipboard.getSystemClipboard();
         final ClipboardContent content = new ClipboardContent();
         content.putString(we.getData().substring(6));
         clipboard.setContent(content);
      }
    });*/
    //----
    this.getChildren().clear();
    this.getChildren().add(browser);
    latch.countDown();
    return null;
    }

  private void handleKeyPress(KeyEvent keyEvent) {
    if(outerPanel==null || !(outerPanel instanceof ReplTextArea)) {return;}
    var c=keyEvent.getCode();
    ReplTextArea editor=((ReplTextArea)outerPanel);
    //---CTRL+S save
    if (keyEvent.isControlDown() && c == KeyCode.S){
      editor.saveToFile();
      editor.removeStar();
      return;
      }
    if(!c.isArrowKey() && !c.isMediaKey() && !c.isModifierKey()){
      editor.addStar(); //file has been modified (NOT SAVED)
      }//selecting only the digits would, for example, fail to recognize deletion
   
    //DOCUMENTATION
    var chS=keyEvent.getText();
    if(chS.length()!=1) {return;}
    char ch=chS.charAt(0);
    var chOk=FromDotToPath.isValidIdChar(ch);
    if(c == KeyCode.PERIOD || chOk) {
      Object o=webEngine.executeScript("ace.edit(\"textArea\").getCursorPosition()");
      assert o instanceof JSObject : o.toString();
      JSObject jsobj=(JSObject)o;
      int row=(int)Double.parseDouble(jsobj.getMember("row").toString());
      int col=(int)Double.parseDouble(jsobj.getMember("column").toString());
      try { displayDoc(editor,row,col,ch); }
      catch(Throwable t){
        t.printStackTrace();
        }
      }
  }

  private void displayDoc(ReplTextArea editor, int row, int col, char last) {
    //row starts from 0 but file lines from 1
    if(ReplGui.main.cache==null) {return;}
    var fi = ReplMain.infer.files.get(editor.filename);
    if (fi==null) {return;}
    String parsableText=FromDotToPath.parsable(editor.getText(),row,col,last);
    S currentHint=S.parse("aaa()");
    if(last!='.'){
      try {
        int i=parsableText.lastIndexOf('.');
        String hint=parsableText.substring(i+1);
        parsableText=parsableText.substring(0,i);
        var options=List.of(hint,"_"+hint,hint+"()","_"+hint+"()");
        for (var s : options){
          try{currentHint=S.parse(s);break;}
          catch(Throwable t){}
          }
        }
      catch(Throwable t){}
      }
    try{
      Full.E e = Parse.e(Constants.dummy, parsableText).res;
      Program p=inferP(fi,row,e);
      var meths = ErrMsg.options(currentHint, p.topCore().mwts());
      ReplMain.gui.hints.setText("Row: "+row+" Col: "+col+"\n"+parsableText+"    hint="+currentHint+"\n"+meths);
      }
    catch(Throwable t) {
      ReplMain.gui.hints.setText("Row: "+row+" Col: "+col+"\n"+parsableText+
            "\nUnknown type; try to recompile");
      }
    }

  private Program inferP(FileInference fi,int row, E e) {
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
        if(call.isSquare()){s=s.withXs(L(new X("squareBuilder")));}         
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

  public static Error propagateException(Throwable t){
    if (t instanceof RuntimeException){throw (RuntimeException)t;}
    if (t instanceof Error){throw (Error)t;}
    if (t instanceof InterruptedException){Thread.currentThread().interrupt();}
    throw new Error(t);
    }
  public void printOut(Object o){
    System.out.println(o);
    }
  public void copy(String s) {
    ClipboardContent content = new ClipboardContent();
    content.putString(s);
    Clipboard.getSystemClipboard().setContent(content);
    }
  public String paste() {
    String content = (String) Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT);
    if(content == null){return "";}
    return content;
    }
  public void foldAll(){this.webEngine.executeScript("foldAll();");}
  public void createHtmlContent(CountDownLatch latch,Consumer<WebEngine> load) {
    assert Platform.isFxApplicationThread();
    initWeb(latch,load);
    //
    Object o=this.webEngine.executeScript(
        "window.event42=function(s){ "
        + "if(event42.eventCollector){"
        + "event42.eventCollector.add(s);"
        + "return 'Event '+s+' added '+event42.eventCollector.toString();} "
        + "return 'Event '+s+' not added';}");
    assert o instanceof JSObject : o.toString();
    JSObject jsobj = (JSObject)o;
    jsobj.setMember("eventCollector",this.events);
    JSObject window = (JSObject) webEngine.executeScript("window");
    window.setMember("htmlFx",this);
    latch.countDown();
  }
}