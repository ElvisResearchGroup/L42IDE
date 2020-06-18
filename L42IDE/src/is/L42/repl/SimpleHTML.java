package is.L42.repl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Scanner;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
public class SimpleHTML{
  public static String fileContents;
  static{try{
    URI uri = SimpleHTML.class.getResource("textArea.xhtml").toURI();
    fileContents = new Scanner(new File(uri)).useDelimiter("\\Z").next();
    System.out.println("read"+fileContents);
    }
    catch (URISyntaxException | FileNotFoundException e){throw new Error(e);}
    }
  public static void main(String[] args){SwingUtilities.invokeLater(SimpleHTML::new);}
  public SimpleHTML(){
    JEditorPane jEditorPane = new JEditorPane();
    jEditorPane.setEditable(false);
    JScrollPane scrollPane = new JScrollPane(jEditorPane);
    HTMLEditorKit kit = new HTMLEditorKit();
    jEditorPane.setEditorKit(kit);
    StyleSheet styleSheet = kit.getStyleSheet();
    styleSheet.addRule("body {color:#000; font-family:times; margin: 4px; }");
    styleSheet.addRule("h1 {color: blue;}");
    styleSheet.addRule("h2 {color: #ff0000;}");
    styleSheet.addRule("pre {font : 10px monaco; color : black; background-color : #fafafa; }");
    String htmlString = fileContents;/*"""
      <html><body>
      <h1>Welcome!</h1>
      <h2>This is an H2 header</h2>
      <p>This is some sample text</p>
      <p><a href=\"http://devdaily.com/blog/\">devdaily blog</a></p>
      </body>
      """;*/
    Document doc = kit.createDefaultDocument();
    jEditorPane.setDocument(doc);
    jEditorPane.setText(htmlString);
    JFrame j = new JFrame("HtmlEditorKit Test");
    j.getContentPane().add(scrollPane, BorderLayout.CENTER);
    j.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    j.setSize(new Dimension(300,200));
    //j.pack();
    // center the jframe, then make it visible
    j.setLocationRelativeTo(null);
    j.setVisible(true);
    }
  }