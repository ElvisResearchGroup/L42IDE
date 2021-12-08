package is.L42.repl;

import java.util.List;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.control.ScrollPane;

public class DisplayTests {
  public static boolean isPass(String s){
    String start="###################\n#Pass";
    return s.startsWith(start);
    }
  TextFlow _passedTests=new TextFlow();
  TextFlow _failedTests=new TextFlow();
  ScrollPane passedTests=new ScrollPane(_passedTests);
  ScrollPane failedTests=new ScrollPane(_failedTests);
  String green="-fx-fill: GREEN;-fx-font-weight:bold;";
  String red="-fx-fill: RED;-fx-font-weight:bold;";
  String black="-fx-fill: Black;-fx-font-weight:normal;";
  String mono="-fx-font-family: 'monospaced';";
  String sep="#########################\n";
  int tests=0;
  int passed=0;
  int failed=0;
  Text failText = new Text();
  Text passText = new Text();
  Text passSplit(){
    Text res = new Text(sep);
    res.setStyle(green);
    return res;
    }
  Text failSplit(){
    Text res = new Text(sep);
    res.setStyle(red);
    return res;
    }
  DisplayTests(){
    _passedTests.setStyle(mono);
    _failedTests.setStyle(mono);
    passText.setStyle(green);
    failText.setStyle(red);
    _passedTests.getChildren().addAll(passText);
    _failedTests.getChildren().addAll(failText);
    }
  void reset(){
    this.passed=0;
    this.failed=0;
    this.tests=0;
    _passedTests.getChildren().clear();
    _failedTests.getChildren().clear();
    failText.setText("No tests yet.");
    passText.setText("No tests yet.");
    _passedTests.getChildren().addAll(passText);
    _failedTests.getChildren().addAll(failText);
    }
  void handle(List<String> ss){ for(String s : ss){handle(s);} }
  void handle(String s){
    tests+=1;
    var pass=isPass(s);
    if(pass) {passed+=1;} else {failed+=1;}
    failText.setText("Failed ("+failed+" of "+tests+")\n");
    passText.setText("Passed ("+passed+" of "+tests+")\n");
    
    Text newS = new Text();
    newS.setStyle("-fx-fill: BLACK;-fx-font-weight:normal;");
    newS.setText(limitTestSize(cropFirstLine(s)));
    if(pass) {_passedTests.getChildren().addAll(passSplit(),newS);}
    else {_failedTests.getChildren().addAll(failSplit(),newS);}
    }
  String cropFirstLine(String txt){
    int i=txt.indexOf("\n");
    if(i==-1) {return txt;}
    return txt.substring(i+1);
    }
  String limitTestSize(String txt) {
    if(txt.length() < GuiData.maxTest ) { return txt; }
    StringBuilder sb = new StringBuilder();
    sb.append("**Test size limit exceeded. Tail of test was removed**<|");
    sb.append(txt,0,GuiData.maxTest);
    return sb.toString();
    }
  }