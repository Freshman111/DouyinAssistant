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

## 用日志区分识别和动作

- `KwsDiagnostics` 的 `start`、`stop`、`audio_window` 给出会话、录音源、累计采样数、全零块、读错误和处理耗时。用 `trace_id` 区分会话。日志的 `pcm_end_sample / 16000` 是对应 PCM 文件内的秒数，代表当前读块结束位置。
- `kws_hit` 有关键词、业务命令和命中 ID。相同会话和 ID 的 `swipe` 可继续跟踪 `accepted`、`completed`、`rejected` 或 `cancelled`。手势完成代表 Android 完成手势，实际视频是否切换可从屏幕核对。
- 视频切换本身不能作为识别成功依据。结合 PCM、口令时间和命中日志区分用户口令、视频内容、系统手势和播放器自动切换。
