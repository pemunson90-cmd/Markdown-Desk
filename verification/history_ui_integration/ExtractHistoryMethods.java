import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
/** Exact AST source-position extraction, no changes to production method bodies. */
public final class ExtractHistoryMethods {
 public static void main(String[] a)throws Exception {
  String source=Files.readString(Path.of(a[0]));
  List<String> names=Arrays.asList("invalidateHistory","historyCurrent","presentHistoryDialog","historyDialogBuilder","historyVersionStatus","showDocumentHistory","showHistoryPage","nameHistoryRevision","restoreHistoryVersion","compareHistory","captureActiveTab","findTabById","switchToTab","restoreActiveTab","lambda$restoreActiveTab$28","onPause","onDestroy");
  Map<String,String> found=new HashMap<>();
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  try(StandardJavaFileManager fm=compiler.getStandardFileManager(null,null,null)) {
   JavacTask task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjects(a[0]));
   CompilationUnitTree tree=task.parse().iterator().next();SourcePositions pos=Trees.instance(task).getSourcePositions();
   for(Tree type:tree.getTypeDecls())if(type instanceof ClassTree&&((ClassTree)type).getSimpleName().contentEquals("MainActivity"))for(Tree m:((ClassTree)type).getMembers())if(m instanceof MethodTree){String name=((MethodTree)m).getName().toString();if(names.contains(name)&&found.put(name,source.substring((int)pos.getStartPosition(tree,m),(int)pos.getEndPosition(tree,m)))!=null)throw new AssertionError("duplicate "+name);}
  }
  if(found.size()!=names.size())throw new AssertionError("missing methods: "+found.keySet());
  StringBuilder out=new StringBuilder();for(String n:names)out.append(found.get(n)).append('\n');
  Files.writeString(Path.of(a[2]),Files.readString(Path.of(a[1])).replace("/* METHODS */",out));
 }
}
