package com.sgffsg.douyin.assist.biz.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.sgffsg.douyin.assist.common.AppRuntime
import com.sgffsg.douyin.assist.common.utils.LogUtil
import com.sgffsg.douyin.assist.router.asr.IAsrMonitor
import com.sgffsg.douyin.assist.router.asr.IAsrMonitorProvider

/**
 * 悬浮窗服务
 * 用于在屏幕上显示ASR识别结果悬浮窗
 */
class FloatWindowService : Service(), IAsrMonitor {

    private val TAG = "FloatWindowService"
    
    private var floatView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // ASR相关
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

    private val CHANNEL_ID = "float_window_service_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        LogUtil.d(TAG, "悬浮窗服务创建")
        createNotificationChannel()
        startForegroundService()
        createFloatWindow()
        registerMonitorAsr()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "抖音助手悬浮窗服务"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("抖音助手")
                .setContentText("悬浮窗服务正在运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("抖音助手")
                .setContentText("悬浮窗服务正在运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatWindow()
        unRegisterMonitorAsr()
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
        ivDrag = floatView?.findViewById(R.id.iv_mic)
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

    // 拖动相关变量
    private var isDragging = false
    private var lastX = 0f
    private var lastY = 0f

    private fun setupFloatWindow() {
        floatView?.apply {
            // 处理悬浮窗拖动
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 记录初始触摸位置
                        lastX = event.rawX
                        lastY = event.rawY
                        isDragging = false
                        return@setOnTouchListener false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 计算移动距离
                        val offsetX = event.rawX - lastX
                        val offsetY = event.rawY - lastY
                        
                        // 更新悬浮窗位置
                        layoutParams?.let {
                            val params = it as WindowManager.LayoutParams
                            params.x += offsetX.toInt()
                            params.y += offsetY.toInt()
                            windowManager?.updateViewLayout(floatView, params)
                        }
                        
                        // 更新最后触摸位置
                        lastX = event.rawX
                        lastY = event.rawY
                        
                        // 标记为正在拖动
                        isDragging = true
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 如果是拖动，不处理点击事件
                        return@setOnTouchListener isDragging
                    }
                    else -> return@setOnTouchListener false
                }
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

    private fun registerMonitorAsr() {
        AppRuntime.getService(IAsrMonitorProvider::class.java)?.registerMonitor(this)
    }

    private fun unRegisterMonitorAsr() {
        AppRuntime.getService(IAsrMonitorProvider::class.java)?.unRegisterMonitor(this)
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
            Settings.canDrawOverlays(applicationContext)
        } else {
            true
        }
    }

    companion object {
        fun startService(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, FloatWindowService::class.java))
            } else {
                context.startService(Intent(context, FloatWindowService::class.java))
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, FloatWindowService::class.java))
        }

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (FloatWindowService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

    override fun onAsr(asr: String) {
        updateAsrResult(asr)
    }
}