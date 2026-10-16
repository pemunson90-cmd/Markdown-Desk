package com.example.markdowndesk;

import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Captured document export. No Activity, editor, save/history or persistent picker authority. */
final class DocumentDocxExport {
    private static final Object guard=new Object();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1),r->new Thread(r,"MarkdownDesk-docx"));
    private static Ticket owner;
    enum Problem { GENERATION, STAGING, DESTINATION, WRITE, VERIFY, CANCELLED, UNCERTAIN_IO }
    static final class Failure {
        final Problem problem;final boolean destinationMayContainPartialCopy;
        Failure(Problem problem,boolean partial){this.problem=problem;destinationMayContainPartialCopy=partial;}
    }
    static final class Completion {
        final boolean verified;final Failure failure;
        Completion(boolean verified,Failure failure){this.verified=verified;this.failure=failure;}
    }
    static final class Offer {
        final String title,filename;final long length;final String sha256;
        final Map<MarkdownDocx.Warning,Integer> warnings;final List<Integer> warningOffsets;
        Offer(String title,MarkdownDocx.Result result){this.title=title;filename=filename(title);length=result.length;sha256=result.sha256;warnings=result.warnings;warningOffsets=result.warningOffsets;}
    }
    static final class Capture {
        final String source,title;final List<Uri> protectedUris;
        Capture(CharSequence source,String title,Uri sourceUri,List<Uri> openUris){
            if(source==null||source.length()>1048576||title==null||title.length()>256)throw new IllegalArgumentException("DOCX capture limit");
            long bytes=0;
            for(int i=0;i<source.length();i++){
                char ch=source.charAt(i);if(Character.isHighSurrogate(ch)){if(++i>=source.length()||!Character.isLowSurrogate(source.charAt(i)))throw new IllegalArgumentException("Invalid Unicode");bytes+=4;}
                else if(Character.isLowSurrogate(ch))throw new IllegalArgumentException("Invalid Unicode");else bytes+=ch<128?1:ch<2048?2:3;
                if(bytes>1048576)throw new IllegalArgumentException("DOCX supports source up to1MiB");
            }
            List<Uri> protectedCopy=uris(openUris,12);if(sourceUri!=null){uri(sourceUri);if(protectedCopy.stream().noneMatch(x->x.toString().equals(sourceUri.toString())))protectedCopy.add(sourceUri);}
            this.source=source.toString();this.title=title;protectedUris=Collections.unmodifiableList(protectedCopy);
        }
    }
    private DocumentDocxExport(){}
    static Ticket tryStart(Context context,Supplier<Capture> supplier,Consumer<Offer> ready,Consumer<Failure> failure){
        requireMain();if(context==null||supplier==null||ready==null||failure==null)throw new IllegalArgumentException("Missing DOCX input");
        synchronized(guard){
            if(owner!=null)return null;
            Context app=context.getApplicationContext();if(app==null)throw new IllegalArgumentException("Application context required");
            Ticket ticket=new Ticket(app,ready,failure);owner=ticket;
            try{ticket.capture=supplier.get();if(ticket.capture==null)throw new IllegalArgumentException("Missing DOCX capture");ticket.protectedUris=ticket.capture.protectedUris;}
            catch(RuntimeException|Error failed){ticket.clearUi();ticket.capture=null;ticket.protectedUris=Collections.emptyList();owner=null;throw failed;}
            ticket.physical=1;
            try{worker.execute(ticket::generate);return ticket;}
            catch(RejectedExecutionException rejected){ticket.clearUi();ticket.capture=null;ticket.protectedUris=Collections.emptyList();ticket.physical=0;owner=null;throw rejected;}
            catch(RuntimeException|Error uncertainSubmission){ticket.cancelled=true;ticket.clearUi();ticket.capture=null;throw uncertainSubmission;}
        }
    }
    private static void requireMain(){if(Thread.currentThread()!=Looper.getMainLooper().getThread())throw new IllegalStateException("DOCX main thread required");}
    private static void uri(Uri uri){if(uri==null||uri.toString().length()>4096||!"content".equals(uri.getScheme())||uri.getAuthority()==null||uri.getAuthority().isEmpty())throw new IllegalArgumentException("Invalid DOCX URI");}
    private static List<Uri> uris(List<Uri> input,int maximum){if(input==null||input.size()>maximum)throw new IllegalArgumentException("DOCX URI limit");List<Uri> copy=new ArrayList<>();for(Uri item:input)if(item!=null){uri(item);if(copy.stream().noneMatch(x->x.toString().equals(item.toString())))copy.add(item);}return copy;}
    private static String filename(String title){StringBuilder name=new StringBuilder();for(int i=0;i<title.length()&&name.length()<118;){int cp=title.codePointAt(i);i+=Character.charCount(cp);if(cp>=32&&!Character.isISOControl(cp)&&cp!='/'&&cp!='\\'&&!(cp>=0xd800&&cp<=0xdfff)&&name.length()+Character.charCount(cp)<=118)name.appendCodePoint(cp);}String clean=name.toString().trim();while(clean.toLowerCase(Locale.ROOT).endsWith(".docx"))clean=clean.substring(0,clean.length()-5);return(clean.isEmpty()?"Document":clean)+".docx";}

    static final class Ticket {
        private final Context app;
        private Capture capture;private DocxStage stage;private List<Uri> protectedUris=Collections.emptyList();
        private Consumer<Offer> ready;private Consumer<Failure> generationFailure;private Consumer<Completion> completion;
        private int physical,queued;private boolean cancelled,offered,published,delivering,finishing,uncertain,cleanupBlocked;
        Ticket(Context app,Consumer<Offer> ready,Consumer<Failure> generationFailure){this.app=app;this.ready=ready;this.generationFailure=generationFailure;}
        private void check()throws MarkdownDocx.ExportException {synchronized(guard){if(cancelled)throw new MarkdownDocx.ExportException(MarkdownDocx.Failure.CANCELLED);}}
        private Capture takeCapture(){synchronized(guard){Capture input=capture;capture=null;return input;}}
        private Offer render(Capture input)throws IOException {
            check();if(input==null)throw new MarkdownDocx.ExportException(MarkdownDocx.Failure.CANCELLED);
            DocxStage created=DocxStage.create(app.getCacheDir());synchronized(guard){stage=created;}
            MarkdownDocx.Result result=MarkdownDocx.export(input.source,input.title,created.output(),MarkdownDocx.Limits.defaults(),this::check);
            created.seal(result,this::check);return new Offer(input.title,result);
        }
        private void generate(){
            boolean handled=false;
            try{Offer offer=render(takeCapture());check();post(()->deliverReady(offer));handled=true;}
            catch(Exception failure){synchronized(guard){capture=null;}Failure classified=classify(failure,false);finishStage();final Failure result=uncertain?new Failure(Problem.UNCERTAIN_IO,false):classified;post(()->deliverGenerationFailure(result));handled=true;}
            catch(Error fatal){synchronized(guard){uncertain|=unknownClose(fatal,0);}throw fatal;}
            finally{if(!handled)synchronized(guard){cancelled=true;clearUi();}if(cancelled)finishStage();synchronized(guard){physical--;if(cancelled)scheduleCleanup();releaseIfSafe();}}
        }
        void publish(Uri destination,List<Uri> currentOpenUris,Consumer<Completion> consumer){
            requireMain();uri(destination);if(consumer==null)throw new IllegalArgumentException("Missing DOCX completion");
            synchronized(guard){
                if(owner!=this||cancelled||!offered||published||delivering)throw new IllegalStateException("Unbound DOCX destination");
                List<Uri> combined=new ArrayList<>(protectedUris);for(Uri item:uris(currentOpenUris,12))if(combined.stream().noneMatch(x->x.toString().equals(item.toString())))combined.add(item);
                if(combined.size()>25)throw new IllegalArgumentException("DOCX URI limit");protectedUris=Collections.unmodifiableList(combined);
                completion=consumer;published=true;physical++;
                try{worker.execute(()->write(destination));}catch(RejectedExecutionException rejected){physical--;cancelled=true;cleanupBlocked=true;clearUi();throw rejected;}
            }
        }
        private void write(Uri destination){
            boolean handled=false;
            try {
                Completion result;
                try{DocxNewCopy.publish(app.getContentResolver(),app,stage,destination,protectedUris,this::check);result=new Completion(true,null);}
                catch(Exception failure){result=new Completion(false,classify(failure,true));}
                finishStage();if(cleanupBlocked||uncertain)result=new Completion(false,new Failure(uncertain?Problem.UNCERTAIN_IO:Problem.STAGING,true));
                final Completion delivered=result;post(()->deliverCompletion(delivered));handled=true;
            }catch(Error fatal){synchronized(guard){uncertain|=unknownClose(fatal,0);}throw fatal;}
            finally{if(!handled)synchronized(guard){cancelled=true;clearUi();}finishStage();synchronized(guard){physical--;releaseIfSafe();}}
        }
        private boolean unknownClose(Throwable failure,int depth){
            if(depth>5||failure==null)return false;
            if(failure instanceof DocxStage.DisposalFailure||failure instanceof DocxNewCopy.Failure&&((DocxNewCopy.Failure)failure).uncertain)return true;
            if(unknownClose(failure.getCause(),depth+1))return true;for(Throwable suppressed:failure.getSuppressed())if(unknownClose(suppressed,depth+1))return true;return false;
        }
        private Failure classify(Exception failure,boolean partial){
            synchronized(guard){uncertain|=unknownClose(failure,0);}
            if(failure instanceof DocxNewCopy.Failure){DocxNewCopy.Failure bad=(DocxNewCopy.Failure)failure;synchronized(guard){uncertain|=bad.uncertain;}Problem problem=bad.uncertain?Problem.UNCERTAIN_IO:bad.reason==DocxNewCopy.Reason.VERIFY?Problem.VERIFY:bad.reason==DocxNewCopy.Reason.WRITE?Problem.WRITE:Problem.DESTINATION;return new Failure(problem,bad.partial);}
            if(failure instanceof MarkdownDocx.ExportException&&((MarkdownDocx.ExportException)failure).failure==MarkdownDocx.Failure.CANCELLED)return new Failure(Problem.CANCELLED,partial);
            if(failure instanceof DocxStage.DisposalFailure)synchronized(guard){uncertain=true;}
            return new Failure(uncertain?Problem.UNCERTAIN_IO:Problem.GENERATION,partial);
        }
        private void post(Runnable delivery){
            synchronized(guard){queued++;if(!main.post(delivery)){queued--;cancelled=true;clearUi();}}
        }
        private void deliverReady(Offer offer){
            requireMain();boolean delivered=false;
            try{Consumer<Offer> callback;synchronized(guard){delivering=true;callback=cancelled?null:ready;ready=null;generationFailure=null;offered=!cancelled;}
                if(callback!=null)callback.accept(offer);delivered=true;
            }finally{synchronized(guard){queued--;delivering=false;if(!delivered){cancelled=true;clearUi();}if(cancelled)scheduleCleanup();releaseIfSafe();}}
        }
        private void deliverGenerationFailure(Failure failure){
            requireMain();try{Consumer<Failure> callback;synchronized(guard){callback=cancelled?null:generationFailure;clearUi();}if(callback!=null)callback.accept(failure);}
            finally{synchronized(guard){queued--;releaseIfSafe();}}
        }
        private void deliverCompletion(Completion result){
            requireMain();try{Consumer<Completion> callback;synchronized(guard){callback=cancelled?null:completion;clearUi();}if(callback!=null)callback.accept(result);}
            finally{synchronized(guard){queued--;releaseIfSafe();}}
        }
        void cancel(){requireMain();synchronized(guard){cancelled=true;capture=null;clearUi();scheduleCleanup();releaseIfSafe();}}
        private void clearUi(){ready=null;generationFailure=null;completion=null;}
        private void scheduleCleanup(){
            if(physical!=0||stage==null||uncertain||cleanupBlocked)return;
            physical++;try{worker.execute(()->{try{finishStage();}finally{synchronized(guard){physical--;releaseIfSafe();}}});}
            catch(RejectedExecutionException rejected){physical--;cleanupBlocked=true;}
        }
        private void finishStage(){
            DocxStage current;synchronized(guard){finishing=true;current=stage;}
            if(current==null)return;
            if(!current.disposed()){synchronized(guard){uncertain=true;}return;}
            try{current.cleanup();synchronized(guard){if(stage==current)stage=null;}}
            catch(IOException|RuntimeException failure){synchronized(guard){cleanupBlocked=true;if(failure instanceof DocxStage.DisposalFailure)uncertain=true;}}
        }
        private void releaseIfSafe(){
            if(owner==this&&(cancelled||finishing)&&physical==0&&queued==0&&stage==null&&!uncertain&&!cleanupBlocked){clearUi();capture=null;protectedUris=Collections.emptyList();owner=null;}
        }
    }
}
