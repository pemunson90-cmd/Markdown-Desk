package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Frozen local mapping for future native-history archive passes. No filesystem,
 * pinning, current-authority proof, native-content validation or export receipt.
 * Captured bindings are provenance; only a future atomic capture/pin adapter can
 * establish that Graph/control snapshots and native sources are currently valid.
 */
public final class HistoryArchiveManifest {
    public static final int MAX_ENTRIES = 16380;
    public static final long MAX_SERIALIZED_BYTES = 16L << 20, MAX_INDEX_BYTES = 16L << 20;
    public static final int INDEX_BASE_BYTES = 4096, INDEX_BYTES_PER_ENTRY = 1024;
    private static final byte[] MAGIC = "MDHMFST\n".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1, FIXED_BYTES = 212, ENTRY_BYTES = 74;
    // Existing PrivateArchive v1: header19 + terminal45; each entry header58.
    private static final int ARCHIVE_FIXED_BYTES = 64, ARCHIVE_ENTRY_BYTES = 58;

    public enum Role {
        STORE(1,"store"), CATALOG(2,"catalog"), IDENTITY(3,"identity"), INDEX(4,"index");
        public final int wireId; final String nativeName;
        Role(int wireId,String nativeName) { this.wireId=wireId;this.nativeName=nativeName; }
        static Role decode(int code)throws IOException {
            for(Role value:values()) if(value.wireId==code)return value;
            throw invalid("Unknown native role");
        }
        static Role parse(String name)throws IOException {
            for(Role value:values()) if(value.nativeName.equals(name))return value;
            throw invalid("Unknown native role");
        }
    }
    public enum Kind {
        SNAPSHOT(1,"snapshot",Role.STORE,PrivateArchive.Type.SNAPSHOT),
        TRANSACTION(2,"transaction",Role.STORE,PrivateArchive.Type.PREPARATION),
        COMPLETE(3,"complete",Role.STORE,PrivateArchive.Type.COMPLETION),
        CHECKPOINT(4,"checkpoint",Role.CATALOG,PrivateArchive.Type.CHECKPOINT),
        IDENTITY(5,"identity",Role.IDENTITY,PrivateArchive.Type.IDENTITY),
        INDEX(6,"index",Role.INDEX,PrivateArchive.Type.INDEX);
        public final int wireId;
        public final Role role;
        public final PrivateArchive.Type archiveType;
        final String nativeName;
        Kind(int wireId,String nativeName,Role role,PrivateArchive.Type archiveType) {
            this.wireId=wireId;this.nativeName=nativeName;this.role=role;this.archiveType=archiveType;
        }
        static Kind decode(int code)throws IOException {
            for(Kind value:values())if(value.wireId==code)return value;
            throw invalid("Unknown native kind");
        }
        static Kind parse(String name)throws IOException {
            for(Kind value:values())if(value.nativeName.equals(name))return value;
            throw invalid("Unknown native kind");
        }
    }
    /** Final metadata only; full-envelope hash is never the document payload hash. */
    public static final class Entry {
        public final Role role;
        public final Kind kind;
        public final UUID nativeId, archiveId;
        public final long envelopeBytes;
        private final byte[] envelopeHash;
        private Entry(Role role,Kind kind,UUID nativeId,UUID archiveId,long length,byte[] hash)throws IOException {
            descriptor(role,kind,nativeId,length,hash);requireArchiveId(archiveId);
            this.role=role;this.kind=kind;this.nativeId=nativeId;this.archiveId=archiveId;
            envelopeBytes=length;envelopeHash=hash.clone();
        }
        public byte[] envelopeHashBytes() { return envelopeHash.clone(); }
        public String envelopeHash() { return hex(envelopeHash); }
    }
    public interface IdSource { UUID next() throws IOException; }
    public final UUID bundleId;
    public final long policyGeneration, quotaBytes, nativeEnvelopeBytes, archiveBytes, serializedBytes, indexBytes;
    public final int automaticCount;
    public final Map<Role,String> registeredRoles;
    public final List<Entry> entries;
    private final byte[] registrationHash, policyHash, graphHash;

