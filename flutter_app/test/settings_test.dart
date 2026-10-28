import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/settings.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'reader appearance and motion settings survive a store recreation',
    () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = await SharedPreferences.getInstance();
      final first = SettingsController(preferences);
      first.setReaderBackground(ReaderBackground.parchment);
      first.setGlassEffectsEnabled(false);
      first.setGlassIntensity(GlassIntensity.high);
      first.setAnimationsEnabled(false);
      first.setHighContrast(true);
      first.setAutoSaveSeconds(30);
      await Future<void>.delayed(Duration.zero);

      final second = SettingsController(preferences);
      expect(second.value.readerBackground, ReaderBackground.parchment);
      expect(second.value.glassEffectsEnabled, isFalse);
      expect(second.value.glassIntensity, GlassIntensity.high);
      expect(second.value.animationsEnabled, isFalse);
      expect(second.value.highContrast, isTrue);
      expect(second.value.autoSaveSeconds, 30);
    },
  );

  test('theme mode is persisted by enum name', () async {
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final first = SettingsController(preferences);
    first.setThemeMode(AppThemeMode.dark);
    await Future<void>.delayed(Duration.zero);
    final second = SettingsController(preferences);
    expect(second.value.themeMode, AppThemeMode.dark);
  });

  test('all new colors and reader backgrounds persist independently', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = SettingsController(prefs);
    for (final color in AppColorTheme.values) {
      for (final background in ReaderBackground.values) {
        controller.setColorTheme(color);
        controller.setReaderBackground(background);
        await Future<void>.delayed(Duration.zero);
        final restored = SettingsController(prefs);
        expect(restored.value.colorTheme, color);
        expect(restored.value.readerBackground, background);
        restored.dispose();
      }
    }
    controller.dispose();
  });

  test(
    'old preferences and unknown future colors have safe defaults',
    () async {
      SharedPreferences.setMockInitialValues({
        'reader_background': 'parchment',
        'color_theme': 'unknown',
      });
      final controller = SettingsController(
        await SharedPreferences.getInstance(),
      );
      expect(controller.value.colorTheme, AppColorTheme.jade);
      expect(controller.value.readerBackground, ReaderBackground.parchment);
      controller.dispose();
    },
  );
}
