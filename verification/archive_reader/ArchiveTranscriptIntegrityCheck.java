package com.example.markdowndesk;

import java.io.IOException;

public final class ArchiveTranscriptIntegrityCheck {
    private interface Checked {void run()throws IOException;}
    private static void ok(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private static void reject(Checked test,String message)throws Exception {
        try{test.run();throw new AssertionError("Accepted invalid transcript: "+message);}
        catch(IOException expected){}
    }
    public static void main(String[] ignored)throws Exception {
        ArchiveTranscriptIntegrity.message(0,0,1);
        ArchiveTranscriptIntegrity.message(100000,100000,0);
        ArchiveTranscriptIntegrity.part(0,0);
        ArchiveTranscriptIntegrity.part(640,640);
        ArchiveTranscriptIntegrity.complete(0,0);
        ArchiveTranscriptIntegrity.complete(70000,70000);
        ArchiveTranscriptIntegrity.generation("source-1","source-1");
        reject(()->ArchiveTranscriptIntegrity.message(10,11,1),"skipped ordinal");
        reject(()->ArchiveTranscriptIntegrity.message(10,9,1),"duplicate ordinal");
        reject(()->ArchiveTranscriptIntegrity.message(10,10,-1),"negative segments");
        reject(()->ArchiveTranscriptIntegrity.message(-1,-1,1),"invalid expected ordinal");
        reject(()->ArchiveTranscriptIntegrity.part(32,33),"skipped text chunk");
        reject(()->ArchiveTranscriptIntegrity.part(32,31),"duplicate text chunk");
        reject(()->ArchiveTranscriptIntegrity.complete(70,69),"truncated message list");
        reject(()->ArchiveTranscriptIntegrity.complete(70,71),"excess message count");
        reject(()->ArchiveTranscriptIntegrity.generation("source-1","source-2"),"reimport race");
        reject(()->ArchiveTranscriptIntegrity.generation("source-1",null),"source missing");
        reject(()->ArchiveTranscriptIntegrity.generation("", ""),"empty generation");
        System.out.println("PASS: indexed transcript generation, message count/ordinals and exact segment completeness");
    }
}
