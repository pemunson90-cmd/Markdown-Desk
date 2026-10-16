package com.example.markdowndesk;

import java.util.UUID;

/** Print-only paper layout; renderer content and trusted readiness script stay intact. */
final class DocumentPrintLayout {
    private static final String PREFIX="<!doctype html><html>";
    private static final String STYLE="<style>@page{margin:12mm}"
            +"@media print{"
            +"html{color-scheme:light!important;background:#fff!important}"
            +"body{max-width:none!important;width:auto!important;margin:0!important;padding:0!important;"
            +"background:#fff!important;color:#111!important;orphans:3;widows:3}"
            +"body *{color:#111!important;background:transparent!important;border-color:#777!important;"
            +"box-shadow:none!important;text-shadow:none!important;transition:none!important}"
            +"h1,h2,h3,h4,h5,h6{break-after:avoid;page-break-after:avoid}"
            +"pre{white-space:pre-wrap!important;overflow-wrap:anywhere!important;word-break:normal;"
            +"overflow:visible!important;max-width:100%;box-sizing:border-box;padding:8px;"
            +"border-radius:0;break-inside:auto;page-break-inside:auto}"
            +"pre code{white-space:inherit;overflow-wrap:inherit}"
            +".table-scroll{overflow:visible!important;max-width:100%}"
            +"table{width:100%;min-width:0!important;max-width:100%;table-layout:fixed;"
            +"border-collapse:collapse;border-radius:0;overflow:visible!important}"
            +"thead{display:table-header-group}tfoot{display:table-footer-group}"
            +"tr{break-inside:avoid;page-break-inside:avoid}"
            +"th,td{white-space:normal!important;overflow-wrap:anywhere!important;word-break:normal;padding:6px 8px}"
            +"}</style>";

    private DocumentPrintLayout(){}

    static String frame(String html,String token) {
        try {
            if(token==null||!UUID.fromString(token).toString().equals(token))throw new IllegalArgumentException("Invalid print token");
        } catch(IllegalArgumentException invalid) {throw new IllegalArgumentException("Invalid print token",invalid);}
        if(html==null||!html.startsWith(PREFIX+"<head>"))throw new IllegalArgumentException("Unexpected print HTML framing");
        int head=html.indexOf("</head>");
        if(head<0||!html.startsWith("</head><body>",head)||html.indexOf("</head>",head+7)>=0
                ||!html.endsWith("</body></html>"))throw new IllegalArgumentException("Unexpected print HTML framing");
        int capacity=Math.addExact(html.length(),STYLE.length()+token.length()+32);
        return new StringBuilder(capacity).append("<!doctype html><html data-md-preview=\"").append(token).append("\">")
                .append(html,PREFIX.length(),head).append(STYLE).append(html,head,html.length()).toString();
    }
}
