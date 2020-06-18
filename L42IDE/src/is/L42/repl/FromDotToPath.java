package is.L42.repl;

import static is.L42.tools.General.L;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import is.L42.common.Constants;
import is.L42.common.Parse;
import is.L42.generated.C;
import is.L42.generated.P;
import is.L42.generated.S;

public class FromDotToPath {
  int thisNum=-1;
  StringBuffer pathString=new StringBuffer();
  List<C> cs;
  List<S> ms=new ArrayList<>();
  public FromDotToPath(String text, int row, int col) {
    String[] lines=text.split("\\r?\\n");
    String lineBefore=lines[row].substring(0, col);
    String reverseLineBefore=reverse(lineBefore);
    String reverseAndReplaced=swapParenthesis(reverseLineBefore);
    parse(reverseAndReplaced);
    if(pathString.length()==0) {throw new IllegalArgumentException();}
    var pathText=pathString.substring(0, pathString.length()-1).toString();
    var csP = Parse.csP(Constants.dummy, pathText);
    assert !csP.hasErr();
    assert csP.res._p()==null;
    cs=csP.res.cs();
    }

  private void parse(String input) {
    String[] tokens=input.split("\\.");
    for(String token : tokens) {
      if(!parseToken(token)) {break;}
    }
  }

  private boolean parseToken(String token) {
    if(token.startsWith("(")){
      parseMCall(token);
      return true;
      }
    StringBuffer tb=new StringBuffer();
    for(int i:token.codePoints().boxed().collect(Collectors.toList())) {
      char c=toChar(i);
      if(isValidPathChar(c)|| c=='#') {tb.appendCodePoint(i);}
      else {break;}
    }
    boolean unskipped=token.length()==tb.length();
    token=tb.reverse().toString();
    if(token.isEmpty()) {return false;}//and stop processing the for
    char c=toChar(token.codePointAt(0));
    boolean down=checkX(""+c,true);
    boolean out=isValidOuter(token);
    boolean up=!out && isValidPathStart(c);
    assert boolToInt(up) + boolToInt(down) + boolToInt(out) == 1;
    if(out) { parseThis(token); return unskipped; }
    if(up) { parsePath(token); return unskipped; }
    if(down) { parseX(token); return unskipped; }
    throw new IllegalArgumentException();
    }
  private int boolToInt(boolean bool) {return bool ? 1 : 0;}

  private void parseMCall(String token) {
    String noFirstChar=token.substring(1, token.length());
    String methRev=noFirstChar.substring(skipArgs(noFirstChar), noFirstChar.length());
    String methName=reverse(methRev);
    String mArgs=noFirstChar.substring(0, noFirstChar.length()-(methName.length()+1));
    ParseMArg mArg=new ParseMArg(mArgs);
    boolean isM=checkX(methName,true);
    if(!isM) {throw new IllegalArgumentException();}
    String args=mArg.xs.toString().replace(" ", "");
    args=args.substring(1, args.length()-1);
    ms.add(0, S.parse(methName+"("+args+")"));
    }
  static int skipArgs(String noFirstChar){return 0;}//TODO:
  /*static int skipArgs(String noFirstChar) {
    try{
      Parser.checkForBalancedParenthesis(noFirstChar);
    } catch(ErrorMessage.UnclosedParenthesis e) {
      System.out.println(e.getClass().getSimpleName());
      System.out.println(e.getPos());
    } catch(ErrorMessage.UnclosedStringLiteral e) {
      System.out.println(e.getClass().getSimpleName());
      System.out.println(e.getPos());
    } catch(ErrorMessage.UnopenedParenthesis e) {
      System.out.println(e.getClass().getSimpleName());
      System.out.println(e.getPos());
      return e.getPos();
    } catch(ErrorMessage.ParenthesisMismatchRange e) {
      System.out.println(e.getClass().getSimpleName());
      System.out.println("Pos1: "+e.getPos1()+" Pos2: "+e.getPos2());
    }
    throw new IllegalArgumentException();
  }*/

  private void parseThis(String token) {
    if(thisNum!=-1){throw new IllegalArgumentException();}
    thisNum=getThisn(token);
  }

  private void parsePath(String token) {
    StringBuffer b=new StringBuffer(token);
    //for(int i:token.codePoints().boxed().collect(Collectors.toList())) {
    //  char c=toChar(i);
    //  if(PathAux.isValidPathChar(c)) {b.appendCodePoint(i);}
    //  else {break;}
    //}
    //b=b.reverse();
    if(!isValidClassName(b.toString())) {throw new IllegalArgumentException();}
    b.append(".");
    pathString=b.append(pathString);


  }

  private void parseX(String token) {
    // TODO Auto-generated method stub
    throw new IllegalArgumentException();
  }

  static char toChar(int codePoint) {
    char[]cs=Character.toChars(codePoint);
    if(cs.length!=1) {throw new IllegalArgumentException();}
    return cs[0];
  }

  static String reverse(String input) {
    return new StringBuilder(input).reverse().toString();
  }

  static String swapParenthesis(String input) {
    StringBuffer b=new StringBuffer();
    input.codePoints().forEachOrdered(i->{
      if(i=='{') {b.append("}");}
      else if(i=='}'){b.append("{");}
      else if(i=='['){b.append("]");}
      else if(i==']'){b.append("[");}
      else if(i=='('){b.append(")");}
      else if(i==')'){b.append("(");}
      else {b.appendCodePoint(i);}
    });
    return b.toString();
  }
  public static boolean isValidPathChar(char c) {
    //not any more if(c == '%'){return true;}
    if(c == '$'){return true;}
    if (c == '_') {return true;}
    assert c != '\t' : c;
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