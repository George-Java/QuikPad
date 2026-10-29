import 'package:flutter/material.dart';

/// A one-field prompt that owns its [TextEditingController].
///
/// The controller must outlive the dialog's exit transition: disposing it as
/// soon as `showDialog` completes leaves the still-mounted [TextField] bound to
/// a disposed controller, which throws "A TextEditingController was used after
/// being disposed" on the next frame. Owning the controller in the dialog's
/// State ties its lifetime to the route instead.
Future<String?> showTextPromptDialog(
  BuildContext context, {
  required String title,
  required String confirmLabel,
  String initialText = '',
  String? labelText,
  String? hintText,
  TextInputAction textInputAction = TextInputAction.done,
}) {
  return showDialog<String>(
    context: context,
    builder: (context) => _TextPromptDialog(
      title: title,
      confirmLabel: confirmLabel,
      initialText: initialText,
      labelText: labelText,
      hintText: hintText,
      textInputAction: textInputAction,
    ),
  );
}

class _TextPromptDialog extends StatefulWidget {
  const _TextPromptDialog({
    required this.title,
    required this.confirmLabel,
    required this.initialText,
    required this.labelText,
    required this.hintText,
    required this.textInputAction,
  });

  final String title;
  final String confirmLabel;
  final String initialText;
  final String? labelText;
  final String? hintText;
  final TextInputAction textInputAction;

  @override
  State<_TextPromptDialog> createState() => _TextPromptDialogState();
}

class _TextPromptDialogState extends State<_TextPromptDialog> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.initialText)
        ..selection = TextSelection(
          baseOffset: 0,
          extentOffset: widget.initialText.length,
        );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() => Navigator.of(context).pop(_controller.text);

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        controller: _controller,
        autofocus: true,
        textInputAction: widget.textInputAction,
        // Keep the normal IME. Some Android skins (notably OPPO/OnePlus) switch
        // to a full-screen "secure input" keyboard when an app marks a field
        // with the no-suggestion/visible-password flags, which leaves the whole
        // screen black while typing. Smart quotes are disabled separately.
        autocorrect: true,
        enableSuggestions: true,
        smartQuotesType: SmartQuotesType.disabled,
        smartDashesType: SmartDashesType.disabled,
        decoration: InputDecoration(
          labelText: widget.labelText,
          hintText: widget.hintText,
        ),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('取消'),
        ),
        FilledButton(onPressed: _submit, child: Text(widget.confirmLabel)),
      ],
    );
  }
}

Future<T?> showGlassSheet<T>(
  BuildContext context, {
  required WidgetBuilder builder,
  required bool animate,
}) {
  return showGeneralDialog<T>(
    context: context,
    barrierDismissible: true,
    barrierLabel: '关闭',
    barrierColor: Colors.black.withValues(alpha: 0.26),
    transitionDuration: animate
        ? const Duration(milliseconds: 210)
        : Duration.zero,
    pageBuilder: (context, animation, secondaryAnimation) {
      return Align(
        alignment: Alignment.bottomCenter,
        child: Material(
          type: MaterialType.transparency,
          child: Padding(
            padding: EdgeInsets.only(
              bottom: MediaQuery.viewInsetsOf(context).bottom,
            ),
            // A sheet taller than the phone must scroll instead of putting its
            // last row (delete, for example) below the bottom edge.
            child: ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.sizeOf(context).height * 0.86,
              ),
              child: Builder(builder: builder),
            ),
          ),
        ),
      );
    },
    transitionBuilder: (context, animation, secondaryAnimation, child) {
      if (!animate) return child;
      final curved = CurvedAnimation(
        parent: animation,
        curve: Curves.easeOutCubic,
        reverseCurve: Curves.easeInCubic,
      );
      return FadeTransition(
        opacity: curved,
        child: SlideTransition(
          position: Tween<Offset>(
            begin: const Offset(0, 0.035),
            end: Offset.zero,
          ).animate(curved),
          child: child,
        ),
      );
    },
  );
}
