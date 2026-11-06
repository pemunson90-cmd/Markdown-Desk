import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Verification-only parser: preserve source text, without resolving Android dependencies. */
public final class ParsedJavaSource {
    public final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final String content;

    private ParsedJavaSource(CompilationUnitTree unit, SourcePositions positions, String content) {
        this.unit = unit;
        this.positions = positions;
        this.content = content;
    }

    public static ParsedJavaSource parse(Path source) throws IOException {
        String content = Files.readString(source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Source extraction requires a JDK compiler");
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null,
                    List.of("-proc:none"), null, manager.getJavaFileObjects(source.toFile()));
            CompilationUnitTree unit = task.parse().iterator().next();
            return new ParsedJavaSource(unit, Trees.instance(task).getSourcePositions(), content);
        }
    }

    /** Return original spelling and whitespace, never the AST's pretty-printed form. */
    public String raw(Tree node) {
        return content.substring(Math.toIntExact(positions.getStartPosition(unit, node)),
                Math.toIntExact(positions.getEndPosition(unit, node)));
    }
}
