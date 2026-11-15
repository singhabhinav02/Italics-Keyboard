package ccom.abhinav.italics   // <- change to YOUR package name

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Simple setup screen: enable the keyboard, switch to it, and test it. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        layout.addView(TextView(this).apply {
            text = "𝘐𝘵𝘢𝘭𝘭𝘪𝘤𝘴 Keyboard\n\n1) Enable it  2) Select it  3) Type below"
            textSize = 18f
        })

        layout.addView(Button(this).apply {
            text = "1. Enable keyboard"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })

        layout.addView(Button(this).apply {
            text = "2. Select keyboard"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })

        layout.addView(EditText(this).apply {
            hint = "3. Test here"
        })

        setContentView(layout)
    }
}