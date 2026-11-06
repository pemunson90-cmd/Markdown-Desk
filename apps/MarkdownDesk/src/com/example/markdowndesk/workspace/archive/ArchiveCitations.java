package com.example.markdowndesk.workspace.archive;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Portable quote and deep-link formatter. No link is registered without user action. */
public final class ArchiveCitations {
    private ArchiveCitations(){}
    public static String deepLink(String archive,String chat,String message){
        return "markdowndesk://archive/v1/"+enc(archive)+"/"+enc(chat)+"/"+enc(message);
    }
    public static String citation(String title,String date,String archive,String chat,String message,String quote){
        StringBuilder out=new StringBuilder();
        out.append(quote==null?"":quote.trim()).append("\n\n");
        out.append("— ").append(title==null?"Untitled conversation":title);
        if(date!=null&&!date.trim().isEmpty())out.append(" · ").append(date);
        out.append("\n").append(deepLink(archive,chat,message));
        return out.toString();
    }
    private static String enc(String s){
        try{return URLEncoder.encode(s==null?"":s,"UTF-8").replace("+","%20");}
        catch(UnsupportedEncodingException unavailable){throw new IllegalStateException(unavailable);}
    }
}