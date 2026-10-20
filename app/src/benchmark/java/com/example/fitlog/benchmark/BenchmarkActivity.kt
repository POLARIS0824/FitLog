package com.example.fitlog.benchmark

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fitlog.R

/** A stable foreground host; leaving it cancels the measurement rather than hiding background bias. */
class BenchmarkActivity : Activity() {
    internal var stopMeasurement: (() -> Unit)? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(this).apply { setText(R.string.benchmark_running); textSize = 22f })
        layout.addView(TextView(this).apply { setText(R.string.benchmark_keep_foreground); textSize = 16f })
        layout.addView(Button(this).apply {
            setText(R.string.benchmark_cancel)
            setOnClickListener { isEnabled = false; stopMeasurement?.invoke() }
        })
        setContentView(layout)
    }

    override fun onStop() {
        if (!completed) stopMeasurement?.invoke()
        super.onStop()
    }

    internal fun finishMeasurement() {
        completed = true
        stopMeasurement = null
        finish()
    }
}
