package com.sgffsg.douyin.assist.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sgffsg.douyin.assist.util.LogUtil

/**
 * 权限工具类
 * 提供权限检查、请求、跳转设置等功能
 */
object PermissionUtil {

    private const val TAG = "PermissionUtil"

    /**
     * 检查单个权限是否已授予
     */
    @JvmStatic
    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查多个权限是否全部已授予
     */
    @JvmStatic
    fun isAllGranted(context: Context, vararg permissions: String): Boolean {
        return permissions.all { permission ->
            isGranted(context, permission)
        }
    }

    /**
     * 检查多个权限是否有任意一个已授予
     */
    @JvmStatic
    fun isAnyGranted(context: Context, vararg permissions: String): Boolean {
        return permissions.any { permission ->
            isGranted(context, permission)
        }
    }

    /**
     * 获取已授予的权限列表
     */
    @JvmStatic
    fun getGrantedPermissions(context: Context, vararg permissions: String): List<String> {
        return permissions.filter { permission ->
            isGranted(context, permission)
        }
    }

    /**
     * 获取未授予的权限列表
     */
    @JvmStatic
    fun getDeniedPermissions(context: Context, vararg permissions: String): List<String> {
        return permissions.filter { permission ->
            !isGranted(context, permission)
        }
    }

    /**
     * 跳转到应用设置页面
     */
    @JvmStatic
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            LogUtil.d(TAG, "跳转到应用设置页面")
        } catch (e: Exception) {
            LogUtil.e(TAG, "跳转应用设置失败: ${e.message}")
        }
    }

    /**
     * 检查存储权限
     */
    @JvmStatic
    fun checkStoragePermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11 及以上
            android.os.Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下
            isAllGranted(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * 请求存储权限
     */
    @JvmStatic
    fun requestStoragePermission(activity: FragmentActivity): PermissionX {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11 及以上需要特殊处理
            PermissionX.with(activity)
                .permissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
        } else {
            PermissionX.with(activity)
                .permissions(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
        }
    }

    /**
     * 检查相机权限
     */
    @JvmStatic
    fun checkCameraPermission(context: Context): Boolean {
        return isGranted(context, Manifest.permission.CAMERA)
    }

    /**
     * 请求相机权限
     */
    @JvmStatic
    fun requestCameraPermission(activity: FragmentActivity): PermissionX {
        return PermissionX.with(activity)
            .permission(Manifest.permission.CAMERA)
    }

    /**
     * 检查录音权限
     */
    @JvmStatic
    fun checkRecordAudioPermission(context: Context): Boolean {
        return isGranted(context, Manifest.permission.RECORD_AUDIO)
    }

    /**
     * 请求录音权限
     */
    @JvmStatic
    fun requestRecordAudioPermission(activity: FragmentActivity): PermissionX {
        return PermissionX.with(activity)
            .permission(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * 检查位置权限
     */
    @JvmStatic
    fun checkLocationPermission(context: Context): Boolean {
        return isAnyGranted(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * 请求位置权限
     */
    @JvmStatic
    fun requestLocationPermission(activity: FragmentActivity): PermissionX {
        return PermissionX.with(activity)
            .permissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
    }

    /**
     * 检查联系人权限
     */
    @JvmStatic
    fun checkContactsPermission(context: Context): Boolean {
        return isGranted(context, Manifest.permission.READ_CONTACTS)
    }

    /**
     * 请求联系人权限
     */
    @JvmStatic
    fun requestContactsPermission(activity: FragmentActivity): PermissionX {
        return PermissionX.with(activity)
            .permission(Manifest.permission.READ_CONTACTS)
    }

    /**
     * 获取权限的友好名称
     */
    @JvmStatic
    fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "相机"
            Manifest.permission.RECORD_AUDIO -> "录音"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "读取存储"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "写入存储"
            Manifest.permission.ACCESS_FINE_LOCATION -> "精确定位"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "粗略定位"
            Manifest.permission.READ_CONTACTS -> "读取联系人"
            Manifest.permission.WRITE_CONTACTS -> "写入联系人"
            Manifest.permission.READ_PHONE_STATE -> "读取手机状态"
            Manifest.permission.CALL_PHONE -> "拨打电话"
            Manifest.permission.READ_SMS -> "读取短信"
            Manifest.permission.SEND_SMS -> "发送短信"
            else -> permission.substringAfterLast('.')
        }
    }

    /**
     * 格式化权限列表为友好名称
     */
    @JvmStatic
    fun formatPermissionNames(permissions: List<String>): String {
        return permissions.joinToString(", ") { getPermissionName(it) }
    }
}
