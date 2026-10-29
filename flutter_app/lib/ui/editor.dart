import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/document_store.dart';
import '../core/document_assets.dart';
import '../core/settings.dart';
import '../markdown/model.dart';
import '../markdown/parser.dart';
import '../widgets/glass.dart';
import '../widgets/source_editor.dart';
import 'outline_sheet.dart';
import 'preview.dart';
import 'sheets.dart';

enum EditorMode { source, live, preview }

class EditorScreen extends StatefulWidget {
  const EditorScreen({
    super.key,
    required this.document,
    required this.store,
    required this.settings,
  });

  final NoteDocument document;
  final DocumentStore store;
  final SettingsController settings;

  @override
  State<EditorScreen> createState() => _EditorScreenState();
}

class _EditorScreenState extends State<EditorScreen>
    with WidgetsBindingObserver {
  late NoteDocument _doc;
  late TextEditingController _controller;
  late FocusNode _editorFocus;
  late MarkdownDocument _parsed;
  late String _lastSavedText;
  late String _lastText;

  final GlobalKey<MarkdownPreviewPaneState> _previewKey =
      GlobalKey<MarkdownPreviewPaneState>();

  Timer? _parseTimer;
  Timer? _autoSaveTimer;
  EditorMode _mode = EditorMode.preview;
  bool _focusMode = false;
  Timer? _focusExitTimer;
  bool _focusExitArmed = false;
  static const _focusExitWindow = Duration(seconds: 2);
  bool _parsing = false;
  bool _parseRunning = false;
  bool _saving = false;
  int _saveDepth = 0;
  Future<void> _saveLock = Future<void>.value();
  bool _leaving = false;
  bool _deleting = false;
  String? _parseError;
  int _parseEpoch = 0;
  Timer? _statsTimer;
  final ValueNotifier<String> _stats = ValueNotifier<String>('0 字 · 1 行');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _doc = widget.document;
    _controller = TextEditingController(text: _doc.text);
    _lastSavedText = _doc.text;
    _lastText = _doc.text;
    _editorFocus = FocusNode();
    _controller.addListener(_onTextChanged);
    _parsed = _doc.text.length > 160000
        ? const MarkdownDocument(blocks: [], outline: [], lineCount: 1)
        : _initialParse(_doc.text);
    if (_doc.text.length > 160000) {
      _scheduleParse(immediate: true);
    }
    // Counting CJK characters and words is three whole-document regex scans.
    // A megabyte-sized note takes long enough that doing it inside every
    // keystroke's build would visibly stall typing, so large documents show a
    // placeholder and are counted after the user pauses.
    if (_doc.text.length <= 100000) {
      _stats.value = _statsForText(_doc.text);
    } else {
      _stats.value = '统计中…';
      _statsTimer = Timer(_statsDelay, () => unawaited(_refreshStats()));
    }
  }

  static const _statsDelay = Duration(milliseconds: 180);

  void _scheduleStats() {
    _statsTimer?.cancel();
    if (_controller.text.trim().isEmpty) {
      _stats.value = '0 字 · 1 行';
      return;
    }
    _statsTimer = Timer(_statsDelay, () => unawaited(_refreshStats()));
  }

  Future<void> _refreshStats() async {
    final text = _controller.text;
    String stats;
    try {
      stats = text.length > 200000
          ? await compute(_statsForText, text)
          : _statsForText(text);
    } catch (_) {
      // Word counting is cosmetic; never let it surface as an unhandled error.
      return;
    }
    if (!mounted || _controller.text != text) return;
    _stats.value = stats;
  }

  MarkdownDocument _initialParse(String text) {
    try {
      return parseMarkdown(text);
    } catch (error) {
      _parseError = '文档排版失败，已显示可编辑源码。';
      return const MarkdownDocument(blocks: [], outline: [], lineCount: 1);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused) {
      unawaited(_save(showMessage: false));
    }
  }

  void _onTextChanged() {
    final text = _controller.text;
    if (text == _lastText) return;
    _lastText = text;
    if (text.trim().isEmpty) {
      _parseEpoch++;
      _parseTimer?.cancel();
      setState(() {
        _parsed = const MarkdownDocument(blocks: [], outline: [], lineCount: 1);
        _parsing = false;
        _parseError = null;
      });
    } else {
      _scheduleParse();
    }
    _scheduleStats();
    _scheduleAutoSave();
  }

  void _scheduleParse({bool immediate = false}) {
    _parseEpoch++;
    _parseTimer?.cancel();
    _parseTimer = Timer(
      immediate ? Duration.zero : const Duration(milliseconds: 130),
      _parseNow,
    );
  }

  Future<void> _parseNow() async {
    // A single worker at a time; newer edits supersede its result.
    if (_parseRunning) return;
    final text = _controller.text;
    final epoch = _parseEpoch;
    if (text.trim().isEmpty) {
      if (mounted) {
        setState(() {
          _parsed = const MarkdownDocument(
            blocks: [],
            outline: [],
            lineCount: 1,
          );
          _parsing = false;
          _parseError = null;
        });
      }
      return;
    }
    _parseRunning = true;
    if (mounted) setState(() => _parsing = true);
    MarkdownDocument? parsed;
    String? error;
    try {
      if (text.length > 160000) {
        parsed = await compute(parseMarkdown, text);
      } else {
        parsed = parseMarkdown(text);
      }
    } catch (parseError) {
      error = '文档排版失败，可继续编辑源码。';
      debugPrint('Markdown parse failed: $parseError');
    } finally {
      _parseRunning = false;
    }
    if (!mounted) return;
    if (epoch != _parseEpoch) {
      setState(() => _parsing = false);
      _scheduleParse(immediate: true);
      return;
    }
    setState(() {
      if (parsed != null) {
        _parsed = parsed;
        _parseError = null;
      } else {
        _parseError = error;
      }
      _parsing = false;
    });
  }

  void _scheduleAutoSave() {
    _autoSaveTimer?.cancel();
    if (!widget.settings.value.autoSaveEnabled) return;
    _autoSaveTimer = Timer(
      Duration(seconds: widget.settings.value.autoSaveSeconds),
      () => unawaited(_save(showMessage: false)),
    );
  }

  Future<void> _save({bool showMessage = false}) async {
    if (_deleting) return;
    if (_controller.text == _lastSavedText) {
      // Even a "nothing new" save must let an in-flight write settle: it can
      // be writing an older revision, and claiming saved before it completes
      // would let a revert be overwritten on disk.
      await _saveLock;
      if (!mounted) return;
      if (_controller.text == _lastSavedText) {
        if (showMessage) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('文档已保存')));
        }
        return;
      }
    }
    // Serialize writes. Two callers (lifecycle, auto-save, manual save) waking
    // together used to write the same atomic-save temporary file concurrently.
    final previous = _saveLock;
    final gate = Completer<void>();
    _saveLock = gate.future;
    _saveDepth++;
    _saving = true;
    if (mounted) setState(() {});
    try {
      await previous;
      if (_deleting) return;
      // Coalesce queued saves onto the text that is current now. Writing the
      // snapshot captured when this save was queued could resurrect an older
      // revision after the user reverted to the already-saved text.
      final latest = _controller.text;
      if (latest == _lastSavedText) return;
      await widget.store.saveDocument(_doc.copyWith(text: latest));
      _lastSavedText = latest;
      if (mounted && _controller.text != latest) {
        // The document changed while the write was in flight (a revert or more
        // typing). The disk now holds `latest`; queue a follow-up so the newer
        // text is not silently lost.
        unawaited(_save());
      }
      if (showMessage && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('文档已保存')));
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('保存失败，请重试')));
      }
    } finally {
      gate.complete();
      _saveDepth--;
      if (_saveDepth == 0) _saving = false;
      if (mounted) setState(() {});
    }
  }

  Future<void> _handleBack() async {
    if (_leaving || _deleting) return;
    if (_focusMode) {
      // A single back gesture is easy to trigger by accident. Arm the exit and
      // only leave focus mode when the user confirms with a second back inside
      // the short window.
      if (_focusExitArmed) {
        _exitFocusMode();
      } else {
        _armFocusExit();
      }
      return;
    }
    _leaving = true;
    _editorFocus.unfocus();
    await _save();
    if (!mounted) return;
    if (_controller.text != _lastSavedText) {
      _leaving = false;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('保存未完成，已留在编辑器')));
      return;
    }
    Navigator.of(context).pop(true);
  }

  void _armFocusExit() {
    _focusExitTimer?.cancel();
    _focusExitArmed = true;
    _focusExitTimer = Timer(_focusExitWindow, () {
      _focusExitArmed = false;
    });
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('再按一次返回退出专注模式'),
          duration: _focusExitWindow,
        ),
      );
  }

  void _exitFocusMode() {
    _focusExitTimer?.cancel();
    _focusExitTimer = null;
    _focusExitArmed = false;
    if (!mounted) return;
    ScaffoldMessenger.of(context).removeCurrentSnackBar();
    setState(() => _focusMode = false);
  }

  void _enterFocusMode() {
    _focusExitTimer?.cancel();
    _focusExitTimer = null;
    _focusExitArmed = false;
    setState(() => _focusMode = true);
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('专注模式：连续按两次返回退出'),
          duration: _focusExitWindow,
        ),
      );
  }

  Future<void> _openOutline() async {
    _editorFocus.unfocus();
    final animate = motionOf(context);
    final entry = await showGlassSheet<OutlineEntry>(
      context,
      animate: animate,
      builder: (context) =>
          OutlineSheet(entries: _parsed.outline, parsing: _parsing),
    );
    if (entry == null || !mounted) return;
    setState(() => _mode = EditorMode.preview);
    await WidgetsBinding.instance.endOfFrame;
    _previewKey.currentState?.scrollToBlock(entry.blockIndex, animate: animate);
  }

  Future<void> _showDocumentMenu() async {
    _editorFocus.unfocus();
    final scheme = Theme.of(context).colorScheme;
    final action = await showGlassSheet<String>(
      context,
      animate: motionOf(context),
      builder: (context) => SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: GlassPanel(
            borderRadius: 26,
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: SingleChildScrollView(
              child: Material(
                // Material must sit below the glass surface's DecoratedBox;
                // otherwise ListTile ink/background paints behind it and Flutter
                // reports that splash feedback is invisible.
                type: MaterialType.transparency,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ListTile(
                      leading: Icon(Icons.search, color: scheme.primary),
                      title: const Text('查找文本'),
                      onTap: () => Navigator.of(context).pop('search'),
                    ),
                    ListTile(
                      leading: Icon(Icons.fullscreen, color: scheme.primary),
                      title: const Text('专注模式'),
                      onTap: () => Navigator.of(context).pop('focus'),
                    ),
                    ListTile(
                      leading: Icon(Icons.ios_share, color: scheme.primary),
                      title: const Text('导出 / 另存为'),
                      onTap: () => Navigator.of(context).pop('export'),
                    ),
                    if (defaultTargetPlatform == TargetPlatform.android)
                      ListTile(
                        leading: Icon(Icons.folder_open, color: scheme.primary),
                        title: const Text('关联图片目录'),
                        subtitle: const Text('选择 Markdown 文件所在文件夹'),
                        onTap: () => Navigator.of(context).pop('assets'),
                      ),
                    ListTile(
                      leading: Icon(
                        Icons.drive_file_rename_outline,
                        color: scheme.primary,
                      ),
                      title: const Text('重命名'),
                      onTap: () => Navigator.of(context).pop('rename'),
                    ),
                    ListTile(
                      leading: Icon(Icons.delete_outline, color: scheme.error),
                      title: Text(
                        '删除本地副本',
                        style: TextStyle(color: scheme.error),
                      ),
                      onTap: () => Navigator.of(context).pop('delete'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
    if (!mounted || action == null) return;
    if (action == 'search') {
      await _openSearch();
    } else if (action == 'focus') {
      _enterFocusMode();
    } else if (action == 'export') {
      await _export();
    } else if (action == 'rename') {
      await _rename();
    } else if (action == 'delete') {
      await _delete();
    } else if (action == 'assets') {
      if (await DocumentAssets.associateDirectory(_doc.path) && mounted) {
        _previewKey.currentState?.reloadImages();
      }
    }
  }

  Future<void> _export() async {
    await _save(showMessage: false);
    if (!mounted) return;
    try {
      final path = await widget.store.exportDocument(
        _doc.copyWith(text: _controller.text),
      );
      if (path != null && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('已导出 Markdown 文件')));
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('导出失败，请重试')));
      }
    }
  }

  Future<void> _rename() async {
    _autoSaveTimer?.cancel();
    await _save(showMessage: false);
    if (!mounted) return;
    final newName = await showTextPromptDialog(
      context,
      title: '重命名文档',
      labelText: '文件名',
      hintText: '例如：读书笔记.md',
      initialText: _doc.name.replaceFirst(
        RegExp(r'\.(md|markdown|txt)$', caseSensitive: false),
        '',
      ),
      confirmLabel: '重命名',
    );
    if (newName == null || newName.trim().isEmpty || !mounted) return;
    try {
      final path = await widget.store.renameDocument(_doc, newName);
      if (!mounted) return;
      setState(() {
        _doc = _doc.copyWith(
          path: path,
          name: path.split(RegExp(r'[/\\]')).last,
        );
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('重命名失败')));
      }
    }
  }

  Future<void> _delete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除本地副本？'),
        content: const Text('删除后无法从 App 内恢复，已导出的文件不受影响。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    _deleting = true;
    _autoSaveTimer?.cancel();
    // Wait for every queued write. Saves that were already queued re-check
    // _deleting after the previous gate and skip, so the file cannot be
    // recreated after this point.
    await _saveLock;
    await widget.store.deleteDocument(_doc);
    if (mounted) Navigator.of(context).pop(true);
  }

  Future<void> _openLink(String url) async {
    if (url.startsWith('#')) {
      var anchor = url.substring(1);
      try {
        anchor = Uri.decodeComponent(anchor);
      } catch (_) {
        // Keep the literal anchor for providers that write a raw '%' path.
      }
      final target =
          _parsed.anchors[anchor] ??
          _parsed.anchors[anchor.toLowerCase()] ??
          _parsed.anchors[anchor.toLowerCase().replaceAll(' ', '-')];
      if (target == null) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('没有找到锚点：$anchor')));
        }
        return;
      }
      if (_mode != EditorMode.preview) {
        setState(() => _mode = EditorMode.preview);
        await WidgetsBinding.instance.endOfFrame;
      }
      if (!mounted) return;
      _previewKey.currentState?.scrollToBlock(
        target,
        animate: motionOf(context),
      );
      return;
    }
    final uri = Uri.tryParse(url);
    if (uri == null) return;
    if (!const {'http', 'https', 'mailto', 'tel'}.contains(uri.scheme)) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('当前仅支持网页、邮件和电话链接')));
      }
      return;
    }
    var opened = false;
    try {
      opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      opened = false;
    }
    if (!opened && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('链接没有可用的打开方式')));
    }
  }

  Future<void> _openFootnote(String label) async {
    DefinitionBlock? definition;
    for (final block in _parsed.blocks) {
      if (block is DefinitionBlock && block.label == label) {
        definition = block;
        break;
      }
    }
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('脚注 · $label'),
        content: definition == null
            ? const Text('没有找到对应的脚注定义。')
            : Text(
                definition.text,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('关闭'),
          ),
        ],
      ),
    );
  }

  void _toggleTask(ListItemBlock block) {
    final text = _controller.text;
    final lines = text.split('\n');
    if (!block.hasSourceLine || block.line >= lines.length) return;
    final original = lines[block.line];
    final mark = block.checked == true ? '[ ]' : '[x]';
    final updated = original.replaceFirst(RegExp(r'\[[ xX]\]'), mark);
    if (updated == original) return;
    lines[block.line] = updated;
    final newText = lines.join('\n');
    final previousSelection = _controller.selection;
    final offset = previousSelection.baseOffset
        .clamp(0, newText.length)
        .toInt();
    _controller.value = TextEditingValue(
      text: newText,
      // A preview-only edit has no valid source cursor; preserve the invalid
      // selection instead of yanking the eventual source caret to offset 0.
      selection: previousSelection.isValid
          ? TextSelection.collapsed(offset: offset)
          : previousSelection,
    );
  }

  void _replaceSelection(
    String before,
    String after, {
    String placeholder = '文本',
  }) {
    final value = _controller.value;
    final text = value.text;
    final selection = value.selection;
    final hasSelection = selection.isValid && !selection.isCollapsed;
    final selected = hasSelection
        ? text.substring(selection.start, selection.end)
        : placeholder;
    final replacement = '$before$selected$after';
    final start = selection.isValid ? selection.start : text.length;
    final end = selection.isValid ? selection.end : text.length;
    final newText = text.replaceRange(start, end, replacement);
    final selectionStart = start + before.length;
    _controller.value = TextEditingValue(
      text: newText,
      selection: TextSelection(
        baseOffset: selectionStart,
        extentOffset: selectionStart + selected.length,
      ),
    );
    _editorFocus.requestFocus();
    HapticFeedback.selectionClick();
  }

  void _replaceHeading(int level) {
    final value = _controller.value;
    final text = value.text;
    final selection = value.selection;
    final start = _lineStart(text, selection.start);
    final end = _lineEnd(text, selection.end);
    final line = text.substring(start, end);
    final stripped = line.replaceFirst(RegExp(r'^#{1,6}[ \t]+'), '');
    final prefix = '${'#' * level} ';
    final newText = text.replaceRange(start, end, '$prefix$stripped');
    _controller.value = TextEditingValue(
      text: newText,
      selection: TextSelection.collapsed(offset: start + prefix.length),
    );
    _editorFocus.requestFocus();
    HapticFeedback.selectionClick();
  }

  void _prefixSelectedLines(String prefix) {
    final value = _controller.value;
    final text = value.text;
    final selection = value.selection;
    final start = _lineStart(text, selection.start);
    final end = _lineEnd(text, selection.end);
    final lines = text.substring(start, end).split('\n');
    final updated = lines.map((line) => '$prefix$line').join('\n');
    final newText = text.replaceRange(start, end, updated);
    _controller.value = TextEditingValue(
      text: newText,
      selection: TextSelection(
        baseOffset: start,
        extentOffset: start + updated.length,
      ),
    );
    _editorFocus.requestFocus();
    HapticFeedback.selectionClick();
  }

  int _lineStart(String text, int offset) {
    final safe = offset.clamp(0, text.length).toInt();
    final index = text.lastIndexOf('\n', math.max(0, safe - 1));
    return index < 0 ? 0 : index + 1;
  }

  int _lineEnd(String text, int offset) {
    final safe = offset.clamp(0, text.length).toInt();
    final index = text.indexOf('\n', safe);
    return index < 0 ? text.length : index;
  }

  Future<void> _openSearch() async {
    final query = await showTextPromptDialog(
      context,
      title: '查找文本',
      hintText: '输入要查找的内容',
      confirmLabel: '查找',
      textInputAction: TextInputAction.search,
    );
    if (query == null || query.isEmpty || !mounted) return;
    final text = _controller.text;
    final from = _controller.selection.end.clamp(0, text.length).toInt();
    var index = text.toLowerCase().indexOf(query.toLowerCase(), from);
    if (index < 0) {
      index = text.toLowerCase().indexOf(query.toLowerCase());
    }
    if (index < 0) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('没有找到“$query”')));
      return;
    }
    setState(() => _mode = EditorMode.source);
    await WidgetsBinding.instance.endOfFrame;
    if (!mounted) return;
    _controller.selection = TextSelection(
      baseOffset: index,
      extentOffset: index + query.length,
    );
    _editorFocus.requestFocus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _parseTimer?.cancel();
    _autoSaveTimer?.cancel();
    _statsTimer?.cancel();
    _focusExitTimer?.cancel();
    _stats.dispose();
    _controller.removeListener(_onTextChanged);
    _controller.dispose();
    _editorFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settings = GlassScope.of(context);
    final motion = motionOf(context);
    final scheme = Theme.of(context).colorScheme;
    return PopScope<Object?>(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) unawaited(_handleBack());
      },
      child: LiquidBackground(
        animate: motion && settings.glassEffectsEnabled,
        child: Scaffold(
          backgroundColor: Colors.transparent,
          body: SafeArea(
            child: Column(
              children: [
                if (!_focusMode) _buildTopBar(context),
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(
                      _focusMode ? 8 : 12,
                      4,
                      _focusMode ? 8 : 12,
                      4,
                    ),
                    child: _mode == EditorMode.preview
                        ? _buildPreview()
                        : _buildEditingArea(),
                  ),
                ),
                if (!_focusMode) _buildBottom(scheme),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 2),
      child: GlassPanel(
        borderRadius: 24,
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 5),
        child: Row(
          children: [
            GlassIconButton(
              icon: Icons.arrow_back,
              tooltip: '返回',
              onPressed: () => unawaited(_handleBack()),
            ),
            const SizedBox(width: 6),
            Expanded(
              child: ValueListenableBuilder<TextEditingValue>(
                valueListenable: _controller,
                builder: (context, value, child) {
                  final dirty = value.text != _lastSavedText;
                  final status = _parsing
                      ? '正在排版…'
                      : _parseError != null
                      ? _parseError!
                      : dirty
                      ? '有未保存更改'
                      : '已保存到本地';
                  final color = _parsing || _saving
                      ? scheme.secondary
                      : _parseError != null
                      ? scheme.error
                      : dirty
                      ? scheme.tertiary
                      : scheme.primary;
                  return Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _doc.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(fontWeight: FontWeight.w700),
                      ),
                      Row(
                        children: [
                          Container(
                            width: 6,
                            height: 6,
                            decoration: BoxDecoration(
                              color: color,
                              shape: BoxShape.circle,
                            ),
                          ),
                          const SizedBox(width: 5),
                          Flexible(
                            child: Text(
                              status,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: scheme.onSurfaceVariant,
                                fontSize: 11,
                              ),
                            ),
                          ),
                          if (_parsing || _saving) ...[
                            const SizedBox(width: 7),
                            SizedBox(
                              width: 10,
                              height: 10,
                              child: CircularProgressIndicator(
                                strokeWidth: 1.4,
                                color: color,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  );
                },
              ),
            ),
            GlassIconButton(
              icon: Icons.toc,
              tooltip: '目录大纲',
              onPressed: _openOutline,
            ),
            GlassIconButton(
              icon: Icons.save_outlined,
              tooltip: '保存',
              onPressed: _saving ? null : () => _save(showMessage: true),
            ),
            GlassIconButton(
              icon: Icons.more_horiz,
              tooltip: '更多文档操作',
              onPressed: _showDocumentMenu,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEditingArea() {
    final source = _buildSourceEditor();
    final preview = _buildPreview();
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide =
            constraints.maxWidth > 720 &&
            constraints.maxWidth > constraints.maxHeight;
        if (_mode == EditorMode.source) return source;
        if (wide) {
          return Row(
            children: [
              Expanded(flex: 44, child: source),
              const SizedBox(width: 8),
              Expanded(flex: 56, child: preview),
            ],
          );
        }
        return Column(
          children: [
            Expanded(flex: 38, child: source),
            const SizedBox(height: 8),
            Expanded(flex: 62, child: preview),
          ],
        );
      },
    );
  }

  Widget _buildSourceEditor() {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: scheme.surface.withValues(alpha: 0.96),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.55),
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          Container(
            height: 34,
            padding: const EdgeInsets.symmetric(horizontal: 13),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerHighest.withValues(alpha: 0.34),
            ),
            child: Row(
              children: [
                Icon(Icons.code, size: 14, color: scheme.onSurfaceVariant),
                const SizedBox(width: 6),
                Text(
                  '源码',
                  style: TextStyle(
                    color: scheme.onSurfaceVariant,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 0.5,
                  ),
                ),
                const Spacer(),
                Flexible(
                  child: ValueListenableBuilder<String>(
                    valueListenable: _stats,
                    builder: (context, stats, child) => Text(
                      stats,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: scheme.onSurfaceVariant,
                        fontSize: 10.5,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: SourceEditor(
              controller: _controller,
              focusNode: _editorFocus,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPreview() {
    return MarkdownPreviewPane(
      key: _previewKey,
      document: _parsed,
      basePath: _doc.path,
      onLinkTap: (url) => unawaited(_openLink(url)),
      onFootnoteTap: (label) => unawaited(_openFootnote(label)),
      onToggleTask: _toggleTask,
    );
  }

  Widget _buildBottom(ColorScheme scheme) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 2, 12, 8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_mode != EditorMode.preview) _buildFormatBar(),
          GlassPanel(
            borderRadius: 22,
            padding: const EdgeInsets.fromLTRB(6, 6, 6, 6),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: GlassSegmentedControl<EditorMode>(
                        values: const [
                          EditorMode.source,
                          EditorMode.live,
                          EditorMode.preview,
                        ],
                        selected: _mode,
                        labelOf: (mode) => switch (mode) {
                          EditorMode.source => '源码',
                          EditorMode.live => '实时',
                          EditorMode.preview => '预览',
                        },
                        onSelected: (mode) => setState(() => _mode = mode),
                      ),
                    ),
                  ],
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(10, 2, 10, 0),
                  child: ValueListenableBuilder<String>(
                    valueListenable: _stats,
                    builder: (context, stats, child) => Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        stats,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: scheme.onSurfaceVariant,
                          fontSize: 11.5,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFormatBar() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 5),
      child: GlassPanel(
        borderRadius: 18,
        padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 2),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              GlassIconButton(
                icon: Icons.title,
                tooltip: '标题',
                onPressed: () => _replaceHeading(1),
              ),
              GlassIconButton(
                icon: Icons.format_bold,
                tooltip: '粗体',
                onPressed: () =>
                    _replaceSelection('**', '**', placeholder: '粗体'),
              ),
              GlassIconButton(
                icon: Icons.format_italic,
                tooltip: '斜体',
                onPressed: () => _replaceSelection('*', '*', placeholder: '斜体'),
              ),
              GlassIconButton(
                icon: Icons.code,
                tooltip: '行内代码',
                onPressed: () => _replaceSelection('`', '`', placeholder: '代码'),
              ),
              GlassIconButton(
                icon: Icons.link,
                tooltip: '链接',
                onPressed: () =>
                    _replaceSelection('[', '](https://)', placeholder: '链接'),
              ),
              GlassIconButton(
                icon: Icons.format_list_bulleted,
                tooltip: '无序列表',
                onPressed: () => _prefixSelectedLines('- '),
              ),
              GlassIconButton(
                icon: Icons.format_list_numbered,
                tooltip: '有序列表',
                onPressed: () => _prefixSelectedLines('1. '),
              ),
              GlassIconButton(
                icon: Icons.format_quote,
                tooltip: '引用',
                onPressed: () => _prefixSelectedLines('> '),
              ),
              GlassIconButton(
                icon: Icons.checklist,
                tooltip: '任务列表',
                onPressed: () => _prefixSelectedLines('- [ ] '),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _statsForText(String text) {
  final trimmed = text.trim();
  if (trimmed.isEmpty) return '0 字 · 1 行';
  final lines = '\n'.allMatches(text).length + 1;
  final cjk = RegExp(r'[\u3400-\u9fff]').allMatches(text).length;
  final words = RegExp(r'[A-Za-z0-9_]+').allMatches(text).length;
  final units = cjk + words;
  final minutes = math.max(1, (units / 400).ceil());
  return '$units 字 · $lines 行 · $minutes 分钟';
}
