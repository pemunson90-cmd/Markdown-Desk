package com.example.markdowndesk.workspace.annotations;
import java.nio.file.Files;
import java.nio.file.Path;
/** Emit exact Java-generated client scripts for dependency-free Node behavioral tests. */
public final class MarkdownNoteScriptDump {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Need capture and boot output paths");
        Files.writeString(Path.of(args[0]),MarkdownNoteScript.capture());
        Files.writeString(Path.of(args[1]),MarkdownNoteScript.boot());
    }
}
