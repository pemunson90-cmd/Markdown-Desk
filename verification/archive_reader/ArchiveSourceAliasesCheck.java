package com.example.markdowndesk;

import java.util.Set;

public final class ArchiveSourceAliasesCheck {
    public static void main(String[] args){
        String base="content://com.android.externalstorage.documents";
        String direct=base+"/document/primary%3ADownload%2FFold+Test.json";
        String throughFolder=base+"/tree/primary%3ADownload/document/primary%3aDownload%2fFold%2BTest.json";
        String differentFile=direct.replace("+","%20");
        String differentProvider=direct.replace("externalstorage","other");
        String differentProfile=direct.replace("content://","content://10@");
        String custom=base+"/custom/document/primary%3ADownload%2FFold+Test.json";
        ArchiveSourceAliases aliases=new ArchiveSourceAliases();
        require(aliases.resolve(direct,false).equals(JsonDocumentIndexer.sourceIdentity(direct,false)),
                "first import retains legacy ID");
        aliases.add("newer-conversation",throughFolder,false);
        aliases.add("older-conversation",direct,false);
        aliases.add("generic",direct,true);
        aliases.add("distinct-file",differentFile,false);
        aliases.add("distinct-provider",differentProvider,false);
        aliases.add("distinct-profile",differentProfile,false);
        aliases.add("custom",custom,false);
        require(aliases.hidden.equals(Set.of("older-conversation")),"only the older alias is hidden");
        require(aliases.resolve(direct,false).equals("newer-conversation"),"reimport keeps the visible archive ID");
        require(aliases.resolve(throughFolder,false).equals("newer-conversation"),"same folder URI reuses ID");
        require(aliases.resolve(throughFolder,true).equals("generic"),"generic JSON mode is independent");
        require(aliases.resolve(custom,false).equals(JsonDocumentIndexer.sourceIdentity(custom,false)),
                "unrecognized paths retain exact-URI identity");
        System.out.println("PASS: document aliases, independent index modes, files and profiles");
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
}
