import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'glass_effect.dart';

/// One bounded backdrop pass per surface, with sharp content painted above it.
class RefractiveSurface extends StatefulWidget {
  const RefractiveSurface({
    super.key,
    required this.child,
    required this.optics,
    required this.enabled,
    required this.motion,
    required this.highContrast,
    required this.radius,
    required this.padding,
    this.margin,
    this.tint,
    this.blurOverride,
    this.interactive = false,
    this.mode = GlassRefractionMode.standard,
  });
  final Widget child;
  final GlassOptics optics;
  final bool enabled;
  final bool motion;
  final bool highContrast;
  final double radius;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final Color? tint;
  final double? blurOverride;
  final bool interactive;
  final GlassRefractionMode mode;

  @override
  State<RefractiveSurface> createState() => _RefractiveSurfaceState();
}

class _RefractiveSurfaceState extends State<RefractiveSurface> {
  ui.FragmentShader? _shader;
  Offset _delta = Offset.zero;
  Size _size = Size.zero;
  bool _hovered = false;
  bool _pressed = false;

  @override
  void initState() {
    super.initState();
    if (widget.enabled && ui.ImageFilter.isShaderFilterSupported) _loadShader();
  }

  Future<void> _loadShader() async {
    await GlassResources.load();
    if (!mounted || GlassResources.program == null) return;
    final map = GlassResources.maps[widget.mode];
    if (map == null) return;
    setState(() {
      _shader = GlassResources.program!.fragmentShader()
        ..setImageSampler(1, map);
    });
  }

