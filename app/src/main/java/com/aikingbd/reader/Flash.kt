package com.aikingbd.reader

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

object Flash {
    private val handler = Handler(Looper.getMainLooper())
    private var cm: CameraManager? = null
    private var camId: String? = null
    private var lit = false
    private var running = false
    private var endAt = 0L
    private var remaining = -1

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (System.currentTimeMillis() > endAt) {
                stopNow()
                return
            }
            if (lit) {
                setTorch(false)
                if (remaining > 0) {
                    remaining--
                    if (remaining == 0) {
                        stopNow()
                        return
                    }
                }
            } else {
                setTorch(true)
            }
            handler.postDelayed(this, 280)
        }
    }

    private fun prepare(ctx: Context): Boolean {
        if (camId != null) return true
        val m = ctx.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm = m
        for (id in m.cameraIdList) {
            val ch = m.getCameraCharacteristics(id)
            val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val back = ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            if (hasFlash && back) {
                camId = id
                return true
            }
        }
        return false
    }

    private fun setTorch(on: Boolean) {
        try {
            cm?.setTorchMode(camId!!, on)
            lit = on
        } catch (_: Exception) {
        }
    }

    private fun begin(ctx: Context, times: Int, maxMs: Long) {
        handler.post {
            val ok = try { prepare(ctx) } catch (_: Exception) { false }
            if (ok) {
                handler.removeCallbacks(tick)
                running = true
                remaining = times
                endAt = System.currentTimeMillis() + maxMs
                handler.post(tick)
            }
        }
    }

    private fun stopNow() {
        running = false
        handler.removeCallbacks(tick)
        setTorch(false)
    }

    fun continuous(ctx: Context, maxMs: Long = 60000) = begin(ctx, -1, maxMs)

    fun blink(ctx: Context, times: Int) = begin(ctx, times, 15000)

    fun stop() {
        handler.post { stopNow() }
    }
}
