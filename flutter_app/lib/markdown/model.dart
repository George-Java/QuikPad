sealed class MdBlock {
  const MdBlock({required this.line});

  /// Zero-based source line, used for "show in source".
  final int line;
}

class HeadingBlock extends MdBlock {
  const HeadingBlock({
    required this.level,
    required this.text,
    required super.line,
  });

  final int level;
  final String text;
}

class ParagraphBlock extends MdBlock {
  const ParagraphBlock({required this.text, required super.line});

  final String text;
}

class CodeBlock extends MdBlock {
  const CodeBlock({
    required this.code,
    required this.language,
    required this.closed,
    required super.line,
    this.sourceCode,
    this.sourceOffset = 0,
  });

  final String code;
  final String? language;
  final bool closed;
  final String? sourceCode;
  final int sourceOffset;
}

class QuoteBlock extends MdBlock {
  const QuoteBlock({required this.lines, required super.line});

  final List<String> lines;
}

class ListItemBlock extends MdBlock {
  const ListItemBlock({
    required this.ordered,
    required this.number,
    required this.depth,
    required this.text,
    required this.checked,
    required super.line,
  });

  final bool ordered;
  final int number;
  final int depth;
  final String text;
  final bool? checked;

  /// Whether this task's exact source line is known. HTML checkbox lists have
  /// no Markdown task marker in the source, so [line] is -1 and must not be
  /// edited through the preview checkbox.
  bool get hasSourceLine => line >= 0;
}

class TableAlignment {
  const TableAlignment(this.name);

  final String name;

  static const left = TableAlignment('left');
  static const center = TableAlignment('center');
  static const right = TableAlignment('right');
}

class TableBlock extends MdBlock {
  const TableBlock({
    required this.headers,
    required this.rows,
    required this.alignments,
    required this.truncatedColumns,
    required super.line,
  });

  final List<String> headers;
  final List<List<String>> rows;
  final List<TableAlignment> alignments;
  final bool truncatedColumns;
}

class ImageBlock extends MdBlock {
  const ImageBlock({
    required this.alt,
    required this.url,
    required this.title,
    required super.line,
  });

  final String alt;
  final String url;
  final String? title;
}

class RuleBlock extends MdBlock {
  const RuleBlock({required super.line});
}

class DefinitionBlock extends MdBlock {
  const DefinitionBlock({
    required this.label,
    required this.text,
    required super.line,
  });

  final String label;
  final String text;
}

class OutlineEntry {
  const OutlineEntry({
    required this.blockIndex,
    required this.level,
    required this.title,
  });

  final int blockIndex;
  final int level;
  final String title;
}

class MarkdownDocument {
  const MarkdownDocument({
    required this.blocks,
    required this.outline,
    required this.lineCount,
    this.anchors = const {},
  });

  final List<MdBlock> blocks;
  final List<OutlineEntry> outline;
  final int lineCount;
  final Map<String, int> anchors;
}
