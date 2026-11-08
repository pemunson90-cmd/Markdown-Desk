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
        JsonRawSearchHit.SearchResult current=JsonRawSearchHit.search(
                new ByteArrayInputStream(bom),"needle");
        ok(current.hits.size()==2,"current JSON literal search includes both matches");
        ok(current.hits.get(0).offset==content.indexOf("needle"),"first offset exact");
        ok(current.hits.get(1).offset==content.lastIndexOf("needle"),"second offset exact");
        ok(current.hits.get(1).line==2,"current JSON second hit CRLF line");
        ok(!current.limited,"small current JSON search is complete");
        ok(current.sha256.equals(hash(bom)),"current source SHA256 matches raw provider bytes");
        JsonRawSearchHit.SearchResult limited=JsonRawSearchHit.search(
                new ByteArrayInputStream("a".repeat(600).getBytes(StandardCharsets.UTF_8)),"a");
        ok(limited.hits.size()==500&&limited.limited,"excessive matches explicitly LIMITED");
        reject(()->JsonRawSearchHit.search(new ByteArrayInputStream(bom),""),"empty query rejected");
        String crlf="line1\r\nline2\r\nline3\r\n";
        JsonRawSearchHit.SearchResult lineHits=JsonRawSearchHit.search(
                new ByteArrayInputStream(crlf.getBytes(StandardCharsets.UTF_8)),"\n");
        ok(lineHits.hits.size()==3,"every CRLF line break found");
        ok(lineHits.hits.get(0).line==2&&lineHits.hits.get(1).line==3
                &&lineHits.hits.get(2).line==4,"CRLF across matching newline must be counted once");
        int second=content.lastIndexOf("needle");
        JsonRawSearchHit.Hit result=JsonRawSearchHit.verify(new ByteArrayInputStream(bom),uri,uri,
                hash(bom),"needle",second);
        ok(result.line==2,"CRLF line number");
        ok(result.snippet.substring(result.begin,result.end).equals("needle"),"exact selected quote");
        ok(result.begin>content.indexOf("needle"),"second match offset must not jump to first");
        ok(!result.snippet.contains("\ufffd"),"valid Unicode retained");
        ok(result.node==null,"concatenated JSON documents must never mint a structural path");
        String valid="{\"nodes\":[{\"name\":\"😀 needle\"},{\"name\":\"needle\"}]}";
        byte[] validBytes=valid.getBytes(StandardCharsets.UTF_8);
        JsonRawSearchHit.Hit exact=JsonRawSearchHit.verify(new ByteArrayInputStream(validBytes),
                uri,uri,hash(validBytes),"needle",valid.lastIndexOf("needle"));
        ok(exact.node!=null&&exact.node.path.size()==3,"verified exact JSON node path");
        ok("nodes".equals(exact.node.path.get(0).key)&&exact.node.path.get(1).index==1
                &&"name".equals(exact.node.path.get(2).key),"duplicate phrase maps to correct array item");
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
