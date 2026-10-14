package android.text;
public interface Editable extends CharSequence {
 <T> T[] getSpans(int start,int end,Class<T> type);
 void setSpan(Object span,int start,int end,int flags);
 void removeSpan(Object span);
 int getSpanStart(Object span);
 int getSpanEnd(Object span);
 int getSpanFlags(Object span);
}
