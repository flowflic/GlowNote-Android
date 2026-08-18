# GlowNote Android

GlowNote 的 Android 客户端 MVP：

当前版本：`0.0.3`

- 在应用内打开任意 `http(s)` 网页；
- 长按选择文字，保存黄色、红色、蓝色、绿色或橙色高亮；
- 为高亮添加批注，`#标签` 会自动进入文章标签；
- 点击网页中的已有高亮可以编辑或删除批注；
- 文章列表按来源 URL 聚合，支持收藏；
- WebDAV 配置、连接测试、手动同步；
- 与 Chrome 扩展共用 WebDAV v2 快照：`/highlight-extension/sync-v2.json`。

## 构建

用 Android Studio 打开 `android/`，或在有 Gradle 8.9 的环境运行：

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## WebDAV 兼容说明

客户端会优先读取配置路径对应的 `-v2.json` 文件；如果不存在，会读取旧的 `.json` 文件并在下一次写入时迁移到 v2。记录中的 `anchor`、Notion 映射等未知字段会保留，避免 Android 同步覆盖桌面端元数据。

WebDAV 服务器保存的是明文 JSON，建议使用 HTTPS。Android 本地只把 WebDAV 密码放入 Android Keystore 加密存储。

网页批注通过 WebView 的选择事件和 DOM 高亮桥接实现。复杂的 SPA 页面可能在自身重绘 DOM 后需要重新打开页面才能恢复高亮；普通文章页、文档页和静态网页可直接使用。
