# QuikPad 2.3 · Flutter

> 一个无需登录、打开即用的本地 Markdown 阅读与编辑器。所有文档、图片和设置都留在本机；不初始化云端，不上传内容。

## 2.3 重点

### 1. 深色模式玻璃卡片右边缘竖线

深色模式下主界面的玻璃面板右边缘曾出现黑/绿相间、上下不连续的细线。根因是：

- 面板边缘用了一条 1.5px 的**斜向渐变描边**，并以 `screen` / `overlay` 混合；渐变透明段压在黑色阴影上时，会形成黑绿交替的碎片。
- 玻璃面板外围有一圈 40px 的四周阴影，放大了边缘色差。
- 非交互玻璃卡片仍会追踪指针移动并做弹性缩放；在卡片上滚动时，裁剪边缘被移动，进一步暴露了滤镜接缝。

修复：

- 移除边缘的斜向渐变描边，改为**完整的 1px 中性描边覆盖层**，压在裁剪层之上，覆盖滤镜抗锯齿接缝。
- 仅保留**顶部水平高光**，不产生任何竖向光带。
- 减少阴影并向内收，避免左右两侧出现黑边。
- 只有真正可交互的玻璃控件才响应按压/指针形变；静态卡片不再随滑动缩放。

回归测试：`dark_edge_test.dart`（1x / 2.75x / 3x DPR）和 `glass_effect_test.dart` 中的静态面板拖动测试。

### 2. 相对路径图片

Markdown 中的图片现在按以下顺序尝试读取：

1. 当前文档所在目录（工作副本或原始目录）；
2. Android 上原始文档 URI 的同级文档；
3. 用户授权过的 Markdown 所在文件夹（SAF tree，持久授权）。

例如：

```markdown
![test](assets/images/test.png)
```

会尝试读取 `当前文档目录/assets/images/test.png`。行内图片、独立图片和 HTML `<img>` 使用同一套解析；图片只在本机读取，不上传。

Android 的 SAF 单文件授权通常不允许读取同级文件；如果直接同级读取被系统拒绝，文档菜单和图片错误卡片提供一次性的「关联图片目录 / 选择 Markdown 所在文件夹」。授权持久化后，后面的相对图片直接读取。

### 3. HTML 与完整 Markdown 语法

解析层改为 CommonMark/GFM + HTML DOM，不再只识别手写子集。现在支持：

- 原始 HTML：`<h1>`–`<h6>`、`<a href>`、`<a name>`/`id`、`<img>`、`<p>`、`<strong>`、`<em>`、`<code>`、`<pre>`、`<blockquote>`、`<ul>`/`<ol>`、`<table>`、`<br>`、`<hr>`；
- HTML 实体、行内 `<u>`/`<mark>`/`<sub>`/`<sup>`；
- GFM 表格、任务列表、删除线、自动链接、脚注；
- 引用链接/图片、嵌套强调、多反引号代码、缩进代码、嵌套列表；
- 文档内锚点 `#id` 可直接跳转。

`<script>`、`<style>`、`<iframe>` 等不会执行，也不会作为文档内容显示。

### 4. 代码块高亮

代码块现在使用完整的 `highlight` 语法库，带 32KB 有界窗口、缓存和多行状态保留：

- C / C++ / C# / Java / Kotlin / Swift / Go / Rust / Dart / Objective-C；
- Python / Ruby / PHP / JavaScript / TypeScript / Lua / Perl / R / Scala / Groovy / Haskell / Elixir / Erlang / Clojure；
- HTML / XML / CSS / SCSS / LESS / Vue / Svelte；
- YAML / TOML / INI / JSON / SQL / GraphQL / Protobuf；
- Shell / Bash / PowerShell / Dockerfile / Makefile / Diff / Nginx / Vim；
- 以及 `highlight` 自带的其余语言。

代码围栏语言别名会归一化（`c`→C++ 语法、`html`→XML 语法、`sh`/`shell`→Bash 等），标题显示友好的语言名。未知语言保持原样显示，不丢字符。

## 主要能力

- 源码 / 实时分栏 / 预览三种模式。
- H1–H3 目录，按真实块索引精确定位。
- 默认、羊皮纸等阅读背景；独立于全局深浅色。
- 液态玻璃开关、低/中/高强度、顶部高光、折射与实时预览。
- 界面动效开关；遵循系统“减少动态效果”。
- 本地工作副本、自动保存、保存、导出/另存为、重命名、删除。
- 查找、格式快捷键、专注模式、字数/行数/阅读时长。
- 无账号、无云端初始化、无网络启动依赖。

## 验证

```bash
cd flutter_app
..\.tooling\flutter-sdk\flutter\bin\flutter.bat analyze
..\.tooling\flutter-sdk\flutter\bin\flutter.bat test
..\.tooling\flutter-sdk\flutter\bin\flutter.bat build apk --release
```

- `flutter analyze`：**0 issues**
- `flutter test`：**51 passed，1 skipped**
  - 1 个 skipped 是宿主不暴露 Impeller shader image filter，不是应用失败。
  - 覆盖 1200+ 块目录跳转、900 项列表、长代码、长段落、长表格、源码分段编辑、HTML 头/链接/锚点、图片相对路径、20+ 代码语言、暗色玻璃边缘与非交互卡片稳定性。
- Release APK：`dist/QuikPad-2.3.0.apk`
- 包名：`com.yg.liquidink`
- 版本：`2.3.0 (5)`
- 大小：61,586,445 bytes（约 58.7 MiB）
- SHA-256：`F5359446A00AE713873D13525D5D22E5AA4458A37FA07D10F66C181933168432`

## 目录

```
flutter_app/
├── lib/
│   ├── core/        settings, documents, assets, diagnostics, text chunks
│   ├── markdown/    model, CommonMark/GFM parser, inline, highlighting
│   ├── ui/          home, editor, preview, outline, settings, theme
│   └── widgets/     liquid glass, refractive surface, images, code text
├── test/
├── assets/
├── shaders/
└── android/
```

旧的 1.x Jetpack Compose 源码保留在 `app/` 作为历史归档，不参与 Flutter APK 构建。

## 数据与隐私

打开文件会把内容复制到 App 私有目录，作为自动保存和最近文档的工作副本；通过「导出 / 另存为」写到用户选择的文件。图片只读取用户指定的本地位置或已授权的本地目录，不上传。无账号、无云端初始化、无广告。

## 已知边界

- 不执行 HTML 中的脚本、样式或 iframe；这是有意的本地预览边界。
- 数学公式、复杂 HTML 布局、自定义 CSS 不渲染。
- Android SAF 单文件授权不保证同级文件读取；相对图片在无法直接读取时，需要一次性的文件夹授权。
- 设备级 IME、TalkBack 和不同 ROM 的 SAF 行为仍建议真机验收。
