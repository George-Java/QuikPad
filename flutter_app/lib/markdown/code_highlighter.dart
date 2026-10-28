import 'package:highlight/highlight.dart' as hl;
import 'package:highlight/languages/all.dart';

class CodeToken {
  const CodeToken(this.text, this.kind);
  final String text;
  final String? kind;
}

/// Bounded cache and bounded tokenizer input keep scrolling independent of file
/// length. Normal fences are tokenized as a whole to retain multiline states.
class CodeHighlighter {
  static const windowSize = 32768;
  static final _cache = <(String, String), List<CodeToken>>{};
  static int _cachedCharacters = 0;
  static const aliases = {
    'c': 'cpp', 'c++': 'cpp', 'cc': 'cpp', 'cxx': 'cpp', 'h': 'cpp', 'hpp': 'cpp',
    'py': 'python', 'python3': 'python', 'js': 'javascript', 'jsx': 'javascript',
    'ts': 'typescript', 'tsx': 'typescript', 'html': 'xml', 'htm': 'xml', 'svg': 'xml',
    'html5': 'xml', 'svelte': 'xml', 'vue': 'vue', 'sh': 'bash', 'shell': 'bash', 'zsh': 'bash',
    'ksh': 'bash', 'fish': 'bash', 'console': 'bash', 'yml': 'yaml',
    'cs': 'cs', 'c#': 'cs', 'csharp': 'cs', 'ps1': 'powershell', 'ps': 'powershell',
    'pwsh': 'powershell', 'cmd': 'dos', 'bat': 'dos', 'make': 'makefile',
    'objc': 'objectivec', 'objective-c': 'objectivec', 'proto': 'protobuf',
    'kt': 'kotlin', 'kts': 'kotlin', 'rs': 'rust', 'rb': 'ruby', 'md': 'markdown',
    'docker': 'dockerfile', 'dockerfile': 'dockerfile', 'toml': 'ini',
    'f#': 'fsharp', 'fs': 'fsharp', 'vb': 'vbnet', 'pas': 'delphi',
    'plaintext': 'plaintext', 'txt': 'plaintext', 'text': 'plaintext',
  };

  static const _display = {
    'c': 'C', 'c++': 'C++', 'cpp': 'C++', 'cs': 'C#', 'c#': 'C#',
    'py': 'Python', 'python': 'Python', 'js': 'JavaScript', 'javascript': 'JavaScript',
    'ts': 'TypeScript', 'typescript': 'TypeScript', 'html': 'HTML', 'htm': 'HTML',
    'xml': 'XML', 'css': 'CSS', 'scss': 'SCSS', 'less': 'LESS', 'yaml': 'YAML',
    'yml': 'YAML', 'sh': 'Shell', 'shell': 'Shell', 'bash': 'Shell', 'zsh': 'Shell',
    'ps1': 'PowerShell', 'powershell': 'PowerShell', 'php': 'PHP', 'java': 'Java',
    'kotlin': 'Kotlin', 'swift': 'Swift', 'go': 'Go', 'rust': 'Rust', 'ruby': 'Ruby',
    'dart': 'Dart', 'sql': 'SQL', 'json': 'JSON', 'md': 'Markdown',
    'markdown': 'Markdown', 'dockerfile': 'Dockerfile', 'makefile': 'Makefile',
    'objectivec': 'Objective-C', 'protobuf': 'Protobuf', 'graphql': 'GraphQL',
    'vbnet': 'VB.NET', 'fsharp': 'F#', 'delphi': 'Delphi', 'perl': 'Perl',
    'lua': 'Lua', 'r': 'R', 'scala': 'Scala', 'groovy': 'Groovy', 'haskell': 'Haskell',
    'elixir': 'Elixir', 'erlang': 'Erlang', 'clojure': 'Clojure', 'vim': 'Vim',
    'diff': 'Diff', 'nginx': 'Nginx', 'toml': 'TOML', 'ini': 'INI', 'vue': 'Vue',
  };

  static String normalize(String? language) {
    if (language == null || language.trim().isEmpty) return '';
    var name = language.trim().toLowerCase().split(RegExp(r'\s+')).first;
    name = name.replaceFirst(RegExp(r'^language-'), '');
    if (name.startsWith('{.') && name.endsWith('}')) name = name.substring(2, name.length - 1);
    return aliases[name] ?? name;
  }

