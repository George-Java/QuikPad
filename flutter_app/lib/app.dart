import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/document_store.dart';
import 'core/settings.dart';
import 'ui/home_screen.dart';
import 'ui/theme.dart';
import 'widgets/glass.dart';

class QuikPadApp extends StatelessWidget {
  const QuikPadApp({super.key, required this.settings, required this.store});

  final SettingsController settings;
  final DocumentStore store;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: settings,
      builder: (context, _) {
        final value = settings.value;
        final themeMode = switch (value.themeMode) {
          AppThemeMode.system => ThemeMode.system,
          AppThemeMode.light => ThemeMode.light,
          AppThemeMode.dark => ThemeMode.dark,
        };
        return GlassScope(
          settings: value,
          child: MaterialApp(
            title: 'QuikPad',
            debugShowCheckedModeBanner: false,
            themeMode: themeMode,
            theme: buildInkTheme(
              brightness: Brightness.light,
              highContrast: value.highContrast,
              colorTheme: value.colorTheme,
            ),
            darkTheme: buildInkTheme(
              brightness: Brightness.dark,
              highContrast: value.highContrast,
              colorTheme: value.colorTheme,
            ),
            locale: const Locale('zh', 'CN'),
            supportedLocales: const [Locale('zh', 'CN'), Locale('en', 'US')],
            localizationsDelegates: const [
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            builder: (context, child) {
              final dark = Theme.of(context).brightness == Brightness.dark;
              final overlay =
                  (dark
                          ? SystemUiOverlayStyle.light
                          : SystemUiOverlayStyle.dark)
                      .copyWith(
                        statusBarColor: Colors.transparent,
                        systemNavigationBarColor: Colors.transparent,
                        systemNavigationBarDividerColor: Colors.transparent,
                        systemNavigationBarIconBrightness: dark
                            ? Brightness.light
                            : Brightness.dark,
                      );
              return AnnotatedRegion<SystemUiOverlayStyle>(
                value: overlay,
                child: child ?? const SizedBox.shrink(),
              );
            },
            home: HomeScreen(settings: settings, store: store),
          ),
        );
      },
    );
  }
}
