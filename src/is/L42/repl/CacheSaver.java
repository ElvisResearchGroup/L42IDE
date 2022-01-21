package is.L42.repl;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import is.L42.top.CachedTop;

public class CacheSaver {
  static ExecutorService executor = Executors.newFixedThreadPool(1);
  static BlockingQueue<CachedTop> queue=new LinkedBlockingQueue<>();
  static {executor.submit(CacheSaver::act);}
  public static void saveCache(CachedTop c){
    try {queue.put(c);}
    catch(InterruptedException e){
      Thread.currentThread().interrupt();
      throw new Error(e);
      }
    }
  private static void act(){
    try {
      while(true){
        CachedTop candidate = queue.take();
        ArrayList<CachedTop> others=new ArrayList<>();
        queue.drainTo(others);
        if(!others.isEmpty()){candidate=others.get(others.size()-1);}
        candidate.saveCache(GuiData.l42Root.inner);
        }//near certain to block as soon as the while repeat.
      }
    catch (InterruptedException e){System.out.println("Cache Saver Thread Interrupted");}
    }//the catch properly terminates the act method.
  }