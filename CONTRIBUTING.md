# 参与贡献

感谢你愿意改进随声刷。无论是 Issue、PR、机型适配还是文档修正，都很有帮助。

## 开始之前

- 先搜索已有 Issue，避免重复。
- 涉及识别效果、崩溃、无障碍失效的问题，请附上：
  - 手机型号、Android 版本、应用版本/commit；
  - 抖音版本；
  - 复现步骤；
  - 相关日志（请先删除个人信息和录音）。
- 不要上传完整录音、声样或包含隐私的截图；必要时只截取日志片段。

## 开发环境

1. 安装 JDK 17、Android SDK Platform 33、NDK 25.2.9519653。
2. `cp local.properties.example local.properties`，填写 `sdk.dir`。
3. 构建：`./gradlew :app:assembleDebug`。
4. 安装到 arm64 真机，开启无障碍服务和麦克风权限后测试。

完整构建和调试步骤见 [doc/android-debug.md](doc/android-debug.md)。

## 分支与提交

- 从 `main` 新建分支：`feat/xxx`、`fix/xxx`、`docs/xxx`。
- 一个 PR 尽量只做一件事，提交信息说清楚「为什么」而不只是「改了什么」。
- 保持 Kotlin 官方代码风格；不要顺手格式化无关文件。
- 提交前至少跑通 `./gradlew :app:assembleDebug`。

## 不能提交的内容

以下内容已在 `.gitignore` 中忽略，请不要再强行加入：

- `sign/` 下的 keystore、`*.jks`、`*.keystore`；
- `local.properties` 及任何密码；
- 录音、PCM/WAV、应用日志；
- `build/` 产物、`.gradle/`、`.idea/`、`.DS_Store`；
- 包含个人信息或设备标识的截图。

## 识别相关改动

语音链路对设备和环境敏感。改动识别、门控、声样或模型时，建议：

- 说明使用的模型版本、录音源（A/B）、音量、是否外放；
- 同时测「有口令」和「纯播放不说话」两段，分别看召回和误触发；
- 用 `tools/replay_voice.py` 或 `tools/replay_gated.py` 提供可复现的离线结果；
- 不要只凭一次体感就下结论，记录次数、延迟和漏检位置。

实验记录可以更新到 [doc/voice-recognition.md](doc/voice-recognition.md)。

## PR 检查清单

- [ ] 构建通过
- [ ] 在真机上验证过主要路径
- [ ] 没有提交密钥、录音或个人信息
- [ ] 必要时更新了 README / doc
- [ ] 改动范围与描述一致

## 许可证

提交 PR 即表示你同意以 [Apache License 2.0](LICENSE) 授权你的贡献。
