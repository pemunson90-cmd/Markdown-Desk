package com.example.markdowndesk.workspace.search;

/** Source-aware exact match gate for an indexed Markdown search result.
 * This class never opens documents, grants URI permissions or modifies source.
 */
public final class UnifiedSearchJump {
    public static final String EXTRA_URI = "com.example.markdowndesk.search.SOURCE";
    public static final String EXTRA_HASH = "com.example.markdowndesk.search.HASH";
    public static final String EXTRA_LITERAL = "com.example.markdowndesk.search.LITERAL";
    public static final String EXTRA_OFFSET = "com.example.markdowndesk.search.OFFSET";
    private UnifiedSearchJump(){}

    /** All comparisons use Java UTF-16 indices, exactly as the selected-tree
     * literal scanner and the Android editor do. The digest must describe the
     * unmodified source bytes of the already-open, authorized document.
     */
    public static boolean matches(String requestedUri,String currentUri,
            String searchHash,String baselineHash,CharSequence displayedText,
            String literal,int offset,boolean unsavedChanges) {
        if(unsavedChanges || requestedUri==null || !requestedUri.equals(currentUri)
                || searchHash==null || baselineHash==null || displayedText==null
                || literal==null || literal.isEmpty() || literal.length()>128
                || !searchHash.matches("[0-9A-Fa-f]{64}")
                || !baselineHash.equalsIgnoreCase(searchHash)
                || offset<0 || offset>displayedText.length()-literal.length())
            return false;
        for(int i=0;i<literal.length();i++)
            if(displayedText.charAt(offset+i)!=literal.charAt(i))return false;
        return true;
    }
}
