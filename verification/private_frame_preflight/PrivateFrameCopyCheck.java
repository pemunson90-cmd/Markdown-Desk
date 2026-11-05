package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.markdowndesk.PrivateFramePreflightCheck.*;

/** Second pass uses real codec frames, actual owned Readers and a lazy internal disk sink. */
public final class PrivateFrameCopyCheck {
    static int cases;
    static final class Sink implements PrivateFramePreflight.ProvisionalSink {
        final Path path;final List<String> events;FileOutputStream file;int writes,closes,maxWrite,closeFailures;boolean writeFailure,closeError;Task callback;
        Sink(Path path,List<String> events){this.path=path;this.events=events;}
        public void write(byte[] b,int o,int n)throws IOException {
            writes++;maxWrite=Math.max(maxWrite,n);if(file==null)file=new FileOutputStream(path.toFile());file.write(b,o,n);
            if(writeFailure)throw new IOException("sink write");
        }
        public void close()throws IOException {
            closes++;events.add("sink-close");
            try{if(callback!=null)callback.run();}catch(IOException e){throw e;}catch(RuntimeException|Error e){throw e;}catch(Exception e){throw new IOException(e);}
            if(closeFailures-->0){if(closeError)throw new AssertionError("sink close error");throw new IOException("sink close");}
            if(file!=null){file.close();file=null;}
        }
    }
    static PrivateFramePreflight.ClosedFrame expected(F f,byte[] bytes)throws IOException {
        return attempt(f.workspace(),PrivateStateCodec.Kind.SESSION_TAB,()->new Source(bytes)).run();
    }
    static void exactAndBinding()throws Exception {
        try(F f=new F();F foreign=new F()){
            byte[] frame=tabFrame();PrivateFramePreflight.ClosedFrame expected=expected(f,frame),sameBytesOther=expected(f,frame);
            List<String> events=new ArrayList<>();Sink sink=new Sink(f.parent.resolve("copy.stage"),events);Source source=new Source(frame);source.onClose=()->events.add("source-close");
            PrivateFramePreflight.Workspace workspace=f.workspace();PrivateFramePreflight.CopyAttempt a=workspace.copyAttempt(expected,()->source,sink);
            check(!Files.exists(sink.path)&&source.readCalls==0,"copy construction has zero sink/source IO");
            PrivateFramePreflight.CopiedFrame proof=a.run();proof.validate(f.owner,expected,sink);
            rejects(()->proof.validate(foreign.owner,expected,sink),"copy owner identity");rejects(()->proof.validate(f.owner,sameBytesOther,sink),"exact original frame identity even equal bytes");rejects(()->proof.validate(f.owner,expected,new Sink(f.parent.resolve("other"),events)),"exact sink/token identity");
            check(events.equals(Arrays.asList("sink-close","source-close")),"sink physical close precedes source close");check(Arrays.equals(frame,Files.readAllBytes(sink.path)),"sink receives whole raw frame including header/checksum");check(sink.maxWrite<=65536,"bounded raw sink writes");rejects(a::run,"copy single use");f.owner.freeze();f.claim();cases++;
        }
        try(F f=new F();F other=new F()){
            PrivateFramePreflight.ClosedFrame wrong=expected(other,tabFrame());Sink sink=new Sink(f.parent.resolve("none"),new ArrayList<>());int[] opens={0};
            rejects(()->f.workspace().copyAttempt(wrong,()->{opens[0]++;return null;},sink),"foreign expected frame before callbacks");check(opens[0]==0&&!Files.exists(sink.path),"foreign expected frame cannot open anything");cases++;
        }
    }
    static void nonTabsAndUncertainOpen()throws Exception {
        for(PrivateStateCodec.Kind kind:new PrivateStateCodec.Kind[]{PrivateStateCodec.Kind.RECOVERY,PrivateStateCodec.Kind.PREFERENCES})try(F f=new F()){
            byte[] frame=kind==PrivateStateCodec.Kind.RECOVERY?recoveryFrame():preferencesFrame();
            PrivateFramePreflight.ClosedFrame expected=attempt(f.workspace(),kind,()->new Source(frame)).run();Sink sink=new Sink(f.parent.resolve("other-kind.stage"),new ArrayList<>());
            f.workspace().copyAttempt(expected,()->new Source(frame),sink).run().validate(f.owner,expected,sink);
            check(Arrays.equals(frame,Files.readAllBytes(sink.path))&&expected.sourceTabIdHash==null,"non-tab second pass preserves exact validated metadata/whole frame");f.owner.freeze();f.claim();cases++;
        }
        try(F f=new F()){
            PrivateFramePreflight.ClosedFrame expected=expected(f,tabFrame());Sink sink=new Sink(f.parent.resolve("unopened.stage"),new ArrayList<>());
            PrivateFramePreflight.CopyAttempt a=f.workspace().copyAttempt(expected,()->{throw new IOException("uncertain source open");},sink);
            rejects(a::run,"copy factory uncertainty");check(sink.writes==0&&sink.closes==0&&!Files.exists(sink.path),"source factory failure never acquires lazy sink FD");rejects(a::retryClose,"unknown source has no fabricated close handle");f.owner.freeze();rejects(f::claim,"uncertain copy source permanently blocks source owner");cases++;
        }
    }
    static void badSecondPass()throws Exception {
        for(String fault:new String[]{"changed-valid","trailing","truncated","write"})try(F f=new F()){
            byte[] original=tabFrame(),changed=original.clone();
            if(fault.equals("changed-valid")){changed[changed.length-33]^=1;changed=seal(changed);}
            if(fault.equals("trailing"))changed=Arrays.copyOf(changed,changed.length+1);
            if(fault.equals("truncated"))changed=Arrays.copyOf(changed,changed.length-1);
            PrivateFramePreflight.ClosedFrame expected=expected(f,original);Sink sink=new Sink(f.parent.resolve("failed.stage"),new ArrayList<>());sink.writeFailure=fault.equals("write");Source source=new Source(changed);
            PrivateFramePreflight.Workspace workspace=f.workspace();PrivateFramePreflight.CopyAttempt a=workspace.copyAttempt(expected,()->source,sink);rejects(a::run,"second pass "+fault);
            check(source.closeCalls==1&&sink.closes==1,"failure actually closes sink then source");check(Files.size(sink.path)<=expected.envelopeBytes,"overlength byte never forwarded to sink");rejects(a::run,"failed copy cannot revive");rejects(()->workspace.copyAttempt(expected,()->source,sink),"copy workspace terminal");a.retryClose();f.owner.freeze();f.claim();cases++;
        }
    }
    static void retainedCloseOrder()throws Exception {
        for(boolean error:new boolean[]{false,true})try(F f=new F()){
            byte[] frame=tabFrame();PrivateFramePreflight.ClosedFrame expected=expected(f,frame);List<String> events=new ArrayList<>();Sink sink=new Sink(f.parent.resolve("retry.stage"),events);sink.closeFailures=2;sink.closeError=error;Source source=new Source(frame);source.onClose=()->events.add("source-close");
            PrivateFramePreflight.CopyAttempt a=f.workspace().copyAttempt(expected,()->source,sink);
            if(error){try{a.run();throw new AssertionError("accepted sink Error");}catch(AssertionError actual){check(actual.getMessage().equals("sink close error"),"actual sink close Error");}}
            else rejects(a::run,"sink close failure");
            check(source.closeCalls==0&&sink.file!=null,"failed sink close retains both original handles");f.owner.freeze();rejects(f::claim,"live Reader protects unresolved sink");
            if(error){try{a.retryClose();throw new AssertionError("accepted retry Error");}catch(AssertionError actual){check(actual.getMessage().equals("sink close error"),"actual retry Error");}}
            else rejects(a::retryClose,"retry same sink close");check(source.closeCalls==0,"source remains live after second sink close failure");
            a.retryClose();check(sink.closes==3&&source.closeCalls==1&&events.get(events.size()-1).equals("source-close"),"same sink succeeds then same Reader actually closes");rejects(a::run,"cleanup cannot return CopiedFrame");f.claim();cases++;
        }
        try(F f=new F()){
            byte[] frame=tabFrame();PrivateFramePreflight.ClosedFrame expected=expected(f,frame);Sink sink=new Sink(f.parent.resolve("source-retry.stage"),new ArrayList<>());Source source=new Source(frame);source.closeFailures=1;
            PrivateFramePreflight.CopyAttempt a=f.workspace().copyAttempt(expected,()->source,sink);rejects(a::run,"source close fails after successful sink close");check(sink.closes==1&&sink.file==null,"sink physically closed exactly once");f.owner.freeze();rejects(f::claim,"failed Reader close still live");a.retryClose();check(sink.closes==1&&source.closeCalls==2,"retry only remaining source handle");f.claim();cases++;
        }
    }
    static void validationAndCloseFailure()throws Exception {
        try(F f=new F()){
            byte[] frame=tabFrame(),changed=frame.clone();changed[changed.length-33]^=1;changed=seal(changed);
            PrivateFramePreflight.ClosedFrame expected=expected(f,frame);Sink sink=new Sink(f.parent.resolve("both-failed.stage"),new ArrayList<>());sink.closeFailures=1;Source source=new Source(changed);
            PrivateFramePreflight.CopyAttempt a=f.workspace().copyAttempt(expected,()->source,sink);IOException error=rejects(a::run,"changed valid bytes and sink close failure");
            check(error.getSuppressed().length==1&&source.closeCalls==0,"validation failure retains sink-close cause and live source");a.retryClose();check(source.closeCalls==1&&sink.closes==2,"cleanup closes original pair without revival");rejects(a::run,"combined failure never returns copy proof");f.owner.freeze();f.claim();cases++;
        }
    }
    static void guardsAndBudget()throws Exception {
        try(F f=new F()){
            byte[] frame=tabFrame();PrivateFramePreflight.ClosedFrame expected=expected(f,frame);Sink sink=new Sink(f.parent.resolve("guard.stage"),new ArrayList<>());Source source=new Source(frame);
            sink.callback=()->{rejects(f.owner::freeze,"sink participant cannot freeze");rejects(f::claim,"sink participant cannot claim");};
            PrivateFramePreflight.Workspace workspace=f.workspace();PrivateFramePreflight.CopyAttempt a=workspace.copyAttempt(expected,()->source,sink);sink.callback=()->{rejects(a::retryClose,"sink close cannot recursively run cleanup");rejects(f.owner::freeze,"sink participant cannot freeze");
                synchronized(HistoryStorage.monitorFor(f.root)){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(f.root)){f.owner.publicationCallbackLocked(()->{try{rejects(f::claim,"nested durability cannot claim");}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}});}}
            };
            final Throwable[] failed={null};Thread foreign=new Thread(()->{try{a.run();failed[0]=new AssertionError("foreign run accepted");}catch(IOException correct){}catch(Throwable problem){failed[0]=problem;}});foreign.start();foreign.join();check(failed[0]==null,"foreign run rejects without poisoning owner");a.run();f.owner.freeze();f.claim();cases++;
        }
        try(F f=new F()){
            PrivateFramePreflight.ClosedFrame expected=expected(f,tabFrame());PrivateFramePreflight.Workspace workspace=f.workspace();Sink sink=new Sink(f.parent.resolve("unopened"),new ArrayList<>());
            long owner=2048L+f.root.getPath().length()*2L;f.budget.charge(HistoryStorage.METADATA_BUDGET-owner-2*PrivateFramePreflight.WORKSPACE_BYTES-PrivateFramePreflight.ATTEMPT_BYTES-512-3000);
            int[] opens={0};rejects(()->workspace.copyAttempt(expected,()->{opens[0]++;return null;},sink),"copy extra metadata reservation before allocation/open");check(opens[0]==0&&!Files.exists(sink.path),"rejected copy opens no resources");cases++;
        }
    }
    static void large()throws Exception {
        try(F f=new F()){
            Path frame=f.parent.resolve("large.frame");InputStream payload=new InputStream(){int left=PrivateStateCodec.MAX_PAYLOAD_BYTES;public int read(){if(left==0)return -1;left--;return 'x';}public int read(byte[] b,int o,int n){if(left==0)return -1;n=Math.min(n,left);Arrays.fill(b,o,o+n,(byte)'x');left-=n;return n;}};
            try(OutputStream out=Files.newOutputStream(frame)){PrivateStateCodec.writeTab(tab(false,0),PrivateStateCodec.MAX_PAYLOAD_BYTES,payload,out);}
            PrivateFramePreflight.ClosedFrame expected=attempt(f.workspace(),PrivateStateCodec.Kind.SESSION_TAB,()->Files.newInputStream(frame)).run();Sink sink=new Sink(f.parent.resolve("large.stage"),new ArrayList<>());
            PrivateFramePreflight.CopiedFrame proof=f.workspace().copyAttempt(expected,()->Files.newInputStream(frame),sink).run();proof.validate(f.owner,expected,sink);
            java.security.MessageDigest digest=HistoryStorage.digest();byte[] buffer=new byte[65536];try(InputStream in=Files.newInputStream(sink.path)){for(int n;(n=in.read(buffer))>=0;)digest.update(buffer,0,n);}
            check(expected.envelopeHash.equals(HistoryStorage.hex(digest.digest()))&&Files.size(sink.path)==expected.envelopeBytes&&sink.maxWrite<=65536,"8MiB raw copy bounded and exact");f.owner.freeze();f.claim();cases++;
        }
    }
    public static void main(String[] args)throws Exception {exactAndBinding();nonTabsAndUncertainOpen();badSecondPass();retainedCloseOrder();validationAndCloseFailure();guardsAndBudget();large();System.out.println("Private frame copy: "+cases+" cases PASS");}
}
