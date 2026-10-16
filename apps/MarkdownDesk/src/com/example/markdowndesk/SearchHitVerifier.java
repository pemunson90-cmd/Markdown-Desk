package com.example.markdowndesk;

import android.os.Handler;
import android.os.Looper;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.function.*;

/** One captured displayed-text hash; no provider I/O, navigation or mutable editor access on worker. */
final class SearchHitVerifier {
    private static final Object guard=new Object();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1),task->new Thread(task,"MarkdownDesk-hit-hash"));
    private static Ticket owner;
    enum Problem { HASH_FAILED }
    static final class Result {
        final long byteLength;final String sha256;
        private Result(long byteLength,String sha256){this.byteLength=byteLength;this.sha256=sha256;}
    }
    static final class Outcome {
        final Result result;final Problem problem;
        private Outcome(Result result,Problem problem){this.result=result;this.problem=problem;}
    }
    static final class Capture {
        private final String source;private final boolean bom;
        Capture(CharSequence displayedText,boolean utf8Bom){
            requireMain();
            if(displayedText==null||displayedText.length()>DocumentIO.MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("Displayed text exceeds native limit");
            long bytes=utf8Bom?3:0;
            for(int i=0;i<displayedText.length();i++){
                char ch=displayedText.charAt(i);
                if(Character.isHighSurrogate(ch)){
                    if(++i>=displayedText.length()||!Character.isLowSurrogate(displayedText.charAt(i)))throw new IllegalArgumentException("Invalid displayed Unicode");
                    bytes+=4;
                }else if(Character.isLowSurrogate(ch))throw new IllegalArgumentException("Invalid displayed Unicode");
                else bytes+=ch<128?1:ch<2048?2:3;
                if(bytes>DocumentIO.MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("Displayed text exceeds native limit");
            }
            source=displayedText.toString();bom=utf8Bom;
        }
    }
    private static final class Cancelled extends RuntimeException {}
    private SearchHitVerifier(){}

    /** Admission precedes supplier/editor capture. BUSY retains/invokes neither supplier nor callback. */
    static Ticket tryVerify(Supplier<Capture> captureAfterAdmission,Consumer<Outcome> callback){
        requireMain();
        if(captureAfterAdmission==null||callback==null)throw new IllegalArgumentException("Missing verification input");
        synchronized(guard){
            if(owner!=null)return null;
            Ticket ticket=new Ticket(callback);owner=ticket;
            try{ticket.capture=captureAfterAdmission.get();if(ticket.capture==null)throw new IllegalArgumentException("Missing displayed capture");}
            catch(RuntimeException|Error failed){ticket.clearUi();ticket.capture=null;owner=null;throw failed;}
            ticket.physical=true;
            try{worker.execute(ticket::verify);return ticket;}
            catch(RejectedExecutionException rejected){ticket.physical=false;ticket.capture=null;ticket.retireLocked();throw rejected;}
            catch(RuntimeException|Error ambiguousSubmission){ticket.cancelled=true;ticket.capture=null;ticket.clearUi();throw ambiguousSubmission;}
        }
    }
    private static void requireMain(){if(Thread.currentThread()!=Looper.getMainLooper().getThread())throw new IllegalStateException("Verification main thread required");}

    static final class Ticket {
        private Capture capture;
        private Consumer<Outcome> callback;
        private Outcome receipt;
        private boolean physical,queued,delivering,cancelled,finished;
        private Ticket(Consumer<Outcome> callback){this.callback=callback;}
        void cancel(){requireMain();synchronized(guard){if(finished)return;cancelled=true;capture=null;clearUi();}}
        private Capture takeCapture(){synchronized(guard){Capture input=capture;capture=null;return input;}}
        private void check(){synchronized(guard){if(cancelled)throw new Cancelled();}}
        private Result hash(Capture input)throws CharacterCodingException,NoSuchAlgorithmException {
            check();if(input==null)throw new Cancelled();
            if(input.source==null||input.source.length()>DocumentIO.MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("Invalid displayed capture");
            CharsetEncoder encoder=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer chars=CharBuffer.wrap(input.source);ByteBuffer bytes=ByteBuffer.allocate(8192);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=input.bom?3:0;
            if(input.bom)digest.update(new byte[]{(byte)239,(byte)187,(byte)191});
            while(true){
                check();CoderResult status=encoder.encode(chars,bytes,true);if(status.isError())status.throwException();
                total=consume(bytes,digest,total);if(status.isUnderflow())break;
            }
            while(true){check();CoderResult status=encoder.flush(bytes);if(status.isError())status.throwException();total=consume(bytes,digest,total);if(status.isUnderflow())break;}
            check();byte[] hash=digest.digest();check();
            StringBuilder hex=new StringBuilder(64);for(byte value:hash){int v=value&255;hex.append("0123456789abcdef".charAt(v>>>4)).append("0123456789abcdef".charAt(v&15));}
            check();return new Result(total,hex.toString());
        }
        private long consume(ByteBuffer bytes,MessageDigest digest,long total){
            int count=bytes.position();if(count>DocumentIO.MAX_DOCUMENT_BYTES-total)throw new IllegalArgumentException("Displayed bytes exceed native limit");
            digest.update(bytes.array(),0,count);bytes.clear();return total+count;
        }
        private void verify(){
            try{
                Outcome result;
                // hash parameter/local source has unwound BEFORE posting source-free metadata.
                try{result=new Outcome(hash(takeCapture()),null);}
                catch(Cancelled stopped){return;}
                catch(CharacterCodingException|NoSuchAlgorithmException|RuntimeException failed){result=new Outcome(null,Problem.HASH_FAILED);}
                synchronized(guard){
                    if(cancelled)return;
                    receipt=result;queued=true;
                    try{if(!main.post(this::deliver)){queued=false;cancelled=true;clearUi();}}
                    catch(RuntimeException|Error ambiguousPost){cancelled=true;clearUi();throw ambiguousPost;}
                }
            }finally{
                synchronized(guard){capture=null;physical=false;if(!queued)clearUi();retireLocked();}
            }
        }
        private void deliver(){
            requireMain();
            try{
                Consumer<Outcome> consumer;Outcome result;
                synchronized(guard){delivering=true;consumer=cancelled?null:callback;result=receipt;clearUi();}
                if(consumer!=null)consumer.accept(result);
            }finally{synchronized(guard){queued=false;delivering=false;clearUi();retireLocked();}}
        }
        private void clearUi(){callback=null;receipt=null;}
        private void retireLocked(){if(physical||queued||delivering)return;capture=null;clearUi();finished=true;if(owner==this)owner=null;}
    }
}