    private HistoryArchiveManifest(UUID bundle,byte[] registration,long generation,byte[] policy,
            long quota,int automatic,byte[] graph,Map<Role,String> roles,List<Entry> entries,long total)throws IOException {
        if(bundle==null||registration==null||registration.length!=32||policy==null||policy.length!=32
                ||graph==null||graph.length!=32)throw invalid("Missing frozen bindings");
        validatePolicy(generation,quota,automatic);validateRoles(roles);bounds(entries.size());
        long sum=0;Entry prior=null;Set<UUID> archiveIds=new HashSet<>();
        for(Entry entry:entries) {
            if(entry==null||prior!=null&&ORDER.compare(prior,entry)>=0)throw invalid("Noncanonical native member order");
            if(!archiveIds.add(entry.archiveId))throw invalid("Duplicate archive entry ID");
            sum=add(sum,entry.envelopeBytes);prior=entry;
        }
        if(sum!=total)throw invalid("Native envelope total mismatch");
        bundleId=bundle;registrationHash=registration.clone();policyHash=policy.clone();graphHash=graph.clone();
        policyGeneration=generation;quotaBytes=quota;automaticCount=automatic;
        registeredRoles=Collections.unmodifiableMap(new EnumMap<>(roles));
        this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
        nativeEnvelopeBytes=sum;archiveBytes=archiveSize(entries.size(),sum);
        serializedBytes=serializedSize(entries.size(),roles);indexBytes=indexCharge(entries.size());
    }
    public byte[] registrationHashBytes() { return registrationHash.clone(); }
    public byte[] policyHashBytes() { return policyHash.clone(); }
    public byte[] graphHashBytes() { return graphHash.clone(); }

