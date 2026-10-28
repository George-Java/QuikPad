> **Legacy 1.x (Jetpack Compose) document.** The shipped app is now the 2.0 Dart/Flutter rewrite. See [`FLUTTER_REWRITE.md`](FLUTTER_REWRITE.md). Do not apply this Compose-era document to the Flutter app.

# 架构说明

## 1. 目标与约束

Liquid Ink 是一个只在 Android 手机上运行的 Markdown 查看/编辑器。约束优先级：

1. **文件不丢**：编码、BOM、未保存改动和权限异常必须有明确处理。
2. **输入不卡**：文件读取、Markdown 解析、字数统计、图片加载与语法高亮都不能阻塞输入/滚动。
3. **大文件可读**：1 MB 以上文档默认进入预览路径，以 `LazyColumn` 按块组合。
4. **视觉可降级**：液态玻璃不是不可关闭的装饰；低端设备必须能回到稳定帧率。
5. **纯原生**：UI 只使用 Kotlin + Compose；没有 WebView，也不要求额外存储权限。

---

## 2. 分层

```
UI (Compose)
  ├─ Home / Settings / Editor chrome
  ├─ MarkdownPreview (AST -> Compose)
  └─ SourceEditor (TextFieldValue + VisualTransformation)
          │ callbacks / StateFlow
          ▼
Application state (MainViewModel)
  ├─ document lifecycle + dirty/save state
  ├─ undo history
  ├─ auto-save job
  └─ parse scheduling
          │
          ├───────────────┬─────────────────┐
          ▼               ▼                 ▼
Markdown engine      File boundary      Persistence
pure Kotlin AST      DocumentRepository  Settings / Recent
                     SAF + TextCodec
```

### 为什么 `markdown` 是纯 Kotlin

`MarkdownParser`、`InlineParser`、`SyntaxHighlighter`、`MarkdownEditing` 不引用 Android 或 Compose（`MarkdownEditing` 只引用 Compose 的 `TextFieldValue` 值类型），因此它们可以在普通 JVM 单元测试里覆盖。UI 层只消费不可变 AST。

### 为什么 UI 不直接读写文件

`MainViewModel` 持有 `DocumentRepository`，文件操作以 `suspend` 函数在 `Dispatchers.IO` 执行。UI 中所有 picker 返回的只是 `Uri`，交给 ViewModel。这保证：

- 旋转屏幕/重组不会重复写文件；
- 文件的 dirty/save 状态只有一份；
- 自动保存和手动保存可以通过同一把 `Mutex` 串行化。

---

### 2.1 顶层目的地与设置隔离

根级路由由 `AppSurface` 明确表达：

```
SETTINGS > EDITOR > HOME
```

- `resolveAppSurface(settingsOpen, hasEditor)` 确保 **设置优先**；
- 设置打开时，Home/Editor 不再组合，只渲染全屏 `SettingsScreen`；
- 主界面的 `rememberSaveable` UI 状态通过 `SaveableStateHolder` 暂存，返回时恢复；
- 设置页不复用 ModalBottomSheet，不再依赖 scrim + 半透明 sheet 遮挡主内容；
- 设置页使用 `readableSettingsGlass()` 提升面板和边框最低不透明度，保证文字可读。

这样既消除了“主界面与设置页重叠渲染”的结构性原因，也避免仅靠调高 scrim 透明度来掩盖布局问题。

### 2.2 阅读外观、玻璃与动效配置

阅读外观与全局主题解耦，配置模型为：

```kotlin
AppSettings(
    readerBackground = ReaderBackground.DEFAULT | PARCHMENT,
    glassEffectsEnabled = Boolean,
    glassIntensity = LOW | MEDIUM | HIGH,
    animationsEnabled = Boolean,
)
```

- `SettingsStore` 以独立的 SharedPreferences key 持久化四项设置。
- `LiquidInkTheme` 将玻璃开关/强度、性能模式、高对比度和动效开关归并为不可变的 `LiquidGlassConfig`，通过 `LocalLiquidGlass` 下发给所有自绘玻璃面板。
- `LiquidInkApp` 额外监听系统的 `ANIMATOR_DURATION_SCALE`；系统“减少动态效果”开启时，动效 Token 为 false，与用户开关取与。
- 阅读页的 `PARCHMENT` 使用固定暖色 `ColorScheme`，不读取应用当前深/浅色；这也保证深色模式下仍有独立纸面。
- 背景主题、玻璃开关、强度与动效均在设置页即时预览并即时作用于下一帧，无需重启。

