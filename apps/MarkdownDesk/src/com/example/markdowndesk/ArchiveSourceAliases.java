package com.example.markdowndesk;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Groups active indexes by the provider's document identity and index mode. */
final class ArchiveSourceAliases {
    private static final Pattern DOCUMENT_PATH=Pattern.compile("(?:/tree/[^/]+)?/document/([^/]+)");
    private final Map<String,String> preferred=new HashMap<>();
    final Set<String> hidden=new HashSet<>();

    // Feed the newest completed index first. Older aliases remain directly addressable.
    void add(String archiveId,String uri,boolean generic){
        String key=documentKey(uri,generic);
        if(key!=null&&preferred.putIfAbsent(key,archiveId)!=null)hidden.add(archiveId);
    }

    String resolve(String uri,boolean generic){
        String id=preferred.get(documentKey(uri,generic));
        return id==null?JsonDocumentIndexer.sourceIdentity(uri,generic):id;
    }

    private static String documentKey(String source,boolean generic){
        try{
            URI uri=URI.create(source);
            if(!"content".equalsIgnoreCase(uri.getScheme())||uri.getRawAuthority()==null
                    ||uri.getRawPath()==null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)return null;
            Matcher path=DOCUMENT_PATH.matcher(uri.getRawPath());
            if(!path.matches())return null;
            // Decode the ID as one segment. Encoded slashes belong to the ID and '+' is literal.
            String document=URI.create("/"+path.group(1)).getPath().substring(1);
            if(document.indexOf('\u0000')>=0||document.indexOf('\ufffd')>=0)return null;
            return (generic?"json":"conversation")+'\u0000'+uri.getRawAuthority()+'\u0000'+document;
        }catch(IllegalArgumentException malformed){
            return null;
        }
    }
}
