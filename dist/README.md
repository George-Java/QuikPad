# QuikPad 2.3.0 · Flutter 测试构建

- 文件：[QuikPad-2.3.0-fixed.apk](https://github.com/George-Java/QuikPad/releases/download/v2.3.0/QuikPad-2.3.0-fixed.apk)（GitHub Releases 资产，不在 Git 树中）
- 包名：`com.yg.liquidink`
- 版本：`2.3.0 (versionCode 5)`
- 最低系统：Android 8.0（API 26）
- 目标：Android 15（API 35）
- ABI：arm64-v8a / armeabi-v7a / x86_64（通用 APK）
- 大小：61,621,069 bytes（约 58.8 MiB）
- SHA-256：`547E6545E0D1E0B93AEE8D1395AF06B45A37C30A5B30D4BF9D205FFCF1ED0F2B`

> `fixed` 为 2026-10-29 在物理手机（PLQ110 / Android 17）上验收后的构建，作为
> GitHub Releases `v2.3.0` 的资产发布，不进入 Git 仓库；
> `QuikPad-2.3.0.apk` 是修复前构建，保留用于对比/回滚。

## 本轮修复

- OPPO/OnePlus 安全键盘全屏黑屏：输入框不再使用无建议/可见密码标记；新建、查找、重命名与源码编辑键盘均可正常显示。
- 任务勾选行号：引用块、嵌套列表、代码围栏和 HTML 复选框不再串行；无法确认行号时不修改。
- 保存：临时文件原子替换、并发保存串行化、写入期间回退会补写磁盘状态；同名重命名不再生成副本。
- 图片：解码保持宽高比，竖图/方图不再被压扁；关联 Markdown 目录后整篇预览自动刷新。
- 文档菜单：高度受限并可滚动，删除行不再被屏幕底部裁掉。
- 大字号：1.5×/2×/3× 系统字体下首页无溢出；Android 关闭 Auto Backup。

## 2.3 更新

- 修复深色模式玻璃卡片右边缘黑/绿断续竖线：移除斜向渐变边缘、增加连续中性描边、限制静态卡片指针形变。
- 相对路径图片：优先从当前 Markdown 同目录读取 `assets/images/test.png` 等；Android SAF 无法直接读同级文件时，可一次性关联 Markdown 所在文件夹。
- 完整 HTML/GFM 解析：`<h2>`、`<a>`、`<img>`、`<table>`、实体、锚点等。
- 代码块高亮：C/C++/Python/Java/PHP/JavaScript/HTML/CSS/XML/YAML/Shell 等 180+ 语言。
- 版本 2.3.0（5），保留原包名、文档目录与签名证书，可直接覆盖升级。

## 安装

```bash
# 先从 Releases 下载 QuikPad-2.3.0-fixed.apk
adb install -r QuikPad-2.3.0-fixed.apk
```

release 构建，R8 压缩，使用本机 Android Debug 证书签名。适合本地安装验收，不是商店发布密钥。

源码、根因、测试与验收说明：

- [根目录 README](../README.md)
- [QuikPad 2.3 修复报告](../docs/QUIKPAD_2_3.md)
- [Flutter 重构记录](../docs/FLUTTER_REWRITE.md)

更早的 1.0 Compose、2.0/2.1/2.2 构建保存在 `legacy/`，仅用于回滚。
