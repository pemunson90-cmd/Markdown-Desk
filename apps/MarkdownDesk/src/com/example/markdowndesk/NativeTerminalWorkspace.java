package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.*;

/**
 * Prepaid CURRENT-native evidence across a terminal callback, independent of the
 * original capture membership. No writes, callback dispatch or Pins authority.
 * One bounded reusable table; overflow refuses before the caller can publish.
 * This is an accounting convention for supported private filesystems, not a
 * Java/provider whole-heap bound (opaque fileKey sizes have no portable maximum).
 */
final class NativeTerminalWorkspace {
    private static final long FIXED_BYTES=64L<<10;
    private static final int MAX_NAME=64,MAX_ROOT_UNITS=4096;
    private final ProtectionStore store;
    private final File[] roots;
    private final Object monitor;
    private final HistoryStorage.Budget originalBudget;
    private final ProtectionStore.PinNativeWorkspace nativeReader;
    private final Slot[] slots;
    private final BasicFileAttributes[] anchors=new BasicFileAttributes[4];
    private final byte[] scratch=new byte[8192];
    private final boolean[] seen;
    private int count;
    private Proof active;
    private boolean checking;

    private static final class Slot {
        int role;String name,hash;Object key;long length;FileTime modified;
        void clear(){name=null;hash=null;key=null;modified=null;length=0;role=0;}
    }
    private NativeTerminalWorkspace(ProtectionStore store,File[] roots,HistoryStorage.Budget original,
            ProtectionStore.PinNativeWorkspace reader,int maximum,Object monitor){
        this.store=store;this.roots=roots;this.monitor=monitor;originalBudget=original;nativeReader=reader;
        slots=new Slot[maximum];seen=new boolean[maximum];for(int i=0;i<maximum;i++)slots[i]=new Slot();
    }
    /**
     * Debit before retained workspace allocation, on the exact supplied parent:
     * 512KiB validator + 64KiB scratch/directories/temporaries + 4*sum(rootUTF8)
     * + N*(2048 + 4*(maxRootUTF8 + 1 + 2*64)). The two leaf terms account for
     * the retained name and maximum full-path representation. N is a resource
     * allowance, never inferred from the original map; valid leaves are ASCII.
     * Partial refusal never refunds spent credit. No pass spends another debit.
     */
    static NativeTerminalWorkspace reserve(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,
            HistoryIndex index,HistoryStorage.Budget original,int maxMembers)throws IOException {
        if(store==null||catalog==null||identity==null||index==null||original==null||maxMembers<0)
            throw invalid("Invalid terminal workspace admission");
        original.charge(FIXED_BYTES); // Includes bounded canonical/path admission temporaries.
        if(catalog.sourceStore()!=store||!index.uses(store,catalog,identity))throw invalid("Foreign terminal role bundle");
        File s=boundedRoot(store.storageRoot()),c=boundedRoot(catalog.storageRoot()),
                i=boundedRoot(identity.storageRoot()),x=boundedRoot(index.storageRoot());
        HistoryStorage.siblings(s,c,i,x);
        long sb=utf8(s),cb=utf8(c),ib=utf8(i),xb=utf8(x);
        try {
            long max=Math.max(Math.max(sb,cb),Math.max(ib,xb));
            long entry=Math.addExact(2048,Math.multiplyExact(4,Math.addExact(max,1+2*MAX_NAME)));
            original.charge(Math.addExact(Math.multiplyExact(4,sb+cb+ib+xb),Math.multiplyExact(maxMembers,entry)));
        }catch(ArithmeticException overflow){throw new IOException("Terminal workspace admission overflow",overflow);}
        ProtectionStore.PinNativeWorkspace reader=ProtectionStore.PinNativeWorkspace.reserve(store,original);
        return new NativeTerminalWorkspace(store,new File[]{s,c,i,x},original,reader,maxMembers,HistoryStorage.monitorFor(s));
    }
    private static File boundedRoot(File root)throws IOException {
        if(root==null||root.getPath().length()>MAX_ROOT_UNITS)throw invalid("Terminal role path bound");
        return HistoryStorage.canonicalRoot(root);
    }
    private static int utf8(File root){return root.getPath().getBytes(StandardCharsets.UTF_8).length;}

