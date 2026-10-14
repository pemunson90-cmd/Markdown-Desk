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
            for (Tree type : unit.getTypeDecls()) {
                if (!(type instanceof ClassTree) || !((ClassTree) type).getSimpleName().contentEquals("MainActivity")) continue;
                for (Tree member : ((ClassTree) type).getMembers()) {
                    if (member instanceof MethodTree && ((MethodTree) member).getName().contentEquals("updateStatus")) {
                        matches.add(content.substring((int) positions.getStartPosition(unit, member),
                                (int) positions.getEndPosition(unit, member)));
                    }
                }
            }
            if (matches.size() != 1) throw new AssertionError("Expected one MainActivity.updateStatus, found " + matches.size());
            String template = Files.readString(Path.of(args[1]));
            Files.writeString(Path.of(args[2]), template.replace("/* EXTRACTED_STATUS */", matches.get(0)));
            System.out.println("Extracted exact MainActivity.updateStatus AST source");
        }
    }
}
