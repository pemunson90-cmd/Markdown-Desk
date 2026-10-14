package android.content.res;
import java.io.*;
public class AssetManager {
    public InputStream open(String path) throws IOException { return new ByteArrayInputStream(new byte[]{1,2,3}); }
}
