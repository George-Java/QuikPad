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

## 本轮稳定性修复

- **原生编辑对话框**：查找、重命名、新建共用 `showTextPromptDialog`，由对话框自身持有并释放 `TextEditingController`。此前在 `showDialog` 返回后立刻 dispose，而退出动画中的 `TextField` 仍持有控制器，会触发 “A TextEditingController was used after being disposed”。
- **图片比例**：解码改用 `ResizeImagePolicy.fit`，宽高上限只做边界，不再把竖向/方形图片压进屏幕宽 × 260dp 的固定比例；所有 Markdown 图片统一 `BoxFit.contain`，不裁切。
- **任务勾选行号**：任务定位支持引用块（`> - [ ]`）、嵌套列表，以及跳过围栏代码中的假任务行；定位时同时校验任务文本（含实体/转义），避免 HTML 复选框或相邻任务劫持行号；无法确认时放弃修改而不是改错行。
- **保存一致性**：先写 `.saving` 临时文件再重命名覆盖，进程在写入中被杀不会留下截断的唯一副本；同名重命名成为无操作，不再生成 `-2` 副本；编辑器对并发/排队的保存串行化，并在写入过程中文本被改回时补写磁盘状态。
- **对话框/菜单水波纹**：玻璃面板内部的 `ListTile`、目录行和分段控件补上透明 `Material` 层，修复 Flutter 的 “ink splashes may be invisible” 断言并恢复点击反馈；文档菜单可纵向滚动。
- **无障碍大字号**：首页英雄区按钮在文字放大或窄屏时改为纵向排列，按钮标签可省略；320dp 宽度 / 2×–3× 字体下无 RenderFlex 溢出。
- **移动端输入**：源码编辑器和文件名/查找输入框关闭 iOS 智能引号与破折号替换，避免把 Markdown 引号改成弯引号；同时保留系统输入法的建议/纠错标记。OPPO/OnePlus 会把“无建议”文本框识别成安全密码输入并拉起全屏黑色安全键盘，真机验收时新建、查找、重命名和源码编辑均因它整屏变黑；现在使用普通 IME，键盘与编辑器正常显示。
- **编辑器工具栏**：查找、专注模式移入右上角“更多”菜单；查看文档时不再显示搜索、设置、全屏按钮，底部只保留模式切换和字数统计。专注模式隐藏上下工具栏，连续两次返回才退出（2 秒窗口）；第一次返回只提示“再按一次”，避免误触直接退出。
- **弹层高度**：底部玻璃弹层最高占屏幕 86% 并内部滚动，文档菜单的“删除本地副本”不再被屏幕底边裁掉。
- **启动与编辑保护**：外链打开、剪贴板复制、异步重命名/统计增加异常与 `mounted` 保护；字数统计对大文档改为防抖 + isolate 计算，避免每次按键同步扫描整篇文本。
- **本地隐私**：Android 关闭 Auto Backup，防止系统云备份把“只在本机”的文档副本上传。

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
- `flutter test`：**69 passed，1 skipped**
  - 1 个 skipped 是宿主不暴露 Impeller shader image filter，不是应用失败。
  - 覆盖 1200+ 块目录跳转、900 项列表、长代码、长段落、长表格、源码分段编辑、HTML 头/链接/锚点、图片相对路径与宽高比、任务勾选行号（引用/代码围栏/嵌套任务）、原生编辑器对话框生命周期、原子保存与并发保存、专注模式两次返回、20+ 代码语言、暗色玻璃边缘与非交互卡片稳定性。
- Release APK：[QuikPad-2.3.0-fixed.apk](https://github.com/George-Java/QuikPad/releases/download/v2.3.0/QuikPad-2.3.0-fixed.apk)（GitHub Releases，真机验收构建）
- 包名：`com.yg.liquidink`
- 版本：`2.3.0 (5)`
- 大小：61,621,029 bytes（约 58.8 MiB）
- SHA-256：`3194463BBB96BC3BEF1D660D0AED934A62174E8B306063FA7A3739136338A4B2`

## 真机验收

- 设备：`PLQ110`（Android 17，1272×2800，USB 连接）。
- 安装：从 Releases 下载 `QuikPad-2.3.0-fixed.apk`，再执行 `adb install -r QuikPad-2.3.0-fixed.apk`。
- Zeno Mobile Runner 0.2.18（从源码构建的 Windows `zmr.exe`）：3 个场景在物理设备上 `passed`，含 launch、healthy、snapshot、点按和返回；trace 位于 `flutter_app/traces/zmr-phone*`。
- 手工验收：新建/查找/重命名窗口与键盘正常；源码编辑可输入；`> - [ ]` 引用任务只改自己的 `[x]`（`task-test.md` 验证）；关联 Markdown 目录后 300×1200 竖图按 1:4 显示；1.5× 系统字体首页无溢出；查找与专注模式从右上角菜单进入，专注模式第一次返回只提示、第二次返回退出；删除文档返回首页。
- 注意：`dist/QuikPad-2.3.0.apk` 是修复前构建，保留用于对比；`QuikPad-2.3.0-fixed.apk` 作为 Releases 资产，不进入 Git；`traces/` 含手机截图，已在 `flutter_app/.gitignore` 中忽略。

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
