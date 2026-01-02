package com.sgffsg.douyin.assist.ui.business

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.sgffsg.douyin.assist.R
import com.sgffsg.douyin.assist.core.accessibility.AssistAccessibilityService
import com.sgffsg.douyin.assist.biz.overlay.FloatWindowService
import com.sgffsg.douyin.assist.databinding.ActivityAssistSettingBinding
import com.sgffsg.douyin.assist.permissions.PermissionUtil
import com.sgffsg.douyin.assist.permissions.PermissionX
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

        // 开启语音控制
        binding.btnStartVoice.setOnClickListener {
            if (!AssistAccessibilityService.isAccessibilitySettingsOn(this)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PermissionX.with(this)
                .permission(Manifest.permission.RECORD_AUDIO)
                .onGranted {
                    startVoiceControl()
                }
                .onDenied { deniedList ->
                    Toast.makeText(this, "您拒绝了权限: $deniedList", Toast.LENGTH_SHORT).show()
                }
                .onPermanentlyDenied { deniedList ->
                    showRequestPermissionDlg(R.string.settings_page_permission_tip)
                }
                .request()
        }

        // 关闭语音控制
        binding.btnStopVoice.setOnClickListener {
            AssistAccessibilityService.instance?.stopVoiceControl()
            Toast.makeText(this, "语音控制已关闭", Toast.LENGTH_SHORT).show()
        }

        // 开启悬浮窗
        binding.btnStartOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                // 请求悬浮窗权限
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "请授予悬浮窗权限后重试", Toast.LENGTH_LONG).show()
            } else {
                startFloatWindow()
            }
        }

        // 关闭悬浮窗
        binding.btnStopOverlay.setOnClickListener {
            stopFloatWindow()
        }
    }

    private fun startFloatWindow() {
        if (FloatWindowService.isServiceRunning(this)) {
            Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show()
            return
        }
        
        FloatWindowService.startService(this)
        Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatWindow() {
        FloatWindowService.stopService(this)
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun showRequestPermissionDlg(id: Int) {
        val dialog = MaterialDialog(this)
            .title(R.string.settings_page_permission_title)
            .message(id)

        dialog.show {
            positiveButton(R.string.confirm) {
                PermissionUtil.openAppSettings(this@AssistSettingActivity)
            }
            negativeButton(R.string.cancel) {

            }
        }
    }

    private fun startVoiceControl() {
        val success = AssistAccessibilityService.instance?.startVoiceControl() ?: false
        if (success) {
            Toast.makeText(this, "语音控制已开启，请说话...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "语音控制开启失败", Toast.LENGTH_SHORT).show()
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