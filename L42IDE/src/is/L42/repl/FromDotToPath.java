package is.L42.repl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import is.L42.common.Constants;
import is.L42.common.Parse;
import is.L42.generated.C;
import is.L42.generated.S;

public class FromDotToPath {
  public static String parsable(String text, int row, int col,char last) {
    int size=0;
    for(int ri=0;ri<row;ri+=1){
      size=text.indexOf("\n", size+1);
      }
    size+=col+1;
    int min=Math.max(0,size-500);//500 limiting to 500 chars for now
    String before=(text.substring(min, size)+last).trim();
    if(before.endsWith(".")) {before=before.substring(0,before.length()-1);}
    return new StartParsable().of(before);
    }
  static class StartParsable{
    int index;
    String s;
    String of(String s){
      this.s=s;
      index=s.length();
      all();
      return s.substring(index,s.length()).trim(); 
      }
    void all() { while(step()>=0); }
    int skipString(){
      while (true) {
        char ci = s.charAt(index-=1);
        if(ci=='\"' || index<=0){return 0;}
        }
      }
    int skipPar(char endPar){
      index+=1;
      while (true) {
        char ci = s.charAt(index-=1);
        if(ci=='\"') {skipString();}
        if(ci==endPar){break;}
        if(index<=0){break;}
        }
      skipSpaces();
      return index-=1;
      }
    int skipSpaces(){
      index+=1;
      while (true) {
        char ci = s.charAt(index-=1);
        if(ci!=' ' && ci!=',' && ci!='\n' && ci!='\r'){return 0;}
        if(index<=0){return 0;}
        }
      }
    int base(char ci) {
      if(FromDotToPath.isValidPathChar(ci)){return 0;}
      index+=1;
      return -1;
      }
    int step() {
      if(index<=0) {return -1;}
      char ci = s.charAt(index-=1);
      return switch(ci){
        case ')'->skipPar('(');
        case ']'->skipPar('[');
        case '}'->skipPar('{');
        case '\"'->skipString();
        case '.'->skipSpaces();
        case '(','[','{'->{index+=1;yield -1;}
        default -> base(ci);
        };
      }
    public String toString() {return index+"    "+s.substring(0,index);}
    }
  
  static char toChar(int codePoint) {
    char[]cs=Character.toChars(codePoint);
    if(cs.length!=1) {throw new IllegalArgumentException();}
    return cs[0];
  }


  public static boolean isValidPathChar(char c) {
    if(c == '$'){return true;}
    if(c == '_'){return true;}
    if(c == '\t'){return false;}
    return Character.isUpperCase(c) || Character.isLowerCase(c) || Character.isDigit(c);
  }
  public static boolean isValidOuter(String name) {//thus invalid as pathName
    if (name.equals("This")||name.equals("This0")){return true;}
    if (!name.startsWith("This")){return false;}
    int firstN = "This".length();
    char c = name.charAt(firstN);
    // first is 1--9 and all rest is 0-9
    if("123456789".indexOf(c) == -1){return false;}
    for(int i = firstN + 1; i < name.length(); i++){
      if("0123456789".indexOf(name.charAt(i)) == -1){return false;}
      }
    return true;
    }
  public static boolean checkX(String s, boolean allowHash){
    if(s.isEmpty()){return false;}
    char c0 = s.charAt(0);
    if(allowHash && c0 == '#'){
      if (s.length() == 1){return false;}
      char c1 = s.charAt(1);
      if (c1 == '#'){return false;}
      }
    for(char c : s.toCharArray()){
      if(allowHash && c == '#'){continue;}
      if(isValidPathChar(c)){continue;}
      return false;
      }
    return c0 == '_' || c0 == '#' || (c0 >= 'a' && c0 <= 'z');
    }
  public static boolean isValidPathStart(char c) {
    if (c == '$' || c == '_'){return true;}
    return Character.isUpperCase(c);
    }
  public static int getThisn(String that) {
    that = that.substring("This".length());
    if (that.isEmpty()) {return 0;}
    return Integer.parseInt(that);
    }
  public static boolean isValidPrimitiveName(String name){
    return List.of("Any","Library","Void").contains(name);
    }
  public static boolean isValidClassName(String name) {
    if(name.isEmpty()){return false;}
    if(isValidOuter(name)){return false;}
    if(isValidPrimitiveName(name)){return false;}
    if(!isValidPathStart(name.charAt(0))){return false;}
    for(int i = 1; i < name.length(); i++){
      if(!isValidPathChar(name.charAt(i))){return false;}
      }
    return true;
    }
  }