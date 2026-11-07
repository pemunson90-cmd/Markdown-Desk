package com.example.markdowndesk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Actual streaming digest verification; no Android provider or APK required. */
public final class ExportReceiptCheck {
    interface Checked {void run()throws IOException;}
    static void ok(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    static void reject(Checked action,String title)throws Exception {
        try{action.run();throw new AssertionError("Unexpected verified export: "+title);}
        catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception {
        byte[] source=("Complete export with emoji 💜 and line breaks\r\n").repeat(150).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream store=new ByteArrayOutputStream();
        ExportReceipt.Sink sink=new ExportReceipt.Sink(store);
        reject(sink::receipt,"premature success before stream closure");
        for(int at=0;at<source.length;at+=17)
            sink.write(source,at,Math.min(17,source.length-at));
        sink.flush();
        sink.close();sink.close();
        ExportReceipt receipt=sink.receipt();
        ok(receipt==sink.receipt(),"completed receipt is immutable/idempotent");
        ok(receipt.length==source.length,"exact byte count");
        ok(Arrays.equals(source,store.toByteArray()),"unaltered UTF-8 output");
        ExportReceipt.verify(new ByteArrayInputStream(store.toByteArray()),receipt);
        reject(()->sink.write(17),"cannot append to closed receipt");
        byte[] corrupted=store.toByteArray();corrupted[20]^=31;
        reject(()->ExportReceipt.verify(new ByteArrayInputStream(corrupted),receipt),"same length mutated body");
        reject(()->ExportReceipt.verify(new ByteArrayInputStream(Arrays.copyOf(source,source.length-1)),receipt),
                "truncated readback");
        byte[] enlarged=Arrays.copyOf(source,source.length+1);
        reject(()->ExportReceipt.verify(new ByteArrayInputStream(enlarged),receipt),"appended provider bytes");
        reject(()->ExportReceipt.verify(null,receipt),"provider denied readback");
        reject(()->new ExportReceipt.Sink(null),"null output denied");
        InputStream stalled=new InputStream(){
            public int read(){return -1;}
            public int read(byte[] data,int off,int len){return 0;}
        };
        reject(()->ExportReceipt.verify(stalled,receipt),"stalled readback");
        System.out.println("PASS: closed SAF stream receipt; exact SHA-256/length, failed and changed readback");
    }
}