  static String displayName(String? language, String code) {
    final raw = language?.trim() ?? '';
    if (raw.isNotEmpty) {
      final key = raw.toLowerCase().split(RegExp(r'\s+')).first;
      final label = _display[key];
      if (label != null) return label;
    }
    final normalized = raw.isEmpty ? detect(code) : normalize(raw);
    return _display[normalized] ?? (normalized.isEmpty ? 'CODE' : normalized.toUpperCase());
  }

  static bool supports(String language) => allLanguages.containsKey(normalize(language));

  static String detect(String code) {
    final sample = code.substring(0, code.length.clamp(0, 2048));
    if (RegExp(r'^#!.*\b(?:ba|z)?sh\b').hasMatch(sample)) return 'bash';
    if (RegExp(r'^#!.*python|\bdef \w+\(|\bfrom \w+ import ').hasMatch(sample)) return 'python';
    if (sample.contains('<?php')) return 'php';
    if (RegExp(r'^\s*<(?:!DOCTYPE|html|\?xml|[a-z]+\b)', caseSensitive: false).hasMatch(sample)) return 'xml';
    if (RegExp(r'#include\s*[<"]|\bstd::').hasMatch(sample)) return 'cpp';
    if (RegExp(r'\bpublic\s+(?:static\s+)?class\b|System\.out\.').hasMatch(sample)) return 'java';
    if (RegExp(r'\b(?:const|let|var)\s+\w+\s*=|=>|console\.log').hasMatch(sample)) return 'javascript';
    if (RegExp(r'^\s*[\[{]').hasMatch(sample) && RegExp(r'"[^"\n]+"\s*:').hasMatch(sample)) return 'json';
    if (RegExp(r'\b(?:SELECT|INSERT|CREATE TABLE)\b', caseSensitive: false).hasMatch(sample)) return 'sql';
    if (RegExp(r'^[\w.-]+:\s', multiLine: true).hasMatch(sample)) return 'yaml';
    return 'plaintext';
  }

  static List<CodeToken> slice(String source, {String? language, int start = 0, int? end}) {
    final finish = end ?? source.length;
    final normalized = normalize(language);
    final selected = normalized.isEmpty ? detect(source) : normalized;
    if (!supports(selected)) return [CodeToken(source.substring(start, finish), null)];
    final windowStart = source.length <= windowSize ? 0 : (start ~/ windowSize) * windowSize;
    final windowEnd = (windowStart + windowSize).clamp(0, source.length);
    if (finish > windowEnd) {
      return [
        ...slice(source, language: selected, start: start, end: windowEnd),
        ...slice(source, language: selected, start: windowEnd, end: finish),
      ];
    }
    final text = source.substring(windowStart, windowEnd);
    final key = (selected, text);
    var tokens = _cache.remove(key);
    if (tokens == null) {
      try {
        final result = hl.highlight.parse(text, language: selected);
        final built = <CodeToken>[];
        void visit(List<hl.Node> nodes, String? parent) {
          for (final node in nodes) {
            final kind = node.className ?? parent;
            if (node.value != null) built.add(CodeToken(node.value!, kind));
            if (node.children != null) visit(node.children!, kind);
          }
        }
        visit(result.nodes ?? [], null);
        tokens = built.map((t) => t.text).join() == text ? built : [CodeToken(text, null)];
      } catch (_) {
        tokens = [CodeToken(text, null)];
      }
      _cachedCharacters += text.length;
    }
    _cache[key] = tokens;
    while (_cachedCharacters > 256 * 1024 || _cache.length > 48) {
      final oldest = _cache.keys.first;
      _cachedCharacters -= oldest.$2.length;
      _cache.remove(oldest);
    }
    var offset = windowStart;
    final sliced = <CodeToken>[];
    for (final token in tokens) {
      final next = offset + token.text.length;
      if (next > start && offset < finish) {
        sliced.add(CodeToken(token.text.substring((start - offset).clamp(0, token.text.length),
          (finish - offset).clamp(0, token.text.length)), token.kind));
      }
      offset = next;
      if (offset >= finish) break;
    }
    return sliced;
  }
}
