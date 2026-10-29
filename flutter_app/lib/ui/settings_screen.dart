import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/settings.dart';
import '../core/diagnostics.dart';
import '../widgets/glass.dart';
import 'theme.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key, required this.controller});

  final SettingsController controller;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final settings = controller.value;
        final motion = motionOf(context);
        return LiquidBackground(
          animate: motion && settings.glassEffectsEnabled,
          child: Scaffold(
            backgroundColor: Colors.transparent,
            body: SafeArea(
              child: Column(
                children: [
                  _SettingsTopBar(onBack: () => Navigator.of(context).pop()),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(12, 4, 12, 10),
                      child: _SettingsCard(
                        child: ListView(
                          padding: const EdgeInsets.fromLTRB(18, 18, 18, 30),
                          children: [
                            Text(
                              '偏好设置',
                              style: Theme.of(context).textTheme.headlineSmall
                                  ?.copyWith(fontWeight: FontWeight.w700),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '所有更改立即生效，并只保存在本机。',
                              style: TextStyle(
                                color: Theme.of(
                                  context,
                                ).colorScheme.onSurfaceVariant,
                                fontSize: 12.5,
                              ),
                            ),
                            const _SectionDivider(),
                            const _SectionLabel('阅读外观'),
                            Text(
                              '独立于全局深浅色的文档背景，仅影响阅读页。',
                              style: TextStyle(
                                color: Theme.of(
                                  context,
                                ).colorScheme.onSurfaceVariant,
                                fontSize: 12.5,
                              ),
                            ),
                            const SizedBox(height: 10),
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: [
                                for (final background
                                    in ReaderBackground.values)
                                  ChoiceChip(
                                    label: Text(background.label),
                                    selected:
                                        settings.readerBackground == background,
                                    onSelected: (_) => controller
                                        .setReaderBackground(background),
                                  ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            _ReaderPreview(settings: settings),
                            const SizedBox(height: 14),
                            _SettingsSwitch(
                              title: '液态玻璃效果',
                              description: '关闭后使用清晰、平面的面板；阅读背景仍然可选。',
                              value: settings.glassEffectsEnabled,
                              onChanged: controller.setGlassEffectsEnabled,
                            ),
                            if (settings.glassEffectsEnabled) ...[
                              const SizedBox(height: 8),
                              Text(
                                '玻璃强度',
                                style: TextStyle(
                                  color: Theme.of(
                                    context,
                                  ).colorScheme.onSurfaceVariant,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              const SizedBox(height: 8),
                              GlassSegmentedControl<GlassIntensity>(
                                values: GlassIntensity.values,
                                selected: settings.glassIntensity,
                                labelOf: (intensity) => switch (intensity) {
                                  GlassIntensity.low => '低',
                                  GlassIntensity.medium => '中',
                                  GlassIntensity.high => '高',
                                },
                                onSelected: controller.setGlassIntensity,
                              ),
                            ],
                            const SizedBox(height: 10),
                            _SettingsSwitch(
                              title: '界面动效',
                              description: '关闭后页面切换、目录和按钮反馈立即完成；系统“减少动态效果”优先。',
                              value: settings.animationsEnabled,
                              onChanged: controller.setAnimationsEnabled,
                            ),
                            const _SectionDivider(),
                            const _SectionLabel('主题'),
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: [
                                for (final color in AppColorTheme.values)
                                  ChoiceChip(
                                    avatar: CircleAvatar(
                                      backgroundColor: color.seed,
                                      radius: 8,
                                    ),
                                    label: Text(color.label),
                                    selected: settings.colorTheme == color,
                                    onSelected: (_) =>
                                        controller.setColorTheme(color),
                                  ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            GlassSegmentedControl<AppThemeMode>(
                              values: AppThemeMode.values,
                              selected: settings.themeMode,
                              labelOf: (mode) => switch (mode) {
                                AppThemeMode.system => '系统',
                                AppThemeMode.light => '浅色',
                                AppThemeMode.dark => '深色',
                              },
                              onSelected: controller.setThemeMode,
                            ),
                            const SizedBox(height: 12),
                            _SettingsSwitch(
                              title: '增强文字对比度',
                              description: '提高玻璃面板不透明度与边框对比，适合强光环境。',
                              value: settings.highContrast,
                              onChanged: controller.setHighContrast,
                            ),
                            const _SectionDivider(),
                            const _SectionLabel('自动保存'),
                            _SettingsSwitch(
                              title: '自动保存到本地副本',
                              description: '停止输入一段时间后写回 App 的本地工作文件；随时可导出。',
                              value: settings.autoSaveEnabled,
                              onChanged: controller.setAutoSaveEnabled,
                            ),
                            if (settings.autoSaveEnabled)
                              Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: Wrap(
                                  spacing: 8,
                                  runSpacing: 8,
                                  children: [
                                    for (final seconds in const [5, 15, 30, 60])
                                      _IntervalChip(
                                        seconds: seconds,
                                        selected:
                                            settings.autoSaveSeconds == seconds,
                                        onTap: () => controller
                                            .setAutoSaveSeconds(seconds),
                                      ),
                                  ],
                                ),
                              ),
                            const _SectionDivider(),
                            const _SectionLabel('数据与隐私'),
                            ListTile(
                              contentPadding: EdgeInsets.zero,
                              leading: const Icon(Icons.bug_report_outlined),
                              title: const Text('导出诊断报告'),
                              subtitle: const Text(
                                '包含设备与退出原因，不含文档正文；仅保存到你选择的位置。',
                              ),
                              onTap: () async {
                                try {
                                  final saved =
                                      await AppDiagnostics.exportReport();
                                  if (saved && context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(content: Text('诊断报告已导出')),
                                    );
                                  }
                                } catch (_) {
                                  if (context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(content: Text('导出失败，请重试')),
                                    );
                                  }
                                }
                              },
                            ),
                            Text(
                              '文件保存在 App 的私有文档目录；不会上传，也不需要账号。'
                              '打开文件时会将内容复制到本地工作副本，通过“导出 / 另存为”写回你选择的文件。',
                              style: TextStyle(
                                color: Theme.of(
                                  context,
                                ).colorScheme.onSurfaceVariant,
                                fontSize: 12.5,
                                height: 1.6,
                              ),
                            ),
                            const SizedBox(height: 24),
                            TextButton(
                              onPressed: () async {
                                final license = await rootBundle.loadString(
                                  'assets/liquid-glass-react-LICENSE.txt',
                                );
                                if (!context.mounted) return;
                                showLicensePage(
                                  context: context,
                                  applicationName: 'QuikPad',
                                  applicationVersion: '2.3.0',
                                  applicationLegalese: license,
                                );
                              },
                              child: const Text('开源许可'),
                            ),
                            Center(
                              child: Text(
                                'QuikPad 2.3.0 · Flutter',
                                style: TextStyle(
                                  color: Theme.of(context)
                                      .colorScheme
                                      .onSurfaceVariant
                                      .withValues(alpha: 0.55),
                                  fontSize: 11,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _SettingsTopBar extends StatelessWidget {
  const _SettingsTopBar({required this.onBack});

  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
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
              onPressed: onBack,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                '外观与设置',
                style: Theme.of(
                  context,
                ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            scheme.surface.withValues(alpha: 0.97),
            scheme.surfaceContainerLow.withValues(alpha: 0.94),
          ],
        ),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.55),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(type: MaterialType.transparency, child: child),
    );
  }
}

class _ReaderPreview extends StatelessWidget {
  const _ReaderPreview({required this.settings});

  final AppSettings settings;

  @override
  Widget build(BuildContext context) {
    final palette = ReaderPalette.from(
      context,
      background: settings.readerBackground,
    );
    final label = switch (settings.glassIntensity) {
      GlassIntensity.low => '低',
      GlassIntensity.medium => '中',
      GlassIntensity.high => '高',
    };
    final sample = Container(
      height: 104,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: palette.gradient,
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: palette.border),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  '章节标题 · H2',
                  style: TextStyle(
                    color: palette.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  settings.readerBackground == ReaderBackground.paper
                      ? '默认阅读背景会跟随应用主题。'
                      : '${settings.readerBackground.label}纸面，独立于全局深浅色。',
                  style: TextStyle(
                    color: palette.secondary,
                    fontSize: 11.5,
                    height: 1.5,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          GlassPanel(
            borderRadius: 14,
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  settings.glassEffectsEnabled
                      ? Icons.blur_on
                      : Icons.layers_clear_outlined,
                  size: 17,
                  color: palette.link,
                ),
                const SizedBox(height: 3),
                Text(
                  settings.glassEffectsEnabled ? '玻璃 $label' : '平面',
                  style: TextStyle(color: palette.secondary, fontSize: 10),
                ),
              ],
            ),
          ),
        ],
      ),
    );
    return Theme(
      data: buildReaderTheme(Theme.of(context), palette),
      child: sample,
    );
  }
}

class _SettingsSwitch extends StatelessWidget {
  const _SettingsSwitch({
    required this.title,
    required this.description,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final String description;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: () => onChanged(!value),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      color: scheme.onSurface,
                      fontSize: 14.5,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    description,
                    style: TextStyle(
                      color: scheme.onSurfaceVariant,
                      fontSize: 11.5,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Switch.adaptive(
              value: value,
              onChanged: onChanged,
              activeTrackColor: scheme.primary.withValues(alpha: 0.55),
            ),
          ],
        ),
      ),
    );
  }
}

class _IntervalChip extends StatelessWidget {
  const _IntervalChip({
    required this.seconds,
    required this.selected,
    required this.onTap,
  });

  final int seconds;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: selected
          ? scheme.primary.withValues(alpha: 0.16)
          : scheme.surfaceContainerHighest.withValues(alpha: 0.42),
      borderRadius: BorderRadius.circular(10),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          child: Text(
            '${seconds}s',
            style: TextStyle(
              color: selected ? scheme.primary : scheme.onSurfaceVariant,
              fontSize: 12,
              fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        text,
        style: TextStyle(
          color: Theme.of(context).colorScheme.onSurface,
          fontSize: 13,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _SectionDivider extends StatelessWidget {
  const _SectionDivider();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 18),
      child: Divider(
        color: Theme.of(
          context,
        ).colorScheme.outlineVariant.withValues(alpha: 0.38),
        height: 1,
      ),
    );
  }
}
