package com.example.markdowndesk;

import java.io.*;
import java.lang.reflect.*;

/** Tests private lexical guards through reflection; no production callback seam. */
public final class WriterSnapshotScopeCheck {
    static int checks;
    interface Work {void run()throws Exception;}
    interface Action {void run(Object fresh)throws Exception;}
    static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void no(Work work,String why)throws Exception {
        try{work.run();throw new AssertionError("accepted "+why);}catch(IOException expected){checks++;}
    }
    static Object field(Object owner,String name)throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    static Object invoke(Method method,Object owner,Object... arguments)throws Exception {
        try{return method.invoke(owner,arguments);}catch(InvocationTargetException failure){
            Throwable problem=failure.getCause();if(problem instanceof Exception)throw (Exception)problem;throw (Error)problem;
        }
    }
    static Method scopeMethod()throws Exception {
        for(Method method:PrivateCaptureWriter.class.getDeclaredMethods())if(method.getName().equals("withChecked")&&method.getParameterCount()==1){method.setAccessible(true);return method;}
        throw new AssertionError("missing private withChecked scope operation");
    }
    static void scope(PrivateCaptureWriter writer,Action action)throws Exception {
        Method method=scopeMethod();Object callback=action==null?null:Proxy.newProxyInstance(WriterSnapshotScopeCheck.class.getClassLoader(),new Class<?>[]{method.getParameterTypes()[0]},
            (proxy,called,args)->{action.run(args[0]);return null;});
        invoke(method,writer,callback);
    }
    static Object access(Object fresh,String name)throws Exception {
        Method method=fresh.getClass().getDeclaredMethod(name);method.setAccessible(true);return invoke(method,fresh);
    }
    static void cleared(PrivateCaptureWriter writer,Object fresh)throws Exception {
        yes(field(writer,"activeSnapshot")==null,"writer released active snapshot");
        yes(field(fresh,"control")==null&&field(fresh,"ledger")==null,"whole control/ledger references cleared");
        no(()->access(fresh,"control"),"expired control access");no(()->access(fresh,"ledger"),"expired ledger access");
    }
    static void locked(PreparationPublicationCheck.Capture capture,Work work)throws Exception {
        synchronized(HistoryStorage.monitorFor(capture.f.store.storageRoot())){
            try(HistoryStorage.MutationScope lease=HistoryStorage.administration(capture.f.store.storageRoot())){work.run();}
        }
    }
    public static void main(String[] args)throws Exception {
        scopeMethod(); // Explicit pre-implementation failure before fixture work.
        PreparationPublicationCheck.Capture capture=new PreparationPublicationCheck.Capture();
        HistoryPinsCheck.Disk disk=new HistoryPinsCheck.Disk();PrivateCaptureWriter writer=capture.publish(disk);
        Object[] escaped={null};
        locked(capture,()->{
            no(()->scope(writer,null),"null scoped action");
            scope(writer,fresh->{
                escaped[0]=fresh;yes(access(fresh,"control")!=null&&access(fresh,"ledger")!=null,"live authentic snapshots available");
                no(()->scope(writer,inner->{}),"nested snapshot scope");
                yes(access(fresh,"control")!=null&&access(fresh,"ledger")!=null,"nested rejection preserves outer scope");
                Throwable[] failure={null};Thread foreign=new Thread(()->{
                    try{access(fresh,"control");failure[0]=new AssertionError("foreign thread read DTO");}
                    catch(IOException expected){}catch(Throwable problem){failure[0]=problem;}
                });foreign.start();foreign.join();yes(failure[0]==null,"foreign thread refused without blocking owner");
            });
            cleared(writer,escaped[0]);
        });
        for(String kind:new String[]{"io","runtime","error"}){
            locked(capture,()->{
                try{scope(writer,fresh->{escaped[0]=fresh;if(kind.equals("io"))throw new IOException("scope action");if(kind.equals("runtime"))throw new IllegalStateException("scope action");throw new AssertionError("scope action");});throw new AssertionError("action failure swallowed");}
                catch(IOException|IllegalStateException expected){yes(!kind.equals("error"),"expected checked/runtime action exception");}
                catch(AssertionError expected){yes(kind.equals("error")&&"scope action".equals(expected.getMessage()),"expected Error action exception");}
                cleared(writer,escaped[0]);scope(writer,fresh->yes(access(fresh,"control")!=null,"scope retry after action failure"));
            });
        }
        File root=capture.f.store.storageRoot();Object history=HistoryStorage.monitorFor(root);
        synchronized(history){
            HistoryStorage.MutationScope initial=HistoryStorage.administration(root);
            try{
                scope(writer,fresh->{
                    escaped[0]=fresh;initial.close();
                    try(HistoryStorage.MutationScope replacement=HistoryStorage.administration(root)){
                        no(()->access(fresh,"control"),"replacement exact lease control");no(()->access(fresh,"ledger"),"replacement exact lease ledger");
                    }
                });
            }finally{initial.close();}
            cleared(writer,escaped[0]);
            try(HistoryStorage.MutationScope retry=HistoryStorage.administration(root)){scope(writer,fresh->yes(access(fresh,"ledger")!=null,"ordinary lease retry after stale scope"));}
        }
        final int[] callbacks={0};
        disk.hook=(event,file)->{try{yes(field(writer,"activeSnapshot")==null,"no snapshot during durability "+event);callbacks[0]++;}catch(Exception problem){if(problem instanceof IOException)throw (IOException)problem;throw new IOException(problem);}};
        locked(capture,writer::writeNativeLocked);
        yes(callbacks[0]>0,"ordinary writer retry reaches real callbacks after scope failures");
        yes(field(writer,"activeSnapshot")==null,"writer returns without whole snapshots");
        System.out.println("Writer snapshot scopes: "+checks+" checks PASS; lexical lifetime only, no portable funding/activation claim");
    }
}
