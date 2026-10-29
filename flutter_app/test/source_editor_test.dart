import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_ink/widgets/source_editor.dart';

void main() {
  testWidgets(
    'page edits, external selections and large pastes preserve all text',
    (tester) async {
      final original = List.generate(3000, (i) => 'line $i 😀\r\n').join();
      final controller = TextEditingController(text: original);
      final focus = FocusNode();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SourceEditor(controller: controller, focusNode: focus),
          ),
        ),
      );
      final field = find.byType(TextField);
      // OPPO/OnePlus treats no-suggestion fields as secure password inputs and
      // replaces the whole screen with a black secure keyboard.
      final fieldWidget = tester.widget<TextField>(field);
      expect(fieldWidget.autocorrect, isTrue);
      expect(fieldWidget.enableSuggestions, isTrue);
      String pageText() => tester.widget<TextField>(field).controller!.text;
      final first = pageText();
      await tester.enterText(field, '${first}INSERT');
      expect(
        controller.text,
        original.replaceRange(first.length, first.length, 'INSERT'),
      );
      await tester.tap(find.byTooltip('下一段'));
      await tester.pumpAndSettle();
      expect(pageText(), isNot(startsWith('line 0')));
      await tester.tap(find.byTooltip('上一段'));
      await tester.pumpAndSettle();
      expect(pageText(), '${first}INSERT');

      final lastOffset = controller.text.indexOf('line 2999');
      controller.selection = TextSelection.collapsed(offset: lastOffset);
      await tester.pump();
      expect(pageText(), contains('line 2999'));
      final beforePaste = controller.text;
      final lastPage = pageText();
      final paste = 'PASTE😀' * 5000;
      await tester.enterText(field, '$lastPage$paste');
      await tester.pump();
      expect(controller.text, '$beforePaste$paste');
      expect(pageText().length, lessThanOrEqualTo(8192));
      expect(tester.takeException(), isNull);

      await tester.pumpWidget(const SizedBox());
      controller.dispose();
      focus.dispose();
    },
  );
}
