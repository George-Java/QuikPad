import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';
import 'core/document_store.dart';
import 'core/diagnostics.dart';
import 'core/settings.dart';
import 'widgets/glass_effect.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  PaintingBinding.instance.imageCache
    ..maximumSize = 64
    ..maximumSizeBytes = 32 * 1024 * 1024;
  await AppDiagnostics.initialize();
  if (ui.ImageFilter.isShaderFilterSupported) await GlassResources.load();
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  final preferences = await SharedPreferences.getInstance();
  runApp(
    QuikPadApp(
      settings: SettingsController(preferences),
      store: DocumentStore(),
    ),
  );
}