    /** Pure frozen evidence conversion, not atomic capture. Includes every graph record. */
    static HistoryArchiveManifest freeze(HistoryGraph.Graph graph,HistoryControlStore.Inspection control,
            IdSource ids)throws IOException {
        if(graph==null||control==null||ids==null||!graph.complete||!graph.usageComplete||(graph.pruneBlocked&&!graph.historicalCancelledOnly)
                ||!graph.diagnostics.isEmpty()||graph.effectivePolicy==null
                ||control.state!=HistoryControlStore.State.VALIDATED||!control.chainComplete||!control.usageComplete
                ||!control.diagnostics.isEmpty()||!control.stages.isEmpty()||control.hasMaintenance()
                ||control.registration==null||control.latest==null)throw invalid("Incomplete frozen authority evidence");
        HistoryControlStore.Registration registration=control.registration;
        HistoryControlStore.Record latest=control.latest;
        HistoryCapacity.EffectivePolicy effective=graph.effectivePolicy;
        if(!registration.bundleId.equals(latest.bundleId)||!latest.bundleId.toString().equals(effective.bundleId)
                ||latest.generation!=effective.generation||!latest.envelopeHash.equals(effective.controlRecordHash)
                ||latest.quotaBytes!=effective.quotaBytes||latest.automaticCount!=effective.automaticCount
                ||registration.roles.size()!=4||graph.registeredRoleNames.size()!=4)
            throw invalid("Frozen graph/control policy mismatch");
        Map<Role,String> roles=new EnumMap<>(Role.class);
        for(Role role:Role.values()) {
            HistoryControlStore.Role binding=registration.roles.get(role.wireId-1);
            if(binding.id!=role.wireId||!binding.basename.equals(graph.registeredRoleNames.get(role.wireId-1)))
                throw invalid("Frozen registered roles mismatch");
            roles.put(role,binding.basename);
        }
        bounds(graph.records.size());
        // Validate every descriptor before generating IDs; never guess filenames or filter inputs.
        List<Native> natives=new ArrayList<>(graph.records.size());long total=0;
        for(ProtectionStore.StoredRecord record:graph.records) {
            if(record==null)throw invalid("Missing native descriptor");
            Role role=Role.parse(record.rootRole);Kind kind=Kind.parse(record.kind);UUID nativeId=canonicalUuid(record.id);
            if(kind.role!=role||!((nativeId.toString()+"."+kind.nativeName).equals(record.filename)))
                throw invalid("Native filename/role/kind mismatch");
            Native member=new Native(role,kind,nativeId,record.serializedBytes,hash(record.envelopeHash));
            natives.add(member);total=add(total,member.length);
        }
        natives.sort(NATIVE_ORDER);
        for(int i=1;i<natives.size();i++)if(NATIVE_ORDER.compare(natives.get(i-1),natives.get(i))>=0)
            throw invalid("Duplicate native member");
        serializedSize(natives.size(),roles);archiveSize(natives.size(),total);
        Set<UUID> archiveIds=new HashSet<>();List<Entry> entries=new ArrayList<>(natives.size());
        for(Native member:natives) {
            UUID archiveId=ids.next();requireArchiveId(archiveId);
            if(!archiveIds.add(archiveId))throw invalid("Duplicate generated archive entry ID");
            entries.add(new Entry(member.role,member.kind,member.id,archiveId,member.length,member.hash));
        }
        return new HistoryArchiveManifest(registration.bundleId,hash(registration.envelopeHash),effective.generation,
                hash(effective.controlRecordHash),effective.quotaBytes,effective.automaticCount,
                hash(graph.fingerprint),roles,entries,total);
    }
    private static final class Native {
        final Role role;final Kind kind;final UUID id;final long length;final byte[] hash;
        Native(Role role,Kind kind,UUID id,long length,byte[] hash)throws IOException {
            descriptor(role,kind,id,length,hash);
            this.role=role;this.kind=kind;this.id=id;this.length=length;this.hash=hash;
        }
    }
    private static void descriptor(Role role,Kind kind,UUID nativeId,long length,byte[] hash)throws IOException {
        if(role==null||kind==null||kind.role!=role||nativeId==null)throw invalid("Invalid typed native member");
        long min=role==Role.STORE?45:44;
        long max=kind==Kind.SNAPSHOT?PrivateArchive.MAX_DOCUMENT_ENTRY_BYTES:(role==Role.STORE?32768:16384);
        if(length<min||length>max||hash==null||hash.length!=32)throw invalid("Invalid native envelope descriptor");
    }
    private static int compare(Role ar,UUID ai,Kind ak,Role br,UUID bi,Kind bk) {
        int result=Integer.compare(ar.wireId,br.wireId);
        if(result==0)result=Long.compareUnsigned(ai.getMostSignificantBits(),bi.getMostSignificantBits());
        if(result==0)result=Long.compareUnsigned(ai.getLeastSignificantBits(),bi.getLeastSignificantBits());
        return result==0?Integer.compare(ak.wireId,bk.wireId):result;
    }
    private static final Comparator<Native> NATIVE_ORDER=(a,b)->compare(a.role,a.id,a.kind,b.role,b.id,b.kind);
    private static final Comparator<Entry> ORDER=(a,b)->compare(a.role,a.nativeId,a.kind,b.role,b.nativeId,b.kind);

    /** Stream exact frozen bytes; no ID generation, native IO, flush or close. */
    public void write(OutputStream output)throws IOException {
        if(output==null)throw invalid("Missing manifest output");
        MessageDigest digest=digest();DigestOutputStream tracked=new DigestOutputStream(output,digest);
        DataOutputStream out=new DataOutputStream(tracked);
        out.write(MAGIC);out.writeInt(VERSION);out.writeInt((int)serializedBytes);out.writeInt(entries.size());
        out.writeLong(nativeEnvelopeBytes);uuid(out,bundleId);out.write(registrationHash);out.writeLong(policyGeneration);
        out.write(policyHash);out.writeLong(quotaBytes);out.writeInt(automaticCount);out.write(graphHash);
        for(Role role:Role.values()) {
            out.writeByte(role.wireId);byte[] name=name(registeredRoles.get(role));out.writeInt(name.length);out.write(name);
        }
        for(Entry entry:entries) {
            out.writeByte(entry.role.wireId);out.writeByte(entry.kind.wireId);uuid(out,entry.nativeId);uuid(out,entry.archiveId);
            out.writeLong(entry.envelopeBytes);out.write(entry.envelopeHash);
        }
        tracked.on(false);out.write(digest.digest());
    }

