import '../core/text_chunks.dart';
import 'model.dart';

/// Presentation slices keep the semantic block index used by the outline.
class PreviewItem {
  const PreviewItem(this.blockIndex, this.block, this.original);
  final int blockIndex;
  final MdBlock block;
  final MdBlock original;
}

List<PreviewItem> buildPreviewItems(MarkdownDocument document) {
  final result = <PreviewItem>[];
  for (var index = 0; index < document.blocks.length; index++) {
    final original = document.blocks[index];
    void add(MdBlock block) => result.add(PreviewItem(index, block, original));
    Iterable<String> pieces(String text, {int limit = 2048}) => textChunks(
      text,
      maxCharacters: limit,
    ).map((range) => text.substring(range.start, range.end));
    if (original is CodeBlock) {
      final ranges = textChunks(original.code).toList();
      for (final range in ranges.isEmpty ? [(start: 0, end: 0)] : ranges) {
        add(
          CodeBlock(
            code: original.code.substring(range.start, range.end),
            language: original.language,
            closed: original.closed,
            line: original.line,
            sourceCode: original.code,
            sourceOffset: range.start,
          ),
        );
      }
    } else if (original is ParagraphBlock) {
      for (final text in pieces(original.text)) {
        add(ParagraphBlock(text: text, line: original.line));
      }
    } else if (original is QuoteBlock) {
      for (final text in pieces(original.lines.join('\n'))) {
        add(QuoteBlock(lines: [text], line: original.line));
      }
    } else if (original is HeadingBlock) {
      final chunks = pieces(original.text).toList();
      for (var i = 0; i < chunks.length; i++) {
        add(
          i == 0
              ? HeadingBlock(
                  level: original.level,
                  text: chunks[i],
                  line: original.line,
                )
              : ParagraphBlock(text: chunks[i], line: original.line),
        );
      }
      if (chunks.isEmpty) add(original);
    } else if (original is ListItemBlock) {
      final chunks = pieces(original.text).toList();
      for (var i = 0; i < chunks.length; i++) {
        add(
          i == 0
              ? ListItemBlock(
                  ordered: original.ordered,
                  number: original.number,
                  depth: original.depth,
                  text: chunks[i],
                  checked: original.checked,
                  line: original.line,
                )
              : ParagraphBlock(text: chunks[i], line: original.line),
        );
      }
      if (chunks.isEmpty) add(original);
    } else if (original is DefinitionBlock) {
      for (final text in pieces('[${original.label}] ${original.text}')) {
        add(ParagraphBlock(text: text, line: original.line));
      }
    } else if (original is TableBlock) {
      // Rows (including oversized cells) are split into small lazy items.
      // Every row is retained; the former 300-row display cutoff is gone.
      final headerParts = original.headers
          .map((s) => pieces(s, limit: 512).toList())
          .toList();
      final headers = headerParts
          .map((parts) => parts.isEmpty ? '' : parts.first)
          .toList();
      void addRows(List<List<String>> rows) => add(
        TableBlock(
          headers: headers,
          rows: rows,
          alignments: original.alignments,
          truncatedColumns: original.truncatedColumns,
          line: original.line,
        ),
      );
      var headerCount = 1;
      for (final parts in headerParts) {
        if (parts.length > headerCount) headerCount = parts.length;
      }
      // Keep oversized column labels too, as continuation rows.
      for (var part = 1; part < headerCount; part++) {
        addRows([
          headerParts
              .map((parts) => part < parts.length ? parts[part] : '')
              .toList(),
        ]);
      }
      if (original.rows.isEmpty && headerCount == 1) addRows([]);
      final rowsPerItem = (32 ~/ headers.length.clamp(1, 32)).clamp(1, 12);
      var pending = <List<String>>[];
      for (final row in original.rows) {
        final cells = row.map((s) => pieces(s, limit: 512).toList()).toList();
        var count = 1;
        for (final cell in cells) {
          if (cell.length > count) count = cell.length;
        }
        for (var part = 0; part < count; part++) {
          pending.add(
            cells.map((cell) => part < cell.length ? cell[part] : '').toList(),
          );
          if (pending.length == rowsPerItem) {
            addRows(pending);
            pending = [];
          }
        }
      }
      if (pending.isNotEmpty) addRows(pending);
    } else {
      add(original);
    }
  }
  return result;
}
