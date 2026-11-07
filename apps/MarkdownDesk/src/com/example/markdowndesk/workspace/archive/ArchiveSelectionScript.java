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
            "return JSON.stringify({id:id,quote:quote,start:start,end:end,\n" +
            " prefix:text.slice(Math.max(0,start-64),start),suffix:text.slice(end,end+64)});\n" +
            "})()";
    }
}
