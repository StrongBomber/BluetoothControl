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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * PlayStation (DualSense) düzeni oyun kumandası arayüzü.
 *
 * Her tuş/stick değişimi [GamepadService.updateState] ile BLE HID raporuna
 * dönüştürülür (16 buton + 5 eksen + 2 hat).
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
            val lb = binder as? GamepadService.LocalBinder ?: return
            service = lb.service
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
        setupHoldableButtons(
            R.id.btn_cross, R.id.btn_circle, R.id.btn_square, R.id.btn_triangle,
            R.id.btn_l1, R.id.btn_r1, R.id.btn_l2, R.id.btn_r2,
            R.id.btn_share, R.id.btn_options, R.id.btn_ps
        )
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

    /** Basılı tutulabilen tuşlar: her bit, HUD maskesindeki karşılığı. */
    private fun setupHoldableButtons(vararg pairs: Int) {
        val bits = mapOf(
            R.id.btn_cross to HidGamepad.BTN_CROSS,
            R.id.btn_circle to HidGamepad.BTN_CIRCLE,
            R.id.btn_square to HidGamepad.BTN_SQUARE,
            R.id.btn_triangle to HidGamepad.BTN_TRIANGLE,
            R.id.btn_l1 to HidGamepad.BTN_L1,
            R.id.btn_r1 to HidGamepad.BTN_R1,
            R.id.btn_l2 to HidGamepad.BTN_L2,
            R.id.btn_r2 to HidGamepad.BTN_R2,
            R.id.btn_share to HidGamepad.BTN_SHARE,
            R.id.btn_options to HidGamepad.BTN_OPTIONS,
            R.id.btn_ps to HidGamepad.BTN_PS
        )
        for (id in pairs) {
            val bit = bits[id] ?: continue
            val b = findViewById<Button>(id)
            b.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        buttons = buttons or bit
                        v.alpha = 0.45f
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        pushState()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        buttons = buttons and bit.inv()
                        v.alpha = 1f
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                        v.alpha = 0.45f
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        pushState()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (hat2 == hat) hat2 = 0
                        v.alpha = 1f
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        pushState()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupSticks() {
        findViewById<JoystickView>(R.id.stick_left).onChange = { sx, sy, hat, pressed ->
            x = sx
            y = sy
            hat1 = hat
            if (pressed) buttons = buttons or HidGamepad.BTN_L3
            else buttons = buttons and HidGamepad.BTN_L3.inv()
            pushState()
        }
        findViewById<JoystickView>(R.id.stick_right).onChange = { sx, sy, _, pressed ->
            rx = sx
            ry = sy
            if (pressed) buttons = buttons or HidGamepad.BTN_R3
            else buttons = buttons and HidGamepad.BTN_R3.inv()
            pushState()
        }
    }

    companion object {
        private const val REQ_PERMS = 1
    }
}
