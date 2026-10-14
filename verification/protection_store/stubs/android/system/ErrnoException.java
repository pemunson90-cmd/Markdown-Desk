package android.system;
public class ErrnoException extends Exception {
 public ErrnoException(String operation,int errno){super(operation+" injected "+errno);}
}
