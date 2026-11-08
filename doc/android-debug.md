# 真机构建与调试

## 构建与启动

应用包名是 `com.sgffsg.douyin.assist`，当前 Gradle Wrapper 7.3.3，AGP 7.2.2，compile/target SDK 33，min SDK 24。应用模块指定 NDK `25.2.9519653`，本项目的 sherpa JNI 当前随包提供 arm64 库，语音测试用 arm64 真机。构建配置见 [gradle/deps.gradle](../gradle/deps.gradle) 和 [app/build.gradle](../app/build.gradle)。

在仓库根目录使用 JDK 17。macOS 下曾用下面的命令成功构建。已缓存依赖时可在 Gradle 命令后加 `--offline`。

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew :app:assembleDebug
```

本机 `local.properties` 提供 Android SDK 路径 `sdk.dir` 和现有签名配置使用的 `sign_pasword`（拼写与 Gradle 一致）。Debug 也使用项目签名。缺少配置时向项目维护者获取本地配置，避免把凭据写进文档或日志。

```sh
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

有多台设备时给 adb 指定 `-s <serial>`。遇到 iQOO 拒绝安装时检查设备是否解锁、是否弹出 USB 安装确认。进入应用的助手设置，在系统设置启用随声刷无障碍服务并授予录音权限，再点「开启语音」，切到抖音播放页测试。悬浮窗有独立入口。

## 采集一组诊断数据

1. 在助手设置先关闭语音，进入调试界面，勾选「保存音频文件」和「保留应用日志」。
2. 记录录音源。不勾选「使用语音识别录音源（B）」是 A（`VOICE_COMMUNICATION`，值 7），勾选是 B（`VOICE_RECOGNITION`，值 6）。更改后关闭并重新开启语音，让新会话按选择创建 `AudioRecord`。
3. 每种条件单独录一段，记录抖音播放状态、音量、是否有人声背景、用户说词的次数和时间。测误触发时留一段只播放、用户不说话的录音。测试滑动时也记录抖音是否启用了播完自动切换。
4. 关闭语音以结束录制，再导出 PCM 和日志。两者开关独立。PCM 文件在语音会话开始时创建，单独在会话中途勾选保存可能没有文件。

```sh
capture_dir="$(mktemp -d "${TMPDIR:-/tmp}/douyin-capture.XXXXXX")"
adb pull /sdcard/Android/data/com.sgffsg.douyin.assist/cache/Audio "$capture_dir/"
adb pull /sdcard/Android/data/com.sgffsg.douyin.assist/files/logs "$capture_dir/"
cd "$capture_dir/Audio"
ffmpeg -f s16le -ar 16000 -ac 1 -i input.pcm output.wav
```

PCM 来自语音控制链路的麦克风读取结果，格式为 16 kHz、单声道、16 位有符号小端，无 WAV 文件头。它与当次送入模型的样本可按日志对齐。如果没有外部文件目录，日志回退到应用内部 `files/logs`，可在支持 `run-as` 的 Debug 包上用 `adb exec-out run-as com.sgffsg.douyin.assist cat files/logs/app.log` 读取。文件日志最多轮转三个文件，每个约 2 MiB。

## VoxLock + Paraformer 实验包

项目的 arm64 sherpa JNI 和 ONNX Runtime 来自同一份 [sherpa-onnx v2.0.1 Android AAR](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1)。实验模型只打入带 `voiceExperimentAssets` 参数的 Debug APK。

准备一个 assets 根目录，在其 `voice-experiment/` 下放入 VoxLock INT8 的 `enroll.onnx`、`chunk_int8.onnx` 和 Paraformer 中文 Small 的 `tokens.txt`、`model.int8.onnx`（后者改名为 `paraformer.int8.onnx`）。当前机器已经准备好这些文件：

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew --offline :app:assembleDebug \
  -PvoiceExperimentAssets=/private/tmp/douyin-android-voxlock-2.0.1/assets
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. 在助手设置关闭语音，再打开调试界面。安静环境下点「录入我的声音」，连续说话约 10 秒。WAV 声样保存在应用私有的免备份目录。
2. 勾选「语音实验（录音对照）」。它会同时打开 PCM 和应用日志开关，自动选择 B 录音源。勾上后只录音不执行，取消勾选才是正式模式。「同时在手机上运行新模型（测速）」现在不再影响是否推理，只保留开关状态。
3. 返回助手设置开启语音，切到抖音外放。分别录制用户说「跳过」与用户不说话的纯播放区间。每轮结束关闭语音，再导出 PCM 和日志。

录音对照模式保存完整 PCM 并记录 KWS 命中。可在电脑上用同一份 PCM 回放新模型。声样可以在 Debug 包上用 `adb exec-out run-as com.sgffsg.douyin.assist cat no_backup/voice-experiment-reference.wav` 导出。

