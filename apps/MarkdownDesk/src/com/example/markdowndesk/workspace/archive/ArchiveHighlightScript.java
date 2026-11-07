package com.example.markdowndesk.workspace.archive;

import com.example.markdowndesk.archive.ArchiveJournal;
import java.util.Collection;

/** Read-only DOM decoration of journal-backed annotations.
 * The original Markdown/JSON content is never modified. Missing or ambiguous
 * quotes are left undecorated rather than attached to an incorrect passage.
 */
public final class ArchiveHighlightScript {
    private ArchiveHighlightScript() {}

    /** JavaScript runs in an app-owned, network-blocked preview WebView only.
     * Pass notes as data and never concatenate source text into executable JS.
     */
    public static String boot() {
        return "<script>(function(){\n"+
            "var saved=Object.create(null);\n"+
            "function nodesFor(root){var walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null),nodes=[],n;"+
            "while((n=walker.nextNode()))nodes.push(n);return nodes;}\n"+
            "function unwrap(){var marks=document.querySelectorAll('mark.md-archive-highlight');"+
            "for(var i=marks.length-1;i>=0;i--){var mark=marks[i],parent=mark.parentNode;"+
            "if(!parent)continue;while(mark.firstChild)parent.insertBefore(mark.firstChild,mark);"+
            "parent.removeChild(mark);parent.normalize();}}\n"+
            "function locate(nodes,offset){var at=0;for(var i=0;i<nodes.length;i++){var n=nodes[i],len=n.nodeValue.length;"+
            "if(offset<at+len ||(offset===at+len && i===nodes.length-1))return [n,offset-at];at+=len;}return null;}\n"+
            "function mark(record){if(!record.quote||!record.message)return;"+
            "var sections=document.querySelectorAll('#archive-stream section[data-message-id],section[data-message-id]');"+
            "for(var s=0;s<sections.length;s++){var section=sections[s];"+
            "if(section.getAttribute('data-message-id')!==record.message)continue;"+
            "var content=section.querySelector('.archive-content');if(!content)continue;"+
            "var nodes=nodesFor(content),text='';for(var j=0;j<nodes.length;j++)text+=nodes[j].nodeValue;"+
            "var positions=[],index=0;while((index=text.indexOf(record.quote,index))!==-1){positions.push(index);index+=Math.max(1,record.quote.length);}"+
            "if(!positions.length)continue;var at=-1;"+
            "{var valid=[];for(var k=0;k<positions.length;k++){var p=positions[k];"+
            "var before=record.prefix||'',after=record.suffix||'';"+
            "if((!before||text.substring(Math.max(0,p-before.length),p).endsWith(before))"+
            "&&(!after||text.substring(p+record.quote.length,p+record.quote.length+after.length).startsWith(after)))valid.push(p);}"+
            "if(valid.length===1)at=valid[0];}"+
            "if(at<0)continue;"+
            "var start=locate(nodes,at),end=locate(nodes,at+record.quote.length);if(!start||!end)continue;"+
            "try{var range=document.createRange();range.setStart(start[0],start[1]);range.setEnd(end[0],end[1]);"+
            "var label=document.createElement('mark');label.className='md-archive-highlight';"+
            "label.setAttribute('data-annotation-id',record.id);label.setAttribute('title','Open saved annotation');"+
            "label.appendChild(range.extractContents());range.insertNode(label);}catch(error){}"+
            "}}\n"+
            "function apply(){unwrap();var keys=Object.keys(saved);for(var i=0;i<keys.length;i++)mark(saved[keys[i]]);}\n"+
            "window.mdArchiveRefreshHighlights=apply;"+
            "window.mdArchiveAddHighlights=function(records){if(!Array.isArray(records))return;"+
            "for(var i=0;i<records.length;i++)if(records[i]&&records[i].id)saved[records[i].id]=records[i];apply();};\n"+
            "document.addEventListener('click',function(event){var m=event.target.closest('mark.md-archive-highlight');"+
            "if(!m)return;event.preventDefault();var id=m.getAttribute('data-annotation-id');"+
            "if(id)window.location.href='mdarchive://annotation?id='+encodeURIComponent(id);});\n"+
            "})();</script>";
    }

    /** Append annotations for the newly visible message batch. Caller filters
     * by exact message identities; records are not sent for unrelated chats.
     */
    public static String add(Collection<ArchiveJournal.Annotation> records){
        StringBuilder json=new StringBuilder("[");boolean first=true;
        if(records!=null)for(ArchiveJournal.Annotation a:records){
            if(a==null||a.id==null||a.messageId==null||a.quote==null||a.quote.isEmpty())continue;
            if(!first)json.append(',');first=false;
            json.append("{\"id\":").append(ArchiveScrollScript.quote(a.id))
                .append(",\"message\":").append(ArchiveScrollScript.quote(a.messageId))
                .append(",\"quote\":").append(ArchiveScrollScript.quote(a.quote))
                .append(",\"prefix\":").append(ArchiveScrollScript.quote(a.prefix==null?"":a.prefix))
                .append(",\"suffix\":").append(ArchiveScrollScript.quote(a.suffix==null?"":a.suffix))
                .append('}');
        }
        return "window.mdArchiveAddHighlights&&window.mdArchiveAddHighlights("+json.append(']').toString()+");";
    }
}