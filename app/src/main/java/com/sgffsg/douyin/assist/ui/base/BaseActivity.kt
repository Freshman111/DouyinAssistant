package com.sgffsg.douyin.assist.ui.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

/**
 * Base Activity
 *
 * @author lizhi, Guo Jinyu
 */
abstract class BaseActivity : AppCompatActivity() {

    private var onCreate: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        onCreate = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        Timber.d("$this onCreate")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.d("$this onNewIntent")
    }

    override fun onRestart() {
        super.onRestart()
        Timber.d("$this onRestart")
    }

    override fun onStart() {
        super.onStart()
        Timber.d("$this onStart")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("$this onResume")
        if (onCreate > 0) {
            Timber.tag("ActivityReadyTime: (onCreate - onResume)")
                .i("${this.javaClass.name} use time = ${System.currentTimeMillis() - onCreate}")
            onCreate = -1
        }
    }

    override fun onPause() {
        super.onPause()
        Timber.d("$this onPause")
    }

    override fun onStop() {
        super.onStop()
        Timber.d("$this onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$this onDestroy")
    }

    override fun setContentView(@LayoutRes layoutResID: Int) {
        super.setContentView(layoutResID)
        initView()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        initView()
    }

    override fun setContentView(
        view: View,
        params: ViewGroup.LayoutParams
    ) {
        super.setContentView(view, params)
        initView()
    }

    protected open fun initView() {}

}