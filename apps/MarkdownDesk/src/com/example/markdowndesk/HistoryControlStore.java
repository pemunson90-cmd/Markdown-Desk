package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/**
 * Bounded control framing and read-only disk evidence. VALIDATED means complete
 * chain bytes, not acknowledged effective policy: a future controller must
 * perform explicit durable open/reconciliation before allowing any writes.
 * No method here creates, syncs, publishes, promotes or removes a file.
 */
final class HistoryControlStore {
    static final String ROOT_NAME="history_control", REGISTRATION_NAME="registration.control";
    static final int MAGIC=0x4d444843, VERSION=1, MAX_RECORD=16384, MAX_ENTRIES=256;
    static final long MAX_BYTES=1024L*1024;
    private static final int HEADER=13, CHECKSUM=32, MAX_NAME=64;
    private static final int REGISTRATION=1, POLICY=2;
    private final File parent;
    private final Path root;

    enum State {UNREGISTERED, VALIDATED, BLOCKED}
    enum StageState {PROVISIONAL, INVALID}

    static final class Role {
        final int id;
        final String basename;
        Role(int id,String basename)throws IOException {
            if(id<1||id>4)throw new IOException("Unknown control role");
            nameBytes(basename);this.id=id;this.basename=basename;
        }
    }
    abstract static class Record {
        final UUID bundleId;
        final long generation, quotaBytes;
        final int automaticCount, serializedBytes;
        final String envelopeHash;
        Record(UUID bundleId,long generation,long quotaBytes,int automaticCount,int serializedBytes,String envelopeHash) {
            this.bundleId=bundleId;this.generation=generation;this.quotaBytes=quotaBytes;
            this.automaticCount=automaticCount;this.serializedBytes=serializedBytes;this.envelopeHash=envelopeHash;
        }
    }
    static final class Registration extends Record {
        final long bootstrapQuotaBytes;
        final List<Role> roles;
        private Registration(UUID bundleId,long bootstrap,List<Role> roles,long quota,int count,int size,String hash) {
            super(bundleId,0,quota,count,size,hash);bootstrapQuotaBytes=bootstrap;
            this.roles=Collections.unmodifiableList(new ArrayList<>(roles));
        }
    }
    static final class Policy extends Record {
        final String previousHash;
        private Policy(UUID bundleId,long generation,String previousHash,long quota,int count,int size,String hash) {
            super(bundleId,generation,quota,count,size,hash);this.previousHash=previousHash;
        }
    }
    static final class Entry {
        /** Null for unknown names: arbitrary user strings never escape inventory. */
        final String filename;
        final long length;
        final String envelopeHash;
        final Record record;
        final boolean stage;
        private Entry(String filename,long length,String hash,Record record,boolean stage) {
            this.filename=filename;this.length=length;envelopeHash=hash;this.record=record;this.stage=stage;
        }
    }
    static final class StageDescriptor {
        final String filename;
        final long length;
        final String envelopeHash;
        final Record record;
        final StageState state;
        private StageDescriptor(Entry entry,StageState state) {
            filename=entry.filename;length=entry.length;envelopeHash=entry.envelopeHash;record=entry.record;this.state=state;
        }
    }
    static final class Inspection {
        final State state;
        final Registration registration;
        /** Last validated committed prefix only; never includes a stage. */
        final Record latest;
        final List<Entry> entries;
        final List<StageDescriptor> stages;
        final long actualBytes;
        final int actualEntries;
        final boolean usageComplete, chainComplete;
        final List<String> diagnostics;
        private Inspection(State state,Registration registration,Record latest,List<Entry> entries,List<StageDescriptor> stages,
                long bytes,int count,boolean usageComplete,boolean chainComplete,List<String> diagnostics) {
            this.state=state;this.registration=registration;this.latest=latest;
            this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
            this.stages=Collections.unmodifiableList(new ArrayList<>(stages));
            actualBytes=bytes;actualEntries=count;this.usageComplete=usageComplete;this.chainComplete=chainComplete;
            this.diagnostics=Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }
    }

    /** Existing canonical trusted parent only; construction itself is read-only. */
    HistoryControlStore(File trustedParent)throws IOException {
        parent=canonicalDirectory(trustedParent);root=parent.toPath().resolve(ROOT_NAME);
    }

