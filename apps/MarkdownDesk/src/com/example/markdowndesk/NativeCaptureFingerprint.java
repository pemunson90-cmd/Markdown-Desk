package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * Internal native-source predicate for PrivateOperationStore.prepare/commitCapture.
 * This is neither a complete ledger proof nor publication/consumer authority.
 * Initial typed Graph + frozen map establish semantics; fresh exact envelopes
 * preserve those semantics without constructing writable imports or a new Graph.
 *
 * Every entry point requires the primary monitor and mutation lease. The caller
 * supplies a freshly inspected control snapshot and the installed acknowledged
 * policy on each pass, proves complete accounting/exact PreparationTransition
 * separately, and keeps one lease from final proof through BEGIN. No callbacks,
 * source copies, pins, persistence or provider IO occur here.
 */
final class NativeCaptureFingerprint {
    private static final int SCRATCH_BYTES=65536, DIRECTORY_COUNT=6;
    private static final long BASE_CHARGE=SCRATCH_BYTES+16384;
    final String fingerprint;
    final long nativeEnvelopeBytes,nativeReservedCompletionBytes,nativeReservedCompletionSlots;
    private final File[] directories; // store, catalog, identity, index, control, parent
    private final Expected[] expected;
    private final BasicFileAttributes[] anchors;
    private final boolean[] seen;
    private final ByteBuffer scratch;
    private final UUID bundle;
    private final String registrationHash,policyHash;
    private final long policyGeneration,bootstrapQuota,quota;
    private final int automaticCount;
    private PhysicalProof active;

    private static final class Expected {
        final int directory;final String name,hash;final long length;
        final Path path;
        Expected(int directory,String name,long length,String hash,File[] roots){
            this.directory=directory;this.name=name;this.length=length;this.hash=hash;
            path=roots[directory].toPath().resolve(name);
        }
    }
    private static final Comparator<Expected> ORDER=Comparator.comparingInt((Expected e)->e.directory).thenComparing(e->e.name);

