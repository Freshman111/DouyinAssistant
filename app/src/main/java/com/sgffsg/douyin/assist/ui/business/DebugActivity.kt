package com.sgffsg.douyin.assist.ui.business

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sgffsg.douyin.assist.R
import com.sgffsg.douyin.assist.common.utils.DebugCache

class DebugActivity : AppCompatActivity() {

    private lateinit var cbSaveAudio: CheckBox
    private lateinit var btnRequestPermission: Button
    private lateinit var tvPermissionStatus: TextView

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1001
        private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        initViews()
        checkPermissions()
        setupListeners()
        updateUI()
    }

    private fun initViews() {
        cbSaveAudio = findViewById(R.id.cb_save_audio)
        btnRequestPermission = findViewById(R.id.btn_request_permission)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
    }

    private fun updateUI() {
        // 根据缓存状态更新UI
        cbSaveAudio.isChecked = DebugCache.isSaveAudioEnabled()
    }

    private fun checkPermissions() {
        val hasRecordPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasWritePermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 10及以上使用分区存储，不需要WRITE_EXTERNAL_STORAGE权限
        }

        updatePermissionStatus(hasRecordPermission, hasWritePermission)

        if (!hasRecordPermission || !hasWritePermission) {
            requestPermissions()
        }
    }

    private fun updatePermissionStatus(hasRecord: Boolean, hasWrite: Boolean) {
        val recordStatus = if (hasRecord) "录音权限: 已授予" else "录音权限: 未授予"
        val writeStatus = if (hasWrite) "存储权限: 已授予" else "存储权限: 未授予"
        tvPermissionStatus.text = "$recordStatus\n$writeStatus"
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    private fun setupListeners() {
        btnRequestPermission.setOnClickListener {
            requestPermissions()
        }

        cbSaveAudio.setOnCheckedChangeListener { _, isChecked ->
            // 更新缓存中的开关状态
            DebugCache.setSaveAudioEnabled(isChecked)
            Toast.makeText(this, "音频保存: ${if (isChecked) "开启" else "关闭"}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 页面重新显示时更新UI状态
        updateUI()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkPermissions()
    }
}