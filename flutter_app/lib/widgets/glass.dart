import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../core/settings.dart';
import 'glass_effect.dart';
import 'refractive_surface.dart';

class GlassScope extends InheritedWidget {
  const GlassScope({super.key, required this.settings, required super.child});

  final AppSettings settings;

  static AppSettings of(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<GlassScope>()?.settings ??
        const AppSettings();
  }

  @override
  bool updateShouldNotify(GlassScope oldWidget) =>
      oldWidget.settings != settings;
}

bool motionOf(BuildContext context) {
  final settings = GlassScope.of(context);
  final media = MediaQuery.maybeOf(context);
  final systemReduced =
      media?.disableAnimations ??
      WidgetsBinding
          .instance
          .platformDispatcher
          .accessibilityFeatures
          .disableAnimations;
  return settings.animationsEnabled && !systemReduced;
}

class LiquidBackground extends StatefulWidget {
  const LiquidBackground({
    super.key,
    required this.animate,
    required this.child,
  });

  final bool animate;
  final Widget child;

  @override
  State<LiquidBackground> createState() => _LiquidBackgroundState();
}

class _LiquidBackgroundState extends State<LiquidBackground>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 32),
  );

  @override
  void initState() {
    super.initState();
    _sync();
  }

  @override
  void didUpdateWidget(covariant LiquidBackground oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.animate != widget.animate) _sync();
  }

  void _sync() {
    if (widget.animate) {
      _controller.repeat();
    } else {
      _controller.stop();
      _controller.value = 0.28;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Stack(
      fit: StackFit.expand,
      children: [
        RepaintBoundary(
          child: CustomPaint(
            painter: _LiquidPainter(
              animation: _controller,
              scheme: scheme,
              dark: dark,
            ),
          ),
        ),
        widget.child,
      ],
    );
  }
}

class _LiquidPainter extends CustomPainter {
  _LiquidPainter({
    required this.animation,
    required this.scheme,
    required this.dark,
  }) : super(repaint: animation);

  final Animation<double> animation;
  final ColorScheme scheme;
  final bool dark;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final base = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          scheme.surface,
          scheme.surfaceContainerLow,
          scheme.surfaceContainer,
        ],
      ).createShader(rect);
    canvas.drawRect(rect, base);

    final phase = animation.value * math.pi * 2;
    final blobs = <_Blob>[
      _Blob(
        center: Offset(
          size.width * (0.18 + 0.06 * math.sin(phase)),
          size.height * (0.16 + 0.05 * math.cos(phase * 0.8)),
        ),
        radius: size.width * 0.52,
        color: scheme.primary.withValues(alpha: dark ? 0.20 : 0.20),
      ),
      _Blob(
        center: Offset(
          size.width * (0.90 + 0.04 * math.cos(phase * 0.7)),
          size.height * (0.34 + 0.06 * math.sin(phase * 0.9)),
        ),
        radius: size.width * 0.48,
        color: scheme.tertiary.withValues(alpha: dark ? 0.13 : 0.16),
      ),
      _Blob(
        center: Offset(
          size.width * (0.38 + 0.07 * math.cos(phase * 0.6 + 1)),
          size.height * (0.92 + 0.03 * math.sin(phase)),
        ),
        radius: size.width * 0.55,
        color: scheme.secondary.withValues(alpha: dark ? 0.10 : 0.13),
      ),
    ];

    for (final blob in blobs) {
      final paint = Paint()
        ..shader = ui.Gradient.radial(blob.center, blob.radius, [
          blob.color,
          blob.color.withValues(alpha: 0),
        ])
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 46);
      canvas.drawCircle(blob.center, blob.radius, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _LiquidPainter oldDelegate) {
    return oldDelegate.dark != dark || oldDelegate.scheme != scheme;
  }
}

class _Blob {
  const _Blob({
    required this.center,
    required this.radius,
    required this.color,
  });

  final Offset center;
  final double radius;
  final Color color;
}

