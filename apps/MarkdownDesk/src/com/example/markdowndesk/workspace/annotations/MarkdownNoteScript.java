package com.example.markdowndesk.workspace.annotations;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.Collection;

/** JavaScript is data-free until annotation values are serialized as safe JSON strings. */
public final class MarkdownNoteScript {
    private MarkdownNoteScript() {}

    /** Only selections with exact DOM text offsets can be saved. */
    public static String capture() {
        return "(function(){var root=document.body,s=window.getSelection();" +
            "if(!root||!s||!s.rangeCount)return null;var r=s.getRangeAt(0);" +
            "if(r.collapsed||!root.contains(r.commonAncestorContainer))return null;" +
            "var quote=r.toString();if(!quote||!quote.trim()||quote.length>65536)return null;" +
            "var before=document.createRange();before.selectNodeContents(root);" +
            "before.setEnd(r.startContainer,r.startOffset);" +
            "var start=before.toString().length,end=start+quote.length;" +
            "var all=root.textContent||'';if(all.slice(start,end)!==quote)return null;" +
            "return {quote:quote,start:start,end:end," +
            "prefix:all.slice(Math.max(0,start-64),start),suffix:all.slice(end,end+64)};" +
            "})()";
    }

    public static String boot() {
        return "(function(){if(window.mdNoteDecorate)return;" +
            "var key='markdown-notes',rows=[];" +
            "function owner(){return document.body;}" +
            "function find(row){" +
            "var root=owner();if(!root||!row||!row.quote)return null;" +
            "var text=root.textContent||'',q=row.quote,valid=[];" +
            "function matches(pos){return text.slice(pos,pos+q.length)===q" +
            "&&text.slice(Math.max(0,pos-row.prefix.length),pos)===row.prefix" +
            "&&text.slice(pos+q.length,pos+q.length+row.suffix.length)===row.suffix;}" +
            "if(Number.isInteger(row.start)&&row.start>=0&&matches(row.start))valid.push(row.start);" +
            "if(!valid.length){var p=0;while((p=text.indexOf(q,p))>=0){" +
            "if(matches(p))valid.push(p);if(valid.length>1)return null;p+=Math.max(1,q.length);}}" +
            "if(valid.length!==1)return null;var begin=valid[0],end=begin+q.length;" +
            "var walk=document.createTreeWalker(root,NodeFilter.SHOW_TEXT),nodes=[],n,offset=0;" +
            "while((n=walk.nextNode())){nodes.push({node:n,start:offset,end:offset+n.length});offset+=n.length;}" +
            "var a=null,z=null;for(var i=0;i<nodes.length;i++){var x=nodes[i];" +
            "if(!a&&begin>=x.start&&begin<x.end)a={node:x.node,offset:begin-x.start};" +
            "if(a&&end>x.start&&end<=x.end){z={node:x.node,offset:end-x.start};break;}}" +
            "if(!a||!z)return null;var range=document.createRange();" +
            "range.setStart(a.node,a.offset);range.setEnd(z.node,z.offset);return range;}" +
            "function show(){if(!window.CSS||!CSS.highlights||!window.Highlight)return;" +
            "var ranges=[];for(var i=0;i<rows.length;i++){var r=find(rows[i]);if(r)ranges.push(r);}" +
            "CSS.highlights.set(key,new Highlight(...ranges));}" +
            "window.mdNoteDecorate=function(data){rows=Array.isArray(data)?data:[];show();return rows.length;};" +
            "window.mdNoteJump=function(id){for(var i=0;i<rows.length;i++)if(rows[i].id===id){" +
            "var r=find(rows[i]);if(!r)return false;var n=r.startContainer.parentElement;" +
            "if(n&&n.scrollIntoView)n.scrollIntoView({block:'center',behavior:'auto'});return true;}" +
            "return false;};" +
            "var css=document.createElement('style');" +
            "css.textContent='::highlight(markdown-notes){background:rgba(186,106,235,.36);color:inherit}';" +
            "document.head.appendChild(css);" +
            "})();";
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
                .append(",start:").append(a.selectionStart).append('}');
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
