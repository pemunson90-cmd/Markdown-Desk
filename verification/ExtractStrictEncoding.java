import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Insert actual pure DocumentIO codec and constants into a controlled resolver boundary. */
public final class ExtractStrictEncoding {
    public static void main(String[] args)throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjects(Path.of(args[0]).toFile()));
            CompilationUnitTree unit=task.parse().iterator().next();
            SourcePositions positions=Trees.instance(task).getSourcePositions();
            String source=Files.readString(Path.of(args[0]));Map<String,String> found=new HashMap<>();
            for(Tree type:unit.getTypeDecls()) if(type instanceof ClassTree&&((ClassTree)type).getSimpleName().contentEquals("DocumentIO"))
                for(Tree member:((ClassTree)type).getMembers()) {
                    String name=member instanceof MethodTree?((MethodTree)member).getName().toString():member instanceof VariableTree?((VariableTree)member).getName().toString():"";
                    if(List.of("encodeStrict","MAX_DOCUMENT_BYTES","UTF8_BOM").contains(name)) {
                        String text=source.substring((int)positions.getStartPosition(unit,member),(int)positions.getEndPosition(unit,member));
                        if(found.put(name,text)!=null)throw new IllegalStateException("Duplicate codec member");
                    }
                }
            if(found.size()!=3)throw new IllegalStateException("Missing pure codec source");
            Path destination=Path.of(args[1]);String harness=Files.readString(destination);
            String marker="/* EXTRACTED_STRICT_ENCODING */";
            if(!harness.contains(marker))throw new IllegalStateException("Missing codec marker");
            Files.writeString(destination,harness.replace(marker,found.get("MAX_DOCUMENT_BYTES")+"\n"+found.get("UTF8_BOM")+"\n"+found.get("encodeStrict")));
        }
    }
}
