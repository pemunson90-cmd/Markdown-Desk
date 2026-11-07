package com.example.markdowndesk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Streaming output receipt. SAF success requires a closed provider stream AND
 * exact byte-for-byte readback, not merely a successful write/flush call.
 * The original JSON is never opened for writing. No document-sized buffer.
 */
final class ExportReceipt {
    final long length;
    final byte[] sha256;
    private ExportReceipt(long length,byte[] digest){
        this.length=length;this.sha256=digest.clone();
    }
    static final class Sink extends OutputStream {
        private final OutputStream destination;
        private final MessageDigest sha;
        private long bytes;
        private boolean closed;
        Sink(OutputStream target)throws IOException {
            if(target==null)throw new IOException("Output document provider returned no stream");
            destination=target;
            try{sha=MessageDigest.getInstance("SHA-256");}
            catch(NoSuchAlgorithmException unavailable){throw new IOException("SHA-256 is unavailable",unavailable);}
        }
        private void admit(int count)throws IOException {
            if(closed)throw new IOException("Output stream already closed");
            if(count<0||bytes>Long.MAX_VALUE-count)throw new IOException("Output length exceeded accounting range");
        }
        @Override public void write(int value)throws IOException{
            admit(1);destination.write(value);sha.update((byte)value);bytes++;
        }
        @Override public void write(byte[] data,int start,int length)throws IOException{
            if(data==null)throw new NullPointerException("data");
            if(start<0||length<0||start>data.length-length)
                throw new IndexOutOfBoundsException("Invalid output slice");
            admit(length);
            destination.write(data,start,length);
            sha.update(data,start,length);bytes+=length;
        }
        @Override public void flush()throws IOException {
            if(closed)throw new IOException("Output stream already closed");
            destination.flush();
        }
        @Override public void close()throws IOException {
            if(closed)return;
            closed=true;
            destination.close();
        }
        ExportReceipt receipt()throws IOException{
            if(!closed||bytes<=0)throw new IOException("Export output was not completely closed");
            return new ExportReceipt(bytes,sha.digest());
        }
    }
    static void verify(InputStream original,ExportReceipt receipt)throws IOException {
        if(original==null||receipt==null||receipt.length<=0)
            throw new IOException("Output document cannot be reopened for integrity verification");
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException unavailable){throw new IOException("SHA-256 is unavailable",unavailable);}
        byte[] block=new byte[16384];long total=0;int read;
        while((read=original.read(block))!=-1){
            if(Thread.currentThread().isInterrupted())throw new IOException("Export verification cancelled");
            if(read==0)throw new IOException("Output provider stalled during verification");
            if(total>receipt.length-read)throw new IOException("Export output contains unexpected bytes");
            digest.update(block,0,read);total+=read;
        }
        if(total!=receipt.length||!Arrays.equals(digest.digest(),receipt.sha256))
            throw new IOException("Export output readback differs from the written complete document");
    }
}
