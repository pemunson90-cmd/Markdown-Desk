#!/usr/bin/env python3
"""Compile unchanged production watcher/scheduler bodies against host UI boundaries."""
import pathlib, re, sys

source = pathlib.Path(sys.argv[1]).read_text()

def method(signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end].replace('MainActivity.this', 'SchedulingHarness.this')

field = re.search(r'private final MarkdownSyntaxHighlighter.State syntaxHighlight[^;]*;', source).group()
methods = [method(signature) for signature in (
    'public void onTextChanged(CharSequence charSequence, int i, int i2, int i3)',
    'public void scheduleHighlight()', 'private void scheduleHighlightEdit()',
    'public /* synthetic */ void lambda$scheduleHighlight$69()')]
pathlib.Path(sys.argv[2]).write_text('''package com.example.markdowndesk;
import java.util.*;
final class SchedulingHarness {
 static final class Editor { SyntaxCheck.Text text; android.text.Editable getText(){return text;} }
 static final class DocumentTab { boolean dirty; }
 static final class Main {
  final List<Runnable> queued=new ArrayList<>();long delay;
  void removeCallbacks(Runnable r){queued.removeIf(item->item==r);}
  void postDelayed(Runnable r,long milliseconds){queued.add(r);delay=milliseconds;}
  void flush(){List<Runnable> tasks=new ArrayList<>(queued);queued.clear();for(Runnable r:tasks)r.run();}
 }
 final Editor editor=new Editor();final Main main=new Main();
 final List<DocumentTab> tabs=new ArrayList<>();int activeTab=-1;
 boolean suppressChanges,dirty,dark;Runnable highlightRunnable;
 void invalidateStatistics(){}void updateStatus(){}void updateTabsUi(){}
 void scheduleRecovery(){}void schedulePreview(){}void scheduleSession(){}
''' + field + '\n' + '\n'.join(methods) + '\n}\n')
