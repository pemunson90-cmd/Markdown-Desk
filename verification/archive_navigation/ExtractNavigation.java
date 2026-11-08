import com.sun.source.tree.*;
import java.nio.file.*;
public final class ExtractNavigation {
    public static void main(String[] args)throws Exception {
        ParsedJavaSource source=ParsedJavaSource.parse(Path.of(args[0]));
        String method=null;
        for(Tree type:source.unit.getTypeDecls())if(type instanceof ClassTree)
            for(Tree member:((ClassTree)type).getMembers())if(member instanceof MethodTree &&
                    ((MethodTree)member).getName().contentEquals("handleDeepLink"))method=source.raw(member);
        if(method==null)throw new AssertionError("Production navigation missing");
        Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1])).replace("/* METHOD */",method));
    }
}
