package com.sgffsg.douyin.assist.router.asr

/**
 * @author: lmk
 * @date: 2026/1/2
 * @desc:
 */
interface IAsrMonitorProvider {
    fun registerMonitor(monitor: IAsrMonitor)

    fun unRegisterMonitor(monitor: IAsrMonitor)
}