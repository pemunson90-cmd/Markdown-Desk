package com.example.markdowndesk;
import java.io.*;import java.nio.file.*;import java.nio.channels.*;import java.util.*;
/** Host constrained-heap regression, not an Android heap budget. */
public class ProtectionMemoryProbe {
 static class D implements ProtectionStore.Durability {
 public void syncFile(FileDescriptor fd)throws IOException{fd.sync();}
 public void move(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);}
 public void syncDirectory(File d)throws IOException{try(FileChannel c=FileChannel.open(d.toPath(),StandardOpenOption.READ)){c.force(true);}}}
 public static void main(String[] args)throws Exception {
 byte[] headroom=new byte[args.length==0?0:Integer.parseInt(args[0])*1024*1024];
 File root=Files.createTempDirectory("memory-probe").resolve("store").toFile();
 ProtectionStore store=new ProtectionStore(root,new D(),64L*1024*1024);
 byte[] input=new byte[8388608];Arrays.fill(input,(byte)'a');
 for(int i=0;i<3;i++){
 final byte[] source=input.clone();
 SaveCoordinator.Provider p=new SaveCoordinator.Provider(){byte[] value=source; public byte[] read(){return value.clone();}public void write(byte[] b){value=b;}};
 SaveCoordinator.Request r=new SaveCoordinator.Request("doc","uri","title",i,input,EditorTools.sha256(source),null,false);
 new SaveCoordinator(store).save(r,p);System.out.println("save "+i+" succeeded");
 }
 System.out.println("transactions="+store.transactions().size());System.out.println("snapshots="+store.snapshots().size());
 for(ProtectionStore.Snapshot snapshot:store.snapshots()){byte[] restored=store.snapshot(snapshot.id).bytes();if(!Arrays.equals(input,restored))throw new AssertionError("restored bytes mismatch");}System.out.println("headroom="+headroom.length);
 }
}
