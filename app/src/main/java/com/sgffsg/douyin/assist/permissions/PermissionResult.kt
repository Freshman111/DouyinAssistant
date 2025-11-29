package com.sgffsg.douyin.assist.permissions

/**
 * 权限请求结果
 */
data class PermissionResult(
    val permission: String,
    val isGranted: Boolean,
    val shouldShowRationale: Boolean = false
)

/**
 * 多个权限请求结果
 */
data class MultiPermissionResult(
    val allGranted: Boolean,
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>,
    val permanentlyDeniedPermissions: List<String>
)