模型和声样都在时，正式模式也跑同一套 VoxLock + Paraformer（双线程），命中「跳过」直接执行滑动；关键词识别只保留其余指令。只对候选片段做分离：20 ms RMS 比 2.5 秒中位数高 6 dB 且高于 −50 dBFS、窗口内至少 120 ms 语音才算候选，候选最长 3 秒，队列超过 35 帧时丢弃该候选并冷却 1 秒。`VoiceExperiment` 的 `ready` 记录模型初始化耗时，`window` 含窗口起止采样、转写、去重前后判断、处理耗时和队列深度，`segment` 给出候选收尾原因（`silence`、`length`、`backlog`）和累计丢弃数，`stop` 给出总音频与总分离量。用 `session_id` 与 `KwsDiagnostics` 关联。`overrun` 表示推理积压导致本轮 ASR 停止，PCM 采集继续。回到正常滑动模式需关闭实验开关，再重新开启语音。

iQOO 12 上，同一段 9 秒音频通过应用测试入口单线程处理约 34 秒，通过 ADB shell 原生回放约 7.4 秒。两次均识别出 3 条口令。2026-11-08 复测表明设备空闲时原生回放稳定在 0.79，抖音前台时同一二进制升到 2.24，应用内全段方案约 5 秒后 `overrun`，与 34 秒同量级。采集完整录音时可关闭手机测速，以便在电脑上回放。

## 抖音并行播放测速

2026-11-08 16:34 已完成全段方案的应用内测速：抖音一见前台，分离耗时从 390 ms/500 ms 涨到约 950 ms，约 5 秒后 `overrun`，结论是跟不上，细节见 [语音识别实验记录](voice-recognition.md)。16:41 换成候选分段后同一台手机上不再中断。下面两项仍适合换机型或改参数时复测，第一项也给模型本身的余量。

第一项用原生回放器，不依赖应用进程调度，给出模型本身在实时节奏下的余量。`/private/tmp/douyin-android-voxlock-2.0.1/run` 已放好 9 秒片段、10 秒声样、两个 VoxLock 模型和 Paraformer：

```sh
adb shell mkdir -p /data/local/tmp/voxprof
adb push /private/tmp/douyin-android-voxlock-2.0.1/run/. /data/local/tmp/voxprof/
adb shell "chmod +x /data/local/tmp/voxprof/profile"
for t in 1 2 4; do
  adb shell "cd /data/local/tmp/voxprof && LD_LIBRARY_PATH=. ./profile $t paced" \
    | tee "/private/tmp/douyin-android-voxlock-2.0.1/profile/paced-thread-$t.txt"
done
```

`paced` 按真实到达节奏喂 100 ms 帧，输出 `lag_p95_ms`、`lag_end_ms` 和 `rtf`。三次结果受温度与调度影响，记录每轮的 `vox_progress_*` 和 `start_cpu`。

第二项用应用实验包，测应用进程在抖音前台时的实际耗时：

1. 安装现有 APK（`app/build/outputs/apk/debug/app-debug.apk`，2026-11-08 13:28 构建，已含实验模型）。
2. 调试界面确认已有声样，勾选「语音实验（录音对照）」并打开日志保存（只录音不执行）。
3. 回助手设置开启语音，切到抖音外放 60–90 秒，其间口播若干次「跳过」，结束时关闭语音。
4. 导出日志后汇总：

```sh
capture_dir="$(mktemp -d "${TMPDIR:-/tmp}/douyin-exp.XXXXXX")"
adb exec-out run-as com.sgffsg.douyin.assist cat files/logs/app.log > "$capture_dir/app.log"
python3 tools/summarize_experiment_log.py "$capture_dir/app.log"
```

脚本按会话给出 `voxlock_rtf`、`combined_rtf`、`lag_p95_ms`、`queue_max` 和 `emitted`。`combined_rtf` 稳定低于 0.8、`lag_p95_ms` 不随时间上升才算跟得上；接近或超过 1、`queue_max` 持续增长或出现 `overrun` 时，改做短段门控。离线对照见 [语音识别实验记录](voice-recognition.md)。

## 用日志区分识别和动作

- `KwsDiagnostics` 的 `start`、`stop`、`audio_window` 给出会话、录音源、累计采样数、全零块、读错误和处理耗时。用 `trace_id` 区分会话。日志的 `pcm_end_sample / 16000` 是对应 PCM 文件内的秒数，代表当前读块结束位置。
- `kws_hit` 有关键词、业务命令和命中 ID。相同会话和 ID 的 `swipe` 可继续跟踪 `accepted`、`completed`、`rejected` 或 `cancelled`。手势完成代表 Android 完成手势，实际视频是否切换可从屏幕核对。
- 视频切换本身不能作为识别成功依据。结合 PCM、口令时间和命中日志区分用户口令、视频内容、系统手势和播放器自动切换。
