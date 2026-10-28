import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppThemeMode { system, light, dark }

enum ReaderBackground { paper, parchment, mist, sand, midnight, black }

enum AppColorTheme { jade, ocean, forest, violet, rose, amber }

enum GlassIntensity { low, medium, high }

@immutable
class AppSettings {
  const AppSettings({
    this.themeMode = AppThemeMode.system,
    this.colorTheme = AppColorTheme.jade,
    this.readerBackground = ReaderBackground.paper,
    this.glassEffectsEnabled = true,
    this.glassIntensity = GlassIntensity.medium,
    this.animationsEnabled = true,
    this.autoSaveEnabled = true,
    this.autoSaveSeconds = 15,
    this.highContrast = false,
  });

  final AppThemeMode themeMode;
  final AppColorTheme colorTheme;
  final ReaderBackground readerBackground;
  final bool glassEffectsEnabled;
  final GlassIntensity glassIntensity;
  final bool animationsEnabled;
  final bool autoSaveEnabled;
  final int autoSaveSeconds;
  final bool highContrast;

  AppSettings copyWith({
    AppThemeMode? themeMode,
    AppColorTheme? colorTheme,
    ReaderBackground? readerBackground,
    bool? glassEffectsEnabled,
    GlassIntensity? glassIntensity,
    bool? animationsEnabled,
    bool? autoSaveEnabled,
    int? autoSaveSeconds,
    bool? highContrast,
  }) {
    return AppSettings(
      themeMode: themeMode ?? this.themeMode,
      colorTheme: colorTheme ?? this.colorTheme,
      readerBackground: readerBackground ?? this.readerBackground,
      glassEffectsEnabled: glassEffectsEnabled ?? this.glassEffectsEnabled,
      glassIntensity: glassIntensity ?? this.glassIntensity,
      animationsEnabled: animationsEnabled ?? this.animationsEnabled,
      autoSaveEnabled: autoSaveEnabled ?? this.autoSaveEnabled,
      autoSaveSeconds: autoSaveSeconds ?? this.autoSaveSeconds,
      highContrast: highContrast ?? this.highContrast,
    );
  }
}

class SettingsController extends ChangeNotifier {
  SettingsController(this._prefs) : _value = _read(_prefs);

  static const _themeKey = 'theme_mode';
  static const _colorThemeKey = 'color_theme';
  static const _readerBackgroundKey = 'reader_background';
  static const _glassEnabledKey = 'glass_effects';
  static const _glassIntensityKey = 'glass_intensity';
  static const _animationsKey = 'animations';
  static const _autoSaveKey = 'auto_save';
  static const _autoSaveSecondsKey = 'auto_save_seconds';
  static const _highContrastKey = 'high_contrast';

  final SharedPreferences _prefs;
  AppSettings _value;

  AppSettings get value => _value;

  static AppSettings _read(SharedPreferences prefs) {
    return AppSettings(
      colorTheme: _enumByName(
        AppColorTheme.values,
        prefs.getString(_colorThemeKey),
        AppColorTheme.jade,
      ),
      themeMode: _enumByName(
        AppThemeMode.values,
        prefs.getString(_themeKey),
        AppThemeMode.system,
      ),
      readerBackground: _enumByName(
        ReaderBackground.values,
        prefs.getString(_readerBackgroundKey),
        ReaderBackground.paper,
      ),
      glassEffectsEnabled: prefs.getBool(_glassEnabledKey) ?? true,
      glassIntensity: _enumByName(
        GlassIntensity.values,
        prefs.getString(_glassIntensityKey),
        GlassIntensity.medium,
      ),
      animationsEnabled: prefs.getBool(_animationsKey) ?? true,
      autoSaveEnabled: prefs.getBool(_autoSaveKey) ?? true,
      autoSaveSeconds: (prefs.getInt(_autoSaveSecondsKey) ?? 15)
          .clamp(3, 120)
          .toInt(),
      highContrast: prefs.getBool(_highContrastKey) ?? false,
    );
  }

  static T _enumByName<T extends Enum>(
    List<T> values,
    String? name,
    T fallback,
  ) {
    for (final value in values) {
      if (value.name == name) return value;
    }
    return fallback;
  }

  void _set(AppSettings next, Future<void> Function() write) {
    if (next == _value) return;
    _value = next;
    notifyListeners();
    write();
  }

  void setColorTheme(AppColorTheme theme) {
    _set(
      _value.copyWith(colorTheme: theme),
      () => _prefs.setString(_colorThemeKey, theme.name),
    );
  }

  void setThemeMode(AppThemeMode mode) {
    _set(
      _value.copyWith(themeMode: mode),
      () => _prefs.setString(_themeKey, mode.name),
    );
  }

  void setReaderBackground(ReaderBackground background) {
    _set(
      _value.copyWith(readerBackground: background),
      () => _prefs.setString(_readerBackgroundKey, background.name),
    );
  }

  void setGlassEffectsEnabled(bool enabled) {
    _set(
      _value.copyWith(glassEffectsEnabled: enabled),
      () => _prefs.setBool(_glassEnabledKey, enabled),
    );
  }

  void setGlassIntensity(GlassIntensity intensity) {
    _set(
      _value.copyWith(glassIntensity: intensity),
      () => _prefs.setString(_glassIntensityKey, intensity.name),
    );
  }

  void setAnimationsEnabled(bool enabled) {
    _set(
      _value.copyWith(animationsEnabled: enabled),
      () => _prefs.setBool(_animationsKey, enabled),
    );
  }

  void setAutoSaveEnabled(bool enabled) {
    _set(
      _value.copyWith(autoSaveEnabled: enabled),
      () => _prefs.setBool(_autoSaveKey, enabled),
    );
  }

  void setAutoSaveSeconds(int seconds) {
    final safe = seconds.clamp(3, 120).toInt();
    _set(
      _value.copyWith(autoSaveSeconds: safe),
      () => _prefs.setInt(_autoSaveSecondsKey, safe),
    );
  }

  void setHighContrast(bool enabled) {
    _set(
      _value.copyWith(highContrast: enabled),
      () => _prefs.setBool(_highContrastKey, enabled),
    );
  }
}
