package com.sgffsg.douyin.assist.ui.business

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sgffsg.douyin.assist.core.accessibility.AssistAccessibilityService
import com.sgffsg.douyin.assist.kws.VoiceEnrollment
import kotlin.concurrent.thread

/**
 * Guided 10 s voice sample capture. The sample is what VoxLock uses to keep the
 * user's voice, so it is recorded with the same microphone path as recognition
 * and checked before it is saved.
 */
class VoiceEnrollmentActivity : AppCompatActivity() {

    private lateinit var promptView: TextView
    private lateinit var timerView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var levelView: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var actionView: Button
    private lateinit var secondaryView: Button

    @Volatile
    private var recording = false
    private var worker: Thread? = null

    private val prompts = listOf("跳过", "上滑", "下滑", "点赞", "返回")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildLayout())
        resetPrompt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(48), dp(28), dp(28))
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
        }
        root.addView(TextView(this).apply {
            text = "录入我的声音"
            textSize = 24f
            setTextColor(0xFF222222.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "用你的声音把口令和视频里的人声分开，能明显提高识别准确率。请用平时说口令的音量，依次说下面 5 个词，大约 10 秒。录音只保存在本机。"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(24))
        })
        promptView = TextView(this).apply {
            textSize = 34f
            setTextColor(0xFF111111.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(promptView)
        timerView = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(timerView)
        progressView = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
            ).apply { topMargin = dp(20) }
        }
        root.addView(progressView)
        levelView = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
            ).apply { topMargin = dp(8) }
        }
        root.addView(levelView)
        statusView = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF333333.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(12))
        }
        root.addView(statusView)
        actionView = Button(this).apply {
            text = "开始录音"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(8) }
        }
        root.addView(actionView)
        secondaryView = Button(this).apply {
            text = "重录"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
        }
        root.addView(secondaryView)
        actionView.setOnClickListener { onAction() }
        secondaryView.setOnClickListener { startCapture() }
        return root
    }

    private fun resetPrompt() {
        promptView.text = prompts.joinToString(" · ")
        timerView.text = "录音时请照着上面的词说"
        progressView.progress = 0
        levelView.progress = 0
        statusView.text = ""
        actionView.text = "开始录音"
        actionView.isEnabled = true
        secondaryView.visibility = View.GONE
    }

    private fun onAction() {
        if (recording) {
            finish()
            return
        }
        when (actionView.text) {
            "完成" -> {
                setResult(Activity.RESULT_OK)
                finish()
            }
            "去设置" -> openAppSettings()
            else -> startCapture()
        }
    }

    private fun startCapture() {
        if (recording) return
        val service = AssistAccessibilityService.instance
        if (service?.isVoiceControlActive() == true) {
            // 语音控制占着麦克风，这里直接录会得到静音，用户会以为录好了。
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("语音控制正在使用麦克风")
                .setMessage("录入声音需要先关闭语音控制，否则麦克风被占用，录不到你的声音。")
                .setPositiveButton("关闭语音并继续") { _, _ ->
                    service.stopVoiceControl()
                    requestPermissionAndBegin()
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        requestPermissionAndBegin()
    }

    private fun requestPermissionAndBegin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD
            )
            return
        }
        begin()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            begin()
        } else if (requestCode == REQUEST_RECORD) {
            statusView.text = "需要麦克风权限才能录入声音"
        }
    }

    private fun begin() {
        recording = true
        actionView.isEnabled = false
        secondaryView.visibility = View.GONE
        statusView.text = ""
        progressView.progress = 0
        var shown = 0
        worker = thread(name = "VoiceEnrollmentCapture") {
            val result = runCatching {
                VoiceEnrollment.record(applicationContext) { fraction, level ->
                    runOnUiThread {
                        progressView.progress = (fraction * 100).toInt()
                        levelView.progress = (level * 100).toInt()
                        timerView.text = String.format("%.1f / 10 秒", fraction * 10)
                        val next = (fraction * 10 / 2.0).toInt().coerceIn(0, prompts.size - 1)
                        if (next != shown) {
                            shown = next
                            promptView.text = prompts[next]
                        }
                    }
                }
            }
            runOnUiThread { finishCapture(result) }
        }
    }

    private fun finishCapture(result: Result<VoiceEnrollment.Result>) {
        recording = false
        val value = result.getOrNull()
        if (value == null) {
            promptView.text = "录入未完成"
            timerView.text = ""
            statusView.text = result.exceptionOrNull()?.message ?: "录音失败，请重试"
            actionView.text = "重新录音"
            actionView.isEnabled = true
            return
        }
        promptView.text = if (value.ok) "录入完成" else "需要重录"
        timerView.text = "有效语音 %.1f 秒 · 峰值 %.0f dB · 环境 %.0f dB".format(value.audibleSeconds, value.peakDb, value.noiseDb)
        statusView.text = if (value.ok) "录入完成，回去开启语音就能用了" else value.message
        actionView.isEnabled = true
        if (value.ok) {
            actionView.text = "完成"
            secondaryView.visibility = View.VISIBLE
        } else {
            actionView.text = "重新录音"
        }
    }

    private fun openAppSettings() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    override fun onBackPressed() {
        worker?.interrupt()
        super.onBackPressed()
    }

    companion object {
        private const val REQUEST_RECORD = 71
    }
}
