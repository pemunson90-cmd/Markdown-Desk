package com.example.markdowndesk;

import android.os.CancellationSignal;
import android.os.Looper;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Process-wide admission for read-only folder browsing, independent of save/session IO.
 * Main-thread admission/cancel/delivery; one physical query or undelivered Page total.
 * A provider may ignore cancellation indefinitely: cancellation is never slot disposal.
 */
final class WorkspaceDirectoryLoader {
    private static final Object guard=new Object();
    private static final ExecutorService worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1),task->new Thread(task,"MarkdownDesk-workspace"));
    private static final WorkspaceTreeReader.Limits limits=WorkspaceTreeReader.Limits.defaults();
    private static Ticket owner;

    private WorkspaceDirectoryLoader(){}

    /**
     * Null means BUSY, with no retained request or queued replacement. Reader must use
     * an application ContentResolver, not an Activity-bound resolver. mainDelivery must
     * be a nonblocking main Handler.post wrapper: throw RejectedExecutionException only
     * if NOT enqueued; never inline. Callback validates its live UI/root/frame token.
     * Invalid/foreign directory handles are programmer misuse, not a provider error;
     * that worker failure retires without fabricating a Page or invoking the callback.
     */
    static Ticket tryLoad(WorkspaceTreeReader reader,WorkspaceTreeReader.Directory directory,
            Executor mainDelivery,Consumer<WorkspaceTreeReader.Page> onPage) {
        requireMain();
        if(reader==null||directory==null||mainDelivery==null||onPage==null)
            throw new IllegalArgumentException("Missing workspace request input");
        synchronized(guard) {
            if(owner!=null)return null;
            Ticket ticket=new Ticket(reader,directory,mainDelivery,onPage);owner=ticket;
            try{worker.execute(ticket::query);}
            catch(RejectedExecutionException rejected){ticket.finishLocked();throw rejected;}
            return ticket;
        }
    }

    private static void requireMain(){
        if(Thread.currentThread()!=Looper.getMainLooper().getThread())
            throw new IllegalStateException("Workspace browser main thread required");
    }

    static final class Ticket {
        private final CancellationSignal signal=new CancellationSignal();
        private WorkspaceTreeReader reader;
        private WorkspaceTreeReader.Directory directory;
        private Executor deliveryExecutor;
        private Consumer<WorkspaceTreeReader.Page> callback;
        private WorkspaceTreeReader.Page page;
        private boolean cancelled,finished;

        private Ticket(WorkspaceTreeReader reader,WorkspaceTreeReader.Directory directory,
                Executor deliveryExecutor,Consumer<WorkspaceTreeReader.Page> callback){
            this.reader=reader;this.directory=directory;this.deliveryExecutor=deliveryExecutor;this.callback=callback;
        }

        /** Drops UI ownership first. Provider cancellation can itself call/block provider code. */
        void cancel(){
            requireMain();
            synchronized(guard){
                if(finished||cancelled)return;
                cancelled=true;callback=null;deliveryExecutor=null;
            }
            // Never call provider code under the process-wide admission monitor.
            try{signal.cancel();}catch(RuntimeException ignored){/* Logical retirement still holds the physical slot. */}
        }

        private void query(){
            boolean queued=false;
            try {
                WorkspaceTreeReader input;WorkspaceTreeReader.Directory target;
                synchronized(guard){if(cancelled)return;input=reader;target=directory;}
                WorkspaceTreeReader.Page result=input.list(target,limits,signal);
                synchronized(guard){
                    reader=null;directory=null;
                    if(cancelled)return;
                    page=result;
                    Executor delivery=deliveryExecutor;deliveryExecutor=null;
                    // Nonblocking, never-inline Handler.post contract; the queued closure owns only this Ticket.
                    delivery.execute(this::deliver);queued=true;
                }
            }catch(RuntimeException failed){
                // Provider failures are represented by reader Pages. This covers programmer
                // misuse/rejected delivery without inventing empty-success or resurrecting UI.
            }finally{
                if(!queued)synchronized(guard){finishLocked();}
            }
        }

        private void deliver(){
            try {
                requireMain();
                Consumer<WorkspaceTreeReader.Page> consumer;WorkspaceTreeReader.Page result;
                synchronized(guard){
                    if(finished)return;
                    consumer=cancelled?null:callback;result=page;
                    callback=null;deliveryExecutor=null;page=null;
                }
                // All controls/delivery are main-thread serialized. Already executing callbacks
                // cannot be revoked; reentrant admission stays BUSY until this finally completes.
                if(consumer!=null)consumer.accept(result);
            }finally{synchronized(guard){finishLocked();}}
        }

        private void finishLocked(){
            finished=true;callback=null;deliveryExecutor=null;page=null;reader=null;directory=null;
            if(owner==this)owner=null;
        }
    }
}
