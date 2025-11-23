package com.sgffsg.douyin.assist

import android.content.Intent
import com.sgffsg.douyin.assist.databinding.ActivityMainBinding
import com.sgffsg.douyin.assist.ui.binding.BaseBindingActivity
import com.sgffsg.douyin.assist.ui.business.AssistSettingActivity
import com.sgffsg.douyin.assist.ui.demo.VoskActivity
import com.sgffsg.douyin.assist.ui.play.PlayOnlineAudioActivity
import com.sgffsg.douyin.assist.ui.record.AudioRecordActivity

class MainActivity : BaseBindingActivity<ActivityMainBinding>() {

    override fun bind(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        binding.btnMediaRecordAudio.setOnClickListener {
            val intent = Intent(MainActivity@ this, AudioRecordActivity::class.java)
            startActivity(intent)
        }
        binding.btnMediaPlayAudio.setOnClickListener {
            val intent = Intent(MainActivity@ this, PlayOnlineAudioActivity::class.java)
            startActivity(intent)
        }
        binding.btnVoskDemo.setOnClickListener {
            val intent = Intent(MainActivity@ this, VoskActivity::class.java)
            startActivity(intent)
        }
        binding.btnAssistSetting.setOnClickListener {
            val intent = Intent(MainActivity@ this, AssistSettingActivity::class.java)
            startActivity(intent)
        }
    }

}