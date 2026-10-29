import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/core/document_assets.dart';
import 'package:liquid_ink/core/document_store.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';

class _FakePathProvider extends PathProviderPlatform {
  _FakePathProvider(this.root);
  final String root;

  @override
  Future<String?> getApplicationDocumentsPath() async => root;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory root;
  late Directory documents;
  late DocumentStore store;

  setUp(() {
    root = Directory.systemTemp.createTempSync('quikpad-store-');
    PathProviderPlatform.instance = _FakePathProvider(root.path);
    documents = Directory(p.join(root.path, 'liquid_ink_documents'));
    store = DocumentStore();
  });

  tearDown(() {
    if (root.existsSync()) root.deleteSync(recursive: true);
  });

  test(
    'creates sanitized, unique documents inside the private directory',
    () async {
      final first = await store.createDocument(
        name: '../a/b:c*?.md',
        text: 'one',
      );
      expect(p.dirname(first.path), documents.path);
      expect(first.name, isNot(contains('/')));
      expect(first.name, isNot(contains(':')));
      expect(await File(first.path).readAsString(), 'one');

      final second = await store.createDocument(name: first.name, text: 'two');
      expect(second.name, isNot(first.name));
      expect(second.name, contains('-2'));

      final noExtension = await store.createDocument(name: 'notes', text: '');
      expect(noExtension.name, 'notes.md');

      final empty = await store.createDocument(name: '   ', text: '');
      expect(empty.name, '未命名.md');
    },
  );

  test(
    'lists only Markdown/text files, newest first, and skips sidecars',
    () async {
      final older = await store.createDocument(name: 'older.md', text: 'a');
      final newer = await store.createDocument(name: 'newer.txt', text: 'b');
      await File(p.join(documents.path, 'ignored.json')).writeAsString('{}');
      await Directory(p.join(documents.path, 'folder.md')).create();
      File(older.path).setLastModifiedSync(DateTime(2026, 1, 1));
      File(newer.path).setLastModifiedSync(DateTime(2026, 2, 1));

      final listed = await store.listDocuments();
      expect(listed.map((m) => m.name), ['newer.txt', 'older.md']);
      expect(listed.every((m) => m.size >= 0), isTrue);
    },
  );

  test('decodes UTF-8 BOM, UTF-16 LE/BE and malformed UTF-8', () {
    final utf8Bom = Uint8List.fromList([
      0xEF,
      0xBB,
      0xBF,
      ...utf8.encode('标题'),
    ]);
    expect(store.decodeText(utf8Bom), '标题');

    final utf16le = Uint8List.fromList([
      0xFF,
      0xFE,
      0x41,
      0x00,
      0x3D,
      0xD8,
      0x00,
      0xDE,
    ]);
    expect(store.decodeText(utf16le), 'A😀');

    final utf16be = Uint8List.fromList([
      0xFE,
      0xFF,
      0x00,
      0x41,
      0xD8,
      0x3D,
      0xDE,
      0x00,
    ]);
    expect(store.decodeText(utf16be), 'A😀');

    final malformed = Uint8List.fromList([0x41, 0xFF, 0x42]);
    expect(store.decodeText(malformed), contains('A'));
  });

  test('same-name rename is a no-op and never creates a duplicate', () async {
    final document = await store.createDocument(name: 'notes.md', text: 'body');
    final unchanged = await store.renameDocument(document, 'notes');
    expect(unchanged, document.path);
    expect(File(document.path).existsSync(), isTrue);

    final listed = await store.listDocuments();
    expect(listed.where((m) => m.name.startsWith('notes')).length, 1);
  });

  test('rename moves the working file and its metadata sidecar', () async {
    final document = await store.createDocument(name: 'notes.md', text: 'body');
    await DocumentAssets.metadataFile(document.path).writeAsString(
      jsonEncode({'uri': 'content://doc/1', 'path': '/sdcard/notes.md'}),
    );
    final renamed = await store.renameDocument(document, 'journal.md');
    expect(renamed, endsWith('journal.md'));
    expect(File(document.path).existsSync(), isFalse);
    expect(File(renamed).existsSync(), isTrue);
    expect(
      await DocumentAssets.source(renamed),
      containsPair('uri', 'content://doc/1'),
    );
    expect(DocumentAssets.metadataFile(document.path).existsSync(), isFalse);
    expect(DocumentAssets.metadataFile(renamed).existsSync(), isTrue);
  });

  test(
    'saving replaces the document without leaving a temporary file',
    () async {
      final document = await store.createDocument(
        name: 'notes.md',
        text: 'old',
      );
      await File(
        '${document.path}.saving',
      ).writeAsString('stale partial write');

      await store.saveDocument(document.copyWith(text: 'new body'));

      expect(await File(document.path).readAsString(), 'new body');
      expect(File('${document.path}.saving').existsSync(), isFalse);
      final listed = await store.listDocuments();
      expect(listed.single.name, 'notes.md');
    },
  );

  test('delete removes the document and its metadata', () async {
    final document = await store.createDocument(name: 'notes.md', text: 'body');
    await DocumentAssets.metadataFile(document.path).writeAsString('{}');
    await store.deleteDocument(document);
    expect(File(document.path).existsSync(), isFalse);
    expect(DocumentAssets.metadataFile(document.path).existsSync(), isFalse);
  });
}
