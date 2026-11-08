package com.example.markdowndesk.workspace.archive;

/** A verified FTS source span, not the user's differently normalized query.
 * Called only after the Activity validates source generation and message identity.
 * Missing/ambiguous rendered spans refuse instead of selecting a different hit.
 */
public final class ArchiveSearchHitScript {
    private ArchiveSearchHitScript() {}
    public static final String EXTRA_MATCH_TEXT="archive_search_match_text";
    public static final String EXTRA_MATCH_START="archive_search_match_start";
    public static final String EXTRA_MATCH_END="archive_search_match_end";
    public static final String EXTRA_REBUILD_INDEXES="archive_rebuild_search_indexes";

    public static String jump(String messageId,int part,String matchedText){
        if(messageId==null||messageId.isEmpty()||part<0||matchedText==null
                ||matchedText.isEmpty()||matchedText.length()>65536)return "false";
        return "(function(){var id="+ArchiveScrollScript.quote(messageId)+",part="+part+
            ",quote="+ArchiveScrollScript.quote(matchedText)+";"+
            "var sections=document.querySelectorAll('section[data-message-id]'),found=null;"+
            "for(var s=0;s<sections.length;s++){if(sections[s].getAttribute('data-message-id')!==id)continue;"+
            "var pieces=sections[s].querySelectorAll('.archive-part');"+
            "for(var p=0;p<pieces.length;p++){var piece=pieces[p],first=Number(piece.getAttribute('data-start-part')),"+
            "last=Number(piece.getAttribute('data-part-end'));"+
            "if(!Number.isInteger(first)||!Number.isInteger(last)||part<first||part>=last)continue;"+
            "var walker=document.createTreeWalker(piece,NodeFilter.SHOW_TEXT),nodes=[],text='',node;"+
            "while((node=walker.nextNode())){if(node.parentElement&&node.parentElement.closest('script,style,noscript,template'))continue;"+
            "nodes.push(node);text+=node.nodeValue;}"+
            "var at=text.indexOf(quote);if(at<0)continue;if(found||text.indexOf(quote,at+1)>=0)return false;"+
            "function point(offset){var count=0;for(var n=0;n<nodes.length;n++){var end=count+nodes[n].nodeValue.length;"+
            "if(offset<end||(offset===end&&n===nodes.length-1))return [nodes[n],offset-count];count=end;}return null;}"+
            "var a=point(at),b=point(at+quote.length);if(!a||!b)return false;found=[a,b];}}"+
            "if(!found)return false;var range=document.createRange();range.setStart(found[0][0],found[0][1]);"+
            "range.setEnd(found[1][0],found[1][1]);"+
            "if(window.CSS&&CSS.highlights&&window.Highlight){"+
            "var style=document.getElementById('md-search-hit-style');if(!style){style=document.createElement('style');"+
            "style.id='md-search-hit-style';style.textContent='::highlight(md-search-hit){background-color:#ffd76a;color:#171717;}';document.head.appendChild(style);}"+
            "CSS.highlights.set('md-search-hit',new Highlight(range));"+
            "}else{var selection=window.getSelection();if(!selection)return false;selection.removeAllRanges();selection.addRange(range);}"+
            "var rect=range.getBoundingClientRect();window.scrollBy(0,rect.top-80);return true;})()";
    }
}
