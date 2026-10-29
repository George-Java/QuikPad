import 'package:flutter/material.dart';

import '../markdown/model.dart';
import '../widgets/glass.dart';

class OutlineSheet extends StatelessWidget {
  const OutlineSheet({super.key, required this.entries, required this.parsing});

  final List<OutlineEntry> entries;
  final bool parsing;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
        child: GlassPanel(
          borderRadius: 28,
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 12),
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * 0.68,
            ),
            child: Material(
              type: MaterialType.transparency,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 6, 4, 4),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '目录',
                                style: Theme.of(context).textTheme.titleLarge
                                    ?.copyWith(fontWeight: FontWeight.w700),
                              ),
                              Text(
                                entries.isEmpty
                                    ? (parsing ? '正在解析标题…' : '当前文档没有 H1–H3 标题')
                                    : '${entries.length} 个标题',
                                style: TextStyle(
                                  color: scheme.onSurfaceVariant,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ),
                        ),
                        GlassIconButton(
                          icon: Icons.close,
                          tooltip: '关闭目录',
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 12),
                  if (entries.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 24,
                        vertical: 36,
                      ),
                      child: Text(
                        parsing ? '正在解析标题…' : '当前文档没有 H1–H3 标题',
                        style: TextStyle(color: scheme.onSurfaceVariant),
                      ),
                    )
                  else
                    Flexible(
                      child: ListView.builder(
                        shrinkWrap: true,
                        padding: const EdgeInsets.fromLTRB(4, 4, 4, 4),
                        itemCount: entries.length,
                        itemBuilder: (context, index) {
                          final entry = entries[index];
                          return _OutlineRow(
                            entry: entry,
                            onTap: () => Navigator.of(context).pop(entry),
                          );
                        },
                      ),
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

class _OutlineRow extends StatelessWidget {
  const _OutlineRow({required this.entry, required this.onTap});

  final OutlineEntry entry;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final left = 12.0 + (entry.level - 1) * 16.0;
    return Semantics(
      button: true,
      label: 'H${entry.level}，${entry.title}，跳转',
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: EdgeInsets.fromLTRB(left, 11, 12, 11),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                width: entry.level == 1 ? 3 : 2,
                height: entry.level == 1 ? 23 : 17,
                decoration: BoxDecoration(
                  color: entry.level == 1
                      ? scheme.primary
                      : scheme.outlineVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  entry.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: entry.level == 1
                        ? scheme.onSurface
                        : scheme.onSurfaceVariant,
                    fontSize: entry.level == 1 ? 16 : 14.5,
                    fontWeight: entry.level == 1
                        ? FontWeight.w700
                        : FontWeight.w500,
                    height: 1.35,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
