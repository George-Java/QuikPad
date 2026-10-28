import 'package:flutter/material.dart';
import '../core/settings.dart';

class InkColors {
  const InkColors._();

  static const seed = Color(0xFF0E5E54);
  static const paper = Color(0xFFF8EEDB);
  static const paperTop = Color(0xFFFBF3E4);
  static const paperBottom = Color(0xFFEBDCC0);
  static const paperBorder = Color(0xFFD6C3A2);
  static const paperText = Color(0xFF32261A);
  static const paperSecondary = Color(0xFF5A4936);
  static const paperLink = Color(0xFF6E4429);
}

class ReaderPalette {
  const ReaderPalette({
    required this.gradient,
    required this.text,
    required this.secondary,
    required this.link,
    required this.border,
    required this.codeBackground,
  });

  final List<Color> gradient;
  final Color text;
  final Color secondary;
  final Color link;
  final Color border;
  final Color codeBackground;

  static ReaderPalette from(
    BuildContext context, {
    bool parchment = false,
    ReaderBackground background = ReaderBackground.paper,
  }) {
    if (background == ReaderBackground.mist) {
      return const ReaderPalette(
        gradient: [Color(0xFFEAF3E8), Color(0xFFDDE9DB)],
        text: Color(0xFF203725),
        secondary: Color(0xFF415743),
        link: Color(0xFF235A34),
        border: Color(0xFFA8BDA7),
        codeBackground: Color(0xFFD3E2D1),
      );
    }
    if (background == ReaderBackground.sand) {
      return const ReaderPalette(
        gradient: [Color(0xFFFFF4E8), Color(0xFFF3E3D3)],
        text: Color(0xFF3A2A24),
        secondary: Color(0xFF665044),
        link: Color(0xFF884023),
        border: Color(0xFFD3BBA9),
        codeBackground: Color(0xFFEAD8C6),
      );
    }
    if (background == ReaderBackground.midnight) {
      return const ReaderPalette(
        gradient: [Color(0xFF131F32), Color(0xFF19263B)],
        text: Color(0xFFE0E8F5),
        secondary: Color(0xFFB4C4DD),
        link: Color(0xFF97C5FF),
        border: Color(0xFF3B4C67),
        codeBackground: Color(0xFF24344D),
      );
    }
    if (background == ReaderBackground.black) {
      return const ReaderPalette(
        gradient: [Colors.black, Colors.black],
        text: Color(0xFFE2E2E2),
        secondary: Color(0xFFB9B9B9),
        link: Color(0xFF9DDDD0),
        border: Color(0xFF424242),
        codeBackground: Color(0xFF191919),
      );
    }
    if (parchment || background == ReaderBackground.parchment) {
      return const ReaderPalette(
        gradient: [InkColors.paperTop, InkColors.paperBottom],
        text: InkColors.paperText,
        secondary: InkColors.paperSecondary,
        link: InkColors.paperLink,
        border: InkColors.paperBorder,
        codeBackground: Color(0xFFEDE0C8),
      );
    }
    final scheme = Theme.of(context).colorScheme;
    return ReaderPalette(
      gradient: [
        scheme.surface.withValues(alpha: 0.92),
        scheme.surfaceContainerLow.withValues(alpha: 0.96),
      ],
      text: scheme.onSurface,
      secondary: scheme.onSurfaceVariant,
      link: scheme.primary,
      border: scheme.outlineVariant.withValues(alpha: 0.55),
      codeBackground: scheme.surfaceContainerHighest,
    );
  }
}

ThemeData buildInkTheme({
  required Brightness brightness,
  bool highContrast = false,
  AppColorTheme colorTheme = AppColorTheme.jade,
}) {
  final scheme = ColorScheme.fromSeed(
    seedColor: colorTheme.seed,
    brightness: brightness,
    contrastLevel: highContrast ? 1.0 : 0.0,
  );
  final base = ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    visualDensity: VisualDensity.standard,
  );
  return base.copyWith(
    scaffoldBackgroundColor: Colors.transparent,
    textTheme: base.textTheme.copyWith(
      bodyLarge: base.textTheme.bodyLarge?.copyWith(
        fontSize: 16.5,
        height: 1.65,
        letterSpacing: 0.05,
      ),
      bodyMedium: base.textTheme.bodyMedium?.copyWith(
        fontSize: 15,
        height: 1.55,
      ),
      titleLarge: base.textTheme.titleLarge?.copyWith(
        fontWeight: FontWeight.w700,
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: scheme.inverseSurface,
      contentTextStyle: TextStyle(color: scheme.onInverseSurface),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    dividerTheme: DividerThemeData(
      color: scheme.outlineVariant.withValues(alpha: 0.35),
      thickness: 1,
    ),
  );
}

ThemeData buildParchmentTheme(ThemeData base) {
  final scheme =
      ColorScheme.fromSeed(
        seedColor: InkColors.paperLink,
        brightness: Brightness.light,
      ).copyWith(
        surface: InkColors.paper,
        onSurface: InkColors.paperText,
        onSurfaceVariant: InkColors.paperSecondary,
        primary: InkColors.paperLink,
        outlineVariant: InkColors.paperBorder,
        surfaceContainerHighest: const Color(0xFFEDE0C8),
      );
  return base.copyWith(
    brightness: Brightness.light,
    colorScheme: scheme,
    scaffoldBackgroundColor: Colors.transparent,
    textTheme: base.textTheme.apply(
      bodyColor: InkColors.paperText,
      displayColor: InkColors.paperText,
    ),
    dividerTheme: DividerThemeData(
      color: InkColors.paperBorder.withValues(alpha: 0.8),
      thickness: 1,
    ),
  );
}

extension AppColorThemeAppearance on AppColorTheme {
  String get label => switch (this) {
    AppColorTheme.jade => '青玉',
    AppColorTheme.ocean => '海蓝',
    AppColorTheme.forest => '森林',
    AppColorTheme.violet => '紫藤',
    AppColorTheme.rose => '玫瑰',
    AppColorTheme.amber => '琥珀',
  };
  Color get seed => switch (this) {
    AppColorTheme.jade => const Color(0xFF0E5E54),
    AppColorTheme.ocean => const Color(0xFF245FA6),
    AppColorTheme.forest => const Color(0xFF416D36),
    AppColorTheme.violet => const Color(0xFF7954A5),
    AppColorTheme.rose => const Color(0xFFA24365),
    AppColorTheme.amber => const Color(0xFF986019),
  };
}

extension ReaderBackgroundAppearance on ReaderBackground {
  String get label => switch (this) {
    ReaderBackground.paper => '默认',
    ReaderBackground.parchment => '羊皮纸',
    ReaderBackground.mist => '护眼绿',
    ReaderBackground.sand => '暖杏',
    ReaderBackground.midnight => '午夜蓝',
    ReaderBackground.black => '纯黑',
  };
}

ThemeData buildReaderTheme(ThemeData base, ReaderPalette palette) {
  final brightness = palette.gradient.first.computeLuminance() < 0.3
      ? Brightness.dark
      : Brightness.light;
  return base.copyWith(
    brightness: brightness,
    colorScheme:
        ColorScheme.fromSeed(
          seedColor: palette.link,
          brightness: brightness,
        ).copyWith(
          surface: palette.gradient.first,
          onSurface: palette.text,
          onSurfaceVariant: palette.secondary,
          primary: palette.link,
          outlineVariant: palette.border,
          surfaceContainerHighest: palette.codeBackground,
        ),
    textTheme: base.textTheme.apply(
      bodyColor: palette.text,
      displayColor: palette.text,
    ),
  );
}
