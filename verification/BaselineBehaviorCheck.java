package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;

/** Independent host fixtures; these do not substitute for SAF or device tests. */
public class BaselineBehaviorCheck {
    private static int checks;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        SearchEngine.Options literal = new SearchEngine.Options(false, false, false);
        List<SearchEngine.Match> matches = SearchEngine.findAll("Cat\r\ncat catfish", "cat", literal);
        check(matches.size() == 3, "literal case-insensitive matches");
        check(matches.get(1).start == 5 && matches.get(1).end == 8 && matches.get(1).line == 2, "CRLF offsets and line numbers");
        check(SearchEngine.findAll("Cat cat catfish", "cat", new SearchEngine.Options(true, true, false)).size() == 1, "case and whole-word options");
        check(SearchEngine.findAll("é élan", "é", new SearchEngine.Options(false, true, false)).size() == 1, "Unicode word boundaries");
        check(SearchEngine.findAll("a.b axb", "a.b", literal).size() == 1, "literal metacharacters");
        SearchEngine.Replacement replacement = SearchEngine.replaceAll("a a", "a", "$1\\x", literal);
        check(replacement.count == 2 && replacement.text.equals("$1\\x $1\\x"), "literal replacement dollar and backslash");
        replacement = SearchEngine.replaceAll("x12 x34", "x([0-9]+)", "$1!", new SearchEngine.Options(true, false, true));
        check(replacement.count == 2 && replacement.text.equals("12! 34!"), "regex capture replacement");
        check(SearchEngine.matchesSelection("cat", "cat", literal) && !SearchEngine.matchesSelection("catfish", "cat", literal), "full selection match");
        check(SearchEngine.findAll("cat", "", literal).isEmpty(), "empty query");
        try {
            SearchEngine.findAll("cat", "[", new SearchEngine.Options(true, false, true));
            throw new AssertionError("invalid regex must report an error");
        } catch (PatternSyntaxException expected) { checks++; }
        check(EditorTools.wordCount("Hello, café 123!") == 3, "word statistics");
        check(EditorTools.lineCount("a\r\nb\n") == 3 && EditorTools.lineCount("") == 0, "line statistics");
        check(EditorTools.sha256("abc".getBytes(StandardCharsets.UTF_8)).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "independent SHA-256 fixture");
        Path dir = Files.createTempDirectory("markdown-baseline-atomic-");
        try {
            byte[] bytes = "\ufeff# café\r\n  trailing  \r\n".getBytes(StandardCharsets.UTF_8);
            Path staged = dir.resolve("staged"), destination = dir.resolve("checkpoint");
            Files.writeString(destination, "previous"); Files.write(staged, bytes);
            AtomicCommit.replace(staged.toFile(), destination.toFile());
            check(java.util.Arrays.equals(Files.readAllBytes(destination), bytes) && !Files.exists(staged), "app-private atomic replacement preserves exact bytes");
            Path other = Files.createDirectory(dir.resolve("other"));
            staged = other.resolve("staged"); Files.writeString(staged, "new");
            try {
                AtomicCommit.replace(staged.toFile(), destination.toFile());
                throw new AssertionError("cross-directory checkpoint must be rejected");
            } catch (java.io.IOException expected) {
                check(java.util.Arrays.equals(Files.readAllBytes(destination), bytes) && Files.readString(staged).equals("new"), "rejected checkpoint leaves both inputs intact");
            }
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new)) Files.delete(p);
            }
        }
        System.out.println("PASS independent host behavior fixtures: " + checks + " search/replace, statistics/hash and app-private atomic-file checks; NOT SAF/device acceptance");
    }
}
