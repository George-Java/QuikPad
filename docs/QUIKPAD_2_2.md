# QuikPad 2.2：品牌更新与液态玻璃移植

## 品牌与升级

应用显示名、首页、设置、诊断报告、新建欢迎文件均使用 **QuikPad**。
根目录 `logo.png` 是唯一图标来源，原图保持不变。已生成 Android 五档密度图标、API 26+ 自适应图标、API 33+ 单色主题图标和启动图，首页使用相同 Logo。

保留 `com.yg.liquidink` 安装标识、原签名配置、`liquid_ink_documents` 文档目录及 SharedPreferences 键。
因此可以从 2.1 直接覆盖升级，继续读取既有文档与偏好。内部 Dart 包名也维持 `liquid_ink`，与用户看到的产品名称独立。

重新生成图标及参考贴图：

```powershell
python flutter_app/tool/prepare_brand_assets.py
```

该脚本需要 Pillow；参考贴图从 `liquid-glass-react/src/utils.ts` 解码为 PNG，保留解码后的像素值。

## 参考实现与差异

阅读了 `liquid-glass-react/README.md`、`src/index.tsx`、`src/shader-utils.ts`、`src/utils.ts` 及 LICENSE。
原 Flutter 实现只有背景模糊、半透明渐变、静态亮边，没有参考库的光学位移与色散，因此需要修改。
继续使用 **Dart / Flutter**，没有替换技术栈，也没有引入 WebView 或 React。

| 环节 | 原实现 | 2.2 对照参考库的实现 |
| --- | --- | --- |
| 背景处理 | 普通 Gaussian blur | 先模糊、sRGB 饱和度增强，再进行位移与轻微柔化 |
| 折射 | 无 | 复用 standard 位移贴图，按 R/B 通道取二维偏移；组件另支持 polar/prominent 贴图 |
| RGB 色散 | 无 | 三次采样，系数对应 `-1`、`-1-aberration*0.05`、`-1-aberration*0.1` |
| 中心与边缘 | 统一静态渐变 | 沿用参考贴图本身的位移分布；当前源贴图不透明，参考 EDGE_MASK 的离散 alpha 为 1 |
| 边框 | 单层亮边 | screen / overlay 两层渐变描边，方向随指针偏移变化 |
| 动态反馈 | 常规 InkWell | 200px 激活范围、方向性缩放、弹性平移、按压缩放 0.96；触摸与鼠标均可触发 |
| 前景 | 不变 | 文本与按钮绘制在背景滤镜之后，保持清晰 |

中等强度沿用参考库外层默认值：displacement=70、blurAmount=0.0625、saturation=140%、aberration=2、elasticity=0.15。
浅背景使用参考 `overLight` 公式：位移减半，模糊为 `12 + blurAmount*32`；深背景为 `4 + blurAmount*32`。
低/高强度在这些参数基础上提供不同档位。

移植目标是参考库默认 **standard** 光学链；没有复刻 DOM/SVG API 或其标注为不稳定的实验 `shader` 模式。不同渲染后端的模糊核、颜色取整与混合结果存在差别，不宣称像素级一致。

## Flutter 实现与性能

- `shaders/liquid_glass.frag`：实际采样背景与源位移贴图，处理色散和饱和度，并处理 Impeller GLES 的 Y 轴方向。
- `lib/widgets/glass_effect.dart`：共享着色器程序、贴图、参考参数与变形公式；绘制时解析面板位置和尺寸。
- `lib/widgets/refractive_surface.dart`：圆角裁剪内执行 BackdropFilter，独立绘制清晰前景和双层边框。
- `GlassPanel`、玻璃按钮及图标按钮使用统一表面；嵌套控件共用父级背景效果，避免重复背景滤镜。
- shader 程序和三张 256×256 贴图全局加载一次，各表面独立管理 shader 实例。
- 关闭玻璃使用普通不透明面板；关闭动效或系统减少动态效果时停止弹性变化。
- 正文维持 2.1 的按需分片渲染，不给每个 Markdown 段落添加折射滤镜。

`ImageFilter.shader` 仅支持 Impeller。运行时检查 `isShaderFilterSupported`；Skia 或资源加载失败时使用模糊＋饱和度＋高光兼容路径，不强行启用不支持的 GPU 后端。这与参考库在不支持 SVG 位移的浏览器保留部分效果的策略相似。

## 验证

在 `flutter_app/` 下执行：

```powershell
..\.tooling\flutter-sdk\flutter\bin\flutter.bat analyze --no-pub
..\.tooling\flutter-sdk\flutter\bin\flutter.bat test --no-pub --enable-impeller
..\.tooling\flutter-sdk\flutter\bin\flutter.bat build apk --release --no-pub
```

静态分析零问题；**35 项测试通过**，包括原长文档测试及新增的真实片元 shader 输出、RGB 色散、Impeller 背景滤镜裁剪、嵌套滤镜数量、关闭玻璃、减少动态效果和尺寸变化。
设置 `QUIKPAD_VISUALS=1` 可将测试图写入 `.tooling/quikpad-*.png`。测试引擎默认使用占位字体，截图用于检查滤镜与布局，不代表设备字形。
当前没有连接的 Android 手机，GPU 测试为本机 Flutter Impeller 测试引擎，不能代替具体手机的 GPU 性能验收。

参考代码采用 MIT 许可，版权 `2025 MAX ROVENSKY`。随包保留完整许可证，可在设置 → 开源许可查看。

## 安装包

`dist/QuikPad-2.2.0.apk`：54,736,941 bytes，版本 `2.2.0 (4)`，minSdk 26 / targetSdk 35。
`aapt dump badging` 确认显示名称 QuikPad；APK 内 Logo 与根目录源文件字节一致，位移贴图、许可证和编译后的着色器均已打包。
`apksigner verify --print-certs` 验证通过，证书与旧版一致。
SHA-256：`54D323E60D378E94630FE8D04AB883C1CB9D8FC276B2E86ACC432F7A63034EF8`。
