import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:liquid_ink/markdown/model.dart';
import 'package:liquid_ink/markdown/parser.dart';
import 'package:liquid_ink/ui/preview.dart';
import 'package:liquid_ink/ui/theme.dart';
import 'package:liquid_ink/widgets/glass.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

void main() {
  Future<void> pumpPreview(
    WidgetTester tester,
    MarkdownDocument document,
    GlobalKey<MarkdownPreviewPaneState> key,
  ) async {
    await tester.pumpWidget(
      GlassScope(
        settings: const AppSettings(
          glassEffectsEnabled: false,
          animationsEnabled: false,
        ),
        child: MaterialApp(
          theme: buildInkTheme(brightness: Brightness.light),
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 411,
                height: 891,
                child: MarkdownPreviewPane(
                  key: key,
                  document: document,
                  basePath: null,
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('scrolls 1200+ blocks and jumps by exact index', (tester) async {
    tester.view.physicalSize = const Size(1233, 2673);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final source = StringBuffer()
      ..writeln('# Start')
      ..writeln()
      ..writeln('| A | B |')
      ..writeln('| --- | --- |')
      ..writeln('| 1 | 2 |')
      ..writeln()
      ..writeln('> A quoted line.')
      ..writeln();
    for (var index = 0; index < 1200; index++) {
      source
        ..writeln('Paragraph $index with **bold** and `code`.')
        ..writeln();
    }
    source
      ..writeln('## Jump Target')
      ..writeln()
      ..writeln('END-OF-DOCUMENT');

    final document = parseMarkdown(source.toString());
    final target = document.outline.last.blockIndex;
    final key = GlobalKey<MarkdownPreviewPaneState>();
    await pumpPreview(tester, document, key);
    expect(tester.takeException(), isNull);

    key.currentState!.scrollToBlock(target, animate: false);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(key.currentState!.isIndexVisible(target), isTrue);
    expect(find.text('Jump Target'), findsWidgets);

    await tester.fling(
      find.byType(ScrollablePositionedList),
      const Offset(0, -760),
      1600,
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    key.currentState!.scrollToBlock(0, animate: false);
    await tester.pumpAndSettle();
    expect(key.currentState!.firstVisibleIndex, lessThan(4));
    expect(find.text('Start'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a 900 item list can jump to the final item', (tester) async {
    tester.view.physicalSize = const Size(1233, 2673);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final source = List.generate(900, (index) => '- list item $index').join('\n');
    final document = parseMarkdown(source);
    final key = GlobalKey<MarkdownPreviewPaneState>();
    await pumpPreview(tester, document, key);

    key.currentState!.scrollToBlock(document.blocks.length - 1, animate: false);
    await tester.pumpAndSettle();
    expect(
      key.currentState!.isIndexVisible(document.blocks.length - 1),
      isTrue,
    );
    expect(find.text('list item 899'), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
