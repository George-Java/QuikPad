import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/document_assets.dart';
import 'package:liquid_ink/markdown/inline.dart';
import 'package:liquid_ink/widgets/markdown_image.dart';
import 'package:liquid_ink/widgets/rich_markdown_text.dart';
import 'package:path/path.dart' as p;

void main() {
  test('relative image is resolved beside the current Markdown document', () async {
    final root = await Directory.systemTemp.createTemp('quikpad-images-');
    addTearDown(() async {
      if (await root.exists()) await root.delete(recursive: true);
    });
    final document = File(p.join(root.path, 'notes.md'));
    await document.writeAsString('# Notes');
    final image = File(p.join(root.path, 'assets', 'images', 'test.png'));
    await image.parent.create(recursive: true);
    await image.writeAsBytes([137, 80, 78, 71]);

    final resolved = await DocumentAssets.resolve(
      document.path,
      'assets/images/test.png',
    );
    expect(resolved, image.path);
    expect(DocumentAssets.localReference('assets/images/test.png'),
        'assets/images/test.png');
    expect(DocumentAssets.localReference('https://example.com/a.png'), isNull);
    expect(DocumentAssets.localReference('data:image/png;base64,AA=='), isNull);
  });

  testWidgets('inline images inherit the preview document scope', (tester) async {
    await tester.pumpWidget(
      DocumentImageScope(
        basePath: '/tmp/notes.md',
        child: MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => RichMarkdownText(
                nodes: parseInline('before ![photo](assets/images/test.png) after'),
                style: const TextStyle(fontSize: 16),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    final image = find.byType(MarkdownImage);
    expect(image, findsOneWidget);
    expect(
      DocumentImageScope.of(tester.element(image)),
      '/tmp/notes.md',
    );
    expect(tester.takeException(), isNull);
  });
}
