import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/text_chunks.dart';
import 'package:liquid_ink/markdown/model.dart';
import 'package:liquid_ink/markdown/parser.dart';
import 'package:liquid_ink/markdown/preview_items.dart';

void main() {
  test('chunks preserve Unicode, line endings and every character', () {
    final source = '${'x' * 2047}😀\r\n${'文😀\r\n' * 3000}';
    final chunks = textChunks(
      source,
    ).map((r) => source.substring(r.start, r.end)).toList();
    expect(chunks.join(), source);
    for (final text in chunks) {
      expect(text.length, lessThanOrEqualTo(2048));
      expect(text.codeUnitAt(0), isNot(inInclusiveRange(0xDC00, 0xDFFF)));
      expect(
        text.codeUnitAt(text.length - 1),
        isNot(inInclusiveRange(0xD800, 0xDBFF)),
      );
    }
  });

  test(
    'code and table slices retain the entire document and semantic indexes',
    () {
      final code = 'line 😀\n' * 12000;
      final rows = List.generate(
        1100,
        (i) => '| row $i | value $i |',
      ).join('\n');
      final document = parseMarkdown(
        '```\n$code```\n\n| A | B |\n| --- | --- |\n$rows\n\n# End',
      );
      final items = buildPreviewItems(document);
      expect(
        items
            .where((i) => i.block is CodeBlock)
            .map((i) => (i.block as CodeBlock).code)
            .join(),
        (document.blocks.first as CodeBlock).code,
      );
      final renderedRows = items
          .where((i) => i.block is TableBlock)
          .expand((i) => (i.block as TableBlock).rows)
          .toList();
      expect(renderedRows.length, 1100);
      expect(renderedRows.last, ['row 1099', 'value 1099']);
      expect(items.last.blockIndex, document.outline.last.blockIndex);
    },
  );
}
