package com.sgffsg.douyin.assist.ui.play

import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.widget.Toast
import com.sgffsg.douyin.assist.R
import com.sgffsg.douyin.assist.databinding.ActivityPlayOnlineAudioBinding
import com.sgffsg.douyin.assist.ui.binding.BaseBindingActivity
import com.sgffsg.douyin.assist.util.LogUtil

class PlayOnlineAudioActivity : BaseBindingActivity<ActivityPlayOnlineAudioBinding>() {
    var isPaused = false
    var isPrepared = false
    val player by lazy {
        MediaPlayer()
    }

    override fun bind(): ActivityPlayOnlineAudioBinding {
        return ActivityPlayOnlineAudioBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        //在线Demo
        //http://music.163.com/song/media/outer/url?id=447925558.mp3
//        binding.editSourceUrl.setText("http://music.163.com/song/media/outer/url?id=447925558.mp3")
        binding.btnPlay.setOnClickListener {
            if (player.isPlaying) {
                binding.btnPlay.setText(R.string.play)
                pause()
            } else {
                binding.btnPlay.setText(R.string.pause)
                if (isPaused) {
                    play()
                } else {
                    if (isPrepared) {
                        play()
                    } else {
                        val sourceUrl = binding.editSourceUrl.text.toString()
                        if (sourceUrl.isBlank()) {
                            Toast.makeText(this, "请输入播放音频源地址", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        initPlayer(sourceUrl)
                    }
                }
            }
        }
        binding.btnStop.setOnClickListener {
            stop()
        }
    }

    private fun initPlayer(sourceUrl: String) {
        player.setOnPreparedListener {
            isPrepared = true
            LogUtil.i("PlayOnlineAudio", "onCreate: onPrepared")
            try {
                player.playbackParams = PlaybackParams().setSpeed(1.0f)
            } catch (e: IllegalArgumentException) {
                LogUtil.i("PlayOnlineAudio", "onPrepared: ${e.message}")
                e.printStackTrace();
            }
            play()
        }
        player.setOnCompletionListener {
            LogUtil.i("PlayOnlineAudio", "onCreate: onCompletion")
            isPaused = false
            binding.btnPlay.setText(R.string.play)
        }
        player.setOnErrorListener { mp, what, extra ->
            LogUtil.e("PlayOnlineAudio", "onCreate: onError")
            return@setOnErrorListener false
        }
        player.reset()
        player.setDataSource(sourceUrl)
        player.setAudioStreamType(AudioManager.STREAM_MUSIC)
        player.prepareAsync()
    }

    private fun play() {
        player.start()
    }

    private fun pause() {
        player.pause()
        isPaused = true
    }

    private fun stop() {
        player.stop()
        player.release()
    }

}