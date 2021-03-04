package tests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import is.L42.repl.FromDotToPath;

class FromDotToPathTest {

  String prog1="""
      reuse [AdamTowel]
      Main = (
        i = 12I
        Debug(S"hi %i").foo().bar()
        S"hi %i".foo().bar()
        S"".foo()
        S.a()\"""
          |gg
          \""".bar()
        foo[and that is all]
        S"".foo[and that is all]
        12I.mod(3I)
        x=A.foo()
        x+=A.foo()+B.bar()
        x+=A.#foo(\\baz)
        c.addSeed(\\(x=x, y=0\\), val=\\.true())
        )
      """;
  void ok(String p,int row,int col,String expected){
    var res=FromDotToPath.parsable(p,row,col,' ');
    assertEquals(expected,res);
    }
  @Test void numLit(){ok(prog1,2,9,"12I");}
  @Test void numLitSpace(){ok(prog1,2,10,"12I");}
  @Test void halfId1(){ok(prog1,3,3,"D");}
  @Test void halfId2(){ok(prog1,3,4,"De");}
  @Test void idDebug(){ok(prog1,3,7,"Debug");}
  @Test void idR(){ok(prog1,3,8,"");}
  @Test void idS(){ok(prog1,3,9,"S");}
  @Test void idAll(){ok(prog1,3,29,"Debug(S\"hi %i\").foo().bar()");}
  @Test void idStr(){ok(prog1,4,22,"S\"hi %i\".foo().bar()");}
  @Test void idStrEmpty(){ok(prog1,5,11,"S\"\".foo()");}
  @Test void idStrMulti(){ok(prog1,8,14,"S.a()\"\"\"\n    |gg\n    \"\"\".bar()");}
  @Test void idSquare(){ok(prog1,9,22,"foo[and that is all]");}
  @Test void idSquare2(){ok(prog1,10,26,"S\"\".foo[and that is all]");}
  @Test void meth(){ok(prog1,11,13,"12I.mod(3I)");}
  @Test void methEq(){ok(prog1,12,11,"A.foo()");}
  @Test void methPlus(){ok(prog1,13,20,"B.bar()");}
  @Test void hash(){ok(prog1,14,19,"A.#foo(\\baz)");}
  @Test void innerSlash(){ok(prog1,15,39,"c.addSeed(\\(x=x, y=0\\), val=\\.true())");}
  
}
