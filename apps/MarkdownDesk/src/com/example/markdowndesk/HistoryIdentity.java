package com.example.markdowndesk;

import java.io.*;
import java.util.*;

/** Immutable exact native URI or unsaved-tab aliases. Never rewrites protected v1 records. */
public final class HistoryIdentity {
    public static final class Descriptor {
        public final String id, exactUri, tabId;
        /** Explicit old history IDs from successful Save As calls; immutable, no inferred lineage. */
        public final List<String> origins;
        private Descriptor(Record record,List<String> origins){
            id=record.historyId;exactUri="URI".equals(record.namespace)?record.alias:null;
            tabId="TAB".equals(record.namespace)?record.alias:null;this.origins=Collections.unmodifiableList(new ArrayList<>(origins));
        }
        public boolean matches(ProtectionStore.Snapshot snapshot){
            return exactUri!=null?exactUri.equals(snapshot.uri):HistoryMetadataIO.empty(snapshot.uri)&&tabId.equals(snapshot.documentId);
        }
    }
    private static final class Record {
        final String id,historyId,namespace,alias,oldId;
        Record(String id,String historyId,String namespace,String alias,String oldId){this.id=id;this.historyId=historyId;this.namespace=namespace;this.alias=alias;this.oldId=oldId;}
    }
    File storageRoot(){return io.root;}
    private final HistoryMetadataIO io;
    public HistoryIdentity(File metadataRoot,ProtectionStore.Durability durability)throws IOException {
        io=new HistoryMetadataIO(metadataRoot,".identity",0x4d444849,durability);
        synchronized(io.lock){try(HistoryStorage.MutationScope scope=HistoryStorage.administration(io.root)){if(!HistoryStorage.controlled(io.root))readAll();}}
    }
    public String resolve(String exactUri,String tabId)throws IOException {
        String namespace=HistoryMetadataIO.empty(exactUri)?"TAB":"URI",alias="URI".equals(namespace)?exactUri:tabId;
        validAlias(alias);
        synchronized(io.lock){try(HistoryStorage.MutationScope scope=HistoryStorage.mutation(io.root,3)){
            PrivateOperationStore.Ledger operationBefore=HistoryStorage.operationWriteProof(io.root);
            List<Record> records=readAll();Record found=binding(records,namespace,alias);if(found!=null){HistoryStorage.verifyOperationWrite(io.root,operationBefore);return found.historyId;}
            String id=UUID.randomUUID().toString();append(new Record(id,id,namespace,alias,""));HistoryStorage.verifyOperationWrite(io.root,operationBefore);return id;
        }}
    }
    /**
     * Controller precondition: invoke ONLY after SaveCoordinator.save returns verified success,
     * never on cancellation/failure. This module performs no provider write or verification.
     * Existing destination identity survives; additional origins append immutable provenance edges.
     * Metadata failure is an optional history error and must not negate the completed provider save.
     */
    public String forkAfterVerifiedSaveAs(String oldHistoryId,String destinationUri)throws IOException {
        HistoryMetadataIO.id(oldHistoryId);validAlias(destinationUri);
        synchronized(io.lock){try(HistoryStorage.MutationScope scope=HistoryStorage.mutation(io.root,3)){
            PrivateOperationStore.Ledger operationBefore=HistoryStorage.operationWriteProof(io.root);
            List<Record> records=readAll();if(byId(records,oldHistoryId)==null)throw new IOException("Unknown Save As origin history");
            Record target=binding(records,"URI",destinationUri);
            if(target==null){String id=UUID.randomUUID().toString();append(new Record(id,id,"URI",destinationUri,oldHistoryId));HistoryStorage.verifyOperationWrite(io.root,operationBefore);return id;}
            if(target.historyId.equals(oldHistoryId)){HistoryStorage.verifyOperationWrite(io.root,operationBefore);return target.historyId;}
            for(Record record:records)if(record.historyId.equals(target.historyId)&&record.oldId.equals(oldHistoryId)){HistoryStorage.verifyOperationWrite(io.root,operationBefore);return target.historyId;}
            append(new Record(UUID.randomUUID().toString(),target.historyId,"FORK",destinationUri,oldHistoryId));HistoryStorage.verifyOperationWrite(io.root,operationBefore);return target.historyId;
        }}
    }
    public Descriptor lookup(String historyId)throws IOException {
        HistoryMetadataIO.id(historyId);
        for(Descriptor descriptor:descriptors())if(descriptor.id.equals(historyId))return descriptor;
        throw new IOException("Unknown history identity");
    }
    /** Complete validated immutable metadata, suitable for background indexing; no document bytes. */
    public List<Descriptor> descriptors()throws IOException {
        synchronized(io.lock){return descriptors(HistoryStorage.readView(io.root));}
    }
    List<Descriptor> descriptors(HistoryTombstoneView view)throws IOException {
        synchronized(io.lock){
            List<Record> records=readAll(view);List<Descriptor> result=new ArrayList<>();
            for(Record record:records)if(!"FORK".equals(record.namespace)){
                Set<String> origins=new LinkedHashSet<>();
                for(Record other:records)if(other.historyId.equals(record.historyId)&&!other.oldId.isEmpty())origins.add(other.oldId);
                result.add(new Descriptor(record,new ArrayList<>(origins)));
            }return Collections.unmodifiableList(result);
        }
    }
    private void append(Record record)throws IOException {
        byte[] bytes=io.encode(out->{HistoryMetadataIO.string(out,record.id);HistoryMetadataIO.string(out,record.historyId);
            HistoryMetadataIO.string(out,record.namespace);HistoryMetadataIO.string(out,record.alias);HistoryMetadataIO.string(out,record.oldId);});
        io.commit(record.id,bytes,()->{});readAll();
    }
    private List<Record> readAll()throws IOException {return readAll(null);}
    private List<Record> readAll(HistoryTombstoneView view)throws IOException {
        List<Record> records=new ArrayList<>();Set<String> aliases=new HashSet<>();Set<String> ids=new HashSet<>();
        for(File file:io.files(view))try(DataInputStream in=io.body(file)){
            Record record=new Record(HistoryMetadataIO.string(in),HistoryMetadataIO.string(in),HistoryMetadataIO.string(in),HistoryMetadataIO.string(in),HistoryMetadataIO.string(in));
            HistoryMetadataIO.id(record.id);HistoryMetadataIO.id(record.historyId);validAlias(record.alias);
            if(!record.oldId.isEmpty())HistoryMetadataIO.id(record.oldId);
            if(in.available()!=0||!file.getName().equals(record.id+".identity"))throw new IOException("History identity framing mismatch");
            if(!Arrays.asList("URI","TAB","FORK").contains(record.namespace))throw new IOException("Unknown identity namespace");
            if("TAB".equals(record.namespace)&&!record.oldId.isEmpty())throw new IOException("Unsaved tab cannot have Save As provenance");
            if("FORK".equals(record.namespace)){
                if(record.oldId.isEmpty()||record.id.equals(record.historyId))throw new IOException("Invalid history provenance");
            }else if(!record.id.equals(record.historyId)||!ids.add(record.historyId)||!aliases.add(record.namespace+":"+record.alias))
                throw new IOException("Conflicting immutable history binding");
            records.add(record);
        }
        Set<String> edges=new HashSet<>();
        for(Record record:records){
            if(!record.oldId.isEmpty()&&(byId(records,record.oldId)==null||record.oldId.equals(record.historyId)))throw new IOException("Dangling/self history provenance");
            if("FORK".equals(record.namespace)){
                Record target=byId(records,record.historyId);
                if(target==null||!"URI".equals(target.namespace)||!target.alias.equals(record.alias))throw new IOException("Dangling destination provenance");
            }
            if(!record.oldId.isEmpty()&&!edges.add(record.oldId+":"+record.historyId))throw new IOException("Duplicate history provenance");
        }return records;
    }
    private static Record binding(List<Record> records,String namespace,String alias){for(Record record:records)if(record.namespace.equals(namespace)&&record.alias.equals(alias))return record;return null;}
    private static Record byId(List<Record> records,String id){for(Record record:records)if(!"FORK".equals(record.namespace)&&record.historyId.equals(id))return record;return null;}
    private static void validAlias(String alias)throws IOException {HistoryMetadataIO.utf8(alias);if(alias.isEmpty())throw new IOException("Empty history alias");}
}
