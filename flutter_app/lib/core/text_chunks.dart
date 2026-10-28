/// UTF-16 ranges for bounded text layout. Joining the slices is lossless.
Iterable<({int start, int end})> textChunks(
  String text, {
  int maxCharacters = 2048,
  int maxLines = 32,
}) sync* {
  var start = 0;
  while (start < text.length) {
    var end = (start + maxCharacters).clamp(0, text.length);
    var cursor = start;
    var lines = 0;
    while (cursor < end) {
      final newline = text.indexOf('\n', cursor);
      if (newline < 0 || newline >= end) break;
      cursor = newline + 1;
      if (++lines == maxLines) {
        end = cursor;
        break;
      }
    }
    // Never cut a surrogate pair or a Windows line ending in half.
    if (end < text.length && end > start) {
      final previous = text.codeUnitAt(end - 1);
      if ((previous >= 0xD800 && previous <= 0xDBFF) ||
          (previous == 13 && text.codeUnitAt(end) == 10)) {
        end--;
      }
    }
    yield (start: start, end: end);
    start = end;
  }
}
