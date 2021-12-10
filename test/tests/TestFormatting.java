package tests;

import static is.L42.tools.General.L;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import is.L42.common.Constants;
import is.L42.introspection.OverviewVisitor;
import is.L42.platformSpecific.javaTranslation.Resources;
import is.L42.top.CachedTop;
import is.L42.top.Init;

public class TestFormatting {
  void test(String source,String expected){
    Resources.clearResKeepReuse();
    Constants.testWithNoUpdatePopChecks(()->{
      var res=Init.topCache(new CachedTop(L(),L()),source);
      var v=new OverviewVisitor();
      res.accept(v);
      assertEquals(expected,v.result().toString());
      });
    }
  @Test void test1(){test("""
    {
    @{hi} method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        }method Void foo()
      }     
    """);}@Test void test2(){test("""
    {
    @{hi
      }method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        }method Void foo()
      }     
    """);}@Test void test3(){test("""
    {
    @{hi
    another line
    }method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        another line
        }method Void foo()
      }     
    """);}@Test void test4(){test("""
    {
    @{hi
    another line
    another line2
    }method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        another line
        another line2
        }method Void foo()
      }     
    """);}@Test void test5(){test("""
    {
    @{hi
    another line
      another line2
    another line3
    }method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        another line
          another line2
        another line3
        }method Void foo()
      }
    """);}@Test void test6(){test("""
    {
    @{
    hi
    another line
      another line2
    another line3
    }method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        another line
          another line2
        another line3
        }method Void foo()
      }
    """);}@Test void test7(){test("""
    {
    @{
    hi
    another line @This bla
      another line2
    another line3
    }method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        another line @This bla
          another line2
        another line3
        }method Void foo()
      }
    """);}@Test void test8(){test("""
    {
    @{
    hi
    another line @This bla
      another line2 @Library   @Void
        another@Void line3
    @This
    }method Void foo()
    }
    ""","""
    {
      foo()                              @{
        hi
        another line @This bla
          another line2 @Library   @Void
            another@Void line3
        @This
        }method Void foo()
      }
    """);}@Test void test9(){test("""
    {
    @{ This is a line
    hi
    another line @This bla
      another line2 @Library   @Void
        another@Void line3
    @This
    }method Void foo()
    }
    ""","""
    {
      foo()                              @{
         This is a line
        hi
        another line @This bla
          another line2 @Library   @Void
            another@Void line3
        @This
        }method Void foo()
      }
    """);}
  }