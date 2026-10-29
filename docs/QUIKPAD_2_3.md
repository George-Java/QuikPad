# QuikPad 2.3 修复与能力升级

版本：`2.3.0 (versionCode 5)`
包名：`com.yg.liquidink`

## 1. 深色模式玻璃边缘竖线

### 现象

深色主题主界面的玻璃卡片右侧出现一条黑/绿相间、上下不连续的细线；左侧部分呈绿色，右侧部分呈黑色。浅色主题无此现象。

### 根因

1. `_GlassHighlights` 用两条 1.5px 的**斜向渐变描边**（`BlendMode.screen` / `overlay`）勾整圈边缘。渐变在部分高度是透明的，透明段露出黑色阴影，形成不连续的绿色/黑色碎片。
2. 面板外围阴影为 40px 四周扩散，深色背景下把边缘反差放大。
3. `RefractiveSurface` 的指针追踪没有检查 `interactive`。非交互的 Hero/卡片在滑动按压时也会做弹性缩放/位移，裁剪边缘与滤镜接缝因此移动。

### 修复

- 删除整圈斜向渐变描边，增加一层在所有滤镜之上的**完整 1px 中性描边**，覆盖 `ClipRRect` 和 shader filter 的抗锯齿接缝。
- 只保留顶部一条水平高光；竖向边缘不再画任何渐变。
- 阴影改为更小、向内收（负 spread），避免卡片左右两侧出现黑边。
- `_track` 只对 `interactive == true` 的玻璃控件生效；静态卡片不再随滑动形变。
- 普通按钮按压反馈仍保留。

### 验证

- `test/dark_edge_test.dart` 在 DPR 1.0 / 2.75 / 3.0 下生成截图并检查右侧裁剪外没有彩色跳变。
- `test/glass_effect_test.dart` 新增静态面板拖拽测试，确认 `AnimatedContainer` 的缩放和位移保持单位矩阵。

## 2. 相对路径图片

### 行为

对于：

```markdown
![test](assets/images/test.png)
```

显示时会按以下顺序查找：

1. 文档工作副本目录：`dirname(document)/assets/images/test.png`；
2. 记录到的原始文档目录（如果平台提供真实路径）；
3. Android 上从原始文档 URI 推导的同级文档 URI；
4. 已关联的 SAF tree（用户选择 Markdown 所在目录后持久授权）。

行内图片、独立图片、HTML `<img>` 统一走 `MarkdownImage`；`DocumentImageScope` 让段落里的行内图片也拿到当前文档路径。

### Android SAF 说明

`ACTION_OPEN_DOCUMENT` 的单文件授权只保证当前文件，不保证同级图片。直接同级读取被系统拒绝时，图片错误卡片或文档菜单提供一次性的「关联图片目录 / 选择 Markdown 所在文件夹」；授权持久化后，后续相对图片直接读取。应用不上传图片，也不申请全盘存储权限。

### 验证

- `test/image_resolution_test.dart` 创建临时 Markdown 和 `assets/images/test.png`，验证同目录解析命中；同时验证行内图片从 `DocumentImageScope` 继承 base path。
- `large_document_test.dart` 验证图片解码被限制在 1536×1024 以内。

## 3. Markdown/HTML 语法

解析器从手写子集切换为 `markdown`（CommonMark + GFM）生成 HTML，再由 `html` DOM 转换为惰性原生块：

- ATX/Setext 标题，H1–H6；H1–H3 进入目录。
- 粗体、斜体、嵌套强调、删除线、行内代码、多反引号代码。
- 链接、引用链接、自动链接、引用图片、行内图片、HTML `<img>`。
- 有序/无序/嵌套/任务列表；缩进代码；围栏代码。
- 表格（含对齐）、引用、脚注定义、分割线。
- 原始 HTML：
  - `<h1>`–`<h6>` 转为标题块；
  - `<a href>`、`<a name>`/`id`；
  - `<p>`、`<strong>`、`<em>`、`<code>`、`<pre>`；
  - `<blockquote>`、`<ul>`/`<ol>`、`<table>`、`<br>`、`<hr>`；
  - 行内 `<u>`、`<ins>`、`<mark>`、`<sub>`、`<sup>`。
- 文档内锚点：`[跳转](#id)` 与 `<a href="#id">` 会调用预览的块索引滚动；未知锚点给出提示。
- `<script>`、`<style>`、`<iframe>` 等不会执行或显示。

### 验证

- `test/markdown_support_test.dart`：
  - HTML h2 + 内联 `<a>` + `<a name>`；
  - `<a>`、`<strong>`、`<br>`、`<img>`；
  - GFM 自动链接、`<mark>`、实体；
  - 引用链接、嵌套强调、多反引号代码；
  - 嵌套列表、缩进代码、引用中的围栏；
  - 脚注定义；
  - `<script>` 被忽略。
