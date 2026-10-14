package android.graphics;
import java.io.*;
public class BitmapFactory {
    public static class Options { public boolean inJustDecodeBounds; public int outWidth; public int outHeight; public int inSampleSize = 1; public Bitmap.Config inPreferredConfig; }
    private static boolean looksLikeImage(byte[] b) {
        if (b.length >= 8 && (b[0]&0xff)==0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G') return true;      // PNG
        if (b.length >= 3 && (b[0]&0xff)==0xFF && (b[1]&0xff)==0xD8) return true;                        // JPEG
        if (b.length >= 12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F' && b[8]=='W') return true; // WEBP
        return false;
    }
    public static Bitmap decodeFile(String path, Options o) {
        File f = new File(path);
        byte[] head = new byte[16];
        int n = 0;
        try (FileInputStream in = new FileInputStream(f)) { n = Math.max(0, in.read(head)); } catch (IOException e) { n = 0; }
        byte[] b = new byte[n];
        System.arraycopy(head, 0, b, 0, n);
        boolean ok = f.exists() && looksLikeImage(b);
        if (o != null) { o.outWidth = ok ? 800 : -1; o.outHeight = ok ? 600 : -1; }
        return ok ? new Bitmap() : null;
    }
    public static Bitmap decodeStream(InputStream in, Object r, Options o) { return new Bitmap(); }
}
