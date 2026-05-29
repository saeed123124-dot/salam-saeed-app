package com.example.salamsaeed

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 100, 50, 100)
        }

        val textView = TextView(this).apply {
            text = "سلام سعید"
            textSize = 32f
            gravity = Gravity.CENTER
        }

        val exitButton = Button(this).apply {
            text = "خروج"
            textSize = 18f
            setOnClickListener {
                finish()
            }
        }

        layout.addView(textView)
        layout.addView(exitButton)

        setContentView(layout)
    }
}
