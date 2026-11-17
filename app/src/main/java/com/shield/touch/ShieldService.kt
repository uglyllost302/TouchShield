package com.shield.touch

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView

class ShieldService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private val pads = mutableListOf<View>()
    private val padParams = mutableListOf<WindowManager.LayoutParams>()
    private var locked = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFg()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createControlPanel()
        loadPads()
    }

    private fun startFg() {
        val chId = "shield_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "TouchShield Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, chId)
                .setContentTitle("TouchShield активен")
                .setContentText("Клики проходят, сдвиг камеры заблокирован")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            Notification.Builder(this).setContentTitle("TouchShield").build()
        }
        startForeground(101, notif)
    }

    private fun makeBg(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 28f
            setStroke(4, stroke)
        }
    }

    private fun createControlPanel() {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = makeBg(Color.parseColor("#CC181818"), Color.parseColor("#00E5FF"))
            setPadding(20, 12, 20, 12)
        }

        val btnLock = makeBtn("⚙️ Edit")
        val btnAdd = makeBtn("➕ Зона")
        val btnSize = makeBtn("📐 Размер")
        val btnClose = makeBtn("✖")

        btnLock.setOnClickListener {
            locked = !locked
            btnLock.text = if (locked) "🔒 Lock" else "⚙️ Edit"
            btnAdd.visibility = if (locked) View.GONE else View.VISIBLE
            btnSize.visibility = if (locked) View.GONE else View.VISIBLE
            updatePadsStyle()
            savePads()
        }

        btnAdd.setOnClickListener {
            addShieldPad(250, 500, 240)
            savePads()
        }

        btnSize.setOnClickListener {
            if (pads.isNotEmpty()) {
                val lp = padParams.last()
                val nextSize = when (lp.width) {
                    160 -> 240
                    240 -> 340
                    else -> 160
                }
                lp.width = nextSize
                lp.height = nextSize
                wm.updateViewLayout(pads.last(), lp)
                savePads()
            }
        }

        btnClose.setOnClickListener {
            savePads()
            stopSelf()
        }

        panel.addView(btnLock)
        panel.addView(btnAdd)
        panel.addView(btnSize)
        panel.addView(btnClose)

        val pParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 40
        }

        panel.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0f; var dy = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx = pParams.x - e.rawX; dy = pParams.y - e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        pParams.x = (e.rawX + dx).toInt()
                        pParams.y = (e.rawY + dy).toInt()
                        wm.updateViewLayout(panel, pParams)
                    }
                }
                return false
            }
        })

        wm.addView(panel, pParams)
    }

    private fun makeBtn(txt: String): TextView {
        return TextView(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(18, 8, 18, 8)
        }
    }

    private fun addShieldPad(startX: Int, startY: Int, size: Int) {
        val pad = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "🛡️"
            setTextColor(Color.WHITE)
            textSize = 16f
        }

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        pad.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0f; var dy = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                if (!locked) {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> { dx = lp.x - e.rawX; dy = lp.y - e.rawY }
                        MotionEvent.ACTION_MOVE -> {
                            lp.x = (e.rawX + dx).toInt()
                            lp.y = (e.rawY + dy).toInt()
                            wm.updateViewLayout(pad, lp)
                        }
                        MotionEvent.ACTION_UP -> savePads()
                    }
                    return true
                }

                // В боевом режиме (🔒 Lock):
                return when (e.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                        // Пропускаем клики дальше в игру, чтобы кнопки нажимались!
                        false
                    }
                    else -> {
                        // Поглощаем только сдвиги пальца (MOVE), чтобы камера не крутилась
                        true
                    }
                }
            }
        })

        pads.add(pad)
        padParams.add(lp)
        wm.addView(pad, lp)
        updatePadsStyle()
    }

    private fun updatePadsStyle() {
        for (pad in pads) {
            val tv = pad as TextView
            if (locked) {
                tv.text = ""
                tv.background = makeBg(Color.parseColor("#1800E5FF"), Color.parseColor("#4400E5FF"))
            } else {
                tv.text = "🛡️"
                tv.background = makeBg(Color.parseColor("#5500E5FF"), Color.parseColor("#FF00E5FF"))
            }
        }
    }

    private fun savePads() {
        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        val data = padParams.joinToString(";") { "${it.x},${it.y},${it.width}" }
        prefs.edit().putString("pads", data).apply()
    }

    private fun loadPads() {
        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("pads", "") ?: ""
        if (raw.isEmpty()) {
            addShieldPad(180, 500, 240)
            addShieldPad(1400, 500, 240)
        } else {
            raw.split(";").forEach { item ->
                val parts = item.split(",")
                if (parts.size == 3) {
                    addShieldPad(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pads.forEach { runCatching { wm.removeView(it) } }
        runCatching { wm.removeView(panel) }
    }
}
