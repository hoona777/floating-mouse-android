package com.floatingmouse.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        val btnOverlay = findViewById<Button>(R.id.btnOverlayPermission)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibilityPermission)
        val btnStart = findViewById<Button>(R.id.btnStartService)

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "مجوز پنجره شناور قبلاً اعطا شده است", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "لطفاً سرویس 'موس شناور' را در لیست فعال کنید", Toast.LENGTH_LONG).show()
        }

        btnStart.setOnClickListener {
            checkAndRefreshStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRefreshStatus()
    }

    private fun checkAndRefreshStatus() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val hasAccessibility = isAccessibilityServiceEnabled()

        if (hasOverlay && hasAccessibility) {
            statusText.text = "سرویس فعال و آماده استفاده است ✅"
            statusText.setTextColor(resources.getColor(android.R.color.holo_green_light))
        } else {
            val missing = mutableListOf<String>()
            if (!hasOverlay) missing.add("پنجره شناور (Overlay)")
            if (!hasAccessibility) missing.add("دسترسی‌پذیری (Accessibility)")
            statusText.text = "نیازمند مجوزهای: ${missing.joinToString("، ")}"
            statusText.setTextColor(resources.getColor(android.R.color.holo_red_light))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${FloatingMouseAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(expectedService)
    }
}