- `test/markdown_parser_test.dart` 的随机畸形输入与锚点/标题索引覆盖。

## 4. 代码高亮

`CodeHighlighter` 使用 `highlight` 的 `allLanguages`，并保留 32KB 窗口、缓存上限和多行 token 状态。代码块按 2KB 分片渲染，分片仍从完整代码窗口取 token，因此滚动大代码块不会一次性布局整段。

语言别名（部分）：

```
c/c++/cc/cxx -> cpp        py -> python       js/jsx -> javascript
ts/tsx -> typescript        html/htm/svg -> xml
sh/shell/zsh/fish -> bash   yml -> yaml        c#/csharp -> cs
objc/objective-c -> objectivec                  toml -> ini
```

支持并测试了 C、C++、Python、Java、PHP、JavaScript、HTML、CSS、XML、YAML、Shell、TypeScript、JSON、SQL、Rust、Go、Kotlin、Swift、C#、Dart、TOML、Dockerfile、Makefile、Objective-C、Protobuf、GraphQL、Vue、Diff、INI 等；`highlight` 自带的其余 180+ 语言也可用。

`CodeText` 的颜色映射补充了 `tag`、`attr`、`property`、`key`、`params`、`operator`、`symbol`、`punctuation` 等 class，HTML 标签/属性、YAML 键值、Shell 操作符都有颜色。

### 验证

- `test/code_highlighter_test.dart`：
  - 20 种常用语言的 token 能重新拼回原文；
  - 多行注释跨分片保留 token class；
  - 未知语言、自动检测、超大代码块无损；
  - 别名和友好显示名；
  - TOML、Dockerfile、Makefile、Objective-C、Protobuf、GraphQL、Vue、Diff、INI。

## 5. 验证与构建

```bash
cd flutter_app
..\.tooling\flutter-sdk\flutter\bin\flutter.bat analyze
..\.tooling\flutter-sdk\flutter\bin\flutter.bat test
..\.tooling\flutter-sdk\flutter\bin\flutter.bat build apk --release
```

- analyze：0 issues
- test：69 passed，1 skipped（宿主不暴露 Impeller shader image filter）
- build：release APK，Android Debug 证书签名；已通过 USB 安装到 PLQ110（Android 17）

> 维护修复：查找/重命名/新建对话框改为由对话框 State 持有控制器；图片解码改用
> `ResizeImagePolicy.fit` 保持宽高比；任务勾选行号支持引用块、长代码围栏前后及
> HTML 复选框隔离，并校验实体/转义后的任务文本；保存改为临时文件原子替换并串行化
> 并发保存；相同名称重命名不再产生副本；玻璃菜单补透明 Material 层并限制高度；
> 首页按钮适配 2×–3× 字体；Android 关闭 Auto Backup；大文档字数统计改为防抖 +
> isolate。OPPO/OnePlus 会把“无建议”输入框当成密码并拉起全屏黑色安全键盘，
> 新建/查找/重命名/源码编辑统一改用普通 IME（保留关闭智能引号）。编辑器底部不再
> 放搜索、设置、全屏按钮：查找和专注模式移入右上角“更多”；专注模式隐藏上下栏，
> 2 秒内连续两次返回才退出，第一次只提示，防止误触。对应测试新增
> `document_store_test.dart`、`editor_actions_test.dart` 及大字号/任务行号/图片比例/
> 并发保存/普通 IME 标记/专注模式两次返回断言。
>
> 真机验收（PLQ110）：ZMR 0.2.18 三个场景 passed；新建/查找/重命名键盘、源码输入、
> `> - [ ]` 引用任务勾选、关联目录后 300×1200 竖图按 1:4 显示、1.5× 字体首页、
> 删除文档均通过。

产物：GitHub Releases `v2.3.0` 资产 `QuikPad-2.3.0-fixed.apk`
（https://github.com/George-Java/QuikPad/releases/download/v2.3.0/QuikPad-2.3.0-fixed.apk）
大小：61,621,029 bytes（约 58.8 MiB）
SHA-256：`3194463BBB96BC3BEF1D660D0AED934A62174E8B306063FA7A3739136338A4B2`
（修复前的 `dist/QuikPad-2.3.0.apk` 保留用于对比；fixed 资产不进入 Git 树）

## 6. 边界

- 不执行 HTML 脚本/样式/iframe；不做任意 CSS 布局。
- 数学公式、admonition、目录中的 H4–H6 不进入大纲（正文仍渲染）。
- Android SAF 同级读取取决于 provider 是否允许；不允许时需要一次性文件夹授权。
- 真机 IME、TalkBack 与厂商 ROM 的 SAF 行为仍建议安装后验收。
