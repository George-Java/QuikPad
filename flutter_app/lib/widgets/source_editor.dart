import 'package:flutter/material.dart';

import '../core/text_chunks.dart';

/// Keeps the full document in [controller], but only one bounded range in the
/// platform text input and RenderEditable. Off-page text is never discarded.
class SourceEditor extends StatefulWidget {
  const SourceEditor({
    super.key,
    required this.controller,
    required this.focusNode,
  });
  final TextEditingController controller;
  final FocusNode focusNode;

  @override
  State<SourceEditor> createState() => _SourceEditorState();
}

class _SourceEditorState extends State<SourceEditor> {
  final _page = TextEditingController();
  List<({int start, int end})> _ranges = [];
  int _index = 0;
  int _start = 0;
  int _end = 0;
  bool _syncing = false;
  late String _text;
  String _pageText = '';

  @override
  void initState() {
    super.initState();
    _text = widget.controller.text;
    _reindex();
    _showSelection();
    _page.addListener(_onPageChanged);
    widget.controller.addListener(_onDocumentChanged);
  }

  void _reindex() {
    _ranges = _text.length <= 8192
        ? [(start: 0, end: _text.length)]
        : textChunks(_text, maxCharacters: 4096, maxLines: 80).toList();
  }

  void _showSelection() {
    final selection = widget.controller.selection;
    if (selection.isValid) {
      final offset = selection.extentOffset.clamp(0, _text.length);
      _index = _ranges.indexWhere((r) => offset >= r.start && offset < r.end);
      if (_index < 0) _index = _ranges.length - 1;
    }
    _loadPage();
  }

  void _loadPage() {
    _index = _index.clamp(0, _ranges.length - 1);
    _start = _ranges[_index].start;
    _end = _ranges[_index].end;
    final full = widget.controller.value;
    final length = _end - _start;
    _pageText = _text.substring(_start, _end);
    _syncing = true;
    _page.value = TextEditingValue(
      text: _pageText,
      selection: full.selection.isValid
          ? TextSelection(
              baseOffset: (full.selection.baseOffset - _start).clamp(0, length),
              extentOffset: (full.selection.extentOffset - _start).clamp(
                0,
                length,
              ),
            )
          : const TextSelection.collapsed(offset: 0),
    );
    _syncing = false;
  }

  void _onDocumentChanged() {
    if (_syncing) return;
    if (_text != widget.controller.text) {
      _text = widget.controller.text;
      _reindex();
    }
    setState(_showSelection);
  }

  void _onPageChanged() {
    if (_syncing) return;
    final value = _page.value;
    final oldLength = _end - _start;
    if (_pageText != value.text) {
      _text = _text.replaceRange(_start, _end, value.text);
      _pageText = value.text;
    }
    _end = _start + value.text.length;
    _syncing = true;
    widget.controller.value = TextEditingValue(
      text: _text,
      selection: value.selection.isValid
          ? TextSelection(
              baseOffset: _start + value.selection.baseOffset,
              extentOffset: _start + value.selection.extentOffset,
            )
          : const TextSelection.collapsed(offset: -1),
    );
    _syncing = false;
    if (oldLength != value.text.length ||
        (value.text.length > 8192 && value.composing.isCollapsed)) {
      // Keep the active IME composition intact. Repartition after composition
      // ends, or immediately for a large paste (which has no composing range).
      if (value.text.length > 8192 && value.composing.isCollapsed) {
        _reindex();
        setState(_showSelection);
      } else {
        final delta = value.text.length - oldLength;
        _ranges[_index] = (start: _start, end: _end);
        for (var i = _index + 1; i < _ranges.length; i++) {
          final range = _ranges[i];
          _ranges[i] = (start: range.start + delta, end: range.end + delta);
        }
      }
    }
  }

  void _turnPage(int index) {
    widget.focusNode.unfocus();
    _syncing = true;
    widget.controller.selection = TextSelection.collapsed(
      offset: _ranges[index].start,
    );
    _syncing = false;
    setState(() {
      _index = index;
      _loadPage();
    });
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onDocumentChanged);
    _page.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      children: [
        if (_ranges.length > 1)
          SizedBox(
            height: 40,
            child: Row(
              children: [
                IconButton(
                  tooltip: '上一段',
                  icon: const Icon(Icons.chevron_left),
                  onPressed: _index > 0 ? () => _turnPage(_index - 1) : null,
                ),
                Expanded(
                  child: Text(
                    '分段编辑 ${_index + 1} / ${_ranges.length}',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 12,
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
                IconButton(
                  tooltip: '下一段',
                  icon: const Icon(Icons.chevron_right),
                  onPressed: _index + 1 < _ranges.length
                      ? () => _turnPage(_index + 1)
                      : null,
                ),
              ],
            ),
          ),
        Expanded(
          child: TextField(
            key: ValueKey(_index),
            controller: _page,
            focusNode: widget.focusNode,
            expands: true,
            maxLines: null,
            minLines: null,
            textAlignVertical: TextAlignVertical.top,
            keyboardType: TextInputType.multiline,
            autocorrect: true,
            enableSuggestions: true,
            // iOS smart punctuation rewrites quotes and dashes, which changes
            // Markdown (links, code fences, front matter) as the user types.
            smartQuotesType: SmartQuotesType.disabled,
            smartDashesType: SmartDashesType.disabled,
            style: TextStyle(
              color: scheme.onSurface,
              fontFamily: 'monospace',
              fontSize: 14.2,
              height: 1.62,
            ),
            cursorColor: scheme.primary,
            decoration: const InputDecoration(
              border: InputBorder.none,
              hintText: '在这里写 Markdown…',
              contentPadding: EdgeInsets.fromLTRB(15, 12, 15, 18),
            ),
          ),
        ),
      ],
    );
  }
}
