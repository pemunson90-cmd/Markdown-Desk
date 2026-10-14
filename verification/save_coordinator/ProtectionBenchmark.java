package com.example.markdowndesk;
import java.nio.file.*;
import java.util.*;

/** Matched host-only fixtures; no device responsiveness claim. */
public final class ProtectionBenchmark {
 public static void main(String[] args)throws Exception {
  System.out.println("fixture,bytes,first_ms,second_ms,third_ms,median_ms,persisted_bytes");
  for(String name:new String[]{"10k.md","100k.md","500k.md","exact-8MiB.md"}) {
   byte[] original=Files.readAllBytes(Path.of(args[0],name));byte[] edited=original.clone();edited[0]=(byte)(original[0]=='a'?'b':'a');
   Path temp=Files.createTempDirectory("protection-benchmark-");
   try {
    ProtectionStore store=new ProtectionStore(temp.resolve("store").toFile(),new ProtectionMemoryProbe.D(),64L*1024*1024);
    long[] samples=new long[3];
    for(int i=0;i<3;i++) {
     SaveCoordinator.Provider provider=new SaveCoordinator.Provider(){byte[] value=original.clone();public byte[] read(){return value.clone();}public void write(byte[] next){value=next.clone();}};
     SaveCoordinator.Request request=new SaveCoordinator.Request("benchmark", "content://synthetic/"+name,name,i,edited,EditorTools.sha256(original),null,false);
     long start=System.nanoTime();new SaveCoordinator(store).save(request,provider);samples[i]=System.nanoTime()-start;
     if(!Arrays.equals(provider.read(),edited))throw new AssertionError("verified save mismatch");
    }
    long[] sorted=samples.clone();Arrays.sort(sorted);
    long size;try(java.util.stream.Stream<Path> paths=Files.list(temp.resolve("store"))){size=paths.mapToLong(p->{try{return Files.size(p);}catch(Exception e){throw new RuntimeException(e);}}).sum();}
    System.out.printf(java.util.Locale.ROOT,"%s,%d,%.3f,%.3f,%.3f,%.3f,%d%n",name,original.length,samples[0]/1e6,samples[1]/1e6,samples[2]/1e6,sorted[1]/1e6,size);
   } finally {try(java.util.stream.Stream<Path> paths=Files.walk(temp)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
  }
 }
}
