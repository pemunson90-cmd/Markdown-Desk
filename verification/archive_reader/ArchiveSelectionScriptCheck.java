package com.example.markdowndesk.workspace.archive;
/** Contract for exact DOM-range capture. Runtime behavior is tested by archive_selection_dom.cjs. */
public final class ArchiveSelectionScriptCheck {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        String script=ArchiveSelectionScript.capture();
        require(script.contains("range.startContainer.nodeType!==3"),"selection must start at a real text node");
        require(script.contains("content.contains(startNode)"),"selection must belong to message body");
        require(script.contains("endParent.closest('[data-message-id]')!==section"),"cannot span messages");
        require(script.contains("if(node===startNode)start=at+range.startOffset"),"capture actual start offset");
        require(script.contains("if(node===endNode)end=at+range.endOffset"),"capture actual end offset");
        require(script.contains("text.slice(start,end)!==quote"),"refuse inaccurate text mapping");
        require(!script.contains("all.indexOf(t)"),"first-occurrence shortcut forbidden");
        System.out.println("PASS archive exact range script source invariants");
    }
}
