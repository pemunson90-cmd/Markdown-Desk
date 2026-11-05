package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Bounded source-driven DOCX subset. Does not open resources or implement import/SAF. */
final class MarkdownDocx {
    enum Failure { INVALID_TEXT, LIMIT_EXCEEDED, CANCELLED, GENERATION_FAILED }
    static final class ExportException extends IOException {
        final Failure failure;
        ExportException(Failure failure) { super("DOCX export: " + failure.name()); this.failure = failure; }
    }
    interface Cancellation { void check() throws ExportException; }
    enum Budget {
        SOURCE_BYTES(1048576), SOURCE_UNITS(1048576), LINE_UNITS(16384), BLOCKS(10000),
        RUNS(50000), WORK(16777216), LIST_DEPTH(9), INLINE_DEPTH(8), TABLES(100),
        COLUMNS(32), CELLS(10000), TARGET_UNITS(2048), RELATIONSHIPS(1000),
        XML_BYTES(8388608), ZIP_BYTES(8388608);
        final int maximum;
        Budget(int maximum) { this.maximum = maximum; }
    }
    static final class Limits {
        private final EnumMap<Budget,Integer> values;
        private Limits(EnumMap<Budget,Integer> values) { this.values = values; }
        static Limits defaults() {
            EnumMap<Budget,Integer> values = new EnumMap<>(Budget.class);
            for (Budget b : Budget.values()) values.put(b, b.maximum);
            return new Limits(values);
        }
        Limits with(Budget b, int value) {
            if (b == null || value < 1 || value > b.maximum) throw new IllegalArgumentException("Invalid DOCX limit");
            EnumMap<Budget,Integer> copy = new EnumMap<>(values); copy.put(b, value); return new Limits(copy);
        }
        int get(Budget b) { return values.get(b); }
    }
    enum Warning { UNSUPPORTED_INLINE, UNSUPPORTED_BLOCK, BLOCKED_LINK, IMAGE_NOT_INCLUDED,
        AMBIGUOUS_TABLE, AMBIGUOUS_LIST, UNTERMINATED_FENCE }
    static final class Result {
        final long length; final String sha256, sourceSha256;
        final Map<Warning,Integer> warnings; final List<Integer> warningOffsets;
        private Result(long length, String sha256, String sourceSha256, Context c) {
            this.length=length; this.sha256=sha256; this.sourceSha256=sourceSha256;
            warnings=Collections.unmodifiableMap(new EnumMap<>(c.warnings));
            warningOffsets=Collections.unmodifiableList(new ArrayList<>(c.offsets));
        }
    }
    private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PKG="http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String XML="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final Pattern HEADING=Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern LIST=Pattern.compile("^([ \\t]*)([-+*]|[0-9]+\\.)[ \\t]+(.*)$");
    private static final Pattern FENCE=Pattern.compile("^\\s*(```|~~~)(.*)$");
    private MarkdownDocx() {}

