package com.floatingmouse.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class MixamoWebActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectMixamoFixes()
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("https://www.mixamo.com")
    }

    private fun injectMixamoFixes() {
        val jsScript = """
            (function() {
                // تزریق CSS جهت غیرفعال‌سازی اسکرول بوم و فعال‌سازی دایره‌ها
                var style = document.createElement('style');
                style.innerHTML = `
                    canvas, .canvas-container, body {
                        pointer-events: none !important;
                        touch-action: none !important;
                    }
                    div[class*="marker"], .marker, svg, path {
                        pointer-events: auto !important;
                        touch-action: none !important;
                        cursor: grab !important;
                    }
                `;
                document.head.appendChild(style);

                // تزریق اسکریپت شبیه‌سازی Pointer Events برای جابه‌جایی دایره‌های Mixamo
                let activeTarget = null;

                function sendPointerEvent(type, touch) {
                    const target = activeTarget || document.elementFromPoint(touch.clientX, touch.clientY);
                    if (!target) return;

                    const event = new PointerEvent(type, {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        clientX: touch.clientX,
                        clientY: touch.clientY,
                        pointerId: 1,
                        pointerType: 'mouse',
                        isPrimary: true,
                        buttons: (type === 'pointerup') ? 0 : 1
                    });

                    target.dispatchEvent(event);
                    return target;
                }

                window.addEventListener('touchstart', function(e) {
                    const touch = e.touches[0];
                    const target = document.elementFromPoint(touch.clientX, touch.clientY);
                    if (target) {
                        e.preventDefault();
                        activeTarget = sendPointerEvent('pointerdown', touch);
                    }
                }, { passive: false });

                window.addEventListener('touchmove', function(e) {
                    if (activeTarget) {
                        e.preventDefault();
                        sendPointerEvent('pointermove', e.touches[0]);
                    }
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (activeTarget) {
                        e.preventDefault();
                        sendPointerEvent('pointerup', e.changedTouches[0]);
                        activeTarget = null;
                    }
                }, { passive: false });
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsScript, null)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
