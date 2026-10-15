package com.example.markdowndesk;
import java.io.*;
import java.security.*;
import java.util.*;
import static com.example.markdowndesk.ScopedArchiveCheck.*;
/** Separate constrained heap process: generated 8 MiB payloads never exist as arrays. */
public final class MemoryCheck {
 static final int LENGTH=PrivateArchive.MAX_DOCUMENT_ENTRY_BYTES;
 static byte[] held;
 static MessageDigest digest()throws Exception{return MessageDigest.getInstance("SHA-256");}
 static byte[] zerosHash(int n)throws Exception {MessageDigest d=digest();byte[] block=new byte[4096];while(n>0){int k=Math.min(n,block.length);d.update(block,0,k);n-=k;}return d.digest();}
 static InputStream zeros(long n){return new InputStream(){long remaining=n;public int read(){if(remaining==0)return -1;remaining--;return 0;}public int read(byte[] b,int off,int len){if(len==0)return 0;if(remaining==0)return -1;int k=(int)Math.min(len,remaining);Arrays.fill(b,off,off+k,(byte)0);remaining-=k;return k;}};}
 static byte[] inventory(byte[] hash)throws Exception {
  return checked(pack(o->{o.write("MDBUNDL\n".getBytes("US-ASCII"));o.writeInt(1);o.writeInt(237);uuid(o,q(1));uuid(o,q(2));o.writeInt(1);o.writeInt(1);o.writeInt(0);o.writeInt(0);o.writeLong(LENGTH);o.writeLong(122L+237+58+LENGTH);uuid(o,q(2));o.writeByte(1);o.writeByte(3);o.writeInt(0);o.writeInt(1);o.writeByte(0);o.writeInt(0);o.writeByte(0);uuid(o,q(4));uuid(o,q(2));o.writeByte(1);o.writeLong(LENGTH);o.write(hash);uuid(o,new UUID(0,7));o.writeInt(0);}));
 }
 public static void main(String[] args)throws Exception {
  held=new byte[36*1024*1024];held[held.length-1]=1;
  byte[] payloadHash=zerosHash(LENGTH),inv=inventory(payloadHash);
  byte[] prefix=pack(o->{o.write("MDESKBACKUP\r\n\032\n".getBytes("US-ASCII"));o.writeInt(2);frame(o,10,q(3),inv);o.writeByte(1);o.writeByte(1);uuid(o,q(4));o.writeLong(LENGTH);o.write(payloadHash);});
  MessageDigest pre=digest();pre.update(prefix);byte[] block=new byte[4096];for(int n=LENGTH;n>0;){int k=Math.min(n,block.length);pre.update(block,0,k);n-=k;}
  byte[] terminal=pack(o->{o.writeByte(0);o.writeInt(2);o.writeLong(prefix.length+(long)LENGTH);o.write(pre.digest());});
  InputStream stream=new InputStream(){int phase;InputStream current=new ByteArrayInputStream(prefix);
   public int read()throws IOException{while(true){int b=current.read();if(b>=0)return b;if(phase==2)return -1;current=++phase==1?zeros(LENGTH):new ByteArrayInputStream(terminal);}}
   public int read(byte[] b,int off,int len)throws IOException{if(len==0)return 0;while(true){int n=current.read(b,off,len);if(n>=0)return n;if(phase==2)return -1;current=++phase==1?zeros(LENGTH):new ByteArrayInputStream(terminal);}}
  };
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  final long[] bytes={0};PrivateArchive.ArchiveSummary summary;
  try(PrivateArchive.PortableResult result=PrivateArchive.readPortable(stream,e->new OutputStream(){public void write(int b){bytes[0]++;}public void write(byte[] b,int off,int n){bytes[0]+=n;}},null,budget)) {summary=result.sourceSummary;ok(bytes[0]==LENGTH,"streamed cap-sized payload");}
  MessageDigest output=digest();PrivateArchive.ArchiveSource source=new PrivateArchive.ArchiveSource(){boolean sent;public PrivateArchive.SourceEntry next(){if(sent)return null;sent=true;return new PrivateArchive.SourceEntry(q(4),PrivateArchive.Type.SNAPSHOT,LENGTH,()->zeros(LENGTH));}};
  PrivateArchive.ArchiveSummary written=PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(inv),source,new OutputStream(){public void write(int b){output.update((byte)b);}public void write(byte[] b,int off,int n){output.update(b,off,n);}},budget);
  ok(summary.sha256.equals(written.sha256)&&written.sha256.equals(hex(output.digest())),"streamed read/write same bytes");
  ok(held[held.length-1]==1&&budget.usedBytes()==0,"held heap and released metadata");
  // Exact retained floor and caller-held metadata participate in the same admission decision.
  long cost;try(PrivateBundleInventory i=PrivateBundleInventory.read(new ByteArrayInputStream(inv),budget)){cost=budget.usedBytes();}
  PrivateBundleInventory.MetadataBudget exact=new PrivateBundleInventory.MetadataBudget(cost);
  try(PrivateBundleInventory i=PrivateBundleInventory.read(new ByteArrayInputStream(inv),exact)){ok(exact.usedBytes()==cost,"exact metadata boundary");}
  PrivateBundleInventory.MetadataBudget shortBudget=new PrivateBundleInventory.MetadataBudget(cost-1);
  fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(inv),shortBudget),"metadata limit minus one");
  try(PrivateBundleInventory.Reservation other=budget.reserve((16L<<20)-cost+1)){fails(()->PrivateBundleInventory.read(new ByteArrayInputStream(inv),budget),"combined held metadata");}
  ok(budget.usedBytes()==0&&shortBudget.usedBytes()==0,"boundary rollback");
  System.out.println("PASS scoped streaming memory checks="+checks+" heldMiB=36 heapMiB=48 payloadBytes="+LENGTH);
 }
}
