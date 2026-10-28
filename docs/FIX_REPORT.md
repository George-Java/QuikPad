> **Legacy 1.x (Jetpack Compose) document.** The shipped app is now the 2.0 Dart/Flutter rewrite. See [`FLUTTER_REWRITE.md`](FLUTTER_REWRITE.md). Do not apply this Compose-era document to the Flutter app.

# 阅读体验与稳定性增强报告

本轮保持「无需登录、开箱即用、本地文件优先」不变；没有引入账号、云端依赖或额外初始化流程。新增设置继续使用 SharedPreferences 本地持久化。

---

## 1. 目录大纲与章节定位

### 实现

- 纯 Kotlin 大纲构建器：[`DocumentOutline.kt`](../app/src/main/java/com/yg/liquidink/markdown/DocumentOutline.kt)
  - 遍历带源码区间和层级的 `MdBlock.Heading`；
  - 支持 H1–H3；
  - 将行内粗体、链接、行内代码等扁平化为可读标题；
  - 每个条目保存真实的 `blockIndex`。
- 目录抽屉：[`DocumentOutlinePanel.kt`](../app/src/main/java/com/yg/liquidink/ui/editor/DocumentOutlinePanel.kt)
  - 右侧玻璃抽屉 + 可点击遮罩；
  - 标题按层级缩进；
  - 使用 `LazyColumn`，长文档大纲也不会一次性组合全部条目；
  - 48dp 触控目标、`Role.Button` 与跳转语义；
  - 目录展开/收起受动效开关与系统减少动态效果控制。
- 编辑器接线：[`EditorScreen.kt`](../app/src/main/java/com/yg/liquidink/ui/editor/EditorScreen.kt)
  - 顶部栏新增「目录大纲」按钮；
  - 点击条目后，如果当前是源码模式会切到预览模式，并生成带 nonce 的 `OutlineScrollRequest`；
  - 在实时/预览模式下，预览列表收到请求后调用 `animateScrollToItem`，动效关闭时改用立即 `scrollToItem`；
  - 目标映射通过 `displayIndexByBlock` 完成，长列表分块后依然准确。
- 预览：[`MarkdownPreview.kt`](../app/src/main/java/com/yg/liquidink/ui/editor/MarkdownPreview.kt)
  - 保存 `LazyListState`；
  - 以标题对应的 Lazy 项索引定位，避免像素偏移估算；
  - 跳转完成后清除一次性请求。

### 验证

- `DocumentOutlineTest`：H1–H3 过滤、层级顺序、真实 block index、行内内容扁平化、空标题兜底。
- `OutlineIntegrationRobolectricTest`：在真实 `EditorScreen` 中打开目录、点击 H3 条目，断言目标标题显示在屏幕上。
- 误差边界：定位单位是标题所在 Lazy 项，不是行号或比例；因此跳转偏差不会超出该标题的段落范围。

---

## 2. 文档滚动闪退修复

### 触发条件与根因

原预览层存在多条不安全的同步测量/渲染链路，问题会在大文档滚动到特定块时暴露：

1. **引用块和表格使用 `height(IntrinsicSize.Min)` 嵌在 `LazyColumn` 的按需测量中。**
   滚动到这些块时，Compose 要反算子项 intrinsic size；嵌套 `SelectionContainer`/多子项内容容易产生不稳定或非法的测量约束。
2. **列表行把 `Modifier.weight(1f)` 放在 `SelectionContainer` 内部 Text 上。**
   权重的 ParentData 没有落在 `Row` 的直接子项上，导致宽高测量路径与预期不一致。
3. **表格宽度没有上界。**
   原实现按 `列数 × 148dp` 生成横向滚动内容；异常/超宽表可产生超出布局约束的尺寸。
4. **超长列表被当成一个 LazyColumn item。**
   500 行的单一列表会一次性组合/测量全部行，在低端设备上可表现为长时间无响应或被系统终止。
