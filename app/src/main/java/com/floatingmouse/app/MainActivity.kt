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
            text = "• علت عدم کارکرد قبلی در Mixamo مرورگر Chrome:\n" +
                   "  نگه داشتن ثابت لمس باعث می‌شد مرورگر Chrome منوی متن/تصویر خود را باز کند و کشیدن بوم سه‌بعدی را قفل کند.\n\n" +
                   "• نحوه علامت‌گذاری و ریگینگ در سایت Mixamo (۲ روش کاملاً عملی):\n" +
                   "  روش ۱ (کشیدن زنده 📍): نوک پوینتر را روی مارکر Mixamo (مثلاً مچ یا زانو) بگذارید، دکمه 'گرفتن 📍' را بزنید. سیستم یک میکرو‌حرکت ۲px ارسال می‌کند تا منوی مرورگر ابطال شود. سپس با تاچ‌پد مارکر را روی مفصل کاراکتر ببرید و 'رها 🎯' را بزنید.\n\n" +
                   "  روش ۲ (انتخاب با تک‌کلیک ۶۰ms 🎯): نوک پوینتر را روی مارکر بگذارید و دکمه 'تک‌کلیک WebGL 🎯' را بزنید. مارکر انتخاب می‌شود. سپس پوینتر را روی نقطه مفصل مدل سه‌بعدی ببرید و دوباره 'تک‌کلیک WebGL 🎯' را بزنید تا مارکر در محل جای‌گذاری شود."
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