## 3. 文档打开流程

```
Intent/OpenDocument Uri
        │
        ▼
VM: set loading EditorUiState (non-blocking UI)
        │
        ▼ IO
persist permission -> read bytes (<= 32 MB) -> detect BOM/charset -> decode
        │
        ▼ Default
TextStatistics.compute
        │
        ▼ main
create EditorUiState(text, stats, encoding)
        │
        ├─ recentStore.markOpened
        └─ scheduleParse(text, delay=0)
                 │ Default
                 └─ parser.parse -> state.copy(parsed=…)
```

`openGeneration: AtomicLong` 在每次打开/关闭时递增。任何迟到的读取/解析结果如果世代号不匹配就直接丢弃，避免快速点击两个文档时旧内容覆盖新文档。

打开文件失败时回到 Home；如果该 URI 已在最近列表里，会移除失效记录，并通过 `UiMessage` 提示。

---

## 4. Markdown AST

### 4.1 块模型

`MdBlock` 是 sealed interface，目前包括：

- `Heading(level, inlines)`
- `Paragraph(inlines)`
- `Quote(lines: List<List<MdInline>>)`
- `ListBlock(ordered, startNumber, items)`
- `CodeBlock(language, code, closed)`
- `Table(headers, alignments, rows)`
- `Image(alt, destination, title)`
- `ThematicBreak`

每个 block 带 **源文本绝对区间** `start..end`（half-open）。这是移动端体验的关键：

- 点按预览块 → 把光标定位到对应源码；
- 任务复选框 → 只替换 `[ ]` 的 3 个字符，不需要重新序列化整棵 AST；
- 脚注/截断引用可追溯源位置。

### 4.2 行扫描

`MarkdownParser.splitLines` 一次生成 `(text, start, end)`。之后的识别顺序是：

1. 空白行；
2. 围栏代码（优先于其它 block，避免代码内 Markdown 被误解析）；
3. ATX 标题；
4. 脚注定义；
5. 引用；
6. 列表；
7. 表格；
8. 分割线；
9. 独立图片；
10. 段落（同时向后寻找 Setext 下划线）。

围栏代码、任务标记、列表缩进等位置都使用绝对偏移。解析器不修改源字符串。

### 4.3 行内解析

`InlineParser` 是一次递归下降扫描：

- 反斜杠转义；
- 反引号代码段（等长反引号闭合）；
- `![alt](url "title")` 图片；
- `[^label]` 脚注引用；
- `[label](url "title")` 链接；
- `<https://…>` / `<mailto:…>` autolink；
- `~~strike~~`、`***b+i***`、`**bold**`、`*italic*` / `_italic_`；
- 未识别语法原样作为文本输出。

强调解析使用 flanking 规则的一个保守子集，避免把 URL/普通乘号大量误判为 Markdown。

### 4.4 表格

`splitTableRow` 跳过转义管道与行内代码中的管道；表头与分隔行单元格数量必须一致，分隔行的 `:---` / `---:` 决定对齐。渲染使用 148dp 固定列宽的横向滚动，避免手机上自动换行导致表格无法比较；异常宽表最多渲染 32 列。

### 4.5 代码高亮

`SyntaxHighlighter`：

1. 先用一个合并的正则消费注释与字符串（避免字符串中的 `//` 被当作注释）；
2. 再按语言补充关键字、数字、函数、标签、属性、变量；
3. 用 `BooleanArray` 防止 token 重叠；
4. 超过 48k 字符时直接返回空 token 列表。

高亮不在解析阶段完成，而是在可见代码块内用 `LaunchedEffect(Dispatchers.Default)` 计算，避免长文档中的所有代码块一起占用 CPU。

### 4.6 目录与长列表显示项

目录不保存像素位置，而是复用 AST 的源码区间：

