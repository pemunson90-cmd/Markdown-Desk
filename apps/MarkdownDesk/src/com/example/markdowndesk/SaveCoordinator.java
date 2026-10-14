package com.example.markdowndesk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** One protected save attempt; never retries or rolls back a provider mutation. */
public final class SaveCoordinator {
    public interface Provider {
        byte[] read() throws IOException;
        void write(byte[] bytes) throws IOException;
    }

    public static final class Request {
        public final String documentId, uri, title, baselineHash, consentHash;
        public final long revision;
        public final boolean newDestination;
        private final byte[] attempted;
        public Request(String documentId, String uri, String title, long revision,
                       byte[] attempted, String baselineHash, String consentHash, boolean newDestination) {
            if (attempted == null) throw new IllegalArgumentException("Missing attempted bytes");
            this.documentId=documentId; this.uri=uri; this.title=title; this.revision=revision;
            this.attempted=attempted.clone(); this.baselineHash=baselineHash;
            this.consentHash=consentHash; this.newDestination=newDestination;
        }
        public Request withConsent(String observedHash) {
            return new Request(documentId,uri,title,revision,attempted,baselineHash,observedHash,newDestination);
        }
    }

    public static final class Result {
        public final ProtectionStore.Transaction transaction;
        public final String hash;
        Result(ProtectionStore.Transaction transaction, String hash) { this.transaction=transaction; this.hash=hash; }
    }

    public static class SaveFailure extends IOException {
        public final ProtectionStore.Snapshot attempted;
        public final ProtectionStore.Transaction transaction;
        public final boolean writeMayHaveOccurred;
        SaveFailure(String message, Exception cause, ProtectionStore.Snapshot attempted,
                    ProtectionStore.Transaction transaction, boolean writeMayHaveOccurred) {
            super(message,cause); this.attempted=attempted; this.transaction=transaction;
            this.writeMayHaveOccurred=writeMayHaveOccurred;
        }
    }

    public static final class ConflictException extends SaveFailure {
        public final String observedHash;
        ConflictException(String observedHash, ProtectionStore.Snapshot attempted, ProtectionStore.Transaction transaction) {
            super("The source changed or its original version is unknown. Choose how to retain both versions.",
                    null,attempted,transaction,false);
            this.observedHash=observedHash;
        }
    }

    private final ProtectionStore store;
    public SaveCoordinator(ProtectionStore store) { if(store==null)throw new IllegalArgumentException("Missing protection store"); this.store=store; }

    public Result save(Request request, Provider provider) throws IOException {
        ProtectionStore.Snapshot attempted=null;
        ProtectionStore.Transaction transaction=null;
        boolean mutationStarted=false;
        try {
            validate(request.attempted);
            // A failed source read must not hide the captured edit behind a debounce.
            attempted=store.putSnapshot(request.documentId,request.uri,request.title,request.revision,request.attempted,"attempted");
            byte[] source=provider.read();
            validate(source);
            String observed=EditorTools.sha256(source);
            boolean consentMatches=request.consentHash!=null&&request.consentHash.equals(observed);
            if (request.consentHash!=null&&!consentMatches)
                throw new ConflictException(observed,attempted,null);
            if (!request.newDestination&&!consentMatches&&(request.baselineHash==null||!request.baselineHash.equals(observed)))
                throw new ConflictException(observed,attempted,null);
            ProtectionStore.Snapshot original=store.putSnapshot(request.documentId,request.uri,request.title,request.revision,source,"original");
            transaction=store.prepare(original,attempted,observed);
            byte[] rechecked=provider.read();
            validate(rechecked);
            String latest=EditorTools.sha256(rechecked);
            if(!observed.equals(latest))throw new ConflictException(latest,attempted,transaction);
            transaction=store.validate(transaction);
            // Generic SAF has no compare-and-swap. External mutation after this check is a documented race.
            mutationStarted=true;
            provider.write(request.attempted.clone());
            byte[] saved=provider.read();
            if(!Arrays.equals(request.attempted,saved))throw new IOException("Saved bytes did not match the submitted version.");
            store.complete(transaction);
            return new Result(transaction,EditorTools.sha256(request.attempted));
        } catch(ConflictException conflict) {
            throw conflict;
        } catch(Exception failure) {
            throw new SaveFailure(mutationStarted
                    ? "The provider save outcome is uncertain. Protected original and edited versions are retained."
                    : "Save stopped before provider mutation. Any committed edit protection is retained.",
                    failure,attempted,transaction,mutationStarted);
        }
    }

    private static void validate(byte[] bytes) throws IOException {
        if(bytes==null)throw new IOException("The provider returned no document bytes.");
        if(bytes.length>8388608)throw new IOException("This document exceeds the 8 MiB safety limit.");
        CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input=ByteBuffer.wrap(bytes);
        CharBuffer scratch=CharBuffer.allocate(8192);
        while(true) {
            CoderResult result=decoder.decode(input,scratch,true);
            if(result.isError())result.throwException();
            if(result.isUnderflow())break;
            scratch.clear();
        }
        scratch.clear();
        CoderResult result=decoder.flush(scratch);
        if(result.isError())result.throwException();
    }
}
