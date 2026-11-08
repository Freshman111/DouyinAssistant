package com.sgffsg.douyin.assist.kws

/**
 * 语音指令枚举
 */
enum class Command(val keywords: List<String>) {
    SWIPE_UP(listOf("上滑", "向上滑", "向上滑动", "下一个", "下一条", "下一个视频", "下一条视频")),
    SWIPE_DOWN(listOf("下滑", "向下滑", "上一个", "上一条", "上一个视频", "上一条视频")),
    NEXT_PAGE(listOf("下一页", "翻页")),
    PREVIOUS_PAGE(listOf("上一页", "返回上页")),
    SKIP(listOf("跳过")),
    LIKE(listOf("点赞", "喜欢", "双击")),
    BACK(listOf("返回", "退出", "回去"));

    companion object {
        /**
         * 完整 ASR 的「跳过」判定。只接受很窄的几种说法：单个词、句尾语气词，
         * 以及「你 / 帮我 / 请」这类称呼和客套，避免视频里的长句误触发。
         * 通过示例：跳过、跳过吧、你跳过、帮我跳过呀、请跳过
         * 拒绝示例：跳过这段、我想跳过、快跳过、我们跳过这一段吧
         */
        private val SKIP_PHRASE = Regex("^(你|帮我|请)?跳过(吧|啊|呀|哦|嘛|把)?$")
        private val NEXT_PHRASE = Regex("^(你|帮我|请)?(下一个视频|下一个|下一条|下一页|后一个视频|后一个|翻页)(吧|啊|呀|哦|嘛|把)?$")
        private val PREVIOUS_PHRASE = Regex("^(你|帮我|请)?(上一个视频|上一个|上一条|上一页|前一个视频|前一个)(吧|啊|呀|哦|嘛|把)?$")
        private val PUNCTUATION = Regex("[\\p{P}\\s]")

        private fun normalize(text: String): String = text.replace(PUNCTUATION, "")

        fun isSkipPhrase(text: String): Boolean = SKIP_PHRASE.matches(normalize(text))

        /**
         * 完整 ASR 负责的指令。返回 null 表示这句话不归 ASR 管，
         * 关键词识别可以继续处理（例如「上滑」「返回」）。
         */
        fun asrCommand(text: String): Command? {
            val normalized = normalize(text)
            if (normalized.isEmpty()) return null
            return when {
                SKIP_PHRASE.matches(normalized) -> SKIP
                NEXT_PHRASE.matches(normalized) -> SWIPE_UP
                PREVIOUS_PHRASE.matches(normalized) -> SWIPE_DOWN
                else -> null
            }
        }

        /**
         * 根据识别文本匹配指令
         */
        fun fromText(text: String): Command? {
            if (text.isBlank()) return null
            
            return values().firstOrNull { command ->
                command.keywords.any { keyword -> 
                    text.contains(keyword, ignoreCase = true)
                }
            }
        }
    }
}
