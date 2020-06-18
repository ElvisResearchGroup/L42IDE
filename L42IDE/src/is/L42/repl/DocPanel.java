package is.L42.repl;

import static is.L42.tools.General.L;

import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.event.HyperlinkEvent.EventType;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Callback;
import javafx.util.Duration;
import is.L42.generated.Core;
import is.L42.generated.Full;
import is.L42.generated.LL;
import is.L42.generated.P;
class Cell{
  String label;
  String tooltip;
  Cell(Core.L.MWT mwt){
    tooltip=mwt.docs().toString();
    mwt=mwt.with_e(null);
    mwt=mwt.withDocs(L());
    label=mwt.toString();
    label=label.replaceAll("\\n", " ");
  }
  Cell(Core.L.NC nc){
    label=nc.key().toString();
    tooltip=nc.docs().toString();
    }
}
public class DocPanel extends VBox{
  ObservableList<Cell> ms;
  ObservableList<Cell> ns;
  Consumer<Object>refresh;
  Label label=new Label("------");
  private Node content() {
    Callback<ListView<Cell>, ListCell<Cell>> f=l->new ListCell<Cell>() {
      @Override public void updateItem(Cell c, boolean empty) {
        super.updateItem(c, empty);
        if(c==null) {return;}
        setText(c.label);
        DocPanel.setTooltip(this,c.tooltip);
        }
      };

    ListView<Cell> methods = new ListView<>();
    ms =FXCollections.observableArrayList();
    methods.setItems(ms);
    methods.setCellFactory(f);
    methods.setCacheShape(false);

    ListView<Cell> nesteds = new ListView<>();
    ns =FXCollections.observableArrayList();
    nesteds.setItems(ns);
    nesteds.setCellFactory(f);
    nesteds.setCacheShape(false);

    TitledPane msTP = new TitledPane("Methods",methods);
    TitledPane nsTP = new TitledPane("Nesteds",nesteds);
    Accordion acc=new Accordion(msTP,nsTP);
    refresh=o->Platform.runLater(()->{
      msTP.setAnimated(false);
      nsTP.setAnimated(false);
      msTP.setExpanded(false);
      nsTP.setExpanded(false);
      Timeline timeline = new Timeline(new KeyFrame(
          Duration.millis(150),
          ae ->{
            msTP.setExpanded(true);
            msTP.setAnimated(true);
            nsTP.setAnimated(true);
          }));
      timeline.play();
      });
    return acc;
  }
  public DocPanel() {
    super();
    //label.setFont(Font.font(label.getFont().getSize()*2));
    //grows also the font of the tooltip??
    this.getChildren().add(label);
    this.getChildren().add(content());
    }
  public void setCore(Core.L cb){
    for(Core.L.MWT mwt:cb.mwts()){ms.add(new Cell(mwt));}
    for(Core.L.NC nc:cb.ncs()){ns.add(new Cell(nc));}
    setTooltip(label,"Supertypes: "+cb.ts()+"\n"+cb.docs());
    }
  public void setFull(Full.L cb){
    for(var m:cb.ms()){
      //TODO:
      }
    setTooltip(label,"Supertypes: "+cb.ts()+"\n"+cb.docs());
    }
  public void setLL(P name,LL cb) {
    this.label.setText(name.toString());
    ms.clear();
    ns.clear();
    if(cb.isFullL()){setFull((Full.L)cb);}
    else{setCore((Core.L)cb);}
    refresh.accept(null);
  }

  public static void setTooltip(Node n,String text) {
    Tooltip t = new Tooltip(text);
    n.setOnMouseEntered(event->{
      Bounds lb = n.getLayoutBounds();
      Point2D p = n.localToScreen(lb.getMaxX(),lb.getMaxY());
      t.show(n, p.getX(), p.getY());
      });
    n.setOnMouseExited(event->t.hide());
    }
  }
