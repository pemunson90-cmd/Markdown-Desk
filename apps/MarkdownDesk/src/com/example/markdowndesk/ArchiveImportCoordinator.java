package com.example.markdowndesk;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import com.example.markdowndesk.archive.ArchiveStreamParser;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A process-owned import job, independent of Activity recreation and Fold posture.
 * If Android kills the process, reopening Archive Research restarts from the
 * beginning after cleaning incomplete staging records; it never resumes an
 * unverified partial JSON parser state, or discards the last ready index.
 */
final class ArchiveImportCoordinator {
    interface Listener { void onArchiveImportChanged(Snapshot snapshot); }
    static final class Snapshot {
        final String state,detail,error;
        final long bytes,size,free,lastChange;
        final int conversations,messages;
        Snapshot(String state,String detail,String error,long bytes,long size,long free,long lastChange,
                 int conversations,int messages){
            this.state=state;this.detail=detail;this.error=error;this.bytes=bytes;this.size=size;
            this.free=free;this.lastChange=lastChange;this.conversations=conversations;this.messages=messages;
        }
        boolean running(){return "checking".equals(state)||"cleaning".equals(state)
                ||"indexing".equals(state)||"publishing".equals(state)
                ||"cancelling".equals(state);}
    }
    private static ArchiveImportCoordinator singleton;
    static synchronized ArchiveImportCoordinator get(Context context){
        if(singleton==null)singleton=new ArchiveImportCoordinator(context.getApplicationContext());
        return singleton;
    }
    private final Context app;
    private final SharedPreferences saved;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancellation=new AtomicBoolean();
    private final AtomicLong bytes=new AtomicLong();
    private volatile WeakReference<Listener> listener=new WeakReference<>(null);
    private volatile InputStream sourceStream;
    private volatile Thread runningThread;
    private volatile boolean busy;
    private volatile String sourceUri;
    private volatile Snapshot snapshot;
    private volatile long lastPersist;
    private int conversations,messages;

