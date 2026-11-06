# Shared source extraction support

`ParsedJavaSource` is verification-only. It centralizes the javac parse/file-manager
lifecycle and exact AST source-position slicing used by the preview and status
harnesses. It parses without symbol analysis, so Android SDK classes need not be
present. It does not pretty-print or rewrite extracted code. Each extractor still
owns its member selection, dependency list, template markers and completeness
assertions; this utility does not discover missing harness dependencies.

Run the isolated behavioral test with:

```sh
bash verification/source_extraction/run.sh
```

A JDK with `javac` is required; `JAVA` and `JAVAC` can select existing binaries.
The test exercises exact fields, methods and nested statements, including CRLF,
comments, supplementary Unicode, unresolved imports and the captured source text
remaining stable after the input file changes. Generated fixtures stay under
`verification/build/source-extraction`.

When adopting this helper in another extractor, add it to that suite's javac
input list (including benchmark runners). Compare complete generated harnesses
byte-for-byte with the pre-change extractor before running their behavior tests.
Do not remove existing selection/completeness checks or use an AST's `toString()`
as replacement source text.

For this initial adoption, both current production harnesses and the preview
benchmark's historical `6630609` extraction were byte-identical before/after the
refactor. Production application sources, save/history/storage behavior, package
identity and assets were not changed.
