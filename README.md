# Assignment_2 - Video Player + Speech Profanity Moderation (Android)

本项目实现：
1. 使用 Jetpack Media3(ExoPlayer) 播放本地视频（assets 内置视频 + 选择系统本地文件）
2. 解析视频音轨，离线语音识别（Vosk）
3. 脏话检测与出现次数统计（词表可扩展，多语言可支持）
4. 加分项：基于响度(dBFS)的“情绪波动”简单推断

---

## 1. 环境要求

- Android Studio
- compileSdk >= 35
- minSdk >= 26（建议 26+，便于 MediaCodec/Media3）
- 依赖：
  - androidx.media3:media3-exoplayer / media3-ui
  - com.alphacephei:vosk-android
  - coroutines-android
  - appcompat / lifecycle-runtime-ktx

---

## 2. 目录结构
app/src/main/
assets/
videos/
sample.mp4
other1.mp4
other2.mp4
profanity/
en.txt
zh.txt
vosk_models/
en/ (解压后的 Vosk 英文模型目录)
zh/ (解压后的 Vosk 中文模型目录，可选)
res/layout/
activity_main.xml
java/com/example/assignment_2/
MainActivity.kt
VideoAudioModerationEngine.kt
AssetCopier.kt
ProfanityDetector.kt
EmotionAnalyzer.kt
Types.kt


---

## 3. 视频应该放在哪里？

### 3.1 内置视频（推荐演示用）
把视频放到：
`app/src/main/assets/videos/`

App 内会通过 `videoSpinner` 自动读取该目录下的视频列表并播放。

### 3.2 系统本地视频（OpenDocument 选择）
“选择本地视频”按钮调用的是系统文件选择器，只能选择设备存储（如 Download/Movies），不能浏览 APK 内的 assets。

如果你想用系统文件选择器：
- 把视频放到模拟器/手机的 `Download` 或 `Movies`
- 再点按钮选择

---

## 4. Vosk 模型放哪里？

把模型**解压后的文件夹内容**放到：

- 英文：
  `app/src/main/assets/vosk_models/en/`
  （里面应有 am/conf/graph/ivector 等目录）

- 中文（可选）：
  `app/src/main/assets/vosk_models/zh/`

注意：Vosk 需要真实文件路径，本项目启动时会把 assets 模型复制到：
`/data/user/0/<package>/files/vosk_models/...`

---

## 5. 脏话词表放哪里？

- 中文词表：
  `app/src/main/assets/profanity/zh.txt`
- 英文词表：
  `app/src/main/assets/profanity/en.txt`

格式：一行一个词/短语，支持 `#` 注释行。

---

## 6. 功能说明

### 6.1 播放
- 使用 Media3 PlayerView 播放视频
- 默认播放 assets/videos 下 spinner 选中的视频

### 6.2 离线识别（Vosk）
- MediaExtractor + MediaCodec 解码音轨得到 PCM
- 下混为单声道，并重采样到 16kHz
- 送入 Vosk Recognizer 得到 partial/final 文本

### 6.3 脏话统计
- 对 final 文本分段统计并累加
- 统计结果包含：
  - totalProfanityCount：出现次数总和
  - detailCounts：每个词出现次数

### 6.4 情绪推断（加分项）
- 对 PCM 计算 dBFS (RMS → dB)
- 根据响度突增/突降推断情绪波动（如激动/震惊/平静）


## 7. 常见问题（FAQ）

### Q1：为什么系统选择器里看不到 assets/videos 的视频？
A：OpenDocument 只能选择设备存储文件，assets 属于 APK 内资源不可见。请使用 videoSpinner 选择 assets 视频，或把视频拷贝到 Download/Movies。

### Q2：App 闪退提示 “Theme.AppCompat theme”
A：MainActivity 是 AppCompatActivity，必须使用 AppCompat 或 MaterialComponents 主题。请在 themes.xml 设置 parent 为 Theme.MaterialComponents.DayNight.NoActionBar。

### Q3：播放几秒后闪退 “CalledFromWrongThreadException”
A：后台线程更新 UI 导致。请确保 onUi 回调中用 runOnUiThread 切回主线程更新 TextView。

### Q4：转写里出现两次脏话，但统计只显示一次？
A：统计只对 final 结果累加，partial 是临时结果可能不计入。可选择：
- UI 只显示 final
- 或在 UI 显示时把 partial 的脏话临时叠加展示（不写入累计 map）

---

## 8. 如何运行

1. Sync Gradle
2. 确认 `assets/videos/` 有视频
3. 确认 `assets/vosk_models/en/`（或 zh）放好了模型
4. Run 到模拟器/真机
5. 从 spinner 选择视频播放，查看转写与脏话统计

---

## 9. 说明
本项目为离线识别方案，不依赖网络；识别精度与速度取决于 Vosk 模型大小与设备性能。


