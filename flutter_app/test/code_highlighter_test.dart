import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/markdown/code_highlighter.dart';

void main() {
  test('common fence languages produce tokens without changing code', () {
    const cases = {
      'c': '#include <stdio.h>\nint main() { return 0; }',
      'C++': 'class Box { public: int value = 42; };',
      'Python': 'def hello(name):\n    return "hi" # greeting',
      'Java': 'public class Main { public static void main(String[] args) {} }',
      'PHP': '<?php function hello() { return "hi"; }',
      'JavaScript': 'const answer = 42; // comment',
      'html': '<h2 class="title">Hello</h2>',
      'css': '.title { color: red; margin: 12px; }',
      'xml': '<?xml version="1.0"?><item id="1"/>',
      'yaml': 'name: QuikPad\nenabled: true',
      'shell': '#!/bin/bash\necho "hello"',
      'typescript': 'interface User { name: string; }',
      'json': '{"name": "QuikPad", "count": 3}',
      'sql': 'SELECT * FROM users WHERE id = 1;',
      'rust': 'fn main() { let x = 3; }',
      'go': 'package main\nfunc main() { return }',
      'kotlin': 'fun main() { val name = "hi" }',
      'swift': 'func greet() { let name = "hi" }',
      'c#': 'public class User { public int Id = 1; }',
      'dart': 'void main() { final name = "hi"; }',
    };
    for (final entry in cases.entries) {
      expect(CodeHighlighter.supports(entry.key), isTrue, reason: entry.key);
      final tokens = CodeHighlighter.slice(entry.value, language: entry.key);
      expect(tokens.map((t) => t.text).join(), entry.value);
      expect(tokens.any((t) => t.kind != null), isTrue, reason: entry.key);
    }
  });

  test('multiline comments retain their token class across render slices', () {
    final code = '/*${' comment ' * 500}\nend */\nconst x = 2;';
    final tokens = CodeHighlighter.slice(code, language: 'javascript', start: 2050, end: 3000);
    expect(tokens.every((token) => token.kind == 'comment'), isTrue);
    expect(tokens.map((t) => t.text).join(), code.substring(2050, 3000));
  });

  test('unknown languages, detected languages and huge blocks remain lossless', () {
    const unknown = '😀 <not a supported grammar> "unchanged"';
    expect(CodeHighlighter.slice(unknown, language: 'not-a-language').single.text, unknown);
    expect(CodeHighlighter.detect('def hello():\n  return True'), 'python');
    final source = 'const answer = 42;\n' * 9000;
    final tokens = CodeHighlighter.slice(source, language: 'js', start: 32000, end: 34000);
    expect(tokens.map((t) => t.text).join(), source.substring(32000, 34000));
  });
  test('aliases and friendly labels cover common fence names', () {
    expect(CodeHighlighter.displayName('C', ''), 'C');
    expect(CodeHighlighter.displayName('c++', ''), 'C++');
    expect(CodeHighlighter.displayName('html', ''), 'HTML');
    expect(CodeHighlighter.displayName('shell', ''), 'Shell');
    expect(CodeHighlighter.displayName('c#', ''), 'C#');
    expect(CodeHighlighter.displayName('yml', ''), 'YAML');
    expect(CodeHighlighter.displayName(null, 'def hello():'), 'Python');
    expect(CodeHighlighter.displayName('language-python', ''), 'Python');
    for (final alias in ['sh', 'shell', 'toml', 'vue', 'docker', 'proto', 'objc']) {
      expect(CodeHighlighter.supports(alias), isTrue, reason: alias);
    }
  });

  test('additional common formats tokenize losslessly', () {
    const cases = {
      'toml': 'title = "QuikPad"\n[owner]\nname = "yg"',
      'dockerfile': 'FROM alpine:3.20\nRUN echo hi',
      'makefile': 'all:\n\tcc main.c -o app',
      'objective-c': '@interface Box : NSObject\n@end',
      'protobuf': 'message User { string name = 1; }',
      'graphql': 'query User { user(id: 1) { name } }',
      'vue': '<template><div>Hello</div></template>',
      'diff': '@@ -1 +1 @@\n-old\n+new',
      'ini': '[core]\nname=QuikPad',
    };
    for (final entry in cases.entries) {
      final tokens = CodeHighlighter.slice(entry.value, language: entry.key);
      expect(tokens.map((t) => t.text).join(), entry.value, reason: entry.key);
    }
  });

}
