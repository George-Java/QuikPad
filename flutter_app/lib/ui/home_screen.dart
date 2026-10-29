import 'package:flutter/material.dart';

import '../core/document_store.dart';
import '../core/settings.dart';
import '../widgets/glass.dart';
import 'editor.dart';
import 'routes.dart';
import 'sheets.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.store, required this.settings});

  final DocumentStore store;
  final SettingsController settings;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  List<DocumentMeta> _documents = const [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load(seed: true);
  }

  Future<void> _load({bool seed = false}) async {
    if (mounted) setState(() => _loading = true);
    try {
      var documents = await widget.store.listDocuments();
      if (seed && documents.isEmpty) {
        await widget.store.ensureWelcomeDocument();
        documents = await widget.store.listDocuments();
      }
      if (mounted) {
        setState(() {
          _documents = documents;
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() => _loading = false);
        _showError('读取本地文档失败');
      }
    }
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _open(NoteDocument document) async {
    await Navigator.of(context).push<bool>(
      liquidRoute<bool>(
        builder: (_) => EditorScreen(
          document: document,
          store: widget.store,
          settings: widget.settings,
        ),
        animate: motionOf(context),
      ),
    );
    await _load();
  }

  Future<void> _import() async {
    try {
      final document = await widget.store.importFromPicker();
      if (document == null) return;
      await _load();
      if (mounted) await _open(document);
    } catch (error) {
      _showError('打开文件失败，请确认文件格式与大小');
    }
  }

  Future<void> _create() async {
    final name = await showTextPromptDialog(
      context,
      title: '新建 Markdown',
      labelText: '文件名',
      hintText: '例如：今天的想法.md',
      confirmLabel: '创建',
    );
    if (name == null || name.trim().isEmpty || !mounted) return;
    try {
      final document = await widget.store.createDocument(
        name: name.trim(),
        text: '# ${name.trim()}\n\n',
      );
      await _load();
      if (mounted) await _open(document);
    } catch (error) {
      _showError('新建文档失败');
    }
  }

  Future<void> _openSettings() async {
    final animate = motionOf(context);
    await Navigator.of(context).push<void>(
      liquidRoute<void>(
        builder: (_) => SettingsScreen(controller: widget.settings),
        animate: animate,
      ),
    );
    if (mounted) setState(() {});
  }

  Future<void> _delete(DocumentMeta meta) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除本地副本？'),
        content: Text('“${meta.name}”将从 App 的本地文档目录中删除。'),
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
    try {
      await widget.store.deleteDocument(
        NoteDocument(path: meta.path, name: meta.name, text: ''),
      );
      await _load();
    } catch (error) {
      _showError('删除失败');
    }
  }

  Future<void> _openMeta(DocumentMeta meta) async {
    try {
      final document = await widget.store.readDocument(meta.path);
      if (mounted) await _open(document);
    } catch (error) {
      _showError('文档已经不存在或无法读取');
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final settings = GlassScope.of(context);
    final motion = motionOf(context);
    final scheme = Theme.of(context).colorScheme;
    return LiquidBackground(
      animate: motion && settings.glassEffectsEnabled,
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          child: RefreshIndicator(
            onRefresh: () => _load(),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(
                parent: BouncingScrollPhysics(),
              ),
              padding: const EdgeInsets.fromLTRB(18, 12, 18, 30),
              children: [
                Row(
                  children: [
                    const _Mark(),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'QuikPad',
                            style: Theme.of(context).textTheme.titleLarge
                                ?.copyWith(
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: -0.4,
                                ),
                          ),
                          Text(
                            'LOCAL MARKDOWN STUDIO',
                            style: TextStyle(
                              color: scheme.onSurfaceVariant,
                              fontSize: 9,
                              letterSpacing: 1.5,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                    GlassIconButton(
                      icon: Icons.palette_outlined,
                      tooltip: '外观与设置',
                      onPressed: _openSettings,
                    ),
                  ],
                ),
                const SizedBox(height: 26),
                _HeroCard(onImport: _import, onCreate: _create),
                const SizedBox(height: 24),
                Row(
                  children: [
                    Text(
                      '最近文档',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      _documents.isEmpty ? '' : '${_documents.length}',
                      style: TextStyle(
                        color: scheme.onSurfaceVariant,
                        fontSize: 12,
                      ),
                    ),
                    const Spacer(),
                    if (_loading)
                      const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 1.8),
                      ),
                  ],
                ),
                const SizedBox(height: 10),
                if (!_loading && _documents.isEmpty)
                  GlassPanel(
                    borderRadius: 20,
                    padding: const EdgeInsets.all(20),
                    child: Row(
                      children: [
                        Icon(Icons.note_add_outlined, color: scheme.primary),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            '还没有文档。打开一个 Markdown 文件，或新建一页。',
                            style: TextStyle(
                              color: scheme.onSurfaceVariant,
                              height: 1.5,
                            ),
                          ),
                        ),
                      ],
                    ),
                  )
                else
                  for (final document in _documents)
                    _DocumentCard(
                      meta: document,
                      onOpen: () => _openMeta(document),
                      onDelete: () => _delete(document),
                    ),
                const SizedBox(height: 22),
                GlassPanel(
                  borderRadius: 20,
                  padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.lock_outline, size: 17, color: scheme.primary),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          '文档只保存在本机。无账号、无云端初始化；网络只用于你主动打开的外部图片或链接。',
                          style: TextStyle(
                            color: scheme.onSurfaceVariant,
                            fontSize: 11.5,
                            height: 1.55,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Mark extends StatelessWidget {
  const _Mark();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: 46,
      height: 46,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(15),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [scheme.primary, scheme.tertiary],
        ),
        boxShadow: [
          BoxShadow(
            color: scheme.primary.withValues(alpha: 0.25),
            blurRadius: 14,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Image.asset('assets/logo.png', semanticLabel: 'QuikPad'),
      ),
    );
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard({required this.onImport, required this.onCreate});

  final VoidCallback onImport;
  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return GlassPanel(
      borderRadius: 28,
      padding: const EdgeInsets.fromLTRB(20, 22, 20, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '让文字\n安静地流动。',
            style: TextStyle(
              color: scheme.onSurface,
              fontSize: 32,
              height: 1.12,
              fontWeight: FontWeight.w800,
              letterSpacing: -1.0,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            '一个为长文阅读设计、无需登录的本地 Markdown 工作台。',
            style: TextStyle(
              color: scheme.onSurfaceVariant,
              fontSize: 13.5,
              height: 1.55,
            ),
          ),
          const SizedBox(height: 18),
          LayoutBuilder(
            builder: (context, constraints) {
              // Two side-by-side actions stop fitting once the system text
              // scale grows or the phone is narrow. Stack them instead of
              // overflowing the hero card.
              final textScale = MediaQuery.textScalerOf(context).scale(15) / 15;
              final stack = textScale > 1.35 || constraints.maxWidth < 260;
              final open = GlassButton(
                label: '打开文件',
                icon: Icons.folder_open_outlined,
                expanded: true,
                onPressed: onImport,
              );
              final create = GlassButton(
                label: '新建',
                icon: Icons.add,
                expanded: stack,
                onPressed: onCreate,
              );
              if (stack) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [open, const SizedBox(height: 10), create],
                );
              }
              return Row(
                children: [
                  Expanded(child: open),
                  const SizedBox(width: 10),
                  create,
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _DocumentCard extends StatelessWidget {
  const _DocumentCard({
    required this.meta,
    required this.onOpen,
    required this.onDelete,
  });

  final DocumentMeta meta;
  final VoidCallback onOpen;
  final VoidCallback onDelete;

  String _formatDate(DateTime value) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${value.year}-${two(value.month)}-${two(value.day)} '
        '${two(value.hour)}:${two(value.minute)}';
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Container(
        decoration: BoxDecoration(
          color: scheme.surface.withValues(alpha: 0.86),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: scheme.outlineVariant.withValues(alpha: 0.45),
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            borderRadius: BorderRadius.circular(18),
            onTap: onOpen,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 6, 12),
              child: Row(
                children: [
                  Container(
                    width: 42,
                    height: 48,
                    decoration: BoxDecoration(
                      color: scheme.primary.withValues(alpha: 0.10),
                      borderRadius: BorderRadius.circular(11),
                    ),
                    child: Icon(
                      Icons.description_outlined,
                      color: scheme.primary,
                      size: 21,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          meta.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: scheme.onSurface,
                            fontSize: 14.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '${_formatDate(meta.modified)} · ${_formatSize(meta.size)}',
                          style: TextStyle(
                            color: scheme.onSurfaceVariant,
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                  ),
                  PopupMenuButton<String>(
                    tooltip: '更多操作',
                    icon: Icon(
                      Icons.more_vert,
                      color: scheme.onSurfaceVariant,
                      size: 20,
                    ),
                    onSelected: (value) {
                      if (value == 'open') {
                        onOpen();
                      } else if (value == 'delete') {
                        onDelete();
                      }
                    },
                    itemBuilder: (context) => [
                      const PopupMenuItem(value: 'open', child: Text('打开')),
                      const PopupMenuItem(
                        value: 'delete',
                        child: Text('删除本地副本'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
