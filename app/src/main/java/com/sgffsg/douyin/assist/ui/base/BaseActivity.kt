package com.sgffsg.douyin.assist.ui.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.sgffsg.douyin.assist.util.LogUtil

/**
 * Base Activity
 *
 * @author lizhi, Guo Jinyu
 */
abstract class BaseActivity : AppCompatActivity() {

    private val TAG = "BaseActivity"

    private var onCreate: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        onCreate = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        LogUtil.i(TAG, "$this onCreate")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        LogUtil.i(TAG, "$this onNewIntent")
    }

    override fun onRestart() {
        super.onRestart()
        LogUtil.i(TAG, "$this onRestart")
    }

    override fun onStart() {
        super.onStart()
        LogUtil.i(TAG, "$this onStart")
    }

    override fun onResume() {
        super.onResume()
//        LogUtil.d(TAG, "$this onResume")
        if (onCreate > 0) {
            LogUtil.i("ActivityReadyTime", "${this.javaClass.name} use time = ${System.currentTimeMillis() - onCreate}")
            onCreate = -1
        }
    }

    override fun onPause() {
        super.onPause()
//        LogUtil.d(TAG, "$this onPause")
    }

    override fun onStop() {
        super.onStop()
        LogUtil.i(TAG, "$this onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.i(TAG, "$this onDestroy")
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