import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../markdown/inline.dart';
import '../markdown/model.dart';
import '../markdown/preview_items.dart';
import '../widgets/glass.dart';
import '../widgets/rich_markdown_text.dart';
import '../widgets/code_text.dart';
import '../widgets/markdown_image.dart';
import '../markdown/code_highlighter.dart';
import 'theme.dart';

class MarkdownPreviewPane extends StatefulWidget {
  const MarkdownPreviewPane({
    super.key,
    required this.document,
    required this.basePath,
    this.onLinkTap,
    this.onFootnoteTap,
    this.onToggleTask,
  });

  final MarkdownDocument document;
  final String? basePath;
  final ValueChanged<String>? onLinkTap;
  final ValueChanged<String>? onFootnoteTap;
  final ValueChanged<ListItemBlock>? onToggleTask;

  @override
  State<MarkdownPreviewPane> createState() => MarkdownPreviewPaneState();
}

class MarkdownPreviewPaneState extends State<MarkdownPreviewPane> {
  int _imageRevision = 0;
  void reloadImages() {
    if (mounted) setState(() => _imageRevision++);
  }

  late List<PreviewItem> _items;
  late List<int> _blockStarts;

  @override
  void initState() {
    super.initState();
    _updateItems();
  }

  @override
  void didUpdateWidget(MarkdownPreviewPane oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.document, widget.document)) _updateItems();
  }

  void _updateItems() {
    _items = buildPreviewItems(widget.document);
    _blockStarts = List.filled(widget.document.blocks.length, 0);
    for (var i = _items.length - 1; i >= 0; i--) {
      _blockStarts[_items[i].blockIndex] = i;
    }
  }

  final ItemScrollController _scrollController = ItemScrollController();
  final ItemPositionsListener _positionsListener =
      ItemPositionsListener.create();

  int? get firstVisibleIndex {
    final positions =
        _positionsListener.itemPositions.value
            .where(
              (position) =>
                  position.itemTrailingEdge > 0 && position.itemLeadingEdge < 1,
            )
            .toList()
          ..sort((a, b) => a.index.compareTo(b.index));
    if (positions.isEmpty || positions.first.index >= _items.length) {
      return null;
    }
    return _items[positions.first.index].blockIndex;
  }

  bool isIndexVisible(int index) {
    return _positionsListener.itemPositions.value.any(
      (position) =>
          position.index < _items.length &&
          _items[position.index].blockIndex == index &&
          position.itemTrailingEdge > 0 &&
          position.itemLeadingEdge < 1,
    );
  }

  void scrollToBlock(int index, {required bool animate}) {
    if (!_scrollController.isAttached || _blockStarts.isEmpty) return;
    final safe = _blockStarts[index.clamp(0, _blockStarts.length - 1)];
    if (animate) {
      _scrollController.scrollTo(
        index: safe,
        duration: const Duration(milliseconds: 360),
        curve: Curves.easeOutCubic,
        alignment: 0.02,
      );
    } else {
      _scrollController.jumpTo(index: safe, alignment: 0.02);
    }
  }

  @override
  Widget build(BuildContext context) {
    final settings = GlassScope.of(context);
    final palette = ReaderPalette.from(
      context,
      background: settings.readerBackground,
    );
    final pageTheme = buildReaderTheme(Theme.of(context), palette);
    final blocks = widget.document.blocks;

    final surface = Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: palette.gradient,
        ),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: palette.border),
        boxShadow: settings.glassEffectsEnabled
            ? [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.12),
                  blurRadius: 20,
                  offset: const Offset(0, 10),
                ),
              ]
            : null,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: blocks.isEmpty
            ? _EmptyPreview(palette: palette)
            : SelectionArea(
                child: ScrollablePositionedList.builder(
                  itemCount: _items.length,
                  addAutomaticKeepAlives: false,
                  itemScrollController: _scrollController,
                  itemPositionsListener: _positionsListener,
                  physics: const BouncingScrollPhysics(
                    parent: AlwaysScrollableScrollPhysics(),
                  ),
                  padding: const EdgeInsets.fromLTRB(18, 16, 18, 32),
                  itemBuilder: (context, index) {
                    return _BlockView(
                      key: ValueKey((index, _imageRevision)),
                      block: _items[index].block,
                      original: _items[index].original,
                      palette: palette,
                      basePath: widget.basePath,
                      onLinkTap: widget.onLinkTap,
                      onFootnoteTap: widget.onFootnoteTap,
                      onToggleTask: widget.onToggleTask,
                    );
                  },
                ),
              ),
      ),
    );

    return Theme(
      data: pageTheme,
      child: DocumentImageScope(
        basePath: widget.basePath,
        onImagesReload: reloadImages,
        child: Semantics(container: true, label: 'Markdown 预览', child: surface),
      ),
    );
  }
}

