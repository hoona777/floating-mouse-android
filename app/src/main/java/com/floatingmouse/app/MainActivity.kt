package com.floatingmouse.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
            setBackgroundColor(0xFF12131C.toInt())
        }

        val scrollView = ScrollView(this).apply {
            addView(rootLayout)
        }

        val title = TextView(this).apply {
            text = "موس شناور حرفه‌ای اندروید"
            textSize = 22f
            setTextColor(0xFF00E5FF.toInt())
            setPadding(0, 0, 0, 24)
        }

        val desc = TextView(this).apply {
            text = "برای فعالسازی و استفاده از موس شناور و پنل‌های کاملاً مستقل:"
            textSize = 14f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(0, 0, 0, 32)
        }

        val btnOverlay = Button(this).apply {
            text = "۱. اعطای مجوز پنجره شناور (Draw Overlays)"
            setOnClickListener { checkOverlayPermission() }
        }

        val btnService = Button(this).apply {
            text = "۲. فعالسازی سرویس دسترسی‌پذیری (Accessibility)"
            setOnClickListener { openAccessibilitySettings() }
        }

        val btnShowMouse = Button(this).apply {
            text = "۳. 🖱️ بازسازی و بازکردن مجدد موس شناور"
            setTextColor(0xFF00E5FF.toInt())
            setOnClickListener {
                val intent = Intent(this@MainActivity, FloatingMouseAccessibilityService::class.java).apply {
                    action = FloatingMouseAccessibilityService.ACTION_SHOW_MOUSE
                }
                startService(intent)
                Toast.makeText(this@MainActivity, "دستور بازسازی موس شناور ارسال شد", Toast.LENGTH_SHORT).show()
            }
        }

        val guideTitle = TextView(this).apply {
            text = "\n💡 ویژگی‌ها و راهنمای جابه‌جایی آیکون‌ها و مفاصل Mixamo:"
            textSize = 16f
            setTextColor(0xFFFF4081.toInt())
            setPadding(0, 24, 0, 12)
        }

        val guideText = TextView(this).apply {
            text = "• علت عملکرد فوق‌العاده دکمه 'فشار 🔒' (Touch Hold Engine):\n" +
                   "  دکمه فشار لمس را با مکانیزم continuousStroke و پارامتر willContinue روی صفحه قفل نگه می‌دارد و سیگنال‌های لمس زنده (ACTION_MOVE) ارسال می‌کند. به همین دلیل سیستم‌عامل و بوم WebGL میکسامو مفصل/آیکون را تحویل می‌گیرند.\n\n" +
                   "• اعمال مکانیزم 'فشار' روی سایر دکمه‌ها (به‌روزرسانی جدید):\n" +
                   "  ۱. دکمه 'گرفتن 📍': اکنون دقیقاً از همین موتور لمس زنده استفاده می‌کند. نوک فلش را روی مفصل Mixamo یا آیکون بگذارید، 'گرفتن 📍' را بزنید (انتهای پوینتر قفل می‌شود)، با تاچ‌پد حرکت دهید و 'رها 🎯' را بزنید تا مفصل رها شود.\n" +
                   "  ۲. دکمه 'چپ': به مدت ۱۲۰ms لمس عمیق ارسال می‌کند تا دکمه‌های WebGL مرورگر پاسخ دهند. همچنین اگر آن را نگه دارید، مانند کلیک چپ فیزیکی لمس را قفل می‌کند!\n" +
                   "  ۳. دکمه 'فشار 🔒': همچنان برای قفل کامل لمس و کشیدن مفاصل سه‌بعدی و آیکون‌ها آماده است."
            textSize = 13f
            setTextColor(0xFFCBD5E1.toInt())
            setLineSpacing(8f, 1.1f)
        }

        rootLayout.addView(title)
        rootLayout.addView(desc)
        rootLayout.addView(btnOverlay)
        rootLayout.addView(btnService)
        rootLayout.addView(btnShowMouse)
        rootLayout.addView(guideTitle)
        rootLayout.addView(guideText)

        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        if (FloatingMouseAccessibilityService.isServiceRunning) {
            val intent = Intent(this, FloatingMouseAccessibilityService::class.java).apply {
                action = FloatingMouseAccessibilityService.ACTION_SHOW_MOUSE
            }
            startService(intent)
        }
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
        } else {
            Toast.makeText(this, "مجوز پنجره شناور قبلا اعطا شده است", Toast.LENGTH_SHORT).show()
        }
    }
}
