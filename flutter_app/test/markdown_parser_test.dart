import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/markdown/inline.dart';
import 'package:liquid_ink/markdown/model.dart';
import 'package:liquid_ink/markdown/parser.dart';

void main() {
  group('block parser', () {
    test('parses headings, paragraphs, and outline indexes', () {
      final document = parseMarkdown('''
Intro

# One

Body

## Two

### Three

#### Four
''');
      expect(document.outline.length, 3);
      expect(document.outline.map((entry) => entry.level), [1, 2, 3]);
      expect(document.outline.map((entry) => entry.title), ['One', 'Two', 'Three']);
      for (final entry in document.outline) {
        expect(document.blocks[entry.blockIndex], isA<HeadingBlock>());
      }
    });

    test('parses fenced code and keeps unclosed fences', () {
      final document = parseMarkdown('''
```dart
void main() {}
```

~~~
unclosed
''');
      final blocks = document.blocks.whereType<CodeBlock>().toList();
      expect(blocks.length, 2);
      expect(blocks.first.language, 'dart');
      expect(blocks.first.closed, isTrue);
      expect(blocks.last.closed, isFalse);
    });

    test('parses setext headings and thematic breaks', () {
      final document = parseMarkdown('Title\n=====\n\n---\n');
      expect(document.blocks[0], isA<HeadingBlock>());
      expect((document.blocks[0] as HeadingBlock).level, 1);
      expect(document.blocks[1], isA<RuleBlock>());
    });

    test('parses a long list as individual lazy blocks', () {
      final source = List.generate(500, (index) => '- item $index').join('\n');
      final document = parseMarkdown(source);
      expect(document.blocks.length, 500);
      expect(document.blocks.every((block) => block is ListItemBlock), isTrue);
      expect((document.blocks.last as ListItemBlock).text, 'item 499');
    });

    test('splits a 500-line paragraph into bounded blocks', () {
      final source = List.generate(500, (index) => 'line $index').join('\n');
      final document = parseMarkdown(source);
      expect(document.blocks.length, greaterThan(1));
      expect(document.blocks.every((block) => block is ParagraphBlock), isTrue);
      final lineCount = document.blocks
          .whereType<ParagraphBlock>()
          .fold<int>(0, (sum, block) => sum + block.text.split('\n').length);
      expect(lineCount, 500);
      expect((document.blocks.last as ParagraphBlock).text, contains('line 499'));
    });

    test('caps extremely wide tables at 32 columns', () {
      final header = List.generate(40, (index) => 'C$index').join('|');
      final delimiter = List.generate(40, (_) => '---').join('|');
      final row = List.generate(40, (index) => '$index').join('|');
      final document = parseMarkdown('|$header|\n|$delimiter|\n|$row|');
      final table = document.blocks.single as TableBlock;
      expect(table.headers.length, 32);
      expect(table.truncatedColumns, isTrue);
      expect(table.rows.single.length, 32);
    });

    test('parses tables, quotes, tasks, and standalone images', () {
      final document = parseMarkdown('''
> quoted text

| A | B |
| :- | -: |
| 1 | 2 |

- [x] done
- [ ] todo

![alt](image.png)
''');
      expect(document.blocks.whereType<QuoteBlock>(), isNotEmpty);
      expect(document.blocks.whereType<TableBlock>(), isNotEmpty);
      expect(
        document.blocks.whereType<ListItemBlock>().map((item) => item.checked),
        [true, false],
      );
      expect(document.blocks.whereType<ImageBlock>().single.alt, 'alt');
    });

    test('empty and whitespace-only documents are safe', () {
      expect(parseMarkdown('').blocks, isEmpty);
      expect(parseMarkdown('   \n\n  ').blocks, isEmpty);
    });

    test('random malformed input never throws or stalls', () {
      final random = Random(20261027);
      const tokens = [
        '#',
        '##',
        '|',
        '---',
        '```',
        '~~~',
        '>',
        '- [ ]',
        '1.',
        '[',
        ']',
        '(',
        ')',
        '!',
        '`',
        '*',
        '_',
        '~~',
        r'\',
        '\t',
        ' ',
        '\n',
        '中文',
        'text',
      ];
      for (var round = 0; round < 250; round++) {
        final buffer = StringBuffer();
        final count = 20 + random.nextInt(160);
        for (var i = 0; i < count; i++) {
          buffer.write(tokens[random.nextInt(tokens.length)]);
          if (random.nextBool()) buffer.write(' ');
        }
        expect(() => parseMarkdown(buffer.toString()), returnsNormally);
      }
    });
  });

  group('inline parser', () {
    test('parses bold, italic, code, links, and footnotes', () {
      final nodes = parseInline(
        'A **bold** *italic* `code` [link](https://example.com) [^1]',
      );
      expect(nodes.whereType<InlineStrong>(), isNotEmpty);
      expect(nodes.whereType<InlineEmphasis>(), isNotEmpty);
      expect(nodes.whereType<InlineCode>().single.text, 'code');
      expect(nodes.whereType<InlineLink>().single.url, 'https://example.com');
      expect(nodes.whereType<InlineFootnote>().single.label, '1');
      expect(inlinePlainText(nodes), contains('bold'));
    });

    test('does not recurse forever on empty emphasis', () {
      expect(() => parseInline('**** ** ** ~~'), returnsNormally);
    });
  });
}
