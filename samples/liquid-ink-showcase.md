# Liquid Ink 功能样张

> 这是一份用于验收 Markdown 渲染、编辑与液态玻璃排版的本地样张。
> 建议在手机上打开本文件，然后切换 **预览 / 实时 / 源码** 三种模式。

## 标题层级

### 三级标题

#### 四级标题

##### 五级标题

###### 六级标题

Setext 标题也一样支持：

二级标题
--------

## 行内语法

普通段落，包含 **粗体**、*斜体*、***粗斜体***、~~删除线~~、`inline code` 和一个 [Liquid Ink 示例链接](https://example.com)。

也可以使用 autolink：<https://developer.android.com/jetpack/compose>。

转义示例：\*这不是斜体\*，\`这不是代码\`。

## 列表

### 无序

- 一片薄荷色的光
- 一层半透明的玻璃
  - 嵌套项：柔和阴影
  - 嵌套项：折射边缘
- 一条安静的写作路径

### 有序

1. 打开本地 Markdown
2. 轻触段落定位源码
3. 停止输入，等待自动保存

### 任务

- [x] 实现 Markdown 块解析
- [x] 实现源码 / 实时 / 预览
- [ ] 给这份样张再加一段故事
- [ ] 在真机上验证 TalkBack 链接操作

> 注意：在预览模式直接点击任务左侧的方框，会修改源文件中的 `[ ]` / `[x]`。

## 引用与分割线

> 写作不是把字堆起来，
> 而是给思绪留出可以呼吸的缝隙。
>
> —— Liquid Ink

---

## 代码

```kotlin
@Composable
fun GlassGreeting(name: String) {
    val liquid = rememberLiquidMotion()
    Text(
        text = "你好，$name",
        modifier = Modifier
            .liquidGlass(corner = 28.dp, tint = liquid.ambient)
            .padding(20.dp),
    )
}
```

```json
{
  "name": "Liquid Ink",
  "visual": "liquid-glass",
  "minApi": 26,
  "encoding": "preserve-original"
}
```

```python
def word_count(text: str) -> int:
    """A deliberately simple sample for the highlighter."""
    return len(text.split())
```

## 表格

| 能力 | 手机竖屏 | 横屏 | 说明 |
| --- | :---: | :---: | --- |
| Markdown 渲染 | ✓ | ✓ | LazyColumn 块渲染 |
| 实时预览 | ✓ | ✓ | 竖屏上下 / 横屏左右 |
| 代码高亮 | ✓ | ✓ | 超过 48k 字符自动降级 |
| 相对图片 | 视提供程序而定 | 视提供程序而定 | SAF 单文档权限限制 |

## 图片

下面使用一张远程图片演示异步加载、圆角和错误态。没有网络时会显示带地址的玻璃错误卡片。

![柔光与玻璃](https://picsum.photos/seed/liquid-ink/1200/700 "Liquid glass sample")

## 脚注

Liquid Ink 的目标不是复刻桌面 IDE，而是把 Markdown 的可移植性与手机上的专注阅读结合起来。[^design]

更激进的内联富文本方案会带来 IME 与光标映射的复杂性，而「源码 + 实时渲染」在中文输入下更可预测。[^ime]

[^design]: 见 `docs/DESIGN.md` 中的液态玻璃层次、动效和性能降级规则。
[^ime]: 当前实现保留 Markdown 原文，不隐藏标记，避免组合输入时跳光标。

## 长段落

清晨的光落在桌面上，像一层没有重量的玻璃。你打开一个本地文档，文字立刻在预览里舒展开来；光标停在段落中间，列表在下一行自然延续，代码围栏自动找到它的终点。没有云端等待，没有多余的浮层，只有文字和它的呼吸。

当你停下来，自动保存悄悄把改动写回原文件；当你继续，撤销历史仍在前方等你。超过一百万字的长文会被交给后台解析，屏幕只组合你看得见的那几段。玻璃很轻，但它的边界足够清楚。