class GlassPanel extends StatelessWidget {
  const GlassPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.margin,
    this.borderRadius = 24,
    this.blurOverride,
    this.tint,
    this.interactive = false,
    this.mode = GlassRefractionMode.standard,
  });
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final double borderRadius;
  final double? blurOverride;
  final Color? tint;
  final bool interactive;
  final GlassRefractionMode mode;

  @override
  Widget build(BuildContext context) {
    final settings = GlassScope.of(context);
    return RefractiveSurface(
      optics: GlassOptics.forIntensity(settings.glassIntensity),
      enabled: settings.glassEffectsEnabled,
      motion: motionOf(context),
      highContrast: settings.highContrast,
      radius: borderRadius,
      padding: padding,
      margin: margin,
      tint: tint,
      blurOverride: blurOverride,
      interactive: interactive,
      mode: mode,
      child: child,
    );
  }
}

class GlassIconButton extends StatelessWidget {
  const GlassIconButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final enabled = onPressed != null;
    return Tooltip(
      message: tooltip,
      child: GlassPanel(
        padding: EdgeInsets.zero,
        borderRadius: 23,
        interactive: enabled,
        child: Semantics(
          button: true,
          label: tooltip,
          enabled: enabled,
          child: Material(
            color: Colors.transparent,
            shape: const CircleBorder(),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: onPressed,
              customBorder: const CircleBorder(),
              child: SizedBox(
                width: 46,
                height: 46,
                child: Icon(
                  icon,
                  size: 21,
                  color: enabled
                      ? scheme.onSurfaceVariant
                      : scheme.onSurfaceVariant.withValues(alpha: 0.38),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class GlassButton extends StatelessWidget {
  const GlassButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.expanded = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final labelText = Text(
      label,
      textAlign: TextAlign.center,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(
        color: scheme.onPrimaryContainer,
        fontWeight: FontWeight.w600,
        fontSize: 15,
      ),
    );
    final content = Row(
      mainAxisSize: expanded ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 19, color: scheme.onPrimaryContainer),
          const SizedBox(width: 8),
        ],
        // An expanded button receives a bounded width and must be allowed to
        // ellipsize; otherwise a long label or a large text scale overflows the
        // Row (and the home hero card) instead of shrinking.
        if (expanded) Flexible(child: labelText) else labelText,
      ],
    );
    return GlassPanel(
      padding: EdgeInsets.zero,
      borderRadius: 18,
      tint: scheme.primaryContainer,
      interactive: onPressed != null,
      child: Semantics(
        button: true,
        label: label,
        child: Material(
          color: Colors.transparent,
          borderRadius: BorderRadius.circular(18),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onPressed,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              child: content,
            ),
          ),
        ),
      ),
    );
  }
}

class GlassSegmentedControl<T> extends StatelessWidget {
  const GlassSegmentedControl({
    super.key,
    required this.values,
    required this.selected,
    required this.labelOf,
    required this.onSelected,
  });

  final List<T> values;
  final T selected;
  final String Function(T) labelOf;
  final ValueChanged<T> onSelected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final motion = motionOf(context);
    final settings = GlassScope.of(context);
    // No BackdropFilter here: this control is often nested inside another glass
    // panel. A translucent surface keeps it visually consistent without a
    // second, expensive blur pass on every frame.
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: scheme.surface.withValues(
          alpha: settings.glassEffectsEnabled ? 0.36 : 0.05,
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.35),
        ),
      ),
      child: Material(
        type: MaterialType.transparency,
        child: Row(
          children: [
            for (final value in values)
              Expanded(
                child: Semantics(
                  button: true,
                  selected: value == selected,
                  label: labelOf(value),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(13),
                    onTap: () => onSelected(value),
                    child: AnimatedContainer(
                      duration: motion
                          ? const Duration(milliseconds: 180)
                          : Duration.zero,
                      curve: Curves.easeOut,
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: BoxDecoration(
                        color: value == selected
                            ? scheme.primary.withValues(alpha: 0.16)
                            : Colors.transparent,
                        borderRadius: BorderRadius.circular(13),
                      ),
                      child: Text(
                        labelOf(value),
                        textAlign: TextAlign.center,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: value == selected
                              ? scheme.primary
                              : scheme.onSurfaceVariant,
                          fontWeight: value == selected
                              ? FontWeight.w700
                              : FontWeight.w500,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
