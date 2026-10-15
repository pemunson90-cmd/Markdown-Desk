package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Focused, independent wire fixtures; runs with -Xmx32m and never buffers a large capsule. */
public final class EvidenceCheck {
    static int checks;
    static final UUID ZERO=new UUID(0,0), PREP=UUID.fromString("12345678-1234-4234-8234-123456789abc"), NODE=UUID.fromString("87654321-4321-4321-8321-987654321abc");
    static final String EMPTY=sha(new byte[0]), NIL=String.join("",Collections.nCopies(64,"0"));
    interface Task {void run()throws Exception;}
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void rejects(Task task,String why)throws Exception{try{task.run();throw new AssertionError("Accepted "+why);}catch(IOException expected){checks++;}}
    static String sha(byte[] bytes){try{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new AssertionError(e);}}
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    static byte[] hash(String s){byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return b;}
    static final class Item {
        final PrivateOperationEvidence.Row row;final byte[] raw;
        Item(PrivateOperationEvidence.Row row,byte[] raw){this.row=row;this.raw=raw;}
    }
    static PrivateOperationEvidence.Row row(int role,UUID owner,UUID node,int kind,int state,String name,long declared,String dh,long actual,String ah,long future,int slots,int relation){return new PrivateOperationEvidence.Row(role,owner,node,kind,state,name,declared,dh,actual,ah,future,slots,relation);}
    static Item context(int kind,String name,byte[] bytes){return new Item(row(4,ZERO,ZERO,kind,2,name,bytes.length,sha(bytes),bytes.length,sha(bytes),0,0,0),bytes);}
    static List<Item> contexts()throws Exception {
        List<HistoryControlStore.Role> roles=Arrays.asList(new HistoryControlStore.Role(1,"snapshots"),new HistoryControlStore.Role(2,"catalog"),new HistoryControlStore.Role(3,"identity"),new HistoryControlStore.Role(4,"index"));
        byte[] registration=HistoryControlStore.encodeRegistration(PREP,64L<<20,roles,64L<<20,20);
        List<Item> rows=new ArrayList<>();
        rows.add(context(4,"baseline-c0",new byte[]{1}));rows.add(context(6,"baseline-graph",new byte[]{2}));rows.add(context(5,"baseline-ledger",new byte[]{3}));rows.add(context(7,"baseline-native",new byte[]{4}));
        rows.add(context(1,"history_enrollment.control",registration));rows.add(context(2,"registration.control",registration));return rows;
    }
    static byte[] fixture(List<Item> rows)throws Exception {
        long length=56;for(Item item:rows)length+=138+item.row.name.getBytes(StandardCharsets.UTF_8).length+item.raw.length;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.write("MDEVID1\n".getBytes(StandardCharsets.US_ASCII));out.writeInt(1);out.writeLong(length);out.writeInt(rows.size());
        for(Item i:rows){PrivateOperationEvidence.Row r=i.row;out.writeByte(r.role);uuid(out,r.ownerPreparation);uuid(out,r.nodeId);out.writeByte(r.kind);out.writeByte(r.state);byte[] name=r.name.getBytes(StandardCharsets.UTF_8);out.writeShort(name.length);out.write(name);out.writeLong(r.declaredLength);out.write(hash(r.declaredHash));out.writeLong(r.actualLength);out.write(hash(r.actualHash));out.writeLong(r.futureBytes);out.writeInt(r.futureSlots);out.writeByte(r.relation);out.writeLong(i.raw.length);out.write(i.raw);}
        out.write(hash(sha(bytes.toByteArray())));return bytes.toByteArray();
    }
    static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    static PrivateOperationEvidence.Source source(List<Item> rows){return new PrivateOperationEvidence.Source(){public long length(){long n=56;for(Item i:rows)n+=138+i.row.name.getBytes(StandardCharsets.UTF_8).length+i.raw.length;return n;}public int rowCount(){return rows.size();}public PrivateOperationEvidence.Cursor open(){return new PrivateOperationEvidence.Cursor(){int at=-1;public PrivateOperationEvidence.Row next(){return ++at<rows.size()?rows.get(at).row:null;}public InputStream openRaw(){return new ByteArrayInputStream(rows.get(at).raw);}public void close(){}};}};}
    static void read(byte[] bytes)throws IOException {try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.read(new ByteArrayInputStream(bytes),null,new PrivateBundleInventory.MetadataBudget())){}}
    public static void main(String[] args)throws Exception {
        List<Item> base=contexts();byte[] wire=fixture(base);PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
        try(PrivateOperationEvidence.Summary result=PrivateOperationEvidence.read(new ByteArrayInputStream(wire),null,budget)){check(result.length==wire.length&&result.rowCount==6,"summary fields");check(budget.usedBytes()==result.metadataBytes,"retain shared metadata debit");}
        check(budget.usedBytes()==0,"summary releases local reservation");
        ByteArrayOutputStream copied=new ByteArrayOutputStream();try(PrivateOperationEvidence.Summary result=PrivateOperationEvidence.write(source(base),copied,budget)){check(Arrays.equals(wire,copied.toByteArray()),"writer matches independent wire");check(result.hash.equals(sha(wire)),"full capsule hash");}
        List<Item> failed=new ArrayList<>();failed.add(new Item(row(2,PREP,NODE,6,3,"state-"+NODE+".frame.stage",100,NIL,3,sha(new byte[]{9,8,7}),97,0,0),new byte[]{9,8,7}));failed.addAll(base);
        byte[] fw=fixture(failed);final long[] observed={0};try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.read(new ByteArrayInputStream(fw),r->new OutputStream(){public void write(int x){observed[0]++;}public void write(byte[] b,int o,int n){observed[0]+=n;}},budget)){}check(observed[0]>3,"lossless failed and context bytes delivered");
        List<Item> bad=new ArrayList<>(base);bad.remove(0);final List<Item> missing=bad;rejects(()->read(fixture(missing)),"missing context");
        bad=new ArrayList<>(base);bad.add(0,base.get(0));final List<Item> duplicate=bad;rejects(()->read(fixture(duplicate)),"duplicate context");
        byte[] corrupt=wire.clone();corrupt[corrupt.length-1]^=1;rejects(()->read(corrupt),"checksum");
        rejects(()->read(Arrays.copyOf(wire,wire.length-1)),"truncated capsule");
        byte[] trailing=Arrays.copyOf(wire,wire.length+1);rejects(()->read(trailing),"trailing capsule");
        PrivateBundleInventory.MetadataBudget tiny=new PrivateBundleInventory.MetadataBudget((512L<<10)-1);final int[] reads={0};rejects(()->PrivateOperationEvidence.read(new InputStream(){public int read(){reads[0]++;return -1;}},null,tiny),"workspace precharge");check(reads[0]==0&&tiny.usedBytes()==0,"budget refusal before input/allocation");
        rejects(()->PrivateOperationEvidence.read(new ByteArrayInputStream(wire),r->{throw new IOException("sink");},budget),"sink failure");check(budget.usedBytes()==0,"failure releases reservation");
        List<Item> relation=new ArrayList<>(failed);PrivateOperationEvidence.Row old=failed.get(0).row;relation.set(0,new Item(row(2,PREP,NODE,6,3,old.name,100,NIL,3,old.actualHash,97,0,1),failed.get(0).raw));rejects(()->read(fixture(relation)),"unproved prefix relation");
        List<Item> path=new ArrayList<>(failed);path.set(0,new Item(row(2,PREP,NODE,6,3,"../bad",100,NIL,3,old.actualHash,97,0,0),failed.get(0).raw));rejects(()->read(fixture(path)),"path basename");
        boundaries(base,failed);policyChain(base);legacy(base);large(base);System.out.println("EvidenceCheck PASS "+checks+" checks");
    }
    static void boundaries(List<Item> base,List<Item> failed)throws Exception {
        byte[] empty=fixture(Collections.emptyList());read(empty);checks++;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.write(source(Collections.emptyList()),out,new PrivateBundleInventory.MetadataBudget())){}
        check(Arrays.equals(empty,out.toByteArray()),"canonical empty writer");
        List<Item> unordered=new ArrayList<>(base);Collections.reverse(unordered);rejects(()->read(fixture(unordered)),"unsorted rows");
        List<Item> wrongName=new ArrayList<>(base);wrongName.set(0,context(4,"wrong-c0",new byte[]{1}));rejects(()->read(fixture(wrongName)),"witness basename");
        PrivateOperationEvidence.Row stage=failed.get(0).row;
        List<Item> slots=new ArrayList<>(failed);slots.set(0,new Item(row(2,PREP,NODE,6,3,stage.name,100,NIL,3,stage.actualHash,97,1,0),failed.get(0).raw));
        rejects(()->read(fixture(slots)),"stage future slot");
        List<Item> absent=new ArrayList<>(base);absent.add(0,new Item(row(2,PREP,NODE,6,0,"state-"+NODE+".frame",3,stage.actualHash,0,EMPTY,3,1,0),new byte[0]));read(fixture(absent));checks++;
        absent.set(0,new Item(row(2,PREP,NODE,6,0,"state-"+NODE+".frame",3,stage.actualHash,0,EMPTY,2,1,0),new byte[0]));rejects(()->read(fixture(absent)),"absent byte commitment");
        List<Item> conflict=new ArrayList<>(failed);conflict.add(0,new Item(row(2,PREP,NODE,6,2,"state-"+NODE+".frame",3,stage.actualHash,3,stage.actualHash,0,0,0),failed.get(0).raw));rejects(()->read(fixture(conflict)),"stage/final logical conflict");
        List<Item> cap=new ArrayList<>(base);cap.add(0,new Item(row(2,PREP,NODE,2,0,"capture-"+NODE+".descriptor",(16L<<20)+1,stage.actualHash,0,EMPTY,(16L<<20)+1,1,0),new byte[0]));rejects(()->read(fixture(cap)),"descriptor declaration cap");
        byte[] utf=fixture(base);utf[61]=(byte)255;byte[] sum=hash(sha(Arrays.copyOf(utf,utf.length-32)));System.arraycopy(sum,0,utf,utf.length-32,32);rejects(()->read(utf),"invalid UTF8");
        PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
        try(PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.read(new InputStream(){int at;public int read(){return at<empty.length?empty[at++]&255:-1;}public int read(byte[] b,int o,int n){return 0;}},null,budget)){checks++;}
        check(budget.usedBytes()==0,"zero-progress input closes budget");
        List<Item> badHash=new ArrayList<>(failed);badHash.set(0,new Item(stage,new byte[]{9,8,6}));rejects(()->read(fixture(badHash)),"physical hash mismatch");
        final IOException primary=new IOException("primary");
        try {PrivateOperationEvidence.read(new ByteArrayInputStream(fixture(base)),r->new OutputStream(){public void write(int b)throws IOException{throw primary;}public void close()throws IOException{throw new IOException("close");}},budget);throw new AssertionError("sink accepted");}
        catch(IOException failure){check(failure==primary&&failure.getSuppressed().length==1,"primary sink failure preserved");}
        check(budget.usedBytes()==0,"all error owners released");
        // The metadata preimage is streamed but must debit the same shared16MiB owner before raw access.
        final long witnessLength=16L<<20;final int[] opens={0};
        final PrivateOperationEvidence.Row witness=row(4,ZERO,ZERO,4,2,"baseline-c0",witnessLength,NIL,witnessLength,NIL,0,0,0);
        PrivateOperationEvidence.Source hugeWitness=new PrivateOperationEvidence.Source(){public long length(){return 56+138+witness.name.length()+witnessLength;}public int rowCount(){return 1;}public PrivateOperationEvidence.Cursor open(){return new PrivateOperationEvidence.Cursor(){int at;public PrivateOperationEvidence.Row next(){return at++==0?witness:null;}public InputStream openRaw(){opens[0]++;return new ByteArrayInputStream(new byte[0]);}public void close(){}};}};
        rejects(()->PrivateOperationEvidence.write(hugeWitness,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},budget),"shared witness metadata cap");
        check(opens[0]==0&&budget.usedBytes()==0,"witness precharge before raw access");
    }
    static void policyChain(List<Item> base)throws Exception {
        List<Item> rows=new ArrayList<>(base);byte[] policy=HistoryControlStore.encodePolicy(PREP,1,sha(base.get(4).raw),64L<<20,20);rows.add(5,context(3,"policy-0000000000000001.control",policy));read(fixture(rows));checks++;
        rows.set(5,context(3,"policy-0000000000000002.control",HistoryControlStore.encodePolicy(PREP,2,sha(policy),64L<<20,20)));rejects(()->read(fixture(rows)),"policy predecessor omission");
    }
    static void legacy(List<Item> base)throws Exception {
        HistoryControlStore.Registration registration=(HistoryControlStore.Registration)HistoryControlStore.decode(base.get(4).raw);
        HistoryPinRecord begin=HistoryPinRecord.begin(NODE,registration,registration,HistoryPinRecord.Purpose.EXPORT,HistoryPinRecord.Scope.ALL_BUNDLE,new HistoryPinRecord.Manifest(HistoryPinRecord.Domain.NATIVE_HISTORY_V1,0,0,EMPTY),Collections.emptyList());
        byte[] b=HistoryPinRecord.encode(begin),c=HistoryPinRecord.close(begin,HistoryPinRecord.Outcome.CANCELLED);List<Item> rows=new ArrayList<>();
        rows.add(new Item(row(1,ZERO,NODE,3,2,begin.name(),b.length,sha(b),b.length,sha(b),0,0,0),b));rows.add(new Item(row(1,ZERO,NODE,4,2,"pin-"+NODE+".close",c.length,sha(c),c.length,sha(c),0,0,0),c));rows.addAll(base);read(fixture(rows));checks++;
        rows.remove(1);rejects(()->read(fixture(rows)),"unmatched legacy BEGIN");
    }
    static void large(List<Item> base)throws Exception {
        final long size=(9L<<20)+3;MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];Arrays.fill(block,(byte)7);for(long left=size;left>0;left-=Math.min(left,block.length))digest.update(block,0,(int)Math.min(left,block.length));String h=hex(digest.digest());
        PrivateOperationEvidence.Row big=row(2,PREP,NODE,4,3,"evidence-"+NODE+".capsule.stage",size+1,h,size,h,1,0,0);List<PrivateOperationEvidence.Row> rows=new ArrayList<>();rows.add(big);for(Item item:base)rows.add(item.row);
        PrivateOperationEvidence.Source source=new PrivateOperationEvidence.Source(){public int rowCount(){return rows.size();}public long length(){long n=56+138+big.name.length()+size;for(Item i:base)n+=138+i.row.name.length()+i.raw.length;return n;}public PrivateOperationEvidence.Cursor open(){return new PrivateOperationEvidence.Cursor(){int at=-1;public PrivateOperationEvidence.Row next(){return ++at<rows.size()?rows.get(at):null;}public InputStream openRaw(){if(at>0)return new ByteArrayInputStream(base.get(at-1).raw);return new InputStream(){long left=size;public int read(){if(left==0)return -1;left--;return 7;}public int read(byte[] b,int off,int len){if(left==0)return -1;int n=(int)Math.min(left,len);Arrays.fill(b,off,off+n,(byte)7);left-=n;return n;}};}public void close(){}};}};
        Path path=Files.createTempFile("evidence-large-",".capsule");try{try(OutputStream out=Files.newOutputStream(path);PrivateOperationEvidence.Summary ignored=PrivateOperationEvidence.write(source,out,new PrivateBundleInventory.MetadataBudget())){}try(InputStream in=Files.newInputStream(path);PrivateOperationEvidence.Summary result=PrivateOperationEvidence.read(in,null,new PrivateBundleInventory.MetadataBudget())){check(result.length==Files.size(path)&&result.length>8L<<20,"stream capsule over8MiB");}}finally{Files.delete(path);}
    }
}