    /**
     * The shared monotonic budget must ALREADY charge all simultaneously retained
     * map/Graph/control/policy metadata, including their copies and reference tables.
     * This method charges its own workspace before allocation: 80KiB fixed, 2048B
     * per expected entry, plus 4B per UTF-8 byte in every retained full path/name,
     * and 2048B per directory. This covers expected/hash/path/reference tables,
     * two attribute objects per live path, directory keys, one proof and streaming
     * temporaries. Payload length is native envelope BYTES; names use UTF-8 BYTES.
     * No payload arrays and no independent 16MiB budget. Failed charges stay spent.
     */
    static NativeCaptureFingerprint capture(HistoryArchiveManifest frozen,HistoryGraph.Graph graph,
            HistoryControlStore.Inspection control,File store,File catalog,File identity,File index,
            long bootstrapQuotaBytes,HistoryStorage.Budget budget)throws IOException {
        return capture(frozen,graph,control,store,catalog,identity,index,bootstrapQuotaBytes,budget,null,null);
    }
    static NativeCaptureFingerprint capture(HistoryArchiveManifest frozen,HistoryGraph.Graph graph,
            HistoryControlStore.Inspection control,File store,File catalog,File identity,File index,
            long bootstrapQuotaBytes,HistoryStorage.Budget budget,PreparationSources owner,UUID preparation)throws IOException {
        HistoryStorage.requireMutation(store);
        if(frozen==null||graph==null||control==null||budget==null||catalog==null||identity==null||index==null)
            throw invalid("Missing initial native evidence/budget");
        // Reserve bounded preflight temporaries before hash clones/path tables.
        budget.charge(BASE_CHARGE);
        if(!store.isAbsolute()||!catalog.isAbsolute()||!identity.isAbsolute()||!index.isAbsolute()
                ||graph.registeredRoleNames.size()!=4||!graph.complete||!graph.usageComplete||(graph.pruneBlocked&&!graph.historicalCancelledOnly)||!graph.diagnostics.isEmpty()
                ||graph.effectivePolicy==null||control.state!=HistoryControlStore.State.VALIDATED
                ||!control.chainComplete||!control.usageComplete||!control.diagnostics.isEmpty()
                ||!control.stages.isEmpty()||control.hasMaintenance()||control.registration==null||control.latest==null
                ||!HistoryStorage.hex(frozen.graphHashBytes()).equals(graph.fingerprint)
                ||graph.records.size()!=frozen.entries.size()||graph.occupiedEntries!=frozen.entries.size()
                ||graph.actualBytes-graph.operationActualBytes!=frozen.nativeEnvelopeBytes
                ||graph.reservedCompletionBytes<0||graph.reservedCompletionSlots<0)
            throw invalid("Incomplete initial frozen native graph");
        if(graph.pruneBlocked){
            if(owner==null||preparation==null||control.operations.terminalCancelled.contains(preparation))throw invalid("Historical source capture needs exact new owner");
            owner.requireIdleBindingLocked(store,preparation,budget);
            PreparationSources registered=HistoryStorage.preparationSources(store);
            for(UUID old:control.operations.terminalCancelled)if(registered!=null&&registered.isPreparationLocked(old))throw invalid("Historical owner still registered");
        }
        int authority=0;long charge=0;
        for(File root:Arrays.asList(store,catalog,identity,index)){
            charge=add(charge,2048L+4L*utf8Length(root.getPath()));
        }
        budget.charge(charge);charge=0;
        File parent=store.getParentFile();
        if(parent==null)throw invalid("Missing native parent");
        charge=add(charge,4096L+8L*utf8Length(parent.getPath())+4L*utf8Length(HistoryControlStore.ROOT_NAME));
        for(HistoryArchiveManifest.Entry e:frozen.entries){
            // Native IDs/kinds in an immutable decoded manifest have bounded names.
            String root=e.role==HistoryArchiveManifest.Role.STORE?store.getPath():e.role==HistoryArchiveManifest.Role.CATALOG?catalog.getPath():e.role==HistoryArchiveManifest.Role.IDENTITY?identity.getPath():index.getPath();
            charge=add(charge,2048L+4L*(utf8Length(root)+1+36+1+utf8Length(e.kind.nativeName)));
        }
        for(HistoryControlStore.Entry e:control.entries)if(e.record!=null){
            if(e.filename==null||e.stage)throw invalid("Invalid initial authority member");
            authority++;charge=add(charge,2048L+4L*(utf8Length(parent.getPath())+1+utf8Length(HistoryControlStore.ROOT_NAME)+1+utf8Length(e.filename)));
        }
        if(authority<2||authority>HistoryControlStore.MAX_ENTRIES)throw invalid("Invalid authority member count");
        budget.charge(charge);
        File[] roots={HistoryStorage.canonicalRoot(store),HistoryStorage.canonicalRoot(catalog),
            HistoryStorage.canonicalRoot(identity),HistoryStorage.canonicalRoot(index),
            new File(parent,HistoryControlStore.ROOT_NAME),parent};
        HistoryStorage.siblings(roots[0],roots[1],roots[2],roots[3]);
        for(int i=0;i<4;i++){
            HistoryArchiveManifest.Role role=HistoryArchiveManifest.Role.values()[i];
            if(!roots[i].getName().equals(frozen.registeredRoles.get(role))
                    ||!roots[i].getName().equals(graph.registeredRoleNames.get(i)))throw invalid("Changed registered role");
        }
        Expected[] entries=new Expected[frozen.entries.size()+authority];int at=0;
        for(HistoryArchiveManifest.Entry e:frozen.entries){
            entries[at++]=new Expected(e.role.wireId-1,e.nativeId+"."+e.kind.nativeName,e.envelopeBytes,e.envelopeHash(),roots);
        }
        for(HistoryControlStore.Entry e:control.entries)if(e.record!=null){
            entries[at++]=new Expected(HistoryControlStore.ENROLLMENT_NAME.equals(e.filename)?5:4,e.filename,e.length,e.envelopeHash,roots);
        }
        Arrays.sort(entries,ORDER);
        for(int i=1;i<entries.length;i++)if(ORDER.compare(entries[i-1],entries[i])>=0)throw invalid("Duplicate expected native source");
        for(ProtectionStore.StoredRecord record:graph.records){
            if(record==null)throw invalid("Missing initial native descriptor");
            int role=HistoryArchiveManifest.Role.parse(record.rootRole).wireId-1;
            int found=find(entries,role,record.filename);
            if(found<0)throw invalid("Frozen map differs from typed graph");
            Expected e=entries[found];HistoryArchiveManifest.Kind kind=HistoryArchiveManifest.Kind.parse(record.kind);
            if(kind.role.wireId-1!=role||record.id==null||!e.name.equals(record.id+"."+kind.nativeName)
                    ||e.length!=record.serializedBytes||!e.hash.equals(record.envelopeHash))throw invalid("Frozen native descriptor changed");
        }
        NativeCaptureFingerprint result=new NativeCaptureFingerprint(frozen,graph,control,roots,entries,bootstrapQuotaBytes);
        result.validate(control,graph.effectivePolicy,false);
        Arrays.fill(result.anchors,null);
        return result;
    }
    private NativeCaptureFingerprint(HistoryArchiveManifest map,HistoryGraph.Graph graph,HistoryControlStore.Inspection control,
            File[] roots,Expected[] entries,long bootstrap)throws IOException {
        directories=roots;expected=entries;anchors=new BasicFileAttributes[entries.length+DIRECTORY_COUNT];seen=new boolean[entries.length];
        scratch=ByteBuffer.allocate(SCRATCH_BYTES);bundle=map.bundleId;
        registrationHash=HistoryStorage.hex(map.registrationHashBytes());policyHash=HistoryStorage.hex(map.policyHashBytes());
        policyGeneration=map.policyGeneration;quota=map.quotaBytes;automaticCount=map.automaticCount;bootstrapQuota=bootstrap;
        nativeEnvelopeBytes=map.nativeEnvelopeBytes;nativeReservedCompletionBytes=graph.reservedCompletionBytes;nativeReservedCompletionSlots=graph.reservedCompletionSlots;
        MessageDigest digest=HistoryStorage.digest();
        try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},digest))){
            text(out,"native-capture-sources-v1");text(out,bundle.toString());text(out,registrationHash);
            out.writeLong(bootstrapQuota);out.writeLong(policyGeneration);text(out,policyHash);out.writeLong(quota);out.writeInt(automaticCount);
            for(int i=0;i<4;i++){out.writeInt(i+1);text(out,roots[i].getPath());}
            out.writeLong(nativeEnvelopeBytes);out.writeLong(nativeReservedCompletionBytes);out.writeLong(nativeReservedCompletionSlots);
            out.writeInt(map.entries.size());
            // Full typed envelopes bind edges, history identities and reservation owners.
            for(Expected e:entries)if(e.directory<4){out.writeInt(e.directory+1);text(out,e.name);out.writeLong(e.length);text(out,e.hash);}
        }
        fingerprint=HistoryStorage.hex(digest.digest());
    }
    /** One outstanding token. Fresh discovery deliberately has no old inode anchor. */
    PhysicalProof prove(HistoryControlStore.Inspection fresh,HistoryCapacity.EffectivePolicy acknowledged)throws IOException {
        requireLease();if(active!=null)throw invalid("Native physical proof already open");
        try{validate(fresh,acknowledged,false);active=new PhysicalProof();return active;}
        catch(IOException|RuntimeException failure){Arrays.fill(anchors,null);throw failure;}
    }
    final class PhysicalProof implements AutoCloseable {
        private final Thread owner=Thread.currentThread();private boolean open=true;
        private PhysicalProof(){}
        /** Rehash/re-enumerate after EACH durability callback, before acknowledgement. */
        void recheck(HistoryControlStore.Inspection fresh,HistoryCapacity.EffectivePolicy acknowledged)throws IOException {
            // Unauthorized callers cannot mutate a lease owner's proof workspace.
            requireLease();if(owner!=Thread.currentThread())throw invalid("Foreign native physical proof thread");
            try{if(!open||active!=this)throw invalid("Invalid native physical proof");validate(fresh,acknowledged,true);}
            catch(IOException|RuntimeException failure){invalidate();throw failure;}
        }
        public void close()throws IOException {requireLease();if(owner!=Thread.currentThread())throw invalid("Foreign native physical proof thread");invalidate();}
        private void invalidate(){open=false;if(active==this){active=null;Arrays.fill(anchors,null);}}
    }
    private void requireLease()throws IOException{HistoryStorage.requireMutation(directories[0]);}
    private void validate(HistoryControlStore.Inspection control,HistoryCapacity.EffectivePolicy acknowledged,boolean anchored)throws IOException {
        requireLease();authority(control,acknowledged);Arrays.fill(seen,false);
        for(int i=0;i<directories.length;i++){
            HistoryStorage.canonicalRoot(directories[i]);BasicFileAttributes a=attributes(directories[i].toPath());
            if(!a.isDirectory()||a.isSymbolicLink()||a.fileKey()==null)throw invalid("Invalid native source directory");
            int slot=expected.length+i;
            if(anchored&&!Objects.equals(anchors[slot].fileKey(),a.fileKey()))throw invalid("Native source directory replaced");
            if(!anchored)anchors[slot]=a;
        }
        try{
            for(int role=0;role<5;role++)try(DirectoryStream<Path> paths=Files.newDirectoryStream(directories[role].toPath())){
                for(Path path:paths){String name=path.getFileName().toString();
                    // Operation/pin publication has its own complete transition proof.
                    if(role==4&&!authorityName(name))continue;
                    if(name.length()>96)throw invalid("Unknown native source member");
                    int slot=find(expected,role,name);
                    if(slot<0||seen[slot])throw invalid("Unknown/staged native source member");
                    seen[slot]=true;verify(slot,anchored);
                }
            }
        }catch(DirectoryIteratorException e){throw new IOException("Native source membership unavailable",e);}
        int enrollment=find(expected,5,HistoryControlStore.ENROLLMENT_NAME);
        if(enrollment<0)throw invalid("Missing expected enrollment");seen[enrollment]=true;verify(enrollment,anchored);
        try{attributes(directories[5].toPath().resolve(HistoryControlStore.ENROLLMENT_STAGE));throw invalid("Staged native enrollment");}catch(NoSuchFileException absent){/* exact absence */}
        for(boolean present:seen)if(!present)throw invalid("Missing native source member");
        // Recheck root keys after all streaming IO; directory mtimes may legitimately change.
        for(int i=0;i<directories.length;i++){
            BasicFileAttributes a=attributes(directories[i].toPath());
            if(!a.isDirectory()||!Objects.equals(a.fileKey(),anchors[expected.length+i].fileKey()))throw invalid("Native source directory changed during proof");
        }
    }
    private void authority(HistoryControlStore.Inspection control,HistoryCapacity.EffectivePolicy acknowledged)throws IOException {
        if(control==null||acknowledged==null||!control.chainComplete||!control.usageComplete||control.hasMaintenance()
                ||control.registration==null||control.latest==null||!bundle.equals(control.registration.bundleId)
                ||!registrationHash.equals(control.registration.envelopeHash)||control.registration.bootstrapQuotaBytes!=bootstrapQuota
                ||!bundle.equals(control.latest.bundleId)||policyGeneration!=control.latest.generation||!policyHash.equals(control.latest.envelopeHash)
                ||quota!=control.latest.quotaBytes||automaticCount!=control.latest.automaticCount
                ||!bundle.toString().equals(acknowledged.bundleId)||policyGeneration!=acknowledged.generation||!policyHash.equals(acknowledged.controlRecordHash)
                ||quota!=acknowledged.quotaBytes||automaticCount!=acknowledged.automaticCount)throw invalid("Changed/unacknowledged native source policy");
        if(control.registration.roles.size()!=4)throw invalid("Changed native role count");
        for(int i=0;i<4;i++)if(control.registration.roles.get(i).id!=i+1||!directories[i].getName().equals(control.registration.roles.get(i).basename))throw invalid("Changed native role registration");
        int count=0;
        for(HistoryControlStore.Entry e:control.entries)if(e.record!=null){
            if(e.stage||e.filename==null)throw invalid("Staged native authority");int directory=HistoryControlStore.ENROLLMENT_NAME.equals(e.filename)?5:4;
            int slot=find(expected,directory,e.filename);
            if(slot<0||e.length!=expected[slot].length||!Objects.equals(e.envelopeHash,expected[slot].hash))throw invalid("Changed native authority member");count++;
        }
        int wanted=0;for(Expected e:expected)if(e.directory>=4)wanted++;
        if(count!=wanted)throw invalid("Missing native authority member");
    }
    private void verify(int slot,boolean anchored)throws IOException {
        Expected e=expected[slot];BasicFileAttributes before=attributes(e.path);
        if(!before.isRegularFile()||before.isSymbolicLink()||before.fileKey()==null||before.size()!=e.length)throw invalid("Invalid native envelope identity/length");
        if(anchored)sameFile(anchors[slot],before);
        MessageDigest digest=HistoryStorage.digest();long length=0;
        try(SeekableByteChannel channel=Files.newByteChannel(e.path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(true){scratch.clear();int n=channel.read(scratch);if(n<0)break;if(n==0)throw invalid("Native envelope read stalled");length=add(length,n);if(length>e.length)throw invalid("Native envelope grew");digest.update(scratch.array(),0,n);}
        }
        sameFile(before,attributes(e.path));
        if(length!=e.length||!e.hash.equals(HistoryStorage.hex(digest.digest())))throw invalid("Changed native envelope bytes");
        if(!anchored)anchors[slot]=before;
    }
    private static void sameFile(BasicFileAttributes before,BasicFileAttributes after)throws IOException {
        if(before==null||before.fileKey()==null||!after.isRegularFile()||after.isSymbolicLink()
                ||!Objects.equals(before.fileKey(),after.fileKey())||before.size()!=after.size()
                ||!before.lastModifiedTime().equals(after.lastModifiedTime())||!before.creationTime().equals(after.creationTime()))throw invalid("Native envelope physically replaced/changed");
    }
    private static boolean authorityName(String name){return name.equals(HistoryControlStore.REGISTRATION_NAME)||name.startsWith("policy-")||name.endsWith(".control")||name.endsWith(".control.stage");}
    private static int find(Expected[] entries,int directory,String name){
        if(name==null)return -1;int low=0,high=entries.length-1;
        while(low<=high){int mid=(low+high)>>>1;Expected e=entries[mid];int c=Integer.compare(e.directory,directory);if(c==0)c=e.name.compareTo(name);if(c<0)low=mid+1;else if(c>0)high=mid-1;else return mid;}return -1;
    }
    private static BasicFileAttributes attributes(Path path)throws IOException{return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static int utf8Length(String text)throws IOException {
        if(text==null)throw invalid("Missing native path/name");long n=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<128)n++;else if(c<2048)n+=2;else if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw invalid("Invalid native path UTF8");n+=4;}else if(Character.isLowSurrogate(c))throw invalid("Invalid native path UTF8");else n+=3;}
        if(n>Integer.MAX_VALUE)throw invalid("Native path length overflow");return (int)n;
    }
    private static void text(DataOutputStream out,String value)throws IOException {byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static long add(long a,long b)throws IOException {try{return Math.addExact(a,b);}catch(ArithmeticException e){throw new IOException("Native source accounting overflow",e);}}
    private static IOException invalid(String text){return new IOException("Native capture: "+text);}
}
