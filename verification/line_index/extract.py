#!/usr/bin/env python3
"""Extract the production editor callback and lookup unchanged into a host shell."""
import pathlib, re, sys
source = pathlib.Path(sys.argv[1]).read_text()
name = sys.argv[3]
methods = []
for method in ('onTextChanged', 'logicalLineForOffset'):
    match = re.search(r'(?:protected|private)\s+(?:void|int)\s+' + method + r'\(', source)
    start = match.start()
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    methods.append(source[start:end])
field = re.search(r'private [^\n;]+ logicalLineStarts[^\n;]*;', source).group()
pathlib.Path(sys.argv[2]).write_text('package com.example.markdowndesk;\n'
    + 'class ' + name + ' extends EditorCallbackSurface {\n' + field + '\n'
    + '\n'.join(methods) + '\nint line(int offset) { return logicalLineForOffset(offset); }\n}\n')
