# 逐行翻译 (LineTrans)

一款用于逐行 / 逐句对照翻译的安卓原生应用，基于 **Jetpack Compose** 开发。

## 功能

### 主界面 / 文档管理
- 顶部三横线侧边菜单：按文件夹分类展示已导入文本。
- 右上角 **+** 添加 `.txt` 源文本。
- 文档卡片展示翻译进度，点击箭头展开 **翻译 / 查看 / 导出 / 删除**。

### 翻译界面
- 支持 **逐句翻译** 与 **逐行翻译** 切换。
- 上方显示原文，下方输入译文，中间分隔线可调、可锁定（防误触）。
- 原文上方提供 **复制 / 修改**；译文上方提供 **AI 翻译 / 复制 / 粘贴原文 / 更多**。
- 右上角 **下一句**，翻译完显示「翻译完毕」并返回主界面。
- 底部展示 **当前模型、输入（未命中/命中）、输出、预计费用**（按用户配置的价格自动估算）。

### AI 翻译
- 支持 OpenAI 兼容接口、Anthropic、自定义接口。
- 在设置中添加 API 提供商、模型，并可配置输入/输出价格（每百万 token）、高峰倍率、高峰时段。
- 可开启自动语言检测，或手动指定源语言 / 目标语言。

### 导出
- 导入文本时选择一个用于存放数据的文件夹（源文本与译文都会保存在那里）。
- 导出方式一：仅导出已翻译文本。
- 导出方式二：已翻译文本替换源文本，未翻译文本显示为源文本。
- 如果导入的文本中已存在翻译内容（如「原文⇥译文」或「原文 => 译文」），会询问是否略过已翻译部分。

### 局域网 Web 终端服务（参考 RikkaHub）
- 在设置中开启 **Web 终端服务**，手机启动一个前台服务，监听局域网端口。
- 同一局域网内的电脑 / 手机浏览器打开：
  ```
  http://<手机IP>:<端口>/terminal
  ```
  即可使用基于 xterm.js 的 Web 终端，终端命令运行在手机本地 shell（`/system/bin/sh`）内。
- 页面与 WebSocket 均使用 xterm.js（通过 CDN 加载），WebSocket 桥接手机 Shell 的输入输出。

## 技术栈
- Kotlin 1.9.24
- Jetpack Compose (Material3)
- Navigation Compose
- OkHttp + Gson（AI API 调用）
- NanoHTTPD + NanoWSD（局域网 Web / WebSocket 终端服务器）
- Android Storage Access Framework（文件夹选择与读写）

## 构建与运行
1. 使用 **Android Studio** 打开本目录（`工程文件/LineTrans`）。
2. 首次打开时，Android Studio 会自动下载 Gradle 与依赖；如提示 Gradle Wrapper 缺失，请用命令行执行：
   ```
   gradle wrapper --gradle-version 8.7
   ```
   或使用 Android Studio 的「Sync Project with Gradle Files」。
3. 连接安卓设备（或模拟器），点击 **Run** 即可。
   - minSdk 26，targetSdk 34。

## 目录结构
```
工程文件/LineTrans/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── assets/web_terminal.html        # Web 终端页面
│       ├── java/com/linetrans/app/
│       │   ├── MainActivity.kt
│       │   ├── ai/TranslationService.kt    # AI 翻译
│       │   ├── data/                       # 设置、文档、存储、导出
│       │   ├── model/Models.kt             # 数据模型
│       │   ├── server/                     # Web 终端服务
│       │   ├── ui/                         # 各界面
│       │   └── util/                       # 文本解析、费用计算、网络
│       └── res/                            # 主题、图标、字符串
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 说明
- Web 终端页面依赖 xterm.js（CDN），首次打开页面需局域网设备能访问互联网；如需离线可用，可自行将 xterm.js 打包进 assets。
- 本应用的 Web 终端运行在应用的本地 shell 中（非 root），仅具备应用进程的权限。
