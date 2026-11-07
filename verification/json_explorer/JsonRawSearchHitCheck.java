package com.example.markdowndesk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class JsonRawSearchHitCheck {
    private static void ok(boolean good,String description){if(!good)throw new AssertionError(description);}
    private interface Check {void run()throws Exception;}
    private static void reject(Check check,String name)throws Exception {
        try{check.run();throw new AssertionError("Unexpected accepted source: "+name);}
        catch(IOException expected){}
    }
    private static String hash(byte[] src)throws Exception{
        StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(src))
            s.append(String.format("%02x",b&255));return s.toString();
    }
    public static void main(String[] args)throws Exception {
        String uri="content://provider/tree/authorized/document/export.json";
        String content="{\"name\":\"😀 needle\"}\r\n{\"again\":\"needle\"}";
        byte[] payload=content.getBytes(StandardCharsets.UTF_8);
        byte[] bom=new byte[payload.length+3];bom[0]=(byte)0xef;bom[1]=(byte)0xbb;bom[2]=(byte)0xbf;
        System.arraycopy(payload,0,bom,3,payload.length);
        int second=content.lastIndexOf("needle");
        JsonRawSearchHit.Hit result=JsonRawSearchHit.verify(new ByteArrayInputStream(bom),uri,uri,
                hash(bom),"needle",second);
        ok(result.line==2,"CRLF line number");
        ok(result.snippet.substring(result.begin,result.end).equals("needle"),"exact selected quote");
        ok(result.begin>content.indexOf("needle"),"second match offset must not jump to first");
        ok(!result.snippet.contains("\ufffd"),"valid Unicode retained");
        reject(()->JsonRawSearchHit.verify(new ByteArrayInputStream(bom),"content://other",uri,hash(bom),"needle",second),"forged URI");
        reject(()->JsonRawSearchHit.verify(new ByteArrayInputStream(bom),uri,uri,"a".repeat(64),"needle",second),"changed hash");
        reject(()->JsonRawSearchHit.verify(new ByteArrayInputStream(bom),uri,uri,hash(bom),"needle",second-1),"wrong quote offset");
        byte[] invalid=new byte[]{(byte)0xc0,(byte)0xaf};
        reject(()->JsonRawSearchHit.verify(new ByteArrayInputStream(invalid),uri,uri,hash(invalid),"a",0),"malformed UTF8");
        InputStream giant=new InputStream(){int remain=JsonRawSearchHit.MAX_BYTES+1;
            public int read(){return remain-->0?'x':-1;}
            public int read(byte[] out,int off,int len){if(remain<=0)return -1;int n=Math.min(len,remain);
                java.util.Arrays.fill(out,off,off+n,(byte)'x');remain-=n;return n;}};
        reject(()->JsonRawSearchHit.verify(giant,uri,uri,hash(bom),"needle",0),"oversized source");
        System.out.println("PASS: verified bounded raw JSON source hit, BOM/UTF16/CRLF, stale/malformed/oversized inputs");
    }
}
