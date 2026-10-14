package com.example.markdowndesk;

import java.util.*;
import java.util.concurrent.*;

/** Delivery retention checks against the actual production helper and counter. */
public final class StatisticsDeliveryCheck {
    static int checks;
    static void eq(Object expected,Object actual,String message) {
        checks++; if (!Objects.equals(expected,actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void drain() { while(!tasks.isEmpty()) tasks.remove().run(); }
    }
    static void heldDelivery() {
        Queue worker=new Queue(), main=new Queue(); List<LatestStatistics.Result> out=new ArrayList<>();
        LatestStatistics stats=new LatestStatistics(worker,main,out::add);
        for(int i=0;i<1000;i++) { stats.request("A",i,"one two " + i,Locale.US); worker.drain(); }
        eq(1,main.tasks.size(),"held main queue must retain only one completion");
        eq(0,worker.tasks.size(),"held delivery owns active slot");
        main.drain(); eq(0,out.size(),"superseded held result rejected"); eq(1,worker.tasks.size(),"only latest pending launches");
        worker.drain(); main.drain(); eq(1,out.size(),"one fresh result"); eq(999L,out.get(0).revision,"latest revision");
        eq(3,out.get(0).words,"latest count"); stats.close();
    }
    static void invalidationAndClose() {
        Queue worker=new Queue(), main=new Queue(); List<LatestStatistics.Result> out=new ArrayList<>();
        LatestStatistics stats=new LatestStatistics(worker,main,out::add);
        stats.request("A",1,"old",Locale.US); worker.drain();
        stats.request("B",1,"middle",Locale.FRANCE); stats.request("A",1,"new words",Locale.US);
        main.drain(); worker.drain(); main.drain();
        eq(1,out.size(),"A B A generations reject old equal key"); eq(2,out.get(0).words,"fresh A counts");
        stats.request("A",2,"obsolete",Locale.US); worker.drain();
        stats.request("B",3,"pending obsolete",Locale.US); stats.invalidate();
        main.drain(); worker.drain(); eq(1,out.size(),"invalidate drops posted and pending");
        stats.request("A",4,"before worker",Locale.US); stats.invalidate(); worker.drain();
        eq(0,main.tasks.size(),"invalidate before worker produces no callback");
        stats.request("A",5,"after invalidation",Locale.US); worker.drain();
        stats.request("B",6,"close pending",Locale.US); stats.close(); main.drain(); worker.drain();
        eq(1,out.size(),"close drops held and pending");
        stats.request("A",7,"after close",Locale.US); eq(0,worker.tasks.size(),"close rejects new admission");
    }

    static void listenerReentrancy() {
        Queue worker=new Queue(), main=new Queue(); List<Long> out=new ArrayList<>();
        LatestStatistics[] holder=new LatestStatistics[1];
        holder[0]=new LatestStatistics(worker,main,result -> {
            out.add(result.revision);
            if(result.revision==1) {
                holder[0].request("next",2,"one two",Locale.FRANCE);
                throw new IllegalStateException("fixture listener failure");
            }
        });
        holder[0].request("first",1,"one",Locale.US); worker.drain();
        try { main.drain(); throw new AssertionError("listener failure missing"); }
        catch(IllegalStateException expected) { eq("fixture listener failure",expected.getMessage(),"listener exception propagated"); }
        eq(1,worker.tasks.size(),"listener finally starts reentrant latest");
        worker.drain(); main.drain(); eq(List.of(1L,2L),out,"listener reentrancy preserved"); holder[0].close();
        List<Long> direct=new ArrayList<>(); LatestStatistics[] inline=new LatestStatistics[1];
        inline[0]=new LatestStatistics(Runnable::run,Runnable::run,result -> {
            direct.add(result.revision);
            if(result.revision==1) inline[0].request("second",2,"two words",Locale.JAPAN);
        });
        inline[0].request("first",1,"word",Locale.US);
        eq(List.of(1L,2L),direct,"direct executors support listener replacement"); inline[0].close();
    }

    static void failures() {
        Queue worker=new Queue(), main=new Queue(); List<LatestStatistics.Result> out=new ArrayList<>();
        LatestStatistics stats=new LatestStatistics(worker,main,out::add);
        stats.request("bad locale",1,"one",null); worker.drain();
        eq(0,main.tasks.size(),"calculation RuntimeException posts nothing");
        stats.request("valid",2,"one two",Locale.US); worker.drain(); main.drain();
        eq(1,out.size(),"calculation failure releases active slot"); eq(2,out.get(0).words,"post-failure exact count"); stats.close();
        Executor rejected=task -> { throw new RejectedExecutionException("fixture rejected"); };
        LatestStatistics badWorker=new LatestStatistics(rejected,main,out::add);
        badWorker.request("bad",3,"one",Locale.US); badWorker.request("closed",4,"one",Locale.US);
        eq(0,main.tasks.size(),"worker rejection has no delivery");
        LatestStatistics badMain=new LatestStatistics(worker,rejected,out::add);
        badMain.request("bad",5,"one",Locale.US); worker.drain();
        badMain.request("closed",6,"one",Locale.US); eq(0,worker.tasks.size(),"main rejection closes admission");
    }

    static void boundedPendingMemory() {
        byte[] headroom=new byte[32*1024*1024]; headroom[0]=7;
        Queue worker=new Queue(), main=new Queue();
        LatestStatistics stats=new LatestStatistics(worker,main,result -> { throw new AssertionError("stale held result"); });
        stats.request("active",0,"small counts",Locale.US); worker.drain();
        for(int i=1;i<=32;i++) {
            char[] chars=new char[8*1024*1024]; Arrays.fill(chars,(char)('a'+i%26));
            stats.request("pending",i,new String(chars),Locale.US); worker.drain();
        }
        eq(1,main.tasks.size(),"32large replacements keep one callback");
        eq(0,worker.tasks.size(),"32large replacements do not enqueue scans");
        stats.close(); main.drain(); eq(7,(int)headroom[0],"32MiB headroom retained");
    }

    static void closeDuringSubmission() throws Exception {
        ExecutorService caller=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        Queue worker=new Queue(), main=new Queue(); List<LatestStatistics.Result> out=new ArrayList<>();
        Executor blocked=task -> {
            entered.countDown();
            try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("submission latch timeout"); }
            catch(InterruptedException failure) { throw new AssertionError(failure); }
            worker.execute(task);
        };
        LatestStatistics stats=new LatestStatistics(blocked,main,out::add);
        try {
            Future<?> request=caller.submit(() -> stats.request("A",1,"word",Locale.US));
            eq(true,entered.await(5,TimeUnit.SECONDS),"submission entered");
            stats.close(); release.countDown(); request.get(5,TimeUnit.SECONDS);
            worker.drain(); main.drain(); eq(0,out.size(),"close racing blocked submission drops input");
            eq(0,main.tasks.size(),"closed submitted worker never posts");
        } finally { release.countDown(); stats.close(); caller.shutdownNow(); eq(true,caller.awaitTermination(5,TimeUnit.SECONDS),"submission test terminates"); }
    }

    public static void main(String[] args) throws Exception {
        heldDelivery(); invalidationAndClose(); listenerReentrancy(); failures(); boundedPendingMemory(); closeDuringSubmission();
        System.out.println("PASS " + checks + " statistics delivery assertions");
    }
}
