import com.sun.source.tree.*;import com.sun.source.util.*;import javax.tools.*;import java.nio.file.*;import java.util.*;
public final class ExtractProtectedSaves {
 public static void main(String[] args)throws Exception {
  String source=Files.readString(Path.of(args[0]));Set<String> names=new LinkedHashSet<>(List.of("protectedSavesRequest","protectedSavesSession","ProtectedSavesSession","showProtectedSaves","invalidateProtectedSaves","protectedSavesCurrent","showProtectedSavesPage","showProtectedSaveActions","presentProtectedSavesDialog","onPause","onDestroy","styleDialog","historyDialogBuilder","workspaceRootRows","workspaceDialogShown","launchProtectedCopy","REQUEST_PROTECTED_COPY"));
  JavaCompiler c=ToolProvider.getSystemJavaCompiler();JavacTask task=(JavacTask)c.getTask(null,null,null,List.of("-proc:none"),null,c.getStandardFileManager(null,null,null).getJavaFileObjects(args[0]));CompilationUnitTree unit=task.parse().iterator().next();SourcePositions p=Trees.instance(task).getSourcePositions();Map<String,String> found=new LinkedHashMap<>();
  for(Tree t:unit.getTypeDecls())if(t instanceof ClassTree)for(Tree m:((ClassTree)t).getMembers()){String n=m instanceof MethodTree?((MethodTree)m).getName().toString():m instanceof ClassTree?((ClassTree)m).getSimpleName().toString():m instanceof VariableTree?((VariableTree)m).getName().toString():"";if(names.contains(n))found.put(n,source.substring((int)p.getStartPosition(unit,m),(int)p.getEndPosition(unit,m)));}
  if(!found.keySet().equals(names)){names.removeAll(found.keySet());throw new AssertionError("Missing actual workspace members: "+names);}
  Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* MEMBERS */",String.join("\n",found.values())).replace("MainActivity.this","ProtectedHarness.this"));
 }
}
