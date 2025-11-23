# 语音识别(ASR)模块使用说明

## 模块结构

```
com.sgffsg.douyin.assist.core.asr/
├── VoiceCommand.kt              # 语音指令枚举
├── VoiceCommandListener.kt      # 语音指令监听器接口
└── VoiceRecognitionService.kt   # 语音识别服务
```

## 核心类说明

### 1. VoiceCommand
定义支持的语音指令：
- `SWIPE_UP` - 上滑（关键词：上滑、向上滑、下一个、下一条）
- `SWIPE_DOWN` - 下滑（关键词：下滑、向下滑、上一个、上一条）
- `NEXT_PAGE` - 下一页（关键词：下一页、翻页）
- `PREVIOUS_PAGE` - 上一页（关键词：上一页、返回上页）
- `SKIP` - 跳过（关键词：跳过、下一个）
- `LIKE` - 点赞（关键词：点赞、喜欢、双击）
- `BACK` - 返回（关键词：返回、退出、回去）

### 2. VoiceCommandListener
语音识别回调接口：
```kotlin
interface VoiceCommandListener {
    fun onCommandRecognized(command: VoiceCommand, originalText: String)
    fun onError(error: String)
    fun onStateChanged(isListening: Boolean)
}
```

### 3. VoiceRecognitionService
语音识别核心服务，基于 Vosk 离线语音识别引擎。

## 在 AssistAccessibilityService 中的集成

已在 `AssistAccessibilityService` 中集成语音控制功能：

### 初始化
服务连接时自动初始化语音识别模块：
```kotlin
override fun onServiceConnected() {
    super.onServiceConnected()
    initVoiceRecognition()
}
```

### 开启/关闭语音控制
```kotlin
// 开启语音控制
AssistAccessibilityService.instance?.startVoiceControl()

// 关闭语音控制
AssistAccessibilityService.instance?.stopVoiceControl()

// 检查状态
val isEnabled = AssistAccessibilityService.instance?.isVoiceControlEnabled()
```

### 暂停/恢复
```kotlin
// 暂停识别（但不关闭服务）
AssistAccessibilityService.instance?.pauseVoiceControl(true)

// 恢复识别
AssistAccessibilityService.instance?.pauseVoiceControl(false)
```

## 使用流程

1. **开启无障碍服务**
   - 进入系统设置 -> 无障碍 -> 抖音助手
   - 启用无障碍服务

2. **打开抖音 App**
   - 确保在抖音视频播放界面

3. **启动语音控制**
   - 在助手设置界面点击"开启语音"按钮
   - 等待提示"语音控制已开启，请说话..."

4. **发出语音指令**
   - 说出指令关键词，如：
     - "上滑" - 切换到下一个视频
     - "点赞" - 给当前视频点赞
     - "返回" - 返回上一级

5. **关闭语音控制**
   - 点击"关闭语音"按钮停止监听

## 技术细节

### Vosk 模型
- 使用中文小型模型：`vosk-model-small-cn`
- 模型位置：`models/src/main/assets/vosk-model-small-cn/`
- 采样率：16000 Hz

### 指令匹配
- 使用关键词匹配，支持模糊匹配
- 识别结果为 JSON 格式，提取 `text` 字段进行匹配
- 匹配成功后立即执行对应操作

### 权限要求
- `android.permission.RECORD_AUDIO` - 录音权限（已在 AndroidManifest.xml 中声明）

## 扩展指令

如需添加新指令，只需修改 `VoiceCommand.kt`：

```kotlin
enum class VoiceCommand(val keywords: List<String>) {
    // ... 现有指令
    
    // 添加新指令
    NEW_COMMAND(listOf("关键词1", "关键词2")),
}
```

然后在 `AssistAccessibilityService.executeVoiceCommand()` 中添加对应的执行逻辑。

## 注意事项

1. **首次使用需等待模型加载**
   - 模型解压需要几秒时间
   - 初始化完成后会有日志提示

2. **语音识别是持续监听的**
   - 开启后会一直监听麦克风
   - 不使用时记得关闭以节省电量

3. **环境噪音影响识别率**
   - 在安静环境下效果最佳
   - 说话清晰、语速适中

4. **离线识别**
   - 不需要网络连接
   - 所有识别在本地完成
