package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.Objects;

/**
 * Validates immutable raw private-state frames in preflight and exact second-pass copy.
 * Copies only to a trusted writer-owned provisional sink, without promotion,
 * source regeneration, table selection, BEGIN or captured-consumer authority.
 * Factory/capture immutability and its retained memory are the producer's contract;
 * this helper validates the actual bytes, not a factory's name or claimed length.
 */
final class PrivateFramePreflight {
    static final long WORKSPACE_BYTES=512L<<10, ATTEMPT_BYTES=2048, COPY_EXTRA_BYTES=2048;
    private static final int MAX_ATTEMPTS=14, BULK_BYTES=65536;
    private PrivateFramePreflight(){}
    /** Trusted internal token-backed sink: construction is IO-free; first write opens lazily.
     * close proves actual physical closure only, never promotion or capture authority.
     * The writer separately charges/owns sink state, its FD and any failed-open uncertainty.
     */
    interface ProvisionalSink {
        void write(byte[] bytes,int offset,int length)throws IOException;
        void close()throws IOException;
    }

    /**
     * Same-original-budget admission happens before workspace allocation/callbacks.
     * 512KiB covers one serialized codec read: metadata<=65483B, transfer65536B,
     * existing codec UTF8 carry65540B, chars32768*2B, six conservative string
     * decodes at4096+8192+8192B each, plus wrapper/digest/DTO/map headroom.
     * The reviewed codec's 65540B carry is an explicit existing exception: helper
     * buffers and provider bulk requests are <=65536B. This is conservative live
     * metadata admission, not measured heap/GC or whole-capture budget proof.
     * The caller separately charges all captures, concurrent metadata and copies.
     */
    static Workspace create(PreparationSources owner,HistoryStorage.Budget sharedBudget)throws IOException {
        participant();
        if(owner==null||sharedBudget==null)throw invalid("Missing owner/budget");
        owner.reserveMetadata(sharedBudget,WORKSPACE_BYTES);
        return new Workspace(owner,sharedBudget);
    }
    static final class Workspace {
        private final PreparationSources owner;
        private final HistoryStorage.Budget budget;
        private final Thread thread=Thread.currentThread();
        private int admissions;
        private boolean failed;
        private Attempt active;
        private Workspace(PreparationSources owner,HistoryStorage.Budget budget){this.owner=owner;this.budget=budget;}
        private void check()throws IOException {
            participant();if(Thread.currentThread()!=thread)throw invalid("Workspace thread mismatch");
        }
        /**
         * Root supplies generated IDs and the final kind/ordinal. Whole table order,
         * cardinality, recovery/preferences presence and cross-row ID uniqueness
         * remain root obligations. No source is opened by attempt creation.
         * Exactly one active attempt and at most14 total attempts per workspace.
         */
        Attempt attempt(PrivateStateCodec.Kind kind,int ordinal,UUID generatedItemId,UUID generatedArtifactId,
                PreparationSources.Factory immutableFrameFactory)throws IOException {
            return admit(kind,ordinal,generatedItemId,generatedArtifactId,immutableFrameFactory,null,null);
        }
        CopyAttempt copyAttempt(ClosedFrame expected,PreparationSources.Factory immutableFrameFactory,ProvisionalSink sink)throws IOException {
            check();if(expected==null||!expected.belongsTo(owner)||sink==null)throw invalid("Foreign expected frame or missing internal sink");
            Attempt core=admit(expected.kind,expected.ordinal,expected.itemId,expected.artifactId,immutableFrameFactory,expected,sink);
            return new CopyAttempt(core);
        }
        private Attempt admit(PrivateStateCodec.Kind kind,int ordinal,UUID generatedItemId,UUID generatedArtifactId,
                PreparationSources.Factory immutableFrameFactory,ClosedFrame expected,ProvisionalSink sink)throws IOException {
            check();
            if(failed||active!=null||admissions==MAX_ATTEMPTS)throw invalid("Workspace failed, active or exhausted");
            if(kind==null||immutableFrameFactory==null||ordinal<0
                    ||ordinal>(kind==PrivateStateCodec.Kind.SESSION_TAB?11:0)
                    ||!generated(generatedItemId)||!generated(generatedArtifactId)||generatedItemId.equals(generatedArtifactId))
                throw invalid("Invalid frozen frame request");
            owner.reserveMetadata(budget,ATTEMPT_BYTES+(expected==null?0:COPY_EXTRA_BYTES));
            admissions++;active=new Attempt(this,kind,ordinal,generatedItemId,generatedArtifactId,immutableFrameFactory,expected,sink);
            return active;
        }
    }
    /** Scalar result only; no codec Record, payload, factory or raw delegate escapes. */
    static final class ClosedFrame {
        private final PreparationSources owner;
        final PrivateStateCodec.Kind kind;
        final int ordinal,payloadBytes,utf16Units;
        final UUID itemId,artifactId;
        final long envelopeBytes;
        final String envelopeHash,sourceTabIdHash;
        private final String scalarCommitment;
        private ClosedFrame(Attempt a,PrivateStateCodec.Record decoded,long length,String hash)throws IOException {
            scalarCommitment=PrivateStateCodec.scalarCommitment(decoded.kind,decoded.tab,decoded.recovery,decoded.preferences);
            owner=a.workspace.owner;kind=a.kind;ordinal=a.ordinal;itemId=a.itemId;artifactId=a.artifactId;
            envelopeBytes=length;envelopeHash=hash;payloadBytes=decoded.payloadBytes;utf16Units=decoded.utf16Units;
            // readString already proved strict UTF8; retain only a bounded identity
            // key so assembly can reject duplicate historical tab IDs before PREPARE.
            sourceTabIdHash=decoded.kind==PrivateStateCodec.Kind.SESSION_TAB?
                HistoryStorage.hex(HistoryStorage.digest().digest(decoded.tab.sourceTabId.getBytes(StandardCharsets.UTF_8))):null;
        }
        boolean belongsTo(PreparationSources expectedOwner){return owner==expectedOwner;}
        boolean matchesScalars(String expected){return scalarCommitment.equals(expected);}
    }
    /** Byte/actual-close evidence for exactly one expected frame and internal sink token.
     * No stage promotion, durable acknowledgement or captured-bundle authority.
     */
    static final class CopiedFrame {
        private final PreparationSources owner;
        private final ClosedFrame expected;
        private final ProvisionalSink sink;
        private CopiedFrame(PreparationSources owner,ClosedFrame expected,ProvisionalSink sink){this.owner=owner;this.expected=expected;this.sink=sink;}
        void validate(PreparationSources originalOwner,ClosedFrame originalFrame,ProvisionalSink originalSink)throws IOException {
            if(owner!=originalOwner||expected!=originalFrame||sink!=originalSink)throw invalid("Foreign copied-frame owner/frame/sink identity");
        }
    }
    static final class CopyAttempt {
        private final Attempt core;
        private CopyAttempt(Attempt core){this.core=core;}
        CopiedFrame run()throws IOException{return (CopiedFrame)core.runCore();}
        void retryClose()throws IOException{core.retryClose();}
    }
    /** Both passes share this one validation/ordered-close core. Keep the handle on failure. */
    static final class Attempt {
        private static final int NEW=0,RUNNING=1,SUCCEEDED=2,FAILED=3;
        private final Workspace workspace;
        private final PrivateStateCodec.Kind kind;
        private final int ordinal;
        private final UUID itemId,artifactId;
        private final ClosedFrame expected;
        private final ProvisionalSink sinkIdentity;
        private ProvisionalSink sink;
        private PreparationSources.Factory factory;
        private PreparationSources.Reader reader;
        private int state=NEW;
        private boolean uncertainOpen;
        private Attempt(Workspace w,PrivateStateCodec.Kind kind,int ordinal,UUID item,UUID artifact,PreparationSources.Factory factory,
                ClosedFrame expected,ProvisionalSink sink){
            workspace=w;this.kind=kind;this.ordinal=ordinal;itemId=item;artifactId=artifact;this.factory=factory;this.expected=expected;this.sink=sink;sinkIdentity=sink;
        }
        ClosedFrame run()throws IOException {
            if(expected!=null)throw invalid("Copy attempt requires its bound copy result");return (ClosedFrame)runCore();
        }
        private Object runCore()throws IOException {
            workspace.check();
            if(state!=NEW||workspace.active!=this||workspace.failed)throw invalid("Attempt is not runnable");
            state=RUNNING;Throwable failure=null;Object result=null;
            try {
                PreparationSources.Factory opening=factory;factory=null;
                uncertainOpen=true;reader=workspace.owner.open(opening);uncertainOpen=false;
                RawInput input=new RawInput(this);
                PrivateStateCodec.Record decoded=PrivateStateCodec.read(input,new OutputStream(){
                    public void write(int b){}public void write(byte[] b,int o,int n){}
                });
                if(decoded.kind!=kind)throw invalid("Raw frame kind differs from frozen request");
                // The read workspace already funds decoded scalars; fund the additional
                // canonical encoder/digest and retained commitment before allocating it.
                workspace.owner.reserveMetadata(workspace.budget,PrivateStateCodec.scalarCommitmentCharge(decoded.kind,decoded.tab,decoded.recovery,decoded.preferences));
                ClosedFrame observed=new ClosedFrame(this,decoded,input.count,HistoryStorage.hex(input.digest.digest()));
                if(expected==null)result=observed;
                else {
                    if(observed.kind!=expected.kind||observed.ordinal!=expected.ordinal||!observed.itemId.equals(expected.itemId)
                            ||!observed.artifactId.equals(expected.artifactId)||observed.envelopeBytes!=expected.envelopeBytes
                            ||!observed.envelopeHash.equals(expected.envelopeHash)||observed.payloadBytes!=expected.payloadBytes
                            ||observed.utf16Units!=expected.utf16Units||!Objects.equals(observed.sourceTabIdHash,expected.sourceTabIdHash)
                            ||!observed.scalarCommitment.equals(expected.scalarCommitment))
                        throw invalid("Copied raw frame differs from original preflight");
                    result=new CopiedFrame(workspace.owner,expected,sinkIdentity);
                }
            }catch(IOException|RuntimeException|Error problem){failure=problem;}
            finally {factory=null;failure=closeOwned(failure);}
            if(failure!=null){state=FAILED;workspace.failed=true;if(reader==null&&!uncertainOpen)workspace.active=null;raise(failure);}
            state=SUCCEEDED;workspace.active=null;return result;
        }
        private Throwable closeOwned(Throwable failure) {
            // Reader admission precedes all sink IO. A failed source factory has no
            // known Reader and the lazy sink has not been touched; retain uncertainty.
            if(reader!=null&&sink!=null){
                try{ProvisionalSink closing=sink;workspace.owner.consumerCallback(closing::close);sink=null;}
                catch(IOException|RuntimeException|Error closing){failure=combine(failure,closing);}
            }
            // Never release the Reader while physical sink closure is unresolved.
            if(reader!=null&&sink==null){
                try{reader.close();reader=null;}
                catch(IOException|RuntimeException|Error closing){failure=combine(failure,closing);}
            }
            return failure;
        }
        /** Cleanup only: retry the same sink first, then the same Reader; no result revival. */
        void retryClose()throws IOException {
            workspace.check();
            if(state!=FAILED)throw invalid("No failed attempt close to retry");
            if(reader==null){if(uncertainOpen)throw invalid("Factory ownership is uncertain; no known reader");return;}
            Throwable failure=closeOwned(null);if(failure!=null)raise(failure);
            if(workspace.active==this)workspace.active=null;
        }
    }
    /** Private adapter: only the owner's bounded read/close methods are available. */
    private static final class RawInput extends InputStream {
        private final Attempt attempt;
        private final PreparationSources.Reader reader;
        private final byte[] one=new byte[1];
        private final long maximum;
        private final MessageDigest digest;
        private long count;
        RawInput(Attempt attempt)throws IOException {
            this.attempt=attempt;reader=attempt.reader;
            maximum=attempt.expected!=null?attempt.expected.envelopeBytes:attempt.kind==PrivateStateCodec.Kind.PREFERENCES?PrivateStateCodec.MAX_PREFERENCES_BYTES:
                (long)PrivateStateCodec.MAX_PAYLOAD_BYTES+PrivateStateCodec.MAX_OVERHEAD_BYTES;
            digest=HistoryStorage.digest();
        }
        public int read()throws IOException {
            int n=read(one,0,1);return n<0?-1:one[0]&255;
        }
        public int read(byte[] bytes,int offset,int length)throws IOException {
            if(offset<0||length<0||offset>bytes.length-length)throw new IndexOutOfBoundsException();
            if(length==0)return 0;
            int request=(int)Math.min(Math.min(length,BULK_BYTES),maximum-count+1);
            int n=reader.read(bytes,offset,request);
            if(n< -1||n>request)throw invalid("Invalid raw source read count");
            if(n==0)throw invalid("Raw source read stalled");
            if(n<0)return -1;
            count+=n;if(count>maximum)throw invalid("Raw frame exceeds kind bound");
            digest.update(bytes,offset,n);
            if(attempt.sink!=null)attempt.workspace.owner.consumerCallback(()->attempt.sink.write(bytes,offset,n));
            return n;
        }
    }
    private static Throwable combine(Throwable failure,Throwable closing){if(failure==null)return closing;if(failure!=closing)failure.addSuppressed(closing);return failure;}
    private static boolean generated(UUID id){return id!=null&&id.version()==4&&id.variant()==2;}
    private static void participant()throws IOException {PreparationSources.rejectParticipant();HistoryOperations.rejectParticipant();}
    private static void raise(Throwable problem)throws IOException {
        if(problem instanceof IOException)throw (IOException)problem;
        if(problem instanceof RuntimeException)throw (RuntimeException)problem;
        throw (Error)problem;
    }
    private static IOException invalid(String text){return new IOException("Private frame preflight: "+text);}
}
