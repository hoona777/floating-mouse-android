package com.floatingmouse.app

import android.content.Intent
import android.net.Uri
import android.os.Build
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
            setPadding(40, 60, 40, 40)
            setBackgroundColor(0xFF0F172A.toInt()) // Dark Slate Background
        }

        val titleView = TextView(this).apply {
            text = "موس و تاچ‌پد شناور پیشرفته"
            textSize = 22f
            setTextColor(0xFF38BDF8.toInt())
            setPadding(0, 0, 0, 20)
        }

        val subtitleView = TextView(this).apply {
            text = "راهنمای استفاده و فعال‌سازی سرویس دسترسی"
            textSize = 14f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 0, 0, 40)
        }

        val btnOverlayPermission = Button(this).apply {
            text = "۱. اعطای مجوز پنجره‌های شناور (Overlay)"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "مجوز پنجره‌های شناور قبلاً اعطا شده است.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnAccessibilityPermission = Button(this).apply {
            text = "۲. فعال‌سازی سرویس دسترسی (Accessibility)"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this@MainActivity, "لطفاً برنامه 'موس شناور' را پیدا و روشن کنید.", Toast.LENGTH_LONG).show()
            }
        }

        val guideText = TextView(this).apply {
            text = "• نقطه هدف‌گیری دقیق پوینتر (Exact Pointer Hotspot):\n" +
                   "  نقطه برخورد کلیک‌ها دقیقاً نوک نوکِ فلش/پوینتر (مختصات ۰و۰) است تا آیکون‌ها، نوار بالای مرورگر و دایره‌های میکسامو ۱۰۰٪ دقیق لمس شوند.\n\n" +
                   "• جابه‌جایی آیکون‌های صفحه اصلی و مفاصل میکسامو (روش ۱ - هوشمند):\n" +
                   "  ۱. نوک نوک فلش موس را روی آیکون یا مفصل Mixamo قرار دهید.\n" +
                   "  ۲. دکمه 'گرفتن 📍' را بزنید (مکان اولیه ثبت می‌شود).\n" +
                   "  ۳. موس را به مقصد ببرید و 'رها 🎯' را بزنید. سیستم حرکت لمس پیوسته با مکث اولیه ۶۵۰ms اجرا می‌کند تا آیکون/مفصل جا‌به‌جا شود.\n\n" +
                   "• حالت قفل فشار/لمس (روش ۲ - Touch Hold Lock 🔒):\n" +
                   "  ۱. نوک فلش را روی مفصل یا آیکون بگذارید.\n" +
                   "  ۲. دکمه 'فشار 🔒' را بزنید تا لمس روی صفحه قفل بماند.\n" +
                   "  ۳. با تاچ‌پد موس را به مقصد بکشید.\n" +
                   "  ۴. دکمه 'رها 🔓' را بزنید تا لمس آزاد شود.\n\n" +
                   "• کلیک و دو کلیک سریع (برای نوار مرورگر و برنامه‌ها):\n" +
                   "  'چپ' برای تک کلیک، '۲ کلیک' برای باز کردن برنامه‌ها/پوشه‌ها، 'راست' برای منوی راست کلیک، و 'مکث/نگه' برای نگه داشتن لمس به مدت ۸۰۰ms."
            textSize = 13f
            setTextColor(0xFFCBD5E1.toInt())
            setLineSpacing(8f, 1.1f)
            setPadding(0, 40, 0, 20)
        }

        val scrollView = ScrollView(this).apply {
            addView(guideText)
        }

        rootLayout.addView(titleView)
        rootLayout.addView(subtitleView)
        rootLayout.addView(btnOverlayPermission)
        rootLayout.addView(btnAccessibilityPermission)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }
}
