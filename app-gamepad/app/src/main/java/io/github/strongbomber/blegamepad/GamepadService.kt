package io.github.strongbomber.blegamepad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * BLE GATT sunucusu: telefon, bağlanan cihaza (PC/TV/konsol) HID gamepad olarak görünür.
 *
 * Mimari, Blek'in (io.appground.blehid) dekonpile edilmiş kodundan rekonstrükte edilen
 * protokolün aynısıdır (extracted/ANALYSIS.md §3.2):
 *  - connectable reklam: 0x1812 (HID) servis UUID + cihaz adı
 *  - GATT: HID + Generic Access + PnP servisleri
 *  - Report bildirimi: [reportID, payload]
 *  - her 5 dakikada keep-alive bildirimi
 */
class GamepadService : Service() {

    interface StateListener {
        fun onStateChange(state: Int, deviceName: String)
    }

    inner class LocalBinder : Binder() {
        val service: GamepadService get() = this@GamepadService
    }

    companion object {
        const val TAG = "GamepadService"
        const val CHANNEL_ID = "gamepad"
        const val NOTIF_ID = 1
        const val STATE_OFF = 0
        const val STATE_ADVERTISING = 1
        const val STATE_CONNECTED = 2
        const val KEEP_ALIVE_MS = 5L * 60L * 1000L

        const val PNP_VENDOR_ID = 0x1A49
        const val PNP_PRODUCT_ID = 0x0001

        val SVC_HID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        val SVC_GAP = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        val SVC_PNP = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")

        val CHAR_HID_INFO = UUID.fromString("00002A4A-0000-1000-8000-00805f9b34fb")
        val CHAR_HID_CTRL = UUID.fromString("00002A4B-0000-1000-8000-00805f9b34fb")
        val CHAR_REPORT_MAP = UUID.fromString("00002A4E-0000-1000-8000-00805f9b34fb")
        val CHAR_REPORT = UUID.fromString("00002A4D-0000-1000-8000-00805f9b34fb")
        val CHAR_DEV_NAME = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")
        val CHAR_APPEARANCE = UUID.fromString("00002A01-0000-1000-8000-00805f9b34fb")
        val CHAR_PNP_ID = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb")
        val CHAR_PNP_VENDOR = UUID.fromString("00002A51-0000-1000-8000-00805f9b34fb")
        val CHAR_PNP_PRODUCT = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")
        val CHAR_PNP_REVISION = UUID.fromString("00002A53-0000-1000-8000-00805f9b34fb")
        val CHAR_PNP_SERIAL = UUID.fromString("00002A54-0000-1000-8000-00805f9b34fb")
        val CHAR_PNP_MODEL = UUID.fromString("00002A55-0000-1000-8000-00805f9b34fb")
        val CHAR_PNP_MANUFACTURER = UUID.fromString("00002A56-0000-1000-8000-00805f9b34fb")
        val DESC_CCCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var reportChar: BluetoothGattCharacteristic? = null

    @Volatile private var connectedDevice: BluetoothDevice? = null
    @Volatile private var state = STATE_OFF
    @Volatile private var servicesReady = false
    @Volatile private var advertising = false

    // Oyun kumandası durumu (ana activity'den güncellenir)
    @Volatile private var buttons = 0
    @Volatile private var x = 127
    @Volatile private var y = 127
    @Volatile private var rx = 127
    @Volatile private var ry = 127
    @Volatile private var rz = 127
    @Volatile private var hat1 = 0
    @Volatile private var hat2 = 0

    private val listeners = mutableListOf<StateListener>()
    private val handler = Handler(Looper.getMainLooper())

    private val keepAlive = object : Runnable {
        override fun run() {
            sendReport()
            handler.postDelayed(this, KEEP_ALIVE_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null) {
            Log.e(TAG, "Bluetooth adapter yok")
            notifyState(STATE_OFF, "")
            return
        }
        gattServer = bm.openGattServer(this, gattServerCallback)
        advertiser = adapter.bluetoothLeAdvertiser
        buildServices()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(keepAlive)
        if (advertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: Exception) {
            }
            advertising = false
        }
        gattServer?.close()
        gattServer = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- API (activity)

    fun addListener(l: StateListener) {
        listeners.add(l)
        l.onStateChange(state, connectedDevice?.let { nameOf(it) } ?: "")
    }

    fun removeListener(l: StateListener) {
        listeners.remove(l)
    }

    fun currentState(): Int = state

    /** Oyun kumandası durumunu günceller ve bağlı cihaza bildirir. */
    fun updateState(
        buttons: Int, x: Int, y: Int, rx: Int, ry: Int,
        rz: Int, hat1: Int, hat2: Int
    ) {
        this.buttons = buttons
        this.x = x
        this.y = y
        this.rx = rx
        this.ry = ry
        this.rz = rz
        this.hat1 = hat1
        this.hat2 = hat2
        sendReport()
    }

    fun startAdvertising() {
        val adv = advertiser
        val server = gattServer
        if (adv == null || server == null) return
        val name = deviceName() // "PS BLE Gamepad" — her zaman reklamda görünür
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid.fromString("00001812-0000-1000-8000-00805f9b34fb"))
            .setIncludeDeviceName(name.length <= 22)
            .build()
        try {
            adv.startAdvertising(settings, data, null, advertiseCallback)
            advertising = true
        } catch (e: Exception) {
            Log.e(TAG, "Reklam başlatılamadı: ${e.message}")
        }
    }

    fun stopAdvertising() {
        if (advertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: Exception) {
            }
        }
        advertising = false
    }

