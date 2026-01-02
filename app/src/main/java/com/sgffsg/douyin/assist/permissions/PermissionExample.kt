package com.sgffsg.douyin.assist.permissions

import android.Manifest
import androidx.fragment.app.FragmentActivity
import com.sgffsg.douyin.assist.common.utils.LogUtil

/**
 * 权限工具使用示例
 * 展示各种常见的权限请求场景
 */
object PermissionExample {

    private const val TAG = "PermissionExample"

    /**
     * 示例1: 简单的单个权限请求
     */
    fun requestSinglePermission(activity: FragmentActivity) {
        PermissionX.with(activity)
            .permission(Manifest.permission.CAMERA)
            .onGranted {
                LogUtil.d(TAG, "相机权限已授予")
                // 执行需要相机权限的操作
            }
            .onDenied { deniedList ->
                LogUtil.w(TAG, "权限被拒绝: ${deniedList.joinToString()}")
                // 提示用户权限被拒绝
            }
            .request()
    }

    /**
     * 示例2: 多个权限请求
     */
    fun requestMultiplePermissions(activity: FragmentActivity) {
        PermissionX.with(activity)
            .permissions(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .onGranted {
                LogUtil.d(TAG, "所有权限已授予")
            }
            .onDenied { deniedList ->
                LogUtil.w(TAG, "部分权限被拒绝: ${deniedList.joinToString()}")
            }
            .request()
    }

    /**
     * 示例3: 处理永久拒绝的权限
     */
    fun requestWithPermanentDenied(activity: FragmentActivity) {
        PermissionX.with(activity)
            .permission(Manifest.permission.CAMERA)
            .onGranted {
                LogUtil.d(TAG, "权限已授予")
            }
            .onDenied { deniedList ->
                LogUtil.w(TAG, "权限被拒绝: ${deniedList.joinToString()}")
            }
            .onPermanentlyDenied { permanentlyDeniedList ->
                LogUtil.e(TAG, "权限被永久拒绝: ${permanentlyDeniedList.joinToString()}")
                // 引导用户去设置页面开启权限
                PermissionUtil.openAppSettings(activity)
            }
            .request()
    }

    /**
     * 示例4: 获取完整的权限结果
     */
    fun requestWithDetailResult(activity: FragmentActivity) {
        PermissionX.with(activity)
            .permissions(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            .onResult { result ->
                LogUtil.d(TAG, "所有权限已授予: ${result.allGranted}")
                LogUtil.d(TAG, "已授予: ${result.grantedPermissions}")
                LogUtil.d(TAG, "被拒绝: ${result.deniedPermissions}")
                LogUtil.d(TAG, "永久拒绝: ${result.permanentlyDeniedPermissions}")
                
                // 根据结果执行不同操作
                if (result.allGranted) {
                    // 所有权限都授予了
                } else if (result.permanentlyDeniedPermissions.isNotEmpty()) {
                    // 有权限被永久拒绝
                    PermissionUtil.openAppSettings(activity)
                }
            }
            .request()
    }

    /**
     * 示例5: 添加权限说明
     */
    fun requestWithRationale(activity: FragmentActivity) {
        PermissionX.with(activity)
            .permission(Manifest.permission.CAMERA)
            .rationale("我们需要相机权限来拍摄照片")
            .onGranted {
                LogUtil.d(TAG, "权限已授予")
            }
            .request()
    }

    /**
     * 示例6: 自定义权限说明对话框
     */
    fun requestWithCustomRationale(activity: FragmentActivity) {
        PermissionX.with(activity)
            .permission(Manifest.permission.CAMERA)
            .rationaleHandler { deniedPermissions, continueRequest ->
                // 显示自定义对话框
                val permissionNames = PermissionUtil.formatPermissionNames(deniedPermissions)
                LogUtil.d(TAG, "需要请求权限: $permissionNames")
                
                // 用户点击继续后调用 continueRequest()
                continueRequest()
            }
            .onGranted {
                LogUtil.d(TAG, "权限已授予")
            }
            .request()
    }

    /**
     * 示例7: 使用工具类方法请求常见权限
     */
    fun requestCommonPermissions(activity: FragmentActivity) {
        // 请求存储权限
        PermissionUtil.requestStoragePermission(activity)
            .onGranted {
                LogUtil.d(TAG, "存储权限已授予")
            }
            .request()

        // 请求相机权限
        PermissionUtil.requestCameraPermission(activity)
            .onGranted {
                LogUtil.d(TAG, "相机权限已授予")
            }
            .request()

        // 请求录音权限
        PermissionUtil.requestRecordAudioPermission(activity)
            .onGranted {
                LogUtil.d(TAG, "录音权限已授予")
            }
            .request()
    }

    /**
     * 示例8: 检查权限状态
     */
    fun checkPermissionStatus(activity: FragmentActivity) {
        // 检查单个权限
        val hasCameraPermission = PermissionUtil.isGranted(activity, Manifest.permission.CAMERA)
        LogUtil.d(TAG, "是否有相机权限: $hasCameraPermission")

        // 检查多个权限是否全部授予
        val hasAllPermissions = PermissionUtil.isAllGranted(
            activity,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        LogUtil.d(TAG, "是否有所有权限: $hasAllPermissions")

        // 检查是否有任意权限
        val hasAnyPermission = PermissionUtil.isAnyGranted(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        LogUtil.d(TAG, "是否有定位权限: $hasAnyPermission")

        // 获取已授予的权限列表
        val grantedPermissions = PermissionUtil.getGrantedPermissions(
            activity,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        LogUtil.d(TAG, "已授予的权限: $grantedPermissions")
    }
}
