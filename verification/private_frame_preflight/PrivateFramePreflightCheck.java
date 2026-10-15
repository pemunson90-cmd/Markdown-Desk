package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual codec frames and actual process-owned preparation Readers. */
public final class PrivateFramePreflightCheck {
    static int scenarios;
    interface Task {void run()throws Exception;}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static IOException rejects(Task task,String why)throws Exception {
        try{task.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){return expected;}
    }
    static final class F implements AutoCloseable {
        final Path parent=Files.createTempDirectory("private-frame-");
        final File root=Files.createDirectory(parent.resolve("store")).toFile();
        final HistoryStorage.Budget budget;
        final PreparationSources owner;
        F()throws IOException{this(HistoryStorage.METADATA_BUDGET);}
        F(long bytes)throws IOException{budget=new HistoryStorage.Budget(bytes);owner=PreparationSources.forPreparation(root,UUID.randomUUID(),budget,PreparationSources.newProducer());}
        PrivateFramePreflight.Workspace workspace()throws IOException{return PrivateFramePreflight.create(owner,budget);}
        void claim()throws IOException {
            synchronized(HistoryStorage.monitorFor(root)){
                try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){owner.claimLocked().validateLocked();}
            }
        }
        public void close()throws IOException{try(java.util.stream.Stream<Path> paths=Files.walk(parent)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
    static final class Source extends ByteArrayInputStream {
        int closeCalls,maxRead,readCalls;int closeFailures;boolean closeError;
        Task onRead,onClose;
        Source(byte[] bytes){super(bytes);}
        public synchronized int read(byte[] b,int off,int len){
            maxRead=Math.max(maxRead,len);readCalls++;
            try{if(onRead!=null)onRead.run();}
            catch(IOException e){throw new UncheckedIOException(e);}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new RuntimeException(e);}
            return super.read(b,off,Math.min(len,17));
        }
        public void close()throws IOException {
            closeCalls++;try{if(onClose!=null)onClose.run();}catch(IOException e){throw e;}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new IOException(e);}
            if(closeFailures-->0){if(closeError)throw new AssertionError("close error");throw new IOException("close");}
        }
    }
    static PrivateStateCodec.Provenance provenance(){return new PrivateStateCodec.Provenance("content://historical/雪","ab".repeat(32),17,true);}
    static PrivateStateCodec.Tab tab(boolean bom,int units){return new PrivateStateCodec.Tab("old/tab","Dräft😀.md",provenance(),bom,true,true,units,0,4,9,2,"find\n雪","replace\r\n",true,false,true);}
    static byte[] tabFrame()throws IOException {
        byte[] content="\ufeff a\r\n雪😀\t ".getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream out=new ByteArrayOutputStream();
        PrivateStateCodec.writeTab(tab(true," a\r\n雪😀\t ".length()),content.length,new ByteArrayInputStream(content),out);return out.toByteArray();
    }
    static byte[] recoveryFrame()throws IOException {
        byte[] content="\r\n😀".getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream out=new ByteArrayOutputStream();
        PrivateStateCodec.writeRecovery(new PrivateStateCodec.Recovery("Recovery雪",provenance(),false,4,0,123),content.length,new ByteArrayInputStream(content),out);return out.toByteArray();
    }
    static byte[] preferencesFrame()throws IOException {
        Map<String,Object> prefs=new TreeMap<>();prefs.put("ui_theme",8);prefs.put("line_numbers",true);prefs.put("split_ratio",0.5f);
        ByteArrayOutputStream out=new ByteArrayOutputStream();PrivateStateCodec.writePreferences(prefs,out);return out.toByteArray();
    }
    static byte[] seal(byte[] frame)throws IOException {
        byte[] result=frame.clone();byte[] hash=HistoryStorage.digest().digest(Arrays.copyOf(frame,frame.length-32));System.arraycopy(hash,0,result,result.length-32,32);return result;
    }
    static PrivateFramePreflight.Attempt attempt(PrivateFramePreflight.Workspace w,PrivateStateCodec.Kind kind,PreparationSources.Factory source)throws IOException {
        return w.attempt(kind,0,UUID.randomUUID(),UUID.randomUUID(),source);
    }
    static void valid()throws Exception {
        byte[][] frames={tabFrame(),recoveryFrame(),preferencesFrame()};PrivateStateCodec.Kind[] kinds=PrivateStateCodec.Kind.values();
        try(F f=new F();F foreign=new F()){
            PrivateFramePreflight.Workspace w=f.workspace();
            for(int i=0;i<frames.length;i++){
                Source source=new Source(frames[i]);UUID item=UUID.randomUUID(),artifact=UUID.randomUUID();
                PrivateFramePreflight.Attempt a=w.attempt(kinds[i],0,item,artifact,()->source);
                check(source.readCalls==0&&source.closeCalls==0,"attempt creation has no source callback");
                PrivateFramePreflight.ClosedFrame r=a.run();
                check(r.belongsTo(f.owner)&&!r.belongsTo(foreign.owner)&&!r.belongsTo(null),"result belongs only to exact preparation owner");
                check(r.kind==kinds[i]&&r.ordinal==0&&r.itemId.equals(item)&&r.artifactId.equals(artifact),"frozen kind/ordinal/generated IDs");
                check(r.envelopeBytes==frames[i].length&&r.envelopeHash.equals(PrivateOperationRecord.sha(frames[i])),"exact whole frame length/hash including checksum");
                check(source.closeCalls==1&&source.maxRead<=65536,"actual close and bounded delegate reads");
                if(i==0)check(r.payloadBytes=="\ufeff a\r\n雪😀\t ".getBytes(StandardCharsets.UTF_8).length&&r.utf16Units==" a\r\n雪😀\t ".length(),"dirty BOM CRLF Unicode frame validated");
                if(i==2)check(r.payloadBytes==0&&r.utf16Units==0,"preferences have no payload");
                rejects(a::run,"successful attempt is single use");
            }
            f.owner.freeze();f.claim();scenarios+=3;
        }
    }
    static void malformed()throws Exception {
        byte[] valid=tabFrame();int metadata=ByteBuffer.wrap(valid).getInt(13);
        for(String fault:new String[]{"checksum","trailing","truncated","utf8","metadata","wrong-kind"})try(F f=new F()){
            byte[] bytes=valid.clone();
            switch(fault){
                case "checksum":bytes[bytes.length-1]^=1;break;
                case "trailing":bytes=Arrays.copyOf(bytes,bytes.length+1);break;
                case "truncated":bytes=Arrays.copyOf(bytes,bytes.length-1);break;
                case "utf8":bytes[21+metadata+3]=(byte)0xff;bytes=seal(bytes);break;
                case "metadata":ByteBuffer.wrap(bytes).putInt(21,Integer.MAX_VALUE);bytes=seal(bytes);break;
            }
            Source source=new Source(bytes);PrivateFramePreflight.Workspace w=f.workspace();
            PrivateFramePreflight.Attempt a=attempt(w,fault.equals("wrong-kind")?PrivateStateCodec.Kind.RECOVERY:PrivateStateCodec.Kind.SESSION_TAB,()->source);
            rejects(a::run,fault);check(source.closeCalls==1,"failure actually closes owned reader");
            rejects(()->attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->source),"workspace terminal after "+fault);
            a.retryClose();rejects(a::run,"cleanup cannot fabricate validation result");f.owner.freeze();f.claim();scenarios++;
        }
    }
    static void closeFailures()throws Exception {
        for(boolean error:new boolean[]{false,true})try(F f=new F()){
            Source source=new Source(tabFrame());source.closeFailures=2;source.closeError=error;
            PrivateFramePreflight.Workspace w=f.workspace();PrivateFramePreflight.Attempt a=attempt(w,PrivateStateCodec.Kind.SESSION_TAB,()->source);
            if(error){try{a.run();throw new AssertionError("Accepted close error");}catch(AssertionError expected){check(expected.getMessage().equals("close error"),"actual close Error propagated");}}
            else rejects(a::run,"failed close");
            f.owner.freeze();rejects(f::claim,"failed close retains original owner resource");
            if(error){try{a.retryClose();throw new AssertionError("Accepted retry error");}catch(AssertionError expected){check(expected.getMessage().equals("close error"),"actual retry Error propagated");}}
            else rejects(a::retryClose,"close retry still failed");
            rejects(f::claim,"second close failure retains original reader");a.retryClose();check(source.closeCalls==3,"same source closed three actual times");f.claim();
            rejects(a::run,"retry close never returns ClosedFrame");scenarios++;
        }
        try(F f=new F()){
            Source source=new Source(new byte[1]);source.closeFailures=1;
            PrivateFramePreflight.Attempt a=attempt(f.workspace(),PrivateStateCodec.Kind.SESSION_TAB,()->source);
            IOException failure=rejects(a::run,"invalid frame with failed close");check(failure.getSuppressed().length==1,"close failure attached to validation error");
            a.retryClose();f.owner.freeze();f.claim();scenarios++;
        }
    }
    static void sourceFailures()throws Exception {
        for(boolean error:new boolean[]{false,true})try(F f=new F()){
            final int[] opens={0};PrivateFramePreflight.Workspace w=f.workspace();
            PrivateFramePreflight.Attempt a=attempt(w,PrivateStateCodec.Kind.SESSION_TAB,()->{opens[0]++;if(error)throw new AssertionError("open error");throw new IOException("open");});
            if(error){try{a.run();throw new AssertionError("Accepted open Error");}catch(AssertionError expected){check(expected.getMessage().equals("open error"),"factory Error propagated");}}
            else rejects(a::run,"factory throws");
            check(opens[0]==1,"factory invoked once");rejects(a::retryClose,"no fictional reader for uncertain open");f.owner.freeze();rejects(f::claim,"factory failure leaves owner permanently uncertain");scenarios++;
        }
        for(boolean error:new boolean[]{false,true})try(F f=new F()){
            InputStream source=new InputStream(){int closes;public int read()throws IOException{throw new IOException("unexpected unbounded delegate read");}public int read(byte[] b,int o,int n)throws IOException{if(error)throw new AssertionError("read error");throw new IOException("read");}public void close(){closes++;check(closes==1,"one actual close after read failure");}};
            PrivateFramePreflight.Attempt a=attempt(f.workspace(),PrivateStateCodec.Kind.SESSION_TAB,()->source);
            if(error){try{a.run();throw new AssertionError("Accepted read Error");}catch(AssertionError expected){check(expected.getMessage().equals("read error"),"read Error propagated");}}
            else rejects(a::run,"source read failure");f.owner.freeze();f.claim();scenarios++;
        }
    }
    static void invalidReaders()throws Exception {
        try(F f=new F()){
            PrivateFramePreflight.Attempt a=attempt(f.workspace(),PrivateStateCodec.Kind.PREFERENCES,()->null);
            rejects(a::run,"factory returned null");f.owner.freeze();rejects(f::claim,"null factory result is permanently uncertain");scenarios++;
        }
        for(int malformed:new int[]{-2,0,Integer.MAX_VALUE})try(F f=new F()){
            int[] closes={0};InputStream source=new InputStream(){public int read(){throw new AssertionError("delegate escaped");}public int read(byte[] b,int o,int n){return malformed;}public void close(){closes[0]++;}};
            PrivateFramePreflight.Attempt a=attempt(f.workspace(),PrivateStateCodec.Kind.PREFERENCES,()->source);
            rejects(a::run,"invalid/stalled raw source count "+malformed);check(closes[0]==1,"malformed reader actually closed");f.owner.freeze();f.claim();scenarios++;
        }
    }
    static void budgetAndBounds()throws Exception {
        try(F f=new F(520000)){rejects(f::workspace,"workspace reservation before callbacks");scenarios++;}
        try(F f=new F()){
            rejects(()->PrivateFramePreflight.create(f.owner,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET)),"second independent budget");
            PrivateFramePreflight.Workspace w=f.workspace();int[] opens={0};
            PrivateFramePreflight.Attempt active=attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->{opens[0]++;return new Source(preferencesFrame());});
            rejects(()->attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->{opens[0]++;return null;}),"one active attempt");check(opens[0]==0,"active admission rejection has no factory callback");active.run();
            for(int i=1;i<14;i++)attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->new Source(preferencesFrame())).run();
            rejects(()->attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->{opens[0]++;return null;}),"14 lifetime attempts");check(opens[0]==1,"lifetime admission does not call factory");scenarios++;
        }
        try(F f=new F()){
            PrivateFramePreflight.Workspace w=f.workspace();f.budget.charge(HistoryStorage.METADATA_BUDGET-(512L<<10)-(2048L+f.root.getPath().length()*2L)-1024);int[] opens={0};
            rejects(()->attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->{opens[0]++;return null;}),"attempt metadata exhausted before allocation/open");check(opens[0]==0,"exhausted attempt has no callback");scenarios++;
        }
    }
    static void reentrancyAndThread()throws Exception {
        try(F f=new F()){
            PrivateFramePreflight.Workspace w=f.workspace();Source source=new Source(tabFrame());PrivateFramePreflight.Attempt a=attempt(w,PrivateStateCodec.Kind.SESSION_TAB,()->source);
            final int[] nested={0};source.onRead=()->{source.onRead=null;rejects(a::run,"reentrant run");rejects(()->attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->null),"reentrant admission");nested[0]++;};
            source.onClose=()->{rejects(a::retryClose,"reentrant owned close");nested[0]++;};
            Throwable[] foreign={null};Thread t=new Thread(()->{try{a.run();foreign[0]=new AssertionError("foreign thread accepted");}catch(IOException expected){}catch(Throwable e){foreign[0]=e;}});t.start();t.join();if(foreign[0]!=null)throw new AssertionError(foreign[0]);
            a.run();check(nested[0]==2,"rejected nested calls preserve successful outer validation");f.owner.freeze();f.claim();scenarios++;
        }
        try(F f=new F()){
            synchronized(HistoryStorage.monitorFor(f.root)){rejects(f::workspace,"workspace under primary monitor");}
            PrivateFramePreflight.Workspace w=f.workspace();int[] opens={0};PrivateFramePreflight.Attempt a=attempt(w,PrivateStateCodec.Kind.PREFERENCES,()->{opens[0]++;return new Source(preferencesFrame());});
            synchronized(HistoryStorage.monitorFor(f.root)){rejects(a::run,"source callbacks under primary monitor");}
            check(opens[0]==0,"locked rejection precedes factory");scenarios++;
        }
    }
    static void blockedOpenAndRead()throws Exception {
        for(boolean opening:new boolean[]{true,false})try(F f=new F()){
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Throwable[] failure={null};int[] closes={0};
            Thread worker=new Thread(()->{try{
                Source source=new Source(preferencesFrame());
                Task block=()->{entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new IOException("test callback timeout");};
                source.onClose=()->closes[0]++;
                if(!opening)source.onRead=()->{source.onRead=null;block.run();};
                PrivateFramePreflight.Attempt a=attempt(f.workspace(),PrivateStateCodec.Kind.PREFERENCES,()->{if(opening)try{block.run();}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}return source;});
                try{a.run();failure[0]=new AssertionError("frozen source preflight accepted");}catch(IOException expected){}
            }catch(Throwable e){failure[0]=e;}});
            worker.start();check(entered.await(3,TimeUnit.SECONDS),"owned "+(opening?"factory":"read")+" callback reached");
            f.owner.freeze();rejects(f::claim,"blocked "+(opening?"open":"read")+" remains registered");release.countDown();worker.join(4000);
            check(!worker.isAlive()&&failure[0]==null&&closes[0]==1,"frozen blocked source fails and actually closes after return");f.claim();scenarios++;
        }
    }
    static void blockedClose()throws Exception {
        try(F f=new F()){
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Throwable[] failure={null};
            Thread worker=new Thread(()->{try{Source s=new Source(preferencesFrame());s.onClose=()->{entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new IOException("test close timeout");};attempt(f.workspace(),PrivateStateCodec.Kind.PREFERENCES,()->s).run();}catch(Throwable e){failure[0]=e;}});
            worker.start();check(entered.await(3,TimeUnit.SECONDS),"owned close reached");f.owner.freeze();rejects(f::claim,"blocked close is still registered running ownership");release.countDown();worker.join(4000);check(!worker.isAlive()&&failure[0]==null,"owned close completes after release");f.claim();scenarios++;
        }
    }
    static void largeSource()throws Exception {
        try(F f=new F()){
            Path frame=f.parent.resolve("large.frame");
            InputStream payload=new InputStream(){int left=PrivateStateCodec.MAX_PAYLOAD_BYTES;public int read(){if(left==0)return -1;left--;return 'x';}public int read(byte[] b,int o,int n){if(left==0)return -1;n=Math.min(n,left);Arrays.fill(b,o,o+n,(byte)'x');left-=n;return n;}};
            try(OutputStream out=Files.newOutputStream(frame)){PrivateStateCodec.writeTab(tab(false,0),PrivateStateCodec.MAX_PAYLOAD_BYTES,payload,out);}
            int[] maximum={0},closed={0};PrivateFramePreflight.Workspace w=f.workspace();
            PreparationSources.Factory factory=()->new FilterInputStream(Files.newInputStream(frame)){
                public int read(){throw new AssertionError("raw single-byte delegate escape");}
                public int read(byte[] b,int o,int n)throws IOException{maximum[0]=Math.max(maximum[0],n);return in.read(b,o,n);}
                public void close()throws IOException{super.close();closed[0]++;}
            };
            PrivateFramePreflight.ClosedFrame r=attempt(w,PrivateStateCodec.Kind.SESSION_TAB,factory).run();
            check(r.payloadBytes==PrivateStateCodec.MAX_PAYLOAD_BYTES&&r.utf16Units==PrivateStateCodec.MAX_PAYLOAD_BYTES,"8MiB streamed through strict codec");
            check(maximum[0]<=65536&&closed[0]==1&&r.envelopeBytes==Files.size(frame),"bounded raw requests and full length");f.owner.freeze();f.claim();scenarios++;
        }
    }
    public static void main(String[] args)throws Exception {
        valid();malformed();closeFailures();sourceFailures();invalidReaders();budgetAndBounds();reentrancyAndThread();blockedOpenAndRead();blockedClose();largeSource();
        System.out.println("Private frame preflight: "+scenarios+" scenarios PASS");
    }
}
