import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/app.dart';
import 'package:liquid_ink/core/document_store.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:liquid_ink/ui/home_screen.dart';
import 'package:liquid_ink/ui/settings_screen.dart';
import 'package:liquid_ink/ui/theme.dart';
import 'package:liquid_ink/widgets/glass.dart';
import 'package:liquid_ink/widgets/glass_effect.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeStore extends DocumentStore {
  _FakeStore(this.meta);

  final DocumentMeta meta;

  @override
  Future<List<DocumentMeta>> listDocuments() async => [meta];

  @override
  Future<NoteDocument> ensureWelcomeDocument() async {
    return NoteDocument(path: meta.path, name: meta.name, text: '# Start\n');
  }
}

void main() {
  testWidgets('home and settings render at phone size without layout errors', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1233, 2673);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final settings = SettingsController(prefs)
      ..setGlassEffectsEnabled(false)
      ..setAnimationsEnabled(false);
    final store = _FakeStore(
      DocumentMeta(
        path: '/tmp/home-test.md',
        name: 'home-test.md',
        modified: DateTime(2026, 10, 27, 12, 30),
        size: 4096,
      ),
    );

    await tester.pumpWidget(
      GlassScope(
        settings: settings.value,
        child: MaterialApp(
          theme: buildInkTheme(brightness: Brightness.light),
          home: HomeScreen(store: store, settings: settings),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('QuikPad'), findsOneWidget);
    expect(find.text('让文字\n安静地流动。'), findsOneWidget);
    expect(find.text('home-test.md'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(
      GlassScope(
        settings: settings.value,
        child: MaterialApp(
          theme: buildInkTheme(brightness: Brightness.light),
          home: SettingsScreen(controller: settings),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('阅读外观'), findsOneWidget);
    await tester.drag(find.byType(ListView), const Offset(0, -900));
    await tester.pumpAndSettle();
    expect(find.text('数据与隐私'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('app root boots with localizations and no network', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1233, 2673);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final settings = SettingsController(prefs)
      ..setGlassEffectsEnabled(false)
      ..setAnimationsEnabled(false);
    final store = _FakeStore(
      DocumentMeta(
        path: '/tmp/root-test.md',
        name: 'root-test.md',
        modified: DateTime(2026, 10, 27, 12, 30),
        size: 1024,
      ),
    );

    settings.setGlassEffectsEnabled(true);
    if (Platform.environment['QUIKPAD_DARK'] == '1') settings.setThemeMode(AppThemeMode.dark);
    await tester.runAsync(GlassResources.load);
    final key = GlobalKey();
    await tester.pumpWidget(
      RepaintBoundary(
        key: key,
        child: QuikPadApp(settings: settings, store: store),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('QuikPad'), findsOneWidget);
    expect(find.text('最近文档'), findsOneWidget);
    expect(tester.takeException(), isNull);
    if (Platform.environment['QUIKPAD_VISUALS'] == '1') {
      final render =
          key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final image = render.toImageSync();
      await tester.runAsync(() async {
        final png = await image.toByteData(format: ui.ImageByteFormat.png);
        await File(
          '../.tooling/quikpad-home.png',
        ).writeAsBytes(png!.buffer.asUint8List());
        image.dispose();
      });
    }
  });
}
