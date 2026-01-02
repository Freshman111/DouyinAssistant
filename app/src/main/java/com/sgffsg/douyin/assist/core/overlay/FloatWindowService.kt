package com.sgffsg.douyin.assist.core.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.sgffsg.douyin.assist.R
import com.sgffsg.douyin.assist.util.LogUtil

/**
 * 悬浮窗服务
 * 用于在屏幕上显示控制悬浮窗
 */
class FloatWindowService : Service() {

    private val TAG = "FloatWindowService"
    
    private var floatView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        LogUtil.d(TAG, "悬浮窗服务创建")
        createFloatWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatWindow()
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
            val ivDrag = findViewById<ImageView>(R.id.iv_drag)
            val tvStatus = findViewById<TextView>(R.id.tv_status)
            val ivClose = findViewById<ImageView>(R.id.iv_close)

            // 拖拽功能
            var lastX = 0f
            var lastY = 0f
            var isDragging = false

            ivDrag.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX
                        lastY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY

                        layoutParams?.apply {
                            x += dx.toInt()
                            y += dy.toInt()
                        }

                        windowManager?.updateViewLayout(floatView, layoutParams)
                        lastX = event.rawX
                        lastY = event.rawY

                        // 判断是否为拖拽操作（移动距离大于阈值）
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isDragging = true
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // 如果不是拖拽，而是点击，则切换状态
                            toggleStatus()
                        }
                        true
                    }
                    else -> false
                }
            }

            // 关闭按钮
            ivClose.setOnClickListener {
                removeFloatWindow()
            }

            // 更新状态显示
            updateStatus()
        }
    }

    private fun toggleStatus() {
        // 这里可以实现切换某些功能的开关状态
        // 暂时只是更新状态文本
        updateStatus()
    }

    private fun updateStatus() {
        floatView?.findViewById<TextView>(R.id.tv_status)?.text = "抖音助手"
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