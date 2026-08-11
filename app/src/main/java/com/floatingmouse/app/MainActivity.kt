package com.floatingmouse.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var grantAccessibilityBtn: Button
    private lateinit var grantOverlayBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createSimpleLayout())

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun createSimpleLayout(): android.view.View {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#0D1B2A"))
        }

        val titleText = TextView(this).apply {
            text = "🖱️ موس شناور (Floating Mouse)"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(android.graphics.Color.parseColor("#00E5FF"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleText)

        statusText = TextView(this).apply {
            text = "در حال بررسی دسترسی‌ها..."
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(statusText)

        grantAccessibilityBtn = Button(this).apply {
            text = "۱. فعال‌سازی دسترسی دسترسی‌پذیری (Accessibility)"
            setOnClickListener { openAccessibilitySettings() }
        }
        layout.addView(grantAccessibilityBtn)

        grantOverlayBtn = Button(this).apply {
            text = "۲. فعال‌سازی دسترسی نمایش روی برنامه‌ها (Overlay)"
            setOnClickListener { openOverlaySettings() }
        }
        layout.addView(grantOverlayBtn)

        return layout
    }

    private fun checkAndRequestPermissions() {
        updateStatus()
    }

    private fun updateStatus() {
        val hasAccessibility = isAccessibilityServiceEnabled(this, FloatingMouseAccessibilityService::class.java)
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

        val sb = StringBuilder()
        if (hasAccessibility && hasOverlay) {
            sb.append("✅ همه دسترسی‌ها فعال هستند.\nبرنامه آماده استفاده است!")
        } else {
            sb.append("⚠️ لطفا دسترسی‌های زیر را فعال کنید:\n")
            if (!hasAccessibility) sb.append("• سرویس دسترسی‌پذیری (Accessibility)\n")
            if (!hasOverlay) sb.append("• نمایش روی سایر برنامه‌ها (Overlay)\n")
        }

        statusText.text = sb.toString()
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "امکان باز کردن تنظیمات نیست", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "امکان باز کردن تنظیمات نیست", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedService = "${context.packageName}/${serviceClass.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)

        while (splitter.hasNext()) {
            val service = splitter.next()
            if (service.equals(expectedService, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
