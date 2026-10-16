package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Versioned MDOP framing only. Decoding never acknowledges a capture or authorizes publication. */
final class PrivateOperationRecord {
    static final int PREPARE=1,LINK=2,DESCRIPTOR=3,MAX_CONTROL=16384,LINK_BYTES=173,BEGIN_BYTES=204;
    static final long MAX_METADATA=16L<<20;
    static final UUID ZERO=new UUID(0,0);
    static final class Binding {
        final UUID preparation,bundle;
        final int phase,automaticCount,flags;
        final long generation,bootstrapQuota,quota;
        final String registrationHash,policyHash,sourceHash,ledgerHash;
        final List<HistoryControlStore.Role> roles;
        Binding(UUID preparation,int phase,UUID bundle,String registration,long generation,String policy,long bootstrap,long quota,int automatic,
                List<HistoryControlStore.Role> roles,String source,String ledger,int flags)throws IOException {
            this(preparation,phase,bundle,registration,generation,policy,bootstrap,quota,automatic,roles,source,ledger,flags,false);
        }
        static Binding portable(UUID preparation,UUID bundle,String registration,long generation,String policy,long bootstrap,long quota,int automatic,
                List<HistoryControlStore.Role> roles,String source,String ledger,int flags)throws IOException {
            return new Binding(preparation,3,bundle,registration,generation,policy,bootstrap,quota,automatic,roles,source,ledger,flags,true);
        }
        private Binding(UUID preparation,int phase,UUID bundle,String registration,long generation,String policy,long bootstrap,long quota,int automatic,
                List<HistoryControlStore.Role> roles,String source,String ledger,int flags,boolean portable)throws IOException {
            generated(preparation);if(bundle==null||phase<1||phase>(portable?3:2)||portable&&phase!=3||generation<0||bootstrap<0||quota<0||automatic<0||roles==null||roles.size()!=4||flags<0||flags>3)throw invalid("Binding fields");
            if(phase==1&&flags!=0||phase>=2&&(flags&2)==0)throw invalid("Private-state presence");
            Set<String> names=new HashSet<>();for(int i=0;i<4;i++){HistoryControlStore.Role r=roles.get(i);if(r==null||r.id!=i+1||!names.add(r.basename))throw invalid("Canonical role order");new HistoryControlStore.Role(r.id,r.basename);}
            this.preparation=preparation;this.phase=phase;this.bundle=bundle;registrationHash=hash(registration);this.generation=generation;policyHash=hash(policy);
            bootstrapQuota=bootstrap;this.quota=quota;automaticCount=automatic;this.roles=Collections.unmodifiableList(new ArrayList<>(roles));sourceHash=hash(source);ledgerHash=hash(ledger);this.flags=flags;
        }
        boolean bound(HistoryControlStore.Registration registration,List<HistoryControlStore.Entry> entries) {
            if(registration==null||!bundle.equals(registration.bundleId)||!registrationHash.equals(registration.envelopeHash)||bootstrapQuota!=registration.bootstrapQuotaBytes)return false;
            for(int i=0;i<4;i++)if(!roles.get(i).basename.equals(registration.roles.get(i).basename))return false;
            for(HistoryControlStore.Entry e:entries)if(!e.stage&&e.record!=null&&e.record.generation==generation&&policyHash.equals(e.record.envelopeHash))return bundle.equals(e.record.bundleId)&&quota==e.record.quotaBytes&&automaticCount==e.record.automaticCount;
            return false;
        }
    }
    static final class Artifact {
        final int kind,ordinal;final UUID id,item;final long length;final String hash;
        Artifact(int kind,UUID id,UUID item,int ordinal,long length,String hash)throws IOException {
            this(kind,id,item,ordinal,length,hash,false);
        }
        static Artifact portable(int kind,UUID id,UUID item,int ordinal,long length,String hash)throws IOException {
            return new Artifact(kind,id,item,ordinal,length,hash,true);
        }
        private Artifact(int kind,UUID id,UUID item,int ordinal,long length,String hash,boolean portable)throws IOException {
            generated(id);if(item==null||ordinal<0||length<1)throw invalid("Artifact fields");
            long maximum;
            if(kind==1||kind==2||portable&&kind>=3&&kind<=5){if(!ZERO.equals(item)||ordinal!=0)throw invalid("Metadata artifact identity");maximum=kind==4?PrivateArchive.MAX_ARCHIVE_BYTES:MAX_METADATA;}
            else if(kind==6||kind==7||kind==8){generated(item);if(id.equals(item)||ordinal>(kind==6?11:0))throw invalid("Private item identity");maximum=kind==8?PrivateStateCodec.MAX_PREFERENCES_BYTES:PrivateStateCodec.MAX_PAYLOAD_BYTES+PrivateStateCodec.MAX_OVERHEAD_BYTES;}
            else throw invalid("Artifact kind");
            if(length>maximum)throw invalid("Artifact length");this.kind=kind;this.id=id;this.item=item;this.ordinal=ordinal;this.length=length;this.hash=hash(hash);
        }
        String filename(){return kind==1?"map-"+id+".native":kind==2?"capture-"+id+".descriptor":kind==3?"inventory-"+id+".bundle":
                kind==4?"evidence-"+id+".capsule":kind==5?"sources-"+id+".catalog":"state-"+id+".frame";}
    }
    final int version,type,directoryMask;final Binding binding;final List<Artifact> artifacts;final HistoryPinRecord begin;
    final UUID preparation,operation,inventoryOuterId;final String preparationHash,beginHash,descriptorHash,envelopeHash;
    private final byte[] bytes;
    private PrivateOperationRecord(int version,UUID inventoryOuterId,int type,Binding binding,List<Artifact> artifacts,int dirs,HistoryPinRecord begin,UUID preparation,UUID operation,String prepHash,String beginHash,String descriptorHash,byte[] bytes)throws IOException {
        this.version=version;this.inventoryOuterId=inventoryOuterId;this.type=type;this.binding=binding;this.artifacts=Collections.unmodifiableList(new ArrayList<>(artifacts));directoryMask=dirs;this.begin=begin;
        this.preparation=preparation;this.operation=operation;preparationHash=prepHash;this.beginHash=beginHash;this.descriptorHash=descriptorHash;this.bytes=bytes;envelopeHash=sha(bytes);
    }
    byte[] encoded(){return bytes.clone();}
    boolean prefix(byte[] value){if(value==null||value.length>bytes.length)return false;for(int i=0;i<value.length;i++)if(value[i]!=bytes[i])return false;return true;}
    String name(){return "prep-"+preparation+(type==PREPARE?".reserve":".link");}
    Artifact artifact(int kind){for(Artifact a:artifacts)if(a.kind==kind)return a;return null;}
    static boolean knownName(String name){
        if(name==null||!name.matches("prep-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(reserve|link)"))return false;
        try{return UUID.fromString(name.substring(5,41)).toString().equals(name.substring(5,41));}catch(IllegalArgumentException invalid){return false;}
    }
    static PrivateOperationRecord descriptor(Binding binding,Artifact map,List<Artifact> privateFrames)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeBinding(out,binding);writeArtifact(out,map);out.writeInt(privateFrames.size());for(Artifact a:privateFrames)writeArtifact(out,a);return decode(envelope(DESCRIPTOR,bytes.toByteArray()));
    }
    static PrivateOperationRecord prepare(Binding binding,int dirs,List<Artifact> artifacts,HistoryPinRecord begin)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeBinding(out,binding);out.writeByte(dirs);out.writeInt(artifacts.size());for(Artifact a:artifacts)writeArtifact(out,a);out.write(HistoryPinRecord.encode(begin));return decode(envelope(PREPARE,bytes.toByteArray()));
    }
    static PrivateOperationRecord link(PrivateOperationRecord prepare)throws IOException {
        if(prepare==null||prepare.type!=PREPARE||prepare.version!=1)throw invalid("Missing preparation");ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        uuid(out,prepare.preparation);uuid(out,prepare.operation);hash(out,prepare.envelopeHash);hash(out,prepare.beginHash);hash(out,prepare.descriptorHash);return decode(envelope(LINK,bytes.toByteArray()));
    }
    static PrivateOperationRecord descriptorPortable(Binding binding,UUID inventoryOuterId,Artifact map,List<Artifact> rows)throws IOException {
        if(binding==null||binding.phase!=3||map==null||rows==null||rows.size()>17)throw invalid("Portable descriptor input/count");
        generated(inventoryOuterId);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeBinding(out,binding);uuid(out,inventoryOuterId);
        writeArtifact(out,map);out.writeInt(rows.size());for(Artifact a:rows)writeArtifact(out,a);return decodePortable(envelope(2,DESCRIPTOR,bytes.toByteArray()));
    }
    static PrivateOperationRecord preparePortable(Binding binding,int dirs,List<Artifact> artifacts,HistoryPinRecord begin)throws IOException {
        if(binding==null||binding.phase!=3||artifacts==null||artifacts.size()>19||begin==null)throw invalid("Portable preparation input/count");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);writeBinding(out,binding);out.writeByte(dirs);
        out.writeInt(artifacts.size());for(Artifact a:artifacts)writeArtifact(out,a);out.write(HistoryPinRecord.encode(begin));return decodePortable(envelope(2,PREPARE,bytes.toByteArray()));
    }
    static PrivateOperationRecord linkPortable(PrivateOperationRecord prepare)throws IOException {
        if(prepare==null||prepare.version!=2||prepare.type!=PREPARE)throw invalid("Missing portable preparation");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);uuid(out,prepare.preparation);uuid(out,prepare.operation);
        hash(out,prepare.envelopeHash);hash(out,prepare.beginHash);hash(out,prepare.descriptorHash);return decodePortable(envelope(2,LINK,bytes.toByteArray()));
    }
    static PrivateOperationRecord decode(byte[] source)throws IOException {return decodeVersion(source,1);}
    /** Structural DTO only. Existing Store readers deliberately retain legacy decode dispatch. */
    static PrivateOperationRecord decodePortable(byte[] source)throws IOException {return decodeVersion(source,2);}
    private static PrivateOperationRecord decodeVersion(byte[] source,int version)throws IOException {
        // Maximum actual schema is smaller than MAX_CONTROL, including descriptors. Bound before clone/allocation.
        if(source==null||source.length<45||source.length>MAX_CONTROL)throw invalid("Envelope bounds");
        byte[] bytes=source.clone();if(!MessageDigest.isEqual(Arrays.copyOfRange(bytes,bytes.length-32,bytes.length),HistoryStorage.digest().digest(Arrays.copyOf(bytes,bytes.length-32))))throw invalid("Checksum");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-32));
        if(in.readInt()!=0x4d444f50||in.readInt()!=version)throw invalid("Envelope version");int type=in.readUnsignedByte();if(in.readInt()!=bytes.length-45)throw invalid("Body length");
        UUID inventoryOuter=null;Binding binding=null;List<Artifact> artifacts=new ArrayList<>();HistoryPinRecord begin=null;int dirs=0;UUID preparation,operation=null;String prepHash=null,beginHash=null,descriptorHash=null;
        if(type==LINK){if(bytes.length!=LINK_BYTES)throw invalid("LINK length");preparation=uuid(in);operation=uuid(in);generated(preparation);generated(operation);prepHash=hash(in);beginHash=hash(in);descriptorHash=hash(in);}
        else if(type==PREPARE||type==DESCRIPTOR){
            binding=readBinding(in,version);preparation=binding.preparation;
            if(type==PREPARE){dirs=in.readUnsignedByte();if(dirs!=2&&dirs!=3)throw invalid("Directory commitment");}
            else{if(version==2){inventoryOuter=uuid(in);generated(inventoryOuter);}Artifact map=readArtifact(in,version);if(map.kind!=1)throw invalid("Missing native map");artifacts.add(map);}
            int count=in.readInt();if(count<0||count>(version==1?(type==PREPARE?16:14):(type==PREPARE?19:17))||count>in.available()/77)throw invalid("Artifact count");
            for(int i=0;i<count;i++)artifacts.add(readArtifact(in,version));
            if(version==1)validateArtifacts(binding,artifacts,type);else validatePortableArtifacts(binding,artifacts,type,inventoryOuter,bytes.length);
            if(type==PREPARE){
                if(in.available()!=BEGIN_BYTES)throw invalid("Exact BEGIN size");byte[] b=new byte[BEGIN_BYTES];in.readFully(b);begin=version==1?HistoryPinRecord.decode(b):HistoryPinRecord.decodePortable(b);generated(begin.operation);
                Artifact descriptor=null;for(Artifact a:artifacts)if(a.kind==2)descriptor=a;
                if(!begin.begin||!begin.sources.isEmpty()||begin.scope!=HistoryPinRecord.Scope.ALL_BUNDLE||begin.purpose!=HistoryPinRecord.Purpose.EXPORT||begin.manifest.domain!=(version==1?HistoryPinRecord.Domain.LOCAL_CAPTURE_V1:HistoryPinRecord.Domain.PORTABLE_CAPTURE_V1)
                    ||!begin.bundle.equals(binding.bundle)||!begin.registrationHash.equals(binding.registrationHash)||begin.generation!=binding.generation||!begin.policyHash.equals(binding.policyHash)
                    ||!begin.manifest.hash.equals(descriptor.hash)||begin.manifest.length!=descriptor.length||begin.manifest.count<(version==1?artifacts.size()-2:artifacts.size()-4)||begin.manifest.count>(version==1?HistoryArchiveManifest.MAX_ENTRIES+artifacts.size()-2:16380))throw invalid("Prepared BEGIN binding");
                if(version==2){Artifact catalog=null,inventory=null,evidence=null;long payload=0;for(Artifact a:artifacts){if(a.kind==3)inventory=a;if(a.kind==4)evidence=a;if(a.kind==5)catalog=a;if(a.kind>=6)payload+=a.length;}
                    if(catalog.length!=52+97*begin.manifest.count||122+inventory.length+58*begin.manifest.count+evidence.length+payload>PrivateArchive.MAX_ARCHIVE_BYTES)throw invalid("Portable archive/catalog lower bounds");}
                operation=begin.operation;beginHash=begin.envelopeHash;descriptorHash=descriptor.hash;
            }
        }else throw invalid("Frame type");
        if(in.available()!=0)throw invalid("Trailing bytes");return new PrivateOperationRecord(version,inventoryOuter,type,binding,artifacts,dirs,begin,preparation,operation,prepHash,beginHash,descriptorHash,bytes);
    }
    private static void validateArtifacts(Binding b,List<Artifact> rows,int type)throws IOException {
        Set<UUID> ids=new HashSet<>();ids.add(b.preparation);int previous=0,ordinal=-1,tabs=0,recovery=0,preferences=0;long metadata=0;
        for(Artifact a:rows){if(a.kind<previous||(a.kind==previous&&a.ordinal!=ordinal+1)||a.kind!=previous&&a.ordinal!=0||!ids.add(a.id)||!ZERO.equals(a.item)&&!ids.add(a.item))throw invalid("Artifact ordering/identity");
            previous=a.kind;ordinal=a.ordinal;if(a.kind<=2)metadata+=a.length;if(a.kind==6)tabs++;if(a.kind==7)recovery++;if(a.kind==8)preferences++;
        }
        if(rows.isEmpty()||rows.get(0).kind!=1||type==PREPARE&&(rows.size()<2||rows.get(1).kind!=2)||type==DESCRIPTOR&&rows.size()>1&&rows.get(1).kind==2||metadata>MAX_METADATA)throw invalid("Metadata table");
        if(tabs>12||recovery>1||preferences>1||recovery!=(b.flags&1)||preferences!=((b.flags>>>1)&1)||b.phase==1&&rows.size()!=(type==PREPARE?2:1))throw invalid("Private table/presence");
    }
    private static void validatePortableArtifacts(Binding b,List<Artifact> rows,int type,UUID outer,int serialized)throws IOException {
        Set<UUID> ids=new HashSet<>();ids.add(b.preparation);int prior=0,ordinal=-1,tabs=0,recovery=0,prefs=0;long metadata=type==DESCRIPTOR?serialized:0;
        int kinds=0;Artifact descriptor=null;
        for(Artifact a:rows) {
            if(a.kind<prior||a.kind==prior&&a.ordinal!=ordinal+1||a.kind!=prior&&a.ordinal!=0||!ids.add(a.id)||!ZERO.equals(a.item)&&!ids.add(a.item))throw invalid("Portable artifact order/identity");
            prior=a.kind;ordinal=a.ordinal;kinds|=1<<a.kind;
            if(a.kind==1||a.kind==2||a.kind==3||a.kind==5)metadata+=a.length;
            if(a.kind==2)descriptor=a;if(a.kind==6)tabs++;if(a.kind==7)recovery++;if(a.kind==8)prefs++;
            if(a.kind==3&&a.length<144||a.kind==4&&a.length<56||a.kind==5&&a.length<52)throw invalid("Portable artifact minimum");
        }
        int required=(1<<1)|(1<<3)|(1<<4)|(1<<5)|(1<<8);if(type==PREPARE)required|=1<<2;
        if((kinds&required)!=required||type==DESCRIPTOR&&(kinds&(1<<2))!=0||metadata>MAX_METADATA||tabs>12||recovery!=(b.flags&1)||prefs!=1)throw invalid("Portable artifact table/presence");
        int frames=tabs+recovery+prefs;
        if(type==DESCRIPTOR){if(outer==null||!ids.add(outer))throw invalid("Inventory outer identity collision");}
        else if(descriptor.length!=65L+bindingLength(b)+77L*(4+frames)||descriptor.length>MAX_CONTROL)throw invalid("Portable descriptor commitment length");
    }
    private static int bindingLength(Binding b) {
        int length=210;for(HistoryControlStore.Role role:b.roles)length+=role.basename.getBytes(StandardCharsets.UTF_8).length;return length;
    }
    static byte[] bindingBytes(Binding b)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();writeBinding(new DataOutputStream(bytes),b);return bytes.toByteArray();}
    private static void writeBinding(DataOutputStream out,Binding b)throws IOException {
        uuid(out,b.preparation);out.writeByte(b.phase);uuid(out,b.bundle);hash(out,b.registrationHash);out.writeLong(b.generation);hash(out,b.policyHash);out.writeLong(b.bootstrapQuota);out.writeLong(b.quota);out.writeInt(b.automaticCount);
        for(HistoryControlStore.Role role:b.roles){out.writeByte(role.id);byte[] name=role.basename.getBytes(StandardCharsets.UTF_8);out.writeInt(name.length);out.write(name);}hash(out,b.sourceHash);hash(out,b.ledgerHash);out.writeByte(b.flags);
    }
    private static Binding readBinding(DataInputStream in,int version)throws IOException {
        UUID prep=uuid(in);int phase=in.readUnsignedByte();UUID bundle=uuid(in);String registration=hash(in);long generation=in.readLong();String policy=hash(in);long bootstrap=in.readLong(),quota=in.readLong();int automatic=in.readInt();List<HistoryControlStore.Role> roles=new ArrayList<>();
        for(int i=0;i<4;i++){int role=in.readUnsignedByte(),length=in.readInt();if(length<1||length>64||length>in.available())throw invalid("Role length");byte[] name=new byte[length];in.readFully(name);String value=new String(name,StandardCharsets.UTF_8);if(!Arrays.equals(name,value.getBytes(StandardCharsets.UTF_8)))throw invalid("Role UTF8");roles.add(new HistoryControlStore.Role(role,value));}
        return new Binding(prep,phase,bundle,registration,generation,policy,bootstrap,quota,automatic,roles,hash(in),hash(in),in.readUnsignedByte(),version==2);
    }
    private static void writeArtifact(DataOutputStream out,Artifact a)throws IOException{out.writeByte(a.kind);uuid(out,a.id);uuid(out,a.item);out.writeInt(a.ordinal);out.writeLong(a.length);hash(out,a.hash);}
    private static Artifact readArtifact(DataInputStream in,int version)throws IOException{return new Artifact(in.readUnsignedByte(),uuid(in),uuid(in),in.readInt(),in.readLong(),hash(in),version==2);}
    private static byte[] envelope(int type,byte[] body)throws IOException {return envelope(1,type,body);}
    private static byte[] envelope(int version,int type,byte[] body)throws IOException {
        if(body.length>MAX_CONTROL-45)throw invalid("Body bound");ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4d444f50);out.writeInt(version);out.writeByte(type);out.writeInt(body.length);out.write(body);out.write(HistoryStorage.digest().digest(bytes.toByteArray()));return bytes.toByteArray();
    }
    private static void generated(UUID id)throws IOException{if(id==null||id.version()!=4||id.variant()!=2)throw invalid("Generated UUIDv4 required");}
    static String sha(byte[] bytes)throws IOException{return HistoryStorage.hex(HistoryStorage.digest().digest(bytes));}
    private static String hash(String value)throws IOException{if(value==null||!value.matches("[0-9a-f]{64}"))throw invalid("SHA256");return value;}
    private static String hash(DataInputStream in)throws IOException{byte[] b=new byte[32];in.readFully(b);return HistoryStorage.hex(b);}
    private static void hash(DataOutputStream out,String hash)throws IOException{hash(hash);for(int i=0;i<64;i+=2)out.writeByte(Integer.parseInt(hash.substring(i,i+2),16));}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static IOException invalid(String text){return new IOException("Operation record: "+text);}
}