5. **解析任务没有异常兜底。**
   文档结构触发解析器异常时，异常会从 ViewModel 协程逸出，导致进程闪退。
6. **液体背景的动画状态在父组合中读取。**
   每一帧都可能推动大范围重组，在滚动时叠加 CPU 压力。

### 修改

- 引用块改成一个盒子和 `drawBehind` 绘制的渐变左栏，彻底移除 intrinsic 测量。
- 表格移除 intrinsic 行高与单元格 `fillMaxHeight()`；同时将每张表的渲染列数限制为 32 列，防止失控宽度。
- 修正列表/脚注行权重位置：权重放在 `SelectionContainer` 的直接 Row 子项上。
- 新增 [`PreviewDisplayItems.kt`](../app/src/main/java/com/yg/liquidink/ui/editor/PreviewDisplayItems.kt)：
  - 超过 48 项的列表被切分为多个有界 Lazy 项；
  - 保持 block index 映射，不影响目录定位。
- 解析协程增加 `CancellationException` 透传与普通异常兜底，失败时显示提示并保留旧的可读内容。
- 将背景动画隔离到独立的 `AnimatedLiquidBackdrop`，主组合与预览内容不再随每个动画帧重组。
- 图片解码尺寸限制为 2048×2048，并按动效设置启用/禁用 crossfade，降低大图滚动时的内存风险。
- 加固行内链接点击越界保护。

### 验证

- `PreviewScrollRobolectricTest`：
  - 生成 **>500 行** Markdown；
  - 包含多级标题、段落、引用、任务/无序列表、代码块、表格、链接；
  - 先执行一次目录目标定位，再分段滚动至文末；
  - 断言目标标题与文末段落均可见；
  - 通过，无异常、无白屏。
- 1 MB 解析冒烟测试继续通过。
- 长列表分块与 block index 稳定性由 `PreviewDisplayItemsTest` 覆盖。
- 本机没有可用的硬件加速 Android 模拟器，因此自动化使用 Robolectric API 34 原生布局测试；设备级 IME/滚动验收清单保留在 `docs/DESIGN.md`。

---

## 3. 阅读背景与液态玻璃设置

### 阅读背景

新增 `ReaderBackground`：

- `DEFAULT`：延续当前主题的透明玻璃阅读面；
- `PARCHMENT`：独立于全局深浅色的暖色纸面，使用固定深棕文字和低蓝光背景。

羊皮纸主题放在 [`Color.kt`](../app/src/main/java/com/yg/liquidink/ui/theme/Color.kt)：

- 纸面：`#F8EEDB → #EDDFC4`；
- 正文：`#32261A`；
- 次要文字：`#5A4936`；
- 链接：`#6E4429`。

即使应用处于深色模式，选择羊皮纸仍显示暖色阅读页。`ParchmentContrastTest` 校验正文对比度达到 WCAG AAA，次要文字/链接达到 AA。

阅读页的本地 MaterialTheme 在 [`EditorScreen.kt`](../app/src/main/java/com/yg/liquidink/ui/editor/EditorScreen.kt) 的 `PreviewPane` 中切换。默认模式使用玻璃面板，羊皮纸模式使用带边框、轻微纸张阴影的纸面。

### 液态玻璃开关与强度

`AppSettings` 新增：

```kotlin
glassEffectsEnabled: Boolean
glassIntensity: GlassIntensity // LOW / MEDIUM / HIGH
readerBackground: ReaderBackground
animationsEnabled: Boolean
```

- 全局主题入口 [`LiquidInkTheme.kt`](../app/src/main/java/com/yg/liquidink/ui/theme/LiquidInkTheme.kt) 计算 `LiquidGlassConfig`。
- 关闭玻璃时：
  - `GlassSurface` 直接走不透明平面面板分支；
  - 无高光、无阴影、无折射；
  - `LiquidBackground` 改为静态平面渐变。
- 强度控制：
  - 低：更不透明、移除重阴影与高光；
  - 中：默认；
  - 高：更通透，更高高光/阴影权重。
