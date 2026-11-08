package com.example.markdowndesk;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class ControlInspectionBoundsCheck {
 static int checks;
 interface Action {void run()throws Exception;}
 static void check(boolean ok)throws Exception {checks++;if(!ok)throw new AssertionError();}
 static void reject(Action a)throws Exception {try{a.run();}catch(IOException expected){checks++;return;}throw new AssertionError("Expected IOException");}
 static final class Fixture {
  final Path parent=Files.createTempDirectory("control-bounds-").toRealPath(),root=Files.createDirectory(parent.resolve("history_control"));
  final HistoryControlStore store;final byte[] reg;
  Fixture()throws Exception {
   List<HistoryControlStore.Role> roles=new ArrayList<>();int id=0;
   for(String n:new String[]{"protection","catalog","identity","index"}){Files.createDirectory(parent.resolve(n));roles.add(new HistoryControlStore.Role(++id,n));}
   reg=HistoryControlStore.encodeRegistration(UUID.randomUUID(),0,roles,64L*1024*1024,5);store=new HistoryControlStore(parent.toFile());
  }
  void put(String n,byte[] b)throws Exception{Files.write(root.resolve(n),b);}
 }
 public static void main(String[] args)throws Exception {
  Fixture empty=new Fixture();check(empty.store.inspectBounded(0,0).actualEntries==0);
  reject(()->empty.store.inspectBounded(-1,0));reject(()->empty.store.inspectBounded(0,-1));
  reject(()->empty.store.inspectBounded(HistoryControlStore.MAX_BYTES+1,0));reject(()->empty.store.inspectBounded(0,HistoryControlStore.MAX_ENTRIES+1));
  Fixture enroll=new Fixture();Files.write(enroll.parent.resolve(HistoryControlStore.ENROLLMENT_NAME),enroll.reg);
  check(enroll.store.inspectBounded(enroll.reg.length,1).actualBytes==enroll.reg.length);
  reject(()->enroll.store.inspectBounded(enroll.reg.length-1,1));reject(()->enroll.store.inspectBounded(enroll.reg.length,0));
  Files.write(enroll.parent.resolve(HistoryControlStore.ENROLLMENT_STAGE),enroll.reg);
  check(enroll.store.inspectBounded(2L*enroll.reg.length,2).actualEntries==2);
  reject(()->enroll.store.inspectBounded(2L*enroll.reg.length-1,2));reject(()->enroll.store.inspectBounded(2L*enroll.reg.length,1));
  enroll.put(HistoryControlStore.REGISTRATION_NAME,enroll.reg);
  check(enroll.store.inspectBounded(3L*enroll.reg.length,3).actualEntries==3);
  reject(()->enroll.store.inspectBounded(3L*enroll.reg.length-1,3));
  reject(()->enroll.store.inspectBounded(3L*enroll.reg.length,2));
  Fixture invalidEnrollment=new Fixture();Files.createDirectory(invalidEnrollment.parent.resolve(HistoryControlStore.ENROLLMENT_NAME));
  reject(()->invalidEnrollment.store.inspectBounded(HistoryControlStore.MAX_BYTES,1));
  Fixture dir=new Fixture();dir.put(HistoryControlStore.REGISTRATION_NAME,dir.reg);
  check(dir.store.inspectBounded(dir.reg.length,1).state==HistoryControlStore.State.VALIDATED);
  reject(()->dir.store.inspectBounded(dir.reg.length-1,1));reject(()->dir.store.inspectBounded(dir.reg.length,0));
  dir.put("unknown",new byte[7]);dir.put("registration.control.stage",dir.reg);
  long total=2L*dir.reg.length+7;
  HistoryControlStore.Inspection snapshot=dir.store.inspectBounded(total,3);
  check(snapshot.actualBytes==total&&snapshot.actualEntries==3&&snapshot.state==HistoryControlStore.State.BLOCKED);
  reject(()->dir.store.inspectBounded(total-1,3));reject(()->dir.store.inspectBounded(total,2));
  Fixture many=new Fixture();many.put(HistoryControlStore.REGISTRATION_NAME,many.reg);
  for(int i=0;i<255;i++)many.put("unknown-"+i,new byte[0]);
  check(many.store.inspectBounded(many.reg.length,256).actualEntries==256);
  many.put("last-unknown",new byte[0]);
  reject(()->many.store.inspectBounded(HistoryControlStore.MAX_BYTES,HistoryControlStore.MAX_ENTRIES));
  check(many.store.inspect().state==HistoryControlStore.State.BLOCKED);
  Fixture huge=new Fixture();try(RandomAccessFile f=new RandomAccessFile(huge.root.resolve("unknown").toFile(),"rw")){f.setLength(1L<<34);}
  reject(()->huge.store.inspectBounded(HistoryControlStore.MAX_BYTES,1));
  check(huge.store.inspect().actualBytes==(1L<<34));
  Files.move(huge.root.resolve("unknown"),huge.parent.resolve(HistoryControlStore.ENROLLMENT_NAME));
  reject(()->huge.store.inspectBounded(HistoryControlStore.MAX_BYTES,1));
  check(huge.store.inspect().actualBytes==(1L<<34));
  Fixture nonregular=new Fixture();Files.createDirectory(nonregular.root.resolve("unknown"));
  reject(()->nonregular.store.inspectBounded(HistoryControlStore.MAX_BYTES,1));
  check(!nonregular.store.inspect().usageComplete);
  System.out.println("PASS control inspection bounds: "+checks+" assertions");
 }
}
