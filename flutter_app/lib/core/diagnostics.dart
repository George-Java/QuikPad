import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

/// Local only. Stores error types and stacks, never document text or paths.
class AppDiagnostics {
  static File? _log;
  static const _channel = MethodChannel('liquid_ink/diagnostics');

  static Future<void> initialize() async {
    try {
      final directory = await getApplicationSupportDirectory();
      _log = File('${directory.path}/diagnostics.txt');
    } catch (_) {
      // Diagnostics must never prevent startup.
    }
    final previous = FlutterError.onError;
    FlutterError.onError = (details) {
      _record(details.exception, details.stack);
      previous?.call(details);
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      _record(error, stack);
      return false;
    };
  }

  static void _record(Object error, StackTrace? stack) {
    try {
      final file = _log;
      if (file == null) return;
      final lines = stack?.toString().split('\n').take(18).join('\n') ?? '';
      final entry =
          '${DateTime.now().toIso8601String()} ${error.runtimeType}\n$lines\n';
      final append = file.existsSync() && file.lengthSync() < 64 * 1024;
      file.writeAsStringSync(
        entry,
        mode: append ? FileMode.append : FileMode.write,
      );
    } catch (_) {
      // A disk failure must not recurse through error reporting.
    }
  }

  static Future<bool> exportReport() async {
    final report = StringBuffer(
      'QuikPad 2.3.0 (5)\n${DateTime.now().toIso8601String()}\n',
    );
    report.writeln(
      'Android exit reason: 3=LOW_MEMORY, 4=CRASH, 5=CRASH_NATIVE, 6=ANR, 9=EXCESSIVE_RESOURCE_USAGE, 10=USER_REQUESTED',
    );
    try {
      final history = await _channel.invokeMethod<Object>('exitHistory');
      report.writeln(const JsonEncoder.withIndent('  ').convert(history));
    } catch (_) {
      report.writeln('Android exit history unavailable.');
    }
    final file = _log;
    if (file != null && await file.exists()) {
      report.writeln(await file.readAsString());
    }
    return await FilePicker.platform.saveFile(
          dialogTitle: '导出诊断报告',
          fileName: 'QuikPad-diagnostics.txt',
          type: FileType.custom,
          allowedExtensions: ['txt'],
          bytes: Uint8List.fromList(utf8.encode(report.toString())),
        ) !=
        null;
  }
}
