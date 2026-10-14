import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Copies actual helper bodies and Appearance's four size-control statements. */
public final class ExtractLabels {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of(args[0]));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, List.of("-proc:none"), null, fm.getJavaFileObjects(args[0]));
            CompilationUnitTree unit = task.parse().iterator().next();
            for (var diagnostic : diagnostics.getDiagnostics())
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(diagnostic.toString());
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            StringBuilder methods = new StringBuilder(), controls = new StringBuilder();
            Map<String,Integer> counts = new HashMap<>();
            int[] statements = {0};
            for (Tree declaration : unit.getTypeDecls()) {
                if (!(declaration instanceof ClassTree) || !((ClassTree) declaration).getSimpleName().contentEquals("MainActivity")) continue;
                for (Tree member : ((ClassTree) declaration).getMembers()) {
                    if (!(member instanceof MethodTree)) continue;
                    MethodTree method = (MethodTree) member;
                    String name = method.getName().toString();
                    if (List.of("createTopButton", "createPillButton", "appearanceButton").contains(name)) {
                        methods.append(source.substring((int) positions.getStartPosition(unit, member), (int) positions.getEndPosition(unit, member))).append('\n');
                        counts.merge(name, 1, Integer::sum);
                    }
                    if (!name.equals("showAppearance")) continue;
                    new TreeScanner<Void,Void>() {
                        @Override public Void visitExpressionStatement(ExpressionStatementTree statement, Void ignored) {
                            if (statement.getExpression() instanceof MethodInvocationTree) {
                                MethodInvocationTree call = (MethodInvocationTree) statement.getExpression();
                                if (call.getMethodSelect() instanceof MemberSelectTree) {
                                    MemberSelectTree target = (MemberSelectTree) call.getMethodSelect();
                                    String receiver = target.getExpression().toString();
                                    if (target.getIdentifier().contentEquals("addView") && (receiver.equals("sourceRow") || receiver.equals("readRow"))) {
                                        controls.append(source.substring((int) positions.getStartPosition(unit, statement), (int) positions.getEndPosition(unit, statement))).append('\n');
                                        statements[0]++;
                                    }
                                }
                            }
                            return super.visitExpressionStatement(statement, ignored);
                        }
                    }.scan(method.getBody(), null);
                }
            }
            if (counts.getOrDefault("createTopButton", 0) != 1 || counts.getOrDefault("createPillButton", 0) != 1
                    || counts.getOrDefault("appearanceButton", 0) < 1 || counts.get("appearanceButton") > 2 || statements[0] != 4)
                throw new AssertionError("Expected helper methods and four actual Appearance size controls: " + counts + "/" + statements[0]);
            String template = Files.readString(Path.of(args[1]));
            Files.writeString(Path.of(args[2]), template.replace("/* METHODS */", methods).replace("/* CONTROLS */", controls));
        }
    }
}
