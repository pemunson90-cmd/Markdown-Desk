import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
public final class ExtractNavigation {
 public static void main(String[] args)throws Exception{
  String source=Files.readString(Path.of(args[0]));
  Set<String> wanted=new LinkedHashSet<>(List.of("navigationPicker","showNavigationPicker","dismissNavigationPicker","showRecents","lambda$showRecents$50","showCommandPalette","lambda$showCommandPalette$59","showOutline","onStop","onConfigurationChanged"));
  Map<String,String> found=new LinkedHashMap<>();JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  try(StandardJavaFileManager fm=compiler.getStandardFileManager(null,null,null)){
   JavacTask task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjects(args[0]));
   CompilationUnitTree unit=task.parse().iterator().next();SourcePositions positions=Trees.instance(task).getSourcePositions();
   for(Tree declaration:unit.getTypeDecls())if(declaration instanceof ClassTree && ((ClassTree)declaration).getSimpleName().contentEquals("MainActivity"))for(Tree member:((ClassTree)declaration).getMembers()){
    String name=member instanceof MethodTree?((MethodTree)member).getName().toString():member instanceof VariableTree?((VariableTree)member).getName().toString():"";
    if(wanted.contains(name))found.put(name,source.substring((int)positions.getStartPosition(unit,member),(int)positions.getEndPosition(unit,member)));
   }
  }
  if(!found.keySet().equals(wanted)){wanted.removeAll(found.keySet());throw new AssertionError("Missing production members "+wanted);}
  Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* EXACT_MEMBERS */",String.join("\n",found.values())));
 }
}
