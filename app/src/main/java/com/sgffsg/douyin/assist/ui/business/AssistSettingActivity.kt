package com.sgffsg.douyin.assist.ui.business

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.sgffsg.douyin.assist.core.accessibility.AssistAccessibilityService
import com.sgffsg.douyin.assist.databinding.ActivityAssistSettingBinding
import com.sgffsg.douyin.assist.ui.binding.BaseBindingActivity
import com.sgffsg.douyin.assist.viewmodel.MainViewModel

/**
 * 助手设置界面
 */
class AssistSettingActivity : BaseBindingActivity<ActivityAssistSettingBinding>() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        observeViewModel()
    }


    override fun bind(): ActivityAssistSettingBinding {
        return ActivityAssistSettingBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        // 检查无障碍服务状态按钮
        binding.btnCheckService.setOnClickListener {
            val isEnabled = AssistAccessibilityService.isAccessibilitySettingsOn(this)
            val message = if (isEnabled) "无障碍服务已开启" else "无障碍服务未开启"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            updateServiceStatus(isEnabled)
        }

        // 打开无障碍设置
        binding.btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // 开始自动点赞
        binding.btnStartLike.setOnClickListener {
            if (!AssistAccessibilityService.isAccessibilitySettingsOn(this)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AssistAccessibilityService.instance?.startAutoLike()
            Toast.makeText(this, "开始自动点赞", Toast.LENGTH_SHORT).show()
        }

        // 停止自动点赞
        binding.btnStopLike.setOnClickListener {
            AssistAccessibilityService.instance?.stopAutoLike()
            Toast.makeText(this, "停止自动点赞", Toast.LENGTH_SHORT).show()
        }

        // 开始自动滑动
        binding.btnStartSwipe.setOnClickListener {
            if (!AssistAccessibilityService.isAccessibilitySettingsOn(this)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AssistAccessibilityService.instance?.startAutoSwipe()
            Toast.makeText(this, "开始自动滑动", Toast.LENGTH_SHORT).show()
        }

        // 停止自动滑动
        binding.btnStopSwipe.setOnClickListener {
            AssistAccessibilityService.instance?.stopAutoSwipe()
            Toast.makeText(this, "停止自动滑动", Toast.LENGTH_SHORT).show()
        }

        // 开始自动点赞+滑动
        binding.btnStartAuto.setOnClickListener {
            if (!AssistAccessibilityService.isAccessibilitySettingsOn(this)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AssistAccessibilityService.instance?.startAutoMode()
            Toast.makeText(this, "开始自动模式", Toast.LENGTH_SHORT).show()
        }

        // 停止所有操作
        binding.btnStopAll.setOnClickListener {
            AssistAccessibilityService.instance?.stopAll()
            Toast.makeText(this, "停止所有操作", Toast.LENGTH_SHORT).show()
        }

        // 开启语音控制
        binding.btnStartVoice.setOnClickListener {
            if (!AssistAccessibilityService.isAccessibilitySettingsOn(this)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val success = AssistAccessibilityService.instance?.startVoiceControl() ?: false
            if (success) {
                Toast.makeText(this, "语音控制已开启，请说话...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "语音控制开启失败", Toast.LENGTH_SHORT).show()
            }
        }

        // 关闭语音控制
        binding.btnStopVoice.setOnClickListener {
            AssistAccessibilityService.instance?.stopVoiceControl()
            Toast.makeText(this, "语音控制已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        // 可以在这里观察ViewModel的数据
    }

    private fun updateServiceStatus(isEnabled: Boolean) {
        binding.tvServiceStatus.text = if (isEnabled) {
            "服务状态: 已启用 ✓"
        } else {
            "服务状态: 未启用 ✗"
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回时检查服务状态
        val isEnabled = AssistAccessibilityService.isAccessibilitySettingsOn(this)
        updateServiceStatus(isEnabled)
    }

}