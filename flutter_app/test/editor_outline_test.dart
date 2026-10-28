import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/document_store.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:liquid_ink/ui/editor.dart';
import 'package:liquid_ink/ui/theme.dart';
import 'package:liquid_ink/widgets/glass.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('outline entry closes the sheet and scrolls to the heading',
      (tester) async {
    tester.view.physicalSize = const Size(1233, 2673);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final settings = SettingsController(prefs)
      ..setGlassEffectsEnabled(false)
      ..setAnimationsEnabled(false);

    final source = StringBuffer('# Start\n\n');
    for (var index = 0; index < 300; index++) {
      source
        ..writeln('Paragraph $index.')
        ..writeln();
    }
    source
      ..writeln('### Jump Target')
      ..writeln()
      ..writeln('Target body END');

    await tester.pumpWidget(
      GlassScope(
        settings: settings.value,
        child: MaterialApp(
          theme: buildInkTheme(brightness: Brightness.light),
          home: EditorScreen(
            document: NoteDocument(
              path: '/tmp/outline-test.md',
              name: 'outline-test.md',
              text: source.toString(),
            ),
            store: DocumentStore(),
            settings: settings,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    await tester.tap(find.byTooltip('目录大纲'));
    await tester.pumpAndSettle();
    expect(find.text('Jump Target'), findsOneWidget);

    await tester.tap(find.text('Jump Target'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Jump Target'), findsWidgets);

    await tester.fling(
      find.byType(ScrollablePositionedList),
      const Offset(0, 500),
      1200,
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    await tester.tap(find.text('源码'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.text('实时'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsOneWidget);
    expect(find.byType(ScrollablePositionedList), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
