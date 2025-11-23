package com.sgffsg.douyin.assist.ui.dialog

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.sgffsg.douyin.assist.R
import com.sgffsg.douyin.assist.databinding.DialogRecordConfigBinding

/**
 * @author lmk
 * @desc 录音配置对话框
 */
class RecordConfigDialog : DialogFragment() {
    private lateinit var binding: DialogRecordConfigBinding

    private var mWebLoadError = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogRecordConfigBinding.inflate(inflater, container, false)
        initDialogParams()
        //设置外部不可点击隐藏对话框
        isCancelable = false
        initView()
        return binding.root
    }


    private fun initView() {
        activity?.let {
//            binding.ivGuideClose.clickWithTrigger {
//                dismiss()
//            }
//            showUserLoading()
//            binding.useGuideWeb.startTimer()
        }
    }

    private fun initDialogParams() {
        val window: Window? = dialog?.window
        //设置了窗口的背景色为透明，这一步是必须的
        window?.decorView?.setPadding(0, 0, 0, 0)
        window?.setBackgroundDrawableResource(R.color.transparent)
        val wlp: WindowManager.LayoutParams? = window?.attributes
        wlp?.gravity = Gravity.CENTER
        wlp?.width = resources.getDimensionPixelSize(R.dimen.dimen_dp_570)
        wlp?.height = resources.getDimensionPixelSize(R.dimen.dimen_dp_360)
        window?.attributes = wlp
    }


}