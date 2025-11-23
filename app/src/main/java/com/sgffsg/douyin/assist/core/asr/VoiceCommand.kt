package com.sgffsg.douyin.assist.core.asr

/**
 * 语音指令枚举
 */
enum class VoiceCommand(val keywords: List<String>) {
    SWIPE_UP(listOf("上滑", "向上滑", "下一个", "下一条")),
    SWIPE_DOWN(listOf("下滑", "向下滑", "上一个", "上一条")),
    NEXT_PAGE(listOf("下一页", "翻页")),
    PREVIOUS_PAGE(listOf("上一页", "返回上页")),
    SKIP(listOf("跳过", "下一个")),
    LIKE(listOf("点赞", "喜欢", "双击")),
    BACK(listOf("返回", "退出", "回去"));

    companion object {
        /**
         * 根据识别文本匹配指令
         */
        fun fromText(text: String): VoiceCommand? {
            if (text.isBlank()) return null
            
            return values().firstOrNull { command ->
                command.keywords.any { keyword -> 
                    text.contains(keyword, ignoreCase = true) 
                }
            }
        }
    }
}
