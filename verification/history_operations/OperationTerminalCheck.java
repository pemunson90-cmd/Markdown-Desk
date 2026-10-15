package com.example.markdowndesk;
import java.io.*;
import java.util.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;
import static com.example.markdowndesk.HistoryPinRecord.*;

/** Production boundary: old capability-free entry must never release a pin. */
public final class OperationTerminalCheck {
    static final byte[] MANIFEST={9,8,7};
    static InputStream input(){return new ByteArrayInputStream(MANIFEST);}
    static HistoryPins pins(Fixture f)throws IOException{return HistoryPins.open(f.store,f.catalog,f.identity,f.index,f.disk);}
    static String begin(Fixture f,HistoryPins pins)throws IOException {
        return pins.begin(Purpose.EXPORT,Scope.EXACT_SUBSET,Collections.singleton(f.original.id),HistoryPins.ManifestIdentity.read(Domain.NATIVE_HISTORY_V1,1,input()));
    }
    static void capabilityRequired()throws Exception {
        Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);
        rejects(()->pins.close(id,Outcome.CANCELLED,null),"capability-free close released operation protection");
        check(inspect(f).pins.active!=null&&inspect(f).pins.closes.isEmpty(),"failed terminal entry preserves BEGIN/reservation");
    }
    static void retry()throws Exception {
        Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);HistoryOperations ops=HistoryOperations.forBundle(f.store,f.catalog,f.identity,f.index);
        HistoryOperations.Quiescence q=ops.quiesce(ops.freezeForTerminal(id)).toCompletableFuture().get();pins.close(q,Outcome.CANCELLED,null);
        pins.close(q,Outcome.CANCELLED,null);check(inspect(f).pins.closes.size()==1,"same exact receipt retry idempotent");
        rejects(()->pins.close(q,Outcome.RELEASED,input()),"retry cannot change immutable outcome");
    }
    static void mixedAuditCannotBypass()throws Exception {
        Fixture f=new Fixture();HistoryPins pins=pins(f);String id=begin(f,pins);HistoryOperations ops=pins.operations();HistoryOperations.OperationHandle h=ops.activateAcknowledged(id,input());
        HistoryOperations.TrackedInput reader=ops.openReader(h,()->input());HistoryPinRecord first=inspect(f).pins.active;
        java.nio.file.Files.write(f.parent.resolve("history_control/pin-"+id+".close"),HistoryPinRecord.close(first,Outcome.CANCELLED));
        HistoryPinRecord next=record(f);java.nio.file.Files.write(f.parent.resolve("history_control/"+next.name()),HistoryPinRecord.encode(next));
        rejects(pins::reconcile,"active BEGIN must not bypass old CLOSED audit drain");reader.close();
    }
    public static void main(String[] args)throws Exception{capabilityRequired();retry();mixedAuditCannotBypass();System.out.println("Operation terminal: "+checks+" checks PASS");}
}
