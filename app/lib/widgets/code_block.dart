import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Wraps every code block with a language label and a copy button.
Widget codeWrapper(Widget child, String code, String language) => _CodeFrame(code: code, language: language, child: child);

class _CodeFrame extends StatelessWidget {
  const _CodeFrame({required this.code, required this.language, required this.child});

  final String code;
  final String language;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Stack(
      children: [
        child,
        Positioned(
          top: 10,
          right: 6,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (language.isNotEmpty && language != 'text')
                Text(language, style: TextStyle(fontSize: 11, color: scheme.outline)),
              IconButton(
                tooltip: 'Copy code',
                visualDensity: VisualDensity.compact,
                iconSize: 18,
                icon: const Icon(Icons.copy_rounded),
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: code));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context)
                      ..hideCurrentSnackBar()
                      ..showSnackBar(const SnackBar(content: Text('Code copied'), duration: Duration(seconds: 1)));
                  }
                },
              ),
            ],
          ),
        ),
      ],
    );
  }
}
