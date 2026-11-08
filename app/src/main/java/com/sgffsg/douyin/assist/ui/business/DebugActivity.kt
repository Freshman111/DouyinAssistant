package com.sgffsg.douyin.assist.ui.business

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sgffsg.douyin.assist.R
import com.sgffsg.douyin.assist.common.utils.DebugCache
import com.sgffsg.douyin.assist.core.accessibility.AssistAccessibilityService
import com.sgffsg.douyin.assist.kws.VoiceEnrollment
import kotlin.concurrent.thread

class DebugActivity : AppCompatActivity() {

    private lateinit var cbSaveAudio: CheckBox
    private lateinit var cbSaveLogs: CheckBox
    private lateinit var cbVoiceRecognitionSource: CheckBox
    private lateinit var btnRequestPermission: Button
    private lateinit var tvPermissionStatus: TextView
    private lateinit var experimentSection: View
    private lateinit var cbVoiceExperiment: CheckBox
    private lateinit var cbVoiceExperimentOnDevice: CheckBox
    private lateinit var btnEnrollVoice: Button
    private lateinit var tvVoiceSample: TextView

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1001
        private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        initViews()
        checkPermissions()
        updateUI()
        setupListeners()
    }

    private fun initViews() {
        cbSaveAudio = findViewById(R.id.cb_save_audio)
        cbSaveLogs = findViewById(R.id.cb_save_logs)
        cbVoiceRecognitionSource = findViewById(R.id.cb_voice_recognition_source)
        btnRequestPermission = findViewById(R.id.btn_request_permission)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        experimentSection = findViewById(R.id.voice_experiment_section)
        cbVoiceExperiment = findViewById(R.id.cb_voice_experiment)
        cbVoiceExperimentOnDevice = findViewById(R.id.cb_voice_experiment_on_device)
        btnEnrollVoice = findViewById(R.id.btn_enroll_voice)
        tvVoiceSample = findViewById(R.id.tv_voice_sample)
    }

    private fun updateUI() {
        // 根据缓存状态更新UI
        cbSaveAudio.isChecked = DebugCache.isSaveAudioEnabled()
        cbSaveLogs.isChecked = DebugCache.isFileLoggingEnabled()
        cbVoiceRecognitionSource.isChecked = DebugCache.useVoiceRecognitionSource()
        experimentSection.visibility = if (VoiceEnrollment.hasModels(this)) View.VISIBLE else View.GONE
        updateEnrollmentUI()
    }

    private fun updateEnrollmentUI(message: String? = null) {
        val hasSample = VoiceEnrollment.hasSample(this)
        tvVoiceSample.text = message ?: if (hasSample) "已录入声样" else "未录入声样"
        btnEnrollVoice.isEnabled = true
        cbVoiceExperiment.isEnabled = hasSample
        cbVoiceExperiment.isChecked = hasSample && DebugCache.isVoiceExperimentEnabled()
        cbVoiceExperimentOnDevice.isEnabled = cbVoiceExperiment.isChecked
        cbVoiceExperimentOnDevice.isChecked = DebugCache.isVoiceExperimentOnDevice()
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
        cbSaveLogs.setOnCheckedChangeListener { _, isChecked ->
            DebugCache.setFileLoggingEnabled(isChecked)
        }
        cbVoiceRecognitionSource.setOnCheckedChangeListener { _, isChecked ->
            DebugCache.setUseVoiceRecognitionSource(isChecked)
        }
        cbVoiceExperiment.setOnCheckedChangeListener { _, isChecked ->
            DebugCache.setVoiceExperimentEnabled(isChecked)
            cbVoiceExperimentOnDevice.isEnabled = isChecked
            if (isChecked) {
                cbSaveAudio.isChecked = true
                cbSaveLogs.isChecked = true
            }
        }
        cbVoiceExperimentOnDevice.setOnCheckedChangeListener { _, isChecked ->
            DebugCache.setVoiceExperimentOnDevice(isChecked)
        }
        btnEnrollVoice.setOnClickListener {
            when {
                AssistAccessibilityService.instance?.isVoiceControlActive() == true ->
                    Toast.makeText(this, "请先关闭语音控制", Toast.LENGTH_SHORT).show()
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED -> requestPermissions()
                else -> startEnrollment()
            }
        }
    }

    private fun startEnrollment() {
        startActivity(Intent(this, VoiceEnrollmentActivity::class.java))
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
