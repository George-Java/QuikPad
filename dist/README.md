# QuikPad 2.3.0 · Flutter 测试构建

- 文件：[QuikPad-2.3.0.apk](QuikPad-2.3.0.apk)
- 包名：`com.yg.liquidink`
- 版本：`2.3.0 (versionCode 5)`
- 最低系统：Android 8.0（API 26）
- 目标：Android 15（API 35）
- ABI：arm64-v8a / armeabi-v7a / x86_64（通用 APK）
- 大小：61,586,445 bytes（约 58.7 MiB）
- SHA-256：`F5359446A00AE713873D13525D5D22E5AA4458A37FA07D10F66C181933168432`

## 2.3 更新

- 修复深色模式玻璃卡片右边缘黑/绿断续竖线：移除斜向渐变边缘、增加连续中性描边、限制静态卡片指针形变。
- 相对路径图片：优先从当前 Markdown 同目录读取 `assets/images/test.png` 等；Android SAF 无法直接读同级文件时，可一次性关联 Markdown 所在文件夹。
- 完整 HTML/GFM 解析：`<h2>`、`<a>`、`<img>`、`<table>`、实体、锚点等。
- 代码块高亮：C/C++/Python/Java/PHP/JavaScript/HTML/CSS/XML/YAML/Shell 等 180+ 语言。
- 版本 2.3.0（5），保留原包名、文档目录与签名证书，可直接覆盖升级。

## 安装

```bash
adb install -r QuikPad-2.3.0.apk
```

release 构建，R8 压缩，使用本机 Android Debug 证书签名。适合本地安装验收，不是商店发布密钥。

源码、根因、测试与验收说明：

- [根目录 README](../README.md)
- [QuikPad 2.3 修复报告](../docs/QUIKPAD_2_3.md)
- [Flutter 重构记录](../docs/FLUTTER_REWRITE.md)

更早的 1.0 Compose、2.0/2.1/2.2 构建保存在 `legacy/`，仅用于回滚。
