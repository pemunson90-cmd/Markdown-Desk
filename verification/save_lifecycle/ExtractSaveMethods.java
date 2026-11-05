import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Extract exact save lifecycle text via source positions, without rewriting method bodies. */
public final class ExtractSaveMethods {
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]); JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,Arrays.asList("-proc:none"),null,manager.getJavaFileObjects(source.toFile()));
            CompilationUnitTree unit=task.parse().iterator().next(); SourcePositions positions=Trees.instance(task).getSourcePositions();
            String content=Files.readString(source); Map<String,String> extracted=new HashMap<>();
            Set<String> signatures=new HashSet<>();
            List<String> wanted=Arrays.asList("MODE_EDIT","MODE_PREVIEW","REQUEST_OPEN","REQUEST_CREATE","REQUEST_SPLASH_IMPORT","REQUEST_PROTECTED_COPY",
                "onActivityResult","captureActiveTab","findTabById","saveInternal","lambda$saveInternal$35","lambda$saveInternal$33","lambda$saveInternal$34","getProtectionStore","getHistoryController","invalidateHistory","restoreProtectedCopy","lambda$saveInternal$32","resumeConflictSave","submitCapturedSave","showProtectedSaves","launchProtectedCopy","protectedSavesRequest","protectedSavesSession","ProtectedSavesSession","invalidateProtectedSaves","protectedSavesCurrent","showProtectedSavesPage","showProtectedSaveActions","presentProtectedSavesDialog","historyDialogBuilder","workspaceRootRows","workspaceDialogShown");
            for(Tree declaration:unit.getTypeDecls()) {
                if(!(declaration instanceof ClassTree) || !((ClassTree)declaration).getSimpleName().contentEquals("MainActivity")) continue;
                for(Tree member:((ClassTree)declaration).getMembers()) {
                    String name=member instanceof MethodTree ? ((MethodTree)member).getName().toString()
                        : member instanceof VariableTree ? ((VariableTree)member).getName().toString() : member instanceof ClassTree ? ((ClassTree)member).getSimpleName().toString() : "";
                    if(wanted.contains(name)) {
                        String signature=name;
                        if(member instanceof MethodTree){
                            StringJoiner parameters=new StringJoiner(",","(",")");
                            for(VariableTree parameter:((MethodTree)member).getParameters())parameters.add(parameter.getType().toString());
                            signature+=parameters.toString();
                        }
                        if(!signatures.add(signature))throw new IllegalStateException("Duplicate source signature: "+signature);
                        if("saveInternal".equals(name) && !Arrays.asList("saveInternal(boolean)","saveInternal(boolean,Uri)").contains(signature))
                            throw new IllegalStateException("Unexpected save request overload: "+signature);
                        if(extracted.containsKey(name) && !"saveInternal".equals(name))throw new IllegalStateException("Unexpected source overload: "+name);
                        String text=content.substring(Math.toIntExact(positions.getStartPosition(unit,member)),Math.toIntExact(positions.getEndPosition(unit,member)));
                        extracted.merge(name,text,(previous,next)->previous+"\n"+next);
                    }
                }
            }
            if(extracted.size()!=wanted.size())throw new IllegalStateException("Missing requested members: "+extracted.keySet());
            if(!signatures.contains("saveInternal(boolean)"))throw new IllegalStateException("Missing ordinary saveInternal(boolean) wrapper");
            String template=Files.readString(Path.of(args[1])); if(!template.contains("/* EXTRACTED_MEMBERS */"))throw new IllegalStateException("Missing marker");
            StringBuilder inserted=new StringBuilder(); for(String name:wanted)inserted.append('\n').append(extracted.get(name)).append('\n');
            Files.writeString(Path.of(args[2]),template.replace("/* EXTRACTED_MEMBERS */",inserted));
            System.out.println("Extracted exact picker handler, capture/lookup, save worker/callbacks and constants via javac AST");
        }
    }
}