    /** Owns stagedOutput even when input validation fails; success follows ZIP finish and all closes. */
    static Result export(String source, String title, OutputStream stagedOutput, Limits limits,
                         Cancellation cancellation) throws IOException {
        if (stagedOutput == null) throw new IllegalArgumentException("Missing output");
        Context c=null; Counted counted=null; String sourceHash=null;
        try (OutputStream owned=stagedOutput) {
            if (source==null || limits==null || cancellation==null) throw new IllegalArgumentException("Missing export input");
            c=new Context(limits,cancellation); c.cancel.check();
            validate(source,c,true); validate(title==null?"Document":title,c,false);
            sourceHash=hex(digest().digest(source.getBytes(StandardCharsets.UTF_8)));
            counted=new Counted(owned,limits.get(Budget.ZIP_BYTES));
            try (ZipOutputStream zip=new ZipOutputStream(counted,StandardCharsets.UTF_8)) {
                c.zip=zip;
                c.entry("word/document.xml");
                c.raw(XML+"<w:document xmlns:w=\""+W+"\" xmlns:r=\""+R+"\"><w:body>");
                document(source,c);
                c.raw("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr></w:body></w:document>");zip.closeEntry();
                part(c,"word/styles.xml",styles());
                numbering(c);
                c.entry("word/_rels/document.xml.rels");
                c.raw(XML+"<Relationships xmlns=\""+PKG+"\"><Relationship Id=\"styles\" Type=\""+R+"/styles\" Target=\"styles.xml\"/><Relationship Id=\"numbering\" Type=\""+R+"/numbering\" Target=\"numbering.xml\"/>");
                for(int i=0;i<c.links.size();i++){c.cancel.check();c.raw("<Relationship Id=\"link"+i+"\" Type=\""+R+"/hyperlink\" Target=\""+escape(c.links.get(i))+"\" TargetMode=\"External\"/>");}
                c.raw("</Relationships>");zip.closeEntry();
                part(c,"_rels/.rels",XML+"<Relationships xmlns=\""+PKG+"\"><Relationship Id=\"document\" Type=\""+R+"/officeDocument\" Target=\"word/document.xml\"/><Relationship Id=\"core\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>");
                part(c,"docProps/core.xml",XML+"<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>"+escape(title==null?"Document":title)+"</dc:title></cp:coreProperties>");
                part(c,"[Content_Types].xml",XML+"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"+override("document","document.main")+override("styles","styles")+override("numbering","numbering")+"<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/></Types>");
                c.cancel.check(); zip.finish();
            }
        } catch (RuntimeException failure) { throw new ExportException(Failure.GENERATION_FAILED); }
        return new Result(counted.count,hex(counted.hash.digest()),sourceHash,c);
    }
    private static String override(String part,String type) {return "<Override PartName=\"/word/"+part+".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml."+type+"+xml\"/>";}
    private static void part(Context c,String name,String content)throws IOException {c.cancel.check();c.entry(name);c.raw(content);c.zip.closeEntry();}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static void validate(String s,Context c,boolean source)throws IOException {
        if(s.length()>(source?c.limits.get(Budget.SOURCE_UNITS):4096))throw new ExportException(Failure.LIMIT_EXCEEDED);
        long bytes=0; int line=0,nextCheck=0;
        for(int i=0;i<s.length();){if(i>=nextCheck){c.cancel.check();nextCheck=i+4096;}int cp=s.codePointAt(i);int chars=Character.charCount(cp);
            if(cp>=0xd800&&cp<=0xdfff || !(cp==9||cp==10||cp==13||cp>=32&&cp<=0xd7ff||cp>=0xe000&&cp<=0xfffd||cp>=0x10000&&cp<=0x10ffff))throw new ExportException(Failure.INVALID_TEXT);
            bytes+=cp<128?1:cp<2048?2:cp<65536?3:4; i+=chars;
            line=cp==10?0:line+chars;
            if(source&&(bytes>c.limits.get(Budget.SOURCE_BYTES)||line>c.limits.get(Budget.LINE_UNITS)))throw new ExportException(Failure.LIMIT_EXCEEDED);
        }
    }
    private static final class Counted extends OutputStream {
        final OutputStream out; final long maximum; final MessageDigest hash=digest();long count;
        Counted(OutputStream out,long maximum){this.out=out;this.maximum=maximum;}
        public void write(int b)throws IOException {write(new byte[]{(byte)b},0,1);}
        public void write(byte[]b,int off,int len)throws IOException {if(len>maximum-count)throw new ExportException(Failure.LIMIT_EXCEEDED);out.write(b,off,len);hash.update(b,off,len);count+=len;}
        public void flush()throws IOException {out.flush();}
        public void close()throws IOException {flush();} // export owns the actual stream, exactly once
    }
    private static final class Context {
        final Limits limits;final Cancellation cancel;ZipOutputStream zip;long xml,work;int blocks,runs,cells,tables;
        final EnumMap<Warning,Integer>warnings=new EnumMap<>(Warning.class);final List<Integer>offsets=new ArrayList<>();
        final List<String>links=new ArrayList<>();final List<Num>nums=new ArrayList<>();int offset;
        Context(Limits limits,Cancellation cancel){this.limits=limits;this.cancel=cancel;}
        void entry(String name)throws IOException {ZipEntry e=new ZipEntry(name);GregorianCalendar date=new GregorianCalendar();date.clear();date.set(2000,Calendar.JANUARY,1,0,0,0);e.setTime(date.getTimeInMillis());zip.putNextEntry(e);}
        void raw(String text)throws IOException {
            long bytes=0;int next=0;for(int i=0;i<text.length();){if(i>=next){cancel.check();next=i+4096;}int cp=text.codePointAt(i);bytes+=cp<128?1:cp<2048?2:cp<65536?3:4;if(bytes>limits.get(Budget.XML_BYTES)-xml)throw new ExportException(Failure.LIMIT_EXCEEDED);i+=Character.charCount(cp);}
            byte[]b=text.getBytes(StandardCharsets.UTF_8);xml+=bytes;zip.write(b);
        }
        void work(int amount)throws IOException {work+=amount;if(work>limits.get(Budget.WORK))throw new ExportException(Failure.LIMIT_EXCEEDED);cancel.check();}
        void block()throws IOException {cancel.check();if(++blocks>limits.get(Budget.BLOCKS))throw new ExportException(Failure.LIMIT_EXCEEDED);}
        void warn(Warning w){warnings.put(w,warnings.getOrDefault(w,0)+1);if(offsets.size()<16)offsets.add(offset);}
        void run(String text,String properties)throws IOException {
            if(++runs>limits.get(Budget.RUNS))throw new ExportException(Failure.LIMIT_EXCEEDED);
            raw("<w:r>"+(properties.isEmpty()?"":"<w:rPr>"+properties+"</w:rPr>"));
            StringBuilder chunk=new StringBuilder(512);
            for(int i=0;i<text.length();i++){
                char ch=text.charAt(i);
                if(ch=='\n'||ch=='\r'||ch=='\t'){
                    textChunk(chunk);raw(ch=='\t'?"<w:tab/>":"<w:br/>");
                    if(ch=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;
                }else{chunk.append(ch);if(chunk.length()>=512&&!Character.isHighSurrogate(ch))textChunk(chunk);}
            }
            textChunk(chunk);raw("</w:r>");
        }
        void textChunk(StringBuilder text)throws IOException {
            if(text.length()>0){cancel.check();raw("<w:t xml:space=\"preserve\">"+escape(text.toString())+"</w:t>");text.setLength(0);}
        }
    }
    private static final class Lines {
        final String source;int at;String line;int offset;
        Lines(String source){this.source=source;next();}
        void next(){if(at>source.length()){line=null;return;}offset=at;int end=source.indexOf('\n',at);if(end<0)end=source.length();line=source.substring(at,end);if(line.endsWith("\r"))line=line.substring(0,line.length()-1);at=end==source.length()?source.length()+1:end+1;}
        String peek(){if(at>source.length())return null;int end=source.indexOf('\n',at);if(end<0)end=source.length();String s=source.substring(at,end);return s.endsWith("\r")?s.substring(0,s.length()-1):s;}
    }
    private static boolean space(char ch){return ch==' '||ch=='\t'||ch=='\r'||ch=='\n'||ch=='\f'||ch==11;}
    private static boolean rule(String line,Context c)throws IOException {int count=0;char marker=0;for(int i=0;i<line.length();i++){if((i&4095)==0)c.cancel.check();char ch=line.charAt(i);if(space(ch))continue;if(marker==0){if(ch!='-'&&ch!='*'&&ch!='_')return false;marker=ch;}if(ch!=marker)return false;count++;}return count>=3;}
    private static boolean divider(String line,Context c)throws IOException {
        if(line==null)return false;int i=0,n=line.length(),cells=0;while(i<n&&space(line.charAt(i)))i++;if(i<n&&line.charAt(i)=='|')i++;
        while(i<n){c.cancel.check();while(i<n&&space(line.charAt(i)))i++;if(i<n&&line.charAt(i)==':')i++;int dashes=0;while(i<n&&line.charAt(i)=='-'){if((i&4095)==0)c.cancel.check();dashes++;i++;}if(dashes<3)return false;if(i<n&&line.charAt(i)==':')i++;while(i<n&&space(line.charAt(i)))i++;cells++;if(i==n)return cells>=2;if(line.charAt(i++)!='|')return false;while(i<n&&space(line.charAt(i)))i++;if(i==n)return cells>=2;}
        return false;
    }
    private static boolean blockStart(Lines l,Context c)throws IOException {return HEADING.matcher(l.line).matches()||FENCE.matcher(l.line).matches()||LIST.matcher(l.line).matches()||l.line.trim().startsWith(">")||rule(l.line,c)||l.line.contains("|")&&divider(l.peek(),c);}
    private static void document(String source,Context c)throws IOException {
        Lines l=new Lines(source);
        while(l.line!=null){c.cancel.check();c.offset=l.offset;if(l.line.trim().isEmpty()){l.next();continue;}
            Matcher fence=FENCE.matcher(l.line),heading=HEADING.matcher(l.line);
            if(fence.matches()){String opener=l.line,marker=fence.group(1);boolean ambiguous=opener.trim().startsWith(marker+marker.charAt(0));l.next();StringBuilder code=new StringBuilder();boolean first=true;
                while(l.line!=null&&!l.line.trim().startsWith(marker)){c.work(l.line.length());if(!first)code.append('\n');code.append(l.line);first=false;l.next();}
                String closing=l.line;
                if(closing==null)c.warn(Warning.UNTERMINATED_FENCE);else{if(!closing.trim().equals(marker))ambiguous=true;l.next();}
                if(ambiguous){c.warn(Warning.UNSUPPORTED_BLOCK);paragraph(c,opener+"\n"+code+(closing==null?"":"\n"+closing),null,true,null);}
                else paragraph(c,code.toString(),"Code",true,null);continue;}
            if(heading.matches()){paragraph(c,heading.group(2),"Heading"+heading.group(1).length(),false,null);l.next();continue;}
            if(l.line.contains("|")&&divider(l.peek(),c)){table(l,c);continue;}
            if(LIST.matcher(l.line).matches()){list(l,c);continue;}
            if(rule(l.line,c)){c.block();c.raw("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\"/></w:pBdr></w:pPr></w:p>");l.next();continue;}
            if(l.line.trim().startsWith(">")){StringBuilder quote=new StringBuilder();while(l.line!=null&&l.line.trim().startsWith(">")){if(quote.length()>0)quote.append('\n');c.work(l.line.length());quote.append(l.line.replaceFirst("^\\s*> ?",""));l.next();}paragraph(c,quote.toString(),"Quote",false,null);continue;}
            c.work(l.line.length());StringBuilder p=new StringBuilder(l.line);l.next();while(l.line!=null&&!l.line.trim().isEmpty()&&!blockStart(l,c)){c.work(l.line.length());p.append('\n').append(l.line);l.next();}paragraph(c,p.toString(),null,false,null);
        }
    }
    private static void paragraph(Context c,String text,String style,boolean literal,String num)throws IOException {
        c.block();c.work(text.length());List<Span>spans=literal?null:inline(text,c);
        if(!literal&&spans==null){c.warn(Warning.UNSUPPORTED_INLINE);literal=true;}
        c.raw("<w:p>"+(style==null&&num==null?"":"<w:pPr>"+(style==null?"":"<w:pStyle w:val=\""+style+"\"/>")+(num==null?"":num)+"</w:pPr>"));
        if(literal)c.run(text,style!=null&&style.equals("Code")?"<w:rFonts w:ascii=\"Liberation Mono\" w:hAnsi=\"Liberation Mono\"/>":"");else for(Span s:spans){
            if(s.kind.equals("link")){if(c.links.size()>=c.limits.get(Budget.RELATIONSHIPS))throw new ExportException(Failure.LIMIT_EXCEEDED);int id=c.links.size();c.links.add(s.target);c.raw("<w:hyperlink r:id=\"link"+id+"\">");c.run(s.text,"<w:color w:val=\"235A97\"/><w:u w:val=\"single\"/>");c.raw("</w:hyperlink>");}else c.run(s.text,properties(s.kind));}
        c.raw("</w:p>");
    }
    private static String properties(String kind){switch(kind){case"b":return"<w:b/>";case"i":return"<w:i/>";case"s":return"<w:strike/>";case"code":return"<w:rFonts w:ascii=\"Liberation Mono\" w:hAnsi=\"Liberation Mono\"/>";default:return"";}}
    private static final class Span {final String text,kind,target;Span(String t,String k,String u){text=t;kind=k;target=u;}}
    private static List<Span> inline(String text,Context c)throws IOException {
        if(text.indexOf('<')>=0||text.contains("[^")||text.indexOf('$')>=0){c.warn(Warning.UNSUPPORTED_BLOCK);return null;}
        List<Span>spans=new ArrayList<>();StringBuilder plain=new StringBuilder();String escapable="\\`*_{}[]()#+-.!|>~";
        for(int i=0;i<text.length();){c.work(1);if(spans.size()>c.limits.get(Budget.RUNS))throw new ExportException(Failure.LIMIT_EXCEEDED);char ch=text.charAt(i);
            if(ch=='\\'&&i+1<text.length()&&escapable.indexOf(text.charAt(i+1))>=0){plain.append(text.charAt(i+1));i+=2;continue;}
            boolean image=ch=='!'&&i+1<text.length()&&text.charAt(i+1)=='[';int open=image?i+1:i;
            if(text.charAt(open)=='['){int end=find(text,"](",open+1,c);int close=end<0?-1:find(text,")",end+2,c);
                if(end>=0&&close>=0){flush(spans,plain);String label=text.substring(open+1,end),target=text.substring(end+2,close).trim();
                    if(target.indexOf('(')>=0)return null;
                    if(target.length()>c.limits.get(Budget.TARGET_UNITS))throw new ExportException(Failure.LIMIT_EXCEEDED);
                    if(label.indexOf('\\')>=0)return null;
                    if(label.matches(".*[\\[\\]`*_~].*"))return null;
                    if(image){c.warn(Warning.IMAGE_NOT_INCLUDED);spans.add(new Span("Image: "+(label.isEmpty()?"Image":label)+" (image not included)","",null));}
                    else if(safeLink(target))spans.add(new Span(label,"link",target));else{c.warn(Warning.BLOCKED_LINK);spans.add(new Span(label,"",null));}
                    i=close+1;continue;}
            }
            String marker=null,kind=null;
            if(ch=='`'){marker="`";kind="code";}else if(text.startsWith("**",i)||text.startsWith("__",i)){marker=text.substring(i,i+2);kind="b";}else if(text.startsWith("~~",i)){marker="~~";kind="s";}else if(ch=='*'||ch=='_'){if(ch=='_'&&i>0&&Character.isLetterOrDigit(text.charAt(i-1))){plain.append(ch);i++;continue;}marker=String.valueOf(ch);kind="i";}
            if(marker!=null){int end=find(text,marker,i+marker.length(),c);if(end<=i+marker.length())return null;if(marker.equals("_")&&end+1<text.length()&&Character.isLetterOrDigit(text.charAt(end+1)))return null;String content=text.substring(i+marker.length(),end);if(!kind.equals("code")&&(content.indexOf('\\')>=0||content.indexOf('[')>=0))return null;
                if(content.indexOf('\n')>=0||!kind.equals("code")&&(content.indexOf('*')>=0||content.indexOf('_')>=0||content.indexOf('~')>=0||content.indexOf('`')>=0))return null;
                flush(spans,plain);spans.add(new Span(content,kind,null));i=end+marker.length();continue;}
            if(ch=='<'||text.startsWith("[^",i))c.warn(Warning.UNSUPPORTED_BLOCK);
            plain.append(ch);i++;if(spans.size()>c.limits.get(Budget.RUNS))throw new ExportException(Failure.LIMIT_EXCEEDED);
        }flush(spans,plain);return spans;
    }
    private static int find(String text,String needle,int start,Context c)throws IOException {
        int charged=0;
        for(int i=start;i<=text.length()-needle.length();i++){
            if(++charged==4096){c.work(charged);charged=0;}
            if(text.startsWith(needle,i)){c.work(charged+needle.length());return i;}
        }
        c.work(charged);return -1;
    }
    private static void flush(List<Span>spans,StringBuilder plain){if(plain.length()>0){spans.add(new Span(plain.toString(),"",null));plain.setLength(0);}}
    private static boolean safeLink(String target){if(!(target.startsWith("http://")||target.startsWith("https://")||target.startsWith("mailto:")))return false;for(int i=0;i<target.length();i++)if(Character.isISOControl(target.charAt(i))||Character.isWhitespace(target.charAt(i)))return false;return true;}
    private static List<String> cells(String line,int maximum){
        String s=line.trim();if(s.startsWith("|"))s=s.substring(1);
        int slashes=0;for(int j=s.length()-2;j>=0&&s.charAt(j)=='\\';j--)slashes++;
        if(s.endsWith("|")&&slashes%2==0)s=s.substring(0,s.length()-1);
        List<String>r=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean escaped=false;
        for(int i=0;i<s.length();i++){char ch=s.charAt(i);
            if(escaped){if(ch!='|')cell.append('\\');cell.append(ch);escaped=false;}
            else if(ch=='\\')escaped=true;
            else if(ch=='|'){if(r.size()>=maximum)return null;r.add(cell.toString().trim());cell.setLength(0);}
            else cell.append(ch);
        }
        if(escaped)cell.append('\\');if(r.size()>=maximum)return null;r.add(cell.toString().trim());return r;
    }
    private static void table(Lines l,Context c)throws IOException {
        StringBuilder source=new StringBuilder(l.line);List<List<String>>rows=new ArrayList<>();
        List<String>header=cells(l.line,c.limits.get(Budget.COLUMNS));int width=header==null?0:header.size();boolean ambiguous=header==null;
        if(header!=null)rows.add(header);l.next();source.append('\n').append(l.line);l.next();int cells=width;
        while(l.line!=null&&!l.line.trim().isEmpty()&&l.line.contains("|")){
            c.work(l.line.length());source.append('\n').append(l.line);
            if(!ambiguous){List<String>row=cells(l.line,c.limits.get(Budget.COLUMNS));if(row==null||row.size()!=width){ambiguous=true;rows.clear();}
                else{if(row.size()>c.limits.get(Budget.CELLS)-c.cells-cells)throw new ExportException(Failure.LIMIT_EXCEEDED);cells+=row.size();rows.add(row);}}
            l.next();
        }
        if(ambiguous){c.warn(Warning.AMBIGUOUS_TABLE);paragraph(c,source.toString(),null,true,null);return;}
        if(++c.tables>c.limits.get(Budget.TABLES)||cells>c.limits.get(Budget.CELLS)-c.cells)throw new ExportException(Failure.LIMIT_EXCEEDED);c.cells+=cells;c.block();
        c.raw("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>");for(String side:new String[]{"top","left","bottom","right","insideH","insideV"})c.raw("<w:"+side+" w:val=\"single\" w:sz=\"4\"/>");c.raw("</w:tblBorders></w:tblPr><w:tblGrid>");for(int i=0;i<width;i++)c.raw("<w:gridCol w:w=\""+(9638/width)+"\"/>");c.raw("</w:tblGrid>");
        for(int i=0;i<rows.size();i++){c.raw("<w:tr>"+(i==0?"<w:trPr><w:tblHeader/></w:trPr>":""));for(String cell:rows.get(i)){c.raw("<w:tc><w:tcPr><w:tcW w:w=\""+(9638/width)+"\" w:type=\"dxa\"/></w:tcPr>");paragraph(c,cell,null,false,null);c.raw("</w:tc>");}c.raw("</w:tr>");}c.raw("</w:tbl>");
    }
    private static final class Num {final boolean ordered;final int start;Num(boolean ordered,int start){this.ordered=ordered;this.start=start;}}
    private static void list(Lines l,Context c)throws IOException {
        List<String>source=new ArrayList<>();while(l.line!=null&&LIST.matcher(l.line).matches()){if(source.size()>=c.limits.get(Budget.BLOCKS))throw new ExportException(Failure.LIMIT_EXCEEDED);source.add(l.line);l.next();}
        int previous=0;boolean ambiguous=false;for(String s:source){Matcher m=LIST.matcher(s);m.matches();int indent=indent(m.group(1));if(indent%4!=0||indent/4>=c.limits.get(Budget.LIST_DEPTH)||indent>previous+4)ambiguous=true;previous=indent;}
        if(!source.isEmpty()&&indent(source.get(0))>0)ambiguous=true;
        if(l.line!=null&&!l.line.trim().isEmpty()&&(l.line.startsWith(" ")||l.line.startsWith("\t"))){while(l.line!=null&&!l.line.trim().isEmpty()){if(source.size()>=c.limits.get(Budget.BLOCKS))throw new ExportException(Failure.LIMIT_EXCEEDED);source.add(l.line);l.next();}ambiguous=true;}
        if(ambiguous){c.warn(Warning.AMBIGUOUS_LIST);paragraph(c,String.join("\n",source),null,true,null);return;}
        int[] active=new int[9];Arrays.fill(active,-1);boolean[] types=new boolean[9];int prevDepth=0;
        for(String s:source){Matcher m=LIST.matcher(s);m.matches();int depth=indent(m.group(1))/4;boolean ordered=m.group(2).endsWith(".");int start=1;try{if(ordered)start=Integer.parseInt(m.group(2).substring(0,m.group(2).length()-1));}catch(NumberFormatException bad){c.warn(Warning.AMBIGUOUS_LIST);paragraph(c,s,null,true,null);continue;}
            if(start<1||start>1000000){c.warn(Warning.AMBIGUOUS_LIST);paragraph(c,s,null,true,null);continue;}
            if(depth<prevDepth)for(int j=depth+1;j<9;j++)active[j]=-1;
            if(active[depth]<0||types[depth]!=ordered){active[depth]=c.nums.size()+1;c.nums.add(new Num(ordered,start));types[depth]=ordered;}
            String text=m.group(3);if(text.matches("^\\[[ xX]\\] .*"))text=(text.charAt(1)==' '?"☐ ":"☑ ")+text.substring(4);
            paragraph(c,text,null,false,"<w:numPr><w:ilvl w:val=\""+depth+"\"/><w:numId w:val=\""+active[depth]+"\"/></w:numPr>");prevDepth=depth;}
    }
    private static int indent(String s){int n=0;for(int i=0;i<s.length();i++){char ch=s.charAt(i);if(ch==' ')n++;else if(ch=='\t')n+=4-n%4;else break;}return n;}
    private static void numbering(Context c)throws IOException {
        c.entry("word/numbering.xml");c.raw(XML+"<w:numbering xmlns:w=\""+W+"\">");
        for(int n=0;n<c.nums.size();n++){c.cancel.check();Num num=c.nums.get(n);
            c.raw("<w:abstractNum w:abstractNumId=\""+n+"\"><w:multiLevelType w:val=\"multilevel\"/>");
            for(int d=0;d<9;d++)c.raw("<w:lvl w:ilvl=\""+d+"\"><w:start w:val=\""+num.start+"\"/><w:numFmt w:val=\""+(num.ordered?"decimal":"bullet")+"\"/><w:lvlText w:val=\""+(num.ordered?"%"+(d+1)+".":"•")+"\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:tabs><w:tab w:val=\"num\" w:pos=\""+(720*(d+1))+"\"/></w:tabs><w:ind w:left=\""+(720*(d+1))+"\" w:hanging=\"360\"/></w:pPr></w:lvl>");
            c.raw("</w:abstractNum>");
        }
        for(int n=0;n<c.nums.size();n++)c.raw("<w:num w:numId=\""+(n+1)+"\"><w:abstractNumId w:val=\""+n+"\"/></w:num>");
        c.raw("</w:numbering>");c.zip.closeEntry();
    }
    private static String styles(){StringBuilder s=new StringBuilder(XML+"<w:styles xmlns:w=\""+W+"\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Liberation Serif\" w:hAnsi=\"Liberation Serif\"/><w:sz w:val=\"24\"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:pPr><w:spacing w:after=\"160\" w:line=\"300\" w:lineRule=\"auto\"/></w:pPr></w:style>");for(int i=1;i<=6;i++)s.append("<w:style w:type=\"paragraph\" w:styleId=\"Heading").append(i).append("\"><w:name w:val=\"heading ").append(i).append("\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:keepNext/><w:outlineLvl w:val=\"").append(i-1).append("\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"").append(40-i*2).append("\"/></w:rPr></w:style>");return s+"<w:style w:type=\"paragraph\" w:styleId=\"Code\"><w:name w:val=\"Code\"/><w:pPr><w:shd w:fill=\"F0F0F0\"/></w:pPr><w:rPr><w:rFonts w:ascii=\"Liberation Mono\" w:hAnsi=\"Liberation Mono\"/><w:sz w:val=\"20\"/></w:rPr></w:style><w:style w:type=\"paragraph\" w:styleId=\"Quote\"><w:name w:val=\"Quote\"/><w:pPr><w:ind w:left=\"720\"/></w:pPr></w:style></w:styles>";}
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
