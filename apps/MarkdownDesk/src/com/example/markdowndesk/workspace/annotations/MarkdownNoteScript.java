package com.example.markdowndesk.workspace.annotations;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.Collection;

/** JavaScript is data-free until annotation values are serialized as safe JSON strings. */
public final class MarkdownNoteScript {
    private MarkdownNoteScript() {}

    /** Only selections with exact DOM text offsets can be saved. */
    public static String capture() {
        return
            "(function(){\n" +
            " var root=document.body,s=window.getSelection();\n" +
            " if(!root||!s||!s.rangeCount)return null;\n" +
            " var r=s.getRangeAt(0);\n" +
            " if(r.collapsed||!root.contains(r.commonAncestorContainer))return null;\n" +
            " var quote=r.toString();if(!quote||!quote.trim()||quote.length>65536)return null;\n" +
            " if(r.startContainer.nodeType!==3||r.endContainer.nodeType!==3)return null;\n" +
            " var walk=document.createTreeWalker(root,NodeFilter.SHOW_TEXT),n,offset=0,start=-1,end=-1,all='';\n" +
            " while((n=walk.nextNode())){\n" +
            "  if(n.parentElement&&n.parentElement.closest('script,style,noscript,template'))continue;\n" +
            "  if(n===r.startContainer)start=offset+r.startOffset;\n" +
            "  if(n===r.endContainer)end=offset+r.endOffset;\n" +
            "  all+=n.nodeValue;offset+=n.length;\n" +
            " }\n" +
            " if(start<0||end<0||end-start!==quote.length||all.slice(start,end)!==quote)return null;\n" +
            " return {quote:quote,start:start,end:end,prefix:all.slice(Math.max(0,start-64),start),suffix:all.slice(end,end+64)};\n" +
            "})()\n";
    }

    public static String boot() {
        return
            "(function(){if(window.mdNoteDecorate)return;\n" +
            "var rows=[],tones=['violet','rose','mint','amber','blue','slate'];\n" +
            "function owner(){return document.body;}\n" +
            "function find(row){\n" +
            " var root=owner();if(!root||!row||!row.quote)return null;\n" +
            " var walk=document.createTreeWalker(root,NodeFilter.SHOW_TEXT),nodes=[],n,offset=0,text='';\n" +
            " while((n=walk.nextNode())){\n" +
            "  if(n.parentElement&&n.parentElement.closest('script,style,noscript,template'))continue;\n" +
            "  nodes.push({node:n,start:offset,end:offset+n.length});offset+=n.length;text+=n.nodeValue;\n" +
            " }\n" +
            " var q=row.quote,valid=[];\n" +
            " function matches(pos){return text.slice(pos,pos+q.length)===q&&text.slice(Math.max(0,pos-row.prefix.length),pos)===row.prefix&&text.slice(pos+q.length,pos+q.length+row.suffix.length)===row.suffix;}\n" +
            " var p=0;while((p=text.indexOf(q,p))>=0){if(matches(p))valid.push(p);if(valid.length>1)return null;p+=Math.max(1,q.length);}\n" +
            " if(valid.length!==1)return null;var begin=valid[0],end=begin+q.length;\n" +
            " var a=null,z=null;for(var i=0;i<nodes.length;i++){var x=nodes[i];if(!a&&begin>=x.start&&begin<x.end)a={node:x.node,offset:begin-x.start};if(a&&end>x.start&&end<=x.end){z={node:x.node,offset:end-x.start};break;}}\n" +
            " if(!a||!z)return null;var range=document.createRange();range.setStart(a.node,a.offset);range.setEnd(z.node,z.offset);return range;\n" +
            "}\n" +
            "function show(){if(!window.CSS||!CSS.highlights||!window.Highlight)return;\n" +
            " for(var t=0;t<tones.length;t++){var tone=tones[t],ranges=[];\n" +
            " for(var i=0;i<rows.length;i++){if((rows[i].tone||'violet')!==tone)continue;var r=find(rows[i]);if(r)ranges.push(r);}\n" +
            " CSS.highlights.set('markdown-notes-'+tone,new Highlight(...ranges));}\n" +
            "}\n" +
            "window.mdNoteDecorate=function(data){rows=Array.isArray(data)?data:[];show();return rows.length;};\n" +
            "window.mdNoteJump=function(id){for(var i=0;i<rows.length;i++)if(rows[i].id===id){var r=find(rows[i]);if(!r)return false;var n=r.startContainer.parentElement;if(n&&n.scrollIntoView)n.scrollIntoView({block:'center',behavior:'auto'});return true;}return false;};\n" +
            "var css=document.createElement('style');css.textContent='"+
            "::highlight(markdown-notes-violet){background:rgba(186,106,235,.36);color:inherit}"+
            "::highlight(markdown-notes-rose){background:rgba(236,100,152,.32);color:inherit}"+
            "::highlight(markdown-notes-mint){background:rgba(82,196,158,.32);color:inherit}"+
            "::highlight(markdown-notes-amber){background:rgba(242,180,70,.34);color:inherit}"+
            "::highlight(markdown-notes-blue){background:rgba(90,150,245,.33);color:inherit}"+
            "::highlight(markdown-notes-slate){background:rgba(132,152,171,.34);color:inherit}';document.head.appendChild(css);\n" +
            "})();\n";
    }

    public static String decorate(Collection<ArchiveJournal.Annotation> entries) {
        StringBuilder out=new StringBuilder("window.mdNoteDecorate([");
        boolean first=true;
        for(ArchiveJournal.Annotation a:entries) {
            if(a==null||a.id==null||a.quote==null||a.quote.isEmpty())continue;
            if(!first)out.append(',');
            first=false;
            out.append("{id:").append(quoted(a.id)).append(",quote:").append(quoted(a.quote))
                .append(",prefix:").append(quoted(a.prefix==null?"":a.prefix))
                .append(",suffix:").append(quoted(a.suffix==null?"":a.suffix))
                .append(",start:").append(a.selectionStart)
                .append(",tone:").append(quoted(AnnotationTone.fromTags(a.tags))).append('}');
        }
        return out.append("]);").toString();
    }

    public static String jump(String id) {
        return "window.mdNoteJump("+quoted(id)+")";
    }

    private static String quoted(String s) {
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) {
                case '"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                default:
                    if(c<' '||c=='<'||c=='>'||c=='\u2028'||c=='\u2029'){
                        String h=Integer.toHexString(c);
                        out.append("\\u");for(int n=h.length();n<4;n++)out.append('0');out.append(h);
                    } else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
