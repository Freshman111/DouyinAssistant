package com.sgffsg.douyin.assist.biz.asr.router

import com.didi.drouter.annotation.Service
import com.didi.drouter.api.Extend
import com.sgffsg.douyin.assist.biz.asr.AsrCommandListener
import com.sgffsg.douyin.assist.biz.asr.AsrDispatcher
import com.sgffsg.douyin.assist.biz.asr.VoiceCommand
import com.sgffsg.douyin.assist.biz.asr.listener.AsrListener
import com.sgffsg.douyin.assist.router.asr.IAsrMonitor
import com.sgffsg.douyin.assist.router.asr.IAsrMonitorProvider

/**
 * @author: lmk
 * @date: 2026/1/2
 * @desc:
 */
@Service(function = [IAsrMonitorProvider::class], cache = Extend.Cache.SINGLETON)
class AsrMonitorProviderImpl : IAsrMonitorProvider {

    private val asrMonitors = mutableListOf<IAsrMonitor>()

    init {
        AsrDispatcher.getInstance().addAsrListener(object : AsrListener {
            override fun onAsrRecognized(originalText: String) {
                for (asrMonitor in asrMonitors) {
                    asrMonitor.onAsr(originalText)
                }
            }

            override fun onError(error: String) {
                TODO("Not yet implemented")
            }

            override fun onStateChanged(isListening: Boolean) {
                TODO("Not yet implemented")
            }
        })
    }


    override fun registerMonitor(monitor: IAsrMonitor) {
        synchronized(this@AsrMonitorProviderImpl) {
            asrMonitors.add(monitor)
        }
    }

    override fun unRegisterMonitor(monitor: IAsrMonitor) {
        synchronized(this@AsrMonitorProviderImpl) {
            if (asrMonitors.contains(monitor)) {
                asrMonitors.remove(monitor)
            }
        }
    }
}