    // ---------------------------------------------------------------- GATT sunucusu

    private fun buildServices() {
        val server = gattServer ?: return

        // 1) HID servisi (HOGP)
        val hid = BluetoothGattService(SVC_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val hidInfo = BluetoothGattCharacteristic(
            CHAR_HID_INFO, BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
        hidInfo.setValue(HidGamepad.HID_INFORMATION)

        val hidCtrl = BluetoothGattCharacteristic(
            CHAR_HID_CTRL,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )

        val reportMap = BluetoothGattCharacteristic(
            CHAR_REPORT_MAP, BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
        reportMap.setValue(HidGamepad.REPORT_DESCRIPTOR)

        reportChar = BluetoothGattCharacteristic(
            CHAR_REPORT,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
        reportChar?.setValue(HidGamepad.pack(0, 127, 127, 127, 127, 127, 0, 0))
        reportChar?.addDescriptor(
            BluetoothGattDescriptor(
                DESC_CCCC,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )

        hid.addCharacteristic(hidInfo)
        hid.addCharacteristic(hidCtrl)
        hid.addCharacteristic(reportMap)
        hid.addCharacteristic(reportChar)
        server.addService(hid)

        // 2) Generic Access (cihaz adı + görünüm)
        val gap = BluetoothGattService(SVC_GAP, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val devName = BluetoothGattCharacteristic(
            CHAR_DEV_NAME, BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
        devName.setValue(deviceName().toByteArray())
        val appearance = BluetoothGattCharacteristic(
            CHAR_APPEARANCE, BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
        appearance.setValue(HidGamepad.APPEARANCE)
        gap.addCharacteristic(devName)
        gap.addCharacteristic(appearance)
        server.addService(gap)

        // 3) PnP (bilgi amaçlı; eşleşme/kimlik)
        val pnp = BluetoothGattService(SVC_PNP, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        pnp.addCharacteristic(pnpChar(CHAR_PNP_ID, byteArrayOf(
            (PNP_VENDOR_ID and 0xFF).toByte(), ((PNP_VENDOR_ID ushr 8) and 0xFF).toByte(),
            (PNP_PRODUCT_ID and 0xFF).toByte(), ((PNP_PRODUCT_ID ushr 8) and 0xFF).toByte()
        )))
        pnp.addCharacteristic(pnpChar(CHAR_PNP_VENDOR, byteArrayOf(
            (PNP_VENDOR_ID and 0xFF).toByte(), ((PNP_VENDOR_ID ushr 8) and 0xFF).toByte()
        )))
        pnp.addCharacteristic(pnpChar(CHAR_PNP_PRODUCT, byteArrayOf(
            (PNP_PRODUCT_ID and 0xFF).toByte(), ((PNP_PRODUCT_ID ushr 8) and 0xFF).toByte()
        )))
        pnp.addCharacteristic(pnpChar(CHAR_PNP_REVISION, byteArrayOf(0x00, 0x01)))
        pnp.addCharacteristic(pnpChar(CHAR_PNP_SERIAL, "BLEGP-0001".toByteArray()))
        pnp.addCharacteristic(pnpChar(CHAR_PNP_MODEL, "BLE Gamepad 01".toByteArray()))
        pnp.addCharacteristic(pnpChar(CHAR_PNP_MANUFACTURER, "BluetoothControl".toByteArray()))
        server.addService(pnp)
    }

    private fun pnpChar(uuid: UUID, value: ByteArray): BluetoothGattCharacteristic {
        val c = BluetoothGattCharacteristic(
            uuid, BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
        c.setValue(value)
        return c
    }

    /**
     * Cihaz kimliği: telefonun adı DEĞİL — bu uygulamanın oyun kumandası adı.
     * Bağlanan taraf (PC/TV/konsol) cihazı "PS BLE Gamepad" olarak görür.
     */
    private fun deviceName(): String = getString(R.string.app_name)

    private fun nameOf(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address
        } catch (_: SecurityException) {
            device.address
        }
    }

    /** [reportID, payload] — Blek ile aynı bildirim biçimi. */
    private fun sendReport() {
        val dev = connectedDevice ?: return
        val char = reportChar ?: return
        val server = gattServer ?: return
        if (!servicesReady) return
        val value = HidGamepad.pack(buttons, x, y, rx, ry, rz, hat1, hat2)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                server.notifyCharacteristicChanged(dev, char, value)
            } else {
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(dev, char, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bildirim gönderilemedi: ${e.message}")
        }
    }

    private fun valueFor(uuid: UUID): ByteArray? = when (uuid) {
        CHAR_HID_INFO -> HidGamepad.HID_INFORMATION
        CHAR_REPORT_MAP -> HidGamepad.REPORT_DESCRIPTOR
        CHAR_REPORT -> HidGamepad.pack(buttons, x, y, rx, ry, rz, hat1, hat2)
        CHAR_DEV_NAME -> deviceName().toByteArray()
        CHAR_APPEARANCE -> HidGamepad.APPEARANCE
        CHAR_PNP_ID -> byteArrayOf(
            (PNP_VENDOR_ID and 0xFF).toByte(), ((PNP_VENDOR_ID ushr 8) and 0xFF).toByte(),
            (PNP_PRODUCT_ID and 0xFF).toByte(), ((PNP_PRODUCT_ID ushr 8) and 0xFF).toByte()
        )
        CHAR_PNP_VENDOR -> byteArrayOf(
            (PNP_VENDOR_ID and 0xFF).toByte(), ((PNP_VENDOR_ID ushr 8) and 0xFF).toByte()
        )
        CHAR_PNP_PRODUCT -> byteArrayOf(
            (PNP_PRODUCT_ID and 0xFF).toByte(), ((PNP_PRODUCT_ID ushr 8) and 0xFF).toByte()
        )
        CHAR_PNP_REVISION -> byteArrayOf(0x00, 0x01)
        CHAR_PNP_SERIAL -> "BLEGP-0001".toByteArray()
        CHAR_PNP_MODEL -> "BLE Gamepad 01".toByteArray()
        CHAR_PNP_MANUFACTURER -> "BluetoothControl".toByteArray()
        else -> null
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Servis eklendi: ${service?.uuid}")
                // 3 servis eklendiğinde hazır
                if (gattServer?.services?.size == 3) {
                    servicesReady = true
                }
            } else {
                Log.e(TAG, "Servis eklenemedi: status=$status")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (device == null) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    stopAdvertising()
                    handler.removeCallbacks(keepAlive)
                    handler.postDelayed(keepAlive, KEEP_ALIVE_MS)
                    Log.i(TAG, "Bağlandı: ${nameOf(device)}")
                    notifyState(STATE_CONNECTED, nameOf(device))
                    startForeground(NOTIF_ID, buildNotification())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val was = connectedDevice?.address == device.address
                    if (was) {
                        connectedDevice = null
                        handler.removeCallbacks(keepAlive)
                        if (advertising || state == STATE_CONNECTED) {
                            startAdvertising()
                        }
                        Log.i(TAG, "Koptu: ${nameOf(device)}")
                        notifyState(STATE_ADVERTISING, "")
                        startForeground(NOTIF_ID, buildNotification())
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = valueFor(characteristic.uuid)
            if (value == null) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, byteArrayOf()
                )
                return
            }
            if (offset >= value.size) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, 0, byteArrayOf()
                )
                return
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            prepared: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            // HID Control Point / Report yazmaları — yalnızca onayla.
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf()
                )
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor
        ) {
            // CCCC okuması: bildirim kapalı durumu (0x0000) dön
            gattServer?.sendResponse(
                device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf(0x00, 0x00)
            )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            prepared: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            // CCCC yazmaları (bildirim aç/kapa) — onayla; bildirim yönetimi OS'ta.
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf()
                )
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "Reklam başladı")
            if (connectedDevice == null) notifyState(STATE_ADVERTISING, "")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Reklam hatası: errorCode=$errorCode")
            advertising = false
            notifyState(STATE_OFF, "")
        }
    }

    // ---------------------------------------------------------------- durum / bildirim

    private fun notifyState(newState: Int, deviceName: String) {
        state = newState
        for (l in listeners) {
            try {
                l.onStateChange(newState, deviceName)
            } catch (_: Exception) {
            }
        }
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val text = when (state) {
            STATE_CONNECTED -> getString(R.string.status_connected, nameOf(connectedDevice ?: return baseNotification("…")))
            STATE_ADVERTISING -> getString(R.string.status_advertising_short)
            else -> getString(R.string.status_off_short)
        }
        return baseNotification(text)
    }

    private fun baseNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
}