class _EmptyPreview extends StatelessWidget {
  const _EmptyPreview({required this.palette});

  final ReaderPalette palette;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.article_outlined, size: 42, color: palette.secondary),
            const SizedBox(height: 12),
            Text(
              '这一页还很安静',
              style: TextStyle(
                color: palette.text,
                fontSize: 17,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '打开一个 Markdown 文件，或从源码开始。',
              textAlign: TextAlign.center,
              style: TextStyle(color: palette.secondary, height: 1.5),
            ),
          ],
        ),
      ),
    );
  }
}

class _BlockView extends StatelessWidget {
  const _BlockView({
    super.key,
    required this.block,
    required this.original,
    required this.palette,
    required this.basePath,
    this.onLinkTap,
    this.onFootnoteTap,
    this.onToggleTask,
  });

  final MdBlock block;
  final MdBlock original;
  final ReaderPalette palette;
  final String? basePath;
  final ValueChanged<String>? onLinkTap;
  final ValueChanged<String>? onFootnoteTap;
  final ValueChanged<ListItemBlock>? onToggleTask;

  @override
  Widget build(BuildContext context) {
    final block = this.block;
    if (block is HeadingBlock) {
      final size = switch (block.level) {
        1 => 29.0,
        2 => 25.0,
        3 => 21.5,
        4 => 19.0,
        _ => 17.0,
      };
      return Padding(
        padding: EdgeInsets.only(top: block.level == 1 ? 18 : 14, bottom: 6),
        child: Semantics(
          header: true,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (block.level == 1)
                Container(
                  width: 36,
                  height: 3,
                  margin: const EdgeInsets.only(bottom: 8),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: [
                        palette.link,
                        palette.link.withValues(alpha: 0.18),
                      ],
                    ),
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
              RichMarkdownText(
                nodes: parseInline(block.text),
                style: TextStyle(
                  color: palette.text,
                  fontSize: size,
                  height: 1.25,
                  fontWeight: block.level <= 2
                      ? FontWeight.w700
                      : FontWeight.w600,
                  letterSpacing: block.level == 1 ? -0.4 : -0.1,
                ),
                onLinkTap: onLinkTap,
                onFootnoteTap: onFootnoteTap,
              ),
            ],
          ),
        ),
      );
    }

    if (block is ParagraphBlock) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: RichMarkdownText(
          nodes: parseInline(block.text),
          style: TextStyle(color: palette.text, fontSize: 16.5, height: 1.68),
          onLinkTap: onLinkTap,
          onFootnoteTap: onFootnoteTap,
        ),
      );
    }

    if (block is ListItemBlock) {
      final task = original as ListItemBlock;
      return _ListLine(
        block: block,
        palette: palette,
        onLinkTap: onLinkTap,
        onFootnoteTap: onFootnoteTap,
        onToggleTask: onToggleTask == null || !task.hasSourceLine
            ? null
            : (_) => onToggleTask!(task),
      );
    }

    if (block is QuoteBlock) {
      return Padding(
        padding: const EdgeInsets.only(top: 2, bottom: 14),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
          decoration: BoxDecoration(
            color: palette.codeBackground.withValues(alpha: 0.52),
            border: Border(left: BorderSide(color: palette.link, width: 3)),
            borderRadius: const BorderRadius.only(
              topRight: Radius.circular(12),
              bottomRight: Radius.circular(12),
            ),
          ),
          child: RichMarkdownText(
            nodes: parseInline(block.lines.join('\n')),
            style: TextStyle(
              color: palette.secondary,
              fontSize: 15.5,
              height: 1.62,
              fontStyle: FontStyle.italic,
            ),
            onLinkTap: onLinkTap,
            onFootnoteTap: onFootnoteTap,
          ),
        ),
      );
    }

    if (block is CodeBlock) {
      return _CodeView(
        block: block,
        palette: palette,
        copyText: (original as CodeBlock).code,
      );
    }

    if (block is TableBlock) {
      return _TableView(block: block, palette: palette);
    }

    if (block is ImageBlock) {
      return MarkdownImage(block: block, palette: palette, basePath: basePath);
    }

    if (block is DefinitionBlock) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              margin: const EdgeInsets.only(top: 2, right: 8),
              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
              decoration: BoxDecoration(
                color: palette.codeBackground,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                block.label,
                style: TextStyle(
                  color: palette.link,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            Expanded(
              child: RichMarkdownText(
                nodes: parseInline(block.text),
                style: TextStyle(
                  color: palette.secondary,
                  fontSize: 14,
                  height: 1.5,
                ),
                onLinkTap: onLinkTap,
                onFootnoteTap: onFootnoteTap,
              ),
            ),
          ],
        ),
      );
    }

    if (block is RuleBlock) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Divider(color: palette.border),
      );
    }

    return const SizedBox.shrink();
  }
}

