package com.example.markdowndesk.workspace.archive;
/** Emit actual Java production JS for Node's DOM test. */
public final class ArchiveSelectionScriptDump {
    public static void main(String[] args){System.out.print(args.length==0?ArchiveSelectionScript.capture():
        "observe".equals(args[0])?ArchiveSelectionScript.observe():ArchiveSelectionScript.restore(
        "{\"id\":\"message-7\",\"quote\":\"target\",\"prefix\":\"before \",\"suffix\":\" after\"}"));}
}
