import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:liquid_ink/widgets/glass.dart';
import 'package:liquid_ink/widgets/glass_effect.dart';

void main() {
  testWidgets('dark glass has no colored seam outside its right clip', (tester) async {
    await tester.runAsync(GlassResources.load);
    for (final ratio in [1.0, 2.75, 3.0]) {
      tester.view.physicalSize = Size(411 * ratio, 891 * ratio);
      tester.view.devicePixelRatio = ratio;
      final key = GlobalKey();
      await tester.pumpWidget(GlassScope(
        settings: const AppSettings(animationsEnabled: false),
        child: MaterialApp(theme: ThemeData.dark(), home: RepaintBoundary(
          key: key,
          child: const ColoredBox(color: Color(0xFF12221D), child: Center(
            child: SizedBox(width: 375, height: 320,
              child: GlassPanel(child: SizedBox.expand())),
          )),
        )),
      ));
      await tester.pumpAndSettle();
      final render = key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final bitmap = render.toImageSync(pixelRatio: ratio);
      final pixels = (await tester.runAsync(() => bitmap.toByteData()))!.buffer.asUint8List();
      final png = await tester.runAsync(() => bitmap.toByteData(format: ui.ImageByteFormat.png));
      await tester.runAsync(() => File('../.tooling/dark-edge-$ratio.png').writeAsBytes(png!.buffer.asUint8List()));
      // Shadows may darken pixels, but cannot introduce a green/red split.
      var largestJump = 0;
      for (var y = (310 * ratio).round(); y < (580 * ratio).round(); y++) {
        for (var x = (393 * ratio).ceil(); x < (410 * ratio).floor(); x++) {
          final i = (y * bitmap.width + x) * 4;
          final previous = i - 4;
          final delta = ((pixels[i + 1] - pixels[previous + 1]) -
              (pixels[i] - pixels[previous])).abs();
          if (delta > largestJump) largestJump = delta;
        }
      }
      expect(largestJump, lessThan(12), reason: 'No chromatic seam outside the panel at DPR $ratio');
      bitmap.dispose();
    }
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  });
}
