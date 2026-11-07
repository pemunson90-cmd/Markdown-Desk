package com.example.markdowndesk;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** Complete transcript export through Android's native PDF primitives.
 * Message text is loaded in bounded database chunks, not from the virtual
 * WebView window. This class never opens the original archive for writing.
 * A failed source/generation/part check aborts without claiming completion.
 */
final class ArchiveTranscriptPdf implements AutoCloseable {
    private static final int WIDTH=595,HEIGHT=842,LEFT=46,TOP=52,RIGHT=WIDTH-46,BOTTOM=HEIGHT-58;
    private static final int MAX_LAYOUT_UNITS=8192;
    private final PdfDocument document=new PdfDocument();
    private final TextPaint title=style(18,true),heading=style(12,true),body=style(10.5f,false),
            caption=style(9,false),footer=style(8,false);
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageCount,y;

    private static TextPaint style(float size,boolean bold){
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);paint.setTextSize(size);
        paint.setTypeface(bold?Typeface.create(Typeface.DEFAULT,Typeface.BOLD):Typeface.DEFAULT);
        return paint;
    }
    private void startPage(){
        if(page!=null)return;
        page=document.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++pageCount).create());
        canvas=page.getCanvas();canvas.drawColor(Color.WHITE);y=TOP;
    }
    private void closePage(){
        if(page==null)return;
        canvas.drawText("Markdown Desk · "+pageCount,LEFT,HEIGHT-28,footer);
        document.finishPage(page);page=null;canvas=null;
    }
    private void space(int amount){
        startPage();
        if(y+amount>BOTTOM){closePage();startPage();}
        else y+=amount;
    }
    private void draw(String original,TextPaint paint,int after)throws IOException {
        if(original==null)original="";
        if(original.isEmpty()){space(Math.max(4,Math.round(paint.getFontSpacing())));return;}
        int offset=0;
        while(offset<original.length()){
            if(Thread.currentThread().isInterrupted())throw new IOException("PDF export cancelled");
            int end=Math.min(original.length(),offset+MAX_LAYOUT_UNITS);
            if(end<original.length()&&Character.isHighSurrogate(original.charAt(end-1)))end--;
            if(end<=offset)throw new IOException("Invalid PDF text boundary");
            StaticLayout layout=StaticLayout.Builder.obtain(original,offset,end,paint,RIGHT-LEFT)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build();
            for(int first=0;first<layout.getLineCount();){
                startPage();
                int last=first;
                int startTop=layout.getLineTop(first);
                while(last<layout.getLineCount()&&
                        layout.getLineBottom(last)-startTop<=BOTTOM-y)last++;
                if(last==first){
                    if(y==TOP)throw new IOException("PDF line exceeds available page height");
                    closePage();continue;
                }
                canvas.save();
                canvas.clipRect(LEFT,y,RIGHT,BOTTOM);
                canvas.translate(LEFT,y-startTop);
                layout.draw(canvas);
                canvas.restore();
                y+=layout.getLineBottom(last-1)-startTop;
                first=last;
            }
            offset=end;
        }
        if(after>0)space(after);
    }
    static void export(ArchiveDatabase database,ArchiveDatabase.Conversation conversation,
                       OutputStream destination)throws IOException {
        if(database==null||conversation==null||destination==null)
            throw new IOException("Missing transcript export source");
        final String generation=database.exportGeneration(conversation.archiveId);
        try(ArchiveTranscriptPdf pdf=new ArchiveTranscriptPdf()){
            pdf.draw(conversation.name,pdf.title,10);
            pdf.draw("Complete archived conversation · "+conversation.messages+" indexed messages",pdf.caption,8);
            pdf.draw("Original JSON preserved. Local PDF export by Markdown Desk.",pdf.caption,16);
            int index=0;
            for(;;){
                if(Thread.currentThread().isInterrupted())throw new IOException("PDF export cancelled");
                database.requireExportGeneration(conversation.archiveId,generation);
                List<ArchiveDatabase.Message> messages=database.messages(conversation.id,index,50);
                if(messages.isEmpty())break;
                for(int row=0;row<messages.size();row++){
                    ArchiveDatabase.Message m=messages.get(row);
                    ArchiveTranscriptIntegrity.message(index+row,m.ordinal,m.segments);
                    pdf.draw((m.role==null?"unknown":m.role)+" · "+
                            (m.created==null?"unknown date":m.created)+
                            " · message "+(m.ordinal+1),pdf.heading,5);
                    if(conversation.archiveId!=null&&!conversation.archiveId.isEmpty()&&
                            m.messageId!=null&&!m.messageId.isEmpty()){
                        Uri source=new Uri.Builder().scheme("markdowndesk").authority("archive")
                                .appendPath("v1").appendPath(conversation.archiveId)
                                .appendPath(conversation.id).appendPath(m.messageId).build();
                        pdf.draw("Source: "+source.toString(),pdf.caption,8);
                    }
                    for(int part=0;part<m.segments;part+=32){
                        int count=Math.min(32,m.segments-part);
                        pdf.draw(database.textChunkVerified(m.id,part,count),pdf.body,0);
                    }
                    pdf.space(14);
                }
                index+=messages.size();
            }
            database.requireExportGeneration(conversation.archiveId,generation);
            ArchiveTranscriptIntegrity.complete(conversation.messages,index);
            pdf.closePage();
            pdf.document.writeTo(destination);
            destination.flush();
        }
    }
    @Override public void close(){
        if(page!=null)closePage();
        document.close();
    }
}
