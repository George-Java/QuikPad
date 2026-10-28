import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/ui/theme.dart';
import 'package:liquid_ink/core/settings.dart';

void main() {
  testWidgets(
    'every reading palette has readable text in light and dark themes',
    (tester) async {
      for (final brightness in Brightness.values) {
        for (final color in AppColorTheme.values) {
          await tester.pumpWidget(
            MaterialApp(
              theme: buildInkTheme(brightness: brightness, colorTheme: color),
              home: Builder(
                builder: (context) {
                  for (final background in ReaderBackground.values) {
                    final palette = ReaderPalette.from(
                      context,
                      background: background,
                    );
                    for (final surface in palette.gradient) {
                      for (final foreground in [
                        palette.text,
                        palette.secondary,
                        palette.link,
                      ]) {
                        final a = foreground.computeLuminance();
                        final b = surface.computeLuminance();
                        final ratio = a > b
                            ? (a + 0.05) / (b + 0.05)
                            : (b + 0.05) / (a + 0.05);
                        expect(
                          ratio,
                          greaterThanOrEqualTo(4.5),
                          reason:
                              '${color.name}/${background.name}/${brightness.name}',
                        );
                      }
                    }
                  }
                  return const SizedBox();
                },
              ),
            ),
          );
          expect(tester.takeException(), isNull);
        }
      }
    },
  );
  test('parchment body and secondary text meet contrast targets', () {
    final body = _contrast(InkColors.paperText, InkColors.paperTop);
    final secondary = _contrast(InkColors.paperSecondary, InkColors.paperTop);
    final link = _contrast(InkColors.paperLink, InkColors.paperBottom);
    expect(body, greaterThanOrEqualTo(7.0));
    expect(secondary, greaterThanOrEqualTo(4.5));
    expect(link, greaterThanOrEqualTo(4.5));
  });
}

double _contrast(Color a, Color b) {
  final first = _luminance(a);
  final second = _luminance(b);
  final lighter = first > second ? first : second;
  final darker = first > second ? second : first;
  return (lighter + 0.05) / (darker + 0.05);
}

double _luminance(Color color) {
  double channel(double value) {
    return value <= 0.03928
        ? value / 12.92
        : ((value + 0.055) / 1.055) *
              ((value + 0.055) / 1.055) *
              ((value + 0.055) / 1.055);
  }

  // Flutter 3.27+ exposes normalized color channels as doubles.
  return 0.2126 * channel(color.r) +
      0.7152 * channel(color.g) +
      0.0722 * channel(color.b);
}
