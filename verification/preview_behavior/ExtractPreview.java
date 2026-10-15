import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Parse production source; execute exact preview methods and watcher in host boundaries. */
public final class ExtractPreview {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        String content = Files.readString(source);
        boolean baseline = args.length > 3;
        Set<String> names = baseline ? Set.of("schedulePreview", "renderPreview")
                : Set.of("schedulePreview", "renderPreview", "invalidatePreview", "previewDocumentId",
                        "acceptPreview", "stopPreview", "onDestroy", "previewPageHtml", "isCurrentPreviewPage", "previewScrollIsCurrent", "isCurrentSourceNavigation", "retireExplicitSourceNavigation", "retireStaleSourceNavigation", "retirePreviewSynchronization");
        List<String> methods = new ArrayList<>(), fields = new ArrayList<>(), watchers = new ArrayList<>();
        String[] layout = {""};
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, null, List.of("-proc:none"), null,
                    fm.getJavaFileObjects(source.toFile()));
            CompilationUnitTree unit = task.parse().iterator().next();
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            java.util.function.Function<Tree, String> raw = node -> content.substring(
                    (int) positions.getStartPosition(unit, node), (int) positions.getEndPosition(unit, node));
            for (Tree type : unit.getTypeDecls()) {
                if (!(type instanceof ClassTree)) continue;
                for (Tree member : ((ClassTree) type).getMembers()) {
                    if (member instanceof MethodTree) {
                        MethodTree method = (MethodTree) member;
                        if (names.contains(method.getName().toString())) methods.add(raw.apply(member));
                        if (!baseline && method.getName().contentEquals("rebuildBody")) {
                            layout[0] = raw.apply(method.getBody().getStatements().get(0));
                            if (!layout[0].equals("invalidatePreview();")) throw new AssertionError("Layout must invalidate first");
                        }
                    }
                    if (!baseline && member instanceof ClassTree && Set.of("PreviewPage", "SourceNavigation").contains(((ClassTree) member).getSimpleName().toString())) fields.add(raw.apply(member));
                    if (member instanceof VariableTree && Set.of("renderIo", "previewRenderToken", "previewWork",
                            "previewRevision", "previewStopped", "previewPage", "previewProbePending", "previewProbeRunning", "previewProbeSequence", "previewScrollOperation", "previewScrollRelease", "previewEditorScroll", "sourceNavigationRevision", "sourceRouteRevision", "explicitNavigationSequence", "pendingOutlineNavigation", "sourceEditorNavigation", "sourceEditorScroll").contains(((VariableTree) member).getName().toString())) {
                        fields.add(raw.apply(member));
                    }
                }
            }
            new TreeScanner<Void, Void>() {
                @Override public Void visitNewClass(NewClassTree node, Void unused) {
                    if (node.getIdentifier().toString().equals("TextWatcher")) watchers.add(raw.apply(node));
                    return super.visitNewClass(node, unused);
                }
            }.scan(unit, null);
        }
        if (methods.size() != names.size() || watchers.isEmpty()) throw new AssertionError("Missing production extraction");
        String out = Files.readString(Path.of(args[1])).replace("/* METHODS */", String.join("\n", methods))
                .replace("/* FIELDS */", String.join("\n", fields)).replace("/* WATCHER */", watchers.get(0))
                .replace("/* LAYOUT */", layout[0]).replace("MainActivity.this", "PreviewHarness.this");
        Files.writeString(Path.of(args[2]), out);
    }
}
