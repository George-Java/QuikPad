import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as html;
import 'package:markdown/markdown.dart' as md;

sealed class InlineNode {
  const InlineNode();
}

class InlineText extends InlineNode {
  const InlineText(this.text);
  final String text;
}

class InlineCode extends InlineNode {
  const InlineCode(this.text);
  final String text;
}

class InlineStrong extends InlineNode {
  const InlineStrong(this.children);
  final List<InlineNode> children;
}

class InlineEmphasis extends InlineNode {
  const InlineEmphasis(this.children);
  final List<InlineNode> children;
}

class InlineStrike extends InlineNode {
  const InlineStrike(this.children);
  final List<InlineNode> children;
}

class InlineLink extends InlineNode {
  const InlineLink({required this.url, required this.children});
  final String url;
  final List<InlineNode> children;
}

class InlineImage extends InlineNode {
  const InlineImage({required this.alt, required this.url});
  final String alt;
  final String url;
}

class InlineFootnote extends InlineNode {
  const InlineFootnote(this.label);
  final String label;
}

class InlineStyled extends InlineNode {
  const InlineStyled(this.tag, this.children);
  final String tag;
  final List<InlineNode> children;
}

class _FootnoteSyntax extends md.InlineSyntax {
  _FootnoteSyntax() : super(r'\[\^([^\]]+)\]');
  @override
  bool onMatch(md.InlineParser parser, Match match) {
    parser.addNode(md.Element.text('quikpad-footnote', match.group(1)!));
    return true;
  }
}

List<InlineNode> parseInline(String text) {
  if (text.isEmpty) return const [];
  final markup = md.markdownToHtml(text, inlineOnly: true,
    extensionSet: md.ExtensionSet.gitHubWeb, inlineSyntaxes: [_FootnoteSyntax()]);
  return inlineFromHtml(html.parseFragment(markup).nodes);
}

List<InlineNode> inlineFromHtml(List<dom.Node> nodes, [int depth = 0]) {
  final result = <InlineNode>[];
  for (final node in nodes) {
    if (node is dom.Text) {
      result.add(InlineText(node.data));
    } else if (node is dom.Element) {
      final tag = node.localName;
      if (const {'script', 'style', 'iframe', 'object', 'embed', 'input', 'button'}.contains(tag)) continue;
      final children = depth < 32 ? inlineFromHtml(node.nodes, depth + 1)
          : [InlineText(node.text)];
      switch (tag) {
        case 'code': case 'kbd': case 'samp':
          result.add(InlineCode(node.text));
        case 'strong': case 'b':
          result.add(InlineStrong(children));
        case 'em': case 'i':
          result.add(InlineEmphasis(children));
        case 'del': case 's': case 'strike':
          result.add(InlineStrike(children));
        case 'u': case 'ins': case 'mark': case 'sub': case 'sup':
          if (tag == 'sup' && (node.classes.contains('footnote-ref') ||
              node.attributes['id']?.startsWith('fnref') == true)) {
            final target = node.querySelector('a')?.attributes['href'] ?? '';
            result.add(InlineFootnote(target.startsWith('#fn-') ? target.substring(4) : node.text));
          } else {
            result.add(InlineStyled(tag!, children));
          }
        case 'a':
          final href = node.attributes['href'];
          if (href != null) {
            result.add(InlineLink(url: href, children: children));
          } else {
            result.addAll(children);
          }
        case 'img':
          result.add(InlineImage(alt: node.attributes['alt'] ?? '', url: node.attributes['src'] ?? ''));
        case 'br':
          result.add(const InlineText('\n'));
        case 'quikpad-footnote':
          result.add(InlineFootnote(node.text));
        default:
          result.addAll(children);
      }
    }
  }
  return result;
}

String inlinePlainText(List<InlineNode> nodes) {
  final buffer = StringBuffer();
  void append(List<InlineNode> items) {
    for (final node in items) {
      if (node is InlineText) {
        buffer.write(node.text);
      } else if (node is InlineCode) {
        buffer.write(node.text);
      } else if (node is InlineStrong) {
        append(node.children);
      } else if (node is InlineEmphasis) {
        append(node.children);
      } else if (node is InlineStrike) {
        append(node.children);
      } else if (node is InlineStyled) {
        append(node.children);
      } else if (node is InlineLink) {
        append(node.children);
      } else if (node is InlineImage) {
        buffer.write(node.alt);
      } else if (node is InlineFootnote) {
        buffer.write('[^${node.label}]');
      }
    }
  }

  append(nodes);
  return buffer.toString().replaceAll(RegExp(r'[ \t]+'), ' ').trim();
}
