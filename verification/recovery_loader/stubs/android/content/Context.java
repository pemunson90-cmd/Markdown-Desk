package android.content;

import java.io.File;

/** Host fixture only: the sole Context API used by session/recovery stores. */
public class Context {
    private final File filesDir;

    public Context(File filesDir) {
        this.filesDir = filesDir;
    }

    public File getFilesDir() {
        return filesDir;
    }
}
