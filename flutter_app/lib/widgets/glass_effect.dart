import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

import '../core/settings.dart';

enum GlassRefractionMode { standard, polar, prominent }

/// Defaults and formulas follow liquid-glass-react/src/index.tsx.
@immutable
class GlassOptics {
  const GlassOptics({
    this.displacement = 70,
    this.blurAmount = 0.0625,
    this.saturation = 1.4,
    this.aberration = 2,
    this.elasticity = 0.15,
  });
  final double displacement;
  final double blurAmount;
  final double saturation;
  final double aberration;
  final double elasticity;

  factory GlassOptics.forIntensity(GlassIntensity intensity) =>
      switch (intensity) {
        GlassIntensity.low => const GlassOptics(
          displacement: 35,
          blurAmount: 0.025,
          saturation: 1.2,
          aberration: 1,
          elasticity: 0.08,
        ),
        GlassIntensity.medium => const GlassOptics(),
        GlassIntensity.high => const GlassOptics(
          displacement: 90,
          blurAmount: 0.1,
          saturation: 1.6,
          aberration: 3,
          elasticity: 0.22,
        ),
      };

  double blurSigma(bool overLight) => (overLight ? 12 : 4) + blurAmount * 32;
  double effectiveDisplacement(bool overLight) =>
      displacement * (overLight ? 0.5 : 1);
  double get softenSigma => math.max(0.1, 0.5 - aberration * 0.1);
}

/// CPU-owned shared resources, loaded once; each panel owns its shader instance.
class GlassResources {
  static ui.FragmentProgram? program;
  static final maps = <GlassRefractionMode, ui.Image>{};
  static Future<void>? _loading;

  static Future<void> load() => _loading ??= _load();

  static Future<void> _load() async {
    try {
      program = await ui.FragmentProgram.fromAsset('shaders/liquid_glass.frag');
      for (final mode in GlassRefractionMode.values) {
        final data = await rootBundle.load('assets/glass/${mode.name}.png');
        final codec = await ui.instantiateImageCodec(data.buffer.asUint8List());
        maps[mode] = (await codec.getNextFrame()).image;
        codec.dispose();
      }
    } catch (error) {
      // Keep the same partial-support fallback as the reference's browser path.
      debugPrint('Glass resources unavailable: ${error.runtimeType}');
      program = null;
    }
  }
}

/// Resolves position-dependent shader uniforms during paint, not after a frame.
@immutable
class GlassFilterConfig implements ImageFilterConfig {
  const GlassFilterConfig({
    required this.shader,
    required this.optics,
    required this.pixelRatio,
    required this.overLight,
    this.blurOverride,
  });
  final ui.FragmentShader? shader;
  final GlassOptics optics;
  final double pixelRatio;
  final bool overLight;
  final double? blurOverride;

  @override
  ui.ImageFilter? get filter => null;
  @override
  String get debugShortDescription => 'liquid-glass-react';

  @override
  ui.ImageFilter resolve(ImageFilterContext context) {
    final sigma = blurOverride ?? optics.blurSigma(overLight);
    final blur = ui.ImageFilter.blur(
      sigmaX: sigma,
      sigmaY: sigma,
      tileMode: ui.TileMode.clamp,
    );
    final current = shader;
    if (current == null || !ui.ImageFilter.isShaderFilterSupported) {
      // Saturation and blur remain available on the Skia fallback backend.
      final s = optics.saturation;
      final r = .213 * (1 - s), g = .715 * (1 - s), b = .072 * (1 - s);
      return ui.ImageFilter.compose(
        inner: blur,
        outer: ui.ColorFilter.matrix([
          r + s,
          g,
          b,
          0,
          0,
          r,
          g + s,
          b,
          0,
          0,
          r,
          g,
          b + s,
          0,
          0,
          0,
          0,
          0,
          1,
          0,
        ]),
      );
    }
    configureGlassShader(
      current,
      bounds: context.bounds,
      pixelRatio: pixelRatio,
      optics: optics,
      overLight: overLight,
    );
    return ui.ImageFilter.compose(
      inner: ui.ImageFilter.compose(
        inner: blur,
        outer: ui.ImageFilter.shader(current),
      ),
      outer: ui.ImageFilter.blur(
        sigmaX: optics.softenSigma,
        sigmaY: optics.softenSigma,
      ),
    );
  }
}

void configureGlassShader(
  ui.FragmentShader shader, {
  required ui.Rect bounds,
  required double pixelRatio,
  required GlassOptics optics,
  required bool overLight,
}) {
  shader
    ..setFloat(2, bounds.left)
    ..setFloat(3, bounds.top)
    ..setFloat(4, bounds.width)
    ..setFloat(5, bounds.height)
    ..setFloat(6, pixelRatio)
    ..setFloat(7, optics.effectiveDisplacement(overLight))
    ..setFloat(8, optics.aberration)
    ..setFloat(9, optics.saturation);
}

/// Reference's 200px activation zone and directional scaling/translation.
({ui.Offset translation, ui.Offset scale}) glassDeformation(
  ui.Offset delta,
  ui.Size size,
  double elasticity,
) {
  final edgeX = math.max(0.0, delta.dx.abs() - size.width / 2);
  final edgeY = math.max(0.0, delta.dy.abs() - size.height / 2);
  final distance = math.sqrt(edgeX * edgeX + edgeY * edgeY);
  if (distance > 200 || delta.distance == 0 || elasticity == 0) {
    return (translation: ui.Offset.zero, scale: const ui.Offset(1, 1));
  }
  final fade = 1 - distance / 200;
  final direction = delta / delta.distance;
  final stretch = math.min(delta.distance / 300, 1.0) * elasticity * fade;
  return (
    translation: delta * (elasticity * .1 * fade),
    scale: ui.Offset(
      math.max(
        .8,
        1 +
            direction.dx.abs() * stretch * .3 -
            direction.dy.abs() * stretch * .15,
      ),
      math.max(
        .8,
        1 +
            direction.dy.abs() * stretch * .3 -
            direction.dx.abs() * stretch * .15,
      ),
    ),
  );
}