    private ArchiveImportCoordinator(Context context){
        app=context;
        saved=app.getSharedPreferences("archive_import_v2",Context.MODE_PRIVATE);
        String state=saved.getString("state","idle");
        sourceUri=saved.getString("uri",null);
        snapshot=new Snapshot("running".equals(state)?"interrupted":state,
                "running".equals(state)?"Previous import was interrupted. Safe restart available.":"",
                saved.getString("error",null),saved.getLong("bytes",0),saved.getLong("size",-1),
                freeBytes(),saved.getLong("changed",0),saved.getInt("conversations",0),saved.getInt("messages",0));
    }
    synchronized void attach(Listener attached){
        listener=new WeakReference<>(attached);notifyListener();
    }
    synchronized void detach(Listener detached){
        if(listener.get()==detached)listener=new WeakReference<>(null);
    }
    Snapshot snapshot(){return snapshot;}
    synchronized boolean start(Uri uri){
        if(busy||uri==null||!"content".equalsIgnoreCase(uri.getScheme()))return false;
        sourceUri=uri.toString();busy=true;cancellation.set(false);bytes.set(0);
        conversations=0;messages=0;
        update("checking","Inspecting archive size and available storage",null,-1,true);
        worker.execute(()->importSource(sourceUri));
        return true;
    }
    synchronized boolean retryInterrupted(){
        if(busy||sourceUri==null||sourceUri.isEmpty())return false;
        return start(Uri.parse(sourceUri));
    }
    synchronized void cancel(){
        // A stale Cancel tap must not transition an idle or completed job into
        // a permanent "cancelling" state. Multiple taps should be idempotent.
        if(!busy || !snapshot.running() || !cancellation.compareAndSet(false,true))return;
        Thread currentWorker=runningThread;
        if(currentWorker!=null)currentWorker.interrupt();
        InputStream current=sourceStream;
        if(current!=null)new Thread(()->{
            try{current.close();}catch(IOException ignored){}
        },"archive-input-close").start();
        update("cancelling","Stopping safely; previous index remains available",null,
                snapshot.size,true);
    }
    private static String err(Exception failure){
        if(failure==null)return "Unknown error";
        String detail=failure.getMessage();
        if(detail==null||detail.trim().isEmpty())detail=failure.getClass().getSimpleName();
        return detail.length()>260?detail.substring(0,260):detail;
    }
    private void importSource(String selected){
        runningThread=Thread.currentThread();
        ArchiveDatabase db=null;
        try{
            if(cancellation.get())throw new IOException("Import cancelled");
            Uri uri=Uri.parse(selected);
            String id=UUID.nameUUIDFromBytes(selected.getBytes(StandardCharsets.UTF_8)).toString();
            long size=sourceLength(uri);
            // Cleanup must be available even if an old interrupted import
            // occupied most of the phone. Do not reject before safe cleanup.
            if(freeBytes()>=0 && freeBytes()<128L*1024*1024)
                throw new IOException("Less than 128 MiB free; free some storage before repairing the index");
            db=new ArchiveDatabase(app,id);
            update("cleaning","Removing incomplete or obsolete index generations",null,size,true);
            db.removeUnpublishedOrObsolete(null);
            checkSpace(size,true,db);
            if(cancellation.get())throw new IOException("Import cancelled");
            update("indexing","Streaming messages into the local search index",null,size,true);
            db.setProgress(new ArchiveDatabase.Progress(){
                @Override public void onMessage(int n){
                    messages=n;throttled(size);
                }
                @Override public void onConversation(int n){
                    conversations=n;throttled(size);
                }
            });
            db.beginImport(selected,"Claude export");
            try(InputStream input=app.getContentResolver().openInputStream(uri)){
                if(input==null)throw new IOException("The file provider returned no data");
                sourceStream=input;
                InputStream counting=new FilterInputStream(input){
                    @Override public int read()throws IOException{
                        if(cancellation.get())throw new IOException("Import cancelled");
                        int v=super.read();
                        if(v>=0){bytes.incrementAndGet();throttled(size);}
                        return v;
                    }
                    @Override public int read(byte[] b,int off,int len)throws IOException{
                        if(cancellation.get())throw new IOException("Import cancelled");
                        int n=in.read(b,off,len);
                        if(n>0){bytes.addAndGet(n);throttled(size);}
                        return n;
                    }
                };
                new ArchiveStreamParser(new InputStreamReader(counting,StandardCharsets.UTF_8),db).parse();
            }finally{sourceStream=null;}
            if(cancellation.get())throw new IOException("Import cancelled");
            update("publishing","Switching to completed index without deleting the previous one",null,size,true);
            db.completeImport();
            update("cleaning","Index ready; removing older generations in small batches",null,size,true);
            try{db.removeUnpublishedOrObsolete(null);}
            catch(Exception cleanup){
                // Publication has already succeeded. The older index is unreachable;
                // future launches may safely retry its cleanup.
            }
            update("complete","Imported "+conversations+" conversations and "+messages+" messages",null,size,true);
        }catch(Exception failure){
            if(db!=null)try{db.abortImport();}catch(Exception ignored){}
            boolean wasCancelled=cancellation.get();
            update(wasCancelled?"cancelled":"failed",
                    wasCancelled?"Import cancelled. Previous index preserved.":"Import incomplete. Previous index preserved.",
                    wasCancelled?null:err(failure),snapshot.size,true);
        }finally{
            sourceStream=null;runningThread=null;
            if(db!=null)try{db.close();}catch(Exception ignored){}
            synchronized(this){busy=false;}
        }
    }
    private void throttled(long size){
        if(cancellation.get()||Thread.currentThread().isInterrupted())
            throw new IllegalStateException("Import cancelled");
        long now=System.currentTimeMillis();
        if(now-lastPersist<1000)return;
        try{checkSpace(size,false,null);}catch(IOException low){throw new IllegalStateException(low.getMessage(),low);}
        update("indexing","Indexing conversations and full-text search",null,size,true);
    }
    private void checkSpace(long size,boolean preflight,ArchiveDatabase db)throws IOException{
        long available=freeBytes();
        if(available<0)return;
        long reusable=db==null?0:db.reusableBytes();
        long required=preflight ? (size>0 ? Math.max(512L*1024*1024,Math.min(Long.MAX_VALUE/2,size)*2L)
                : 512L*1024*1024) : 512L*1024*1024;
        if(available<128L*1024*1024 || (preflight && available+reusable<required)
                || (!preflight && available<512L*1024*1024))
            throw new IOException("Storage safeguard: "+(available/(1024*1024))+
                    " MiB free, "+(reusable/(1024*1024))+" MiB reusable inside the cache. " +
                    "The original and previous completed index remain unchanged.");
    }
    private long sourceLength(Uri uri){
        try(Cursor c=app.getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getLong(0);
        }catch(Exception ignored){}
        return -1;
    }
    private long freeBytes(){
        try{return new StatFs(app.getFilesDir().getAbsolutePath()).getAvailableBytes();}
        catch(Exception ignored){return -1;}
    }
    private synchronized void update(String state,String detail,String error,long size,boolean persist){
        long now=System.currentTimeMillis();
        snapshot=new Snapshot(state,detail,error,bytes.get(),size,freeBytes(),now,conversations,messages);
        if(persist){
            lastPersist=now;
            saved.edit().putString("state",snapshot.running()?"running":state)
                    .putString("uri",sourceUri).putString("error",error)
                    .putLong("bytes",snapshot.bytes).putLong("size",size)
                    .putLong("changed",now).putInt("conversations",conversations)
                    .putInt("messages",messages).apply();
        }
        notifyListener();
    }
    private void notifyListener(){
        Listener current=listener.get();
        if(current!=null)current.onArchiveImportChanged(snapshot);
    }
}