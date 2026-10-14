import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Executes the exact live refresh and its selection helper, parsed from production. */
public final class ExtractAppearance {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of(args[0]));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        var files = compiler.getStandardFileManager(null, null, null).getJavaFileObjects(args[0]);
        JavacTask task = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null, files);
        CompilationUnitTree unit = task.parse().iterator().next();
        SourcePositions positions = Trees.instance(task).getSourcePositions();
        StringBuilder methods = new StringBuilder();
        boolean foundRefresh = false;
        for (Tree declaration : unit.getTypeDecls()) {
            if (!(declaration instanceof ClassTree)) continue;
            for (Tree member : ((ClassTree) declaration).getMembers()) {
                if (!(member instanceof MethodTree)) continue;
                String name = ((MethodTree) member).getName().toString();
                if (name.equals("refreshAppearance") || name.equals("setAppearanceSelection")) {
                    methods.append(source.substring((int) positions.getStartPosition(unit, member),
                        (int) positions.getEndPosition(unit, member))).append('\n');
                    foundRefresh |= name.equals("refreshAppearance");
                }
            }
        }
        if (!foundRefresh) throw new AssertionError("Missing production refreshAppearance");
        Files.writeString(Path.of(args[2]), Files.readString(Path.of(args[1])).replace("/* METHODS */", methods));
    }
}
