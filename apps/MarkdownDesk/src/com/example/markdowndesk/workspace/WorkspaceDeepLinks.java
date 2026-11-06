package com.example.markdowndesk.workspace;

import java.net.URI;
import java.net.URISyntaxException;

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
}