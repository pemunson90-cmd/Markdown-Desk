package android.print;
import java.util.*;
public class PrintManager {
 public final List<PrintDocumentAdapter> adapters=new ArrayList<>();public final List<String> names=new ArrayList<>();public final PrintJob job=new PrintJob();public Runnable duringPrint;public boolean fail,returnNull;
 public PrintJob print(String name,PrintDocumentAdapter adapter,PrintAttributes attrs){names.add(name);adapters.add(adapter);if(duringPrint!=null)duringPrint.run();if(fail)throw new IllegalStateException("print");return returnNull?null:job;}
}

