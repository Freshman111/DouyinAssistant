package com.sgffsg.douyin.assist.permissions

/**
 * Android权限管理接口类
 *
 * @author lizhi
 */
interface PermissionsInterface {
    val needPermissions: Array<String>?
    fun onPermissionGrantedSuccess()
    fun onPermissionGrantedFailed()
}