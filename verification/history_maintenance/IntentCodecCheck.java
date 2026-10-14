package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Actual native envelopes; the codec never opens, publishes or removes a file. */
public final class IntentCodecCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    interface Action {void run()throws IOException;}
    interface Attempt {void run()throws Exception;}
    static void rejects(Attempt action)throws Exception{try{action.run();throw new AssertionError("expected rejection");}catch(IOException expected){checks++;}}
    static final class Disk implements ProtectionStore.Durability {
        Action onCatalogSync;
        public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
        public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        public void syncDirectory(File d)throws IOException{if(d.getName().equals("catalog")&&onCatalogSync!=null){Action a=onCatalogSync;onCatalogSync=null;a.run();}try(FileChannel c=FileChannel.open(d.toPath(),StandardOpenOption.READ)){c.force(true);}}
    }
    static final class Fixture {
        final Path parent=Files.createTempDirectory("history-intent-"); final Disk disk=new Disk();
        final ProtectionStore store=new ProtectionStore(parent.resolve("store").toFile(),disk,64L*1024*1024);
        final HistoryCatalog catalog=new HistoryCatalog(parent.resolve("catalog").toFile(),store,disk);
        final HistoryIdentity identity=new HistoryIdentity(parent.resolve("identity").toFile(),disk);
        final HistoryIndex index=new HistoryIndex(store,catalog,identity,parent.resolve("index").toFile(),disk);
        Fixture()throws Exception{}
        HistoryGraph graph(int count)throws Exception{return new HistoryGraph(store,catalog,identity,index,new HistoryPolicy(64L*1024*1024,count));}
        HistoryPruner pruner(int count)throws Exception{return new HistoryPruner(graph(count));}
        ProtectionStore.Transaction group(String uri,boolean complete,boolean indexed)throws Exception{
            ProtectionStore.Snapshot a=store.putSnapshot("tab",uri,"title",1,new byte[]{1,2,3},"original");
            ProtectionStore.Snapshot b=store.putSnapshot("tab",uri,"title",1,new byte[]{4,5},"attempted");
            return pair(uri,a,b,complete,indexed);
        }
        ProtectionStore.Transaction pair(String uri,ProtectionStore.Snapshot a,ProtectionStore.Snapshot b,boolean complete,boolean indexed)throws Exception{
            ProtectionStore.Transaction t=store.prepare(a,b,a.hash);if(complete)store.complete(t);
            if(indexed)index.recordVerified(identity.resolve(uri,"tab"),t.id);return t;
        }
        Map<String,String> capture()throws Exception{
            Map<String,String> m=new TreeMap<>();for(String r:Arrays.asList("store","catalog","identity","index"))try(java.util.stream.Stream<Path> s=Files.list(parent.resolve(r))){
                for(Path p:(Iterable<Path>)s::iterator){MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}m.put(r+"/"+p.getFileName(),HistoryStorage.hex(d.digest()));}}
            return m;
        }
    }

    static final String HASH="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    static HistoryPruneIntent.PolicyBinding policy(int count)throws IOException{return new HistoryPruneIntent.PolicyBinding(UUID.randomUUID().toString(),0,HASH,64L*1024*1024,count);}
    static HistoryPruneIntent.Authorization auth(HistoryPruner.DeletePlan p)throws IOException{return new HistoryPruneIntent.Authorization(p.proofFingerprint,p.selectedCheckpointIds);}
    static HistoryPruner.DeletePlan plan(Fixture f,int count)throws Exception{HistoryPruner.Result r=f.pruner(count).planAutomaticPressure(1,1);check(r.plan!=null,"native preview");return r.plan;}
    static final class Cursor {
        final byte[] bytes;int pos=180;Cursor(byte[] bytes){this.bytes=bytes;}
        int integer(){int value=java.nio.ByteBuffer.wrap(bytes,pos,4).getInt();pos+=4;return value;}
        String text(){int size=integer();String value=new String(bytes,pos,size,java.nio.charset.StandardCharsets.UTF_8);pos+=size;return value;}
        void ids(){int n=integer();for(int i=0;i<n;i++)text();}
        void members(){int n=integer();for(int i=0;i<n;i++){text();text();text();text();pos+=8;text();}}
        void group(){for(int i=0;i<5;i++)text();pos+=8;ids();members();int n=integer();for(int i=0;i<n*6;i++)text();}
    }
    static void repaired(byte[] bad,boolean proofChanged)throws Exception{
        if(proofChanged){int size=java.nio.ByteBuffer.wrap(bad,176,4).getInt();byte[] ph=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(bad,180,180+size));System.arraycopy(ph,0,bad,80,32);System.arraycopy(ph,0,bad,112,32);}
        byte[] checksum=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bad,bad.length-32));System.arraycopy(checksum,0,bad,bad.length-32,32);rejects(()->HistoryPruneIntent.decode(bad));
    }
    static void adversarial(byte[] encoded,HistoryPruner.DeletePlan p)throws Exception{
        Cursor cursor=new Cursor(encoded);cursor.text();cursor.text();cursor.text();int automatic=cursor.pos;cursor.pos++;
        int requested=cursor.pos,bytes=requested+16;cursor.pos+=40;int policyCount=cursor.pos;cursor.pos+=4;cursor.ids();cursor.ids();int memberCount=cursor.pos;
        byte[] bad=encoded.clone();bad[automatic]=0;repaired(bad,true);
        bad=encoded.clone();bad[automatic]=2;repaired(bad,true);
        bad=encoded.clone();java.nio.ByteBuffer.wrap(bad,bytes,8).putLong(Long.MAX_VALUE);repaired(bad,true);
        bad=encoded.clone();java.nio.ByteBuffer.wrap(bad,requested,8).putLong(-1);repaired(bad,true);
        bad=encoded.clone();java.nio.ByteBuffer.wrap(bad,memberCount,4).putInt(257);repaired(bad,true);
        bad=encoded.clone();java.nio.ByteBuffer.wrap(bad,policyCount,4).putInt(0);java.nio.ByteBuffer.wrap(bad,76,4).putInt(0);repaired(bad,true);
        int n=cursor.integer();check(n==5,"native member count");cursor.text();int filenameLength=cursor.pos;cursor.text();cursor.text();cursor.text();int nativeLength=cursor.pos;
        bad=encoded.clone();java.nio.ByteBuffer.wrap(bad,nativeLength,8).putLong(0);repaired(bad,true);
        bad=encoded.clone();java.nio.ByteBuffer.wrap(bad,filenameLength,4).putInt(65536);repaired(bad,true);
        cursor.pos=memberCount;int count=cursor.integer(),snapshotStart=-1,snapshotLength=0;
        for(int i=0;i<count;i++){int start=cursor.pos;cursor.text();cursor.text();cursor.text();String kind=cursor.text();cursor.pos+=8;cursor.text();if("snapshot".equals(kind)){
            if(snapshotStart<0){snapshotStart=start;snapshotLength=cursor.pos-start;}else{check(snapshotLength==cursor.pos-start,"equal native snapshot descriptor framing");bad=encoded.clone();System.arraycopy(bad,snapshotStart,bad,start,snapshotLength);repaired(bad,true);}}}
        cursor=new Cursor(encoded);cursor.text();cursor.text();cursor.text();cursor.pos+=45;cursor.ids();cursor.ids();cursor.members();int groups=cursor.integer();for(int i=0;i<groups;i++)cursor.group();int anchorCount=cursor.integer();check(anchorCount==1,"native anchor count");
        for(int i=0;i<5;i++)cursor.text();int anchorSequence=cursor.pos;
        bad=encoded.clone();java.nio.ByteBuffer.wrap(bad,anchorSequence,8).putLong(p.groups.get(0).sequence);repaired(bad,true);
        bad=encoded.clone();int proofSize=java.nio.ByteBuffer.wrap(bad,176,4).getInt();int identityUuid=180+proofSize+4+4+2;
        check(java.nio.ByteBuffer.wrap(bad,180+proofSize+4,4).getInt()==1,"native identity envelope count");bad[identityUuid]^=1;repaired(bad,false);
        hostile(encoded,"store","index",false);hostile(encoded,p.members.get(0).id,p.members.get(0).id.toUpperCase(Locale.ROOT),false);
        byte[] malformed=encoded.clone();int uri=indexOf(malformed,"content://intent".getBytes(java.nio.charset.StandardCharsets.UTF_8),180);check(uri>=0,"native routing alias found");malformed[uri]=(byte)0xc0;repaired(malformed,true);
    }
    static int indexOf(byte[] bytes,byte[] needle,int start){for(int i=start;i<=bytes.length-needle.length;i++){boolean same=true;for(int j=0;j<needle.length;j++)same&=bytes[i+j]==needle[j];if(same)return i;}return -1;}
    static void regular()throws Exception {
        Fixture f=new Fixture();ProtectionStore.Transaction old=f.group("content://intent",true,true);f.group("content://intent",true,true);
        HistoryPruner.DeletePlan p=plan(f,20);Map<String,String> before=f.capture();
        HistoryPruneIntent intent=HistoryPruneIntent.create(p,f.graph(20).scan(),policy(20),auth(p),HASH);
        byte[] encoded=HistoryPruneIntent.encode(intent);HistoryPruneIntent decoded=HistoryPruneIntent.decode(encoded);
        check(encoded.length<=65536&&decoded.serializedBytes==encoded.length,"complete envelope bound/length");
        check(decoded.envelopeHash.equals(HistoryStorage.hex(MessageDigest.getInstance("SHA-256").digest(encoded))),"complete envelope hash");
        check(Arrays.equals(encoded,HistoryPruneIntent.encode(decoded)),"canonical roundtrip");
        check(decoded.members.size()==5&&decoded.groups.size()==1&&decoded.anchors.size()==1&&decoded.identities.size()==1,"native closure/identity proof");
        check(decoded.previewFingerprint.equals(p.proofFingerprint)&&decoded.graphFingerprint.equals(p.graphFingerprint),"exact preview binding");
        for(HistoryPruneIntent.Member member:decoded.members){check(before.get(member.role.wire+"/"+member.id+"."+member.kind.wire).equals(member.envelopeHash),"exact native full envelope hash");}
        check(f.capture().equals(before),"no native mutation");
        try{decoded.members.clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException expected){checks++;}
        byte[] leaked=HistoryPruneIntent.encode(intent);leaked[0]=0;check(Arrays.equals(encoded,HistoryPruneIntent.encode(intent)),"encoded arrays do not mutate immutable intent");
        rejects(()->HistoryPruneIntent.create(p,f.graph(20).scan(),policy(5),auth(p),HASH));
        rejects(()->HistoryPruneIntent.create(p,f.graph(20).scan(),policy(20),new HistoryPruneIntent.Authorization(HASH,Collections.emptySet()),HASH));
        rejects(()->HistoryPruneIntent.create(p,f.graph(20).scan(),policy(20),new HistoryPruneIntent.Authorization(p.proofFingerprint,Arrays.asList(UUID.randomUUID().toString())),HASH));
        rejects(()->new HistoryPruneIntent.Authorization(HASH,Arrays.asList(old.id,old.id)));
        rejects(()->new HistoryPruneIntent.PolicyBinding("../escape",0,HASH,64L*1024*1024,1));
        rejects(()->HistoryPruneIntent.decode(new byte[65537]));
        for(int n:new int[]{0,4,11,encoded.length-1})rejects(()->HistoryPruneIntent.decode(Arrays.copyOf(encoded,n)));
        for(int off:new int[]{0,4,8,encoded.length-1}){byte[] bad=encoded.clone();bad[off]^=1;rejects(()->HistoryPruneIntent.decode(bad));}
        byte[] extra=Arrays.copyOf(encoded,encoded.length+1);rejects(()->HistoryPruneIntent.decode(extra));
        adversarial(encoded,p);
        // Valid checksums cannot rescue a forged UUID/path, role/kind, closure, or duplicate descriptor.
        hostile(encoded,"store","other",false);hostile(encoded,"snapshot","../paths",false);
        hostile(encoded,old.original.id,old.attempted.id,true);
        HistoryCatalog.Checkpoint name=f.catalog.name(old.attempted.id,"named");HistoryPruner.DeletePlan selected=f.pruner(20).planSelected(Arrays.asList(name.id)).plan;
        check(selected!=null,"selected native preview");HistoryPruneIntent named=HistoryPruneIntent.create(selected,f.graph(20).scan(),policy(20),auth(selected),HASH);
        check(HistoryPruneIntent.decode(HistoryPruneIntent.encode(named)).selectedCheckpointIds.equals(Collections.singleton(name.id)),"explicit selected authorization binding");
        byte[] namedBytes=HistoryPruneIntent.encode(named);int namedProof=java.nio.ByteBuffer.wrap(namedBytes,176,4).getInt();namedBytes[180+namedProof+4]^=1;repaired(namedBytes,false);
        rejects(()->HistoryPruneIntent.create(p,f.graph(20).scan(),policy(20),auth(p),HASH)); // the new checkpoint changed the exact graph
        HistoryPruner.DeletePlan zero=f.pruner(0).planSelected(Arrays.asList(name.id)).plan;
        check(HistoryPruneIntent.decode(HistoryPruneIntent.encode(HistoryPruneIntent.create(zero,f.graph(0).scan(),policy(0),auth(zero),HASH))).mode==HistoryPruner.Mode.SELECTED,"count0 permits explicit selected intent only");
        Fixture floor=new Fixture();for(int i=0;i<7;i++)floor.group("content://floor",true,true);
        HistoryPruner.DeletePlan fp=floor.pruner(5).planAutomatic(1,1).plan;check(fp!=null,"native floor preview");
        check(HistoryPruneIntent.decode(HistoryPruneIntent.encode(HistoryPruneIntent.create(fp,floor.graph(5).scan(),policy(5),auth(fp),HASH))).mode==HistoryPruner.Mode.AUTOMATIC_FLOOR,"floor domain preserved");
        Fixture provenance=new Fixture();provenance.group("content://origin",true,true);String origin=provenance.identity.resolve("content://origin","tab");provenance.identity.forkAfterVerifiedSaveAs(origin,"content://fork");
        provenance.group("content://fork",true,true);provenance.group("content://fork",true,true);HistoryPruner.DeletePlan forkPlan=plan(provenance,20);
        HistoryPruneIntent fork=HistoryPruneIntent.create(forkPlan,provenance.graph(20).scan(),policy(20),auth(forkPlan),HASH);
        check(fork.identityEnvelopes.size()==2&&fork.identities.get(0).origins.contains(origin),"exact full immutable origin identity envelopes included");
        Fixture tab=new Fixture();tab.group("",true,true);tab.group("",true,true);HistoryPruner.DeletePlan tabPlan=plan(tab,20);
        HistoryPruneIntent tabIntent=HistoryPruneIntent.create(tabPlan,tab.graph(20).scan(),policy(20),auth(tabPlan),HASH);
        check(tabIntent.identities.get(0).exactUri==null&&"tab".equals(tabIntent.identities.get(0).tabId),"nullable native unsaved TAB namespace preserved");
        check(HistoryPruneIntent.INTENT_NAME.equals("prune.intent")&&HistoryPruneIntent.STAGE_NAME.equals("prune.intent.stage"),"fixed future names");
    }
    static void hostile(byte[] envelope,String from,String to,boolean last)throws Exception {
        check(from.length()==to.length(),"fixture same length");byte[] bad=envelope.clone();int proofOffset=180;int size=java.nio.ByteBuffer.wrap(bad,176,4).getInt();
        byte[] a=from.getBytes(java.nio.charset.StandardCharsets.UTF_8),b=to.getBytes(java.nio.charset.StandardCharsets.UTF_8);int hit=-1;
        for(int i=proofOffset;i<=proofOffset+size-a.length;i++){boolean same=true;for(int j=0;j<a.length;j++)same&=bad[i+j]==a[j];if(same){hit=i;if(!last)break;}}
        check(hit>=0,"hostile field found");System.arraycopy(b,0,bad,hit,b.length);
        byte[] proof=Arrays.copyOfRange(bad,proofOffset,proofOffset+size);byte[] ph=MessageDigest.getInstance("SHA-256").digest(proof);System.arraycopy(ph,0,bad,80,32);System.arraycopy(ph,0,bad,112,32);
        byte[] checksum=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bad,bad.length-32));System.arraycopy(checksum,0,bad,bad.length-32,32);rejects(()->HistoryPruneIntent.decode(bad));
    }
    static void memory()throws Exception {
        Fixture f=new Fixture();byte[] payload=new byte[ProtectionStore.MAX_DOCUMENT_BYTES-1];Arrays.fill(payload,(byte)0x5a);
        ProtectionStore.Snapshot a=f.store.putSnapshot("tab","content://large",null,1,payload,"original");payload=null;
        ProtectionStore.Snapshot b=f.store.putSnapshot("tab","content://large",null,1,new byte[]{1},"attempted");ProtectionStore.Transaction old=f.pair("content://large",a,b,true,true);f.group("content://large",true,true);
        byte[] headroom=new byte[32*1024*1024];HistoryPruner.DeletePlan p=plan(f,20);
        byte[] encoded=HistoryPruneIntent.encode(HistoryPruneIntent.create(p,f.graph(20).scan(),policy(20),auth(p),HASH));
        check(encoded.length<16384&&HistoryPruneIntent.decode(encoded).members.size()==5,"8MiB native sources never enter encoded intent");
        List<String> names=new ArrayList<>();boolean reached=false;
        for(int i=0;i<128;i++){names.add(f.catalog.name(old.attempted.id,"name"+i).id);HistoryPruner.DeletePlan q=f.pruner(20).planSelected(names).plan;if(q==null)break;
            try{HistoryPruneIntent intent=HistoryPruneIntent.create(q,f.graph(20).scan(),policy(20),auth(q),HASH);check(HistoryPruneIntent.encode(intent).length<=65536,"complete bound includes proof+administrative overhead");}
            catch(IOException bounded){
                long identityCount=f.graph(20).scan().records.stream().filter(r->r.rootRole.equals("identity")).count();
                long completeBytes=220L+q.proofBytes+16L*q.selectedCheckpointIds.size()+58L*identityCount;
                check(completeBytes>65536&&q.proofBytes<=65536,"independent complete frame calculation exceeds limit while valid proof still fits");
                check(bounded.getMessage().startsWith("Complete intent "),"specifically complete-envelope bound rejected, not an unrelated decode failure");reached=true;break;}}
        check(reached&&headroom[headroom.length-1]==0,"overhead boundary reached under128MiB heap/32MiB held");
    }
    public static void main(String[] args)throws Exception{if(args.length==0)regular();else memory();System.out.println("PASS inert history intent codec: "+checks+" checks; host only, no deletion authority");}
}
