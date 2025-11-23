package com.sgffsg.douyin.assist.ui.mvvm

import android.os.Bundle
import androidx.viewbinding.ViewBinding
import com.sgffsg.douyin.assist.ui.base.PermissionActivity

/**
 * MVVM架构下的Base Activity类
 *
 * @author Guo Jinyu
 */
abstract class MvvmActivity<T : ViewBinding> : PermissionActivity<T>() {

    override val needPermissions: Array<String>?
        get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initObserver()
    }

    protected abstract fun initObserver()

}