import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import java.nio.file.*;

/** Behavioral checks for exact AST slices, independent of Android symbols. */
public final class ParsedJavaSourceCheck {
    public static void main(String[] args) throws Exception {
        Path fixture = Path.of(args[0]);
        String method = "void render() {\r\n        // preserve braces { } and Unicode 😀\r\n        unknown.call(\"λ\");\r\n    }";
        String field = "String title = \"😀\";";
        Files.writeString(fixture, "import missing.Android;\r\nclass Example {\r\n    " + field + "\r\n    " + method + "\r\n}\r\n");
        ParsedJavaSource parsed = ParsedJavaSource.parse(fixture);
        CompilationUnitTree unit = parsed.unit;
        ClassTree type = (ClassTree) unit.getTypeDecls().get(0);
        check(field.equals(parsed.raw(type.getMembers().get(0))), "field bytes");
        check(method.equals(parsed.raw(type.getMembers().get(1))), "method CRLF, comments and Unicode");
        String[] statement = {null};
        new TreeScanner<Void, Void>() {
            @Override public Void visitExpressionStatement(ExpressionStatementTree node, Void unused) {
                statement[0] = parsed.raw(node);
                return super.visitExpressionStatement(node, unused);
            }
        }.scan(unit, null);
        check("unknown.call(\"λ\");".equals(statement[0]), "nested statement slice");
        // Parsed source remains usable after its compiler file manager has closed.
        Files.writeString(fixture, "class Replacement {}");
        check(method.equals(parsed.raw(type.getMembers().get(1))), "source snapshot");
        System.out.println("PASS: exact field/method/nested slices, Unicode, CRLF, unresolved imports and source snapshot");
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
