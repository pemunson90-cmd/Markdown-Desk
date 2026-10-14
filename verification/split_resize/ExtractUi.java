import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
public final class ExtractUi {
 public static void main(String[] args) throws Exception {
  String source=Files.readString(Path.of(args[0]));
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  try(var fm=compiler.getStandardFileManager(null,null,null)) {
   JavacTask task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjects(args[0]));
   CompilationUnitTree unit=task.parse().iterator().next();
   SourcePositions pos=Trees.instance(task).getSourcePositions();
   StringBuilder methods=new StringBuilder(),gallery=new StringBuilder();
   for(Tree decl:unit.getTypeDecls()) if(decl instanceof ClassTree && ((ClassTree)decl).getSimpleName().contentEquals("MainActivity"))
    for(Tree member:((ClassTree)decl).getMembers()) if(member instanceof MethodTree) {
     MethodTree method=(MethodTree)member;
     if(List.of("updateTabsUi","rebuildBody","adjustSplitRatio").contains(method.getName().toString()))
      methods.append(source.substring((int)pos.getStartPosition(unit,member),(int)pos.getEndPosition(unit,member))).append('\n');
     if(method.getName().toString().equals("buildSplashRow")) new TreeScanner<Void,Void>() {
      public Void visitExpressionStatement(ExpressionStatementTree statement,Void ignored) {
       if(statement.getExpression() instanceof MethodInvocationTree) {
        MethodInvocationTree call=(MethodInvocationTree)statement.getExpression();
        if(call.getMethodSelect() instanceof MemberSelectTree) {
         MemberSelectTree select=(MemberSelectTree)call.getMethodSelect();
         if(select.getExpression().toString().equals("remove") && List.of("setText","setTooltipText","setContentDescription").contains(select.getIdentifier().toString()))
          gallery.append(source.substring((int)pos.getStartPosition(unit,statement),(int)pos.getEndPosition(unit,statement))).append('\n');
        }
       } return super.visitExpressionStatement(statement,ignored);
      }
     }.scan(method.getBody(),null);
    }
   if(gallery.length()==0) throw new AssertionError("No gallery remove construction found");
   Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* METHODS */",methods.toString().replace("android.view.accessibility.AccessibilityNodeInfo", "AccessibilityNodeInfo").replace("android.os.Bundle", "Bundle")).replace("/* GALLERY */",gallery));
  }
 }
}
