package com.example.markdowndesk;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Single-process operation lifetime. No consumer, scheduler or provider is installed here. */
final class HistoryOperations {
    private enum Phase {COLD_FROZEN,OPEN,FREEZING,QUIESCENT,TERMINATING,TERMINAL_RETRY,TERMINAL}
    private static final ThreadLocal<Integer> PARTICIPANT=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> COLD_CANCELLATION_PARTICIPANT=new ThreadLocal<>();
    private final File root;private final Object history;private final Object gateLock=new Object();private final HistoryPins probe;
    private final Map<String,Gate> gates=new HashMap<>();
    private HistoryOperations(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index)throws IOException {
        root=store.storageRoot();history=HistoryStorage.monitorFor(root);probe=HistoryPins.operationProbe(store,catalog,identity,index);
    }
    static HistoryOperations forBundle(ProtectionStore store,HistoryCatalog catalog,HistoryIdentity identity,HistoryIndex index)throws IOException {
        Object history=HistoryStorage.monitorFor(store.storageRoot());synchronized(history){
            HistoryPins probe=HistoryPins.operationProbe(store,catalog,identity,index);probe.operationControlLocked();
            HistoryOperations result=HistoryStorage.operations(store.storageRoot());
            if(result==null){result=new HistoryOperations(store,catalog,identity,index);HistoryStorage.operations(store.storageRoot(),result);}return result;
        }
    }
    interface ReaderFactory {InputStream open()throws IOException;}
    interface WriterFactory {OutputStream open()throws IOException;}
    interface CallbackQueue {void post(Runnable task);}
    private static final class Gate {
        final HistoryPinRecord begin;boolean coldCancellation;PrivateOperationStore.CapturedData capture;byte[] captureDescriptor;long epoch;Phase phase=Phase.COLD_FROZEN;int resources,running,posting;
        final Set<CallbackTicket> pending=new HashSet<>();final List<CompletableFuture<Quiescence>> waiters=new ArrayList<>();
        Gate(HistoryPinRecord begin){this.begin=begin;}
    }
    static final class OperationHandle {
        private final HistoryOperations owner;private final Gate gate;private final long epoch;
        private OperationHandle(HistoryOperations owner,Gate gate){this.owner=owner;this.gate=gate;epoch=gate.epoch;}
    }
    static final class DrainRequest {
        private final HistoryOperations owner;private final Gate gate;private final long epoch;
        private DrainRequest(HistoryOperations owner,Gate gate){this.owner=owner;this.gate=gate;epoch=gate.epoch;}
    }
    static final class Quiescence {
        private final HistoryOperations owner;private final Gate gate;private final long epoch;
        private Quiescence(HistoryOperations owner,Gate gate){this.owner=owner;this.gate=gate;epoch=gate.epoch;}
        String operation(){return gate.begin.operation.toString();}
    }
    static final class AuditQuiescence {
        private final HistoryOperations owner;private final List<Quiescence> proofs;
        private AuditQuiescence(HistoryOperations owner,List<Quiescence> proofs){this.owner=owner;this.proofs=proofs;}
    }
    static final class CallbackTicket {
        private final HistoryOperations owner;private final Gate gate;private final long epoch;private Runnable callback;private boolean running,done;
        private CallbackTicket(HistoryOperations owner,Gate gate,Runnable callback){this.owner=owner;this.gate=gate;epoch=gate.epoch;this.callback=callback;}
    }
    private Gate bind(HistoryPinRecord begin)throws IOException {
        Gate gate=gates.get(begin.operation.toString());
        if(gate==null){gate=new Gate(begin);gates.put(begin.operation.toString(),gate);}
        if(!gate.begin.envelopeHash.equals(begin.envelopeHash)||!gate.begin.registrationHash.equals(begin.registrationHash)||!gate.begin.bundle.equals(begin.bundle))throw new IOException("Operation binding changed");return gate;
    }
    OperationHandle activateAcknowledged(String operation,InputStream manifest)throws IOException {
        outsideHistory();rejectParticipant();synchronized(history){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){
            HistoryPinRecord before=probe.operationBeginLocked(operation,false);Gate gate;long epoch;
            synchronized(gateLock){gate=bind(before);activation(gate);epoch=gate.epoch;}
            probe.operationResumeLocked(operation,manifest);
            synchronized(gateLock){if(gate.epoch!=epoch)throw new IOException("Operation epoch changed");activation(gate);if(gate.epoch==Long.MAX_VALUE)throw new IOException("Operation epoch exhausted");gate.epoch++;gate.phase=Phase.OPEN;return new OperationHandle(this,gate);}
        }}
    }
    OperationHandle activateCaptured(PrivateCaptureWriter.CapturedBinding captured,HistoryStorage.Budget budget)throws IOException {
        outsideHistory();rejectParticipant();if(captured==null)throw new IOException("Missing authentic captured binding");captured.requireBudget(budget);
        synchronized(history){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){
            if(!root.equals(captured.root))throw new IOException("Foreign captured bundle");
            synchronized(gateLock){Gate old=gates.get(captured.operation.toString());if(old!=null)activation(old);}
            PrivateOperationStore.CapturedData data=probe.operationCapturedLocked(captured,budget);
            synchronized(gateLock){Gate gate=bind(data.begin);activation(gate);if(gate.epoch==Long.MAX_VALUE)throw new IOException("Operation epoch exhausted");
                gate.capture=data;gate.captureDescriptor=data.descriptor;gate.epoch++;gate.phase=Phase.OPEN;return new OperationHandle(this,gate);}
        }}
    }
    TrackedInput openCapturedSource(OperationHandle operation,UUID item)throws IOException {
        outsideHistory();Gate gate;PrivateOperationStore.CapturedSource source;
        synchronized(gateLock){gate=handle(operation,true);if(gate.capture==null||item==null||(source=gate.capture.sources.get(item))==null)throw new IOException("Undeclared captured source");
            gate.capture.budget.charge(16384); // resource/digest/stream wrappers plus bounded skip/transfer scratch
            gate.resources++;
        }
        enter();
        try{
            CapturedInput input=new CapturedInput(gate,source);
            TrackedInput tracked=new TrackedInput(new Resource(gate),input);
            input.open();return tracked; // all wrappers retained before actual FD acquisition
        }
        catch(IOException|RuntimeException|Error failure){uncertain(gate);throw failure;}finally{leave();}
    }
    /** In-memory bounded descriptor only, for existing exact-manifest CLOSE after drain. */
    InputStream capturedManifest(Quiescence proof)throws IOException {
        outsideHistory();rejectParticipant();synchronized(gateLock){
            if(proof==null||proof.owner!=this||proof.epoch!=proof.gate.epoch||proof.gate.captureDescriptor==null
                    ||(proof.gate.phase!=Phase.QUIESCENT&&proof.gate.phase!=Phase.TERMINAL_RETRY&&proof.gate.phase!=Phase.TERMINAL)||proof.gate.resources!=0||proof.gate.running!=0||proof.gate.posting!=0||!proof.gate.pending.isEmpty())throw new IOException("Capture manifest requires exact drain proof");
            return new ByteArrayInputStream(proof.gate.captureDescriptor);
        }
    }
    /** Immutable local source; all skip/transfer routes consume checked reads, reset unsupported. */
    private final class CapturedInput extends InputStream {
        private final Gate gate;private final PrivateOperationStore.CapturedSource source;private InputStream input;
        private final MessageDigest digest=HistoryStorage.digest();private final byte[] single=new byte[1];
        private long count;private boolean eof,bad;
        CapturedInput(Gate gate,PrivateOperationStore.CapturedSource source)throws IOException {
            this.gate=gate;this.source=source;
        }
        void open()throws IOException {source.verify();input=Files.newInputStream(source.path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);}
        public int read()throws IOException{int n=read(single,0,1);return n<0?-1:single[0]&255;}
        public int read(byte[] bytes,int offset,int length)throws IOException {
            if(bytes==null)throw new NullPointerException();if(offset<0||length<0||offset>bytes.length-length)throw new IndexOutOfBoundsException();if(length==0)return 0;
            try{
                if(bad)throw new IOException("Captured source validation already failed");source.verify();if(eof)return -1;
                int n=input.read(bytes,offset,Math.min(length,65536));
                if(n<0){if(count!=source.length||!source.hash.equals(HistoryStorage.hex(digest.digest())))throw new IOException("Captured source EOF/hash mismatch");source.verify();eof=true;return -1;}
                if(n==0)throw new IOException("Captured source read stalled");count=PrivateOperationStore.add(count,n);
                if(count>source.length)throw new IOException("Captured source grew");digest.update(bytes,offset,n);return n;
            }catch(IOException|RuntimeException|Error failure){bad=true;uncertain(gate);throw failure;}
        }
        public void close()throws IOException{input.close();}
    }
    private static void activation(Gate gate)throws IOException {if(gate.phase!=Phase.COLD_FROZEN&&gate.phase!=Phase.QUIESCENT)throw new IOException("Operation cannot activate");}
    DrainRequest freezeForTerminal(String operation)throws IOException {
        outsideHistory();rejectFreezeParticipant();List<Runnable> signals=new ArrayList<>();DrainRequest request;
        synchronized(history){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){
            HistoryPinRecord begin=probe.operationBeginLocked(operation,true);
            synchronized(gateLock){Gate gate=bind(begin);if(gate.phase!=Phase.TERMINAL)freezeGate(gate);request=new DrainRequest(this,gate);collect(gate,signals);}
        }}signal(signals);return request;
    }
    DrainRequest freeze(OperationHandle handle)throws IOException {
        outsideHistory();rejectFreezeParticipant();List<Runnable> signals=new ArrayList<>();DrainRequest request;
        synchronized(gateLock){Gate gate=handle(handle,false);freezeGate(gate);request=new DrainRequest(this,gate);collect(gate,signals);}signal(signals);return request;
    }
    private void freezeGate(Gate gate)throws IOException {
        if(gate.phase==Phase.TERMINATING||gate.phase==Phase.TERMINAL)throw new IOException("Terminal operation cannot freeze again");
        // A failed terminal attempt never regains activation authority.
        if(gate.phase!=Phase.TERMINAL_RETRY)gate.phase=Phase.FREEZING;
        for(CallbackTicket ticket:gate.pending){ticket.done=true;ticket.callback=null;}gate.pending.clear();
    }
    CompletionStage<Quiescence> quiesce(DrainRequest request)throws IOException {
        outsideHistory();rejectFreezeParticipant();List<Runnable> signals=new ArrayList<>();CompletableFuture<Quiescence> future=new CompletableFuture<>();
        synchronized(gateLock){if(request==null||request.owner!=this||request.epoch!=request.gate.epoch)throw new IOException("Foreign drain request");Gate gate=request.gate;
            if(gate.phase!=Phase.FREEZING&&gate.phase!=Phase.QUIESCENT&&gate.phase!=Phase.TERMINAL_RETRY&&gate.phase!=Phase.TERMINAL)throw new IOException("Operation is not frozen");gate.waiters.add(future);collect(gate,signals);}
        signal(signals);return future.thenApply(value->value);
    }
    private void collect(Gate gate,List<Runnable> signals){
        if(gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())return;
        if(gate.phase!=Phase.FREEZING&&gate.phase!=Phase.QUIESCENT&&gate.phase!=Phase.TERMINAL_RETRY&&gate.phase!=Phase.TERMINAL)return;
        if(gate.phase!=Phase.TERMINAL_RETRY&&gate.phase!=Phase.TERMINAL)gate.phase=Phase.QUIESCENT;
        Quiescence proof=new Quiescence(this,gate);for(CompletableFuture<Quiescence> future:gate.waiters)signals.add(()->future.complete(proof));gate.waiters.clear();
    }
    private static void signal(List<Runnable> signals){for(Runnable signal:signals)signal.run();}
    private Gate handle(OperationHandle handle,boolean open)throws IOException {
        if(handle==null||handle.owner!=this||handle.epoch!=handle.gate.epoch)throw new IOException("Foreign operation handle");
        if(open&&handle.gate.phase!=Phase.OPEN)throw new IOException("Operation admission frozen");return handle.gate;
    }
    private void outsideHistory()throws IOException {if(Thread.holdsLock(history))throw new IOException("Operation work/signaling under history monitor");}
    /** Consumers may request a drain while their counted work still prevents quiescence. */
    private static void rejectFreezeParticipant()throws IOException {
        PreparationSources.rejectParticipant();
        if(COLD_CANCELLATION_PARTICIPANT.get()!=null)throw new IOException("Cold cancellation participant cannot request quiescence");
    }
    static void rejectParticipant()throws IOException {if(PARTICIPANT.get()!=null)throw new IOException("Terminal entry from operation participant");PreparationSources.rejectParticipant();}
    private static void enter(){Integer n=PARTICIPANT.get();PARTICIPANT.set(n==null?1:n+1);}
    private static void leave(){int n=PARTICIPANT.get();if(n==1)PARTICIPANT.remove();else PARTICIPANT.set(n-1);}
    private Gate opening(OperationHandle handle)throws IOException {outsideHistory();synchronized(gateLock){Gate gate=handle(handle,true);if(gate.capture!=null)throw new IOException("Captured operation requires declared local sources");gate.resources++;return gate;}}
    private void uncertain(Gate gate){List<Runnable> signals=new ArrayList<>();synchronized(gateLock){try{freezeGate(gate);}catch(IOException impossible){throw new IllegalStateException(impossible);}collect(gate,signals);}signal(signals);}
    TrackedInput openReader(OperationHandle handle,ReaderFactory factory)throws IOException {
        if(factory==null)throw new IOException("Missing reader factory");Gate gate=opening(handle);enter();
        try{InputStream stream=factory.open();if(stream==null)throw new IOException("Factory returned no owned stream");return new TrackedInput(new Resource(gate),stream);}
        catch(IOException|RuntimeException|Error failure){uncertain(gate);throw failure;}finally{leave();}
    }
    TrackedOutput openWriter(OperationHandle handle,WriterFactory factory)throws IOException {
        if(factory==null)throw new IOException("Missing writer factory");Gate gate=opening(handle);enter();
        try{OutputStream stream=factory.open();if(stream==null)throw new IOException("Factory returned no owned stream");return new TrackedOutput(new Resource(gate),stream);}
        catch(IOException|RuntimeException|Error failure){uncertain(gate);throw failure;}finally{leave();}
    }
    private final class Resource {
        final Gate gate;final Thread owner=Thread.currentThread();boolean closed,busy;
        Resource(Gate gate){this.gate=gate;}
        boolean start(boolean closing)throws IOException {
            outsideHistory();if(Thread.currentThread()!=owner)throw new IOException("Tracked stream ownership mismatch");
            synchronized(gateLock){if(busy)throw new IOException("Reentrant stream operation");if(closed){if(closing)return false;throw new IOException("Closed stream");}
                if(!closing&&gate.phase!=Phase.OPEN)throw new IOException("Operation I/O frozen");busy=true;}
            enter();return true;
        }
        void finish(boolean closedSuccessfully){
            List<Runnable> signals=new ArrayList<>();try{synchronized(gateLock){busy=false;if(closedSuccessfully){closed=true;gate.resources--;}collect(gate,signals);}signal(signals);}finally{leave();}
        }
    }
    final class TrackedInput extends InputStream {
        private final Resource resource;private final InputStream delegate;
        private TrackedInput(Resource resource,InputStream delegate){this.resource=resource;this.delegate=delegate;}
        public int read()throws IOException{resource.start(false);try{return delegate.read();}finally{resource.finish(false);}}
        public int read(byte[] b)throws IOException{return read(b,0,b.length);}
        public int read(byte[] b,int off,int len)throws IOException{resource.start(false);try{return delegate.read(b,off,len);}finally{resource.finish(false);}}
        /** Local implementation: never invoke InputStream.transferTo (absent on Android API26). */
        public long transferTo(OutputStream output)throws IOException {
            Objects.requireNonNull(output);resource.start(false);
            try{byte[] buffer=new byte[8192];long total=0;int count;
                while((count=delegate.read(buffer))!=-1){if(count==0)throw new IOException("Transfer made no progress");output.write(buffer,0,count);total=Math.addExact(total,count);}return total;
            }catch(ArithmeticException overflow){throw new IOException("Transfer length overflow",overflow);}finally{resource.finish(false);}
        }
        public long skip(long n)throws IOException{resource.start(false);try{return delegate.skip(n);}finally{resource.finish(false);}}
        public int available()throws IOException{resource.start(false);try{return delegate.available();}finally{resource.finish(false);}}
        public void mark(int limit){try{resource.start(false);}catch(IOException failure){throw new IllegalStateException(failure);}try{delegate.mark(limit);}finally{resource.finish(false);}}
        public void reset()throws IOException{resource.start(false);try{delegate.reset();}finally{resource.finish(false);}}
        public boolean markSupported(){try{resource.start(false);}catch(IOException failure){throw new IllegalStateException(failure);}try{return delegate.markSupported();}finally{resource.finish(false);}}
        public void close()throws IOException{if(!resource.start(true))return;boolean success=false;try{delegate.close();success=true;}finally{resource.finish(success);}}
    }
    final class TrackedOutput extends OutputStream {
        private final Resource resource;private final OutputStream delegate;
        private TrackedOutput(Resource resource,OutputStream delegate){this.resource=resource;this.delegate=delegate;}
        public void write(int b)throws IOException{resource.start(false);try{delegate.write(b);}finally{resource.finish(false);}}
        public void write(byte[] b)throws IOException{write(b,0,b.length);}
        public void write(byte[] b,int off,int len)throws IOException{resource.start(false);try{delegate.write(b,off,len);}finally{resource.finish(false);}}
        public void flush()throws IOException{resource.start(false);try{delegate.flush();}finally{resource.finish(false);}}
        public void close()throws IOException{if(!resource.start(true))return;boolean success=false;try{delegate.close();success=true;}finally{resource.finish(success);}}
    }
    CallbackTicket post(OperationHandle operation,CallbackQueue queue,Runnable callback)throws IOException {
        outsideHistory();if(queue==null||callback==null)throw new IOException("Missing callback delivery");CallbackTicket ticket;
        synchronized(gateLock){Gate gate=handle(operation,true);ticket=new CallbackTicket(this,gate,callback);gate.pending.add(ticket);gate.posting++;}
        enter();try{queue.post(()->deliver(ticket));return ticket;}catch(RuntimeException|Error failure){cancelPending(ticket);throw failure;}
        finally{List<Runnable> signals=new ArrayList<>();try{synchronized(gateLock){ticket.gate.posting--;collect(ticket.gate,signals);}signal(signals);}finally{leave();}}
    }
    boolean cancelPending(CallbackTicket ticket)throws IOException {
        outsideHistory();List<Runnable> signals=new ArrayList<>();boolean removed;
        synchronized(gateLock){if(ticket==null||ticket.owner!=this)throw new IOException("Foreign callback ticket");removed=ticket.gate.pending.remove(ticket);if(removed){ticket.done=true;ticket.callback=null;}collect(ticket.gate,signals);}signal(signals);return removed;
    }
    private void deliver(CallbackTicket ticket){
        try{outsideHistory();}catch(IOException failure){throw new IllegalStateException(failure);}
        enter();try{Runnable callback;synchronized(gateLock){if(ticket.done)return;
            if(ticket.epoch!=ticket.gate.epoch||ticket.gate.phase!=Phase.OPEN){ticket.gate.pending.remove(ticket);ticket.done=true;ticket.callback=null;return;}
            ticket.gate.pending.remove(ticket);ticket.gate.running++;ticket.running=true;callback=ticket.callback;ticket.callback=null;}
            try{callback.run();}finally{List<Runnable> signals=new ArrayList<>();synchronized(gateLock){ticket.running=false;ticket.done=true;ticket.gate.running--;collect(ticket.gate,signals);}signal(signals);}
        }finally{leave();}
    }
    /** Cold cancellation never freezes, drains or resets a process-owned lifetime. */
    static void requireColdCancellationLocked(File root,HistoryPinRecord begin,Claim claim)throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);
        if(HistoryStorage.preparationSources(root)!=null)throw new IOException("Registered preparation owner excludes cold cancellation");
        HistoryOperations owner=HistoryStorage.operations(root);
        if(owner==null){if(claim!=null)throw new IOException("Cold operation owner changed");return;}
        synchronized(owner.gateLock){
            Gate gate=owner.gates.get(begin.operation.toString());
            if(gate!=null&&(!gate.begin.envelopeHash.equals(begin.envelopeHash)||gate.epoch!=0
                    ||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty()
                    ||(claim==null?gate.phase!=Phase.COLD_FROZEN&&!(gate.phase==Phase.TERMINAL_RETRY&&gate.coldCancellation)
                        :claim.owner!=owner||claim.gate!=gate)))throw new IOException("Same-process operation lifetime excludes cold cancellation");
            if(claim!=null){if(gate==null)throw new IOException("Cold cancellation gate disappeared");claim.validate();}
        }
    }
    /** Read-only preflight/refresh: a durable foreign receipt never discharges a live process owner. */
    static void requireColdCancellationAuditsLocked(File root,HistoryControlStore.Inspection control,
            HistoryPinRecord own,List<Claim> claimed)throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);HistoryOperations owner=HistoryStorage.operations(root);
        if(owner==null){if(!claimed.isEmpty())throw new IOException("Closed-audit lifetime owner changed");return;}
        Set<UUID> closed=new HashSet<>();for(HistoryPinRecord receipt:control.pins.closes)closed.add(receipt.operation);
        synchronized(owner.gateLock){for(HistoryPinRecord begin:control.pins.begins){
            if(begin.operation.equals(own.operation)||!closed.contains(begin.operation))continue;
            Gate gate=owner.gates.get(begin.operation.toString());if(gate==null)continue;
            boolean leaseClaim=false;
            for(Claim claim:claimed)if(claim.owner==owner&&claim.gate==gate){claim.validate();leaseClaim=true;}
            if(!gate.begin.envelopeHash.equals(begin.envelopeHash)||!gate.begin.registrationHash.equals(begin.registrationHash)||!gate.begin.bundle.equals(begin.bundle)
                    ||gate.phase==Phase.OPEN||gate.phase==Phase.TERMINATING&&!leaseClaim
                    ||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())
                throw new IOException("QUIESCENCE_PENDING: foreign closed-audit lifetime");
        }}
    }
    /** Preserve existing closed-audit rules while this lease claims its separate cold operation. */
    List<Claim> claimCancellationAuditsLocked(HistoryPinRecord own)throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);List<Claim> claims=new ArrayList<>();
        HistoryControlStore.Inspection control=probe.operationControlLocked();Set<UUID> closed=new HashSet<>();
        for(HistoryPinRecord receipt:control.pins.closes)closed.add(receipt.operation);
        try{synchronized(gateLock){for(HistoryPinRecord begin:control.pins.begins){
            if(begin.operation.equals(own.operation)||!closed.contains(begin.operation))continue;Gate gate=bind(begin);
            if(gate.phase==Phase.OPEN||gate.phase==Phase.TERMINATING||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())throw new IOException("QUIESCENCE_PENDING");
            if(gate.phase!=Phase.TERMINAL){gate.phase=Phase.TERMINAL_RETRY;claims.add(claimLocked(new Quiescence(this,gate),begin));}
        }}return claims;}catch(IOException|RuntimeException|Error failure){for(Claim claim:claims)claim.close();throw failure;}
    }
    Claim claimColdCancellationLocked(HistoryPinRecord begin)throws IOException {
        requireColdCancellationLocked(root,begin,null);
        synchronized(gateLock){Gate gate=bind(begin);gate.coldCancellation=true;gate.phase=Phase.TERMINATING;return new Claim(this,gate);}
    }
    /** Synchronous durability participants cannot fabricate quiescence or publish another terminal. */
    static void cancellationCallbackLocked(PreparationSources.Publication callback)throws IOException {
        rejectParticipant();COLD_CANCELLATION_PARTICIPANT.set(Boolean.TRUE);enter();
        try{callback.run();}finally{try{leave();}finally{COLD_CANCELLATION_PARTICIPANT.remove();}}
    }
    static final class Claim implements AutoCloseable {
        private final HistoryOperations owner;private final Gate gate;private final long epoch;private boolean done;
        private Claim(HistoryOperations owner,Gate gate){this.owner=owner;this.gate=gate;epoch=gate.epoch;}
        void validate()throws IOException {HistoryStorage.requireMutation(owner.root);synchronized(owner.gateLock){if(done||gate.epoch!=epoch||gate.phase!=Phase.TERMINATING||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())throw new IOException("Terminal claim changed");}}
        void finish()throws IOException{validate();synchronized(owner.gateLock){gate.phase=Phase.TERMINAL;gate.capture=null;done=true;}}
        public void close(){synchronized(owner.gateLock){if(!done){gate.phase=Phase.TERMINAL_RETRY;done=true;}}}
    }
    Claim claimLocked(Quiescence proof,HistoryPinRecord begin)throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);synchronized(gateLock){
            if(proof==null||proof.owner!=this||proof.gate!=bind(begin)||proof.epoch!=proof.gate.epoch)throw new IOException("Foreign terminal capability");Gate gate=proof.gate;
            if((gate.phase!=Phase.QUIESCENT&&gate.phase!=Phase.TERMINAL_RETRY)||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())throw new IOException("Operation is not quiescent");
            gate.coldCancellation=false;gate.phase=Phase.TERMINATING;return new Claim(this,gate);
        }
    }
    void validateCommittedLocked(Quiescence proof,HistoryPinRecord begin)throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);HistoryControlStore.Inspection control=probe.operationControlLocked();boolean found=false;
        for(HistoryPinRecord closed:control.pins.closes)if(HistoryPinRecord.matches(begin,closed))found=true;
        if(!found)throw new IOException("Exact committed CLOSED audit required");
        synchronized(gateLock){if(proof==null||proof.owner!=this||proof.gate!=bind(begin)||proof.epoch!=proof.gate.epoch)throw new IOException("Foreign audit proof");Gate gate=proof.gate;
            if((gate.phase!=Phase.TERMINAL&&gate.phase!=Phase.TERMINAL_RETRY&&gate.phase!=Phase.QUIESCENT)||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())throw new IOException("Committed audit is not quiescent");
        }
    }
    /** Last callback-free guard shared by every pin acknowledgement, including mixed journals. */
    void requireAcknowledgementLocked(HistoryControlStore.Inspection control)throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);synchronized(gateLock){
            for(HistoryPinRecord closed:control.pins.closes){Gate gate=gates.get(closed.operation.toString());
                if(gate==null||!HistoryPinRecord.matches(gate.begin,closed)||(gate.phase!=Phase.TERMINATING&&gate.phase!=Phase.TERMINAL)||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())throw new IOException("Closed audit lacks terminal lifetime authority");
            }
        }
    }
    AuditQuiescence tryClosedAuditsNowLocked()throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);HistoryControlStore.Inspection control=probe.operationControlLocked();
        if(control.pins.active!=null||!control.pins.stages.isEmpty())throw new IOException("Closed audits required");return retainedAuditsNowLocked();
    }
    /** Exact closed pairs only, even when a separate active BEGIN also needs reconciliation. */
    AuditQuiescence retainedAuditsNowLocked()throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);HistoryControlStore.Inspection control=probe.operationControlLocked();List<Quiescence> proofs=new ArrayList<>();
        Set<UUID> closed=new HashSet<>();for(HistoryPinRecord receipt:control.pins.closes)closed.add(receipt.operation);
        synchronized(gateLock){for(HistoryPinRecord begin:control.pins.begins){if(!closed.contains(begin.operation))continue;Gate gate=bind(begin);
            if(gate.phase==Phase.OPEN||gate.phase==Phase.TERMINATING||gate.resources!=0||gate.running!=0||gate.posting!=0||!gate.pending.isEmpty())throw new IOException("QUIESCENCE_PENDING");
            // Durable closed evidence permanently forbids activation, even before sync acknowledgement.
            if(gate.phase!=Phase.TERMINAL)gate.phase=Phase.TERMINAL_RETRY;proofs.add(new Quiescence(this,gate));}}
        return new AuditQuiescence(this,proofs);
    }
    CompletionStage<AuditQuiescence> prepareClosedAudits()throws IOException {
        outsideHistory();List<Runnable> signals=new ArrayList<>();List<CompletableFuture<Quiescence>> futures=new ArrayList<>();
        synchronized(history){try(HistoryStorage.MutationScope lease=HistoryStorage.administration(root)){
            HistoryControlStore.Inspection control=probe.operationControlLocked();
            if(control.pins.active!=null||!control.pins.stages.isEmpty())throw new IOException("Closed audits required");
            synchronized(gateLock){
                // Check the whole batch before freezing any key.
                for(HistoryPinRecord begin:control.pins.begins)if(bind(begin).phase==Phase.TERMINATING)throw new IOException("Terminal attempt in progress");
                for(HistoryPinRecord begin:control.pins.begins){Gate gate=bind(begin);CompletableFuture<Quiescence> future=new CompletableFuture<>();futures.add(future);
                    if(gate.phase==Phase.TERMINAL){Quiescence proof=new Quiescence(this,gate);signals.add(()->future.complete(proof));}
                    else{freezeGate(gate);gate.phase=Phase.TERMINAL_RETRY;gate.waiters.add(future);collect(gate,signals);}
                }
            }
        }}
        signal(signals);
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).thenApply(unused->{
            List<Quiescence> proofs=new ArrayList<>();for(CompletableFuture<Quiescence> future:futures)proofs.add(future.getNow(null));return new AuditQuiescence(this,proofs);
        });
    }
    List<Claim> claimAuditsLocked(AuditQuiescence audit)throws IOException {
        rejectParticipant();HistoryStorage.requireMutation(root);if(audit==null||audit.owner!=this)throw new IOException("Foreign audit capability");
        AuditQuiescence current=retainedAuditsNowLocked();if(current.proofs.size()!=audit.proofs.size())throw new IOException("Audit batch changed");List<Claim> claims=new ArrayList<>();
        try{synchronized(gateLock){for(int i=0;i<audit.proofs.size();i++){Quiescence proof=audit.proofs.get(i),now=current.proofs.get(i);if(proof.gate!=now.gate||proof.epoch!=now.epoch)throw new IOException("Audit binding changed");if(proof.gate.phase!=Phase.TERMINAL)claims.add(claimLocked(proof,proof.gate.begin));}}return claims;}
        catch(IOException failure){for(Claim claim:claims)claim.close();throw failure;}
    }
}
