import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Extracts exact MainActivity method text using javac's parsed source positions. */
public final class ExtractMethods {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        var files = compiler.getStandardFileManager(null, null, null).getJavaFileObjects(source.toFile());
        JavacTask task = (JavacTask) compiler.getTask(null, null, null, Arrays.asList("-proc:none"), null, files);
        CompilationUnitTree unit = task.parse().iterator().next();
        SourcePositions positions = Trees.instance(task).getSourcePositions();
        String content = Files.readString(source);
        Map<String,String> methods = new HashMap<>();
        for (Tree declaration : unit.getTypeDecls()) {
            if (!(declaration instanceof ClassTree)) continue;
            ClassTree cls = (ClassTree) declaration;
            if (!cls.getSimpleName().contentEquals("MainActivity")) continue;
            for (Tree member : cls.getMembers()) {
                if (!(member instanceof MethodTree)) continue;
                MethodTree method = (MethodTree) member;
                String name = method.getName().toString();
                if (Arrays.asList("toggleFocusMode", "setMode", "updateModeChrome", "toggleEditorLock", "applyEditorLockState").contains(name)) {
                    int start = Math.toIntExact(positions.getStartPosition(unit, member));
                    int end = Math.toIntExact(positions.getEndPosition(unit, member));
                    methods.put(name, content.substring(start, end));
                }
            }
        }
        if (methods.size() != 5) throw new IllegalStateException("Expected five unique MainActivity methods; got " + methods.keySet());
        String template = Files.readString(Path.of(args[1]));
        StringBuilder inserted = new StringBuilder();
        for (String name : new String[]{"toggleFocusMode", "setMode", "updateModeChrome", "toggleEditorLock", "applyEditorLockState"}) {
            inserted.append("\n    ").append(methods.get(name)).append("\n");
        }
        Files.writeString(Path.of(args[2]), template.replace("/* EXTRACTED_METHODS */", inserted));
        System.out.println("Extracted five parsed MainActivity methods from " + source);
    }
}