- 所有改动由 `StateFlow` 即时驱动，设置页中的实时效果预览会立刻更新；返回文档后立即采用。
- 设置页新增「阅读外观」区，包含背景主题、液态玻璃开关、强度、界面动效，并提供即时玻璃效果预览。

### 持久化

[`SettingsStore.kt`](../app/src/main/java/com/yg/liquidink/data/SettingsStore.kt) 写入 SharedPreferences：

- `reader_background`
- `glass_effects`
- `glass_intensity`
- `animations`

`SettingsPersistenceTest` 重新创建 Store 后断言四个值均保留。

---

## 4. 动效增强与减少动态效果

- 页面切换：Home ↔ Editor 淡入/缩放；动效关闭时使用零时长切换。
- 按钮：已有按压 spring 缩放反馈。
- 目录：展开/收起淡入+缩放；分段控件颜色切换；设置项的展开/收起。
- 图片 crossfade 跟随动效开关。
- 动画开关：设置页提供「界面动效」，持久化保存。
- 系统级：读取并监听 `Settings.Global.ANIMATOR_DURATION_SCALE`。系统开启「减少动态效果 / 移除动画」时，即使应用开关为开也会关闭动画。
- 性能模式为「省电」或自动降级为低内存模式时，也会冻结背景与高开销动效。

`GlassConfigTest` 校验动画禁用、玻璃关闭、强度与高对比度的 token 行为。

---

## 5. 验证命令与结果

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

- 全量测试：98 个，全部通过；本轮新增：
  - 4 个目录模型测试；
  - 3 个长列表渲染分块测试；
  - 1 个真实目录点击→滚动集成测试；
  - 2 个设置页/目录组合 UI 测试；
  - 1 个 500+ 行滚动回归；
  - 1 个设置持久化测试；
  - 5 个玻璃 token 测试；
  - 3 个羊皮纸对比度测试。
- Lint：0 errors。
- Debug / Release 构建通过，release APK 重新签名。
- 详细手动验收步骤见下方。

### 手动验收步骤

1. 打开含 H1/H2/H3、表格、引用、代码块、>500 行的 Markdown。
2. 快速连续滑动到文末，再回滑到开头；观察是否出现白屏/退出。
3. 点击顶部栏「目录大纲」，选择靠后的 H3；观察预览平滑定位，标题位于可视区域。
4. 打开「阅读外观」：切换默认/羊皮纸；确认深色模式下羊皮纸仍为暖色纸面。
5. 切换液态玻璃开关与低/中/高强度；观察设置页预览和返回后的面板变化。
6. 关闭「界面动效」，再开启系统「移除动画」；确认页面切换、目录和分段控件不再播放动画。
7. 杀掉应用重新打开，确认四项阅读外观设置保留。

---

## 6. 涉及文件

| 文件 | 作用 |
| --- | --- |
| `markdown/DocumentOutline.kt` | H1–H3 大纲与行内文本扁平化 |
| `ui/editor/DocumentOutlinePanel.kt` | 目录抽屉、点击与动效 |
| `ui/editor/MarkdownPreview.kt` | LazyListState、目标定位、测量稳定性 |
| `ui/editor/PreviewDisplayItems.kt` | 长列表分块与 block index 映射 |
| `ui/editor/EditorScreen.kt` | 阅读页背景、目录/外观入口、动效开关 |
| `ui/settings/SettingsScreen.kt` | 阅读外观与强度设置界面 |
| `ui/theme/LiquidInkTheme.kt` | 玻璃 token、强度、动效 |
| `ui/components/LiquidGlass.kt` | 开关/强度渲染与背景动画隔离 |
| `core/AppSettings.kt` | 新设置模型 |
| `data/SettingsStore.kt` | SharedPreferences 持久化 |
| `ui/MainViewModel.kt` | 新设置 setter、解析异常兜底 |
| `ui/LiquidInkApp.kt` | 系统减少动效监听、设置接线 |
