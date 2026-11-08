# 语音识别实验记录

用户报告安静时「跳过」约九成可用，抖音外放时体感约 20–30%。当前目标是在同类外放条件下把「跳过」的用户口令召回率提高到 80–90%，同时检查误滑、重复滑动和延迟。保留当前口令，优先验证现成模型和工程方案。用户已尝试获取抖音的独立播放参考音频，当前设备和应用组合未拿到可用参考。

## 当前基线

- 2026-11-08 工作区中，APK 的 sherpa JNI 为 `1.12.20`。KWS 配置 `type=0` 使用 `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` 的 FP32 chunk-16 模型。默认 `maxActivePaths=4`、`keywordsScore=1.5`、`keywordsThreshold=0.25`、`numTrailingBlanks=2`。
- 模型目录中的 `keywords.txt` 决定 KWS 能报出的词。Java/Kotlin 侧的 `Command.kt` 决定收到文本后执行的命令。使用完整 ASR 直接接 `Command.fromText` 前，注意现有实现按枚举顺序做子串匹配，普通对话里的「跳过」也可能匹配，需要在实验中验证命令判定。
- 维护者本机的上游检出在 `/Users/lmk/Android/Temp/sherpa-onnx`，当时为 `347ec768`。之前桌面回放用过 sherpa-onnx 2.0.1。上游检出、桌面回放库和 APK 的 JNI 分别记录。更新 JNI 时一并核对 Kotlin 绑定和 ONNX Runtime 是否匹配。

## 已做过的对照

| 对照 | 实验结果 |
| --- | --- |
| iQOO 12 录音源 A/B | 外放会话 A 为 89.3 秒，触发 6 次。B 为 80.1 秒，触发 5 次。两段均用桌面回放复现了手机的触发，日志有对应滑动完成记录。A 有 757/893 个全零块，B 有 1/801 个。全零块是输入特征，不能单独用来判断漏掉了哪个音节。两段没有逐条人工标注，不能从触发数推出召回率或误触发率。 |
| KWS 参数和原生重置策略 | 放宽阈值、提高搜索路径数在部分录音上增加命中，仍有漏检。保留 encoder 状态的原生补丁在部分样本有收益、在其他样本丢失已有命中，尚无一致收益。 |
| 单纯增益与 VoxLock | 对 B 的 PCM 单纯增益没有增加 KWS 触发。B 加约 18 dB 后经过 VoxLock 得到 11 次触发，A 在原始增益下仍为 6 次。B 新增触发的说话人未核实，这一轮只说明前处理改变了输出。 |

同一 PCM 的离线回放复现了手机端漏检，因此后续可先做录音对照来验证模型和音频前处理。对完整 ASR 的首选候选曾是 [SenseVoiceSmall INT8](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html) 和 [Paraformer 中文 Small INT8](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-paraformer/paraformer-models.html)。截至本记录，这两项尚无该批 PCM 的实测成绩，临时目录的下载未完成。上轮候选只用于记录，后续实验以维护者给出的新方案为准。

## 复用本机数据

| 本机路径 | 内容 |
| --- | --- |
| `/Users/lmk/Android/logs/Audio` | 早期导出的录音。 |
| `/private/tmp/douyin-recording-source-ab.cKOxzx` | 录音源 A/B 的 PCM、日志和 `summary.json`，另有增益和 VoxLock 对照。 |
| `/private/tmp/douyin-iqoo-speaker-retest.tCoQd4` | 较早的单段外放复测及 `analysis.json`。 |
| `/private/tmp/douyin-iqoo-20261108.878zFI` | 较早的长录音，包含静音播放和外放测试片段。 |
| `/private/tmp/douyin-kws-deep-research.FIC3h9` | `replay.py`、按候选片段回放的结果、当前 KWS 的音素检查和未完成的模型文件。 |
| `/private/tmp/douyin-kws-reset-ab.HbdlzN` | 上游原生补丁、构建出的 A/B 回放器和 `summary.json`。 |

`/private/tmp` 下的文件属于历史会话产物，可能被清理。录音和日志应保存在仓库外。复用时先检查文件完整性，并为测试样本记录录音源、模型配置、用户命令时间和纯播放区间。

离线评估先用人工标注的命令片段比较模型，再用同一批完整录音验证连续识别。每次用户口令最多计一次命中，另计重复触发和纯播放时的误触发。历史脚本的能量分段只能定位候选，尤其要区分视频里说的词和用户口令。若接入独立 ASR 来补回漏检，旧 KWS 的命中不应成为该路径的启动条件。真机验证还要对齐录音、识别和手势的时间，测抖音并行播放时的延迟与资源占用。
