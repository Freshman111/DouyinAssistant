package com.sgffsg.douyin.assist.ui.record

import com.sgffsg.douyin.assist.databinding.ActivityAudioRecordBinding
import com.sgffsg.douyin.assist.ui.dialog.RecordConfigDialog
import com.sgffsg.douyin.assist.ui.mvvm.MvvmActivity
import com.sgffsg.douyin.assist.util.clickWithTrigger

class AudioRecordActivity : MvvmActivity<ActivityAudioRecordBinding>() {

    private var recordConfigDialog: RecordConfigDialog? = null
    var isPaused = false
    var isPrepared = false

    companion object{
        const val RECORD_CONFIG_DIALOG = "RecordConfigDialog"
    }

    override fun initObserver() {
        TODO("Not yet implemented")
    }

    override fun bind(): ActivityAudioRecordBinding {
        return ActivityAudioRecordBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
//        val audioRecorder = AudioRecorder.build {
//            this@AudioRecordActivity
//            recorderType = RecorderType.AUDIO_RECORD
//            sampleRate
//        }
        //在线Demo
        //http://music.163.com/song/media/outer/url?id=447925558.mp3
//        binding.editSourceUrl.setText("http://music.163.com/song/media/outer/url?id=447925558.mp3")
//        binding.btnStart.setOnClickListener {
////            SettingPropertyUtil.setSystemStayWakeUpPhoenix(true)
//            audioManagerProxy.isAppAECEnabled = true
//        }
        binding.btnSettings.setOnClickListener {
            showUseGuideDialog()
        }
        binding.btnStartRecord.clickWithTrigger {

        }
    }

    private fun showUseGuideDialog() {
        if (recordConfigDialog == null) {
            recordConfigDialog = RecordConfigDialog()
            recordConfigDialog?.show(supportFragmentManager, RECORD_CONFIG_DIALOG)
        } else {
            recordConfigDialog?.dialog?.let {
                it.show()
            } ?: kotlin.run {
                recordConfigDialog?.show(supportFragmentManager, RECORD_CONFIG_DIALOG)
            }
        }
    }

}