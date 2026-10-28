import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import '../core/document_assets.dart';
import '../markdown/model.dart';
import '../ui/theme.dart';

class DocumentImageScope extends InheritedWidget {
  const DocumentImageScope({super.key, required this.basePath, required super.child});
  final String? basePath;
  static String? of(BuildContext context) => context.dependOnInheritedWidgetOfExactType<DocumentImageScope>()?.basePath;
  @override
  bool updateShouldNotify(DocumentImageScope oldWidget) => oldWidget.basePath != basePath;
}

class MarkdownImage extends StatefulWidget {
  const MarkdownImage({
    super.key,
    required this.block,
    required this.palette,
    this.basePath,
  });

  final ImageBlock block;
  final ReaderPalette palette;
  final String? basePath;

  @override
  State<MarkdownImage> createState() => _MarkdownImageState();
}

class _MarkdownImageState extends State<MarkdownImage> {
  ImageBlock get block => widget.block;
  ReaderPalette get palette => widget.palette;
  String? get basePath => widget.basePath ?? DocumentImageScope.of(context);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: _buildImage(context),
      ),
    );
  }

  Widget _buildImage(BuildContext context) {
    final url = block.url;
    // Layout height alone does not limit decoded bitmap allocation.
    final pixelRatio = MediaQuery.devicePixelRatioOf(context);
    final decodeWidth = (MediaQuery.sizeOf(context).width * pixelRatio)
        .round()
        .clamp(1, 1536);
    final decodeHeight = (260 * pixelRatio).round().clamp(1, 1024);
    Widget errorCard() => Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      color: palette.codeBackground,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.broken_image_outlined, color: palette.secondary),
          const SizedBox(height: 6),
          Text(
            block.alt.isEmpty
                ? '图片加载失败'
                : block.alt.substring(0, block.alt.length.clamp(0, 512)),
            style: TextStyle(color: palette.secondary, fontSize: 13),
          ),
          if (!url.startsWith('http') && !url.startsWith('data:') && Platform.isAndroid && basePath != null)
            TextButton.icon(
              icon: const Icon(Icons.folder_open),
              label: const Text('选择 Markdown 所在目录'),
              onPressed: () async {
                final messenger = ScaffoldMessenger.of(context);
                try {
                  final selected = await DocumentAssets.associateDirectory(basePath!);
                  if (selected && mounted) setState(() {});
                } catch (_) {
                  if (mounted) {
                    messenger.showSnackBar(
                      const SnackBar(content: Text('无法打开目录，请重试')),
                    );
                  }
                }
              },
            ),
          Text(
            url.startsWith('data:')
                ? '内嵌图片'
                : url.substring(0, url.length.clamp(0, 512)),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: palette.secondary.withValues(alpha: 0.7),
              fontSize: 11,
            ),
          ),
        ],
      ),
    );

    if (url.startsWith('http://') || url.startsWith('https://')) {
      return Image.network(
        url,
        cacheWidth: decodeWidth,
        cacheHeight: decodeHeight,
        width: double.infinity,
        height: 260,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stack) => errorCard(),
        loadingBuilder: (context, child, progress) {
          if (progress == null) return child;
          return const SizedBox(
            height: 160,
            child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
          );
        },
      );
    }

    if (url.startsWith('data:image/')) {
      try {
        final comma = url.indexOf(',');
        if (comma > 0) {
          final bytes = base64Decode(url.substring(comma + 1));
          return Image.memory(
            bytes,
            cacheWidth: decodeWidth,
            cacheHeight: decodeHeight,
            width: double.infinity,
            height: 260,
            fit: BoxFit.cover,
            errorBuilder: (context, error, stack) => errorCard(),
          );
        }
      } on FormatException {
        return errorCard();
      }
    }

    final document = basePath;
    final lookup = document == null
        ? Future<String?>.value(DocumentAssets.localReference(url))
        : DocumentAssets.resolve(document, url);
    return FutureBuilder<String?>(future: lookup, builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) {
        return const SizedBox(height: 160, child: Center(child: CircularProgressIndicator(strokeWidth: 2)));
      }
      if (snapshot.data == null) return errorCard();
      return Image.file(
      File(snapshot.data!),
      cacheWidth: decodeWidth,
      cacheHeight: decodeHeight,
      width: double.infinity,
      height: 260,
      fit: BoxFit.contain,
      errorBuilder: (context, error, stack) => errorCard(),
    );
    });
  }
}
