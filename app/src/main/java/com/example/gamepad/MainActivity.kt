package com.example.gamepad

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private var webSocketClient: WebSocketClient? = null
    private var analogTouchId: Int? = null
    private var lastSendTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()
        setupButtons()
        setupAnalogStick()

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val ip = findViewById<EditText>(R.id.etIpAddress).text.toString().trim()
            if (ip.isNotEmpty()) {
                connectWebSocket(ip)
            }
        }
    }

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun connectWebSocket(ip: String) {
        val uri = URI("ws://$ip:8765")
        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {}
            override fun onMessage(message: String?) {}
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }
        webSocketClient?.connect()
    }

    private fun sendButtonInput(btn: String, action: String) {
        if (webSocketClient?.isOpen == true) {
            val json = JSONObject().apply {
                put("type", "button")
                put("button", btn)
                put("action", action)
            }
            webSocketClient?.send(json.toString())
        }
    }

    private fun sendAnalogInput(x: Int, y: Int) {
        if (webSocketClient?.isOpen == true) {
            val json = JSONObject().apply {
                put("type", "analog")
                put("x", x)
                put("y", y)
            }
            webSocketClient?.send(json.toString())
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtons() {
        val buttonMap = mapOf(
            R.id.btnL to "L", R.id.btnR to "R",
            R.id.btnUp to "UP", R.id.btnDown to "DOWN",
            R.id.btnLeft to "LEFT", R.id.btnRight to "RIGHT",
            R.id.btnTriangle to "TRIANGLE", R.id.btnSquare to "SQUARE",
            R.id.btnCircle to "CIRCLE", R.id.btnCross to "CROSS",
            R.id.btnSelect to "SELECT", R.id.btnStart to "START"
        )

        buttonMap.forEach { (id, btnKey) ->
            findViewById<View>(id)?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        sendButtonInput(btnKey, "down")
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        sendButtonInput(btnKey, "up")
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAnalogStick() {
        val base = findViewById<FrameLayout>(R.id.analogBase)
        val stick = findViewById<ImageView>(R.id.analogStick)
        val maxRadius = 100f

        base.setOnTouchListener { _, event ->
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (analogTouchId == null) {
                        analogTouchId = pointerId
                        processAnalog(event.getX(actionIndex), event.getY(actionIndex), base, stick, maxRadius)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (analogTouchId != null) {
                        for (i in 0 until event.pointerCount) {
                            if (event.getPointerId(i) == analogTouchId) {
                                processAnalog(event.getX(i), event.getY(i), base, stick, maxRadius)
                                break
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (analogTouchId == pointerId) {
                        analogTouchId = null
                        stick.translationX = 0f
                        stick.translationY = 0f
                        sendAnalogInput(0, 0)
                    }
                }
            }
            true
        }
    }

    private fun processAnalog(x: Float, y: Float, base: View, stick: View, maxRadius: Float) {
        val centerX = base.width / 2f
        val centerY = base.height / 2f

        var deltaX = x - centerX
        var deltaY = y - centerY
        val distance = hypot(deltaX, deltaY)

        if (distance > maxRadius) {
            deltaX = (deltaX / distance) * maxRadius
            deltaY = (deltaY / distance) * maxRadius
        }

        stick.translationX = deltaX
        stick.translationY = deltaY

        val now = System.currentTimeMillis()
        if (now - lastSendTime > 30) {
            val normX = ((deltaX / maxRadius) * 32767).toInt()
            val normY = (-(deltaY / maxRadius) * 32767).toInt()
            sendAnalogInput(normX, normY)
            lastSendTime = now
        }
    }
}