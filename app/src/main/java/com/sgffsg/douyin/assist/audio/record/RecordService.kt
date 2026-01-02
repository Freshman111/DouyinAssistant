package com.sgffsg.douyin.assist.audio.record

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.sgffsg.douyin.assist.common.utils.LogUtil

/**
 * @author: lmk
 * @date: 2024/5/08
 * @desc: 录音服务
 */
class RecordService : Service() {

    private val localReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            LogUtil.i("RecordService", "hideReceiver Initializer ---onReceive ${intent?.action}")
            if (context != null) {

            }
        }
    }

    companion object {
        const val KEY_FROM = "from"
        const val FROM_WAKE_UP = "wake_up"
    }

    override fun onCreate() {
        LogUtil.d("RecordService", "onCreate   ===>")
        super.onCreate()
        registerHideReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra(KEY_FROM) == FROM_WAKE_UP) {
            //唤醒语音助手
//            showVoiceDialog()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.e("RecordService", "Initializer ---onDestroy unRegisterReceivers")
        unRegisterReceiver()
    }

    /**
     * 注册关闭广播
     */
    private fun registerHideReceiver() {
//        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
//        val intentFilter = IntentFilter()
//        intentFilter.addAction(BROADCAST_ACTION_HIDE_VOICE_ASSIST)
//        intentFilter.addAction(BROADCAST_ACTION_CONTROL_ASSIST)
//        localBroadcastManager.registerReceiver(localReceiver, intentFilter)
    }

    private fun unRegisterReceiver() {
//        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
//        localBroadcastManager.unregisterReceiver(localReceiver)
    }

}