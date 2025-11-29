package com.sgffsg.douyin.assist.permissions

import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.sgffsg.douyin.assist.util.LogUtil

/**
 * 权限请求构建器
 * 支持链式调用，处理权限申请和结果回调
 */
class PermissionX private constructor(
    private val activity: FragmentActivity
) {

    private val TAG = "PermissionX"
    
    private val permissions = mutableListOf<String>()
    private var onGranted: (() -> Unit)? = null
    private var onDenied: ((List<String>) -> Unit)? = null
    private var onPermanentlyDenied: ((List<String>) -> Unit)? = null
    private var onResult: ((MultiPermissionResult) -> Unit)? = null
    private var rationaleMessage: String? = null
    private var rationaleAction: ((List<String>, () -> Unit) -> Unit)? = null

    companion object {
        /**
         * 创建权限请求
         */
        fun with(activity: FragmentActivity): PermissionX {
            return PermissionX(activity)
        }

        /**
         * 创建权限请求（从 Fragment）
         */
        fun with(fragment: Fragment): PermissionX {
            return PermissionX(fragment.requireActivity())
        }
    }

    /**
     * 添加单个权限
     */
    fun permission(permission: String): PermissionX {
        if (!permissions.contains(permission)) {
            permissions.add(permission)
        }
        return this
    }

    /**
     * 添加多个权限
     */
    fun permissions(vararg permissions: String): PermissionX {
        permissions.forEach { permission ->
            if (!this.permissions.contains(permission)) {
                this.permissions.add(permission)
            }
        }
        return this
    }

    /**
     * 添加多个权限（列表）
     */
    fun permissions(permissions: List<String>): PermissionX {
        permissions.forEach { permission ->
            if (!this.permissions.contains(permission)) {
                this.permissions.add(permission)
            }
        }
        return this
    }

    /**
     * 所有权限都授予时的回调
     */
    fun onGranted(action: () -> Unit): PermissionX {
        this.onGranted = action
        return this
    }

    /**
     * 有权限被拒绝时的回调
     */
    fun onDenied(action: (List<String>) -> Unit): PermissionX {
        this.onDenied = action
        return this
    }

    /**
     * 有权限被永久拒绝时的回调
     */
    fun onPermanentlyDenied(action: (List<String>) -> Unit): PermissionX {
        this.onPermanentlyDenied = action
        return this
    }

    /**
     * 完整的结果回调
     */
    fun onResult(action: (MultiPermissionResult) -> Unit): PermissionX {
        this.onResult = action
        return this
    }

    /**
     * 设置权限说明信息
     */
    fun rationale(message: String): PermissionX {
        this.rationaleMessage = message
        return this
    }

    /**
     * 自定义权限说明处理
     */
    fun rationaleHandler(action: (List<String>, () -> Unit) -> Unit): PermissionX {
        this.rationaleAction = action
        return this
    }

    /**
     * 发起权限请求
     */
    fun request() {
        if (permissions.isEmpty()) {
            LogUtil.w(TAG, "没有需要请求的权限")
            onGranted?.invoke()
            return
        }

        // 检查已授予的权限
        val deniedPermissions = permissions.filter { permission ->
            !isPermissionGranted(permission)
        }

        if (deniedPermissions.isEmpty()) {
            LogUtil.d(TAG, "所有权限已授予")
            onGranted?.invoke()
            onResult?.invoke(
                MultiPermissionResult(
                    allGranted = true,
                    grantedPermissions = permissions,
                    deniedPermissions = emptyList(),
                    permanentlyDeniedPermissions = emptyList()
                )
            )
            return
        }

        // 检查是否需要显示权限说明
        val shouldShowRationale = deniedPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        if (shouldShowRationale && rationaleAction != null) {
            // 自定义权限说明处理
            rationaleAction?.invoke(deniedPermissions) {
                performRequest(deniedPermissions)
            }
        } else {
            // 直接请求权限
            performRequest(deniedPermissions)
        }
    }

    /**
     * 执行权限请求
     */
    private fun performRequest(permissionsToRequest: List<String>) {
        val fragment = PermissionFragment.newInstance(
            permissionsToRequest.toTypedArray(),
            rationaleMessage
        )

        fragment.setCallback(object : PermissionFragment.PermissionCallback {
            override fun onPermissionsResult(
                grantedPermissions: List<String>,
                deniedPermissions: List<String>,
                permanentlyDeniedPermissions: List<String>
            ) {
                handleResult(grantedPermissions, deniedPermissions, permanentlyDeniedPermissions)
            }
        })

        activity.supportFragmentManager
            .beginTransaction()
            .add(fragment, PermissionFragment.TAG)
            .commitNowAllowingStateLoss()
    }

    /**
     * 处理权限请求结果
     */
    private fun handleResult(
        grantedPermissions: List<String>,
        deniedPermissions: List<String>,
        permanentlyDeniedPermissions: List<String>
    ) {
        val allGranted = deniedPermissions.isEmpty() && permanentlyDeniedPermissions.isEmpty()

        LogUtil.d(TAG, "权限请求结果 - 授予: ${grantedPermissions.size}, 拒绝: ${deniedPermissions.size}, 永久拒绝: ${permanentlyDeniedPermissions.size}")

        // 回调完整结果
        onResult?.invoke(
            MultiPermissionResult(
                allGranted = allGranted,
                grantedPermissions = grantedPermissions,
                deniedPermissions = deniedPermissions,
                permanentlyDeniedPermissions = permanentlyDeniedPermissions
            )
        )

        // 回调具体状态
        when {
            allGranted -> {
                onGranted?.invoke()
            }
            permanentlyDeniedPermissions.isNotEmpty() -> {
                onPermanentlyDenied?.invoke(permanentlyDeniedPermissions)
            }
            deniedPermissions.isNotEmpty() -> {
                onDenied?.invoke(deniedPermissions)
            }
        }
    }

    /**
     * 检查权限是否已授予
     */
    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