    Proof captureLocked()throws IOException {
        HistoryStorage.requireMutation(roots[0]);
        if(active!=null||checking)throw invalid("Terminal workspace already owns a proof");
        Object lease=HistoryStorage.mutationIdentityLocked(roots[0]);checking=true;
        try {
            for(int role=0;role<4;role++)anchors[role]=directory(role);
            ProtectionStore.PinNativeTotals totals=scan(false);
            sort();for(int i=1;i<count;i++)if(compare(slots[i-1],slots[i])==0)throw invalid("Duplicate native member");
            checkDirectories();active=new Proof(Thread.currentThread(),lease,totals);return active;
        }catch(IOException|RuntimeException|Error failure){clear();throw failure;}
        finally{checking=false;}
    }
    final class Proof implements AutoCloseable {
        private final Thread owner;
        private final Object lease;
        private final long actualBytes,reservedBytes,reservedSlots;
        private final int storeEntries;
        private boolean closed;
        private Proof(Thread owner,Object lease,ProtectionStore.PinNativeTotals totals){
            this.owner=owner;this.lease=lease;actualBytes=totals.actualBytes;reservedBytes=totals.reservedCompletionBytes;
            reservedSlots=totals.reservedCompletionSlots;storeEntries=totals.occupiedEntries;
        }
        void recheckLocked()throws IOException {
            // Unauthorized calls cannot revoke another caller's legitimate proof.
            if(Thread.currentThread()!=owner||closed||active!=this||checking)throw invalid("Foreign/inactive terminal proof");
            if(!Thread.holdsLock(monitor))throw invalid("Terminal proof monitor required");
            checking=true;
            try {
                HistoryStorage.requireMutation(roots[0]);
                if(HistoryStorage.mutationIdentityLocked(roots[0])!=lease)throw invalid("Terminal proof lease changed");
                checkDirectories();Arrays.fill(seen,0,count,false);
                ProtectionStore.PinNativeTotals now=scan(true);
                for(int i=0;i<count;i++)if(!seen[i])throw invalid("Native member disappeared");
                if(now.actualBytes!=actualBytes||now.reservedCompletionBytes!=reservedBytes
                        ||now.reservedCompletionSlots!=reservedSlots||now.occupiedEntries!=storeEntries)
                    throw invalid("Native accounting/reservations changed");
                checkDirectories();
            }catch(IOException|RuntimeException|Error failure){release();throw failure;}
            finally{checking=false;}
        }
        public void close()throws IOException {
            if(Thread.currentThread()!=owner||checking)throw invalid("Foreign/reentrant terminal proof close");
            if(!closed)release();
        }
        private void release(){closed=true;if(active==this){clear();active=null;}}
    }
    private ProtectionStore.PinNativeTotals scan(boolean recheck)throws IOException {
        ProtectionStore.PinNativeTotals totals=store.validatePinNativeLocked(nativeReader,(record,attrs)->
                member(0,record.filename,record.serializedBytes,record.envelopeHash,attrs,recheck));
        for(int role=1;role<4;role++)metadata(role,recheck);
        return totals;
    }
    private void metadata(int role,boolean recheck)throws IOException {
        int occupied=0;
        try(DirectoryStream<Path> files=Files.newDirectoryStream(roots[role].toPath())){
            for(Path path:files){
                if(occupied++>=HistoryMetadataIO.MAX_ENTRIES)throw invalid("Native metadata entry limit");
                String name=path.getFileName().toString();
                if(name.length()>MAX_NAME)throw invalid("Native member name bound");
                int dot=name.lastIndexOf('.');
                if(dot!=36)throw invalid("Unknown native metadata name");
                String id=name.substring(0,dot),kind=name.substring(dot+1);
                try{if(!UUID.fromString(id).toString().equals(id))throw invalid("Invalid native UUID");}
                catch(IllegalArgumentException bad){throw new IOException("Invalid native UUID",bad);}
                if(!kind.equals("stage")&&!kind.equals(role==1?"checkpoint":role==2?"identity":"index"))throw invalid("Unknown native metadata kind");
                BasicFileAttributes before=attributes(path);requireFile(before);
                // Existing terminal metadata semantics permit retained stages and
                // oversized known entries; oversized hashes remain null.
                String hash=before.size()>HistoryMetadataIO.MAX_RECORD?null:hash(path,before);
                same(before,attributes(path));
                member(role,name,before.size(),hash,before,recheck);
            }
        }catch(DirectoryIteratorException failure){throw new IOException("Native metadata enumeration failed",failure);}
    }
    private void member(int role,String name,long length,String hash,BasicFileAttributes attrs,boolean recheck)throws IOException {
        requireFile(attrs);
        if(name==null||name.length()>MAX_NAME||length!=attrs.size())throw invalid("Invalid native member dimensions");
        if(recheck){
            int at=find(role,name);if(at<0||seen[at])throw invalid("New/duplicate native member");
            Slot old=slots[at];
            if(old.length!=length||!Objects.equals(old.hash,hash)||!Objects.equals(old.key,attrs.fileKey())
                    ||!old.modified.equals(attrs.lastModifiedTime()))throw invalid("Native member changed");
            seen[at]=true;
        }else {
            if(count>=slots.length)throw invalid("Native terminal member capacity exceeded");
            Slot slot=slots[count++];slot.role=role;slot.name=name;slot.length=length;slot.hash=hash;
            slot.key=attrs.fileKey();slot.modified=attrs.lastModifiedTime();
        }
    }
    private BasicFileAttributes directory(int role)throws IOException {
        boundedRoot(roots[role]);BasicFileAttributes attrs=attributes(roots[role].toPath());
        if(!attrs.isDirectory()||attrs.fileKey()==null)throw invalid("Invalid native role directory");return attrs;
    }
    private void checkDirectories()throws IOException {
        for(int role=0;role<4;role++){
            BasicFileAttributes now=directory(role),before=anchors[role];
            if(before==null||!Objects.equals(before.fileKey(),now.fileKey())||!before.creationTime().equals(now.creationTime()))
                throw invalid("Native role directory replaced");
        }
    }
    private static BasicFileAttributes attributes(Path path)throws IOException{return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static void requireFile(BasicFileAttributes attrs)throws IOException {
        if(attrs==null||!attrs.isRegularFile()||attrs.fileKey()==null||attrs.size()<0)throw invalid("Invalid native file identity");
    }
    private static void same(BasicFileAttributes before,BasicFileAttributes after)throws IOException {
        requireFile(after);if(before.size()!=after.size()||!Objects.equals(before.fileKey(),after.fileKey())
                ||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw invalid("Native file changed while reading");
    }
    private String hash(Path path,BasicFileAttributes before)throws IOException {
        MessageDigest digest=HistoryStorage.digest();ByteBuffer bytes=ByteBuffer.wrap(scratch);long read=0;
        try(SeekableByteChannel channel=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(read<before.size()){
                bytes.clear();bytes.limit((int)Math.min(scratch.length,before.size()-read));int n=channel.read(bytes);
                if(n<=0)throw invalid("Native file truncated/stalled");read+=n;digest.update(scratch,0,n);
            }
            bytes.clear();bytes.limit(1);if(channel.read(bytes)!=-1)throw invalid("Native file grew");
        }
        same(before,attributes(path));return HistoryStorage.hex(digest.digest());
    }
    private int find(int role,String name){
        int low=0,high=count-1;while(low<=high){int mid=(low+high)>>>1;Slot slot=slots[mid];int c=Integer.compare(slot.role,role);if(c==0)c=slot.name.compareTo(name);if(c<0)low=mid+1;else if(c>0)high=mid-1;else return mid;}return -1;
    }
    private static int compare(Slot a,Slot b){int c=Integer.compare(a.role,b.role);return c!=0?c:a.name.compareTo(b.name);}
    // In-place heapsort: no second reference array during terminal capture.
    private void sort(){for(int root=count/2-1;root>=0;root--)sift(root,count);for(int end=count-1;end>0;end--){swap(0,end);sift(0,end);}}
    private void sift(int root,int length){while(root<length/2){int child=2*root+1;if(child+1<length&&compare(slots[child],slots[child+1])<0)child++;if(compare(slots[root],slots[child])>=0)return;swap(root,child);root=child;}}
    private void swap(int a,int b){Slot value=slots[a];slots[a]=slots[b];slots[b]=value;}
    private void clear(){for(int i=0;i<count;i++)slots[i].clear();Arrays.fill(anchors,null);Arrays.fill(seen,0,count,false);count=0;}
    private static IOException invalid(String why){return new IOException(why);}
}
