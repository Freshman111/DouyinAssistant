package com.sgffsg.douyin.assist.permissions

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.sgffsg.douyin.assist.common.utils.LogUtil

/**
 * 隐藏的 Fragment，用于处理权限请求
 * 避免在 Activity 中直接处理权限请求回调
 */
class PermissionFragment : Fragment() {

    private val TAG = "PermissionFragment"
    
    private var permissionsToRequest: Array<String> = emptyArray()
    private var rationaleMessage: String? = null
    private var callback: PermissionCallback? = null

    companion object {
        const val TAG = "PermissionFragment"
        private const val REQUEST_CODE = 100
        private const val ARG_PERMISSIONS = "permissions"
        private const val ARG_RATIONALE = "rationale"

        fun newInstance(permissions: Array<String>, rationale: String?): PermissionFragment {
            return PermissionFragment().apply {
                arguments = Bundle().apply {
                    putStringArray(ARG_PERMISSIONS, permissions)
                    putString(ARG_RATIONALE, rationale)
                }
            }
        }
    }

    interface PermissionCallback {
        fun onPermissionsResult(
            grantedPermissions: List<String>,
            deniedPermissions: List<String>,
            permanentlyDeniedPermissions: List<String>
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        permissionsToRequest = arguments?.getStringArray(ARG_PERMISSIONS) ?: emptyArray()
        rationaleMessage = arguments?.getString(ARG_RATIONALE)

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest, REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_CODE) {
            return
        }

        val grantedPermissions = mutableListOf<String>()
        val deniedPermissions = mutableListOf<String>()
        val permanentlyDeniedPermissions = mutableListOf<String>()

        permissions.forEachIndexed { index, permission ->
            if (grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED) {
                grantedPermissions.add(permission)
                LogUtil.d(TAG, "权限已授予: $permission")
            } else {
                // 检查是否永久拒绝
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    permission
                )
                
                if (!shouldShowRationale) {
                    permanentlyDeniedPermissions.add(permission)
                    LogUtil.w(TAG, "权限被永久拒绝: $permission")
                } else {
                    deniedPermissions.add(permission)
                    LogUtil.w(TAG, "权限被拒绝: $permission")
                }
            }
        }

        callback?.onPermissionsResult(
            grantedPermissions,
            deniedPermissions,
            permanentlyDeniedPermissions
        )

        // 移除自己
        removeSelf()
    }

    fun setCallback(callback: PermissionCallback) {
        this.callback = callback
    }

    private fun removeSelf() {
        parentFragmentManager
            .beginTransaction()
            .remove(this)
            .commitAllowingStateLoss()
    }
}
