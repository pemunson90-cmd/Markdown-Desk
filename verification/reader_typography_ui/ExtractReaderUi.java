import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
public final class ExtractReaderUi {
 public static void main(String[] args)throws Exception {
  String source=Files.readString(Path.of(args[0]));Set<String> names=new LinkedHashSet<>(List.of("readReaderTypography","adjustReaderTypography","updateReaderTypography","refreshReaderTypography","addReaderTypographyControls"));Map<String,String> methods=new HashMap<>();String share=null;
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();JavacTask task=(JavacTask)compiler.getTask(null,null,null,List.of("-proc:none"),null,compiler.getStandardFileManager(null,null,null).getJavaFileObjects(args[0]));CompilationUnitTree unit=task.parse().iterator().next();SourcePositions positions=Trees.instance(task).getSourcePositions();
  for(Tree type:unit.getTypeDecls())if(type instanceof ClassTree)for(Tree member:((ClassTree)type).getMembers())if(member instanceof MethodTree){String name=((MethodTree)member).getName().toString();if(names.contains(name))methods.put(name,source.substring((int)positions.getStartPosition(unit,member),(int)positions.getEndPosition(unit,member)));if(name.equals("shareDocument"))share=source.substring((int)positions.getStartPosition(unit,member),(int)positions.getEndPosition(unit,member));}
  if(!methods.keySet().equals(names)){names.removeAll(methods.keySet());throw new AssertionError("Missing production typography UI methods: "+names);}
  if(args.length>3){if(share==null)throw new AssertionError("Missing actual HTML share action");Files.writeString(Path.of(args[4]),Files.readString(Path.of(args[3])).replace("/* SHARE_METHOD */",share));}
  Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* METHODS */",String.join("\n",methods.values())));
 }
}