  @override
  void didUpdateWidget(RefractiveSurface oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.enabled &&
        !oldWidget.enabled &&
        _shader == null &&
        ui.ImageFilter.isShaderFilterSupported) {
      _loadShader();
    }
    if (oldWidget.mode != widget.mode && _shader != null) {
      _shader!.setImageSampler(1, GlassResources.maps[widget.mode]!);
    }
    if (!widget.motion || !widget.enabled) {
      _delta = Offset.zero;
      _pressed = false;
      _hovered = false;
    }
  }

  void _track(Offset local) {
    if (!widget.interactive || !widget.motion || !widget.enabled) return;
    final box = context.findRenderObject();
    if (box is! RenderBox || !box.hasSize || box.size.isEmpty) return;
    setState(() {
      _size = box.size;
      _delta = local - _size.center(Offset.zero);
    });
  }

  void _reset() {
    if (!mounted) return;
    setState(() {
      _delta = Offset.zero;
      _pressed = false;
      _hovered = false;
    });
  }

  @override
  void dispose() {
    _shader?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final nested =
        context.dependOnInheritedWidgetOfExactType<_GlassLayer>() != null;
    final radius = BorderRadius.circular(widget.radius);
    final content = Padding(
      padding: widget.padding,
      child: _GlassLayer(child: widget.child),
    );
    if (!widget.enabled) {
      return Container(
        margin: widget.margin,
        decoration: BoxDecoration(
          color: scheme.surface,
          borderRadius: radius,
          border: Border.all(
            color: scheme.outlineVariant.withValues(alpha: .7),
          ),
        ),
        child: content,
      );
    }

    final deformation = glassDeformation(
      _delta,
      _size,
      widget.motion ? widget.optics.elasticity : 0,
    );
    final scale = _pressed && widget.interactive && widget.motion
        ? const Offset(.96, .96)
        : deformation.scale;
    final transform = Matrix4.identity()
      ..translateByDouble(
        deformation.translation.dx,
        deformation.translation.dy,
        0,
        1,
      )
      ..scaleByDouble(scale.dx, scale.dy, 1, 1);

    final surface = DecoratedBox(
      decoration: BoxDecoration(
        // The reference leaves the backdrop visible. Contrast mode deliberately
        // adds an opaque backing for readable text on arbitrary backgrounds.
        color: (widget.tint ?? scheme.surface).withValues(
          alpha: widget.highContrast ? .88 : (dark ? .20 : .18),
        ),
        borderRadius: radius,
      ),
      child: CustomPaint(
        foregroundPainter: _GlassHighlights(
          radius: widget.radius,
          dark: dark,
          hovered: _hovered && widget.interactive,
          pressed: _pressed && widget.interactive,
        ),
        child: content,
      ),
    );
    final optics = ClipRRect(
      borderRadius: radius,
      child: nested
          ? surface
          : BackdropFilter(
              filterConfig: GlassFilterConfig(
                shader: _shader,
                optics: widget.optics,
                pixelRatio: MediaQuery.devicePixelRatioOf(context),
                overLight: !dark,
                blurOverride: widget.blurOverride,
              ),
              child: surface,
            ),
    );

    return Padding(
      padding: widget.margin ?? EdgeInsets.zero,
      child: MouseRegion(
        onEnter: (event) {
          if (!widget.interactive || !widget.motion) return;
          setState(() => _hovered = true);
          _track(event.localPosition);
        },
        onHover: (event) => _track(event.localPosition),
        onExit: (_) => _reset(),
        child: Listener(
          onPointerDown: (event) {
            if (!widget.interactive || !widget.motion) return;
            setState(() => _pressed = true);
            _track(event.localPosition);
          },
          onPointerMove: (event) => _track(event.localPosition),
          onPointerUp: (_) => _reset(),
          onPointerCancel: (_) => _reset(),
          child: AnimatedContainer(
            duration: widget.motion
                ? const Duration(milliseconds: 200)
                : Duration.zero,
            curve: Curves.easeOut,
            transform: transform,
            transformAlignment: Alignment.center,
            decoration: BoxDecoration(
              borderRadius: radius,
              boxShadow: nested
                  ? []
                  : [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: dark ? .20 : .14),
                        blurRadius: dark ? 28 : 42,
                        spreadRadius: -5,
                        offset: const Offset(0, 13),
                      ),
                    ],
            ),
            // Reset paint origin and keep the filter bounded to this panel.
            // The neutral overlay ring covers clip/filter antialiasing seams.
            child: Stack(
              fit: StackFit.passthrough,
              children: [
                RepaintBoundary(child: optics),
                Positioned.fill(
                  child: IgnorePointer(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        borderRadius: radius,
                        border: Border.all(
                          color: dark
                              ? Colors.white.withValues(alpha: .16)
                              : Colors.black.withValues(alpha: .08),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _GlassLayer extends InheritedWidget {
  const _GlassLayer({required super.child});
  @override
  bool updateShouldNotify(_GlassLayer oldWidget) => false;
}

/// A restrained top sheen plus pointer feedback. The continuous edge is painted
/// by the neutral overlay ring, never by a diagonal gradient stroke: a gradient
/// stroke used to leave alternating green/black segments on the right edge in
/// dark mode.
class _GlassHighlights extends CustomPainter {
  const _GlassHighlights({
    required this.radius,
    required this.dark,
    required this.hovered,
    required this.pressed,
  });
  final double radius;
  final bool dark;
  final bool hovered;
  final bool pressed;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final shape = RRect.fromRectAndRadius(
      rect.deflate(.75),
      Radius.circular(radius),
    );
    // Top-only specular line. It cannot create a vertical side seam.
    canvas.save();
    canvas.clipRRect(shape);
    final sheen = Paint()
      ..blendMode = BlendMode.screen
      ..shader = ui.Gradient.linear(
        Offset.zero,
        Offset(size.width, 0),
        [
          Colors.transparent,
          Colors.white.withValues(alpha: dark ? .16 : .52),
          Colors.transparent,
        ],
        const [0.0, 0.5, 1.0],
      );
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height < 1.4 ? size.height : 1.4),
      sheen,
    );
    canvas.restore();

    if (hovered || pressed) {
      canvas.save();
      canvas.clipRRect(shape);
      canvas.drawRect(
        rect,
        Paint()
          ..blendMode = BlendMode.overlay
          ..shader = ui.Gradient.radial(
            Offset(size.width / 2, 0),
            size.longestSide * .8,
            [
              Colors.white.withValues(alpha: pressed ? .5 : .25),
              Colors.transparent,
            ],
          ),
      );
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(_GlassHighlights oldDelegate) =>
      radius != oldDelegate.radius ||
      dark != oldDelegate.dark ||
      hovered != oldDelegate.hovered ||
      pressed != oldDelegate.pressed;

}
