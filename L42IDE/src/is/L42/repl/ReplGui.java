package is.L42.repl;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import is.L42.platformSpecific.javaTranslation.Resources;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
//import profiling.Timer;

public class ReplGui extends Application {
  static ReplMain main;
  private static final int SCENE_WIDTH = 1200;
  private static final int SCENE_HEIGHT = 600;
  TabPane tabPane=new TabPane();
  TextArea output=new TextArea();
  {Resources.setOutHandler(this::setOut);}
  private void setOut(String s){output.appendText(s);}
  TextArea errors=new TextArea();
  StringBuffer err=new StringBuffer();
  boolean rootPathSet=false;
  boolean running=false;
  Button runB;
  Button openFileBtn;
  Button refreshB;
  Button loadProjectBtn;
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
  @Override
  public void start(Stage primaryStage) throws Exception {
    assert Platform.isFxApplicationThread();
    ReplMain.gui=this;
    stage=primaryStage;
    BorderPane borderPane = new BorderPane();
    tabPane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
    tabPane.getSelectionModel().selectedItemProperty().addListener((tab,oldTab,newTab)->selectedTab = newTab);
    loadProjectBtn = new Button("Load Project");
    loadProjectBtn.setOnAction(t->{
      assert Platform.isFxApplicationThread();
      DirectoryChooser directoryChooser = new DirectoryChooser();
      directoryChooser.setTitle("Select an L42 project to load!");
      File outputFolder = directoryChooser.showDialog(primaryStage);
      if(outputFolder==null) {return;} //no selection has been made
      running=true;
      runB.setText("Loading");
      openFileBtn.setDisable(false);
      refreshB.setDisable(false);
      ReplMain.runLater(()->{
        if(rootPathSet) {
          ReplGui.runAndWait(1,l->{closeAllTabs();l.countDown();return null;});
        }
        main.loadProject(outputFolder.toPath());
      });
    });
    openFileBtn=new Button("Open File in Project");
    openFileBtn.setDisable(true);
    openFileBtn.setOnAction(t->{
      assert Platform.isFxApplicationThread();
      FileChooser fileChooser = new FileChooser();
      FileChooser.ExtensionFilter l42Filter = new FileChooser.ExtensionFilter("L42 files (*.L42)", "*.L42");
      fileChooser.getExtensionFilters().add(l42Filter);
      fileChooser.setInitialDirectory(ReplMain.l42Root.toFile());
      File chosenFile = fileChooser.showOpenDialog(primaryStage);
      if(chosenFile==null) {return;} //no selection has been made
      ReplMain.runLater(()->main.openFile(chosenFile.toPath()));
      });
    runB=new Button("Run!");
    runB.setDisable(true);
    runB.setOnAction(e->{
      for (Tab t : tabPane.getTabs()) {
        if (t.getText().endsWith("*")){
          System.out.println("Saving: " + t.getText());
          ReplTextArea editor = (ReplTextArea)t.getContent();
          editor.saveToFile();
          editor.removeStar();
          }
        }
      assert !running: "was running-buttonRunPressed";
      disableRunB();
      ReplMain.runLater(()->main.runCode());
      });
    refreshB=new Button("Refresh");
    refreshB.setDisable(true);
    refreshB.setOnAction(t->{
      for(Tab tab: tabPane.getTabs()) {
        ((ReplTextArea)tab.getContent()).refresh();
      }
    });
    Pane empty=new Pane();
    HBox.setHgrow(empty, Priority.ALWAYS);
    ToolBar toolbar = new ToolBar(loadProjectBtn, openFileBtn, refreshB, empty, runB);
    borderPane.setTop(toolbar);
    //System.setOut(delegatePrintStream(out,System.out));
    System.setErr(delegatePrintStream(err,System.err));
    TabPane outputPane = new TabPane();
    outputPane.setSide(Side.LEFT);
    outputPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
    output.setEditable(false);
    errors.setEditable(false);
    outputPane.getTabs().add(new Tab("output", output));
    outputPane.getTabs().add(new Tab("errors", errors));
    SplitPane splitPane = new SplitPane(tabPane, outputPane);
    splitPane.setDividerPositions(0.7f);
    splitPane.setOrientation(Orientation.VERTICAL);
    borderPane.setCenter(splitPane);
    Scene scene = new Scene(borderPane, SCENE_WIDTH, SCENE_HEIGHT);
    primaryStage.setTitle("L42 IDE");
    primaryStage.setScene(scene);
    primaryStage.setMinWidth(scene.getWidth());
    primaryStage.setMinHeight(scene.getHeight());
    primaryStage.show();
    ReplMain.runLater(main::eventStart);
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
    System.out.println("Finished");
  }
  void disableRunB() {
    running=true;
    runB.setDisable(true);
    runB.setText("Running");
    }
  private void tabOnClose(ReplTextArea editor,Event t) {
    if(!editor.tab.getText().endsWith("*")) {return;}
    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    alert.getButtonTypes().setAll(ButtonType.NO,ButtonType.CANCEL,ButtonType.YES);
    alert.setTitle("Save file?");
    alert.setHeaderText(null);
    alert.setContentText("Do you want to save \""+editor.filename+"\" before closing?");
    alert.showAndWait().ifPresent(response->{
      if(response == ButtonType.YES) {editor.saveToFile();return;}
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

  void updateTextFields(){//TODO: bad name; also, now out is handled separatly.
    try{
      assert err!=null:"a";
      assert errors!=null:"b";
      String newErr=err.toString();
      errors.setText(newErr);
      }
    finally{
      this.enableRunB();
      }
    }

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
  }
