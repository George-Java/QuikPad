import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/document_store.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:liquid_ink/ui/editor.dart';
import 'package:liquid_ink/ui/theme.dart';
import 'package:liquid_ink/widgets/glass.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> pumpFor(WidgetTester tester, int milliseconds) async {
  const step = 50;
  for (var elapsed = 0; elapsed < milliseconds; elapsed += step) {
    await tester.pump(const Duration(milliseconds: step));
  }
}

class ActionStore extends DocumentStore {
  String? savedText;
  String? renamedTo;
  bool deleted = false;
  int saveCount = 0;

  @override
  Future<void> saveDocument(NoteDocument document) async {
    savedText = document.text;
    saveCount++;
  }

  @override
  Future<String> renameDocument(NoteDocument document, String name) async {
    renamedTo = name;
    return '/private/docs/renamed.md';
  }

  @override
  Future<void> deleteDocument(NoteDocument document) async {
    deleted = true;
  }
}

class GatedStore extends ActionStore {
  final entered = Completer<void>();
  final release = Completer<void>();

  @override
  Future<void> saveDocument(NoteDocument document) async {
    if (!entered.isCompleted) entered.complete();
    await release.future;
    savedText = document.text;
    saveCount++;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SettingsController settings;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    settings = SettingsController(await SharedPreferences.getInstance())
      ..setAnimationsEnabled(false)
      ..setGlassEffectsEnabled(false)
      ..setAutoSaveEnabled(false);
  });

  tearDown(() => settings.dispose());

  Future<void> openEditor(
    WidgetTester tester,
    ActionStore store, {
    String text = '# Title\n\nbody',
  }) async {
    final document = NoteDocument(
      path: '/private/docs/probe.md',
      name: 'probe.md',
      text: text,
    );
    await tester.pumpWidget(
      GlassScope(
        settings: settings.value,
        child: MaterialApp(
          theme: buildInkTheme(brightness: Brightness.light),
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () => Navigator.of(context).push<void>(
                    MaterialPageRoute(
                      builder: (_) => EditorScreen(
                        document: document,
                        store: store,
                        settings: settings,
                      ),
                    ),
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await pumpFor(tester, 600);
  }

  testWidgets('rename dialog does not dispose its controller early', (
    tester,
  ) async {
    final store = ActionStore();
    await openEditor(tester, store);

    await tester.tap(find.byTooltip('更多文档操作'));
    await pumpFor(tester, 400);
    await tester.tap(find.text('重命名'));
    await pumpFor(tester, 400);
    final promptField = tester.widget<TextField>(find.byType(TextField).last);
    expect(promptField.autocorrect, isTrue);
    expect(promptField.enableSuggestions, isTrue);
    await tester.enterText(find.byType(TextField).last, 'renamed');
    await tester.tap(find.widgetWithText(FilledButton, '重命名'));
    await pumpFor(tester, 600);

    expect(store.renamedTo, 'renamed');
    expect(find.text('renamed.md'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('search dialog does not dispose its controller early', (
    tester,
  ) async {
    final store = ActionStore();
    await openEditor(tester, store, text: 'hello world\n\nsecond paragraph');

    await tester.tap(find.byTooltip('查找文本'));
    await pumpFor(tester, 400);
    await tester.enterText(find.byType(TextField).last, 'world');
    await tester.tap(find.widgetWithText(FilledButton, '查找'));
    await pumpFor(tester, 600);

    final field = tester.widget<TextField>(find.byType(TextField).first);
    expect(field.controller!.text, contains('hello world'));
    expect(field.controller!.selection.start, 6);
    expect(field.controller!.selection.end, 11);
    expect(tester.takeException(), isNull);
  });

  testWidgets('auto-save writes once after the configured idle delay', (
    tester,
  ) async {
    settings
      ..setAutoSaveEnabled(true)
      ..setAutoSaveSeconds(3);
    final store = ActionStore();
    await openEditor(tester, store);

    await tester.tap(find.text('源码'));
    await tester.pump();
    await tester.pump();
    await tester.enterText(find.byType(TextField), '# Title\n\nchanged');
    await pumpFor(tester, 1000);
    expect(store.savedText, isNull);

    await pumpFor(tester, 3000);
    expect(store.savedText, contains('changed'));
    expect(store.saveCount, 1);

    await pumpFor(tester, 3500);
    expect(store.saveCount, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('delete confirms, deletes and returns to the caller', (
    tester,
  ) async {
    final store = ActionStore();
    await openEditor(tester, store);

    await tester.tap(find.byTooltip('更多文档操作'));
    await pumpFor(tester, 400);
    await tester.tap(find.text('删除本地副本'));
    await pumpFor(tester, 400);
    await tester.tap(find.widgetWithText(FilledButton, '删除'));
    await pumpFor(tester, 800);

    expect(store.deleted, isTrue);
    expect(find.text('open'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('mode switches retain the edited text', (tester) async {
    final store = ActionStore();
    await openEditor(tester, store);

    await tester.tap(find.text('源码'));
    await tester.pump();
    await tester.pump();
    await tester.enterText(find.byType(TextField), '# Title\n\nedited text');
    await tester.pump();
    await tester.tap(find.text('预览'));
    await pumpFor(tester, 400);
    await tester.tap(find.text('源码'));
    await pumpFor(tester, 400);

    final field = tester.widget<TextField>(find.byType(TextField).first);
    expect(field.controller!.text, contains('edited text'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('task checkboxes toggle their own source line', (tester) async {
    final semantics = tester.ensureSemantics();
    final store = ActionStore();
    await openEditor(
      tester,
      store,
      text: '# Tasks\n\n- [ ] plain\n\n> - [ ] quoted\n',
    );

    final marks = find.byWidgetPredicate(
      (widget) => widget is Semantics && widget.properties.label == '未完成',
    );
    expect(marks, findsNWidgets(2));
    await tester.tap(marks.last);
    await tester.pump();
    await tester.tap(find.byTooltip('保存'));
    await pumpFor(tester, 300);

    expect(store.savedText, contains('- [ ] plain'));
    expect(store.savedText, contains('> - [x] quoted'));
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  testWidgets('footnote references open their definition', (tester) async {
    final store = ActionStore();
    await openEditor(
      tester,
      store,
      text: 'Reference [^note]\n\n[^note]: explanation',
    );

    await tester.tap(find.text('[note]'));
    await pumpFor(tester, 400);
    expect(find.text('脚注 · note'), findsOneWidget);
    expect(find.textContaining('explanation'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a revert during an in-flight write is saved again', (
    tester,
  ) async {
    final store = GatedStore();
    await openEditor(tester, store, text: '# Title\n\nA');
    await tester.tap(find.text('源码'));
    await tester.pump();
    await tester.pump();

    await tester.enterText(find.byType(TextField), '# Title\n\nB');
    await tester.pump();
    await tester.tap(find.byTooltip('保存'));
    await tester.pump();
    expect(store.entered.isCompleted, isTrue);

    // Revert to the already-saved text while the write of B is blocked.
    await tester.enterText(find.byType(TextField), '# Title\n\nA');
    await tester.pump();
    store.release.complete();
    await pumpFor(tester, 800);

    expect(store.savedText, '# Title\n\nA');
    expect(store.saveCount, greaterThanOrEqualTo(2));
    expect(tester.takeException(), isNull);
  });
}
