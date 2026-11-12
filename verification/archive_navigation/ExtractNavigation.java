import com.sun.source.tree.*;
import java.nio.file.*;
import java.util.Set;

public final class ExtractNavigation {
    public static void main(String[] args)throws Exception {
        ParsedJavaSource source=ParsedJavaSource.parse(Path.of(args[0]));
        Set<String> required=Set.of("handleDeepLink","handleIncomingIndexedPassage",
                "indexedPassageBundle","readIndexedPassage","clearIndexedPassage",
                "openIndexedPassage","renderIndexedPassage");
        StringBuilder methods=new StringBuilder();
        int found=0;
        for(Tree type:source.unit.getTypeDecls())if(type instanceof ClassTree)
            for(Tree member:((ClassTree)type).getMembers())if(member instanceof MethodTree &&
                    required.contains(((MethodTree)member).getName().toString())){
                methods.append(source.raw(member)).append('\n');
                found++;
            }
        if(found!=required.size())throw new AssertionError("Production navigation missing");
        Files.writeString(Path.of(args[2]),Files.readString(Path.of(args[1]))
                .replace("/* METHODS */",methods.toString()));
    }
}
