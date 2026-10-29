import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'document_assets.dart';

class DocumentMeta {
  const DocumentMeta({
    required this.path,
    required this.name,
    required this.modified,
    required this.size,
  });

  final String path;
  final String name;
  final DateTime modified;
  final int size;
}

class NoteDocument {
  const NoteDocument({
    required this.path,
    required this.name,
    required this.text,
  });

  final String path;
  final String name;
  final String text;

  NoteDocument copyWith({String? path, String? name, String? text}) {
    return NoteDocument(
      path: path ?? this.path,
      name: name ?? this.name,
      text: text ?? this.text,
    );
  }

  DocumentMeta toMeta(DateTime modified, int size) {
    return DocumentMeta(path: path, name: name, modified: modified, size: size);
  }
}

class DocumentStore {
  DocumentStore();

  static const maxBytes = 24 * 1024 * 1024;
  static const _extensions = ['md', 'markdown', 'txt'];

  Directory? _cachedDirectory;

  Future<Directory> _documentsDirectory() async {
    final cached = _cachedDirectory;
    if (cached != null) return cached;
    final base = await getApplicationDocumentsDirectory();
    final directory = Directory(p.join(base.path, 'liquid_ink_documents'));
    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }
    _cachedDirectory = directory;
    return directory;
  }

  Future<List<DocumentMeta>> listDocuments() async {
    final directory = await _documentsDirectory();
    if (!await directory.exists()) return const [];
    final entries = <DocumentMeta>[];
    await for (final entity in directory.list(followLinks: false)) {
      if (entity is! File) continue;
      final extension = p.extension(entity.path).toLowerCase();
      if (!_extensions.contains(extension.replaceFirst('.', ''))) continue;
      final stat = await entity.stat();
      entries.add(
        DocumentMeta(
          path: entity.path,
          name: p.basename(entity.path),
          modified: stat.modified,
          size: stat.size,
        ),
      );
    }
    entries.sort((a, b) => b.modified.compareTo(a.modified));
    return List<DocumentMeta>.unmodifiable(entries);
  }

  Future<NoteDocument?> importFromPicker() async {
    final result = await FilePicker.platform.pickFiles(
      dialogTitle: '打开 Markdown',
      type: FileType.custom,
      allowedExtensions: _extensions,
      // Android provides a cached file path. Check size before allocating the
      // entire file in Dart; withData would allocate even rejected imports.
      withData: false,
    );
    if (result == null || result.files.isEmpty) return null;
    final picked = result.files.single;
    if (picked.size > maxBytes) {
      throw const FormatException('文件超过 24 MB，请先拆分后再打开。');
    }
    var bytes = picked.bytes;
    if (bytes == null && picked.path != null) {
      final file = File(picked.path!);
      if (await file.length() > maxBytes) {
        throw const FormatException('文件超过 24 MB，请先拆分后再打开。');
      }
      bytes = await file.readAsBytes();
    }
    if (bytes == null) {
      throw const FormatException('没有读取到文件内容。');
    }
    if (bytes.length > maxBytes) {
      throw const FormatException('文件超过 24 MB，请先拆分后再打开。');
    }
    final text = decodeText(bytes);
    final directory = await _documentsDirectory();
    final name = await _uniqueName(_safeName(picked.name), directory);
    final file = File(p.join(directory.path, name));
    await file.writeAsString(text, flush: true);
    try {
      await DocumentAssets.record(
        file.path,
        originalUri: picked.identifier,
        originalPath: picked.path,
      );
    } catch (_) {
      // The working copy exists; losing provenance only limits relative-image
      // lookup and must not turn a successful import into an error.
    }
    return NoteDocument(path: file.path, name: name, text: text);
  }

  Future<NoteDocument> createDocument({
    required String name,
    String text = '',
  }) async {
    final directory = await _documentsDirectory();
    final safe = await _uniqueName(_safeName(name), directory);
    final file = File(p.join(directory.path, safe));
    await file.writeAsString(text, flush: true);
    return NoteDocument(path: file.path, name: safe, text: text);
  }

  Future<NoteDocument> readDocument(String path) async {
    final file = File(path);
    final stat = await file.stat();
    if (stat.size > maxBytes) {
      throw const FormatException('文件超过 24 MB，请先拆分后再打开。');
    }
    final bytes = await file.readAsBytes();
    return NoteDocument(
      path: path,
      name: p.basename(path),
      text: decodeText(bytes),
    );
  }

  Future<NoteDocument> ensureWelcomeDocument() async {
    final documents = await listDocuments();
    if (documents.isNotEmpty) {
      return readDocument(documents.first.path);
    }
    final sample = await rootBundle.loadString('assets/welcome.md');
    return createDocument(name: '欢迎使用.md', text: sample);
  }

  Future<void> saveDocument(NoteDocument document) async {
    // Write then rename: a process kill during writeAsString must not leave the
    // only copy of the document truncated. The side file is ignored by
    // listDocuments and replaced on the next save.
    final temporary = File('${document.path}.saving');
    try {
      await temporary.writeAsString(document.text, flush: true);
      await temporary.rename(document.path);
    } catch (_) {
      try {
        if (await temporary.exists()) await temporary.delete();
      } catch (_) {
        // The save error below is the useful one.
      }
      rethrow;
    }
  }

  Future<String> renameDocument(NoteDocument document, String name) async {
    final directory = await _documentsDirectory();
    final safe = _safeName(name);
    final target = File(p.join(directory.path, safe));
    // Confirming the current name must not create a "-2" duplicate (and must
    // not rename the file out from under the open editor).
    if (p.equals(target.path, document.path)) return document.path;
    final unique = await _uniqueName(safe, directory);
    final uniqueTarget = File(p.join(directory.path, unique));
    await File(document.path).rename(uniqueTarget.path);
    // A previous interrupted save left an ignored partial file; it must not
    // linger under the old name after the document moved.
    final saving = File('${document.path}.saving');
    try {
      if (await saving.exists()) await saving.delete();
    } catch (_) {
      // Best effort; the document itself has already moved.
    }
    try {
      await DocumentAssets.move(document.path, uniqueTarget.path);
    } catch (_) {
      // The document itself moved; do not fail the rename (and leave the
      // editor pointing at a path that no longer exists) over sidecar metadata.
    }
    return uniqueTarget.path;
  }

  Future<void> deleteDocument(NoteDocument document) async {
    final file = File(document.path);
    if (await file.exists()) {
      await file.delete();
    }
    final saving = File('${document.path}.saving');
    try {
      if (await saving.exists()) await saving.delete();
    } catch (_) {
      // The document was removed; do not fail on an orphaned partial file.
    }
    try {
      await DocumentAssets.remove(document.path);
    } catch (_) {
      // The file is already gone; a locked sidecar must not report the delete
      // as failed and strand the editor.
    }
  }

  Future<String?> exportDocument(NoteDocument document) async {
    final bytes = Uint8List.fromList(utf8.encode(document.text));
    return FilePicker.platform.saveFile(
      dialogTitle: '导出 Markdown',
      fileName: document.name,
      type: FileType.custom,
      allowedExtensions: _extensions,
      bytes: bytes,
    );
  }

  String decodeText(Uint8List bytes) {
    if (bytes.length >= 3 &&
        bytes[0] == 0xEF &&
        bytes[1] == 0xBB &&
        bytes[2] == 0xBF) {
      return utf8.decode(bytes.sublist(3), allowMalformed: true);
    }
    if (bytes.length >= 2 && bytes[0] == 0xFF && bytes[1] == 0xFE) {
      return _decodeUtf16(bytes.sublist(2), littleEndian: true);
    }
    if (bytes.length >= 2 && bytes[0] == 0xFE && bytes[1] == 0xFF) {
      return _decodeUtf16(bytes.sublist(2), littleEndian: false);
    }
    return utf8.decode(bytes, allowMalformed: true);
  }

  String _decodeUtf16(Uint8List bytes, {required bool littleEndian}) {
    final units = <int>[];
    for (var i = 0; i + 1 < bytes.length; i += 2) {
      units.add(
        littleEndian
            ? bytes[i] | (bytes[i + 1] << 8)
            : (bytes[i] << 8) | bytes[i + 1],
      );
    }
    return String.fromCharCodes(units);
  }

  String _safeName(String input) {
    var name = input.trim();
    if (name.isEmpty) name = '未命名.md';
    name = name.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
    if (!name.toLowerCase().endsWith('.md') &&
        !name.toLowerCase().endsWith('.markdown') &&
        !name.toLowerCase().endsWith('.txt')) {
      name = '$name.md';
    }
    return name;
  }

  Future<String> _uniqueName(String name, Directory directory) async {
    var candidate = name;
    var index = 2;
    final extension = p.extension(name);
    final stem = p.basenameWithoutExtension(name);
    while (await File(p.join(directory.path, candidate)).exists()) {
      candidate = '$stem-$index$extension';
      index++;
    }
    return candidate;
  }
}
