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

/**
 * Main Activity for Floating Mouse App
 * Target File Path in GitHub: app/src/main/java/com/floatingmouse/app/MainActivity.kt
 */
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
            text = "برای فعال‌سازی و استفاده از موس شناور و پنل‌های کاملاً مستقل:"
            textSize = 14f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(0, 0, 0, 32)
        }

        val btnOverlay = Button(this).apply {
            text = "۱. اعطای مجوز پنجره شناور (Draw Overlays)"
            setOnClickListener { checkOverlayPermission() }
        }

        val btnService = Button(this).apply {
            text = "۲. فعال‌سازی سرویس دسترسی‌پذیری (Accessibility)"
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
            text = "• پنجره‌های کاملاً مستقل و شناور:\n" +
                   "  تاچ‌پد و پنل کلیک ۱۰۰٪ از هم جدا هستند. با کشیدن نوار بالای هر کدام (::: تاچ‌پد ::: یا ::: پنل کلیک :::)، می‌توانید آن‌ها را به هر جای صفحه منتقل کنید.\n\n" +
                   "• جابه‌جایی دقیق آیکون‌های صفحه اصلی و مفاصل میکسامو (Mixamo Rigging):\n" +
                   "  ۱. نوک نشانگر موس را دقیقاً روی آیکون یا دایره مفصل قرار دهید.\n" +
                   "  ۲. دکمه 'گرفتن 📍' را بزنید (مکث ۷۵۰ms جهت آماده‌سازی جابه‌جایی اعمال می‌شود).\n" +
                   "  ۳. نشانگر موس را با تاچ‌پد به مقصد ببرند.\n" +
                   "  ۴. دکمه 'رها 🎯' را بزنید تا آیکون یا مفصل منتقل شود.\n\n" +
                   "• حالت کشیدن زنده (Live Drag):\n" +
                   "  با زدن دکمه 'کشیدن' به حالت 'کشیدن [فعال]'، هر حرکتی روی تاچ‌پد به صورت لمس پیوسته زیر نشانگر منتقل می‌شود.\n\n" +
                   "• بازگرداندن موس پس از خاموش و روشن کردن:\n" +
                   "  پس از روشن کردن سرویس یا زدن دکمه ۳ بالا یا لمس اعلان در نوار اعلان‌های اندروید، موس شناور فوراً ظاهر می‌شود."
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