- `buildOutline(parsed.blocks)` 过滤 H1–H3，生成 `OutlineEntry(blockIndex, level, title)`；
- 标题中的粗体/链接/代码等行内节点扁平化为纯文本，空标题有兜底；
- 预览的 LazyColumn 不直接把 `parsed.blocks` 当成 item，而是先经过 `buildPreviewDisplayItems`：
  - 普通块一一对应；
  - 超过 48 项的列表按 48 项分块，避免一个 item 一次性测量数百行；
  - `displayIndexByBlock` 保存每个原始 block 对应的第一个 Lazy item 下标。
- 目录点击产生一次性 `OutlineScrollRequest(blockIndex, nonce)`；`MarkdownPreview` 用映射找到真正 item 下标，动效开启时 `animateScrollToItem`，关闭时 `scrollToItem`，完成后清除请求。

这样即使长列表被分块、文档被重新解析，目录定位也不会漂移到别的段落。

### 4.7 预览测量安全约束

预览层的稳定运行依赖以下约束（`PreviewScrollRobolectricTest`、`PreviewDisplayItemsTest` 与目录集成测试保护）：

1. 引用与表格不得在 LazyColumn item 内使用 `IntrinsicSize.Min`；引用左栏由 `drawBehind` 绘制，表格行使用自然高度。
2. `Modifier.weight` 只放在 `Row` 的直接子项（`SelectionContainer`）上，不能放在其内部 Text 上。
3. 表格横向宽度设上限，最多渲染 32 列，避免异常文档产生不可表示的约束。
4. 超长列表分块；图片请求限制解码尺寸。
5. 解析异常被 ViewModel 捕获，取消异常继续向上传递；初始解析失败时显示提示并保留可用状态。

---

## 5. 编辑管线

### 5.1 状态

`EditorUiState.value` 是 Compose 的 `TextFieldValue`，同时保存：

- `text`
- `selection`
- `composition`（IME 组合状态）

`onEditorValueChange` 分三种情况：

1. 仅选择/组合状态变化：只替换 value，不进入历史与保存；
2. 普通输入：应用自动续写规则，记录历史，dirty，调度解析与自动保存；
3. 结构化变更：换行、粘贴、工具栏格式命令会关闭当前撤销组。

### 5.2 快捷输入

`MarkdownEditing.applyAutoFormatting`：

- 在列表项按 Enter：延续 `-` / `*` / `+`、递增 `1.`、延续任务状态为新未完成；
- 空列表项按 Enter：移除空项标记，结束列表；
- 引用延续：`> `；
- 开围栏后按 Enter：自动补闭合围栏，光标放在内容行；
- 在围栏内部：不触发列表/引用续写；
- 粘贴或一次插入多个字符：不自动格式化，保持用户数据原样。

### 5.3 撤销/重做

`UndoRedoManager`：

- 900 ms 内的连续输入合成一个 undo group；
- 结构编辑、格式命令强制断开；
- 项数上限 80；字符总量上限约 2400 万；
- undo/redo 保留选择区间；
- 新编辑清空 redo。

### 5.4 查找/替换

- `findLiteralMatches` 是字面量、非重叠搜索，大小写可选，最多 2,000 个结果；
- 搜索在 `Dispatchers.Default` 上执行，UI 侧只接收区间与截断标记；
- 高亮通过 `MarkdownVisualTransformation` 叠加；
- 替换单个区间会走一次完整的历史/解析/自动保存管线；
- 替换全部在后台构造新文本，回到主线程前再次核对源文本是否变化，避免覆盖用户并发输入。

---

## 6. 保存一致性

### 6.1 串行化

所有可能改变磁盘内容的操作（保存、另存为、重命名、删除）共享 `saveMutex`。自动保存任务只负责等待和触发，不取消正在写入的 IO 协程。

保存流程：

```
autoSaveJob?.cancel()
saveMutex.withLock {
  snapshot = state
  if (!snapshot.dirty) return true
  state = state.copy(isSaving = true)
  repo.write(snapshot.uri, snapshot.text, snapshot.encoding)  // IO
  savedText = snapshot.text
  state = latest.copy(
    isSaving = false,
    isDirty = latest.text != snapshot.text,
    lastSavedAt = now
  )
}
```

如果用户在写入期间继续输入，`latest.text != snapshot.text`，dirty 会继续保持；不会出现「UI 显示已保存但最新输入未落盘」的情况。

### 6.2 自动保存

