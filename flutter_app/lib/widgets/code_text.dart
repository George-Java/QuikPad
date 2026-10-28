import 'package:flutter/material.dart';

import '../markdown/code_highlighter.dart';

class CodeText extends StatelessWidget {
  const CodeText({super.key, required this.source, this.language,
    this.start = 0, this.end, required this.style});
  final String source;
  final String? language;
  final int start;
  final int? end;
  final TextStyle style;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    Color? color(String? kind) {
      if (kind == null) return null;
      if (RegExp(r'comment|quote').hasMatch(kind)) return dark ? const Color(0xFF9FAAB9) : const Color(0xFF58616B);
      if (RegExp(r'keyword|selector-tag|meta|doctag|tag').hasMatch(kind)) return dark ? const Color(0xFFC9A7FF) : const Color(0xFF7733A0);
      if (RegExp(r'string|regexp|attribute|attr|selector-attr|addition').hasMatch(kind)) return dark ? const Color(0xFFA6DEA0) : const Color(0xFF286234);
      if (RegExp(r'number|literal|built_in|type|symbol').hasMatch(kind)) return dark ? const Color(0xFFFFCB88) : const Color(0xFF87500C);
      if (RegExp(r'title|function|name|section|selector-class|property|key|params').hasMatch(kind)) return dark ? const Color(0xFF8DCDFB) : const Color(0xFF185C98);
      if (RegExp(r'variable|bullet|deletion|operator').hasMatch(kind)) return dark ? const Color(0xFFFFA9B8) : const Color(0xFF9E2845);
      if (RegExp(r'punctuation').hasMatch(kind)) return dark ? const Color(0xFFB8C4BE) : const Color(0xFF52615B);
      return null;
    }
    return Text.rich(TextSpan(style: style, children: [
      for (final token in CodeHighlighter.slice(source, language: language, start: start, end: end))
        TextSpan(text: token.text, style: TextStyle(color: color(token.kind))),
    ]));
  }
}
