package com.floatingmouse.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 96)
            setBackgroundColor(0xFF12131C.toInt())
        }

        val title = TextView(this).apply {
            text = "موس شناور حرفه‌ای اندروید"
            textSize = 22f
            setTextColor(0xFF00E5FF.toInt())
            setPadding(0, 0, 0, 32)
        }

        val desc = TextView(this).apply {
            text = "جهت فعال‌سازی نشانگر و کپسول شناور روی کل صفحه گوشی، لطفا ۲ مجوز زیر را فعال کنید:"
            textSize = 14f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(0, 0, 0, 48)
        }

        val btnOverlay = Button(this).apply {
            text = "۱. اعطای مجوز پنجره شناور (Draw Overlays)"
            setOnClickListener { checkOverlayPermission() }
        }

        val btnService = Button(this).apply {
            text = "۲. فعال‌سازی سرویس دسترسی‌پذیری (Accessibility)"
            setOnClickListener { openAccessibilitySettings() }
        }

        layout.addView(title)
        layout.addView(desc)
        layout.addView(btnOverlay)
        layout.addView(btnService)

        setContentView(layout)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
