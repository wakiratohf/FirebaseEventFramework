package com.tohsoft.ads.views

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.DialogFragment

class ProgressDialogFragment(customProgressView: View? = null) : DialogFragment() {
    private var mDialog: Dialog? = null
    private var mCustomProgressView: View? = customProgressView

    @SuppressLint("UseKtx")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        try {
            mDialog = Dialog(requireActivity())
            mDialog?.apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setCancelable(false)
                mCustomProgressView?.let { setContentView(it) }

                val lp = WindowManager.LayoutParams()
                lp.copyFrom(mDialog!!.window!!.attributes)
                lp.width = WindowManager.LayoutParams.MATCH_PARENT
                lp.height = WindowManager.LayoutParams.MATCH_PARENT
                lp.gravity = Gravity.CENTER
                window?.attributes = lp
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mDialog = Dialog(requireActivity())
        }
        return mDialog!!
    }

    override fun dismiss() {
        try {
            getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss()
            requireActivity().supportFragmentManager.popBackStack()
            mDialog!!.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
