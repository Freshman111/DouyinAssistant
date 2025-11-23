package com.sgffsg.douyin.assist.ui.binding

import android.os.Bundle
import androidx.viewbinding.ViewBinding
import com.sgffsg.douyin.assist.ui.base.BaseActivity

/**
 * 支持ViewBinding的Base Activity
 *
 * @author Guo Jinyu
 */
abstract class BaseBindingActivity<T : ViewBinding> : BaseActivity() {

    protected val binding by lazy(LazyThreadSafetyMode.NONE) { bind() }

    private var needSetContentView = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (needSetContentView) {
            setContentView(binding.root)
        }
    }

    protected abstract fun bind(): T

    fun requireNotSetContentView(notSet: Boolean) {
        this.needSetContentView = !notSet
    }
}