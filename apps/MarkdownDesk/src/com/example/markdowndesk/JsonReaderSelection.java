package com.example.markdowndesk;

import com.example.markdowndesk.workspace.annotations.VerifiedTextAnchor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Bounded recreation snapshot. A selection belongs to exact source, path and decoded page bytes. */
final class JsonReaderSelection {
    final String source,path,quote,prefix,suffix,digest;
    final int pageStart,pageTotal,start;
    JsonReaderSelection(String source,String path,int pageStart,int pageTotal,int start,
            String quote,String prefix,String suffix,String digest){
        this.source=source;this.path=path;this.pageStart=pageStart;this.pageTotal=pageTotal;this.start=start;
        this.quote=quote;this.prefix=prefix;this.suffix=suffix;this.digest=digest;
    }
    static JsonReaderSelection capture(String source,String path,int pageStart,int pageTotal,
            String value,int first,int last){
        if(source==null||source.length()>8192||path==null||path.length()>32768||value==null||
                value.length()>131200||pageStart<0||pageTotal<pageStart||first<0||last<=first||
                last>value.length()||last-first>65536)return null;
        if(first>0&&Character.isLowSurrogate(value.charAt(first))||
                last<value.length()&&Character.isLowSurrogate(value.charAt(last)))return null;
        String quote=value.substring(first,last),before=value.substring(Math.max(0,first-64),first),
                after=value.substring(last,Math.min(value.length(),last+64));
        if(!VerifiedTextAnchor.matchesSelected(value,quote,before,after,first,last))return null;
        return new JsonReaderSelection(source,path,pageStart,pageTotal,first,quote,before,after,hash(value));
    }
    int locate(String source,String path,int pageStart,int pageTotal,String value){
        if(this.source==null||!this.source.equals(source)||this.path==null||!this.path.equals(path)||
                this.pageStart!=pageStart||this.pageTotal!=pageTotal||value==null||value.length()>131200||
                quote==null||quote.isEmpty()||quote.length()>65536||prefix==null||prefix.length()>64||
                suffix==null||suffix.length()>64||digest==null||!digest.equals(hash(value)))return -1;
        int at=VerifiedTextAnchor.locate(value,quote,prefix,suffix,start);
        return at==start?at:-1;
    }
    private static String hash(String value){
        try{
            byte[] sum=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);for(byte b:sum)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
