package com.example.markdowndesk;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Observe a real constructor validation, then mutate caller-owned bytes. No production hook. */
public final class CaptureRaceCheck {
    public static void main(String[] args)throws Exception {
        Field owned=HistoryController.CapturedRevision.class.getDeclaredField("strictUtf8");owned.setAccessible(true);
        for(int attempt=0;attempt<30;attempt++) {
            byte[] caller=new byte[8388608];Arrays.fill(caller,(byte)'a');
            AtomicReference<HistoryController.CapturedRevision> captured=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
            Thread worker=new Thread(()->{try{captured.set(new HistoryController.CapturedRevision("tab","00000000-0000-0000-0000-000000000001",null,null,0,caller));}catch(Throwable failure){error.set(failure);}},"strict-capture-race");
            worker.start();boolean observed=false;
            while(worker.isAlive()&&!observed)for(StackTraceElement frame:worker.getStackTrace())
                if(frame.getClassName().equals(HistoryController.class.getName())&&frame.getMethodName().equals("validateBytes")){observed=true;break;}
            if(observed)caller[0]=(byte)0xff;worker.join(5000);
            if(worker.isAlive())throw new AssertionError("capture worker did not terminate");
            if(error.get()!=null&&!(error.get() instanceof IOException))throw new AssertionError("unexpected constructor failure",error.get());
            if(captured.get()!=null&&((byte[])owned.get(captured.get()))[0]==(byte)0xff)
                throw new AssertionError("accepted frozen capture retained malformed bytes changed after validation began");
            if(observed){System.out.println("History captured revision race: observed real validation; retained bytes remain strict or capture rejects PASS");return;}
        }
        throw new AssertionError("Could not observe real validation; race check is inconclusive");
    }
}
