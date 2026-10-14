import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Generates a harness around the current production method, without duplicating it. */
public final class ExtractStyle {
    public static void main(String[] args) throws Exception {
        String source=Files.readString(Path.of(args[0]));
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm=compiler.getStandardFileManager(null,null,null)) {
            JavacTask task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjects(args[0]));
            CompilationUnitTree unit=task.parse().iterator().next();
            SourcePositions positions=Trees.instance(task).getSourcePositions();
            String[] method={null};
            new TreeScanner<Void,Void>() {
                public Void visitMethod(MethodTree tree,Void ignored) {
                    if (tree.getName().contentEquals("styleButton")) {
                        method[0]=source.substring((int)positions.getStartPosition(unit,tree),(int)positions.getEndPosition(unit,tree));
                    }
                    return super.visitMethod(tree,ignored);
                }
            }.scan(unit,null);
            if (method[0]==null) throw new AssertionError("styleButton method absent");
            Files.writeString(Path.of(args[1]), "package com.example.markdowndesk;\n"+
                "class StyleHarness { static final int MODE_EDIT=0; UiTheme.Palette palette; int dp(int value) {return value;}\n"+
                "static class Button { int textColor; Object background; void setTextColor(int c){textColor=c;} void setBackground(Object d){background=d;} }\n"+
                "void apply(Button b,boolean selected,boolean filled){styleButton(b,selected,filled);}\n"+method[0]+"\n}\n");
        }
    }
}
