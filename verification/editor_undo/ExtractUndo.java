import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Copies actual parsed production bodies; missing APIs fail before harness compilation. */
public final class ExtractUndo {
 static String extract(Path source,String className,String... names)throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();String content=Files.readString(source);
  JavacTask task=(JavacTask)compiler.getTask(null,null,null,Arrays.asList("-proc:none"),null,compiler.getStandardFileManager(null,null,null).getJavaFileObjects(source.toFile()));
  CompilationUnitTree unit=task.parse().iterator().next();SourcePositions positions=Trees.instance(task).getSourcePositions();Map<String,String> found=new HashMap<>();
  for(Tree decl:unit.getTypeDecls())if(decl instanceof ClassTree&&((ClassTree)decl).getSimpleName().contentEquals(className))for(Tree member:((ClassTree)decl).getMembers()){
   String name=member instanceof MethodTree?((MethodTree)member).getName().toString():member instanceof ClassTree?((ClassTree)member).getSimpleName().toString():member instanceof VariableTree?((VariableTree)member).getName().toString():"";
   if(Arrays.asList(names).contains(name))found.put(name,content.substring((int)positions.getStartPosition(unit,member),(int)positions.getEndPosition(unit,member)));
  }
  StringBuilder result=new StringBuilder();for(String name:names){if(!found.containsKey(name))throw new AssertionError("Missing production "+className+"."+name);result.append(found.get(name)).append('\n');}return result.toString();
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);String template=Files.readString(Path.of(args[1]));
  String kinetic=extract(root.resolve("KineticEditText.java"),"KineticEditText","HistoryActionListener","historyActionListener","setHistoryActionListener","performNativeHistoryAction","onTextContextMenuItem");
  String main=extract(root.resolve("MainActivity.java"),"MainActivity","configureEditorHistoryActions","runEditorHistoryAction","onKeyShortcut","canMutate","ensureEditor","continueMarkdownLine");
  Files.writeString(Path.of(args[2]),template.replace("/* KINETIC_METHODS */",kinetic).replace("/* ACTIVITY_METHODS */",main));
 }
}
