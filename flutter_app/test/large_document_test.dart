import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/document_store.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:liquid_ink/markdown/parser.dart';
import 'package:liquid_ink/ui/editor.dart';
import 'package:liquid_ink/ui/preview.dart';
import 'package:liquid_ink/ui/theme.dart';
import 'package:liquid_ink/widgets/glass.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  Future<void> mount(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(1080, 2160);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      GlassScope(
        settings: const AppSettings(animationsEnabled: false),
        child: MaterialApp(
          theme: buildInkTheme(brightness: Brightness.light),
          home: Scaffold(body: child),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  void expectBoundedParagraphs(WidgetTester tester) {
    for (final element in find.byType(RichText).evaluate()) {
      final render = element.renderObject;
      if (render is RenderParagraph) {
        expect(
          render.text.toPlainText().length,
          lessThanOrEqualTo(8192),
          reason: 'A lazy list must not lay out an entire large block.',
        );
      }
    }
    expect(tester.takeException(), isNull);
  }

  testWidgets(
    'images decode to a bounded bitmap instead of their original size',
    (tester) async {
      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder);
      canvas.drawRect(
        const Rect.fromLTWH(0, 0, 2400, 1200),
        Paint()..color = Colors.blue,
      );
      final picture = recorder.endRecording();
      final bitmap = (await tester.runAsync(
        () => picture.toImage(2400, 1200),
      ))!;
      final data = await tester.runAsync(
        () => bitmap.toByteData(format: ui.ImageByteFormat.png),
      );
      final uri = Uri.dataFromBytes(
        data!.buffer.asUint8List(),
        mimeType: 'image/png',
      );
      bitmap.dispose();
      picture.dispose();
      await mount(
        tester,
        MarkdownPreviewPane(
          document: parseMarkdown('![large]($uri)'),
          basePath: null,
        ),
      );
      final image = tester.widget<Image>(find.byType(Image));
      final provider = image.image;
      expect(provider, isA<ResizeImage>());
      expect((provider as ResizeImage).width, lessThanOrEqualTo(1536));
      expect(provider.height, lessThanOrEqualTo(1024));
      final size = await tester.runAsync(() async {
        final completer = Completer<Size>();
        final stream = provider.resolve(ImageConfiguration.empty);
        final listener = ImageStreamListener(
          (info, _) {
            if (!completer.isCompleted) {
              completer.complete(
                Size(info.image.width.toDouble(), info.image.height.toDouble()),
              );
            }
            info.dispose();
          },
          onError: (Object error, StackTrace? stack) {
            if (!completer.isCompleted) completer.completeError(error, stack);
          },
        );
        stream.addListener(listener);
        try {
          return await completer.future.timeout(const Duration(seconds: 10));
        } finally {
          stream.removeListener(listener);
        }
      });
      expect(size!.width, lessThanOrEqualTo(1536));
      expect(size.height, lessThanOrEqualTo(1024));
      // Decoding must preserve the 2:1 source ratio; exact-policy resizing
      // squashed it into the screen-width x 260dp box.
      expect(size.width / size.height, closeTo(2, 0.01));
    },
  );
  testWidgets('long code is bounded while scrolling and reaching the end', (
    tester,
  ) async {
    final code = List.generate(12000, (i) => 'print("row $i");').join('\n');
    final key = GlobalKey<MarkdownPreviewPaneState>();
    final document = parseMarkdown('# Start\n\n```text\n$code\n```\n\n# End');
    await mount(
      tester,
      MarkdownPreviewPane(key: key, document: document, basePath: null),
    );
    expectBoundedParagraphs(tester);
    for (var i = 0; i < 8; i++) {
      await tester.fling(
        find.byType(MarkdownPreviewPane),
        const Offset(0, -500),
        2000,
      );
      await tester.pumpAndSettle();
      expectBoundedParagraphs(tester);
    }
    key.currentState!.scrollToBlock(
      document.outline.last.blockIndex,
      animate: false,
    );
    await tester.pumpAndSettle();
    expect(find.text('End'), findsWidgets);
  });

  testWidgets('single long line never becomes one giant paragraph', (
    tester,
  ) async {
    await mount(
      tester,
      MarkdownPreviewPane(
        document: parseMarkdown(List.filled(30000, '内容😀').join()),
        basePath: null,
      ),
    );
    expectBoundedParagraphs(tester);
  });

  testWidgets('large table builds only a bounded number of visible cells', (
    tester,
  ) async {
    final rows = List.generate(1000, (i) => '| row $i | value $i |').join('\n');
    await mount(
      tester,
      MarkdownPreviewPane(
        document: parseMarkdown('| A | B |\n| --- | --- |\n$rows'),
        basePath: null,
      ),
    );
    expect(find.byType(RichText).evaluate().length, lessThan(150));
    expect(tester.takeException(), isNull);
  });

  testWidgets('large source edits keep the native editing buffer bounded', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final settings = SettingsController(await SharedPreferences.getInstance())
      ..setAutoSaveEnabled(false);
    final source = List.generate(
      1400,
      (i) => 'line $i ${'content ' * 10}',
    ).join('\n');
    await mount(
      tester,
      EditorScreen(
        document: NoteDocument(
          path: 'large.md',
          name: 'large.md',
          text: source,
        ),
        store: DocumentStore(),
        settings: settings,
      ),
    );
    await tester.tap(find.text('源码'));
    await tester.pumpAndSettle();
    final field = tester.widget<TextField>(find.byType(TextField).first);
    expect(
      field.controller!.text.length,
      lessThanOrEqualTo(8192),
      reason: 'RenderEditable must not shape the whole large file.',
    );
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    settings.dispose();
  });
}
