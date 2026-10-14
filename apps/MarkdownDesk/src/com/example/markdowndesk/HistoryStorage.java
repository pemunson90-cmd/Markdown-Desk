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
    private static final class State {long generation;Thread mutator;HistoryCapacity capacity;}
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
    static HistoryCapacity capacity(File root)throws IOException{return ((State)monitorFor(root)).capacity;}
    static void capacity(File root,HistoryCapacity value)throws IOException{requireMutation(root);((State)monitorFor(root)).capacity=value;}
    static boolean controlled(File root)throws IOException{return Files.exists(new File(canonicalRoot(root).getParentFile(),HistoryControlStore.ROOT_NAME).toPath(),LinkOption.NOFOLLOW_LINKS);}
    static final class MutationScope implements AutoCloseable {
        private State state;
        MutationScope(State state){this.state=state;}
        public void close(){if(state!=null){state.mutator=null;state=null;}}
    }
    static MutationScope administration(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state))throw new IOException("History monitor required");
        if(state.mutator!=null)throw new IOException("Nested history mutation rejected");
        state.mutator=Thread.currentThread();return new MutationScope(state);
    }
    static MutationScope mutation(File root)throws IOException {
        MutationScope scope=administration(root);
        try{if(controlled(root)){HistoryCapacity authority=capacity(root);if(authority==null)throw new IOException("History control requires explicit open");authority.requireWriter(root);}return scope;}
        catch(IOException|RuntimeException failure){scope.close();throw failure;}
    }
    static void requireMutation(File root)throws IOException {
        State state=(State)monitorFor(root);if(!Thread.holdsLock(state)||state.mutator!=Thread.currentThread())throw new IOException("History mutation scope required");
    }
    static boolean constructorReadOnly(File root,int role,Long bootstrap)throws IOException {
        requireMutation(root);if(!controlled(root))return false;
        if(!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Controlled roots must already exist");
        HistoryControlStore.Inspection control=new HistoryControlStore(root.getParentFile()).inspect();
        HistoryControlStore.Registration registration=control.registration;
        if(registration==null&&control.stages.size()==1&&control.stages.get(0).record instanceof HistoryControlStore.Registration)registration=(HistoryControlStore.Registration)control.stages.get(0).record;
        if(registration!=null){if(!registration.roles.get(role-1).basename.equals(root.getName()))throw new IOException("Unlisted controlled root");
            if(bootstrap!=null&&bootstrap.longValue()!=registration.bootstrapQuotaBytes)throw new IOException("Conflicting constructor bootstrap hint");}
        return true;
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
    static MessageDigest digest()throws IOException {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}}
    static String hex(byte[] bytes){StringBuilder result=new StringBuilder(bytes.length*2);for(byte b:bytes)result.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return result.toString();}
}
