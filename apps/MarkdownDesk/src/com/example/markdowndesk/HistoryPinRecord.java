package com.example.markdowndesk;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded immutable operation-pin framing; never grants output/provider authority. */
final class HistoryPinRecord {
    static final int MAX_BYTES=16384,MAX_SOURCES=256,CLOSE_BYTES=126;
    static final long MAX_MANIFEST_BYTES=16L*1024*1024,MAX_MANIFEST_COUNT=65536+3*4096;
    private static final int MAGIC=0x4d44504e,HEADER=13,CHECKSUM=32;
    enum Purpose {EXPORT(1),RESTORE(2);final int wire;Purpose(int wire){this.wire=wire;}}
    enum Scope {EXACT_SUBSET(1),ALL_BUNDLE(2);final int wire;Scope(int wire){this.wire=wire;}}
    enum Domain {NATIVE_HISTORY_V1(1),LOCAL_CAPTURE_V1(2),PORTABLE_CAPTURE_V1(3);final int wire;Domain(int wire){this.wire=wire;}}
    enum Outcome {RELEASED(1),CANCELLED(2);final int wire;Outcome(int wire){this.wire=wire;}}
    static final class Manifest {
        final Domain domain;final long count,length;final String hash;
        Manifest(Domain domain,long count,long length,String hash)throws IOException {
            if(domain==null||count<0||count>MAX_MANIFEST_COUNT||length<0||length>MAX_MANIFEST_BYTES)throw invalid("Manifest bounds");
            if(domain==Domain.PORTABLE_CAPTURE_V1&&(count<1||count>16380||length<1||length>16384))throw invalid("Portable manifest bounds");
            this.domain=domain;this.count=count;this.length=length;this.hash=hash(hash);
        }
    }
    static final class Source {
        final String id,hash;final long length;
        Source(String id,long length,String hash)throws IOException {
            HistoryMetadataIO.id(id);if(length<45||length>ProtectionStore.MAX_DOCUMENT_BYTES+32768L)throw invalid("Snapshot envelope bounds");
            this.id=id;this.length=length;this.hash=hash(hash);
        }
    }
    final boolean begin;final UUID operation,bundle;
    final String registrationHash,policyHash,beginHash,manifestHash,envelopeHash;
    final long generation;final Purpose purpose;final Scope scope;final Manifest manifest;final Outcome outcome;
    final List<Source> sources;
    private HistoryPinRecord(UUID operation,UUID bundle,String registration,long generation,String policy,Purpose purpose,Scope scope,Manifest manifest,List<Source> sources,String envelopeHash)throws IOException {
        if(operation==null||bundle==null||generation<0||purpose==null||scope==null||manifest==null||sources==null||sources.size()>MAX_SOURCES)throw invalid("BEGIN fields");
        if((scope==Scope.EXACT_SUBSET&&sources.isEmpty())||(scope==Scope.ALL_BUNDLE&&!sources.isEmpty()))throw invalid("Scope/source mismatch");
        if(manifest.domain==Domain.PORTABLE_CAPTURE_V1&&(purpose!=Purpose.EXPORT||scope!=Scope.ALL_BUNDLE||!sources.isEmpty()
                ||operation.version()!=4||operation.variant()!=2))throw invalid("Portable BEGIN shape");
        String previous=null;for(Source source:sources){if(source==null||(previous!=null&&previous.compareTo(source.id)>=0))throw invalid("Noncanonical source order");previous=source.id;}
        begin=true;this.operation=operation;this.bundle=bundle;registrationHash=hash(registration);this.generation=generation;policyHash=hash(policy);this.purpose=purpose;this.scope=scope;this.manifest=manifest;
        this.sources=Collections.unmodifiableList(new ArrayList<>(sources));this.envelopeHash=envelopeHash;manifestHash=manifest.hash;beginHash=null;outcome=null;
    }
    private HistoryPinRecord(UUID operation,String beginHash,String manifestHash,Outcome outcome,String envelopeHash)throws IOException {
        if(operation==null||outcome==null)throw invalid("CLOSE fields");begin=false;this.operation=operation;this.beginHash=hash(beginHash);this.manifestHash=hash(manifestHash);this.outcome=outcome;this.envelopeHash=envelopeHash;
        bundle=null;registrationHash=null;policyHash=null;generation=-1;purpose=null;scope=null;manifest=null;sources=Collections.emptyList();
    }
    static HistoryPinRecord begin(UUID operation,HistoryControlStore.Registration registration,HistoryControlStore.Record policy,Purpose purpose,Scope scope,Manifest manifest,List<Source> sources)throws IOException {
        return decode(encode(new HistoryPinRecord(operation,registration.bundleId,registration.envelopeHash,policy.generation,policy.envelopeHash,purpose,scope,manifest,sources,null)));
    }
    /** Explicit framing factory only; no publication/activation capability is created. */
    static HistoryPinRecord beginPortable(UUID operation,HistoryControlStore.Registration registration,HistoryControlStore.Record policy,Manifest manifest)throws IOException {
        if(manifest==null||manifest.domain!=Domain.PORTABLE_CAPTURE_V1)throw invalid("Portable manifest required");
        return decodePortable(encode(new HistoryPinRecord(operation,registration.bundleId,registration.envelopeHash,policy.generation,policy.envelopeHash,
                Purpose.EXPORT,Scope.ALL_BUNDLE,manifest,Collections.emptyList(),null)));
    }
    static byte[] close(HistoryPinRecord begin,Outcome outcome)throws IOException {
        if(begin==null||!begin.begin||begin.envelopeHash==null)throw invalid("Missing BEGIN");
        return encode(new HistoryPinRecord(begin.operation,begin.envelopeHash,begin.manifestHash,outcome,null));
    }
    String name(){return "pin-"+operation+(begin?".begin":".close");}
    static boolean knownName(String name){
        if(name==null||!name.matches("pin-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(begin|close)"))return false;
        try{return UUID.fromString(name.substring(4,40)).toString().equals(name.substring(4,40));}catch(IllegalArgumentException failure){return false;}
    }
    static byte[] encode(HistoryPinRecord record)throws IOException {
        ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(body);uuid(out,record.operation);
        if(record.begin){
            uuid(out,record.bundle);out.write(unhex(record.registrationHash));out.writeLong(record.generation);out.write(unhex(record.policyHash));out.writeByte(record.purpose.wire);out.writeByte(record.scope.wire);out.writeByte(record.manifest.domain.wire);
            out.writeLong(record.manifest.count);out.writeLong(record.manifest.length);out.write(unhex(record.manifest.hash));out.writeInt(record.sources.size());
            for(Source source:record.sources){out.writeByte(1);out.writeByte(1);uuid(out,UUID.fromString(source.id));out.writeLong(source.length);out.write(unhex(source.hash));}
        }else{out.write(unhex(record.beginHash));out.write(unhex(record.manifestHash));out.writeByte(record.outcome.wire);}
        if(body.size()>MAX_BYTES-HEADER-CHECKSUM)throw invalid("Complete pin byte bound");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();out=new DataOutputStream(bytes);out.writeInt(MAGIC);out.writeInt(1);out.writeByte(record.begin?1:2);out.writeInt(body.size());body.writeTo(out);out.write(HistoryStorage.digest().digest(bytes.toByteArray()));
        if(!record.begin&&bytes.size()!=CLOSE_BYTES)throw invalid("CLOSE size invariant");return bytes.toByteArray();
    }
    static HistoryPinRecord decode(byte[] bytes)throws IOException {return decodeVersion(bytes,false);}
    /** Deliberate portable dispatch; generic control readers remain unable to activate domain3. */
    static HistoryPinRecord decodePortable(byte[] bytes)throws IOException {return decodeVersion(bytes,true);}
    private static HistoryPinRecord decodeVersion(byte[] bytes,boolean portable)throws IOException {
        if(bytes==null||bytes.length<HEADER+CHECKSUM||bytes.length>MAX_BYTES)throw invalid("Pin envelope bounds");
        if(!MessageDigest.isEqual(Arrays.copyOfRange(bytes,bytes.length-CHECKSUM,bytes.length),HistoryStorage.digest().digest(Arrays.copyOf(bytes,bytes.length-CHECKSUM))))throw invalid("Pin checksum");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-CHECKSUM));
        if(in.readInt()!=MAGIC||in.readInt()!=1)throw invalid("Pin framing");int type=in.readUnsignedByte();if(in.readInt()!=bytes.length-HEADER-CHECKSUM)throw invalid("Pin body length");
        UUID operation=uuid(in);String envelope=HistoryStorage.hex(HistoryStorage.digest().digest(bytes));HistoryPinRecord record;
        if(type==1){
            UUID bundle=uuid(in);String registration=hash(in);long generation=in.readLong();String policy=hash(in);
            Purpose purpose=purpose(in.readUnsignedByte());Scope scope=scope(in.readUnsignedByte());Domain domain=domain(in.readUnsignedByte());
            Manifest manifest=new Manifest(domain,in.readLong(),in.readLong(),hash(in));int count=in.readInt();if(count<0||count>MAX_SOURCES||count>in.available()/58)throw invalid("Pin source bound");
            List<Source> sources=new ArrayList<>();for(int i=0;i<count;i++){if(in.readUnsignedByte()!=1||in.readUnsignedByte()!=1)throw invalid("Only STORE/SNAPSHOT can be pinned");sources.add(new Source(uuid(in).toString(),in.readLong(),hash(in)));}
            record=new HistoryPinRecord(operation,bundle,registration,generation,policy,purpose,scope,manifest,sources,envelope);
        }else if(type==2){if(bytes.length!=CLOSE_BYTES)throw invalid("Noncanonical CLOSE length");record=new HistoryPinRecord(operation,hash(in),hash(in),outcome(in.readUnsignedByte()),envelope);}
        else throw invalid("Unknown pin type");
        if(record.begin&&(record.manifest.domain==Domain.PORTABLE_CAPTURE_V1)!=portable)throw invalid("Explicit pin domain dispatch required");
        if(in.available()!=0)throw invalid("Trailing pin bytes");return record;
    }
    static final class Inventory {
        final List<HistoryPinRecord> begins=new ArrayList<>(),closes=new ArrayList<>();
        final List<HistoryControlStore.Entry> stages=new ArrayList<>();
        HistoryPinRecord active;boolean valid=true,present;long reservedBytes;int reservedEntries;String fingerprint;
    }
    static Inventory inspect(List<HistoryControlStore.Entry> entries,HistoryControlStore.Registration registration)throws IOException {
        Inventory result=new Inventory();Map<UUID,HistoryPinRecord> begins=new TreeMap<>(),closes=new TreeMap<>();List<HistoryControlStore.Entry> pins=new ArrayList<>();
        Map<Long,HistoryControlStore.Record> policies=new HashMap<>();for(HistoryControlStore.Entry entry:entries)if(!entry.stage&&entry.record!=null)policies.put(entry.record.generation,entry.record);
        for(HistoryControlStore.Entry entry:entries){String name=entry.filename;if(name==null)continue;String committed=entry.stage?name.substring(0,name.length()-6):name;if(!knownName(committed))continue;
            result.present=true;pins.add(entry);if(entry.stage){result.stages.add(entry);continue;}
            HistoryPinRecord record=entry.pin;if(record==null||!record.name().equals(name)){result.valid=false;continue;}
            if((record.begin?begins:closes).put(record.operation,record)!=null)result.valid=false;
        }
        List<HistoryPinRecord> active=new ArrayList<>();
        for(HistoryPinRecord begin:begins.values()){
            if(!bound(begin,registration,policies)||!associated(begin,entries))result.valid=false;
            HistoryPinRecord close=closes.get(begin.operation);
            if(close==null)active.add(begin);else if(!matches(begin,close))result.valid=false;
        }
        for(UUID operation:closes.keySet())if(!begins.containsKey(operation))result.valid=false;
        for(HistoryControlStore.Entry stage:result.stages){
            if(stage.filename.endsWith(".begin.stage")){
                HistoryPinRecord begin=stage.pin;
                if(begin==null||!begin.begin||!stage.filename.equals(begin.name()+".stage")||begins.containsKey(begin.operation)||closes.containsKey(begin.operation)||!bound(begin,registration,policies)||!associated(begin,entries))result.valid=false;
                else active.add(begin);
            }
        }
        if(active.size()>1)result.valid=false;
        if(active.size()==1){result.active=active.get(0);result.reservedBytes=CLOSE_BYTES;result.reservedEntries=1;}
        for(HistoryControlStore.Entry stage:result.stages)if(stage.filename.endsWith(".close.stage")){
            HistoryPinRecord begin=result.active;
            if(begin==null||!stage.filename.equals("pin-"+begin.operation+".close.stage")||!begins.containsKey(begin.operation)
                    ||(!stage.pinPrefix(close(begin,Outcome.RELEASED))&&!stage.pinPrefix(close(begin,Outcome.CANCELLED))))result.valid=false;
            else{result.reservedBytes=CLOSE_BYTES-stage.length;result.reservedEntries=0;}
        }
        result.begins.addAll(begins.values());result.closes.addAll(closes.values());pins.sort(Comparator.comparing(entry->entry.filename));
        MessageDigest digest=HistoryStorage.digest();try(DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(new OutputStream(){public void write(int value){}public void write(byte[] b,int off,int length){}},digest))){
            for(HistoryControlStore.Entry entry:pins){out.writeUTF(entry.filename);out.writeLong(entry.length);out.writeUTF(entry.envelopeHash==null?"":entry.envelopeHash);}
        }result.fingerprint=HistoryStorage.hex(digest.digest());return result;
    }
    private static boolean bound(HistoryPinRecord begin,HistoryControlStore.Registration registration,Map<Long,HistoryControlStore.Record> policies){
        HistoryControlStore.Record policy=policies.get(begin.generation);return registration!=null&&begin.bundle.equals(registration.bundleId)&&begin.registrationHash.equals(registration.envelopeHash)&&policy!=null&&begin.policyHash.equals(policy.envelopeHash)&&begin.bundle.equals(policy.bundleId);
    }
    private static boolean associated(HistoryPinRecord begin,List<HistoryControlStore.Entry> entries){
        PrivateOperationRecord owner=null;
        // The physical namespace is bounded to256 entries. No second owner/index budget.
        for(HistoryControlStore.Entry entry:entries)if(entry.operation!=null&&entry.operation.type==PrivateOperationRecord.PREPARE
                &&entry.operation.operation.equals(begin.operation)){
            if(owner!=null)return false;owner=entry.operation;
        }
        return owner==null?begin.manifest.domain==Domain.NATIVE_HISTORY_V1:
                owner.beginHash.equals(begin.envelopeHash)&&owner.begin.manifest.domain==begin.manifest.domain;
    }
    static boolean matches(HistoryPinRecord begin,HistoryPinRecord close){return !close.begin&&begin.operation.equals(close.operation)&&begin.envelopeHash.equals(close.beginHash)&&begin.manifestHash.equals(close.manifestHash);}
    private static Purpose purpose(int id)throws IOException {for(Purpose value:Purpose.values())if(value.wire==id)return value;throw invalid("Unknown purpose");}
    private static Scope scope(int id)throws IOException {for(Scope value:Scope.values())if(value.wire==id)return value;throw invalid("Unknown scope");}
    private static Domain domain(int id)throws IOException {for(Domain value:Domain.values())if(value.wire==id)return value;throw invalid("Unknown domain");}
    private static Outcome outcome(int id)throws IOException {for(Outcome value:Outcome.values())if(value.wire==id)return value;throw invalid("Unknown outcome");}
    private static String hash(String value)throws IOException{if(value==null||!value.matches("[0-9a-f]{64}"))throw invalid("Pin hash");return value;}
    private static String hash(DataInputStream in)throws IOException{byte[] bytes=new byte[32];in.readFully(bytes);return HistoryStorage.hex(bytes);}
    private static byte[] unhex(String value)throws IOException{hash(value);byte[] bytes=new byte[32];for(int i=0;i<32;i++)bytes[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return bytes;}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static IOException invalid(String reason){return new IOException(reason);}
}
