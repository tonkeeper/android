package com.tonapps.signer.screen.crash

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.tonapps.signer.App
import com.tonapps.signer.R
import com.tonapps.signer.extensions.copyToClipboard
import uikit.base.BaseActivity
import java.io.PrintWriter
import java.io.StringWriter

class CrashActivity: BaseActivity() {

    companion object {
        private const val EXTRA_STACK_TRACE = "EXTRA_STACK_TRACE"

        fun open(e: Throwable, context: Context = App.instance) {
            val mainLooper = Looper.getMainLooper()
            if (!mainLooper.isCurrentThread) {
                Handler(Looper.getMainLooper()).post {
                    open(e, context)
                }
                return
            }

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            val stackTraceString = sw.toString()

            val intent = Intent(context, CrashActivity::class.java)
            intent.putExtra(EXTRA_STACK_TRACE, stackTraceString)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }
    }

    private val stack: String by lazy { intent.getStringExtra(EXTRA_STACK_TRACE) ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)
        val stackView = findViewById<TextView>(R.id.stack)
        stackView.text = stack

        val copyButton = findViewById<TextView>(R.id.copy)
        copyButton.setOnClickListener {
            copyToClipboard(stack)
        }
    }
}