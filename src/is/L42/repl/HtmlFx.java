package is.L42.repl;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

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
    browser.addEventHandler(KeyEvent.KEY_TYPED, this::handleKeyTyped);
    //browser.setOnK

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
  private void handleKeyTyped(KeyEvent keyEvent) {
    //var c=keyEvent.getCode();
    ReplTextArea editor=((ReplTextArea)outerPanel);
    var chS=keyEvent.getCharacter();
    if(chS.length()!=1) {return;}
    char ch=chS.charAt(0);
    //var chOk=FromDotToPath.isValidIdChar(ch);
    //if(ch == '.' || c==KeyCode.PERIOD || chOk) {
    Object o=webEngine.executeScript("ace.edit(\"textArea\").getCursorPosition()");
    assert o instanceof JSObject : o.toString();
    JSObject jsobj=(JSObject)o;
    int row=(int)Double.parseDouble(jsobj.getMember("row").toString());
    int col=(int)Double.parseDouble(jsobj.getMember("column").toString());
    try{ displayHint(editor,row,col,ch); }
    catch(Throwable t){t.printStackTrace();}
    }    
  private void handleKeyPress(KeyEvent keyEvent) {
    if(outerPanel==null || !(outerPanel instanceof ReplTextArea)) {return;}
    var c=keyEvent.getCode();
    ReplTextArea editor=((ReplTextArea)outerPanel);
    //---CTRL+S save
    if (keyEvent.isControlDown() && c == KeyCode.S){
      editor.saveToFileAndRemoveStar();
      return;
      }
    if(!c.isArrowKey() && !c.isMediaKey() && !c.isModifierKey()){
      editor.addStar(); //file has been modified (NOT SAVED)
      }//selecting only the digits would, for example, fail to recognize deletion
    }
  private void displayHint(ReplTextArea editor, int row, int col, char last) {
    //row starts from 0 but file lines from 1
    String filename=editor.tabName;
    String parsabletext=FromDotToPath.parsable(editor.getText(),row,col,last);
    ReplMain.gui.hints.setText(GuiData.computeHint(row,col,last,filename,parsabletext));
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