package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Independent wire construction; no production writer is used for hostile/handwritten frames. */
public final class PrivateStateCheck {
    static int checks;
    interface Io { void run() throws Exception; }
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    static void eq(Object expected,Object actual,String message) { check(Objects.equals(expected,actual),message + ": " + expected + " != " + actual); }
    static void reject(Io action,String message) throws Exception {
        checks++; try { action.run(); } catch(IOException expected) { return; }
        throw new AssertionError("Accepted " + message);
    }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static byte[] join(byte[]... values) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); for(byte[] value:values) out.write(value); return out.toByteArray();
    }
    static void string(DataOutputStream out,String value) throws IOException {
        if(value==null) { out.writeInt(-1); return; }
        byte[] encoded=bytes(value); out.writeInt(encoded.length); out.write(encoded);
    }
    static final String NAME="Dräft😀\n.md", ID="historical/id\u0000", URI="content://synthetic/document/7";
    static final String HASH="0123456789abcdef".repeat(4);
    static byte[] common(String name,String uri,String hash,long modified,int readOnly,int bom,int start,int end) throws IOException {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(data);
        string(out,name); string(out,uri); string(out,hash); out.writeLong(modified); out.writeByte(readOnly);
        out.writeByte(bom); out.writeInt(start); out.writeInt(end); return data.toByteArray();
    }
    static byte[] tab(byte[] common,String id,int dirty,int locked,int editorY,int previewY,int mode,
            String find,String replace,int caseFlag,int wordFlag,int regexFlag) throws IOException {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); data.write(common); DataOutputStream out=new DataOutputStream(data);
        string(out,id); out.writeByte(dirty); out.writeByte(locked); out.writeInt(editorY); out.writeInt(previewY);
        out.writeInt(mode); string(out,find); string(out,replace); out.writeByte(caseFlag); out.writeByte(wordFlag); out.writeByte(regexFlag);
        return data.toByteArray();
    }
    static byte[] tab(boolean bom,int start,int end) throws IOException {
        return tab(common(NAME,URI,HASH,19,1,bom?1:0,start,end),ID,0,1,13,17,2,"find\n\u0000","replace\r\n",1,0,1);
    }
    static byte[] recovery(byte[] common,long timestamp) throws IOException {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); data.write(common); new DataOutputStream(data).writeLong(timestamp); return data.toByteArray();
    }
    static byte[] frame(int kind,byte[] metadata,byte[] payload) throws Exception {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(data);
        out.write(bytes("MDSTATE\n")); out.writeInt(1); out.writeByte(kind); out.writeInt(metadata.length);
        out.writeInt(payload.length); out.write(metadata); out.write(payload);
        out.write(MessageDigest.getInstance("SHA-256").digest(data.toByteArray())); return data.toByteArray();
    }
    static byte[] checksum(byte[] frame) throws Exception {
        byte[] copy=frame.clone(),hash=MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(copy,copy.length-32));
        System.arraycopy(hash,0,copy,copy.length-32,32); return copy;
    }
    static PrivateStateCodec.Record read(byte[] frame) throws Exception {
        return PrivateStateCodec.read(new ByteArrayInputStream(frame),new ByteArrayOutputStream());
    }
    static PrivateStateCodec.Provenance provenance() { return new PrivateStateCodec.Provenance(URI,HASH,19,true); }
    static PrivateStateCodec.Tab tabDto(boolean bom,int start,int end) {
        return new PrivateStateCodec.Tab(ID,NAME,provenance(),bom,false,true,start,end,13,17,2,"find\n\u0000","replace\r\n",true,false,true);
    }
    static byte[] writeTab(PrivateStateCodec.Tab tab,byte[] payload) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); PrivateStateCodec.writeTab(tab,payload.length,new ByteArrayInputStream(payload),out); return out.toByteArray();
    }

    static void independentValid() throws Exception {
        String text=" \r\nα😀\t\u0000 tail \r\n";
        byte[] payload=join(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf},bytes(text));
        byte[] wire=frame(6,tab(true,text.length(),1),payload);
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        PrivateStateCodec.Record result=PrivateStateCodec.read(new ByteArrayInputStream(wire),sink);
        eq(PrivateStateCodec.Kind.SESSION_TAB,result.kind,"handmade tab type");
        check(Arrays.equals(payload,sink.toByteArray()),"raw BOM CRLF NUL whitespace preserved");
        eq(text.length(),result.utf16Units,"UTF16 count including surrogate pair");
        eq(text.length(),result.tab.selectionStart,"reversed anchor retained"); eq(1,result.tab.selectionEnd,"reversed focus retained");
        eq(NAME,result.tab.displayName,"metadata Unicode/newline preserved"); eq(ID,result.tab.sourceTabId,"historical identity remains provenance");
        eq(URI,result.tab.provenance.sourceUri,"URI provenance retained exactly");
        eq(HASH,result.tab.provenance.sourceBaselineHash,"hash provenance retained");
        check(!result.tab.sourceDirty && result.tab.provenance.sourceReadOnly,"source clean/readOnly remain explicit observations");
        eq("find\n\u0000",result.tab.findQuery,"search NUL/newline preserved"); check(result.tab.editorLocked,"editor lock retained");
        check(Arrays.equals(wire,writeTab(tabDto(true,text.length(),1),payload)),"writer exact handwritten tab wire");
        byte[] common=common("Recovery",null,null,-1,0,0,2,0);
        byte[] recovery=frame(7,recovery(common,1234),bytes("😀"));
        PrivateStateCodec.Record decoded=read(recovery); eq(2,decoded.utf16Units,"recovery UTF16 count");
        eq(2,decoded.recovery.selectionStart,"reverse recovery anchor"); eq(0,decoded.recovery.selectionEnd,"reverse recovery focus");
        eq(null,decoded.recovery.provenance.sourceUri,"null provenance remains null");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        PrivateStateCodec.writeRecovery(new PrivateStateCodec.Recovery("Recovery",new PrivateStateCodec.Provenance(null,null,-1,false),false,2,0,1234),4,new ByteArrayInputStream(bytes("😀")),out);
        check(Arrays.equals(recovery,out.toByteArray()),"writer exact handwritten recovery wire");
        for(boolean bom:new boolean[]{false,true}) for(String content:new String[]{"","\ufeff","\ufeff\ufeff","x\r\ny","😀"}) {
            byte[] raw=join(bom?new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf}:new byte[0],bytes(content));
            PrivateStateCodec.Record r=read(frame(6,tab(bom,content.length(),0),raw));
            eq(content.length(),r.utf16Units,"BOM flag semantics");
            check(Arrays.equals(frame(6,tab(bom,content.length(),0),raw),writeTab(tabDto(bom,content.length(),0),raw)),"BOM exact full wire");
        }
    }

    static void framingFaults() throws Exception {
        byte[] good=frame(6,tab(false,0,0),bytes("text"));
        for(int i=0;i<good.length;i++) {
            byte[] prefix=Arrays.copyOf(good,i); reject(() -> read(prefix),"every truncated prefix " + i);
        }
        for(int at:new int[]{0,8,12,13,17,21,good.length-33,good.length-1}) {
            byte[] corrupt=good.clone(); corrupt[at]^=1; reject(() -> read(corrupt),"corrupt frame byte " + at);
        }
        reject(() -> read(join(good,new byte[]{0})),"trailing byte");
        reject(() -> read(join(good,good)),"second concatenated record");
        for(int kind:new int[]{0,1,5,9,255}) reject(() -> read(frame(kind,tab(false,0,0),bytes("x"))),"unknown kind " + kind);
        byte[] version=good.clone(); ByteBuffer.wrap(version).putInt(8,2); reject(() -> read(checksum(version)),"future version with valid digest");
        for(int length:new int[]{-1,Integer.MAX_VALUE,32716}) {
            byte[] oversized=good.clone(); ByteBuffer.wrap(oversized).putInt(13,length);
            reject(() -> read(checksum(oversized)),"metadata bound " + length);
        }
        for(int length:new int[]{-1,Integer.MAX_VALUE,8388609}) {
            byte[] oversized=good.clone(); ByteBuffer.wrap(oversized).putInt(17,length);
            reject(() -> read(checksum(oversized)),"payload bound " + length);
        }
        reject(() -> read(frame(6,join(tab(false,0,0),new byte[]{0}),bytes("x"))),"trailing metadata");
        byte[] invalidString=tab(false,0,0); ByteBuffer.wrap(invalidString).putInt(0,-2);
        reject(() -> read(frame(6,invalidString,bytes("x"))),"invalid negative string length");
        ByteBuffer.wrap(invalidString).putInt(0,4097); reject(() -> read(frame(6,invalidString,bytes("x"))),"metadata string length cap");
    }

    static void metadataFaults() throws Exception {
        byte[] text=bytes("abc");
        for(int start:new int[]{-1,4,Integer.MAX_VALUE}) reject(() -> read(frame(6,tab(false,start,0),text)),"invalid start " + start);
        for(int end:new int[]{-1,4,Integer.MAX_VALUE}) reject(() -> read(frame(6,tab(false,0,end),text)),"invalid end " + end);
        for(int flag:new int[]{2,127,255}) {
            reject(() -> read(frame(6,tab(common(NAME,null,null,-1,flag,0,0,0),ID,0,0,0,0,0,"","",0,0,0),text)),"readOnly bool " + flag);
            reject(() -> read(frame(6,tab(common(NAME,null,null,-1,0,flag,0,0),ID,0,0,0,0,0,"","",0,0,0),text)),"BOM bool " + flag);
            for(int position=0;position<5;position++) {
                final int p=position;
                reject(() -> read(frame(6,tab(common(NAME,null,null,-1,0,0,0,0),ID,p==0?flag:0,p==1?flag:0,0,0,0,"","",p==2?flag:0,p==3?flag:0,p==4?flag:0),text)),"tab bool " + position);
            }
        }
        for(String hash:new String[]{"", "0".repeat(63), "A".repeat(64), "g".repeat(64)})
            reject(() -> read(frame(6,tab(common(NAME,null,hash,-1,0,0,0,0),ID,0,0,0,0,0,"","",0,0,0),text)),"bad baseline hash");
        reject(() -> read(frame(7,recovery(common(NAME,null,null,-2,0,0,0,0),0),text)),"negative baseline below sentinel");
        reject(() -> read(frame(7,recovery(common(NAME,null,null,-1,0,0,0,0),-1),text)),"negative timestamp");
        for(int mode:new int[]{-1,3,Integer.MAX_VALUE}) reject(() -> read(frame(6,tab(common(NAME,null,null,-1,0,0,0,0),ID,0,0,0,0,mode,"","",0,0,0),text)),"mode out of range");
        reject(() -> read(frame(6,tab(common(NAME,null,null,-1,0,0,0,0),"",0,0,0,0,0,"","",0,0,0),text)),"empty source ID");
        for(int position=0;position<2;position++) {
            final int p=position;
            reject(() -> read(frame(6,tab(common(NAME,null,null,-1,0,0,0,0),ID,0,0,p==0?-1:0,p==1?-1:0,0,"","",0,0,0),text)),"negative scroll");
        }
        byte[] malformedName=tab(false,0,0); malformedName[4]=(byte)0xff;
        reject(() -> read(frame(6,malformedName,text)),"malformed metadata UTF8");
        String max="é".repeat(2048);
        byte[] maxMetadata=tab(common(max,max,null,-1,0,0,0,0),max,0,0,0,0,0,max,max,0,0,0);
        eq(max,read(frame(6,maxMetadata,text)).tab.displayName,"4096UTF8bytes allowed");
        reject(() -> read(frame(6,tab(common(max+"x",null,null,-1,0,0,0,0),ID,0,0,0,0,0,"","",0,0,0),text)),"4097UTF8bytes rejected");
        PrivateStateCodec.Tab invalid=new PrivateStateCodec.Tab(ID,"\ud800",provenance(),false,false,false,0,0,0,0,0,"","",false,false,false);
        reject(() -> writeTab(invalid,text),"writer lone surrogate metadata");
        reject(() -> writeTab(tabDto(false,4,0),text),"writer invalid cursor");
    }

    static byte[] prefEntries(String[] keys,int[] tags,int[] raw) throws IOException {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(data); out.writeInt(keys.length);
        for(int i=0;i<keys.length;i++) { string(out,keys[i]); out.writeByte(tags[i]); if(tags[i]==1) out.writeByte(raw[i]); else out.writeInt(raw[i]); }
        return data.toByteArray();
    }
    static void preferences() throws Exception {
        Map<String,Object> values=new LinkedHashMap<>(); values.put("word_goal",Integer.MAX_VALUE); values.put("ui_theme",8);
        values.put("typewriter",true); values.put("split_ratio",0.72f); values.put("preview_font_size",32f);
        values.put("line_numbers",false); values.put("launch_splash",true); values.put("font_size",12f);
        byte[] metadata=prefEntries(new String[]{"font_size","launch_splash","line_numbers","preview_font_size","split_ratio","typewriter","ui_theme","word_goal"},
                new int[]{3,1,1,3,3,1,2,2},new int[]{Float.floatToIntBits(12f),1,0,Float.floatToIntBits(32f),Float.floatToIntBits(0.72f),1,8,Integer.MAX_VALUE});
        byte[] handmade=frame(8,metadata,new byte[0]); PrivateStateCodec.Record decoded=read(handmade);
        eq(values,decoded.preferences,"all eight preferences exact types/ranges");
        ByteArrayOutputStream out=new ByteArrayOutputStream(); PrivateStateCodec.writePreferences(values,out);
        check(Arrays.equals(handmade,out.toByteArray()),"preference writer canonical sorted wire");
        try { decoded.preferences.put("word_goal",0); throw new AssertionError("mutable preferences"); } catch(UnsupportedOperationException expected) { checks++; }
        eq(0,read(frame(8,prefEntries(new String[0],new int[0],new int[0]),new byte[0])).preferences.size(),"empty selection preserves omission");
        for(String key:new String[]{"dark_theme","split_hint_seen","history_quota","recents","grants","signing_key","custom_images","unknown"}) {
            reject(() -> read(frame(8,prefEntries(new String[]{key},new int[]{1},new int[]{1}),new byte[0])),"unknown preference " + key);
            reject(() -> PrivateStateCodec.writePreferences(Map.of(key,true),new ByteArrayOutputStream()),"writer unknown preference " + key);
        }
        for(String[] keys:new String[][]{{"ui_theme","ui_theme"},{"word_goal","ui_theme"}})
            reject(() -> read(frame(8,prefEntries(keys,new int[]{2,2},new int[]{0,0}),new byte[0])),"duplicate/out-of-order keys");
        for(String key:new String[]{"font_size","preview_font_size","split_ratio"}) for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-0f,0f,-1f,100f}) {
            reject(() -> read(frame(8,prefEntries(new String[]{key},new int[]{3},new int[]{Float.floatToRawIntBits(value)}),new byte[0])),"float value " + key + "/" + value);
            reject(() -> PrivateStateCodec.writePreferences(Map.of(key,value),new ByteArrayOutputStream()),"writer float value");
        }
        for(String key:new String[]{"font_size","preview_font_size"}) for(float value:new float[]{Math.nextDown(12f),Math.nextUp(32f)})
            reject(() -> read(frame(8,prefEntries(new String[]{key},new int[]{3},new int[]{Float.floatToIntBits(value)}),new byte[0])),"font next outside bound");
        for(float value:new float[]{Math.nextDown(0.28f),Math.nextUp(0.72f)})
            reject(() -> read(frame(8,prefEntries(new String[]{"split_ratio"},new int[]{3},new int[]{Float.floatToIntBits(value)}),new byte[0])),"ratio next outside bound");
        for(String key:new String[]{"word_goal","ui_theme"}) reject(() -> read(frame(8,prefEntries(new String[]{key},new int[]{2},new int[]{-1}),new byte[0])),"negative integer pref");
        reject(() -> read(frame(8,prefEntries(new String[]{"ui_theme"},new int[]{2},new int[]{9}),new byte[0])),"theme9");
        reject(() -> read(frame(8,prefEntries(new String[]{"line_numbers"},new int[]{1},new int[]{2}),new byte[0])),"noncanonical preference bool");
        for(int tag:new int[]{0,1,3,4,255}) reject(() -> read(frame(8,prefEntries(new String[]{"ui_theme"},new int[]{tag},new int[]{0}),new byte[0])),"unknown/wrong preference tag");
        for(Object value:new Object[]{18,18d,18L,"18",true}) reject(() -> PrivateStateCodec.writePreferences(Map.of("font_size",value),new ByteArrayOutputStream()),"wrong exact writer type");
        for(int count:new int[]{-1,9,Integer.MAX_VALUE}) {
            byte[] badCount=ByteBuffer.allocate(4).putInt(count).array();
            reject(() -> read(frame(8,badCount,new byte[0])),"preference count bound");
        }
        {
            Map<String,Object> invalid=new HashMap<>(); invalid.put(null,Boolean.TRUE);
            reject(() -> PrivateStateCodec.writePreferences(invalid,new ByteArrayOutputStream()),"null preference key");
            invalid.clear(); invalid.put("line_numbers",null);
            reject(() -> PrivateStateCodec.writePreferences(invalid,new ByteArrayOutputStream()),"null preference value");
        }
        byte[] preferenceSize=handmade.clone(); ByteBuffer.wrap(preferenceSize).putInt(13,65484);
        reject(() -> read(checksum(preferenceSize)),"complete preference envelope cap");
        reject(() -> read(frame(8,metadata,new byte[]{0})),"preference payload forbidden");
        reject(() -> read(frame(8,join(metadata,new byte[]{0}),new byte[0])),"trailing preference metadata");
    }

    static void utf8Oracle() throws Exception {
        List<byte[]> cases=new ArrayList<>();
        cases.add(new byte[]{(byte)0xc0,(byte)0x80}); cases.add(new byte[]{(byte)0xed,(byte)0xa0,(byte)0x80});
        cases.add(new byte[]{(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80}); cases.add(new byte[]{(byte)0xf0,(byte)0x9f});
        cases.add(bytes("\u0000\u007f\u0080\u07ff\u0800\uffff😀\udbff\udfff"));
        Random random=new Random(721); for(int i=0;i<300;i++) { byte[] raw=new byte[random.nextInt(9)]; random.nextBytes(raw); cases.add(raw); }
        for(byte[] raw:cases) {
            Integer units=null;
            try { units=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).length(); }
            catch(CharacterCodingException bad) { }
            byte[] wire=frame(6,tab(false,0,0),raw);
            if(units==null) reject(() -> read(wire),"JDK malformed UTF8 oracle");
            else eq(units,read(wire).utf16Units,"JDK valid UTF8 oracle");
        }
        for(byte[] raw:new byte[][]{{},{(byte)0xef},{(byte)0xef,(byte)0xbb},{(byte)0xef,(byte)0xbb,0},{1,2,3}})
            reject(() -> read(frame(6,tab(true,0,0),raw)),"missing flagged BOM");
        for(int at=65532;at<=65538;at++) {
            String text="a".repeat(at)+"😀é€z"; byte[] raw=bytes(text);
            eq(text.length(),read(frame(6,tab(false,text.length(),0),raw)).utf16Units,"multibyte transfer boundary " + at);
        }
    }

    static final class ObservedOutput extends ByteArrayOutputStream {
        boolean closed,flushed;
        public void close() { closed=true; }
        public void flush() { flushed=true; }
    }
    static final class ChunkInput extends ByteArrayInputStream {
        final int chunk; final boolean zeroBulk; boolean closed;
        ChunkInput(byte[] bytes,int chunk,boolean zeroBulk) { super(bytes); this.chunk=chunk; this.zeroBulk=zeroBulk; }
        public synchronized int read(byte[] bytes,int offset,int length) {
            return zeroBulk?0:super.read(bytes,offset,Math.min(length,chunk));
        }
        public void close() { closed=true; }
    }
    static void streamContracts() throws Exception {
        byte[] raw=bytes("😀\r\né\u0000"); byte[] wire=frame(6,tab(false,1,0),raw);
        for(int chunk=1;chunk<=7;chunk++) for(boolean zero:new boolean[]{false,true}) {
            ChunkInput input=new ChunkInput(wire,chunk,zero); ObservedOutput sink=new ObservedOutput();
            PrivateStateCodec.Record record=PrivateStateCodec.read(input,sink);
            check(Arrays.equals(raw,sink.toByteArray()),"chunked/zero-progress exact payload");
            eq(1,record.tab.selectionStart,"cursor may lie inside surrogate pair");
            check(!input.closed&&!sink.closed&&!sink.flushed,"reader caller stream ownership");
            ChunkInput source=new ChunkInput(raw,chunk,zero); ObservedOutput output=new ObservedOutput();
            PrivateStateCodec.writeTab(tabDto(false,1,0),raw.length,source,output);
            check(Arrays.equals(wire,output.toByteArray()),"chunked writer exact frame");
            check(!source.closed&&!output.closed&&!output.flushed,"writer caller stream ownership");
        }
        byte[] corrupt=wire.clone(); corrupt[corrupt.length-1]^=1; ObservedOutput provisional=new ObservedOutput();
        reject(() -> PrivateStateCodec.read(new ByteArrayInputStream(corrupt),provisional),"checksum failure after provisional bytes");
        check(Arrays.equals(raw,provisional.toByteArray()),"sink may have full bytes but no successful return");
        check(!provisional.closed&&!provisional.flushed,"failed read owns no sink lifecycle");
        reject(() -> PrivateStateCodec.writeTab(tabDto(false,0,0),raw.length-1,new ByteArrayInputStream(raw),new ByteArrayOutputStream()),"writer source longer than declared");
        reject(() -> PrivateStateCodec.writeTab(tabDto(false,0,0),raw.length+1,new ByteArrayInputStream(raw),new ByteArrayOutputStream()),"writer source shorter than declared");
        reject(() -> PrivateStateCodec.writeTab(tabDto(false,0,0),1,new ByteArrayInputStream(new byte[]{(byte)0xff}),new ByteArrayOutputStream()),"writer invalid UTF8");
        InputStream failed=new InputStream(){ public int read() throws IOException { throw new IOException("fixture read failure"); } };
        reject(() -> PrivateStateCodec.writeTab(tabDto(false,0,0),1,failed,new ByteArrayOutputStream()),"writer source exception");
        OutputStream failedOut=new OutputStream(){ public void write(int b) throws IOException { throw new IOException("fixture write failure"); } };
        reject(() -> PrivateStateCodec.read(new ByteArrayInputStream(wire),failedOut),"reader sink exception");
        reject(() -> PrivateStateCodec.writeTab(tabDto(false,0,0),0,new ByteArrayInputStream(new byte[0]),failedOut),"writer frame exception");
    }

    public static void main(String[] args) throws Exception {
        independentValid(); framingFaults(); metadataFaults(); preferences(); utf8Oracle(); streamContracts();
        System.out.println("PASS " + checks + " private-state functional assertions");
    }
}
