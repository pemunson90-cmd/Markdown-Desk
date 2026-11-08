package com.example.markdowndesk.workspace.archive;

/** Only exact observed WebView DOM ranges can be bound to journal annotations. */
public final class ArchiveSelectionScript {
    private ArchiveSelectionScript(){}
    public static String capture(){
        return
            "(function(){\n" +
            "var selection=window.getSelection();\n" +
            "if(!selection||!selection.rangeCount)return '';\n" +
            "var range=selection.getRangeAt(0);\n" +
            "if(!range||range.collapsed||range.startContainer.nodeType!==3||range.endContainer.nodeType!==3)return '';\n" +
            "var startNode=range.startContainer,endNode=range.endContainer;\n" +
            "var startParent=startNode.parentElement,endParent=endNode.parentElement;\n" +
            "var section=startParent&&startParent.closest('[data-message-id]');\n" +
            "if(!section||!endParent||endParent.closest('[data-message-id]')!==section)return '';\n" +
            "var content=section.querySelector('.archive-content');\n" +
            "if(!content||!content.contains(startNode)||!content.contains(endNode))return '';\n" +
            "var id=section.getAttribute('data-message-id');\n" +
            "if(!id||id.length>2048)return '';\n" +
            "var quote=range.toString();\n" +
            "if(!quote||!quote.trim()||quote.length>65536)return '';\n" +
            "var walker=document.createTreeWalker(content,NodeFilter.SHOW_TEXT),node,at=0,start=-1,end=-1,text='';\n" +
            "while((node=walker.nextNode())){\n" +
            " if(node.parentElement&&node.parentElement.closest('script,style,noscript,template'))continue;\n" +
            " if(node===startNode)start=at+range.startOffset;\n" +
            " if(node===endNode)end=at+range.endOffset;\n" +
            " text+=node.nodeValue;at+=node.length;\n" +
            "}\n" +
            "if(start<0||end<=start||end-start!==quote.length||text.slice(start,end)!==quote)return '';\n" +
            "var part=startParent.closest('.archive-part');\n" +
            "var sourcePart=part?Number(part.getAttribute('data-start-part')):-1;\n" +
            "if(!Number.isInteger(sourcePart)||sourcePart<0)sourcePart=-1;\n" +
            "return JSON.stringify({id:id,quote:quote,start:start,end:end,sourcePart:sourcePart,\n" +
            " prefix:text.slice(Math.max(0,start-64),start),suffix:text.slice(end,end+64)});\n" +
            "})()";
    }
    /** Observe bounded selection data in the app-owned offline preview only. */
    public static String observe(){
        return "(function(){if(window.mdSelectionObserved)return;window.mdSelectionObserved=true;"+
            "function send(value){if(window.ArchiveSelectionState)window.ArchiveSelectionState.update(value);}"+
            "document.addEventListener('selectionchange',function(){var selected="+capture()+
            ";if(selected)send(selected);});"+
            "document.addEventListener('pointerdown',function(){send('');},true);"+
            "document.addEventListener('keydown',function(){send('');},true);})()";
    }
    /** Match one unique quote with both contexts before reconstructing a DOM
     * range. Selection text is data; source changes and duplicate matches refuse. */
    public static String restore(String snapshot){
        return "(function(){var record=JSON.parse("+ArchiveScrollScript.quote(snapshot)+
            "),sections=document.querySelectorAll('section[data-message-id]');"+
            "if(!record.quote||record.quote.length>65536)return false;var found=null;"+
            "for(var s=0;s<sections.length;s++){if(sections[s].getAttribute('data-message-id')!==record.id)continue;"+
            "var content=sections[s].querySelector('.archive-content');if(!content)continue;"+
            "var walker=document.createTreeWalker(content,NodeFilter.SHOW_TEXT),nodes=[],text='',node;"+
            "while((node=walker.nextNode())){if(node.parentElement&&node.parentElement.closest('script,style,noscript,template'))continue;"+
            "nodes.push(node);text+=node.nodeValue;}var at=-1,next=0,index;"+
            "while((index=text.indexOf(record.quote,next))!==-1){next=index+1;var before=record.prefix||'',after=record.suffix||'';"+
            "if(index<before.length||text.slice(index-before.length,index)!==before||"+
            "text.slice(index+record.quote.length,index+record.quote.length+after.length)!==after)continue;"+
            "if(at>=0||found)return false;at=index;}if(at<0)continue;"+
            "function point(offset){var count=0;for(var i=0;i<nodes.length;i++){var end=count+nodes[i].nodeValue.length;"+
            "if(offset<end||(offset===end&&i===nodes.length-1))return [nodes[i],offset-count];count=end;}return null;}"+
            "var first=point(at),last=point(at+record.quote.length);if(!first||!last)return false;found=[first,last];}"+
            "if(!found)return false;var range=document.createRange();range.setStart(found[0][0],found[0][1]);"+
            "range.setEnd(found[1][0],found[1][1]);var selection=window.getSelection();"+
            "selection.removeAllRanges();selection.addRange(range);return true;})()";
    }

}
