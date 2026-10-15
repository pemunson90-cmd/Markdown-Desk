package com.example.markdowndesk;
import java.io.*;
import java.util.*;
import static com.example.markdowndesk.ScopedArchiveCheck.*;
final class InventoryCheck {
 static class Scope {
  long id;int kind=2,flags=3;long[] origins={},tabs={};long active;boolean policy;
  Scope(long id){this.id=id;}
 }
 static class Row {
  long id,scope,length;int type;UUID identity;long[] edges={};
  Row(long id,long scope,int type,UUID identity){this.id=id;this.scope=scope;this.type=type;this.identity=identity;}
 }
 static class Origin {long id;long[] parents={};Origin(long id){this.id=id;}}
 static class Edge {
  long id,entry,origin;int type;UUID identity;int[] tags;byte[][] values;
  Edge(long id,long entry,long origin,int type,UUID identity,String... values)throws Exception {
   this.id=id;this.entry=entry;this.origin=origin;this.type=type;this.identity=identity;
   tags=new int[values.length];this.values=new byte[values.length][];
   for(int i=0;i<values.length;i++){tags[i]=type==9?2+i:type==6?5:type==5?4:1;this.values[i]=values[i].getBytes("UTF-8");}
  }
 }
 static class Fixture {
  List<Scope> scopes=new ArrayList<>();List<Row> rows=new ArrayList<>();List<Origin> origins=new ArrayList<>();List<Edge> edges=new ArrayList<>();
  Fixture(){Scope root=new Scope(2);root.kind=1;scopes.add(root);}
  byte[] encode()throws Exception {
   byte[] tables=pack(o->{
    for(Scope s:scopes){uuid(o,q(s.id));o.writeByte(s.kind);o.writeByte(s.flags);refs(o,s.origins);int n=0;for(Row r:rows)if(r.scope==s.id)n++;o.writeInt(n);o.writeByte(s.active==0?0:1);if(s.active!=0)uuid(o,q(s.active));refs(o,s.tabs);o.writeByte(s.policy?1:0);if(s.policy){uuid(o,new UUID(0,0));o.writeLong(0);o.writeLong(64L<<20);o.writeInt(5);o.write(new byte[32]);}}
    for(Row r:rows){uuid(o,q(r.id));uuid(o,q(r.scope));o.writeByte(r.type);o.writeLong(r.length);o.write(sha(new byte[0]));uuid(o,r.identity);refs(o,r.edges);}
    for(Origin r:origins){uuid(o,q(r.id));o.write(new byte[32]);uuid(o,q(999));refs(o,r.parents);}
    for(Edge e:edges){uuid(o,q(e.id));uuid(o,q(e.entry));uuid(o,q(e.origin));o.writeByte(e.type);uuid(o,e.identity);o.writeInt(e.tags.length);for(int i=0;i<e.tags.length;i++){o.writeByte(e.tags[i]);o.writeInt(e.values[i].length);o.write(e.values[i]);}}
   });
   long sum=0;for(Row r:rows)sum+=r.length;final long payload=sum;
   return checked(pack(o->{o.write("MDBUNDL\n".getBytes("US-ASCII"));o.writeInt(1);o.writeInt(112+tables.length);uuid(o,q(1));uuid(o,q(2));o.writeInt(scopes.size());o.writeInt(rows.size());o.writeInt(origins.size());o.writeInt(edges.size());o.writeLong(payload);o.writeLong(122+112+tables.length+58L*rows.size()+payload);o.write(tables);}));
  }
 }
 static void refs(DataOutputStream o,long[] refs)throws Exception{o.writeInt(refs.length);for(long r:refs)uuid(o,q(r));}
 static Fixture lineage()throws Exception {
  Fixture f=new Fixture();f.scopes.get(0).policy=true;f.origins.add(new Origin(30));
  Row prep=new Row(4,2,2,new UUID(0,7)),complete=new Row(5,2,3,new UUID(0,7));prep.edges=new long[]{40};complete.edges=new long[]{41};f.rows.add(prep);f.rows.add(complete);
  f.edges.add(new Edge(40,4,30,2,new UUID(0,8),"legacy-recovery"));f.edges.add(new Edge(41,5,30,3,new UUID(0,8),"legacy-recovery"));return f;
 }
 static void accepted(Fixture f,String why)throws Exception {
  byte[] bytes=f.encode();PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  try(PrivateBundleInventory inv=PrivateBundleInventory.read(new ByteArrayInputStream(bytes),budget)) {
   ByteArrayOutputStream b=new ByteArrayOutputStream();inv.write(b);ok(Arrays.equals(bytes,b.toByteArray()),why+" exact canonical bytes");
   try{inv.entries().clear();throw new AssertionError("mutable entries");}catch(UnsupportedOperationException expected){checks++;}
   try{inv.scopes().get(0).tabs.add(q(200));throw new AssertionError("mutable refs");}catch(UnsupportedOperationException expected){checks++;}
  }
  ok(budget.usedBytes()==0,why+" release");
 }
 static void rejected(Fixture f,String why)throws Exception {
  byte[] bytes=f.encode();PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(bytes),budget),why);ok(budget.usedBytes()==0,why+" failed rollback");
 }
 static void run()throws Exception {
  Fixture f=lineage();accepted(f,"shared native transaction with inert policy");
  f.edges.get(1).identity=new UUID(0,9);rejected(f,"transaction mapping functional across types");
  f=lineage();f.rows.get(1).identity=new UUID(0,9);rejected(f,"transaction mapping injective across types");
  f=lineage();f.edges.get(1).origin=31;rejected(f,"missing lineage origin");
  f=lineage();f.rows.get(0).edges=new long[]{40,41};rejected(f,"edge belongs exactly one entry");
  f=lineage();f.rows.get(0).edges=new long[]{};rejected(f,"unowned edge");
  f=lineage();f.edges.get(0).entry=5;rejected(f,"wrong owner");
  f=lineage();f.edges.get(0).type=3;rejected(f,"wrong lineage type");
  f=lineage();f.edges.get(0).tags[0]=5;rejected(f,"native coordinate tag");
  f=lineage();f.edges.get(0).values[0]=new byte[]{(byte)0xc0,(byte)0xaf};rejected(f,"malformed UTF8");
  f=lineage();f.edges.get(0).values[0]=new byte[0];rejected(f,"empty coordinate");
  f=lineage();f.edges.get(0).values[0]=new byte[4097];rejected(f,"coordinate byte limit");
  f=lineage();f.edges.get(0).values[0]=repeat("\u00e9",2048).getBytes("UTF-8");accepted(f,"4096-byte multibyte coordinate");
  f=lineage();f.origins.get(0).parents=new long[]{31};f.origins.add(new Origin(31));accepted(f,"reachable parent");
  f.origins.get(1).parents=new long[]{30};rejected(f,"origin cycle");
  f=lineage();f.origins.add(new Origin(31));rejected(f,"unattached origin");
  f.scopes.get(0).origins=new long[]{31};accepted(f,"scope origin reachability");
  f.scopes.get(0).origins=new long[]{31,30};rejected(f,"origin refs canonical order");
  f=lineage();f.rows.add(new Row(6,2,2,new UUID(0,7)));rejected(f,"native scoped duplicate");
  f=lineage();f.rows.get(1).id=4;rejected(f,"outer ID duplicate");
  f=lineage();f.rows.get(1).scope=3;rejected(f,"missing scope");
  f=lineage();Scope retained=new Scope(3);f.scopes.add(retained);f.rows.get(1).scope=3;accepted(f,"repeated native identity across scopes");
  f=lineage();f.scopes.get(0).flags=2;rejected(f,"root flags fixed");
  f=new Fixture();Scope second=new Scope(3);f.scopes.add(second);
  for(int i=0;i<13;i++){long scope=i<12?2:3;Row row=new Row(4+i,scope,6,q(200+i%12));f.rows.add(row);}
  f.scopes.get(0).tabs=new long[]{4,5,6,7,8,9,10,11,12,13,14,15};f.scopes.get(0).active=4;second.tabs=new long[]{16};second.active=16;
  accepted(f,"13 retained drafts distinct across scopes");
  second.flags=1;rejected(f,"presence flag excludes private rows");second.flags=3;
  f.scopes.get(0).active=16;rejected(f,"active tab wrong scope");f.scopes.get(0).active=4;
  f.scopes.get(0).tabs[1]=4;rejected(f,"duplicate semantic tab");
  f=new Fixture();f.rows.add(new Row(4,2,7,q(20)));f.rows.add(new Row(5,2,8,q(20)));rejected(f,"private identity shared domain");
  f.rows.get(1).identity=q(21);accepted(f,"recovery/preferences independent private items");
  f.rows.get(1).type=7;rejected(f,"one recovery per scope");
  f=new Fixture();f.rows.add(new Row(4,2,9,new UUID(0,0)));f.rows.get(0).edges=new long[]{40};f.origins.add(new Origin(30));f.edges.add(new Edge(40,4,30,9,new UUID(0,7),"legacy-recovery","other-document"));accepted(f,"preserved denormalized index coordinates");
  f.edges.get(0).tags[1]=2;rejected(f,"coordinate canonical order");
  f=new Fixture();for(int i=0;i<64;i++){if(i>0)f.scopes.add(new Scope(2+i));}accepted(f,"64 scopes");f.scopes.add(new Scope(66));rejected(f,"scope global cap");
  f=new Fixture();Row oversizedRow=new Row(4,2,1,new UUID(0,7));oversizedRow.length=PrivateArchive.MAX_DOCUMENT_ENTRY_BYTES+1L;f.rows.add(oversizedRow);rejected(f,"document cap plus one");
  oversizedRow.type=8;oversizedRow.identity=q(7);oversizedRow.length=65537;rejected(f,"metadata cap plus one");
  f=new Fixture();for(int i=0;i<128;i++){Row row=new Row(4+i,2,1,new UUID(0,i));row.length=PrivateArchive.MAX_DOCUMENT_ENTRY_BYTES;f.rows.add(row);}rejected(f,"whole archive 1GiB cap");
  f=new Fixture();f.scopes.get(0).origins=new long[]{1255};
  for(int i=0;i<256;i++){Origin origin=new Origin(1000+i);origin.parents=new long[i];for(int j=0;j<i;j++)origin.parents[j]=1000+j;f.origins.add(origin);}
  accepted(f,"dense reachable parent DAG");
  byte[] dense=f.encode();long references=32641;
  // UUID object (32 bytes) + conservative 8-byte array reference, before list/array headers.
  long minimum=4096+4096+1024+384L*256+40*references;
  PrivateBundleInventory.MetadataBudget underfundedRefs=new PrivateBundleInventory.MetadataBudget(minimum-1);
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(dense),underfundedRefs),"all retained reference objects and array slots charged");
  byte[] inv=inventory(false);byte[] oversized=inv.clone();setInt(oversized,52,16381);final byte[] tooMany=oversized;
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(tooMany),budget),"entry count before allocation");
  oversized=inv.clone();setInt(oversized,60,65536);final byte[] retainedTooLarge=oversized;fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(retainedTooLarge),budget),"aggregate metadata before allocation");
  oversized=inv.clone();setInt(oversized,12,0xffffffff);final byte[] unsigned=oversized;fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(unsigned),budget),"unsigned length before allocation");
  ok(budget.usedBytes()==0,"count rejection rollback");
  PrivateBundleInventory closed=PrivateBundleInventory.read(new ByteArrayInputStream(inv),budget);closed.close();closed.close();ok(budget.usedBytes()==0,"idempotent inventory close");
  try{closed.entries();throw new AssertionError("closed rows accessible");}catch(IllegalStateException expected){checks++;}
 }
 static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
 static void setInt(byte[] b,int at,int value){b[at]=(byte)(value>>>24);b[at+1]=(byte)(value>>>16);b[at+2]=(byte)(value>>>8);b[at+3]=(byte)value;}
}
