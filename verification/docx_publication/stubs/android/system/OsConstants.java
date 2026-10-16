package android.system;public final class OsConstants {public static final int O_RDONLY=0,O_NOFOLLOW=1,O_CLOEXEC=2;public static boolean S_ISDIR(int mode){return mode==040000;}}
