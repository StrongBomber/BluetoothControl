package io.github.strongbomber.blegamepad

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * Oyun kumandası arayüzü: iki joystick + yön tuşları + aksiyon tuşları.
 * Her durum değişiminde GamepadService'e bildirilir → BLE HID raporu gönderilir.
 */
class MainActivity : Activity() {

    private var service: GamepadService? = null
    private var serviceStarted = false

    // Oyun kumandası durumu
    private var buttons = 0
    private var x = 127
    private var y = 127
    private var rx = 127
    private var ry = 127
    private var hat1 = 0
    private var hat2 = 0

    private val statusView: TextView by lazy { findViewById(R.id.status) }
    private val toggleButton: Button by lazy { findViewById(R.id.btn_toggle) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val binder = binder as? GamepadService.LocalBinder ?: return
            service = binder.service
            service?.addListener(stateListener)
            updateUi()
            pushState()
            // Yarış durumu: kullanıcı bağlanma tamamlanmadan "Bağlanabilir Ol" dedi
            if (serviceStarted) service?.startAdvertising()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.removeListener(stateListener)
            service = null
        }
    }

    private val stateListener = object : GamepadService.StateListener {
        override fun onStateChange(state: Int, deviceName: String) {
            runOnUiThread { updateUi(state, deviceName) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupActionButtons()
        setupDpad()
        setupSticks()
        toggleButton.setOnClickListener { onToggle() }
        requestPerms()
    }

    override fun onResume() {
        super.onResume()
        // Listener kayıt işlemi onServiceConnected içinde yapılır (servis hazır olduğunda).
        bindService(Intent(this, GamepadService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        service?.removeListener(stateListener)
        try {
            unbindService(connection)
        } catch (_: Exception) {
        }
    }

    private fun requestPerms() {
        val needed = mutableListOf<String>()
        for (p in listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) needed.add(p)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, R.string.status_perm, Toast.LENGTH_LONG).show()
        }
    }

    private fun onToggle() {
        if (serviceStarted) {
            stopService(Intent(this, GamepadService::class.java))
            service?.stopAdvertising()
            serviceStarted = false
        } else {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, R.string.status_perm, Toast.LENGTH_LONG).show()
                requestPerms()
                return
            }
            startForegroundService(Intent(this, GamepadService::class.java))
            service?.startAdvertising()
            serviceStarted = true
        }
        updateUi()
    }

    private fun updateUi(state: Int = service?.currentState() ?: GamepadService.STATE_OFF, deviceName: String = "") {
        toggleButton.setText(if (serviceStarted) R.string.btn_stop else R.string.btn_start)
        statusView.text = when (state) {
            GamepadService.STATE_CONNECTED -> getString(R.string.status_connected, deviceName)
            GamepadService.STATE_ADVERTISING -> getString(R.string.status_advertising)
            else -> getString(R.string.status_off)
        }
    }

    private fun pushState() {
        service?.updateState(buttons, x, y, rx, ry, 127, hat1, hat2)
    }

    private fun setupActionButtons() {
        val map = mapOf(
            R.id.btn_a to 1,
            R.id.btn_b to 2,
            R.id.btn_x to 4,
            R.id.btn_y to 8,
            R.id.btn_l to 16,
            R.id.btn_r to 32
        )
        for ((id, bit) in map) {
            val b = findViewById<Button>(id)
            b.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        buttons = buttons or bit
                        v.alpha = 0.55f
                        pushState()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        buttons = buttons and bit.inv()
                        v.alpha = 1f
                        pushState()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupDpad() {
        // Yön tuşları Hat 2'ye gider
        val map = mapOf(
            R.id.btn_up to 1,
            R.id.btn_right to 3,
            R.id.btn_down to 5,
            R.id.btn_left to 7
        )
        for ((id, hat) in map) {
            val b = findViewById<Button>(id)
            b.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        hat2 = hat
                        v.alpha = 0.55f
                        pushState()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (hat2 == hat) hat2 = 0
                        v.alpha = 1f
                        pushState()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupSticks() {
        findViewById<JoystickView>(R.id.stick_left).onChange = { sx, sy, hat ->
            x = sx
            y = sy
            hat1 = hat
            pushState()
        }
        findViewById<JoystickView>(R.id.stick_right).onChange = { sx, sy, _ ->
            rx = sx
            ry = sy
            pushState()
        }
    }

    companion object {
        private const val REQ_PERMS = 1
    }
}
