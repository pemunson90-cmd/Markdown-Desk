package com.example.markdowndesk.workspace.archive;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.Collection;

/** Local reader decorations for immutable, journal-backed annotations.
 * A quote is highlighted only if its exact context matches once. Formatting
 * elements are never extracted or moved; only individual text nodes are wrapped.
 */
public final class ArchiveHighlightScript {
    private ArchiveHighlightScript() {}

    public static String boot() {
        return "<script>"+library()+"</script>";
    }

    static String library() {
        return "(function(){\n"+
            "var saved=Object.create(null);\n"+
            "function nodesFor(root){var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT),out=[],n,offset=0;"+
            "while((n=w.nextNode())){out.push({node:n,start:offset});offset+=n.nodeValue.length;}return out;}\n"+
            "function unwrap(){var marks=document.querySelectorAll('mark.md-archive-highlight');"+
            "for(var i=marks.length-1;i>=0;i--){var mark=marks[i],parent=mark.parentNode;"+
            "if(!parent)continue;while(mark.firstChild)parent.insertBefore(mark.firstChild,mark);"+
            "parent.removeChild(mark);parent.normalize();}"+
            "var badges=document.querySelectorAll('.md-archive-note-badge');"+
            "for(var j=0;j<badges.length;j++)badges[j].remove();}\n"+
            "function find(record,text){var positions=[],at=0;"+
            "while((at=text.indexOf(record.quote,at))!==-1){"+
            "var before=record.prefix||'',after=record.suffix||'',end=at+record.quote.length;"+
            "if((!before||text.slice(Math.max(0,at-before.length),at).endsWith(before))&&"+
            "(!after||text.slice(end,end+after.length).startsWith(after)))positions.push(at);"+
            "at+=Math.max(1,record.quote.length);}"+
            "return positions.length===1?positions[0]:-1;}\n"+
            "function highlight(content,record){if(!record.quote)return false;"+
            "var nodes=nodesFor(content),text='';for(var i=0;i<nodes.length;i++)text+=nodes[i].node.nodeValue;"+
            "var at=find(record,text);if(at<0)return false;var end=at+record.quote.length;"+
            "for(var i=nodes.length-1;i>=0;i--){var part=nodes[i],node=part.node;"+
            "var from=Math.max(at,part.start)-part.start,to=Math.min(end,part.start+node.nodeValue.length)-part.start;"+
            "if(from>=to||!node.parentNode)continue;"+
            "var range=document.createRange();range.setStart(node,from);range.setEnd(node,to);"+
            "var marker=document.createElement('mark');marker.className='md-archive-highlight';"+
            "marker.setAttribute('data-annotation-id',record.id);marker.setAttribute('title','Open saved annotation');"+
            "try{range.surroundContents(marker);}catch(e){return false;}}return true;}\n"+
            "function apply(){unwrap();var sections=document.querySelectorAll('section.archive-msg[data-message-id]');"+
            "for(var s=0;s<sections.length;s++){var section=sections[s],id=section.getAttribute('data-message-id');"+
            "var content=section.querySelector('.archive-content'),meta=section.querySelector('.archive-meta');"+
            "if(!content||!meta||!id)continue;var records=[],keys=Object.keys(saved);"+
            "for(var i=0;i<keys.length;i++)if(saved[keys[i]].message===id)records.push(saved[keys[i]]);"+
            "if(!records.length)continue;var badge=document.createElement('a');badge.className='md-archive-note-badge';"+
            "badge.href='mdarchive://annotationlist?message='+encodeURIComponent(id);"+
            "badge.textContent='Notes ('+records.length+')';meta.appendChild(badge);"+
            "for(var r=0;r<records.length;r++)if(records[r].quote){"+
            "if(!highlight(content,records[r]))badge.title='Some anchors need review';"+
            "}}}\n"+
            "window.mdArchiveRefreshHighlights=apply;"+
            "window.mdArchiveReplaceHighlights=function(records){saved=Object.create(null);"+
            "if(Array.isArray(records))for(var i=0;i<records.length;i++)"+
            "if(records[i]&&records[i].id)saved[records[i].id]=records[i];apply();};"+
            "window.mdArchiveAddHighlights=function(records){if(!Array.isArray(records))return;"+
            "for(var i=0;i<records.length;i++)if(records[i]&&records[i].id)saved[records[i].id]=records[i];apply();};\n"+
            "document.addEventListener('click',function(event){var node=event.target;"+
            "if(!node||!node.closest)return;var mark=node.closest('mark.md-archive-highlight');"+
            "if(!mark)return;event.preventDefault();event.stopPropagation();"+
            "var id=mark.getAttribute('data-annotation-id');"+
            "if(id)window.location.href='mdarchive://annotation?id='+encodeURIComponent(id);});\n"+
            "})();\n";
    }

    public static String add(Collection<ArchiveJournal.Annotation> records) {
        return "window.mdArchiveAddHighlights&&window.mdArchiveAddHighlights("+json(records)+");";
    }

    public static String replace(Collection<ArchiveJournal.Annotation> records) {
        return "window.mdArchiveReplaceHighlights&&window.mdArchiveReplaceHighlights("+json(records)+");";
    }

    private static String json(Collection<ArchiveJournal.Annotation> records){
        StringBuilder json=new StringBuilder("[");boolean first=true;
        if(records!=null)for(ArchiveJournal.Annotation a:records){
            if(a==null||a.id==null||a.messageId==null)continue;
            if(!first)json.append(',');first=false;
            json.append("{\"id\":").append(ArchiveScrollScript.quote(a.id))
                .append(",\"message\":").append(ArchiveScrollScript.quote(a.messageId))
                .append(",\"quote\":").append(ArchiveScrollScript.quote(a.quote==null?"":a.quote))
                .append(",\"prefix\":").append(ArchiveScrollScript.quote(a.prefix==null?"":a.prefix))
                .append(",\"suffix\":").append(ArchiveScrollScript.quote(a.suffix==null?"":a.suffix))
                .append('}');
        }
        return json.append(']').toString();
    }
}