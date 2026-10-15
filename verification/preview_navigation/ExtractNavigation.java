import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Copy exact Activity members, including its nested WebViewClient; no body substitutions. */
public final class ExtractNavigation {
    public static void main(String[] args) throws Exception {
        String source=Files.readString(Path.of(args[0]));
        Set<String> wanted=new LinkedHashSet<>(Arrays.asList("AnonymousClass2", "renderIo", "previewWork",
            "schedulePreview", "invalidatePreview", "previewDocumentId", "renderPreview", "acceptPreview", "stopPreview",
            "lambda$buildInterface$7", "captureActiveTab", "schedulePreviewSyncFromEditor",
            "lambda$schedulePreviewSyncFromEditor$72", "scheduleEditorSyncFromPreview", "lambda$scheduleEditorSyncFromPreview$75",
            "parseJsInt", "scrollEditorToOffset", "lambda$scrollEditorToOffset$76"));
        boolean baseline=args.length>3 && args[3].equals("--baseline");
        boolean printing=args.length>3;
        if(printing)wanted.addAll(List.of("printDocument", "lambda$printDocument$67"));
        if(baseline)wanted.addAll(List.of("lambda$schedulePreviewSyncFromEditor$70", "lambda$schedulePreviewSyncFromEditor$71", "lambda$scheduleEditorSyncFromPreview$73", "lambda$scheduleEditorSyncFromPreview$74"));
        else wanted.addAll(List.of("PreviewPage", "previewPage", "previewProbePending", "previewProbeRunning", "previewProbeSequence", "previewScrollIsCurrent", "previewScrollOperation", "previewScrollRelease", "previewEditorScroll", "previewPageHtml", "isCurrentPreviewPage", "previewDomGuard", "acknowledgePreviewPage", "beginPreviewScroll", "releasePreviewScroll"));
        Map<String,String> found=new LinkedHashMap<>();
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(null,null,null)) {
            JavacTask task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjects(args[0]));
            CompilationUnitTree unit=task.parse().iterator().next();SourcePositions positions=Trees.instance(task).getSourcePositions();
            for(Tree declaration:unit.getTypeDecls())if(declaration instanceof ClassTree && ((ClassTree)declaration).getSimpleName().contentEquals("MainActivity"))
                for(Tree member:((ClassTree)declaration).getMembers()) {
                    String name=member instanceof MethodTree?((MethodTree)member).getName().toString():member instanceof VariableTree?((VariableTree)member).getName().toString():member instanceof ClassTree?((ClassTree)member).getSimpleName().toString():"";
                    if(wanted.contains(name) && found.put(name,source.substring((int)positions.getStartPosition(unit,member),(int)positions.getEndPosition(unit,member)))!=null)throw new AssertionError("Duplicate "+name);
                }
        }
        if(!found.keySet().equals(wanted)){wanted.removeAll(found.keySet());throw new AssertionError("Missing source members: "+wanted);}
        Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* EXACT_MEMBERS */",String.join("\n",found.values())).replace("/* PRINT_ACTION */",printing ? "printDocument();" : "throw new AssertionError(\"Print action is tested in run_print.sh\");"));
        System.out.println("Extracted "+found.size()+" exact production members (including WebViewClient)");
    }
}
