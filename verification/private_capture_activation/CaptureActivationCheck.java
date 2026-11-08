package com.example.markdowndesk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;

/** Same-process authentic activation and owned actual source FD lifetime. */
public final class CaptureActivationCheck {
    static int cases;
    interface Work{void run()throws Exception;}
    static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Work task,String why)throws Exception{try{task.run();throw new AssertionError("accepted "+why);}catch(IOException expected){}}
    static final class Capture {
        final PrivateArtifactCopyCheck.Capture c=new PrivateArtifactCopyCheck.Capture(false);
        final HistoryPins pins=HistoryPins.open(c.f.store,c.f.catalog,c.f.identity,c.f.index,c.f.disk);
        final HistoryOperations ops=pins.operations();final PrivateCaptureWriter.CapturedBinding binding;
        Capture()throws Exception{c.copyPreferences(new ByteArrayInputStream(PrivateArtifactCopyCheck.prefs(8)));binding=c.writer.commitCapture(pins);}
        HistoryOperations.OperationHandle activate()throws IOException{return ops.activateCaptured(binding,c.budget);}
    }
    static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[1024];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}
    static void normal()throws Exception {
        Capture c=new Capture();reject(()->c.ops.activateCaptured(c.binding,new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET)),"foreign metadata allowance");
        c.c.f.store.putSnapshot("later","uri","ordinary later save",2,new byte[]{9},"original");
        HistoryOperations.OperationHandle h=c.activate();reject(c::activate,"duplicate OPEN activation");
        boolean[] called={false};reject(()->c.ops.openReader(h,()->{called[0]=true;return new ByteArrayInputStream(new byte[0]);}),"generic captured reader factory");
        reject(()->c.ops.openWriter(h,()->{called[0]=true;return new ByteArrayOutputStream();}),"generic captured writer factory");yes(!called[0],"undeclared factories never called");
        reject(()->c.ops.openCapturedSource(h,UUID.randomUUID()),"undeclared item");
        UUID nativeItem=c.c.plan.nativeMap.entries.get(0).archiveId;
        try(HistoryOperations.TrackedInput in=c.ops.openCapturedSource(h,nativeItem)){yes(read(in).length==c.c.plan.nativeMap.entries.get(0).envelopeBytes,"exact native envelope streamed");}
        HistoryOperations.TrackedInput privateInput=c.ops.openCapturedSource(h,c.c.frames.get(0).itemId);
        yes(Arrays.equals(read(privateInput),PrivateArtifactCopyCheck.prefs(8)),"exact private frame streamed");
        CompletionStage<HistoryOperations.Quiescence> waiting=c.ops.quiesce(c.ops.freeze(h));
        yes(!waiting.toCompletableFuture().isDone(),"live actual Reader blocks drain");reject(()->privateInput.read(),"freeze blocks IO");privateInput.close();
        HistoryOperations.Quiescence proof=waiting.toCompletableFuture().getNow(null);yes(proof!=null,"actual close releases drain");
        try(InputStream manifest=c.ops.capturedManifest(proof)){c.pins.close(proof,HistoryPinRecord.Outcome.RELEASED,manifest);}
        reject(c::activate,"closed capture activation");reject(()->c.ops.openCapturedSource(h,nativeItem),"stale handle after terminal close");cases++;
    }
    static void mutations()throws Exception {
        Capture missing=new Capture();Files.delete(missing.c.f.parent.resolve("history_control/"+missing.c.plan.link.name()));reject(missing::activate,"missing LINK");cases++;
        Capture changed=new Capture();Path source=changed.c.target(0);byte[] bytes=Files.readAllBytes(source);bytes[0]^=1;Files.write(source,bytes);reject(changed::activate,"changed declared private envelope");cases++;
        Capture after=new Capture();HistoryOperations.OperationHandle handle=after.activate();Path path=after.c.target(0);byte[] original=Files.readAllBytes(path);
        HistoryOperations.TrackedInput reader=after.ops.openCapturedSource(handle,after.c.frames.get(0).itemId);yes(!reader.markSupported(),"no unchecked reset");reject(reader::reset,"reset unsupported");
        java.nio.file.attribute.FileTime modified=Files.getLastModifiedTime(path);bytes=original.clone();bytes[bytes.length-1]^=1;Files.write(path,bytes);Files.setLastModifiedTime(path,modified);reject(()->read(reader),"changed bytes during tracked reading");reader.close();
        HistoryOperations.Quiescence proof=after.ops.quiesce(after.ops.freeze(handle)).toCompletableFuture().getNow(null);yes(proof!=null,"failed read still needs and permits actual close");cases++;
    }
    static void skipAndCloseRetry()throws Exception {
        Capture c=new Capture();HistoryOperations.OperationHandle h=c.activate();UUID item=c.c.frames.get(0).itemId;
        try(HistoryOperations.TrackedInput skipped=c.ops.openCapturedSource(h,item)){
            yes(skipped.skip(Long.MAX_VALUE)==c.c.frames.get(0).envelopeBytes&&skipped.read()==-1,"skip consumes exact hashed bytes through EOF");
        }
        HistoryOperations.TrackedInput reader=c.ops.openCapturedSource(h,item);
        java.lang.reflect.Field outer=reader.getClass().getDeclaredField("delegate");outer.setAccessible(true);Object checked=outer.get(reader);
        java.lang.reflect.Field field=checked.getClass().getDeclaredField("input");field.setAccessible(true);InputStream actual=(InputStream)field.get(checked);
        int[] calls={0};field.set(checked,new FilterInputStream(actual){public void close()throws IOException{calls[0]++;super.close();if(calls[0]==1)throw new IOException("actual close threw after closing original FD");}});
        CompletionStage<HistoryOperations.Quiescence> waiting=c.ops.quiesce(c.ops.freeze(h));
        reject(reader::close,"throwing source close");yes(!waiting.toCompletableFuture().isDone()&&calls[0]==1,"throwing close retains original resource ownership");
        reader.close();yes(calls[0]==2&&waiting.toCompletableFuture().getNow(null)!=null,"retry same wrapper performs real close and releases drain");
        HistoryOperations.Quiescence proof=waiting.toCompletableFuture().getNow(null);
        try(InputStream manifest=c.ops.capturedManifest(proof)){c.pins.close(proof,HistoryPinRecord.Outcome.CANCELLED,manifest);}cases++;
    }
    static void terminalRetention()throws Exception {
        Capture c=new Capture();HistoryOperations.OperationHandle h=c.activate();
        java.lang.reflect.Field handleGate=h.getClass().getDeclaredField("gate");handleGate.setAccessible(true);Object gate=handleGate.get(h);
        java.lang.reflect.Field capture=gate.getClass().getDeclaredField("capture");capture.setAccessible(true);
        HistoryOperations.Quiescence proof=c.ops.quiesce(c.ops.freeze(h)).toCompletableFuture().getNow(null);
        boolean[] fired={false};c.c.f.disk.hook=(event,file)->{if(!fired[0]&&event.equals("file-after")&&Files.exists(c.c.f.parent.resolve("history_control/pin-"+c.binding.operation+".close.stage"))){fired[0]=true;throw new IOException("terminal close sync failure");}};
        try(InputStream manifest=c.ops.capturedManifest(proof)){reject(()->c.pins.close(proof,HistoryPinRecord.Outcome.CANCELLED,manifest),"terminal publication failure");}
        yes(fired[0]&&capture.get(gate)!=null,"TERMINAL_RETRY preserves source table and budget");c.c.f.disk.hook=null;
        try(InputStream manifest=c.ops.capturedManifest(proof)){c.pins.recoverClose(proof,HistoryPins.RecoveryChoice.RECOVER_BOUND,manifest);}
        yes(capture.get(gate)==null,"successful TERMINAL drops historical source table and budget reference");
        HistoryArchiveManifest.Entry source=c.c.plan.nativeMap.entries.get(0);Files.delete(c.c.f.parent.resolve(c.c.plan.nativeMap.registeredRoles.get(source.role)).resolve(source.nativeId+"."+source.kind.nativeName));
        try(InputStream manifest=c.ops.capturedManifest(proof)){yes(Arrays.equals(read(manifest),c.c.plan.descriptor.encoded()),"terminal bounded descriptor survives independently of source files");}
        try(InputStream manifest=c.ops.capturedManifest(proof)){c.pins.close(proof,HistoryPinRecord.Outcome.CANCELLED,manifest);}
        reject(()->c.ops.openCapturedSource(h,source.archiveId),"terminal source access remains refused");cases++;
    }
    public static void main(String[] args)throws Exception{normal();mutations();skipAndCloseRetry();terminalRetention();System.out.println("Actual same-process capture activation: "+cases+" cases PASS; cold adoption deferred");}
}