class _ListLine extends StatelessWidget {
  const _ListLine({
    required this.block,
    required this.palette,
    this.onLinkTap,
    this.onFootnoteTap,
    this.onToggleTask,
  });

  final ListItemBlock block;
  final ReaderPalette palette;
  final ValueChanged<String>? onLinkTap;
  final ValueChanged<String>? onFootnoteTap;
  final ValueChanged<ListItemBlock>? onToggleTask;

  @override
  Widget build(BuildContext context) {
    final marker = block.ordered ? '${block.number}.' : '•';
    final taskMark = block.checked == null
        ? null
        : _TaskMark(
            checked: block.checked!,
            onTap: onToggleTask == null ? null : () => onToggleTask!(block),
          );
    return Padding(
      padding: EdgeInsets.only(left: 2 + block.depth * 18.0, bottom: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 30,
            child: Padding(
              padding: const EdgeInsets.only(top: 2),
              child:
                  taskMark ??
                  Text(
                    marker,
                    style: TextStyle(
                      color: block.ordered ? palette.secondary : palette.link,
                      fontSize: 17,
                      height: 1.5,
                    ),
                  ),
            ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 1),
              child: RichMarkdownText(
                nodes: parseInline(block.text),
                style: TextStyle(
                  color: block.checked == true
                      ? palette.secondary.withValues(alpha: 0.65)
                      : palette.text,
                  fontSize: 16,
                  height: 1.6,
                  decoration: block.checked == true
                      ? TextDecoration.lineThrough
                      : null,
                ),
                onLinkTap: onLinkTap,
                onFootnoteTap: onFootnoteTap,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _TaskMark extends StatelessWidget {
  const _TaskMark({required this.checked, this.onTap});

  final bool checked;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return Semantics(
      checked: checked,
      label: checked ? '已完成' : '未完成',
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: SizedBox(
          width: 28,
          height: 28,
          child: Center(
            child: AnimatedContainer(
              duration: motionOf(context)
                  ? const Duration(milliseconds: 140)
                  : Duration.zero,
              width: 18,
              height: 18,
              decoration: BoxDecoration(
                color: checked ? color : Colors.transparent,
                borderRadius: BorderRadius.circular(5),
                border: Border.all(
                  color: checked ? color : color.withValues(alpha: 0.6),
                  width: 1.4,
                ),
              ),
              child: checked
                  ? const Icon(Icons.check, size: 13, color: Colors.white)
                  : null,
            ),
          ),
        ),
      ),
    );
  }
}

class _CodeView extends StatelessWidget {
  const _CodeView({
    required this.block,
    required this.palette,
    required this.copyText,
  });

  final CodeBlock block;
  final ReaderPalette palette;
  final String copyText;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Container(
        decoration: BoxDecoration(
          color: palette.codeBackground,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: palette.border),
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          children: [
            Container(
              height: 38,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.035),
              ),
              child: Row(
                children: [
                  Icon(Icons.code, size: 15, color: palette.secondary),
                  const SizedBox(width: 7),
                  Expanded(
                    child: Text(
                      CodeHighlighter.displayName(block.language, copyText),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: palette.secondary,
                        fontSize: 11,
                        letterSpacing: 0.7,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: '复制代码',
                    iconSize: 17,
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(
                      minWidth: 36,
                      minHeight: 36,
                    ),
                    onPressed: () async {
                      var copied = true;
                      try {
                        await Clipboard.setData(ClipboardData(text: copyText));
                      } catch (_) {
                        copied = false;
                      }
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(copied ? '代码已复制' : '无法访问剪贴板')),
                        );
                      }
                    },
                    icon: Icon(Icons.copy_rounded, color: palette.secondary),
                  ),
                ],
              ),
            ),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.fromLTRB(14, 12, 14, 14),
              child: CodeText(
                source: block.sourceCode ?? block.code,
                language: block.language,
                start: block.sourceOffset,
                end: block.sourceOffset + block.code.length,
                style: TextStyle(
                  color: theme.colorScheme.onSurface,
                  fontFamily: 'monospace',
                  fontSize: 13.2,
                  height: 1.55,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TableView extends StatelessWidget {
  const _TableView({required this.block, required this.palette});

  final TableBlock block;
  final ReaderPalette palette;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final rows = block.rows.take(300).toList(growable: false);
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: palette.border),
              ),
              clipBehavior: Clip.antiAlias,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _TableRowView(
                    cells: block.headers,
                    alignments: block.alignments,
                    palette: palette,
                    header: true,
                  ),
                  for (var index = 0; index < rows.length; index++)
                    _TableRowView(
                      cells: rows[index],
                      alignments: block.alignments,
                      palette: palette,
                      alternate: index.isOdd,
                    ),
                ],
              ),
            ),
          ),
          if (block.rows.length > rows.length)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(
                '表格较长，仅显示前 ${rows.length} 行。',
                style: TextStyle(color: palette.secondary, fontSize: 12),
              ),
            ),
          if (block.truncatedColumns)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                '表格列数较多，仅渲染前 ${block.headers.length} 列。',
                style: TextStyle(color: theme.colorScheme.error, fontSize: 12),
              ),
            ),
        ],
      ),
    );
  }
}

