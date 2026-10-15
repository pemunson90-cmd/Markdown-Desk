import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Copy exact Activity members, including its nested WebViewClient; no body substitutions. */
public final class ExtractOutline {
    public static void main(String[] args) throws Exception {
        String source=Files.readString(Path.of(args[0]));
        Set<String> wanted=new LinkedHashSet<>(Arrays.asList("AnonymousClass2", "renderIo", "previewWork",
            "schedulePreview", "invalidatePreview", "previewDocumentId", "renderPreview", "acceptPreview", "stopPreview",
            "lambda$buildInterface$7", "captureActiveTab", "schedulePreviewSyncFromEditor",
            "lambda$schedulePreviewSyncFromEditor$72", "scheduleEditorSyncFromPreview", "lambda$scheduleEditorSyncFromPreview$75",
            "parseJsInt", "scrollEditorToOffset", "lambda$scrollEditorToOffset$76"));
        wanted.addAll(List.of("PreviewPage", "previewPage", "previewProbePending", "previewProbeRunning", "previewProbeSequence", "previewScrollIsCurrent", "previewScrollOperation", "previewScrollRelease", "previewEditorScroll", "previewPageHtml", "isCurrentPreviewPage", "previewDomGuard", "acknowledgePreviewPage", "beginPreviewScroll", "releasePreviewScroll", "showOutline", "scrollPreviewToSourceOffset", "jumpToSourceOffset", "handlePreviewUri", "setMode", "editorOffsetAtViewport", "findNext", "lambda$showSearchMatches$88"));
        wanted.addAll(List.of("SourceNavigation", "sourceNavigationRevision", "sourceRouteRevision", "explicitNavigationSequence", "pendingOutlineNavigation", "sourceEditorNavigation", "sourceEditorScroll", "isCurrentSourceNavigation", "retireExplicitSourceNavigation", "retireStaleSourceNavigation", "retirePreviewSynchronization", "applyPendingOutlineNavigation"));
        List<String> watchers=new ArrayList<>();
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
            new TreeScanner<Void,Void>() { @Override public Void visitNewClass(NewClassTree node,Void unused){
                if(node.getIdentifier().toString().equals("TextWatcher")) watchers.add(source.substring((int)positions.getStartPosition(unit,node),(int)positions.getEndPosition(unit,node)));
                return super.visitNewClass(node,unused);
            }}.scan(unit,null);
        }
        if(watchers.isEmpty())throw new AssertionError("Missing actual editor watcher");
        if(!found.keySet().equals(wanted)){wanted.removeAll(found.keySet());throw new AssertionError("Missing source members: "+wanted);}
        Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* EXACT_MEMBERS */",String.join("\n",found.values())).replace("/* WATCHER */",watchers.get(0)));
        System.out.println("Extracted "+found.size()+" exact production members (including WebViewClient)");
    }
}
