package is.L42.repl;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import is.L42.main.Main;
import is.L42.tools.General;


public class ReplGui extends Application {
  static ReplMain main;
  private static final int SCENE_WIDTH = 1200;
  private static final int SCENE_HEIGHT = 600;
  TabPane tabPane=new TabPane();
  TextArea output=new TextArea();
  TextArea errors=new TextArea();
  TextArea hints=new TextArea();
  DisplayTests tests=new DisplayTests();
  {
    //System.out.println(Font.getFontNames());
    //var monos=Font.getFontNames().stream().filter(s->s.contains("Mono") || s.contains("mono")).findFirst();
    //if(!monos.isEmpty()){
    //var font=Font.font(monos.get());
    //output.setFont(font);
    output.setStyle("-fx-font-family: 'monospaced';");
    errors.setStyle("-fx-font-family: 'monospaced';");
    hints.setStyle("-fx-font-family: 'monospaced';");
    }  
  boolean rootPathSet=false;
  boolean running=false;
  Button runB;
  Button openFileBtn;
  Button refreshB;
  Button loadProjectBtn;
  Button openOverviewBtn;
  Button clearCacheBtn;
  Button newFileBtn;
  Button aboutBtn;
  Stage stage;
  Tab selectedTab=null;
  @SuppressWarnings("unchecked")
  public static <T>T runAndWait(int operations,Function<CountDownLatch,T>task){
    assert !Platform.isFxApplicationThread();
    CountDownLatch latch = new CountDownLatch(operations);
    Object[]res={null};
    Platform.runLater(()->res[0]=task.apply(latch));
    try {latch.await();}
    catch (InterruptedException e) {throw HtmlFx.propagateException(e);}
    return (T)res[0];
    }  
  private void mkLoadProjectBtn(Stage primaryStage){
    loadProjectBtn = new Button("Load Project");
    loadProjectBtn.setOnAction(t->{
      assert Platform.isFxApplicationThread();
      DirectoryChooser directoryChooser = new DirectoryChooser();
      directoryChooser.setTitle("Select an L42 project to load!");
      File outputFolder = directoryChooser.showDialog(primaryStage);
      if(outputFolder==null) {return;} //no selection has been made
      String title="";
      Path p=outputFolder.toPath().toAbsolutePath();
      for(int i :General.range(3)) {
        if(p==null){ break; }
        title=p.getFileName().toString()+(i==0?"":".")+title;
        p=p.getParent();
        }
      primaryStage.setTitle("L42 IDE   "+title);
      running=true;
      runB.setText("Loading");
      openFileBtn.setDisable(false);
      newFileBtn.setDisable(false);
      refreshB.setDisable(false);
      ReplMain.runLater(()->{
        if(rootPathSet) {
          ReplGui.runAndWait(1,l->{closeAllTabs();l.countDown();return null;});
        }
        main.loadProject(outputFolder.toPath());
        });
      });
    }
  private void mkOpenFileBtn(Stage primaryStage){
    openFileBtn=new Button("Open File in Project");
    openFileBtn.setDisable(true);
    openFileBtn.setOnAction(t->{
      assert Platform.isFxApplicationThread();
      FileChooser fileChooser = new FileChooser();
      FileChooser.ExtensionFilter l42Filter = new FileChooser.ExtensionFilter("L42 files (*.L42)", "*.L42");
      fileChooser.getExtensionFilters().add(l42Filter);
      fileChooser.setInitialDirectory(GuiData.l42Root.toFile());
      File chosenFile = fileChooser.showOpenDialog(primaryStage);
      if(chosenFile==null) {return;} //no selection has been made
      ReplMain.runLater(()->main.openFile(chosenFile.toPath()));
      });
    }
  private void mkNewFileBtn(Stage primaryStage){
    newFileBtn=new Button("New File in Project");
    newFileBtn.setDisable(true);
    newFileBtn.setOnAction(t->{
      assert Platform.isFxApplicationThread();
      TextInputDialog dialog = new TextInputDialog("Example1/Example2/Example3");
      dialog.setHeaderText("Insert File Name. End with '/' for a new folder");
      dialog.setTitle("New File in Project");
      dialog.setContentText("name");
      Optional<String> result = dialog.showAndWait();
      if(result.isEmpty()){return;}
      ReplMain.runLater(()->main.makeNewFile(result.get()));
      });
    }
  private void mkRunBtn(Stage primaryStage){
    runB=new Button("Run!");
    runB.setDisable(true);
    runB.setOnAction(e->{
      tests.reset();
      saveAll();
      clearAll();
      assert !running: "was running-buttonRunPressed";
      GuiData.updateSettings();
      disableRunB();
      ReplMain.runLater(()->main.runCode());
      });
    }
  private void clearAll() {
    output.setText("");
    errors.setText("");
    tests.reset();
    }
  private void saveAll() {
    for (Tab t : tabPane.getTabs()) {
      if(t.getText().equals("OVERVIEW")){continue;}
      if(t.getText().equals("OVERVIEW*")){continue;}
      if(t.getText().endsWith("*")){
        ReplTextArea editor = (ReplTextArea)t.getContent();
        editor.saveToFileAndRemoveStar();
        }
      }
    }
  private void mkRefreshBtn(Stage primaryStage){
    refreshB=new Button("Refresh");
    refreshB.setDisable(true);
    refreshB.setOnAction(t->{
      for(Tab tab: tabPane.getTabs()) {
        ((ReplTextArea)tab.getContent()).refresh();
        }
      });
    }
  private void mkOpenOverviewBtn(Stage primaryStage){
    openOverviewBtn=new Button("Overview");
    openOverviewBtn.setOnAction(t->ReplMain.runLater(main::openOverview));
    }
  private void mkClearCacheBtn(Stage primaryStage){
    clearCacheBtn=new Button("Terminate and ClearCache");
    //clearCacheBtn.setOnAction(t->ReplMain.runLater(main::clearCache));
    clearCacheBtn.setOnAction(t->GuiData.terminate42());
    }
  private void mkAboutBtn(Stage primaryStage){
  aboutBtn=new Button("About");
  FlowPane fp = aboutText(newVersion());
  aboutBtn.setOnAction(t->makeDialog("About", fp));
  }
  @Override
  public void start(Stage primaryStage) throws Exception {
    assert Platform.isFxApplicationThread();
    ReplMain.gui=this;
    stage=primaryStage;
    BorderPane borderPane = new BorderPane();
    tabPane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
    tabPane.getSelectionModel().selectedItemProperty().addListener((tab,oldTab,newTab)->selectedTab = newTab);
    mkLoadProjectBtn(primaryStage);
    mkOpenFileBtn(primaryStage);
    mkRunBtn(primaryStage);
    mkRefreshBtn(primaryStage);
    mkOpenOverviewBtn(primaryStage);
    mkClearCacheBtn(primaryStage);
    mkNewFileBtn(primaryStage);
    mkAboutBtn(primaryStage);
    Pane empty=new Pane();
    HBox.setHgrow(empty, Priority.ALWAYS);
    ToolBar toolbar = new ToolBar(
      loadProjectBtn,openFileBtn,refreshB,openOverviewBtn,newFileBtn,aboutBtn,empty,clearCacheBtn,runB);
    borderPane.setTop(toolbar);
    TabPane outputPane = new TabPane();
    outputPane.setSide(Side.LEFT);
    outputPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
    output.setEditable(false);
    errors.setEditable(false);
    hints.setEditable(false);
    outputPane.getTabs().add(new Tab("output", output));//0
    outputPane.getTabs().add(new Tab("errors", errors));//1
    outputPane.getTabs().add(new Tab("hints", hints));//2
    outputPane.getTabs().add(new Tab("OK", tests.passedTests));//3
    outputPane.getTabs().add(new Tab("KO", tests.failedTests));//4
    SplitPane splitPane = new SplitPane(tabPane, outputPane);
    splitPane.setDividerPositions(0.7f);
    splitPane.setOrientation(Orientation.VERTICAL);
    borderPane.setCenter(splitPane);
    Scene scene = new Scene(borderPane, SCENE_WIDTH, SCENE_HEIGHT);
    primaryStage.setTitle("L42 IDE");
    primaryStage.setScene(scene);
    primaryStage.setMinWidth(scene.getWidth()/3);
    primaryStage.setMinHeight(scene.getHeight()/3);
    primaryStage.show();
    ReplMain.runLater(main::eventStart);
  }
  public void selectErr() {
    ((TabPane)ReplMain.gui.errors.getParent().getParent())
      .getSelectionModel().select(1);
    }
  public void selectOut() {
    ((TabPane)ReplMain.gui.output.getParent().getParent())
      .getSelectionModel().select(0);
    }
  public void selectPass() {
    ((TabPane)ReplMain.gui.output.getParent().getParent())
      .getSelectionModel().select(3);
    }
  public void selectFail() {
    ((TabPane)ReplMain.gui.output.getParent().getParent())
      .getSelectionModel().select(4);
    }
  public void origStop() throws Exception {
    this.stage.close();
    super.stop();
  }

