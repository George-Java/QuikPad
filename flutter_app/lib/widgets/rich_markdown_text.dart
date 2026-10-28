import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart';

import '../markdown/inline.dart';
import '../markdown/model.dart';
import '../ui/theme.dart';
import 'markdown_image.dart';

class RichMarkdownText extends StatefulWidget {
  const RichMarkdownText({
    super.key,
    required this.nodes,
    required this.style,
    this.onLinkTap,
    this.onFootnoteTap,
    this.textAlign = TextAlign.start,
  });

  final List<InlineNode> nodes;
  final TextStyle style;
  final ValueChanged<String>? onLinkTap;
  final ValueChanged<String>? onFootnoteTap;
  final TextAlign textAlign;

  @override
  State<RichMarkdownText> createState() => _RichMarkdownTextState();
}

class _RichMarkdownTextState extends State<RichMarkdownText> {
  final _recognizers = <TapGestureRecognizer>[];
  ValueChanged<String>? get onLinkTap => widget.onLinkTap;
  ValueChanged<String>? get onFootnoteTap => widget.onFootnoteTap;

  @override
  void dispose() {
    for (final recognizer in _recognizers) { recognizer.dispose(); }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    for (final recognizer in _recognizers) { recognizer.dispose(); }
    _recognizers.clear();
    final theme = Theme.of(context);
    final spans = _buildSpans(
      context,
      widget.nodes,
      widget.style,
      theme.colorScheme.primary,
      theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.8),
    );
    return Text.rich(
      TextSpan(style: widget.style, children: spans),
      textAlign: widget.textAlign,
      textWidthBasis: TextWidthBasis.parent,
    );
  }

  List<InlineSpan> _buildSpans(
    BuildContext context,
    List<InlineNode> nodes,
    TextStyle base,
    Color linkColor,
    Color codeBackground,
  ) {
    final theme = Theme.of(context);
    final spans = <InlineSpan>[];
    for (final node in nodes) {
      if (node is InlineText) {
        spans.add(TextSpan(text: node.text, style: base));
      } else if (node is InlineCode) {
        spans.add(
          TextSpan(
            text: node.text,
            style: base.copyWith(
              fontFamily: 'monospace',
              backgroundColor: codeBackground,
              color: theme.colorScheme.onSurface,
            ),
          ),
        );
      } else if (node is InlineStrong) {
        spans.addAll(
          _buildSpans(
            context,
            node.children,
            base.copyWith(fontWeight: FontWeight.w700),
            linkColor,
            codeBackground,
          ),
        );
      } else if (node is InlineEmphasis) {
        spans.addAll(
          _buildSpans(
            context,
            node.children,
            base.copyWith(fontStyle: FontStyle.italic),
            linkColor,
            codeBackground,
          ),
        );
      } else if (node is InlineStrike) {
        spans.addAll(
          _buildSpans(
            context,
            node.children,
            base.copyWith(decoration: TextDecoration.lineThrough),
            linkColor,
            codeBackground,
          ),
        );
      } else if (node is InlineStyled) {
        var styled = base;
        if (node.tag == 'u' || node.tag == 'ins') styled = base.copyWith(decoration: TextDecoration.underline);
        if (node.tag == 'mark') styled = base.copyWith(backgroundColor: theme.colorScheme.tertiaryContainer);
        if (node.tag == 'sup' || node.tag == 'sub') styled = base.copyWith(fontSize: (base.fontSize ?? 16) * .8);
        spans.addAll(_buildSpans(context, node.children, styled, linkColor, codeBackground));
      } else if (node is InlineLink) {
        final linkStyle = base.copyWith(
          color: linkColor,
          decoration: TextDecoration.underline,
          decorationColor: linkColor.withValues(alpha: 0.55),
        );
        final recognizer = TapGestureRecognizer()..onTap = onLinkTap == null ? null : () => onLinkTap!(node.url);
        _recognizers.add(recognizer);
        spans.add(TextSpan(text: inlinePlainText(node.children), style: linkStyle,
          recognizer: recognizer));
      } else if (node is InlineImage) {
        spans.add(
          WidgetSpan(
            alignment: PlaceholderAlignment.middle,
            child: SizedBox(width: (MediaQuery.sizeOf(context).width - 90).clamp(60, 480),
              child: MarkdownImage(block: ImageBlock(alt: node.alt, url: node.url, title: null, line: 0),
                palette: ReaderPalette.from(context))),
          ),
        );
      } else if (node is InlineFootnote) {
        spans.add(
          WidgetSpan(
            alignment: PlaceholderAlignment.top,
            child: Semantics(
              label: '脚注 ${node.label}',
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: onFootnoteTap == null
                    ? null
                    : () => onFootnoteTap!(node.label),
                child: Transform.translate(
                  offset: const Offset(0, -2),
                  child: Text(
                    '[${node.label}]',
                    style: base.copyWith(
                      fontSize: (base.fontSize ?? 16) * 0.72,
                      color: linkColor,
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      }
    }
    return spans;
  }
}
