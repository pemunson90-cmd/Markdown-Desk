package com.example.markdowndesk;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Existing M0 matched fixture, measure ONLY new Activity metadata wrapping, not renderer speed. */
public final class WrapBenchmark {
    static volatile String retained;
    static String fixture(int words) {
        String line="alpha beta gamma delta epsilon zeta eta theta iota kappa\n";
        StringBuilder text=new StringBuilder();
        for(int row=0;row<words/10;row++){text.append(row%100==0?line.replace("alpha","needle"):line);if(row%100==99)text.append('\n');}
        return text.toString();
    }
    public static void main(String[] args)throws Exception {
        Method wrap=MainActivity.class.getDeclaredMethod("previewPageHtml",String.class,String.class);wrap.setAccessible(true);
        System.out.println("words,source_bytes,source_sha256,html_chars,wrapped_chars,reference1_ns,reference2_ns,reference3_ns,wrap1_ms,wrap2_ms,wrap3_ms");
        for(int words:new int[]{10000,100000,500000}) {
            String source=fixture(words);byte[] bytes=source.getBytes(StandardCharsets.UTF_8);StringBuilder hash=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));
            String html=MarkdownRenderer.render(source,UiTheme.palette(0,false),18);String token=Long.toString(Long.MAX_VALUE);
            for(int i=0;i<3;i++)retained=(String)wrap.invoke(null,html,token);
            long[] reference=new long[3];double[] timings=new double[3];
            for(int i=0;i<3;i++){long start=System.nanoTime();retained=html;reference[i]=System.nanoTime()-start;start=System.nanoTime();retained=(String)wrap.invoke(null,html,token);timings[i]=(System.nanoTime()-start)/1e6;}
            if(!retained.replace(" data-md-preview=\""+token+"\"","").equals(html))throw new AssertionError("wrapper changed renderer bytes");
            System.out.printf(Locale.ROOT,"%d,%d,%s,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f%n",words,bytes.length,hash,html.length(),retained.length(),reference[0],reference[1],reference[2],timings[0],timings[1],timings[2]);
        }
    }
}
