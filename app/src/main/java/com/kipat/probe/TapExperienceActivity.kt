package com.kipat.probe

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class TapExperienceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tap_experience)

        val btnDismiss = findViewById<Button>(R.id.btn_dismiss)
        btnDismiss?.setOnClickListener {
            finish()
        }

        val btnConnect = findViewById<Button>(R.id.btn_connect)
        btnConnect?.setOnClickListener {
            // Handle connect action here
            finish()
        }
    }
}