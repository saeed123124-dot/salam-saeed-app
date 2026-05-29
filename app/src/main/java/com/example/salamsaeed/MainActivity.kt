package com.example.salamsaeed

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        val textView = TextView(this).apply {
            text = "سلام سعید"
            textSize = 32f
        }

        val exitButton = Button(this).apply {
            text = "خروج"
            setOnClickListener {
                finish()
            }
        }

        layout.addView(textView)
        layout.addView(exitButton)
        setContentView(layout)
    }
}
