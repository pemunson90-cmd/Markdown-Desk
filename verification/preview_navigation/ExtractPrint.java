import com.sun.source.tree.*;import com.sun.source.util.*;import javax.tools.*;import java.nio.file.*;import java.util.*;
public final class ExtractPrint {
 public static void main(String[] args)throws Exception{
  String src=Files.readString(Path.of(args[0]));List<String> found=new ArrayList<>();Set<String> names=new HashSet<>();
  var compiler=ToolProvider.getSystemJavaCompiler();try(var fm=compiler.getStandardFileManager(null,null,null)){
   var task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjects(args[0]));var unit=task.parse().iterator().next();var pos=Trees.instance(task).getSourcePositions();
   for(Tree decl:unit.getTypeDecls())if(decl instanceof ClassTree)for(Tree m:((ClassTree)decl).getMembers())if(m instanceof MethodTree){String name=((MethodTree)m).getName().toString();if(name.equals("printDocument")||name.equals("onDestroy")){found.add(src.substring((int)pos.getStartPosition(unit,m),(int)pos.getEndPosition(unit,m)));names.add(name);}}
  }if(names.size()!=2)throw new AssertionError(names);
  Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* EXACT_MEMBERS */",String.join("\n",found)));
  String io=Files.readString(Path.of(args[3]));var matcher=java.util.regex.Pattern.compile("(?:public )?static final int MAX_DOCUMENT_BYTES = ([^;]+);").matcher(io);if(!matcher.find())throw new AssertionError("missing real size cap");Files.writeString(Path.of(args[4]),"package com.example.markdowndesk;final class DocumentIO {static final int MAX_DOCUMENT_BYTES = "+matcher.group(1)+";}");
 }
}
