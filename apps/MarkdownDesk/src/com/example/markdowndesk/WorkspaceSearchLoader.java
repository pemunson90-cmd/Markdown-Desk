package com.example.markdowndesk;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One process-wide physical search or undelivered receipt; independent of browse/save IO. */
final class WorkspaceSearchLoader {
    private static final Object guard=new Object();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1),task->new Thread(task,"MarkdownDesk-search"));
    private static Ticket owner;
    enum Problem { ENGINE_FAILURE }
    static final class Delivery {
        final WorkspaceLiteralSearch.Result result;
        final Problem problem;
        private Delivery(WorkspaceLiteralSearch.Result result,Problem problem){this.result=result;this.problem=problem;}
    }
    private WorkspaceSearchLoader(){}

    /** Null BUSY prepares nothing and queues no replacement. All controls/delivery are main-only. */
    static Ticket trySearch(Context context,Uri selectedSavedTree,String literal,
            WorkspaceLiteralSearch.Limits limits,Consumer<Delivery> callback){
        requireMain();
        if(context==null||callback==null)throw new IllegalArgumentException("Missing search inputs");
        synchronized(guard){
            if(owner!=null)return null;
            Ticket ticket=new Ticket(callback);owner=ticket;
            try{
                Context app=context.getApplicationContext();
                if(app==null)throw new IllegalArgumentException("Application context required");
                // Pure preparation before submission: this exact capability owns any later resources.
                ticket.attempt=new WorkspaceLiteralSearch(app.getContentResolver(),selectedSavedTree)
                        .prepareSearch(literal,limits,ticket.signal);
            }catch(RuntimeException|Error failure){ticket.clearUi();owner=null;throw failure;}
            ticket.physical=true;
            try{worker.execute(ticket::search);return ticket;}
            catch(RejectedExecutionException rejected){ticket.physical=false;ticket.retireLocked();throw rejected;}
            catch(RuntimeException|Error ambiguousSubmission){
                // Acceptance is not established. Never release a potentially executing reservation.
                ticket.cancelled=true;ticket.clearUi();throw ambiguousSubmission;
            }
        }
    }
    private static void requireMain(){if(Thread.currentThread()!=Looper.getMainLooper().getThread())throw new IllegalStateException("Search main thread required");}

    static final class Ticket {
        private final CancellationSignal signal=new CancellationSignal();
        private WorkspaceLiteralSearch.Attempt attempt;
        private Consumer<Delivery> callback;
        private Delivery receipt;
        private boolean physical,queued,delivering,cancelled,uncertain,finished;
        private Ticket(Consumer<Delivery> callback){this.callback=callback;}

        /** Clear Activity/UI ownership before invoking potentially blocking provider cancellation. */
        void cancel(){
            requireMain();
            synchronized(guard){if(finished||cancelled)return;cancelled=true;clearUi();}
            try{signal.cancel();}catch(RuntimeException ignored){/* Physical ownership is unchanged. */}
        }
        private void search(){
            try{
                Delivery result;
                try{result=new Delivery(attempt.execute(),null);}
                catch(RuntimeException failure){result=new Delivery(null,Problem.ENGINE_FAILURE);}
                synchronized(guard){
                    uncertain=attempt.closeDisposition()==WorkspaceLiteralSearch.CloseDisposition.UNCERTAIN;
                    if(cancelled)return;
                    receipt=result;queued=true;
                    try{
                        if(!main.post(this::deliver)){queued=false;cancelled=true;clearUi();}
                    }catch(RuntimeException|Error ambiguousPost){
                        // A thrown post cannot prove whether the single receipt was accepted.
                        cancelled=true;clearUi();throw ambiguousPost;
                    }
                }
            }finally{
                synchronized(guard){
                    // Includes fatal Error paths: the preowned receipt, never Result state, proves closure.
                    uncertain=attempt.closeDisposition()==WorkspaceLiteralSearch.CloseDisposition.UNCERTAIN;
                    physical=false;
                    if(!queued){cancelled=true;clearUi();}
                    retireLocked();
                }
            }
        }
        private void deliver(){
            requireMain();
            try{
                Consumer<Delivery> consumer;Delivery result;
                synchronized(guard){
                    delivering=true;consumer=cancelled?null:callback;result=receipt;clearUi();
                }
                if(consumer!=null)consumer.accept(result);
            }finally{
                synchronized(guard){queued=false;delivering=false;clearUi();retireLocked();}
            }
        }
        private void clearUi(){callback=null;receipt=null;}
        private void retireLocked(){
            if(physical||queued||delivering)return;
            clearUi();
            if(uncertain)return; // Keep exactly this engine-minted unresolved resource, no UI, until process restart.
            attempt=null;finished=true;
            if(owner==this)owner=null;
        }
    }
}
