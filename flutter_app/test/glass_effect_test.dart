import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:liquid_ink/widgets/glass.dart';
import 'package:liquid_ink/widgets/glass_effect.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('dark neutral backdrop does not acquire green seams at transparent texture edges', () async {
    await GlassResources.load();
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    canvas.drawRect(const Rect.fromLTWH(0, 0, 224, 256), Paint()..color = const Color(0xFF202020));
    final picture = recorder.endRecording();
    final background = await picture.toImage(256, 256);
    picture.dispose();
    final shader = GlassResources.program!.fragmentShader()
      ..setFloat(0, 256)..setFloat(1, 256)
      ..setImageSampler(0, background)
      ..setImageSampler(1, GlassResources.maps[GlassRefractionMode.standard]!);
    configureGlassShader(shader, bounds: const Rect.fromLTWH(0, 0, 256, 256),
      pixelRatio: 1, optics: const GlassOptics(saturation: 1), overLight: false);
    final output = ui.PictureRecorder();
    Canvas(output).drawRect(const Rect.fromLTWH(0, 0, 256, 256), Paint()..shader = shader);
    final rendered = output.endRecording();
    final image = await rendered.toImage(256, 256);
    final pixels = (await image.toByteData())!.buffer.asUint8List();
    final png = await image.toByteData(format: ui.ImageByteFormat.png);
    await File('../.tooling/dark-alpha-seam.png').writeAsBytes(png!.buffer.asUint8List());
    var worst = 0;
    for (var i = 0; i < pixels.length; i += 4) {
      final difference = (pixels[i] - pixels[i + 1]).abs();
      if (difference > worst) worst = difference;
    }
    image.dispose(); rendered.dispose(); shader.dispose(); background.dispose();
    expect(worst, lessThanOrEqualTo(1), reason: 'Neutral dark input must stay neutral where channel samples hit transparent edges');
  });

  test('reference defaults and deformation retain upstream behavior', () {
    const optics = GlassOptics();
    expect(optics.blurSigma(false), 6);
    expect(optics.blurSigma(true), 14);
    expect(optics.effectiveDisplacement(true), 35);
    expect(optics.saturation, 1.4);
    final near = glassDeformation(
      const Offset(150, 0),
      const Size(300, 100),
      .15,
    );
    expect(near.translation.dx, closeTo(2.25, .0001));
    expect(near.scale.dx, closeTo(1.0225, .0001));
    expect(near.scale.dy, closeTo(.98875, .0001));
    final far = glassDeformation(
      const Offset(351, 0),
      const Size(300, 100),
      .15,
    );
    expect(far.scale, const Offset(1, 1));
    expect(far.translation, Offset.zero);
  });

  test(
    'real fragment shader preserves identity and separates RGB refraction',
    () async {
      await GlassResources.load();
      expect(GlassResources.program, isNotNull);
      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder);
      for (var x = 0; x < 256; x++) {
        canvas.drawRect(
          Rect.fromLTWH(x.toDouble(), 0, 1, 256),
          Paint()..color = Color.fromARGB(255, x, (x * 7) % 256, 255 - x),
        );
      }
      final picture = recorder.endRecording();
      final background = await picture.toImage(256, 256);
      picture.dispose();
      final source = (await background.toByteData())!.buffer.asUint8List();

      Future<ui.Image> render(
        GlassOptics optics,
        GlassRefractionMode mode,
      ) async {
        final shader = GlassResources.program!.fragmentShader()
          ..setFloat(0, 256)
          ..setFloat(1, 256)
          ..setImageSampler(0, background)
          ..setImageSampler(1, GlassResources.maps[mode]!);
        configureGlassShader(
          shader,
          bounds: const Rect.fromLTWH(0, 0, 256, 256),
          pixelRatio: 1,
          optics: optics,
          overLight: false,
        );
        final recorder = ui.PictureRecorder();
        Canvas(recorder).drawRect(
          const Rect.fromLTWH(0, 0, 256, 256),
          Paint()..shader = shader,
        );
        final picture = recorder.endRecording();
        final image = await picture.toImage(256, 256);
        picture.dispose();
        shader.dispose();
        return image;
      }

      final identity = await render(
        const GlassOptics(displacement: 0, saturation: 1),
        GlassRefractionMode.standard,
      );
      final original = (await identity.toByteData())!.buffer.asUint8List();
      // Minor backend rounding is acceptable; a coordinate flip is not.
      for (var i = 0; i < source.length; i++) {
        expect((source[i] - original[i]).abs(), lessThanOrEqualTo(1));
      }
      identity.dispose();
      for (final mode in GlassRefractionMode.values) {
        final plain = await render(const GlassOptics(aberration: 0), mode);
        final chromatic = await render(const GlassOptics(), mode);
        final a = (await plain.toByteData())!.buffer.asUint8List();
        final b = (await chromatic.toByteData())!.buffer.asUint8List();
        var changed = 0;
        for (var i = 0; i < a.length; i += 4) {
          if ((a[i + 1] - b[i + 1]).abs() > 2 ||
              (a[i + 2] - b[i + 2]).abs() > 2) {
            changed++;
          }
        }
        expect(
          changed,
          greaterThan(200),
          reason: '${mode.name} must actually refract separate channels',
        );
        if (Platform.environment['QUIKPAD_VISUALS'] == '1') {
          final png = await chromatic.toByteData(
            format: ui.ImageByteFormat.png,
          );
          await File(
            '../.tooling/quikpad-${mode.name}.png',
          ).writeAsBytes(png!.buffer.asUint8List());
        }
        plain.dispose();
        chromatic.dispose();
      }
      background.dispose();
    },
  );

  testWidgets('nested controls share a backdrop and glass can be disabled', (
    tester,
  ) async {
    Future<void> pump(bool glass, bool reduced) => tester.pumpWidget(
      GlassScope(
        settings: AppSettings(glassEffectsEnabled: glass),
        child: MaterialApp(
          home: MediaQuery(
            data: MediaQueryData(disableAnimations: reduced),
            child: Scaffold(
              body: Center(
                child: GlassPanel(
                  child: GlassButton(label: 'Sharp content', onPressed: () {}),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await pump(true, false);
    await tester.pumpAndSettle();
    expect(find.byType(BackdropFilter), findsOneWidget);
    expect(find.byType(ImageFiltered), findsNothing);
    final gesture = await tester.startGesture(
      tester.getCenter(find.text('Sharp content')),
    );
    await tester.pump(const Duration(milliseconds: 210));
    final animated = tester
        .widgetList<AnimatedContainer>(find.byType(AnimatedContainer))
        .toList();
    expect(
      animated.any((widget) => widget.transform!.storage[0] == .96),
      isTrue,
    );
    await gesture.up();
    await tester.pumpAndSettle();
    await pump(true, true);
    await tester.pumpAndSettle();
    expect(
      tester
          .widgetList<AnimatedContainer>(find.byType(AnimatedContainer))
          .every((widget) => widget.duration == Duration.zero),
      isTrue,
    );
    await pump(false, true);
    await tester.pumpAndSettle();
    expect(find.byType(BackdropFilter), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Impeller filters the backdrop inside the panel clip', (
    tester,
  ) async {
    if (!ui.ImageFilter.isShaderFilterSupported) {
      markTestSkipped(
        'This host does not expose Impeller shader image filters.',
      );
      return;
    }
    await tester.runAsync(GlassResources.load);
    final shader = GlassResources.program!.fragmentShader()
      ..setImageSampler(1, GlassResources.maps[GlassRefractionMode.standard]!);
    tester.view.physicalSize = const Size(400, 240);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final key = GlobalKey();
    await tester.pumpWidget(
      MaterialApp(
        home: RepaintBoundary(
          key: key,
          child: Stack(
            fit: StackFit.expand,
            children: [
              CustomPaint(painter: _GridPainter()),
              Positioned(
                left: 50,
                top: 40,
                width: 300,
                height: 160,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(32),
                  child: BackdropFilter(
                    filterConfig: GlassFilterConfig(
                      shader: shader,
                      optics: const GlassOptics(),
                      pixelRatio: 1,
                      overLight: false,
                    ),
                    child: const Center(
                      child: Text(
                        'QuikPad',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 26,
                          decoration: TextDecoration.none,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final render =
        key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
    final image = await tester.runAsync(() => render.toImage());
    final bytes = await tester.runAsync(
      () => image!.toByteData(format: ui.ImageByteFormat.png),
    );
    final raw = (await tester.runAsync(
      () => image!.toByteData(),
    ))!.buffer.asUint8List();
    // Pixels outside the clipped panel must retain the unfiltered backdrop.
    int channel(int x, int y, int c) => raw[(y * 400 + x) * 4 + c];
    expect(channel(10, 10, 2), 160);
    expect(channel(390, 210, 0), 207);
    // Interior grid colors must be affected by the actual backdrop filter.
    expect(channel(70, 70, 2), isNot(160));
    if (Platform.environment['QUIKPAD_VISUALS'] == '1') {
      await tester.runAsync(
        () => File(
          '../.tooling/quikpad-backdrop.png',
        ).writeAsBytes(bytes!.buffer.asUint8List()),
      );
    }
    image!.dispose();
    await tester.pumpWidget(const SizedBox());
    shader.dispose();
    expect(tester.takeException(), isNull);
  });

  testWidgets('non-interactive glass does not deform during a scroll drag', (
    tester,
  ) async {
    await tester.pumpWidget(
      const GlassScope(
        settings: AppSettings(),
        child: MaterialApp(
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 360,
                height: 240,
                child: GlassPanel(child: Text('static surface')),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final panel = tester.widget<AnimatedContainer>(
      find.byType(AnimatedContainer).first,
    );
    final before = List<double>.from(panel.transform!.storage);
    final gesture = await tester.startGesture(
      tester.getCenter(find.text('static surface')),
    );
    await gesture.moveBy(const Offset(100, 40));
    await tester.pump(const Duration(milliseconds: 220));
    final after = tester
        .widget<AnimatedContainer>(find.byType(AnimatedContainer).first)
        .transform!
        .storage;
    expect(after[0], before[0]);
    expect(after[5], before[5]);
    expect(after[12], before[12]);
    expect(after[13], before[13]);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'glass surface stays bounded across resize and intensity changes',
    (tester) async {
      final key = GlobalKey();
      for (final width in [320.0, 600.0]) {
        for (final intensity in GlassIntensity.values) {
          await tester.pumpWidget(
            GlassScope(
              settings: AppSettings(
                glassIntensity: intensity,
                animationsEnabled: false,
              ),
              child: MaterialApp(
                home: Scaffold(
                  body: Center(
                    child: RepaintBoundary(
                      key: key,
                      child: SizedBox(
                        width: width,
                        height: 180,
                        child: const GlassPanel(child: Text('QuikPad')),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          );
          await tester.pumpAndSettle();
          final render =
              key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
          expect(render.size, Size(width, 180));
          expect(tester.takeException(), isNull);
        }
      }
    },
  );
}

class _GridPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    for (var x = 0; x < size.width; x += 20) {
      for (var y = 0; y < size.height; y += 20) {
        canvas.drawRect(
          Rect.fromLTWH(x.toDouble(), y.toDouble(), 20, 20),
          Paint()
            ..color = ((x + y) ~/ 20).isEven
                ? const Color(0xFF1959A0)
                : const Color(0xFFCF9841),
        );
      }
    }
  }

  @override
  bool shouldRepaint(_GridPainter oldDelegate) => false;
}