class _TableRowView extends StatelessWidget {
  const _TableRowView({
    required this.cells,
    required this.alignments,
    required this.palette,
    this.header = false,
    this.alternate = false,
  });

  final List<String> cells;
  final List<TableAlignment> alignments;
  final ReaderPalette palette;
  final bool header;
  final bool alternate;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var index = 0; index < cells.length; index++)
          Container(
            width: 148,
            padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
            decoration: BoxDecoration(
              color: header
                  ? palette.link.withValues(alpha: 0.10)
                  : alternate
                  ? palette.codeBackground.withValues(alpha: 0.34)
                  : Colors.transparent,
              border: Border(
                right: index == cells.length - 1
                    ? BorderSide.none
                    : BorderSide(color: palette.border.withValues(alpha: 0.5)),
                bottom: BorderSide(
                  color: palette.border.withValues(alpha: 0.4),
                ),
              ),
            ),
            child: RichMarkdownText(
              nodes: parseInline(cells[index]),
              textAlign: switch (index < alignments.length
                  ? alignments[index].name
                  : 'left') {
                'center' => TextAlign.center,
                'right' => TextAlign.right,
                _ => TextAlign.left,
              },
              style: TextStyle(
                color: palette.text,
                fontSize: header ? 13.5 : 14,
                height: 1.4,
                fontWeight: header ? FontWeight.w700 : FontWeight.w400,
              ),
            ),
          ),
      ],
    );
  }
}