    static byte[] encodeRegistration(UUID bundleId,long bootstrapQuotaBytes,List<Role> roles,long quotaBytes,int automaticCount)throws IOException {
        if(bundleId==null||bootstrapQuotaBytes<0)throw new IOException("Invalid registration configuration");
        validateRoles(roles);validatePolicy(quotaBytes,automaticCount);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            uuid(out,bundleId);out.writeLong(0);
            for(Role role:roles){out.writeByte(role.id);byte[] name=nameBytes(role.basename);out.writeInt(name.length);out.write(name);}
            out.writeLong(bootstrapQuotaBytes);out.writeLong(quotaBytes);out.writeInt(automaticCount);
        }
        return envelope(REGISTRATION,bytes.toByteArray());
    }
    static byte[] encodePolicy(UUID bundleId,long generation,String previousHash,long quotaBytes,int automaticCount)throws IOException {
        if(bundleId==null||generation<=0)throw new IOException("Invalid policy identity");
        validatePolicy(quotaBytes,automaticCount);byte[] previous=hashBytes(previousHash);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            uuid(out,bundleId);out.writeLong(generation);out.write(previous);out.writeLong(quotaBytes);out.writeInt(automaticCount);
        }
        return envelope(POLICY,bytes.toByteArray());
    }
    private static byte[] envelope(int type,byte[] body)throws IOException {
        if(body.length>MAX_RECORD-HEADER-CHECKSUM)throw new IOException("Control record limit exceeded");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(HEADER+body.length+CHECKSUM);
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeByte(type);out.writeInt(body.length);out.write(body);
            out.write(sha(bytes.toByteArray()));
        }
        return bytes.toByteArray();
    }
    static Record decode(byte[] bytes)throws IOException {
        if(bytes==null||bytes.length<HEADER+CHECKSUM||bytes.length>MAX_RECORD)throw new IOException("Invalid control record size");
        if(!MessageDigest.isEqual(sha(Arrays.copyOf(bytes,bytes.length-CHECKSUM)),
                Arrays.copyOfRange(bytes,bytes.length-CHECKSUM,bytes.length)))throw new IOException("Control checksum mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-CHECKSUM));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Unsupported control framing");
        int type=in.readUnsignedByte(),length=in.readInt();
        if((type!=REGISTRATION&&type!=POLICY)||length!=bytes.length-HEADER-CHECKSUM)throw new IOException("Invalid control framing");
        UUID bundle=uuid(in);long generation=in.readLong();Record record;
        String hash=hex(sha(bytes));
        if(type==REGISTRATION) {
            if(generation!=0)throw new IOException("Invalid registration generation");
            List<Role> roles=new ArrayList<>(4);
            for(int i=0;i<4;i++)roles.add(new Role(in.readUnsignedByte(),name(in)));
            validateRoles(roles);long bootstrap=in.readLong(),quota=in.readLong();int count=in.readInt();
            if(bootstrap<0)throw new IOException("Invalid bootstrap quota");validatePolicy(quota,count);
            record=new Registration(bundle,bootstrap,roles,quota,count,bytes.length,hash);
        }else {
            if(generation<=0)throw new IOException("Invalid policy generation");
            byte[] previous=new byte[CHECKSUM];in.readFully(previous);long quota=in.readLong();int count=in.readInt();validatePolicy(quota,count);
            record=new Policy(bundle,generation,hex(previous),quota,count,bytes.length,hash);
        }
        if(in.available()!=0)throw new IOException("Trailing control body");
        return record;
    }
    static long nextGeneration(long previous)throws IOException {
        if(previous<0)throw new IOException("Invalid previous generation");
        try{return Math.addExact(previous,1);}catch(ArithmeticException exhausted){throw new IOException("Control generation exhausted",exhausted);}
    }
    static String policyFilename(long generation)throws IOException {
        if(generation<=0)throw new IOException("Invalid policy generation");
        return String.format(Locale.ROOT,"policy-%016x.control",generation);
    }
    static String stageFilename(String committedName)throws IOException {
        if(!knownCommittedName(committedName))throw new IOException("Unknown control destination");
        return committedName+".stage";
    }

    Inspection inspect()throws IOException {
        List<Entry> entries=new ArrayList<>();List<StageDescriptor> stages=new ArrayList<>();List<String> diagnostics=new ArrayList<>();
        long bytes=0;int count=0;boolean usageComplete=true,inventoryValid=true;
        Registration registration=null;Record latest=null;SortedMap<Long,Policy> policies=new TreeMap<>();
        BasicFileAttributes directoryBefore;
        try {
            canonicalDirectory(parent);
            // readAttributes distinguishes genuine absence from permission/read failures.
            try{directoryBefore=attributes(root);}catch(NoSuchFileException absent) {
                canonicalDirectory(parent);
                return new Inspection(State.UNREGISTERED,null,null,entries,stages,0,0,true,false,diagnostics);
            }
            canonicalDirectory(root.toFile());
        }catch(IOException invalid) {
            diagnostics.add("control: invalid root");
            return new Inspection(State.BLOCKED,null,null,entries,stages,0,0,false,false,diagnostics);
        }
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(root)) {
            for(Path path:paths) {
                if(count==MAX_ENTRIES){diagnostics.add("control: entry limit");usageComplete=false;inventoryValid=false;break;}
                count++;
                String name=path.getFileName().toString();boolean stage=name.endsWith(".stage");
                String committed=stage?name.substring(0,name.length()-6):name;
                boolean known=knownCommittedName(committed);
                long size=0;String hash=null;Record record=null;
                if(!known){diagnostics.add("control: unknown entry");inventoryValid=false;}
                try {
                    BasicFileAttributes before=attributes(path);size=before.size();
                    if(!before.isRegularFile()||size<0)throw new IOException("Nonregular control entry");
                    bytes=Math.addExact(bytes,size);
                    if(size>MAX_RECORD){same(before,attributes(path));diagnostics.add("control: oversized entry");inventoryValid=false;}
                    else {
                        byte[] envelope=read(path,before);hash=hex(sha(envelope));
                        if(known) {
                            try {
                                record=decode(envelope);
                                if(!recordMatches(committed,record))throw new IOException("Control filename mismatch");
                                if(record instanceof Registration)validatePhysicalRoles((Registration)record);
                            }catch(IOException invalid){record=null;diagnostics.add("control: invalid record");inventoryValid=false;}
                        }
                    }
                }catch(IOException|ArithmeticException invalid) {
                    diagnostics.add("control: unreadable entry");usageComplete=false;inventoryValid=false;
                }
                Entry entry=new Entry(known?name:null,size,hash,record,stage);entries.add(entry);
                if(!stage&&record instanceof Registration)registration=(Registration)record;
                if(!stage&&record instanceof Policy) {
                    Policy policy=(Policy)record;
                    if(policies.put(policy.generation,policy)!=null){diagnostics.add("control: duplicate generation");inventoryValid=false;}
                }
            }
        }catch(IOException|DirectoryIteratorException failure) {
            diagnostics.add("control: inventory unavailable");usageComplete=false;inventoryValid=false;
        }
        if(bytes>MAX_BYTES){diagnostics.add("control: byte limit");inventoryValid=false;}
        boolean chainComplete=inventoryValid&&usageComplete&&registration!=null;
        if(registration==null)diagnostics.add("control: missing registration");
        latest=registration;
        for(Policy policy:policies.values()) {
            if(latest==null||!policy.bundleId.equals(latest.bundleId)||!policy.previousHash.equals(latest.envelopeHash)) {
                diagnostics.add("control: broken policy chain");chainComplete=false;break;
            }
            try {
                if(policy.generation!=nextGeneration(latest.generation))throw new IOException("Missing control generation");
            }catch(IOException broken){diagnostics.add("control: broken policy generation");chainComplete=false;break;}
            latest=policy;
        }
        // Recheck physical roots and directory identity after the complete stream.
        try {
            canonicalDirectory(parent);canonicalDirectory(root.toFile());same(directoryBefore,attributes(root));
            if(registration!=null)validatePhysicalRoles(registration);
            for(Entry entry:entries)if(entry.stage&&entry.record instanceof Registration)validatePhysicalRoles((Registration)entry.record);
        }catch(IOException changed) {
            diagnostics.add("control: root changed");usageComplete=false;chainComplete=false;inventoryValid=false;
            registration=null;latest=null;
        }
        List<Entry> pending=new ArrayList<>();for(Entry entry:entries)if(entry.stage)pending.add(entry);
        for(Entry entry:pending) {
            boolean provisional=false;
            if(pending.size()==1&&inventoryValid&&usageComplete&&entry.record!=null) {
                if(entry.record instanceof Registration)provisional=registration==null&&policies.isEmpty()&&entries.size()==1;
                else if(chainComplete&&latest!=null) {
                    Policy policy=(Policy)entry.record;
                    try{provisional=policy.generation==nextGeneration(latest.generation)&&policy.bundleId.equals(latest.bundleId)&&policy.previousHash.equals(latest.envelopeHash);}
                    catch(IOException exhausted){provisional=false;}
                }
            }
            stages.add(new StageDescriptor(entry,provisional?StageState.PROVISIONAL:StageState.INVALID));
        }
        if(!pending.isEmpty())diagnostics.add("control: retained stage");
        entries.sort(Comparator.comparing(entry->entry.filename==null?"":entry.filename));
        stages.sort(Comparator.comparing(stage->stage.filename==null?"":stage.filename));
        State state=chainComplete&&pending.isEmpty()?State.VALIDATED:State.BLOCKED;
        return new Inspection(state,registration,latest,entries,stages,bytes,count,usageComplete,chainComplete,diagnostics);
    }

    private void validatePhysicalRoles(Registration registration)throws IOException {
        Set<File> roots=new HashSet<>();
        for(Role role:registration.roles) {
            File roleRoot=canonicalDirectory(new File(parent,role.basename));
            if(!parent.equals(roleRoot.getParentFile())||ROOT_NAME.equals(role.basename)||!roots.add(roleRoot))throw new IOException("Invalid physical role bundle");
        }
    }
    private static boolean recordMatches(String name,Record record)throws IOException {
        return record instanceof Registration?REGISTRATION_NAME.equals(name):policyFilename(record.generation).equals(name);
    }
    private static boolean knownCommittedName(String name) {
        if(REGISTRATION_NAME.equals(name))return true;
        if(name==null||!name.matches("policy-[0-9a-f]{16}\\.control"))return false;
        try{return Long.parseLong(name.substring(7,23),16)>0;}catch(NumberFormatException invalid){return false;}
    }
    private static void validateRoles(List<Role> roles)throws IOException {
        if(roles==null||roles.size()!=4)throw new IOException("Incomplete control roles");
        Set<String> names=new HashSet<>();
        for(int i=0;i<4;i++) {
            Role role=roles.get(i);
            if(role==null||role.id!=i+1||!names.add(role.basename)||ROOT_NAME.equals(role.basename))throw new IOException("Invalid ordered control roles");
            nameBytes(role.basename);
        }
    }
    private static void validatePolicy(long quota,int count)throws IOException {
        long mib=1024L*1024;
        if(quota!=64*mib&&quota!=128*mib&&quota!=256*mib&&quota!=512*mib&&quota!=1024*mib)throw new IOException("Unsupported control quota");
        if(count!=0&&count!=5&&count!=10&&count!=20&&count!=50)throw new IOException("Unsupported retention count");
    }
    private static byte[] nameBytes(String name)throws IOException {
        if(name==null||name.isEmpty()||name.length()>MAX_NAME||name.equals(".")||name.equals(".."))throw new IOException("Invalid control root name");
        for(int i=0;i<name.length();i++) {
            char c=name.charAt(i);
            if(c=='/'||c=='\\'||c==':'||Character.isISOControl(c))throw new IOException("Invalid control root name");
        }
        try {
            ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(name));
            if(encoded.remaining()>MAX_NAME)throw new IOException("Control root name byte limit");
            byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);return bytes;
        }catch(CharacterCodingException malformed){throw new IOException("Invalid control root UTF-8",malformed);}
    }
    private static String name(DataInputStream in)throws IOException {
        int size=in.readInt();if(size<1||size>MAX_NAME||size>in.available())throw new IOException("Invalid control name length");
        byte[] bytes=new byte[size];in.readFully(bytes);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException malformed){throw new IOException("Invalid control root UTF-8",malformed);}
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static byte[] hashBytes(String hash)throws IOException {
        if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid previous control hash");
        byte[] bytes=new byte[CHECKSUM];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hash.substring(i*2,i*2+2),16);return bytes;
    }
    private static byte[] sha(byte[] bytes)throws IOException {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException unavailable){throw new IOException("SHA-256 unavailable",unavailable);}
    }
    private static String hex(byte[] bytes) {
        StringBuilder result=new StringBuilder(bytes.length*2);
        for(byte b:bytes){result.append(Character.forDigit((b&255)>>>4,16));result.append(Character.forDigit(b&15,16));}return result.toString();
    }
    private static BasicFileAttributes attributes(Path path)throws IOException{return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static File canonicalDirectory(File file)throws IOException {
        if(file==null)throw new IOException("Missing trusted directory");File absolute=file.getAbsoluteFile();Path path=absolute.toPath();
        for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Aliased trusted directory");
        if(!path.normalize().equals(path)||!absolute.getCanonicalFile().equals(absolute)||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid canonical directory");
        return absolute;
    }
    private static byte[] read(Path path,BasicFileAttributes before)throws IOException {
        if(before.size()<0||before.size()>MAX_RECORD)throw new IOException("Control record limit");
        ByteBuffer buffer=ByteBuffer.allocate((int)before.size());
        try(SeekableByteChannel channel=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            while(buffer.hasRemaining()){int n=channel.read(buffer);if(n<0)throw new IOException("Truncated control record");if(n==0)throw new IOException("Control read made no progress");}
            if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Control record grew");
        }
        same(before,attributes(path));return buffer.array();
    }
    private static void same(BasicFileAttributes before,BasicFileAttributes after)throws IOException {
        if(before.isRegularFile()!=after.isRegularFile()||before.isDirectory()!=after.isDirectory()||before.size()!=after.size()
                ||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())
                ||!before.creationTime().equals(after.creationTime()))throw new IOException("Control file identity changed");
    }
}
