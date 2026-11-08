# 抖音助手开发接手

DouyinAssistant（应用内名称「随声刷」）是一个 Android Kotlin 项目。用户在设置页打开语音控制后，应用持续读取麦克风，识别「跳过」「上滑」「点赞」等口令，并通过 Android 无障碍服务执行操作。「跳过」对应向上滑动到下一个视频。

- [真机构建与调试](android-debug.md) 记录构建、运行、导出录音和定位问题的步骤。
- [语音识别实验记录](voice-recognition.md) 记录外放漏识别的目标、已完成的对照和本机数据位置。

## 从哪里开始读代码

| 模块 | 职责和入口 |
| --- | --- |
| `app` | [AssistSettingActivity](../app/src/main/java/com/sgffsg/douyin/assist/ui/business/AssistSettingActivity.kt) 处理权限和启停，[AssistAccessibilityService](../app/src/main/java/com/sgffsg/douyin/assist/core/accessibility/AssistAccessibilityService.kt) 持有识别实例并把命令转成操作，[AssistAccessibilityPerform](../app/src/main/java/com/sgffsg/douyin/assist/core/accessibility/AssistAccessibilityPerform.kt) 调用 `dispatchGesture`。 |
| `biz-kws` | [KwsRecognitionService](../biz-kws/src/main/java/com/sgffsg/douyin/assist/kws/KwsRecognitionService.kt) 是当前语音控制的录音与识别入口。[Command](../biz-kws/src/main/java/com/sgffsg/douyin/assist/kws/Command.kt) 将识别文本映射到业务命令。 |
| `biz-sherpa-onnx` | [KeywordSpotter.kt](../biz-sherpa-onnx/src/main/java/com/k2fsa/sherpa/onnx/KeywordSpotter.kt) 定义模型配置与 JNI 绑定。模型及词表在 `src/main/assets/`，预编译 JNI 和 ONNX Runtime 在 `libs/arm64-v8a/`。 |
| `common` | `DebugCache` 保存调试开关，`LogUtil` 和 `FileLogWriter` 处理可选的应用文件日志，由 `AssistApp` 初始化。 |
| `biz-asr`、`models`、`router-asr`、`biz-overlay` | Vosk、模型资源、ASR 路由及悬浮窗相关模块。项目里也有录音和识别 Demo。排查设置页「开启语音」应从 `biz-kws` 开始。 |

当前工作区的主链路是 `AssistSettingActivity → AssistAccessibilityService → KwsRecognitionService → KeywordSpotter → CommandListener → AssistAccessibilityPerform`。识别代码每次读取最多 1600 个采样（100 ms），将 PCM16 转为浮点后送入同一条流，并在关键词命中后重置该流。当前录音、可选 PCM 写入和 KWS 解码都在 `KwsRecording` 线程上执行。

语音链路的模型切换和录音源 A/B 改动可能仍在工作区。接手先看 `git status --short` 和相关 diff，并以当前文件、实际安装的 APK 和会话日志分别确认代码、运行版本和实验配置。
