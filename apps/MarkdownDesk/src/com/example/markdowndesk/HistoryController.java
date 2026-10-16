package com.example.markdowndesk;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.*;

/** Synchronous native document history; call on the existing I/O queue, never the UI thread.
 * No provider, Activity, deletion, export, durable operation pin or resumable comparison.
 */
public final class HistoryController {
    private static final String LEGACY_RECOVERY="legacy-recovery";
    private final ProtectionStore store;
    private final HistoryCatalog catalog;
    private final HistoryIdentity identity;
    private final HistoryIndex index;
    private final Object monitor;

    private HistoryController(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index)throws IOException {
        this.store=store;this.catalog=catalog;this.identity=identity;this.index=index;
        monitor=HistoryStorage.monitorFor(store.storageRoot());
    }
    /** Publish only after exact shared capacity has been registered or explicitly reconciled.
     * Raw protection readers may retain the supplied handle on failure; never use a legacy
     * write fallback when any enrollment/control footprint exists or initialization failed.
     */
    public static HistoryController open(ProtectionStore store,ProtectionStore.Durability durability)throws IOException {
        if(store==null||durability==null)throw new IOException("Missing history startup configuration");
        File source=store.storageRoot(),parent=source.getParentFile();
        File catalogRoot=new File(parent,"history_catalog"),identityRoot=new File(parent,"history_identity"),indexRoot=new File(parent,"history_index");
        HistoryStorage.siblings(source,catalogRoot,identityRoot,indexRoot);
        synchronized(HistoryStorage.monitorFor(source)) {
            // Inspect before constructing optional roots: controlled constructors never create missing roots.
            boolean controlled=HistoryStorage.controlled(source);
            HistoryCatalog catalog=new HistoryCatalog(catalogRoot,store,durability);
            HistoryIdentity identity=new HistoryIdentity(identityRoot,durability);
            HistoryIndex index=new HistoryIndex(store,catalog,identity,indexRoot,durability);
            if(controlled)HistoryCapacity.open(store,catalog,identity,index,durability);
            else HistoryCapacity.register(store,catalog,identity,index,new HistoryPolicy(64L<<20,20),durability);
            return new HistoryController(store,catalog,identity,index);
        }
    }
    /** Native writes continue to check current authority; this is not a permanent permission token. */
    public ProtectionStore protectionStore(){return store;}
    public enum LocalProtectionIssue {
        INCOMPLETE_ACCOUNTING, MUTATION_BLOCKED, OVER_QUOTA, BACKEND_DIAGNOSTICS_PRESENT
    }
    /** Immutable local accounting, not available disk space or pruning authority.
     * Incomplete totals must never be presented as a capacity allowance. */
    public static final class LocalProtectionStatus {
        public final long quotaBytes,policyGeneration,dataActualBytes,completionReservedBytes,
                operationReservedBytes,dataChargedBytes,controlActualBytes,controlReservedBytes;
        public final int automaticCount,controlActualEntries,controlReservedEntries;
        public final boolean usageComplete,mutationBlocked,overQuota;
        public final Set<LocalProtectionIssue> issues;
        private LocalProtectionStatus(HistoryCapacity.EffectivePolicy policy,HistoryCapacity.Usage usage) {
            quotaBytes=policy.quotaBytes;policyGeneration=policy.generation;automaticCount=policy.automaticCount;
            dataActualBytes=usage.dataActualBytes;completionReservedBytes=usage.completionReservedBytes;
            operationReservedBytes=usage.operationReservedBytes;dataChargedBytes=usage.dataChargedBytes;
            controlActualBytes=usage.controlActualBytes;controlReservedBytes=usage.controlReservedBytes;
            controlActualEntries=usage.controlActualEntries;controlReservedEntries=usage.controlReservedEntries;
            usageComplete=usage.usageComplete;mutationBlocked=usage.mutationBlocked;overQuota=usage.overQuota;
            EnumSet<LocalProtectionIssue> flags=EnumSet.noneOf(LocalProtectionIssue.class);
            if(!usageComplete)flags.add(LocalProtectionIssue.INCOMPLETE_ACCOUNTING);
            if(mutationBlocked)flags.add(LocalProtectionIssue.MUTATION_BLOCKED);
            if(overQuota)flags.add(LocalProtectionIssue.OVER_QUOTA);
            if(!usage.diagnostics.isEmpty())flags.add(LocalProtectionIssue.BACKEND_DIAGNOSTICS_PRESENT);
            issues=Collections.unmodifiableSet(flags);
        }
    }
    /** Read-only synchronous inspection on the existing I/O queue. Never registers,
     * reconciles or repairs authority; callback changes invalidate the entire sample.
     * Provider/storage detail is deliberately excluded from this presentation API. */
    public LocalProtectionStatus inspectLocalProtection()throws IOException {
        synchronized(monitor) {
            try {
                HistoryCapacity authority=HistoryStorage.capacity(store.storageRoot());
                if(authority==null)throw new IOException("Missing local protection authority");
                authority.requireStoreHandle(store.storageRoot());
                long generation=HistoryStorage.generation(store.storageRoot());
                HistoryCapacity.EffectivePolicy policy=authority.policy();
                HistoryCapacity.Usage usage=authority.inspect();
                if(HistoryStorage.capacity(store.storageRoot())!=authority
                        ||HistoryStorage.generation(store.storageRoot())!=generation||authority.policy()!=policy)
                    throw new IOException("Changed local protection sample");
                return new LocalProtectionStatus(policy,usage);
            }catch(IOException|RuntimeException failure){throw new IOException("Local protection status is unavailable. Try again after storage recovery.");}
        }
    }
    public String resolveDocument(String exactUri,String tabId)throws IOException {
        document(tabId);nullable(exactUri);return identity.resolve(exactUri,tabId);
    }
    public List<HistoryIndex.Version> list(String historyId)throws IOException {
        synchronized(monitor) {
            List<HistoryIndex.Version> result=new ArrayList<>();
            for(HistoryIndex.Version version:index.list(historyId))if(!LEGACY_RECOVERY.equals(version.documentId))result.add(version);
            return Collections.unmodifiableList(result);
        }
    }
    public static final class CapturedRevision {
        public final String tabId,historyId,exactUri,title;
        public final long revision;
        private final byte[] strictUtf8;
        public CapturedRevision(String tabId,String historyId,String exactUri,String title,long revision,byte[] strictUtf8)throws IOException {
            document(tabId);HistoryMetadataIO.id(historyId);nullable(exactUri);nullable(title);
            if(revision<0)throw new IOException("Invalid captured revision");
            if(strictUtf8==null||strictUtf8.length>ProtectionStore.MAX_DOCUMENT_BYTES)throw new IOException("Captured revision exceeds the 8 MiB safety limit");
            // Validate the exact owned bytes, not a caller array that could change before copying.
            byte[] owned=strictUtf8.clone();validateBytes(owned);
            this.tabId=tabId;this.historyId=historyId;this.exactUri=exactUri;this.title=title;this.revision=revision;
            this.strictUtf8=owned;
        }
    }
    public static final class NameResult {
        public final String snapshotId,diagnostic;
        public final HistoryCatalog.Checkpoint checkpoint;
        private NameResult(String snapshotId,HistoryCatalog.Checkpoint checkpoint,String diagnostic){
            this.snapshotId=snapshotId;this.checkpoint=checkpoint;this.diagnostic=diagnostic;
        }
        public boolean named(){return checkpoint!=null;}
    }
    /** A known snapshot survives optional label failure, including an uncertain committed label. */
    public NameResult nameCapturedRevision(CapturedRevision capture,String label)throws IOException {
        if(capture==null)throw new IOException("Missing captured revision");
        byte[] encoded=HistoryMetadataIO.utf8(label);
        if(encoded.length>256||label.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)))
            throw new IOException("Checkpoint label must be nonblank and at most 256 UTF-8 bytes");
        synchronized(monitor) {
            HistoryIdentity.Descriptor owner=identity.lookup(capture.historyId);
            if(!matches(owner,capture.exactUri,capture.tabId))throw new IOException("Captured revision does not belong to this history");
            ProtectionStore.Snapshot snapshot=store.putSnapshot(capture.tabId,capture.exactUri,capture.title,
                    capture.revision,capture.strictUtf8,"attempted");
            try{return new NameResult(snapshot.id,catalog.name(snapshot.id,label),"");}
            catch(Exception failure){return new NameResult(snapshot.id,null,"Captured version retained; checkpoint label outcome is unconfirmed. "+diagnostic(failure));}
        }
    }
    /** Single-process, nonresumable comparison. No handles escape the shared monitor/context. */
    public HistoryDiff.Preview preview(String historyId,String leftId,String rightId)throws IOException {
        HistoryMetadataIO.id(leftId);HistoryMetadataIO.id(rightId);
        synchronized(monitor) {
            return store.withValidatedSources(sources->{
                if(!sources.inventory.complete)throw new IOException("Incomplete history sources");
                boolean left=false,right=false;
                for(HistoryIndex.Version version:list(historyId)) {
                    left|=version.snapshotId.equals(leftId);right|=version.snapshotId.equals(rightId);
                }
                if(!left||!right)throw new IOException("Comparison versions do not belong to this document history");
                return new HistoryDiff().compare(sources.snapshot(store,leftId),sources.snapshot(store,rightId));
            });
        }
    }
    /** Frozen by the Activity before URI adoption; source URI is provenance, never inferred from bytes. */
    public static final class SaveRoute {
        public final String tabId,sourceUri,destinationUri;
        public final long revision;
        public SaveRoute(String tabId,String sourceUri,String destinationUri,long revision) {
            this.tabId=tabId;this.sourceUri=sourceUri;this.destinationUri=destinationUri;this.revision=revision;
        }
    }
    public static final class IndexResult {
        public final String historyId,diagnostic;
        public final boolean indexed;
        private IndexResult(String historyId,boolean indexed,String diagnostic){this.historyId=historyId;this.indexed=indexed;this.diagnostic=diagnostic;}
    }
    /** Call only after SaveCoordinator returns success. Independently checks actual completion before
     * any alias/fork publication. Optional failures are data here, never a failed provider result.
     */
    public IndexResult recordVerifiedSave(SaveRoute route,String transactionId) {
        String historyId=null;
        try {
            if(route==null)throw new IOException("Missing captured save route");
            document(route.tabId);nullable(route.sourceUri);HistoryMetadataIO.utf8(route.destinationUri);
            if(HistoryMetadataIO.empty(route.destinationUri)||route.revision<0)throw new IOException("Invalid captured destination/revision");
            HistoryMetadataIO.id(transactionId);
            synchronized(monitor) {
                store.withValidatedSources(sources->{
                    if(!sources.inventory.complete)throw new IOException("Incomplete history sources");
                    for(ProtectionStore.Transaction transaction:sources.inventory.transactions)if(transaction.id.equals(transactionId)) {
                        if(!transaction.verified||!savedRouteMatches(route,transaction.original)||!savedRouteMatches(route,transaction.attempted))
                            throw new IOException("Save route requires matching actual verified completion");
                        return null;
                    }
                    throw new IOException("Missing verified save transaction");
                });
                if(!Objects.equals(route.sourceUri,route.destinationUri)) {
                    String origin=identity.resolve(route.sourceUri,route.tabId);
                    historyId=identity.forkAfterVerifiedSaveAs(origin,route.destinationUri);
                } else historyId=identity.resolve(route.destinationUri,route.tabId);
                index.recordVerified(historyId,transactionId);
                return new IndexResult(historyId,true,"");
            }
        }catch(Exception failure){return new IndexResult(historyId,false,"Optional history indexing was not confirmed. "+diagnostic(failure));}
    }
    private static boolean savedRouteMatches(SaveRoute route,ProtectionStore.Snapshot snapshot) {
        return route.tabId.equals(snapshot.documentId)&&route.destinationUri.equals(snapshot.uri)&&route.revision==snapshot.revision;
    }
    private static boolean matches(HistoryIdentity.Descriptor owner,String uri,String tab) {
        return owner.exactUri!=null?owner.exactUri.equals(uri):HistoryMetadataIO.empty(uri)&&owner.tabId.equals(tab);
    }
    private static void document(String tabId)throws IOException {
        HistoryMetadataIO.utf8(tabId);
        if(HistoryMetadataIO.empty(tabId)||LEGACY_RECOVERY.equals(tabId))throw new IOException("Invalid document history tab identity");
    }
    private static void nullable(String value)throws IOException{if(value!=null)HistoryMetadataIO.utf8(value);}
    private static String diagnostic(Exception failure){String message=failure.getMessage();return message==null?failure.getClass().getSimpleName():message;}
    private static void validateBytes(byte[] bytes)throws IOException {
        CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input=ByteBuffer.wrap(bytes);CharBuffer scratch=CharBuffer.allocate(4096);
        while(true){CoderResult result=decoder.decode(input,scratch,true);if(result.isError())result.throwException();if(result.isUnderflow())break;scratch.clear();}
        scratch.clear();CoderResult result=decoder.flush(scratch);if(result.isError())result.throwException();
    }
}
