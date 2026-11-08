package com.example.markdowndesk;
import java.io.*;
import java.util.*;
import static com.example.markdowndesk.ScopedArchiveCheck.*;
final class FramingCheck {
 static final class Input extends ByteArrayInputStream {
  int closes; boolean zero;
  Input(byte[] b){super(b);} public void close(){closes++;}
  public synchronized int read(byte[] b,int off,int len){return zero&&len>0?0:super.read(b,off,len);}
 }
 static class Output extends ByteArrayOutputStream {int closes,flushes;public void close(){closes++;}public void flush(){flushes++;}}
 static class Sink implements PrivateArchive.ScopedSink {
  int begins,finishes,verified; boolean writeFail,finishFail,beginFail,nullOutput;
  public OutputStream begin(PrivateBundleInventory.DirectoryEntry e)throws IOException {
   if(beginFail)throw new IOException("begin");begins++;if(nullOutput)return null;
   return new OutputStream(){public void write(int b)throws IOException{if(writeFail)throw new IOException("write");}};
  }
  public void finish(PrivateBundleInventory.DirectoryEntry e,OutputStream out,boolean good)throws IOException {finishes++;if(good)verified++;if(finishFail)throw new IOException("finish");}
 }
 static class Spool implements PrivateArchive.LegacySpool {
  int begins,finishes; final PrivateBundleInventory.MetadataBudget budget; boolean fail;
  Spool(PrivateBundleInventory.MetadataBudget b){budget=b;}
  public OutputStream begin(PrivateArchive.Entry e)throws IOException {
   ok(budget.usedBytes()>0&&budget.usedBytes()<16L<<20,"incremental legacy metadata reservation");begins++;
   return new OutputStream(){public void write(int v)throws IOException{if(fail)throw new IOException("legacy write");}};
  }
  public void finish(PrivateArchive.Entry e,OutputStream output,boolean good){finishes++;}
 }
 static PrivateArchive.ArchiveSource source(boolean mixed,boolean changed,int[] closes)throws Exception {
  byte[][] payloads=mixed?new byte[][]{snapshot(),prefs()}:new byte[0][];
  return new PrivateArchive.ArchiveSource(){int at;
   public PrivateArchive.SourceEntry next(){if(at==payloads.length)return null;int index=at++;
    return new PrivateArchive.SourceEntry(q(4+index),index==0?PrivateArchive.Type.SNAPSHOT:PrivateArchive.Type.PREFERENCES,payloads[index].length,new PrivateArchive.StreamOpener(){int calls;
     public InputStream open(){byte[] data=payloads[index].clone();if(changed&&calls++>0)data[0]^=1;
      return new ByteArrayInputStream(data){public void close(){closes[0]++;}};}
    });}
  };
 }
 static void run()throws Exception {
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  for(boolean mixed:new boolean[]{false,true}) {
   byte[] inv=inventory(mixed),wire=archive(inv,mixed);Input input=new Input(wire);input.zero=true;Sink sink=new Sink();
   try(PrivateArchive.PortableResult r=PrivateArchive.readPortable(input,sink,null,budget)) {
    ok(r.sourceSummary.byteCount==wire.length&&r.sourceSummary.sha256.equals(hex(sha(wire))),"v2 source summary");
    ok(r.inventory!=null&&r.provisionalLegacySpool==null,"v2 tagged result");
    ok(sink.begins==(mixed?2:0)&&sink.finishes==sink.begins&&sink.verified==sink.begins,"successful scoped lifecycle");
   }
   ok(input.closes==0&&budget.usedBytes()==0,"v2 input ownership/budget");
   Output output=new Output();int[] closes={0},invCloses={0};
   PrivateArchive.ArchiveSummary written=PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(inv){public void close(){invCloses[0]++;}},source(mixed,false,closes),output,budget);
   ok(Arrays.equals(output.toByteArray(),wire)&&written.sha256.equals(hex(sha(wire))),"v2 golden output");
   ok(output.closes==0&&output.flushes==0&&invCloses[0]==2&&closes[0]==(mixed?4:0),"write stream ownership");
   for(int cut=0;cut<wire.length;cut++) {
    Input truncated=new Input(Arrays.copyOf(wire,cut));Sink partial=new Sink();
    fails(()->PrivateArchive.readPortable(truncated,partial,null,budget),"archive truncation");
    ok(truncated.closes==0&&budget.usedBytes()==0&&partial.begins==partial.finishes,"truncated lifecycle");
   }
  }
  byte[] inv=inventory(true),wire=archive(inv,true);
  // Inner-valid inventory with wrong outer hash is rejected before ordinary callbacks.
  byte[] bad=wire.clone();bad[45]^=1;zeroCallbacks(bad,budget,"inventory outer hash");
  bad=inventory(true);bad[143]=99;bad=recheck(bad);zeroCallbacks(archive(bad,true),budget,"inventory crossref");
  bad=wire.clone();bad[408]=10;zeroCallbacks(bad,budget,"second inventory");
  bad=wire.clone();bad[464]^=1;zeroCallbacks(bad,budget,"ordinary descriptor mismatch");
  bad=wire.clone();bad[465]^=1;Sink corrupt=new Sink();final byte[] corruptWire=bad;
  fails(()->PrivateArchive.readPortable(new Input(corruptWire),corrupt,null,budget),"ordinary hash mismatch");
  ok(corrupt.begins==1&&corrupt.finishes==1&&corrupt.verified==0,"bad hash finish false");
  Sink fail=new Sink();fail.writeFail=true;fail.finishFail=true;
  try{PrivateArchive.readPortable(new Input(wire),fail,null,budget);throw new AssertionError("write must fail");}
  catch(IOException expected){ok(expected.getMessage().equals("write")&&expected.getSuppressed().length==1&&expected.getSuppressed()[0].getMessage().equals("finish"),"primary error suppression");}
  ok(fail.begins==1&&fail.finishes==1,"finish exactly once");
  for(int which=0;which<3;which++) {
   Sink s=new Sink();s.beginFail=which==0;s.nullOutput=which==1;s.finishFail=which==2;
   fails(()->PrivateArchive.readPortable(new Input(wire),s,null,budget),"begin/null/finish rejection");
   ok(s.finishes==(which==2?1:0)&&budget.usedBytes()==0,"successful nonnull begin contract");
  }
  byte[] trailing=Arrays.copyOf(wire,wire.length+1);Sink provisional=new Sink();
  fails(()->PrivateArchive.readPortable(new Input(trailing),provisional,null,budget),"trailing byte");
  ok(provisional.verified==2&&budget.usedBytes()==0,"callbacks stay provisional");
  int[] closes={0};Output incomplete=new Output();
  fails(()->PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(inv),source(true,true,closes),incomplete,budget),"changed second source pass");
  ok(closes[0]==2&&incomplete.closes==0&&budget.usedBytes()==0,"changed source resources");
  int[] calls={0};fails(()->PrivateArchive.writeV2(q(3),()->{byte[] x=inv.clone();if(calls[0]++>0)x[0]^=1;return new ByteArrayInputStream(x);},source(true,false,new int[1]),new Output(),budget),"changed frozen inventory");
  fails(()->PrivateArchive.writeV2(q(4),()->new ByteArrayInputStream(inv),source(true,false,new int[1]),new Output(),budget),"inventory ID collision");
  Output failedOutput=new Output(){public synchronized void write(byte[] b,int off,int n){throw new IllegalStateException("destination");}};
  try{PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(inv),source(true,false,new int[1]),failedOutput,budget);throw new AssertionError("output must fail");}
  catch(IllegalStateException expected){ok(budget.usedBytes()==0&&failedOutput.closes==0,"destination runtime failure rollback/ownership");}
  fails(()->PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(inv),source(true,false,new int[1]),new OutputStream(){public void write(int b)throws IOException{throw new IOException("destination write");}},budget),"destination write IOException");
  ok(budget.usedBytes()==0,"destination IO rollback");
  byte[] terminalBad=wire.clone();terminalBad[802]^=1;Sink terminalSink=new Sink();
  fails(()->PrivateArchive.readPortable(new Input(terminalBad),terminalSink,null,budget),"terminal count mismatch");
  ok(terminalSink.verified==2&&budget.usedBytes()==0,"terminal failure keeps callbacks provisional");
  legacy();
  fails(()->PrivateArchive.read(new Input(wire),new Spool(budget)),"v1 rejects v2");
  byte[] v1Type10=archive(inventory(false),false);v1Type10[18]=1;
  fails(()->PrivateArchive.read(new Input(v1Type10),new Spool(budget)),"v1 rejects type10");
  byte[] unknown=wire.clone();unknown[18]=3;zeroCallbacks(unknown,budget,"unknown dispatch");
  // A source's first close can fail after parsing. The retained metadata must still be released.
  fails(()->PrivateArchive.writeV2(q(3),()->new ByteArrayInputStream(inv){public void close()throws IOException{throw new IOException("close");}},source(true,false,new int[1]),new Output(),budget),"inventory close failure");
  ok(budget.usedBytes()==0,"inventory first-close failure releases retained metadata");
 }
 static void legacy()throws Exception {
  PrivateBundleInventory.MetadataBudget budget=new PrivateBundleInventory.MetadataBudget();
  // Existing v1 remains the actual compatibility oracle; portable path delegates unchanged.
  Output v1=new Output();PrivateArchive.ArchiveSummary original=PrivateArchive.write(source(true,false,new int[1]),v1);
  Spool spool=new Spool(budget);Sink ordinary=new Sink();Input legacy=new Input(v1.toByteArray());legacy.zero=true;
  try(PrivateArchive.PortableResult result=PrivateArchive.readPortable(legacy,ordinary,spool,budget)) {
   ok(result.inventory==null&&result.provisionalLegacySpool==spool&&result.sourceSummary.sha256.equals(original.sha256),"legacy original summary/provisional spool");
  }
  ok(ordinary.begins==0&&spool.begins==2&&spool.finishes==2&&legacy.closes==0&&budget.usedBytes()==0,"legacy ownership");
  PrivateBundleInventory.MetadataBudget shared=new PrivateBundleInventory.MetadataBudget(1L<<20);
  Spool concurrent=new Spool(shared);
  try(PrivateBundleInventory.Reservation held=shared.reserve(1024);
      PrivateArchive.PortableResult result=PrivateArchive.readPortable(new Input(v1.toByteArray()),ordinary,concurrent,shared)) {
   ok(result.sourceSummary.sha256.equals(original.sha256)&&concurrent.begins==2,"small legacy shares coordinator budget");
   ok(shared.usedBytes()==1024,"reader settles while coordinator remains charged");
  }
  try(PrivateBundleInventory.Reservation held=shared.reserve((1L<<20)-65536)) {
   fails(()->PrivateArchive.readPortable(new Input(v1.toByteArray()),ordinary,concurrent,shared),"actual simultaneous metadata exhaustion");
   ok(concurrent.begins==2&&shared.usedBytes()==(1L<<20)-65536,"exhaustion refuses before spool");
  }
  Spool broken=new Spool(budget);broken.fail=true;Input brokenInput=new Input(v1.toByteArray());
  fails(()->PrivateArchive.readPortable(brokenInput,ordinary,broken,budget),"legacy spool failure");
  ok(broken.begins==1&&broken.finishes==1&&brokenInput.closes==0&&budget.usedBytes()==0,"failed legacy settlement");
  for(int cut=0;cut<v1.size();cut++){Input shortInput=new Input(Arrays.copyOf(v1.toByteArray(),cut));Spool s=new Spool(budget);fails(()->PrivateArchive.readPortable(shortInput,ordinary,s,budget),"legacy truncated");ok(shortInput.closes==0&&s.begins==s.finishes&&budget.usedBytes()==0,"legacy truncation ownership");}
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=1||!args[0].equals("legacy"))throw new IllegalArgumentException("Expected legacy selector");
  legacy();System.out.println("PASS affected legacy framing checks="+checks);
 }
 static void zeroCallbacks(byte[] wire,PrivateBundleInventory.MetadataBudget budget,String why)throws Exception {
  Input input=new Input(wire);Sink sink=new Sink();fails(()->PrivateArchive.readPortable(input,sink,null,budget),why);ok(sink.begins==0&&input.closes==0&&budget.usedBytes()==0,why+" preflight/ownership");
 }
}
