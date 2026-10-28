import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/markdown/inline.dart';
import 'package:liquid_ink/markdown/model.dart';
import 'package:liquid_ink/markdown/parser.dart';

void main() {
  test('HTML headings, named anchors, links and images become native blocks', () {
    final doc = parseMarkdown('''
<h2 id="chapter">HTML title</h2>

<a name="target"></a>
<p>Text <a href="https://example.com">click</a> <strong>bold</strong><br>new line</p>
<img src="assets/images/test.png" alt="local">
''');
    expect(doc.outline.single.title, 'HTML title');
    expect(doc.anchors['chapter'], 0);
    expect(doc.anchors.containsKey('target'), isTrue);
    expect(doc.blocks.whereType<ImageBlock>().single.url, 'assets/images/test.png');
    final text = doc.blocks.whereType<ParagraphBlock>().map((b) => b.text).join();
    final nodes = parseInline(text);
    expect(nodes.whereType<InlineLink>().single.url, 'https://example.com');
    expect(nodes.whereType<InlineStrong>(), isNotEmpty);
    expect(inlinePlainText(nodes), contains('new line'));
  });

  test('reference links, nested emphasis, entities and multi-backtick code', () {
    final doc = parseMarkdown('''
[visit][site] and ![photo][picture]

[site]: https://example.com "Title"
[picture]: assets/images/test.png
''');
    final nodes = parseInline(doc.blocks.whereType<ParagraphBlock>().single.text);
    expect(nodes.whereType<InlineLink>().single.url, 'https://example.com');
    expect(nodes.whereType<InlineImage>().single.url, 'assets/images/test.png');
    final nested = parseInline('**bold with *emphasis*** &amp; ``a ` b`` <u>underline</u>');
    expect(nested.whereType<InlineStrong>().single.children.whereType<InlineEmphasis>(), isNotEmpty);
    expect(nested.whereType<InlineCode>().single.text, 'a ` b');
    expect(inlinePlainText(nested), contains('&'));
    expect(nested.whereType<InlineStyled>().single.tag, 'u');
  });

  test('nested lists, ordered starts, indented code and quoted fences work', () {
    final doc = parseMarkdown('''
3. third
   - child
     - grandchild

    indented code

> quote
>
> ```python
> print("hello")
> ```
''');
    final list = doc.blocks.whereType<ListItemBlock>().toList();
    expect(list.first.number, 3);
    expect(list.map((b) => b.depth), containsAll([0, 1, 2]));
    expect(doc.blocks.whereType<CodeBlock>().any((b) => b.language == 'python'), isTrue);
  });

  test('task line provenance and footnote definitions survive parsing', () {
    const source = '# title\n\n- [ ] **first**\n- [x] second\n\nreference [^note]\n\n[^note]: explanation';
    final doc = parseMarkdown(source);
    expect(doc.blocks.whereType<ListItemBlock>().map((b) => b.line), [2, 3]);
    expect(doc.blocks.whereType<DefinitionBlock>(), isNotEmpty);
    expect(parseInline(doc.blocks.whereType<ParagraphBlock>().first.text).whereType<InlineFootnote>(), isNotEmpty);
  });

  test('HTML scripts do not execute or appear as document content', () {
    final doc = parseMarkdown('<script>alert("never")</script>\n\n<h2>Visible</h2>');
    expect(doc.blocks.whereType<ParagraphBlock>(), isEmpty);
    expect(doc.outline.single.title, 'Visible');
  });
  test('HTML headings keep inline links and named anchors', () {
    final doc = parseMarkdown('''
<h2 id="chapter">Chapter <a href="#target">jump</a></h2>

<a name="target"></a>
<p>Body</p>
''');
    final heading = doc.blocks.whereType<HeadingBlock>().single;
    final nodes = parseInline(heading.text);
    expect(nodes.whereType<InlineLink>().single.url, '#target');
    expect(inlinePlainText(nodes), 'Chapter jump');
    expect(doc.anchors['chapter'], 0);
    expect(doc.anchors['target'], isNotNull);
  });

  test('GFM autolinks and inline HTML formatting survive', () {
    final nodes = parseInline(
      '<https://example.com> <strong>bold</strong> <mark>marked</mark> <br> tail',
    );
    expect(nodes.whereType<InlineLink>().single.url, 'https://example.com');
    expect(nodes.whereType<InlineStrong>(), isNotEmpty);
    expect(nodes.whereType<InlineStyled>().map((n) => n.tag), contains('mark'));
  });

}
