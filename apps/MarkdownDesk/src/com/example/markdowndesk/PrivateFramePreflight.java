package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Validates one root-producer-owned immutable raw private-state frame before PREPARE.
 * No persistence, source regeneration, table selection, BEGIN or consumer authority.
 * Factory/capture immutability and its retained memory are the producer's contract;
 * this helper validates the actual bytes, not a factory's name or claimed length.
 */
final class PrivateFramePreflight {
    static final long WORKSPACE_BYTES=512L<<10, ATTEMPT_BYTES=2048;
    private static final int MAX_ATTEMPTS=14, BULK_BYTES=65536;
    private PrivateFramePreflight(){}

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
            check();
            if(failed||active!=null||admissions==MAX_ATTEMPTS)throw invalid("Workspace failed, active or exhausted");
            if(kind==null||immutableFrameFactory==null||ordinal<0
                    ||ordinal>(kind==PrivateStateCodec.Kind.SESSION_TAB?11:0)
                    ||!generated(generatedItemId)||!generated(generatedArtifactId)||generatedItemId.equals(generatedArtifactId))
                throw invalid("Invalid frozen frame request");
            owner.reserveMetadata(budget,ATTEMPT_BYTES);
            admissions++;active=new Attempt(this,kind,ordinal,generatedItemId,generatedArtifactId,immutableFrameFactory);
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
        private ClosedFrame(Attempt a,PrivateStateCodec.Record decoded,long length,String hash)throws IOException {
            owner=a.workspace.owner;kind=a.kind;ordinal=a.ordinal;itemId=a.itemId;artifactId=a.artifactId;
            envelopeBytes=length;envelopeHash=hash;payloadBytes=decoded.payloadBytes;utf16Units=decoded.utf16Units;
            // readString already proved strict UTF8; retain only a bounded identity
            // key so assembly can reject duplicate historical tab IDs before PREPARE.
            sourceTabIdHash=decoded.kind==PrivateStateCodec.Kind.SESSION_TAB?
                HistoryStorage.hex(HistoryStorage.digest().digest(decoded.tab.sourceTabId.getBytes(StandardCharsets.UTF_8))):null;
        }
        boolean belongsTo(PreparationSources expectedOwner){return owner==expectedOwner;}
    }
    /**
     * Retain this handle after failure. An actual close failure keeps the original
     * owned Reader here and in the workspace, so retryClose can retry that close.
     * No successful result after any run failure, even after later close success.
     */
    static final class Attempt {
        private static final int NEW=0,RUNNING=1,SUCCEEDED=2,FAILED=3;
        private final Workspace workspace;
        private final PrivateStateCodec.Kind kind;
        private final int ordinal;
        private final UUID itemId,artifactId;
        private PreparationSources.Factory factory;
        private PreparationSources.Reader reader;
        private int state=NEW;
        private boolean uncertainOpen;
        private Attempt(Workspace w,PrivateStateCodec.Kind kind,int ordinal,UUID item,UUID artifact,PreparationSources.Factory factory){
            workspace=w;this.kind=kind;this.ordinal=ordinal;itemId=item;artifactId=artifact;this.factory=factory;
        }
        ClosedFrame run()throws IOException {
            workspace.check();
            if(state!=NEW||workspace.active!=this||workspace.failed)throw invalid("Attempt is not runnable");
            state=RUNNING;Throwable failure=null;ClosedFrame result=null;
            try {
                // Store the owned Reader before any read/codec callback. Never use
                // try-with-resources, which would lose the only retry-close handle.
                PreparationSources.Factory opening=factory;factory=null;
                uncertainOpen=true;reader=workspace.owner.open(opening);uncertainOpen=false;
                RawInput input=new RawInput(reader,kind);
                PrivateStateCodec.Record decoded=PrivateStateCodec.read(input,new OutputStream(){
                    public void write(int b){}public void write(byte[] b,int o,int n){}
                });
                if(decoded.kind!=kind)throw invalid("Raw frame kind differs from frozen request");
                // Codec.read requires checksum, strict UTF8/metadata and true EOF;
                // the outer digest also includes the raw checksum bytes themselves.
                result=new ClosedFrame(this,decoded,input.count,HistoryStorage.hex(input.digest.digest()));
            }catch(IOException|RuntimeException|Error problem){failure=problem;}
            finally {
                factory=null;
                if(reader!=null){
                    try{reader.close();reader=null;}
                    catch(IOException|RuntimeException|Error closing){
                        if(failure==null)failure=closing;else if(failure!=closing)failure.addSuppressed(closing);
                    }
                }
            }
            if(failure!=null){state=FAILED;workspace.failed=true;if(reader==null&&!uncertainOpen)workspace.active=null;raise(failure);}
            state=SUCCEEDED;workspace.active=null;return result;
        }
        /** Cleanup only. Successful close never revives failed validation/preflight. */
        void retryClose()throws IOException {
            workspace.check();
            if(state!=FAILED)throw invalid("No failed attempt close to retry");
            if(reader==null){if(uncertainOpen)throw invalid("Factory ownership is uncertain; no known reader");return;}
            reader.close();reader=null;if(workspace.active==this)workspace.active=null;
        }
    }
    /** Private adapter: only the owner's bounded read/close methods are available. */
    private static final class RawInput extends InputStream {
        private final PreparationSources.Reader reader;
        private final byte[] one=new byte[1];
        private final long maximum;
        private final MessageDigest digest;
        private long count;
        RawInput(PreparationSources.Reader reader,PrivateStateCodec.Kind kind)throws IOException {
            this.reader=reader;maximum=kind==PrivateStateCodec.Kind.PREFERENCES?PrivateStateCodec.MAX_PREFERENCES_BYTES:
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
            digest.update(bytes,offset,n);return n;
        }
    }
    private static boolean generated(UUID id){return id!=null&&id.version()==4&&id.variant()==2;}
    private static void participant()throws IOException {PreparationSources.rejectParticipant();HistoryOperations.rejectParticipant();}
    private static void raise(Throwable problem)throws IOException {
        if(problem instanceof IOException)throw (IOException)problem;
        if(problem instanceof RuntimeException)throw (RuntimeException)problem;
        throw (Error)problem;
    }
    private static IOException invalid(String text){return new IOException("Private frame preflight: "+text);}
}