  @Override
  public void stop() throws Exception {
    origStop();
    Platform.exit();
    System.exit(0);
    }
  void enableRunB() {
    running=false;
    runB.setDisable(false);
    runB.setText("Run!");
  }
  void disableRunB() {
    running=true;
    runB.setDisable(true);
    runB.setText("Running");
    }
  private void tabOnClose(ReplTextArea editor,Event t) {
    if(!editor.tab.getText().endsWith("*")) {return;}
    if(editor.tab.getText().equals("Overview")) {return;}
    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    alert.getButtonTypes().setAll(ButtonType.NO,ButtonType.CANCEL,ButtonType.YES);
    alert.setTitle("Save file?");
    alert.setHeaderText(null);
    alert.setContentText("Do you want to save \""+editor.filename+"\" before closing?");
    alert.showAndWait().ifPresent(response->{
      if(response == ButtonType.YES) {
        boolean success=editor.saveToFileAndRemoveStar();
        if(!success && t!=null) {t.consume();}
        return;
        }
      if(response == ButtonType.NO) {return;}
      if(response == ButtonType.CANCEL && t!=null){t.consume();}
      });    
    }
  void openTab(ReplTextArea editor, String tabContent) {
    assert Platform.isFxApplicationThread();
    editor.setText(tabContent);
    editor.tab = new Tab();
    editor.tab.setText(editor.filename);
    editor.tab.setContent(editor);
    editor.setFocusTraversable(true);
    editor.htmlFx.setFocusTraversable(true);
    editor.htmlFx.browser.setFocusTraversable(true);
    editor.tab.setOnSelectionChanged (e ->{
      if(editor.tab.isSelected()){
        Platform.runLater(()->editor.htmlFx.browser.requestFocus());
        }
      });  
    editor.tab.setOnCloseRequest(t->this.tabOnClose(editor,t));
    tabPane.getTabs().add(editor.tab);
    tabPane.getSelectionModel().select(editor.tab);
    }
  private void closeAllTabs() {//Ask for confirmation for all unsaved tabs before closing
    assert Platform.isFxApplicationThread();
    for(Tab tab: tabPane.getTabs()) {
      EventHandler<Event> handler=tab.getOnCloseRequest();
      tabPane.getSelectionModel().select(tab);
      if(handler!=null) handler.handle(null);
    }
    tabPane.getTabs().clear();
  }
  void makeAlert(String title, String content) {
    assert Platform.isFxApplicationThread();
    Alert alert = new Alert(AlertType.ERROR);
    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(content);
    alert.showAndWait();
  }
  void makeDialog(String title, FlowPane content) {
    assert Platform.isFxApplicationThread();
    Alert alert = new Alert(AlertType.INFORMATION);
    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.getDialogPane().setContent(content);
    alert.showAndWait();
  }
  private FlowPane aboutText(String nextVersion) {
    FlowPane fp = new FlowPane();
    String content = "L42 Version : " + Main.l42IsRepoVersion + "\n";
    Hyperlink hl = new Hyperlink();
    content +=  nextVersion;
    if(nextVersion == "Update available ") {
      hl.setText("\nhere");
      hl.setOnAction((a)->getHostServices().showDocument("Https://l42.is/tutorial.xhtml#Download"));}
    Label l = new Label(content);
    fp.getChildren().addAll(l,hl);
    return fp;
    }
  private String newVersion() {
    if(Main.l42IsRepoVersion == "testing") { return "";}
    String versionCode;
    String prefix = Main.l42IsRepoVersion.substring(0,1);
    int versionNum = Integer.parseInt(Main.l42IsRepoVersion.substring(1)) + 1;
    if(versionNum >= 100) { versionCode = prefix + versionNum;}
    else { versionCode = prefix + "0" + versionNum;}
    if (newUpdate(versionCode)) { return "Update available ";}
  return "Up to date!";
  }
  private boolean newUpdate(String nextVersion) {
    HttpURLConnection huc = null;
    try {
      URL nextUrl = new URL("https://github.com/Language42/is/blob/main/" + nextVersion);
      huc = (HttpURLConnection) nextUrl.openConnection();
      if(huc.getResponseCode() == 404) { return false;}
    } catch(MalformedURLException e) { return false; }
      catch( IOException e) { return false; }
    finally { if(huc != null) {huc.disconnect();} }
    return true;
  }
  /*
  public static PrintStream delegatePrintStream(StringBuffer err,PrintStream prs){
    return new PrintStream(prs){
      public void print(String s) {
  //      doAndWait(()->{
  //        prs.print(s);
          err.append(s);
  //        });
        super.print(s);
        }
      public void println(String s) {
  //      doAndWait(()->{
          String ss=s+"\n";
  //        prs.println(ss);
          err.append(ss);
  //        });
        super.println(s);
        }
      };
    }
  */
}