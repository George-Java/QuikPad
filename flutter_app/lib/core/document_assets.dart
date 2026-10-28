import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

/// Source provenance is local metadata beside the private Markdown copy.
/// Existing imports without metadata continue to resolve beside that copy.
class DocumentAssets {
  static const channel = MethodChannel('quikpad/documents');
  static final _metadata = <String, Map<String, dynamic>>{};
  static final _pending = <(String, String), Future<String?>>{};

  static File metadataFile(String document) => File('$document.quikpad.json');

  static Future<Map<String, dynamic>> source(String document) async {
    final cached = _metadata[document];
    if (cached != null) return cached;
    try {
      final data = jsonDecode(await metadataFile(document).readAsString());
      return _metadata[document] = Map<String, dynamic>.from(data as Map);
    } catch (_) { return _metadata[document] = {}; }
  }

  static Future<void> record(String document, {String? originalUri, String? originalPath}) async {
    final data = await source(document);
    if (originalUri != null) data['uri'] = originalUri;
    if (originalPath != null) data['path'] = originalPath;
    await metadataFile(document).writeAsString(jsonEncode(data), flush: true);
    _pending.removeWhere((key, _) => key.$1 == document);
  }

  static Future<bool> associateDirectory(String document) async {
    if (Platform.isAndroid) {
      final tree = await channel.invokeMethod<String>('pickAssetDirectory');
      if (tree == null) return false;
      final data = await source(document);
      data['tree'] = tree;
      await metadataFile(document).writeAsString(jsonEncode(data), flush: true);
      _pending.removeWhere((key, _) => key.$1 == document);
      return true;
    }
    return false;
  }

  static Future<void> move(String previous, String next) async {
    final file = metadataFile(previous);
    if (await file.exists()) await file.rename(metadataFile(next).path);
    _metadata.remove(previous);
    _metadata.remove(next);
    _pending.removeWhere((key, _) => key.$1 == previous || key.$1 == next);
  }

  static Future<void> remove(String document) async {
    final file = metadataFile(document);
    if (await file.exists()) await file.delete();
    _metadata.remove(document);
    _pending.removeWhere((key, _) => key.$1 == document);
  }

  static String? localReference(String url) {
    if (url.startsWith('//')) return null;
    if (p.isAbsolute(url) && !url.startsWith('file:')) return p.normalize(url);
    final uri = Uri.tryParse(url);
    if (uri == null) return null;
    if (uri.scheme == 'file') {
      try { return uri.toFilePath(); } catch (_) { return null; }
    }
    if (uri.hasScheme) return null;
    try { return Uri.decodeComponent(uri.path).replaceAll('\\', '/'); }
    catch (_) { return null; }
  }

  static Future<String?> resolve(String document, String url) =>
      _pending.putIfAbsent((document, url), () => _resolve(document, url));

  static Future<String?> _resolve(String document, String url) async {
    final relative = localReference(url);
    if (relative == null || relative.isEmpty) return null;
    if (p.isAbsolute(relative)) return await File(relative).exists() ? relative : null;
    final data = await source(document);
    // Try the actual source directory first, then the private working copy.
    for (final base in [data['path'] as String?, document]) {
      if (base == null) continue;
      final target = p.normalize(p.join(p.dirname(base), relative));
      if (await File(target).exists()) return target;
    }
    if (!Platform.isAndroid) return null;
    try {
      return await channel.invokeMethod<String>('resolveAsset', {
        'uri': data['uri'], 'tree': data['tree'], 'relative': relative,
        'document': document,
      });
    } on PlatformException { return null; }
  }
}
