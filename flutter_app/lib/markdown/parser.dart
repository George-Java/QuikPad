import 'dart:convert';

import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as html;
import 'package:markdown/markdown.dart' as md;

import 'inline.dart';
import 'model.dart';

/// CommonMark/GFM syntax maps to flat, lazy native blocks. HTML is static data.
MarkdownDocument parseMarkdown(String source) {
  final normalized = source.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
  final lines = normalized.split('\n');
  final markup = md.markdownToHtml(normalized, extensionSet: md.ExtensionSet.gitHubWeb);
  final fragment = html.parseFragment(markup);
  final blocks = <MdBlock>[];
  final anchors = <String, int>{};
  final outline = <OutlineEntry>[];
  var sourceCursor = 0;

  int locate(String text, {bool task = false}) {
    final first = text.trim().split('\n').first;
    final needle = first.substring(0, first.length.clamp(0, 12));
    for (var i = sourceCursor; i < lines.length && i < sourceCursor + 256; i++) {
      if (task ? RegExp(r'^\s*(?:[-+*]|\d+[.)])\s+\[[ xX]\]').hasMatch(lines[i])
          : needle.isNotEmpty && lines[i].contains(needle)) {
        sourceCursor = i + 1;
        return i;
      }
    }
    return (sourceCursor++).clamp(0, lines.length - 1);
  }

  void paragraph(String text, int line) {
    if (text.trim().isEmpty) return;
    final parts = text.split('\n');
    for (var i = 0; i < parts.length; i += 120) {
      blocks.add(ParagraphBlock(text: parts.sublist(i, (i + 120).clamp(0, parts.length)).join('\n'), line: line + i));
    }
  }

  late void Function(List<dom.Node>, int, int) visit;
  void list(dom.Element element, int depth, int recursion) {
    if (recursion > 32) { paragraph(element.text, 0); return; }
    final ordered = element.localName == 'ol';
    var number = int.tryParse(element.attributes['start'] ?? '') ?? 1;
    for (final item in element.children.where((n) => n.localName == 'li')) {
      if ((item.id.startsWith('fn-') || item.id.startsWith('fn:')) &&
          (element.parent?.classes.contains('footnotes') == true ||
          element.parent?.attributes.containsKey('data-footnotes') == true)) {
        blocks.add(DefinitionBlock(label: item.id.substring(3), text: item.text.replaceAll('\u21a9', '').trim(), line: 0));
        continue;
      }
      final checkbox = item.querySelector('input[type="checkbox"]');
      final checked = checkbox?.attributes.containsKey('checked');
      final content = item.nodes.where((node) => node is! dom.Element ||
          !const {'ul', 'ol', 'pre', 'table', 'blockquote'}.contains(node.localName)).toList();
      var text = content.map((n) => n is dom.Element && n.localName == 'p'
          ? '${n.innerHtml}\n' : n is dom.Element ? n.outerHtml : const HtmlEscape().convert(n.text ?? '')).join().trim();
      text = text.replaceAll(RegExp(r'<input\b[^>]*>'), '').trim();
      final line = locate(item.text, task: checked != null);
      if (text.isNotEmpty || checked != null) {
        blocks.add(ListItemBlock(ordered: ordered, number: number, depth: depth.clamp(0, 8),
          text: text, checked: checked, line: line));
      }
      for (final child in item.children) {
        if (const {'ul', 'ol'}.contains(child.localName)) {
          list(child, depth + 1, recursion + 1);
        } else if (const {'pre', 'table', 'blockquote'}.contains(child.localName)) {
          visit([child], depth + 1, recursion + 1);
        }
      }
      number++;
    }
  }

  visit = (nodes, depth, recursion) {
    if (recursion > 32) { paragraph(nodes.map((n) => n.text ?? '').join(), 0); return; }
    for (final node in nodes) {
      if (node is dom.Text) {
        if (node.data.trim().isNotEmpty) paragraph(const HtmlEscape().convert(node.data), locate(node.data));
        continue;
      }
      if (node is! dom.Element) continue;
      final tag = node.localName ?? '';
      if (const {'script', 'style', 'iframe', 'object', 'embed', 'head'}.contains(tag)) continue;
      final anchor = node.attributes['id'] ?? node.attributes['name'];
      if (anchor != null && anchor.isNotEmpty) anchors[anchor] = blocks.length;
      if (tag == 'p') {
        for (final child in node.querySelectorAll('[id], [name]')) {
          final name = child.attributes['id'] ?? child.attributes['name'];
          if (name != null && name.isNotEmpty) anchors[name] = blocks.length;
        }
      }
      if (RegExp(r'^h[1-6]$').hasMatch(tag)) {
        final level = int.parse(tag.substring(1));
        final block = HeadingBlock(level: level, text: node.innerHtml, line: locate(node.text));
        if (level <= 3) outline.add(OutlineEntry(blockIndex: blocks.length, level: level, title: node.text));
        anchors.putIfAbsent(node.text.toLowerCase().replaceAll(RegExp(r'[^\w\u3400-\u9fff -]'), '')
          .replaceAll(' ', '-'), () => blocks.length);
        blocks.add(block);
      } else if (tag == 'pre') {
        final code = node.querySelector('code');
        var text = (code ?? node).text;
        if (text.endsWith('\n')) text = text.substring(0, text.length - 1);
        final classes = code?.classes ?? <String>{};
        final lang = classes.where((c) => c.startsWith('language-')).firstOrNull;
        var codeLine = locate(text.split('\n').first);
        var closed = true;
        for (var i = (codeLine - 1).clamp(0, lines.length - 1); i >= 0; i--) {
          final match = RegExp(r'^ {0,3}(`{3,}|~{3,})').firstMatch(lines[i]);
          if (match == null) continue;
          final marker = match.group(1)!;
          if (i < codeLine && codeLine - i <= 2) {
            codeLine = i;
            closed = lines.skip(i + 1).any((s) => RegExp('^ {0,3}${RegExp.escape(marker[0])}{${marker.length},} *\$').hasMatch(s));
          }
          break;
        }
        blocks.add(CodeBlock(code: text, language: lang?.substring(9), closed: closed, line: codeLine));
      } else if (tag == 'ul' || tag == 'ol') {
        list(node, depth, recursion);
      } else if (tag == 'table') {
        final rows = node.querySelectorAll('tr');
        if (rows.isEmpty) continue;
        final headerCells = rows.first.children.where((c) => c.localName == 'th' || c.localName == 'td').toList();
        final count = headerCells.length.clamp(1, 32);
        final alignments = List.generate(count, (i) {
          final style = i < headerCells.length ? '${headerCells[i].attributes['align']} ${headerCells[i].attributes['style']}' : '';
          if (style.contains('center')) return TableAlignment.center;
          if (style.contains('right')) return TableAlignment.right;
          return TableAlignment.left;
        });
        blocks.add(TableBlock(headers: List.generate(count, (i) => i < headerCells.length ? headerCells[i].innerHtml : ''),
          rows: rows.skip(1).map((row) {
            final cells = row.children.where((c) => c.localName == 'td' || c.localName == 'th').toList();
            return List.generate(count, (i) => i < cells.length ? cells[i].innerHtml : '');
          }).toList(), alignments: alignments, truncatedColumns: headerCells.length > 32, line: locate(node.text)));
      } else if (tag == 'blockquote') {
        for (final child in node.nodes) {
          if (child is dom.Element && child.localName == 'p') {
            blocks.add(QuoteBlock(lines: [child.innerHtml], line: locate(child.text)));
          } else { visit([child], depth + 1, recursion + 1); }
        }
      } else if (tag == 'img') {
        blocks.add(ImageBlock(alt: node.attributes['alt'] ?? '', url: node.attributes['src'] ?? '',
          title: node.attributes['title'], line: locate(node.attributes['src'] ?? '')));
      } else if (tag == 'hr') {
        blocks.add(RuleBlock(line: locate('---')));
      } else if (tag == 'p') {
        final nonempty = node.nodes.where((n) => n is! dom.Text || n.text.trim().isNotEmpty);
        if (nonempty.isNotEmpty && nonempty.every((n) => n is dom.Element && n.localName == 'img')) {
          visit(node.nodes, depth, recursion + 1);
        } else { paragraph(node.innerHtml, locate(node.text)); }
      } else if (const {'div', 'section', 'article', 'main', 'figure', 'details', 'summary', 'dl', 'dt', 'dd'}.contains(tag)) {
        visit(node.nodes, depth, recursion + 1);
      } else { paragraph(node.outerHtml, locate(node.text)); }
    }
  };
  visit(fragment.nodes, 0, 0);
  return MarkdownDocument(blocks: List.unmodifiable(blocks), outline: List.unmodifiable(outline),
    lineCount: lines.length, anchors: Map.unmodifiable(anchors));
}

String stripInlineMarkdown(String input) => inlinePlainText(parseInline(input));

List<String> splitTableRow(String line) {
  var text = line.trim();
  if (text.startsWith('|')) text = text.substring(1);
  if (text.endsWith('|') && !text.endsWith(r'\|')) text = text.substring(0, text.length - 1);
  final cells = <String>[];
  final buffer = StringBuffer();
  var escaped = false;
  for (final rune in text.runes) {
    final char = String.fromCharCode(rune);
    if (escaped) { buffer.write(char); escaped = false; }
    else if (char == '\\') { escaped = true; }
    else if (char == '|') { cells.add(buffer.toString().trim()); buffer.clear(); }
    else { buffer.write(char); }
  }
  if (escaped) buffer.write('\\');
  cells.add(buffer.toString().trim());
  return cells;
}
