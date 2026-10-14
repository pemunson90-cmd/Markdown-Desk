import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
public class ParseJava {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try (StandardJavaFileManager fm=compiler.getStandardFileManager(diagnostics,null,null)) {
   List<java.io.File> files=new ArrayList<>();
   for(String dir:args) try(var paths=Files.walk(Path.of(dir))) { paths.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toFile())); }
   JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-source","11","-proc:none"),null,fm.getJavaFileObjectsFromFiles(files));
   task.parse();
   for(var d:diagnostics.getDiagnostics()) if(d.getKind()==Diagnostic.Kind.ERROR) throw new AssertionError(d.toString());
   System.out.println("PASS Java 11 syntax parse: "+files.size()+" files (not Android type-check or APK compilation)");
  }
 }
}
