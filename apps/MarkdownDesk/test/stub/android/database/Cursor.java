package android.database;
public interface Cursor { boolean moveToFirst(); int getColumnIndex(String n); String getString(int i); void close(); }
