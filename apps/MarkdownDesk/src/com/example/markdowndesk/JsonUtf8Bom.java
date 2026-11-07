package com.example.markdowndesk;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Objects;

/** Exact UTF-8 BOM admission for short-read SAF streams (without consuming source bytes). */
final class JsonUtf8Bom {
    private JsonUtf8Bom(){}
    static InputStream strip(InputStream original)throws IOException {
        PushbackInputStream input=new PushbackInputStream(
                new BufferedInputStream(Objects.requireNonNull(original),32768),3);
        byte[] mark=new byte[3];int count=0;
        try {
            while(count<3){
                int next=input.read();
                if(next<0)break;
                mark[count++]=(byte)next;
            }
            if(!(count==3 && (mark[0]&255)==0xef && (mark[1]&255)==0xbb && (mark[2]&255)==0xbf)
                    &&count>0)input.unread(mark,0,count);
            return input;
        }catch(IOException|RuntimeException error){
            try{input.close();}catch(IOException ignored){}
            throw error;
        }
    }
}
