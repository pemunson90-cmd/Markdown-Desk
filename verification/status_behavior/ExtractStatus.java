import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Insert the exact parsed production method into a small host class. */
public final class ExtractStatus {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        String content = Files.readString(source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, null, List.of("-proc:none"), null,
                    fm.getJavaFileObjects(source.toFile()));
            CompilationUnitTree unit = task.parse().iterator().next();
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            List<String> matches = new ArrayList<>();
            Set<String> methods = Set.of("updateStatus", "invalidateStatistics", "statisticsDocumentId",
                    "requestStatistics", "acceptStatistics", "stopStatistics", "onDestroy");
            List<String> fields = new ArrayList<>();
            for (Tree type : unit.getTypeDecls()) {
                if (!(type instanceof ClassTree) || !((ClassTree) type).getSimpleName().contentEquals("MainActivity")) continue;
                for (Tree member : ((ClassTree) type).getMembers()) {
                    if (member instanceof VariableTree && (((VariableTree) member).getName().toString().startsWith("statistics") || ((VariableTree) member).getName().contentEquals("sourceNavigationRevision"))) {
                        fields.add(content.substring((int) positions.getStartPosition(unit, member),
                                (int) positions.getEndPosition(unit, member)));
                    }
                    if (member instanceof MethodTree && methods.contains(((MethodTree) member).getName().toString())) {
                        matches.add(content.substring((int) positions.getStartPosition(unit, member),
                                (int) positions.getEndPosition(unit, member)));
                    }
                }
            }
            if (matches.size() != methods.size()) throw new AssertionError("Expected all status/lifecycle methods, found " + matches.size());
            List<String> watchers = new ArrayList<>();
            new TreeScanner<Void, Void>() {
                @Override public Void visitNewClass(NewClassTree node, Void unused) {
                    if (node.getIdentifier().toString().equals("TextWatcher")) {
                        watchers.add(content.substring((int) positions.getStartPosition(unit, node),
                                (int) positions.getEndPosition(unit, node)));
                    }
                    return super.visitNewClass(node, unused);
                }
            }.scan(unit, null);
            if (watchers.isEmpty()) throw new AssertionError("No production TextWatcher");
            String template = Files.readString(Path.of(args[1]));
            Files.writeString(Path.of(args[2]), template.replace("/* EXTRACTED_STATUS */", String.join("\n", matches))
                    .replace("/* EXTRACTED_FIELDS */", String.join("\n", fields))
                    .replace("/* EXTRACTED_WATCHER */", watchers.get(0).replace("MainActivity.this", "StatusHarness.this")));
            System.out.println("Extracted exact status methods/fields, TextWatcher and onDestroy AST source");
        }
    }
}
