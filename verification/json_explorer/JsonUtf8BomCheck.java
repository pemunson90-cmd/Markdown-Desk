package com.example.markdowndesk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class JsonUtf8BomCheck {
    private static final class OneByte extends ByteArrayInputStream {
        OneByte(byte[] b){super(b);}
        @Override public synchronized int read(byte[] out,int offset,int count){return super.read(out,offset,Math.min(count,1));}
    }
    private static void check(byte[] source,byte[] expected)throws Exception {
        try(InputStream input=JsonUtf8Bom.strip(new OneByte(source))){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buffer=new byte[17];int n;
            while((n=input.read(buffer))>=0){if(n>0)out.write(buffer,0,n);}
            if(!Arrays.equals(out.toByteArray(),expected))throw new AssertionError("BOM short-read altered original bytes");
        }
    }
    public static void main(String[] args)throws Exception {
        byte[] utf8="{\"x\":\"😀\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bom=new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf};
        byte[] withBom=new byte[bom.length+utf8.length];
        System.arraycopy(bom,0,withBom,0,bom.length);
        System.arraycopy(utf8,0,withBom,bom.length,utf8.length);
        check(withBom,utf8);
        check(utf8,utf8);
        check(new byte[]{(byte)0xef},new byte[]{(byte)0xef});
        check(new byte[]{(byte)0xef,(byte)0xbb},new byte[]{(byte)0xef,(byte)0xbb});
        check(new byte[]{(byte)0xef,(byte)0xbb,0},new byte[]{(byte)0xef,(byte)0xbb,0});
        check(bom,new byte[0]);
        check(new byte[0],new byte[0]);
        System.out.println("PASS: JSON UTF8 BOM across short provider reads and exact non-BOM bytes");
    }
}
