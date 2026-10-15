import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Parse and extract exact app method/constant text; never rewrite loader logic. */
public final class ExtractMethods {
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]); JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,Arrays.asList("-proc:none"),null,manager.getJavaFileObjects(source.toFile()));
            CompilationUnitTree unit=task.parse().iterator().next(); SourcePositions positions=Trees.instance(task).getSourcePositions();
            String content=Files.readString(source); Map<String,String> extracted=new HashMap<>();
            List<String> wanted=args.length>3 ? Arrays.asList("lambda$restoreSessionOrRecovery$41","pruneRedundantUntitledTabs","recoveryRepresented","showPendingProtectedRecovery") : Arrays.asList("MODE_EDIT","MODE_PREVIEW","MAX_TABS","lambda$restoreSessionOrRecovery$42","mergeNewerRecovery","protectLegacyRecovery","getHistoryController","lambda$writeRecoveryNow$46","lambda$writeRecoveryNow$45","lambda$clearRecoveryAsync$47");
            for(Tree declaration:unit.getTypeDecls()) {
                if(!(declaration instanceof ClassTree) || !((ClassTree)declaration).getSimpleName().contentEquals("MainActivity")) continue;
                for(Tree member:((ClassTree)declaration).getMembers()) {
                    String name=member instanceof MethodTree ? ((MethodTree)member).getName().toString()
                        : member instanceof VariableTree ? ((VariableTree)member).getName().toString() : "";
                    if(wanted.contains(name)) {
                        if(extracted.containsKey(name)) throw new IllegalStateException("Duplicate requested member: "+name);
                        int start=Math.toIntExact(positions.getStartPosition(unit,member)),end=Math.toIntExact(positions.getEndPosition(unit,member));
                        extracted.put(name,content.substring(start,end));
                    }
                }
            }
            if(extracted.size()!=wanted.size()) throw new IllegalStateException("Missing source members: "+extracted.keySet());
            StringBuilder inserted=new StringBuilder(); for(String name:wanted) inserted.append('\n').append(extracted.get(name)).append('\n');
            String template=Files.readString(Path.of(args[1]));
            if(!template.contains("/* EXTRACTED_MEMBERS */")) throw new IllegalStateException("Missing template marker");
            Files.writeString(Path.of(args[2]),template.replace("/* EXTRACTED_MEMBERS */",inserted));
            System.out.println("Extracted exact Activity recovery members via javac AST");
        }
    }
}
