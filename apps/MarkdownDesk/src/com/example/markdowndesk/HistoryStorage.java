package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Private single-process history coordination. A JVM monitor is not a process lock. */
final class HistoryStorage {
    private static final ConcurrentHashMap<String,State> MONITORS=new ConcurrentHashMap<>();
    private static final class State {long generation;boolean enrolled,recoveryRequired,uncertain,publishing;ProtectionStore store;Configuration previous;String pendingHash;}
    static final long METADATA_BUDGET=16L*1024*1024;
    private HistoryStorage() {}
    static File canonicalRoot(File root)throws IOException {
        if(root==null)throw new IOException("Missing history root");
        File absolute=root.getAbsoluteFile();Path path=absolute.toPath();
        for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("History path alias");
        if(!path.normalize().equals(path)||!absolute.getCanonicalFile().equals(absolute))throw new IOException("History root is not canonical");
        File parent=absolute.getParentFile();
        if(parent==null||!Files.isDirectory(parent.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing private history parent");
        return absolute;
    }
    static Object monitorFor(File root)throws IOException {
        File canonical=canonicalRoot(root);return MONITORS.computeIfAbsent(canonical.getParent(),unused->new State());
    }
    static long generation(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");return state.generation;
    }
    static void changed(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");
        try{state.generation=Math.addExact(state.generation,1);}catch(ArithmeticException failure){throw new IOException("History generation exhausted",failure);}
    }
    static void siblings(File... roots)throws IOException {
        Set<File> seen=new HashSet<>();File parent=null;
        for(File root:roots){File canonical=canonicalRoot(root);if(parent==null)parent=canonical.getParentFile();
            if(!parent.equals(canonical.getParentFile())||!seen.add(canonical))throw new IOException("History roles require distinct direct siblings");}
    }
    static final class BudgetExceeded extends IOException {
        BudgetExceeded(){super("History metadata budget exceeded");}
    }
    static final class Budget {
        private long remaining;
        Budget(long maximum)throws IOException {if(maximum<0||maximum>METADATA_BUDGET)throw new IOException("Invalid history metadata budget");remaining=maximum;}
        void charge(long amount)throws IOException {if(amount<0||amount>remaining)throw new BudgetExceeded();remaining-=amount;}
    }
    static final class FilesView {
        final List<ProtectionStore.StoredRecord> records=new ArrayList<>();
        final List<String> diagnostics=new ArrayList<>();long bytes;boolean usageComplete=true;
    }
    static FilesView inspect(File root,String role,long maxFile,int maxEntries,Budget budget)throws IOException {
        FilesView view=new FilesView();
        try{
            canonicalRoot(root);
            if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing history directory");
            try(DirectoryStream<Path> paths=Files.newDirectoryStream(root.toPath())){
                for(Path path:paths){
                    if(view.records.size()>=maxEntries){view.diagnostics.add(role+": entry limit");view.usageComplete=false;break;}
                    String name=path.getFileName().toString();
                    try{budget.charge(512L+name.length()*2L);}catch(IOException exceeded){view.diagnostics.add(role+": metadata budget");view.usageComplete=false;break;}
                    long size=0;String hash=null,kind="unknown",id=null;boolean validName=false;
                    try{
                        BasicFileAttributes attrs=attrs(path);size=attrs.size();
                        if(!attrs.isRegularFile()){view.diagnostics.add(role+": nonregular entry");view.usageComplete=false;}
                        else {
                            view.bytes=Math.addExact(view.bytes,size);
                            int dot=name.lastIndexOf('.');if(dot>0){id=name.substring(0,dot);kind=name.substring(dot+1);try{if(!UUID.fromString(id).toString().equals(id))throw new IOException("Invalid UUID");validName=true;}catch(IOException|IllegalArgumentException invalid){id=null;}}
                            if(!validName||!known(role,kind))view.diagnostics.add(role+": unknown entry");
                            long limit=maxFile;
                            if("store".equals(role)&&("transaction".equals(kind)||"complete".equals(kind)||"completion-stage".equals(kind)))limit=Math.min(limit,32768);
                            if(!validName||!known(role,kind))limit=Math.min(limit,16384);
                            if(size<0||size>limit)view.diagnostics.add(role+": oversized entry");
                            else hash=hash(path,attrs);
                            if("stage".equals(kind)||"completion-stage".equals(kind))view.diagnostics.add(role+": retained stage");
                        }
                    }catch(IOException|ArithmeticException failure){view.diagnostics.add(role+": unreadable entry");view.usageComplete=false;}
                    view.records.add(new ProtectionStore.StoredRecord(role,name,id,kind,size,hash));
                }
            }
        }catch(IOException|DirectoryIteratorException failure){view.diagnostics.add(role+": inventory unavailable");view.usageComplete=false;}
        view.records.sort(Comparator.comparing(record->record.filename));return view;
    }
    private static boolean known(String role,String kind){return "stage".equals(kind)||("store".equals(role)?Arrays.asList("snapshot","transaction","complete","completion-stage").contains(kind):("catalog".equals(role)?"checkpoint":role).equals(kind));}
    private static BasicFileAttributes attrs(Path path)throws IOException {return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static String hash(Path path,BasicFileAttributes before)throws IOException {
        MessageDigest digest=digest();ByteBuffer bytes=ByteBuffer.allocate(8192);long read=0;
        try(SeekableByteChannel channel=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(read<before.size()){bytes.clear();bytes.limit((int)Math.min(bytes.capacity(),before.size()-read));int n=channel.read(bytes);if(n<0)throw new IOException("Truncated inventory file");if(n==0)continue;read+=n;bytes.flip();digest.update(bytes);}
            bytes.clear();bytes.limit(1);if(channel.read(bytes)!=-1)throw new IOException("Inventory file grew");
        }
        BasicFileAttributes after=attrs(path);
        if(!after.isRegularFile()||before.size()!=after.size()||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Inventory file changed");
        return hex(digest.digest());
    }
    private static final String CAPACITY_ROOT="history_capacity";
    private static final int POLICY_MAGIC=0x4d444851, POLICY_RECORD_MAX=4096, POLICY_ENTRIES=256;
    private static final long POLICY_BYTES=65536;
    private static final String[] ROLES={"store","catalog","identity","index"};
    static final class Configuration {
        final String[] names;final long initialQuota,quota,sequence;final int automaticCount;final String hash;
        Configuration(String[] names,long initialQuota,long quota,int count,long sequence,String hash){this.names=names.clone();this.initialQuota=initialQuota;this.quota=quota;automaticCount=count;this.sequence=sequence;this.hash=hash;}
    }
    private static final class Ledger {
        final File root;final List<ProtectionStore.StoredRecord> records=new ArrayList<>();
        Configuration latest,effective;File stage;long bytes;boolean blocked;
        Ledger(File root){this.root=root;}
    }
    static final class Capacity {
        final Configuration configuration;final long actualBytes,reservedBytes,reservedSlots;final boolean blocked;
        Capacity(Configuration config,long bytes,long reserved,long slots,boolean blocked){configuration=config;actualBytes=bytes;reservedBytes=reserved;reservedSlots=slots;this.blocked=blocked;}
        long chargedBytes()throws IOException{return add(actualBytes,reservedBytes);}
    }
    private static State state(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");return state;
    }
    private static long add(long a,long b)throws IOException {try{return Math.addExact(a,b);}catch(ArithmeticException failure){throw new IOException("History capacity overflow",failure);}}
    static void validatePolicy(long quota,int count)throws IOException {
        if(!(quota==64L*1024*1024||quota==128L*1024*1024||quota==256L*1024*1024||quota==512L*1024*1024||quota==1024L*1024*1024)
            ||!(count==0||count==5||count==10||count==20||count==50))throw new IOException("Unsupported history policy choice");
    }
    private static File policyRoot(File root)throws IOException {return canonicalRoot(new File(canonicalRoot(root).getParentFile(),CAPACITY_ROOT));}
    private static String policyName(long sequence,String extension){return String.format(Locale.ROOT,"%020d",sequence)+extension;}
    private static byte[] policyBytes(Configuration config,String previous)throws IOException {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(buffer)){
            out.writeInt(POLICY_MAGIC);out.writeInt(1);out.writeLong(config.sequence);out.writeLong(config.initialQuota);out.writeLong(config.quota);out.writeInt(config.automaticCount);out.writeUTF(previous);
            for(String name:config.names)out.writeUTF(name);out.write(digest().digest(buffer.toByteArray()));
        }
        return buffer.toByteArray();
    }
    private static byte[] boundedPolicyRead(File file)throws IOException {
        BasicFileAttributes before=attrs(file.toPath());long length=before.size();
        if(!before.isRegularFile()||length<0||length>POLICY_RECORD_MAX)throw new IOException("Invalid history policy record");
        ByteBuffer data=ByteBuffer.allocate((int)length);
        try(SeekableByteChannel channel=Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(data.hasRemaining())if(channel.read(data)<0)throw new IOException("Truncated policy record");
            if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Policy record grew");
        }
        BasicFileAttributes after=attrs(file.toPath());
        if(!after.isRegularFile()||after.size()!=length||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Policy changed while reading");
        return data.array();
    }
    private static Configuration decodePolicy(byte[] bytes,Configuration previous)throws IOException {
        if(bytes.length<80||!MessageDigest.isEqual(Arrays.copyOfRange(bytes,bytes.length-32,bytes.length),digest().digest(Arrays.copyOf(bytes,bytes.length-32))))throw new IOException("Policy integrity mismatch");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-32))){
            if(in.readInt()!=POLICY_MAGIC||in.readInt()!=1)throw new IOException("Unsupported policy record");
            long sequence=in.readLong(),initial=in.readLong(),quota=in.readLong();int count=in.readInt();String prior=in.readUTF();String[] names=new String[4];
            for(int i=0;i<4;i++){names[i]=in.readUTF();if(!names[i].matches("[A-Za-z0-9_-]{1,128}")||names[i].equals(CAPACITY_ROOT))throw new IOException("Invalid registered root name");}
            if(in.available()!=0||new HashSet<>(Arrays.asList(names)).size()!=4||initial<0||sequence!=(previous==null?0:previous.sequence+1)
                ||!prior.equals(previous==null?"":previous.hash)||(previous!=null&&(initial!=previous.initialQuota||!Arrays.equals(names,previous.names))))throw new IOException("Invalid policy chain");
            validatePolicy(quota,count);return new Configuration(names,initial,quota,count,sequence,hex(digest().digest(bytes)));
        }
    }
    private static Ledger ledger(File source)throws IOException {
        State state=state(source);File root=policyRoot(source);
        if(!Files.exists(root.toPath(),LinkOption.NOFOLLOW_LINKS)){if(state.enrolled)throw new IOException("Registered history policy disappeared");return null;}
        if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid history policy directory");
        Ledger result=new Ledger(root);List<File> files=new ArrayList<>();
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(root.toPath())){for(Path path:paths){if(files.size()>=POLICY_ENTRIES)throw new IOException("History policy entry limit");files.add(path.toFile());}}
        catch(DirectoryIteratorException failure){throw new IOException("Policy enumeration failed",failure.getCause());}
        files.sort(Comparator.comparing(File::getName));
        for(File file:files){
            String name=file.getName();boolean stage=name.endsWith(".policy-stage");
            String expected=policyName(result.latest==null?0:result.latest.sequence+1,stage?".policy-stage":".policy");
            if(!name.equals(expected)||result.stage!=null)throw new IOException("Unknown or conflicting policy record");
            byte[] bytes=boundedPolicyRead(file);result.bytes=add(result.bytes,bytes.length);if(result.bytes>POLICY_BYTES)throw new IOException("History policy byte limit");
            if(stage)result.stage=file;else result.latest=decodePolicy(bytes,result.latest);
            result.records.add(new ProtectionStore.StoredRecord("policy",name,null,stage?"policy-stage":"policy",bytes.length,hex(digest().digest(bytes))));
        }
        result.effective=state.uncertain||state.publishing?state.previous:result.latest;
        if(state.uncertain&&(result.latest==null||state.pendingHash==null||!result.latest.hash.equals(state.pendingHash)))throw new IOException("Uncertain policy publication changed");
        result.blocked=result.stage!=null||result.effective==null||state.uncertain||state.publishing||state.recoveryRequired;
        return result;
    }
    static void checkRoot(File root,String role,Long constructorQuota)throws IOException {
        Ledger ledger;
        try{ledger=ledger(root);}catch(IOException unavailable){
            // Optional policy damage must not hide native v1 recovery on an existing root.
            // Every admission reparses the ledger and still fails closed.
            if(!"store".equals(role)||!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw unavailable;
            State state=state(root);state.enrolled=true;state.recoveryRequired=true;return;
        }
        if(ledger==null)return;
        State state=state(root);if(!state.enrolled){state.enrolled=true;state.recoveryRequired=true;}
        Configuration config=ledger.latest;if(config==null)return;int index=Arrays.asList(ROLES).indexOf(role);
        if(index<0||!config.names[index].equals(root.getName()))throw new IOException("History root conflicts with registered role");
        if(constructorQuota!=null&&constructorQuota.longValue()!=config.initialQuota)throw new IOException("Constructor quota conflicts with registered history");
    }
    static void attachStore(ProtectionStore store)throws IOException {State state=state(store.storageRoot());state.store=store;}
    static void configure(ProtectionStore store,File catalog,File identity,File index,long quota,int count)throws IOException {
        File root=store.storageRoot();State state=state(root);validatePolicy(quota,count);
        File[] roots={root,catalog,identity,index};siblings(roots);String[] names=new String[4];
        for(int i=0;i<4;i++){names[i]=roots[i].getName();if(!names[i].matches("[A-Za-z0-9_-]{1,128}")||names[i].equals(CAPACITY_ROOT))throw new IOException("Invalid registered root name");}
        Ledger existing=ledger(root);
        if(existing!=null&&existing.latest!=null){
            Configuration current=existing.latest;
            if(!Arrays.equals(names,current.names)||store.constructorQuotaBytes()!=current.initialQuota||quota!=current.quota||count!=current.automaticCount)throw new IOException("Conflicting history configuration; use explicit policy update");
            state.store=store;recoverPolicy(root,store.durability());return;
        }
        Configuration config=new Configuration(names,store.constructorQuotaBytes(),quota,count,0,"");
        // Registration sees every role before it can enable shared writes, including old stage bytes.
        usage(store,config,existing);state.store=store;
        // Every initial-registration retry must prove a previously created directory durable too.
        changed(root);File policy=policyRoot(root);
        if(existing==null)Files.createDirectory(policy.toPath());
        state.enrolled=true;state.recoveryRequired=true;
        store.durability().syncDirectory(root.getParentFile());store.durability().syncDirectory(policy);
        publishPolicy(root,config,store.durability());
    }
    static Configuration currentPolicy(File root)throws IOException {Ledger ledger=ledger(root);if(ledger==null||ledger.effective==null)throw new IOException("History capacity is not configured");return ledger.effective;}
    static void setPolicy(File root,long quota,int count,ProtectionStore.Durability durability)throws IOException {
        validatePolicy(quota,count);Ledger ledger=ledger(root);if(ledger==null||ledger.effective==null)throw new IOException("History capacity is not configured");
        State state=state(root);if(state.uncertain||state.publishing)throw new IOException("History policy recovery required");
        Configuration previous=ledger.effective;
        Configuration next=new Configuration(previous.names,previous.initialQuota,quota,count,previous.sequence+1,"");
        if(state.recoveryRequired){
            // An explicit identical request may resume an exact stage after process death.
            // Generic recoverPolicy never guesses whether a staged setting was intended.
            byte[] expected=policyBytes(next,previous.hash);
            if(ledger.stage==null||!Arrays.equals(expected,boundedPolicyRead(ledger.stage)))throw new IOException("History policy recovery or exact staged request required");
            durability.syncDirectory(ledger.root);
            Ledger checked=ledger(root);if(checked.latest==null||!checked.latest.hash.equals(previous.hash))throw new IOException("Policy changed during staged retry");
        }
        if(previous.quota==quota&&previous.automaticCount==count&&ledger.stage==null)return;
        publishPolicy(root,next,durability);
    }
    static void recoverPolicy(File root,ProtectionStore.Durability durability)throws IOException {
        if(state(root).publishing)throw new IOException("Policy publication is in progress");
        Ledger ledger=ledger(root);if(ledger==null||ledger.latest==null||ledger.stage!=null)throw new IOException("No complete policy publication to recover");
        durability.syncDirectory(ledger.root);Ledger checked=ledger(root);
        if(checked.latest==null||!checked.latest.hash.equals(ledger.latest.hash))throw new IOException("Policy changed during recovery");
        changed(root);State state=state(root);state.uncertain=false;state.pendingHash=null;state.previous=null;state.recoveryRequired=false;state.enrolled=true;
    }
    private static void publishPolicy(File root,Configuration next,ProtectionStore.Durability durability)throws IOException {
        Ledger ledger=ledger(root);if(ledger==null)throw new IOException("Missing history policy directory");State state=state(root);
        Configuration previous=ledger.latest;byte[] expected=policyBytes(next,previous==null?"":previous.hash);
        // Decode before mutation to prove root binding, exact next sequence and supported policy.
        Configuration validated=decodePolicy(expected,previous);
        File destination=new File(ledger.root,policyName(next.sequence,".policy"));File stage=new File(ledger.root,policyName(next.sequence,".policy-stage"));
        boolean reuse=ledger.stage!=null;
        if(reuse&&(!stage.equals(ledger.stage)||!Arrays.equals(expected,boundedPolicyRead(stage))))throw new IOException("Partial or changed policy stage retained");
        if(!reuse&&(ledger.records.size()>=POLICY_ENTRIES||expected.length>POLICY_BYTES-ledger.bytes))throw new IOException("History policy ledger full");
        if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Policy destination exists");
        changed(root);state.enrolled=true;state.publishing=true;state.previous=previous;
        try {
            if(!reuse)Files.createFile(stage.toPath());
            try(FileOutputStream out=new FileOutputStream(stage,reuse)){if(!reuse)out.write(expected);out.flush();durability.syncFile(out.getFD());}
            if(!Arrays.equals(expected,boundedPolicyRead(stage)))throw new IOException("Policy stage changed");
            durability.move(stage,destination);
            if(Files.exists(stage.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Policy move retained stage");
            durability.syncDirectory(ledger.root);
            if(!Arrays.equals(expected,boundedPolicyRead(destination)))throw new IOException("Committed policy differs");
            state.uncertain=false;state.previous=null;state.pendingHash=null;state.recoveryRequired=false;
        }catch(IOException failure){
            if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS)){state.uncertain=true;state.previous=previous;state.pendingHash=validated.hash;}
            else {state.previous=null;state.pendingHash=null;}
            throw failure;
        }finally{state.publishing=false;}
    }
    private static final class Usage {long actual,reserved,slots;final long[] entries=new long[4],bytes=new long[4];}
    private static Usage usage(ProtectionStore store,Configuration config,Ledger ledger)throws IOException {
        if(!store.storageRoot().getName().equals(config.names[0]))throw new IOException("Registered protection handle mismatch");
        Usage usage=new Usage();File parent=store.storageRoot().getParentFile();
        for(int i=0;i<4;i++){
            File root=canonicalRoot(new File(parent,config.names[i]));if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Registered history root missing");
            try(DirectoryStream<Path> paths=Files.newDirectoryStream(root.toPath())){for(Path path:paths){
                if(++usage.entries[i]>(i==0?65536:4096))throw new IOException("Registered history entry cap");
                String name=path.getFileName().toString();int dot=name.lastIndexOf('.');if(dot<=0)throw new IOException("Unknown registered history entry");
                String id=name.substring(0,dot),kind=name.substring(dot+1);try{if(!UUID.fromString(id).toString().equals(id))throw new IOException("Invalid history entry identity");}catch(IllegalArgumentException failure){throw new IOException("Invalid history entry identity",failure);}
                if(!known(ROLES[i],kind))throw new IOException("Unknown registered history type");
                BasicFileAttributes attrs=attrs(path);long length=attrs.size(),maximum=i==0?("snapshot".equals(kind)||"stage".equals(kind)?ProtectionStore.MAX_DOCUMENT_BYTES+32768L:32768):16384;
                if(!attrs.isRegularFile()||length<0||length>maximum)throw new IOException("Invalid registered history file");
                usage.bytes[i]=add(usage.bytes[i],length);if(i>0&&usage.bytes[i]>8L*1024*1024)throw new IOException("Registered history metadata cap");
            }}catch(DirectoryIteratorException failure){throw new IOException("Registered history inventory unavailable",failure.getCause());}
            usage.actual=add(usage.actual,usage.bytes[i]);
        }
        if(ledger!=null)usage.actual=add(usage.actual,ledger.bytes);
        long[] reservation=store.completionReservations();usage.reserved=reservation[0];usage.slots=reservation[1];
        if(add(usage.entries[0],usage.slots)>65536)throw new IOException("Protection reservations exceed entry cap");return usage;
    }
    static Capacity capacity(File root)throws IOException {
        Ledger ledger=ledger(root);if(ledger==null||ledger.effective==null)throw new IOException("History capacity is not configured");State state=state(root);
        if(state.store==null)throw new IOException("Open the registered protection store first");Usage usage=usage(state.store,ledger.effective,ledger);
        return new Capacity(ledger.effective,usage.actual,usage.reserved,usage.slots,ledger.blocked);
    }
    static boolean admit(File root,String role,long newBytes,long newReservation,long releaseBytes,long newSlots,long releaseSlots,boolean completion)throws IOException {
        Ledger ledger=ledger(root);if(ledger==null)return false;
        Configuration config=ledger.effective;if(config==null)throw new IOException("History registration is pending");
        int affected=Arrays.asList(ROLES).indexOf(role);if(affected<0||!root.getName().equals(config.names[affected]))throw new IOException("Unregistered history writer");
        State state=state(root);if(state.store==null)throw new IOException("Open the registered protection store first");
        Usage usage=usage(state.store,config,ledger);
        if(newBytes<0||newReservation<0||releaseBytes<0||releaseSlots<0||newSlots<0||releaseBytes>usage.reserved||releaseSlots>usage.slots)throw new IOException("Unowned history reservation");
        long growth=add(newBytes,newReservation)-releaseBytes,slotGrowth=newSlots-releaseSlots;
        boolean conversion=completion&&affected==0&&newReservation==0&&growth==0&&slotGrowth==0;
        if(ledger.blocked&&!conversion)throw new IOException("Pending policy blocks history mutation");
        if(growth<0||slotGrowth<0)throw new IOException("Invalid history admission credit");
        if(add(add(usage.actual,usage.reserved),growth)>config.quota&&!conversion)throw new IOException("Shared history quota exhausted");
        long occupied=add(usage.entries[affected],affected==0?usage.slots:0);
        if(add(occupied,slotGrowth)>(affected==0?65536:4096))throw new IOException("Shared history entry limit");
        if(affected>0&&add(usage.bytes[affected],newBytes)>8L*1024*1024)throw new IOException("Shared metadata quota exhausted");
        return true;
    }
    static void admitMetadata(File root,String role,long bytes)throws IOException {admit(root,role,bytes,0,0,1,0,false);}
    static List<ProtectionStore.StoredRecord> policyRecords(File root)throws IOException {Ledger ledger=ledger(root);return ledger==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(ledger.records));}
    static Configuration configuredPolicy(File root)throws IOException {Ledger ledger=ledger(root);return ledger==null?null:ledger.effective;}
    static boolean policyBlocked(File root)throws IOException {Ledger ledger=ledger(root);return ledger!=null&&ledger.blocked;}
    static MessageDigest digest()throws IOException {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}}
    static String hex(byte[] bytes){StringBuilder result=new StringBuilder(bytes.length*2);for(byte b:bytes)result.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return result.toString();}
}
