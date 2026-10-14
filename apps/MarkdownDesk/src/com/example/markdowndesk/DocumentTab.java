package com.example.markdowndesk;

import java.util.UUID;

/* JADX INFO: loaded from: classes.dex */
public final class DocumentTab {
    public boolean dirty;
    public int editorScrollY;
    public int mode;
    public int previewScrollY;
    public boolean readOnly;
    public boolean editorLocked;
    public int selectionEnd;
    public int selectionStart;
    public String uri;
    public String findQuery = "";
    public String replaceQuery = "";
    public boolean findCaseSensitive;
    public boolean findWholeWord;
    public boolean findRegex;
    public boolean utf8Bom;
    public String id = UUID.randomUUID().toString();
    public String displayName = "Untitled.md";
    public String text = "";
    public String baselineHash = EditorTools.sha256(new byte[0]);
    public long baselineModified = -1;

    public DocumentTab copy() {
        DocumentTab documentTab = new DocumentTab();
        documentTab.id = this.id;
        documentTab.uri = this.uri;
        documentTab.findQuery = this.findQuery;
        documentTab.replaceQuery = this.replaceQuery;
        documentTab.findCaseSensitive = this.findCaseSensitive;
        documentTab.findWholeWord = this.findWholeWord;
        documentTab.findRegex = this.findRegex;
        documentTab.displayName = this.displayName;
        documentTab.text = this.text;
        documentTab.baselineHash = this.baselineHash;
        documentTab.baselineModified = this.baselineModified;
        documentTab.utf8Bom = this.utf8Bom;
        documentTab.readOnly = this.readOnly;
        documentTab.editorLocked = this.editorLocked;
        documentTab.dirty = this.dirty;
        documentTab.selectionStart = this.selectionStart;
        documentTab.selectionEnd = this.selectionEnd;
        documentTab.editorScrollY = this.editorScrollY;
        documentTab.previewScrollY = this.previewScrollY;
        documentTab.mode = this.mode;
        return documentTab;
    }
}
