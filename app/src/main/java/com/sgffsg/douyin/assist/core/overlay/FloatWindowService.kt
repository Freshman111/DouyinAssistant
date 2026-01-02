package com.sgffsg.douyin.assist.core.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import com.sgffsg.douyin.assist.R
import com.sgffsg.douyin.assist.core.asr.VoiceCommand
import com.sgffsg.douyin.assist.core.asr.VoiceCommandListener
import com.sgffsg.douyin.assist.core.asr.VoiceRecognitionService
import com.sgffsg.douyin.assist.util.LogUtil

/**
 * 悬浮窗服务
 * 用于在屏幕上显示ASR识别结果悬浮窗
 */
class FloatWindowService : Service() {

    private val TAG = "FloatWindowService"
    
    private var floatView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // ASR相关
    private var voiceRecognitionService: VoiceRecognitionService? = null
    private var isAsrEnabled = false
    
    // 动画和UI组件
    private var tvAsrResult: TextView? = null
    private var ivDrag: ImageView? = null
    private var cvContainer: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        LogUtil.d(TAG, "悬浮窗服务创建")
        createFloatWindow()
        initVoiceRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatWindow()
        releaseVoiceRecognition()
        LogUtil.d(TAG, "悬浮窗服务销毁")
    }

    private fun createFloatWindow() {
        if (!checkPermission()) {
            LogUtil.e(TAG, "没有悬浮窗权限，无法创建悬浮窗")
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 创建悬浮窗布局
        floatView = LayoutInflater.from(this).inflate(R.layout.layout_float_window, null)
        
        // 初始化UI组件
        cvContainer = floatView?.findViewById(R.id.cv_container)
        ivDrag = floatView?.findViewById(R.id.iv_drag)
        tvAsrResult = floatView?.findViewById(R.id.tv_asr_result)

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT

            // 设置初始位置
            x = 100
            y = 100
        }

        layoutParams = params

        try {
            windowManager?.addView(floatView, params)
            setupFloatWindow()
        } catch (e: Exception) {
            LogUtil.e(TAG, "创建悬浮窗失败: ${e.message}")
        }
    }

    private fun setupFloatWindow() {
        floatView?.apply {
            ivDrag?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 点击开始，启动ASR识别
                        startAsrRecognition()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 点击结束，停止ASR识别
                        stopAsrRecognition()
                        true
                    }
                    else -> false
                }
            }

            // 关闭按钮
            findViewById<ImageView>(R.id.iv_close).setOnClickListener {
                removeFloatWindow()
            }
        }
    }

    private fun removeFloatWindow() {
        try {
            floatView?.let { view ->
                windowManager?.removeView(view)
                floatView = null
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "移除悬浮窗失败: ${e.message}")
        }
    }

    private fun initVoiceRecognition() {
        voiceRecognitionService = VoiceRecognitionService(this)
        voiceRecognitionService?.setVoiceCommandListener(object : VoiceCommandListener {
            override fun onCommandRecognized(command: VoiceCommand, originalText: String) {
                LogUtil.d(TAG, "收到语音指令: ${command.name}, 原始文本: $originalText")
                updateAsrResult(originalText)
            }

            override fun onError(error: String) {
                LogUtil.e(TAG, "语音识别错误: $error")
                updateAsrResult("识别错误")
            }

            override fun onStateChanged(isListening: Boolean) {
                LogUtil.d(TAG, "语音识别状态: ${if (isListening) "监听中" else "已停止"}")
            }
        })

        // 初始化模型
        voiceRecognitionService?.initialize { success, error ->
            if (success) {
                LogUtil.d(TAG, "语音识别初始化成功")
            } else {
                LogUtil.e(TAG, "语音识别初始化失败: $error")
            }
        }
    }

    private fun releaseVoiceRecognition() {
        voiceRecognitionService?.release()
        voiceRecognitionService = null
    }

    private fun startAsrRecognition() {
        if (isAsrEnabled) return
        
        val success = voiceRecognitionService?.startListening() ?: false
        if (success) {
            isAsrEnabled = true
            LogUtil.d(TAG, "语音识别已开启")
        } else {
            LogUtil.e(TAG, "语音识别开启失败")
        }
    }

    private fun stopAsrRecognition() {
        if (!isAsrEnabled) return
        
        voiceRecognitionService?.stopListening()
        isAsrEnabled = false
        LogUtil.d(TAG, "语音识别已停止")
        
        // 延迟隐藏结果文本
        handler.removeCallbacksAndMessages(null)
        hideRunnable = Runnable {
            hideAsrResult()
        }
        handler.postDelayed(hideRunnable!!, 2000) // 2秒后隐藏
    }

    private fun updateAsrResult(result: String) {
        tvAsrResult?.text = result
        
        // 显示结果文本
        tvAsrResult?.visibility = View.VISIBLE
        
        // 执行展开动画
        startExpandAnimation()
        
        // 取消之前的隐藏任务
        hideRunnable?.let { handler.removeCallbacks(it) }
        
        // 设置新的隐藏任务
        hideRunnable = Runnable {
            hideAsrResult()
        }
        handler.postDelayed(hideRunnable!!, 3000) // 3秒后自动隐藏
    }

    private fun hideAsrResult() {
        // 执行收起动画
        startCollapseAnimation()
        
        // 动画结束后隐藏文本
        handler.postDelayed({
            tvAsrResult?.visibility = View.GONE
        }, 300) // 动画时长
    }

    private fun startExpandAnimation() {
        val scaleAnimation = ScaleAnimation(
            0f, 1f, // X轴从0到1
            0f, 1f, // Y轴从0到1
            Animation.RELATIVE_TO_SELF, 0.5f, // 以中心为锚点
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 300
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    tvAsrResult?.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animation?) {
                    // 动画结束后保持可见
                    tvAsrResult?.visibility = View.VISIBLE
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
        
        tvAsrResult?.startAnimation(scaleAnimation)
    }

    private fun startCollapseAnimation() {
        val scaleAnimation = ScaleAnimation(
            1f, 0f, // X轴从1到0
            1f, 0f, // Y轴从1到0
            Animation.RELATIVE_TO_SELF, 0.5f, // 以中心为锚点
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 300
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    // 动画开始时保持可见
                    tvAsrResult?.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animation?) {
                    // 动画结束后隐藏视图
                    tvAsrResult?.visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
        
        tvAsrResult?.startAnimation(scaleAnimation)
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    companion object {
        fun startService(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, FloatWindowService::class.java))
            } else {
                context.startService(Intent(context, FloatWindowService::class.java))
            }
        }

        fun stopService(context: android.content.Context) {
            context.stopService(Intent(context, FloatWindowService::class.java))
        }

        fun isServiceRunning(context: android.content.Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (FloatWindowService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}