    /** Read one complete checksummed frame, with no prefix result or stream ownership transfer. */
    public static HistoryArchiveManifest read(InputStream input)throws IOException {
        if(input==null)throw invalid("Missing manifest input");
        MessageDigest digest=digest();DigestInputStream tracked=new DigestInputStream(new ProgressInput(input),digest);
        DataInputStream in=new DataInputStream(tracked);
        byte[] magic=new byte[MAGIC.length];in.readFully(magic);
        if(!Arrays.equals(magic,MAGIC)||in.readInt()!=VERSION)throw invalid("Unknown manifest domain/version");
        int declared=in.readInt(),count=in.readInt();
        if(declared<FIXED_BYTES+4||declared>MAX_SERIALIZED_BYTES)throw invalid("Invalid serialized manifest size");
        bounds(count);
        long total=in.readLong();archiveSize(count,total);
        UUID bundle=uuid(in);byte[] registration=readHash(in);long generation=in.readLong();
        byte[] policy=readHash(in);long quota=in.readLong();int automatic=in.readInt();
        validatePolicy(generation,quota,automatic);byte[] graph=readHash(in);
        Map<Role,String> roles=new EnumMap<>(Role.class);
        for(Role expected:Role.values()) {
            if(Role.decode(in.readUnsignedByte())!=expected)throw invalid("Noncanonical registered role order");
            int length=in.readInt();if(length<1||length>64)throw invalid("Invalid registered basename length");
            byte[] bytes=new byte[length];in.readFully(bytes);
            String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            name(value);roles.put(expected,value);
        }
        validateRoles(roles);
        if(serializedSize(count,roles)!=declared)throw invalid("Serialized manifest size mismatch");
        List<Entry> entries=new ArrayList<>(count);Set<UUID> ids=new HashSet<>();Entry prior=null;long sum=0;
        for(int i=0;i<count;i++) {
            Entry entry=new Entry(Role.decode(in.readUnsignedByte()),Kind.decode(in.readUnsignedByte()),
                    uuid(in),uuid(in),in.readLong(),readHash(in));
            if(prior!=null&&ORDER.compare(prior,entry)>=0)throw invalid("Noncanonical native member order");
            if(!ids.add(entry.archiveId))throw invalid("Duplicate archive entry ID");
            sum=add(sum,entry.envelopeBytes);entries.add(entry);prior=entry;
        }
        if(sum!=total)throw invalid("Native envelope total mismatch");
        tracked.on(false);byte[] expected=readHash(in);
        if(!MessageDigest.isEqual(expected,digest.digest()))throw invalid("Manifest checksum mismatch");
        if(in.read()!=-1)throw invalid("Trailing manifest bytes");
        return new HistoryArchiveManifest(bundle,registration,generation,policy,quota,automatic,graph,roles,entries,total);
    }
    private static void validatePolicy(long generation,long quota,int automatic)throws IOException {
        if(generation<0||(quota!=(64L<<20)&&quota!=(128L<<20)&&quota!=(256L<<20)&&quota!=(512L<<20)&&quota!=(1024L<<20))
                ||(automatic!=0&&automatic!=5&&automatic!=10&&automatic!=20&&automatic!=50))
            throw invalid("Invalid captured policy");
    }
    private static void validateRoles(Map<Role,String> roles)throws IOException {
        if(roles==null||roles.size()!=4)throw invalid("Missing registered roles");
        Set<String> unique=new HashSet<>();
        for(Role role:Role.values()) {
            String value=roles.get(role);name(value);
            if(!unique.add(value)||value.equals(HistoryControlStore.ROOT_NAME)
                    ||value.equals(HistoryControlStore.ENROLLMENT_NAME)||value.equals(HistoryControlStore.ENROLLMENT_STAGE))
                throw invalid("Conflicting registered basename");
        }
    }
    private static byte[] name(String value)throws IOException {
        if(value==null||value.isEmpty()||value.length()>64||value.equals(".")||value.equals(".."))
            throw invalid("Invalid registered basename");
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='/'||c=='\\'||c==':'||Character.isISOControl(c))throw invalid("Invalid registered basename");
        }
        ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        if(encoded.remaining()>64)throw invalid("Oversized registered basename");
        byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);return bytes;
    }
    private static void bounds(int count)throws IOException {
        if(count<0||count>MAX_ENTRIES||count>PrivateArchive.MAX_ENTRIES||indexCharge(count)>MAX_INDEX_BYTES
                ||multiply(count,PrivateArchive.INDEX_BYTES_PER_ENTRY)>PrivateArchive.MAX_INDEX_BYTES)
            throw invalid("Manifest index budget exceeded");
    }
    private static long indexCharge(int count)throws IOException {return add(INDEX_BASE_BYTES,multiply(count,INDEX_BYTES_PER_ENTRY));}
    private static long serializedSize(int count,Map<Role,String> roles)throws IOException {
        long size=add(FIXED_BYTES,multiply(count,ENTRY_BYTES));
        for(Role role:Role.values())size=add(size,name(roles.get(role)).length);
        if(size>MAX_SERIALIZED_BYTES)throw invalid("Manifest byte cap exceeded");
        return size;
    }
    private static long archiveSize(int count,long total)throws IOException {
        if(total<0)throw invalid("Negative native envelope total");
        long size=add(add(ARCHIVE_FIXED_BYTES,multiply(count,ARCHIVE_ENTRY_BYTES)),total);
        if(size>PrivateArchive.MAX_ARCHIVE_BYTES)throw invalid("Native mapping exceeds archive byte cap");
        return size;
    }
    private static long add(long a,long b)throws IOException {
        try{return Math.addExact(a,b);}catch(ArithmeticException failure){throw invalid("Manifest size overflow");}
    }
    private static long multiply(long a,long b)throws IOException {
        try{return Math.multiplyExact(a,b);}catch(ArithmeticException failure){throw invalid("Manifest size overflow");}
    }
    private static UUID canonicalUuid(String value)throws IOException {
        try {
            UUID id=UUID.fromString(value);
            if(!id.toString().equals(value))throw invalid("Noncanonical native UUID");
            return id;
        }catch(IllegalArgumentException|NullPointerException failure){throw invalid("Invalid native UUID");}
    }
    private static void requireArchiveId(UUID id)throws IOException {
        if(id==null||id.version()!=4||id.variant()!=2)throw invalid("Archive ID must be UUIDv4 variant2");
    }
    private static byte[] hash(String value)throws IOException {
        if(value==null||value.length()!=64)throw invalid("Invalid native SHA256");
        byte[] bytes=new byte[32];
        for(int i=0;i<64;i++) {
            char c=value.charAt(i);int digit=c>='0'&&c<='9'?c-'0':c>='a'&&c<='f'?c-'a'+10:-1;
            if(digit<0)throw invalid("Noncanonical native SHA256");
            bytes[i/2]|=(byte)(digit<<((i%2==0)?4:0));
        }
        return bytes;
    }
    private static String hex(byte[] bytes) {
        char[] digits="0123456789abcdef".toCharArray(),text=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){text[2*i]=digits[(bytes[i]>>>4)&15];text[2*i+1]=digits[bytes[i]&15];}
        return new String(text);
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException {out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException {return new UUID(in.readLong(),in.readLong());}
    private static byte[] readHash(DataInputStream in)throws IOException {byte[] value=new byte[32];in.readFully(value);return value;}
    private static MessageDigest digest() {
        try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static IOException invalid(String message){return new IOException(message);}
    /** Avoid a nonprogressing bulk read without buffering the manifest or closing the caller stream. */
    private static final class ProgressInput extends InputStream {
        private final InputStream input;
        ProgressInput(InputStream input){this.input=input;}
        public int read()throws IOException{return input.read();}
        public int read(byte[] bytes,int offset,int length)throws IOException {
            if(length==0)return 0;
            int count=input.read(bytes,offset,length);
            if(count < -1||count>length)throw invalid("Invalid input read count");
            if(count!=0)return count;
            int value=input.read();if(value<0)return -1;bytes[offset]=(byte)value;return 1;
        }
    }
}
