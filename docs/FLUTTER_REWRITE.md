# Liquid Ink 2.0 · Flutter 重构报告

> 本文记录 2.0 的实现与当时测试。后续确认“惰性列表”仍有超大单块布局、整篇源码输入和图片解码风险，2.1 已调整；以 [STABILITY_2_1.md](STABILITY_2_1.md) 的修复证据与验证边界为准。本文关于“不会重复崩溃”的表述不代表手机实测结论。

## 1. 问题与决定

1.x 在 Jetpack Compose 的预览界面中，滑动到少量内容后仍会闪退。上一次局部修补针对的是可疑的测量路径，但没有替换真正的渲染栈，因此没有解决你看到的退出。

这次按你的要求推倒重来：

- **产品 UI 全部改为 Dart / Flutter**；
- Kotlin/Compose 界面不再参与 APK 构建；
- 不再沿用 Compose 的 LazyColumn 预览、Intrinsic 测量和自绘玻璃滚动层；
- 重新实现本地文件、Markdown 解析、目录、阅读背景、玻璃、动效和自动保存。

## 2. 为什么 Flutter 版本不会重复同一类崩溃

### 2.1 块与渲染彻底分离

`lib/markdown/parser.dart` 只负责把文本切成不可变块：

- `HeadingBlock`
- `ParagraphBlock`
- `ListItemBlock`（每个列表项就是一个块）
- `QuoteBlock`
- `CodeBlock`
- `TableBlock`
- `ImageBlock`
- `DefinitionBlock`
- `RuleBlock`

解析器是单遍线性扫描。所有循环都保证推进索引；随机畸形输入有 250 轮回归。

### 2.2 预览使用专用的定位列表

`lib/ui/preview.dart` 使用 `ScrollablePositionedList.builder`：

- 每个块是独立惰性 item；
- 目录条目保存 `blockIndex`；
- 点击目录时：
  - 动效开启 → `scrollTo(index: blockIndex, duration: 360ms)`；
  - 动效关闭 → `jumpTo(index: blockIndex)`（不会触发包内 duration 断言）；
- 不再估算像素，也不构建整篇 `Column`。

### 2.3 滚动区没有这些高风险结构

- **没有** `IntrinsicHeight` / `IntrinsicWidth`；
- **没有** 嵌套的纵向 `ListView`；
- **没有** 实时 `BackdropFilter`；
- 表格是横向滚动，最多 32 列、最多渲染 300 行；
- 图片固定最大高度 260dp，并带错误卡片；
- 长列表的每个项目都是独立块，不存在一次布局 500 行的单 item；
- 液体背景只由一个 `CustomPainter` + `RepaintBoundary` 绘制，不触发内容重组。

### 2.4 在测试中抓到的真实布局断言

源码/实时/预览切换测试暴露了一个真实问题：

```
A RenderAnimatedSize was mutated in its own performLayout implementation.
```

原因是用 `AnimatedSize` 包裹“顶部栏/底部工具条”，而在切换到源码模式时底部格式栏出现，导致 AnimatedSize 在自己的布局阶段改变尺寸。该路径会直接触发 Flutter 断言并终止。

修复：移除编辑器顶部/底部以及设置页的 `AnimatedSize`，让栏高直接随内容变化；目录、页面、分段控件的其他动画保留。修复后源码/实时/预览切换测试通过。

### 2.5 所有错误边界

- Markdown 解析失败：保留源码编辑能力，显示排版错误状态，不退出。
- 文件导入超过 24MB：拒绝并提示。
- 文件读取失败：Snackbar + 回到列表。
- 图片失败：显示地址与 alt 的错误卡片。
- 保存失败：保留 dirty 状态，不离开编辑器。
- 导出取消：不视为错误。

## 3. 功能对齐

| 能力 | 2.0 实现 |
| --- | --- |
| 打开/导入本地 Markdown | `file_picker` 系统选择器，复制到 App 私有工作目录 |
| 阅读 | 惰性块渲染，代码/表格/引用/任务列表/图片 |
| 目录 | H1–H3，按块索引精确跳转 |
| 阅读背景 | 默认 / 羊皮纸，羊皮纸固定暖色 |
| 玻璃 | 开关 + 低/中/高强度，固定 chrome 和面板 |
| 动效 | 页面/目录/面板/按钮；App 开关 + 系统减少动效 |
| 编辑 | 源码、分栏、预览；格式快捷键；查找；专注模式 |
| 保存 | 自动保存、保存、导出/另存为、重命名、删除 |
| 隐私 | 无账号、无云端初始化、无存储权限 |

## 4. 验证

### 自动化

```bash
flutter analyze
flutter test
```

结果：

- analyze：**0 issues**。
- test：**19 tests passed**。
- 覆盖：
  - 标题/Setext/围栏/表格/长列表/任务/图片；
  - 250 轮随机畸形输入；
  - 行内格式解析；
  - 1200+ 块的目录跳转与 fling；
  - 900 项列表跳到末项；
  - 真实 EditorScreen 的目录点击→滚动；
  - 源码/实时/预览切换（同时捕获并修复了 `RenderAnimatedSize` 在自身布局中变脏的真实断言）；
  - 应用根启动与中文本地化；
  - 设置持久化、首页/设置页手机尺寸布局；
  - 羊皮纸 WCAG 对比度。

### 构建

```bash
flutter build apk --release
```

产物：`dist/LiquidInk-2.0.0-flutter.apk`
包名：`com.yg.liquidink`
版本：`2.0.0 (2)`
签名：本机 Android Debug 证书（验收用途，不是商店签名）
大小：53,095,519 bytes（约 50.6 MB，通用 ABI）
SHA-256：`BE1DEE968E2846474FD0BBE7CCC7DE9CF5C4E083FBB352BCDC98B6A15D3E15BC`

### 手动验收清单

1. 打开含 H1–H3、表格、引用、代码块、任务列表的 >500 行文档。
2. 连续滑到文末，再快速回滑到开头；观察白屏、闪退、卡死。
3. 打开「目录大纲」，点最后一个 H3；确认标题出现在可视区。
4. 关掉「界面动效」，重复目录跳转；应是瞬时定位。
5. 切换默认/羊皮纸；在系统深色模式下确认羊皮纸仍是暖色。
6. 切换玻璃开关与低/中/高强度；确认设置预览与返回后的面板变化。
7. 杀进程重开，确认阅读外观设置保留。
8. 导入一个文件，编辑后导出，确认导出文件内容完整。

## 5. 已知边界

- 不是完整 CommonMark；复杂嵌套引用、HTML 和数学扩展没有实现。
- 打开文件采用“私有工作副本 + 导出”模型，而不是直接写回原 SAF URI；这是为了在无存储权限、无持久 URI 的情况下保证自动保存与最近文档可用。
- 远程图片是可选的网络功能；离线时显示错误卡片，不影响本地阅读。
- 本轮自动化是 Flutter headless 测试，没有物理设备上的 IME/TalkBack 验收；APK 已构建，供你安装实测。
