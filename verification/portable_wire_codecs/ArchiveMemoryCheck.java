package com.example.markdowndesk;
import java.io.*;
import java.security.*;
import java.util.*;
import static com.example.markdowndesk.ScopedArchiveCheck.*;
/** Evidence framing over8MiB with24MiB held in32MiB heap; no whole payload fits. */
public final class ArchiveMemoryCheck {
 static byte[] held;
 static final long LENGTH=(9L<<20)+3;
 static InputStream zeros(){return new InputStream(){long left=LENGTH;
  public int read(){if(left==0)return -1;left--;return 0;}
  public int read(byte[] b,int off,int n){if(n==0)return 0;if(left==0)return -1;int k=(int)Math.min(left,n);Arrays.fill(b,off,off+k,(byte)0);left-=k;return k;}
 };}
 public static void main(String[] args)throws Exception {
  held=new byte[24<<20];held[held.length-1]=1;
  MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[4096];
  for(long left=LENGTH;left>0;left-=Math.min(left,block.length))digest.update(block,0,(int)Math.min(left,block.length));
  byte[] hash=digest.digest(),inv=WireCheck.evidenceInventory(2,LENGTH,11,hash);
  byte[] prefix=pack(o->{o.write("MDESKBACKUP\r\n\032\n".getBytes("US-ASCII"));o.writeInt(3);frame(o,10,q(3),inv);o.writeByte(1);o.writeByte(11);uuid(o,q(4));o.writeLong(LENGTH);o.write(hash);});
  digest.reset();digest.update(prefix);for(long left=LENGTH;left>0;left-=Math.min(left,block.length))digest.update(block,0,(int)Math.min(left,block.length));byte[] prehash=digest.digest();
  byte[] terminal=pack(o->{o.writeByte(0);o.writeInt(2);o.writeLong(prefix.length+LENGTH);o.write(prehash);});
  InputStream wire=new SequenceInputStream(Collections.enumeration(Arrays.asList(new ByteArrayInputStream(prefix),zeros(),new ByteArrayInputStream(terminal))));
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();final long[] copied={0};String complete;
  try(PrivateArchive.PortableResult result=PrivateArchive.readPortable(wire,e->new OutputStream(){public void write(int b){copied[0]++;}public void write(byte[] b,int off,int n){copied[0]+=n;}},null,budget)) {
   ok(copied[0]==LENGTH&&result.inventory.schema==2,"large type11 streamed");complete=result.sourceSummary.sha256;
  }
  MessageDigest output=MessageDigest.getInstance("SHA-256");
  PrivateArchive.ArchiveSource source=new PrivateArchive.ArchiveSource(){boolean done;public PrivateArchive.SourceEntry next(){if(done)return null;done=true;return new PrivateArchive.SourceEntry(q(4),PrivateArchive.Type.OPERATION_EVIDENCE,LENGTH,ArchiveMemoryCheck::zeros);}};
  PrivateArchive.ArchiveSummary result=PrivateArchive.writeV3(q(3),()->new ByteArrayInputStream(inv),source,new OutputStream(){public void write(int b){output.update((byte)b);}public void write(byte[] b,int off,int n){output.update(b,off,n);}},budget);
  ok(result.sha256.equals(complete)&&result.sha256.equals(hex(output.digest())),"large type11 independent framing/writer match");
  ok(held[held.length-1]==1&&budget.usedBytes()==0,"held heap and logical settlement");
  System.out.println("PASS type11 archive memory checks="+checks+" payloadBytes="+LENGTH+" heldMiB=24 heapMiB=32");
 }
}
