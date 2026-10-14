package android.system;
public final class OsConstants {
 public static final int O_RDONLY=0,O_NOFOLLOW=0x20000,O_CLOEXEC=0x80000;
 public static boolean S_ISDIR(int mode){return mode==0040000;}
}
