package com.example.markdowndesk.workspace;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Safe shared deep-link routing for authored Markdown and archive references.
 * Arbitrary custom-scheme URLs must not acquire app-internal navigation rights.
 * This validator is intentionally Android-free for local fuzz and regression tests.
 */
public final class WorkspaceDeepLinks {
    private WorkspaceDeepLinks() {}

    public static boolean isArchiveLink(String candidate) {
        if(candidate==null || candidate.length()>4096 || candidate.isEmpty())return false;
        try {
            URI uri=new URI(candidate);
            if(!"markdowndesk".equalsIgnoreCase(uri.getScheme()) ||
                    !"archive".equalsIgnoreCase(uri.getHost()) ||
                    uri.getUserInfo()!=null || uri.getPort()!=-1 ||
                    uri.getFragment()!=null)return false;
            String path=uri.getRawPath();
            if(path==null || !path.matches("/v1/[^/]+/[^/]+/[^/]+"))return false;
            if(path.contains("..") || path.contains("\\\\"))return false;
            // Restrict query keys to a single optional link ID; unexpected data
            // does not become an app intent or a different privilege path.
            String query=uri.getRawQuery();
            return query==null || query.matches("link=[A-Za-z0-9_%+.-]{1,512}");
        }catch(URISyntaxException|IllegalArgumentException invalid){return false;}
    }

    /** Exact, versioned document link. Only an encoded content:// URI may be a target.
     * This method does NOT grant the underlying URI permission or open the document.
     */
    public static String documentTarget(String candidate) {
        if(candidate==null || candidate.isEmpty() || candidate.length()>8192)return null;
        try {
            URI link=new URI(candidate);
            if(!"markdowndesk".equalsIgnoreCase(link.getScheme()) ||
                    !"document".equalsIgnoreCase(link.getHost()) ||
                    link.getUserInfo()!=null || link.getPort()!=-1 ||
                    link.getRawFragment()!=null)return null;
            String path=link.getRawPath();
            if(path==null || !path.startsWith("/v1/"))return null;
            String encoded=path.substring(4);
            if(encoded.isEmpty() || encoded.indexOf('/')>=0 || encoded.indexOf(92)>=0 ||
                    encoded.contains("..") || !encoded.matches("[A-Za-z0-9%._~!$&'()*+,;=:@-]+"))return null;
            String query=link.getRawQuery();
            if(query!=null && !query.matches("note=(?:[A-Za-z0-9._~-]|%[A-Fa-f0-9]{2}){1,512}"))return null;
            String decoded=URLDecoder.decode(encoded,StandardCharsets.UTF_8);
            URI target=new URI(decoded);
            if(!"content".equalsIgnoreCase(target.getScheme()) || target.getRawAuthority()==null ||
                    target.getRawAuthority().isEmpty() || target.getUserInfo()!=null || target.getPort()!=-1 ||
                    target.getRawFragment()!=null || target.getRawPath()==null ||
                    target.getRawPath().isEmpty() || target.getRawPath().contains(".."))return null;
            return decoded;
        }catch(URISyntaxException|IllegalArgumentException invalid){return null;}
    }

    /** Read-only source identity for a JSON Inspector note. The saved link NEVER
     * grants a ContentProvider permission; Android must already authorize it.
     */
    public static String inspectorTarget(String candidate) {
        if(candidate==null||candidate.length()>8192||candidate.isEmpty())return null;
        try{
            URI link=new URI(candidate);
            if(!"markdowndesk".equalsIgnoreCase(link.getScheme()) ||
                    !"inspector".equalsIgnoreCase(link.getHost()) ||
                    link.getUserInfo()!=null||link.getPort()!=-1||
                    link.getRawFragment()!=null)return null;
            String path=link.getRawPath();
            if(path==null||!path.startsWith("/v1/"))return null;
            String encoded=path.substring(4);
            if(encoded.isEmpty()||encoded.indexOf('/')>=0||encoded.indexOf(92)>=0||
                    encoded.contains("..")||
                    !encoded.matches("[A-Za-z0-9%._~!    public static boolean isDocumentLink(String candidate) {'()*+,;=:@-]+"))return null;
            String query=link.getRawQuery();
            if(query==null||!query.matches("note=(?:[A-Za-z0-9._~-]|%[A-Fa-f0-9]{2}){1,512}"))
                return null;
            String decoded=URLDecoder.decode(encoded,StandardCharsets.UTF_8);
            URI source=new URI(decoded);
            if(!"content".equalsIgnoreCase(source.getScheme())||
                    source.getRawAuthority()==null||source.getRawAuthority().isEmpty()||
                    source.getUserInfo()!=null||source.getPort()!=-1||
                    source.getRawFragment()!=null||source.getRawPath()==null||
                    source.getRawPath().isEmpty()||source.getRawPath().contains(".."))return null;
            return decoded;
        }catch(URISyntaxException|IllegalArgumentException invalid){return null;}
    }
    public static String inspectorNoteId(String candidate){
        if(inspectorTarget(candidate)==null)return null;
        try{
            URI link=new URI(candidate);
            String q=link.getRawQuery();
            return URLDecoder.decode(q.substring(5),StandardCharsets.UTF_8);
        }catch(URISyntaxException|IllegalArgumentException invalid){return null;}
    }
    public static boolean isInspectorLink(String candidate){
        return inspectorTarget(candidate)!=null;
    }

    public static boolean isDocumentLink(String candidate) {
        return documentTarget(candidate)!=null;
    }

    /** Constrained internal links alone can be rendered as navigable in Reading mode. */
    public static boolean isInternalLink(String candidate) {
        return isArchiveLink(candidate) || isDocumentLink(candidate) || isInspectorLink(candidate);
    }
}