- 停止输入后延迟到用户配置的间隔再保存；每次新输入会重置计时。
- 设置改变时重新安排；关闭自动保存会取消等待中的计时。
- `MainActivity.onStop()` 在开启自动保存时再补一次静默保存，覆盖切后台/锁屏。
- 自动保存不弹成功提示，避免打扰；失败始终通过错误 Snackbar 提示。

### 6.3 离开保护

`EditorScreen` 自己处理系统返回：

- find panel 打开时先关闭；
- 专注模式时先退出专注；
- dirty 时弹出三选一对话框：保存并离开 / 直接离开 / 取消；
- 保存失败时 Editor 保持打开，让用户可以重试或另存为。

---

## 7. 文件与编码

`DocumentRepository` 使用 SAF 的 `content://` URI：

- `openInputStream` 分块读取，超过 32 MB 抛 `DocumentException`；
- 元数据从 `OpenableColumns` 读取，缺失时用 URI 尾段作为显示名；
- 写入优先 `openOutputStream(uri, "wt")`，再退到 `"w"`；
- 删除/重命名使用 `DocumentsContract`，不支持时给出明确消息；
- `persistPermission` 尝试读写持久授权，不支持持久授权的 provider 当前会话仍可读写。

`TextCodec`：

- 检测并剥离 UTF-8 / UTF-16LE / UTF-16BE BOM；
- 无 BOM 时，严格 UTF-8 解码失败后检查 UTF-16 零字节分布，再尝试 GB18030 与 ISO-8859-1；
- 保存沿用 `TextEncoding.charsetName + bom` 重新编码；
- 新文档默认 UTF-8（无 BOM）。

> IO 写入在部分 DocumentsProvider 上不是原子替换；代码不宣称原子性，保存失败会保留 dirty 状态。

---

## 8. 性能与线程

| 工作 | Dispatcher | 证据 |
| --- | --- | --- |
| 读取/写入文件、权限、metadata | IO | `DocumentRepository` |
| Markdown parse | Default | `MainViewModel.scheduleParse` |
| 字数统计 | Default | 同 parse job |
| 代码 token 化 | Default | `MarkdownCodeBlock.LaunchedEffect` |
| 搜索/替换文本构造 | Default | `EditorScreen` / `replaceAll` |
| AST → Compose | Main | 视口内的少量块；长列表已分块 |
| 图片解码 | Coil 自身线程池 | `SubcomposeAsyncImage` |

Debounce：

- < 250k 字符：120 ms；
- 250k–1M：320 ms；
- ≥ 1M：650 ms。

超大文件保护：

- 默认进入 Preview；
- 源码语法变换超过 140k 字符关闭；
- 1 MB 文件输入依然可用，但不在 UI 线程做整篇正则；
- 初始解析期间显示加载态，不是“白屏无反馈”。

历史/保存以 String 引用传递，不做每键全量深拷贝；AST 是不可变数据类，Compose 只会重组依赖它的块。

---

## 9. 测试策略

测试分两层：

- 纯 JVM：解析器/行内解析/代码高亮/编辑命令/搜索/编码/历史、目录模型、长列表分块与玻璃 token。
- Robolectric API 34 原生布局：真实 `EditorScreen` 的目录点击→滚动、设置页阅读外观控件、设置持久化。
- 500+ 行 Markdown 回归（表格/引用/代码块/任务列表）分段滚动至文末，无白屏或异常。
- 1 MB 冒烟测试生成 1,048,576+ 字节的 Markdown，验证块数量与宽松耗时预算。
- UI/SAF/图片/IME 仍需要真机或模拟器手动验收（见 `docs/DESIGN.md` 的验收清单）。
- 当前全量测试：98。

---

## 10. 可扩展点

- 增加 Markdown 语法：在 `MdBlock`/`InlineNode` 与相应 parser 分支加入，渲染层按 sealed 分支穷举即可。
- 增加文件来源：在 `DocumentRepository` 之外实现不同文件源，保持 `OpenDocument` 抽象。
- 真 backdrop blur：在 `LiquidGlassConfig` 增加 haze token，用不透明降级路径兜底，仅在性能模式 FULL 时启用。
- 协同/云同步：当前 VM 单文档；若要云同步，需要把保存冲突变成显式状态而不是静默覆盖。
