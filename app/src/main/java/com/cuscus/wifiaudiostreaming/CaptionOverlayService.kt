/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 */

package com.cuscus.wifiaudiostreaming

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class CaptionOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var container: FrameLayout? = null
    private var label: TextView? = null
    private var attached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildView()
        attach()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> applyText("")
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        detach()
        container = null
        label = null
        windowManager = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun buildView() {
        val density = resources.displayMetrics.density
        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(6f * density, 0f, 0f, Color.BLACK)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            gravity = Gravity.CENTER
            maxLines = 3
            includeFontPadding = false
            val padH = (16 * density).toInt()
            val padV = (8 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.argb(150, 0, 0, 0))
            }
            visibility = View.GONE
        }
        label = text

        container = FrameLayout(this).apply {
            addView(
                text,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                )
            )
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = resources.displayMetrics.density
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (bottomMarginDp * density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun attach() {
        if (attached) return
        val wm = windowManager ?: return
        val view = container ?: return
        if (!canDrawOverlays(this)) return
        runCatching {
            wm.addView(view, layoutParams())
            attached = true
        }
    }

    private fun detach() {
        if (!attached) return
        runCatching { windowManager?.removeViewImmediate(container) }
        attached = false
    }

    private fun applyText(text: String) {
        val view = label ?: return
        if (text.isEmpty()) {
            if (view.visibility != View.GONE) view.visibility = View.GONE
            return
        }
        if (view.text?.toString() != text) view.text = text
        if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
    }

    private fun applyStyle() {
        label?.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        val wm = windowManager ?: return
        val view = container ?: return
        if (!attached) return
        runCatching { wm.updateViewLayout(view, layoutParams()) }
    }

    companion object {
        const val ACTION_HIDE = "com.cuscus.wifiaudiostreaming.CAPTION_HIDE"
        const val ACTION_STOP = "com.cuscus.wifiaudiostreaming.CAPTION_STOP"

        private const val OVERLAY_SETTINGS = Settings.ACTION_MANAGE_OVERLAY_PERMISSION

        @Volatile
        private var instance: CaptionOverlayService? = null

        @Volatile
        var textSizeSp: Float = 22f
            private set

        @Volatile
        var bottomMarginDp: Float = 48f
            private set

        private val mainHandler = Handler(Looper.getMainLooper())

        val isShowing: Boolean get() = instance != null

        fun canDrawOverlays(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

        fun permissionIntent(context: Context): Intent =
            Intent(OVERLAY_SETTINGS, Uri.parse("package:${context.packageName}"))

        fun show(context: Context): Boolean {
            if (!canDrawOverlays(context)) return false
            context.startService(Intent(context, CaptionOverlayService::class.java))
            return true
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, CaptionOverlayService::class.java).setAction(ACTION_STOP)
            )
        }

        fun setText(text: String) {
            val service = instance ?: return
            mainHandler.post { service.applyText(text) }
        }

        fun setStyle(sizeSp: Float, marginDp: Float) {
            textSizeSp = sizeSp.coerceIn(12f, 48f)
            bottomMarginDp = marginDp.coerceIn(0f, 300f)
            val service = instance ?: return
            mainHandler.post { service.applyStyle() }
        }
    }
}
