package io.github.strongbomber.blegamepad

/**
 * Gamepad HID profili (usage page 0x01 Generic Desktop, usage 0x05 Gamepad).
 *
 * Bu format, Blek'in GATT-sunucusu mimarisinden rekonstrükte edilen "tek Report
 * karakteristik + report-ID öneki" modeliyle uyumludur (bkz. extracted/ANALYSIS.md §3.2).
 *
 * Rapor formatı (Report ID 0x01, 9 bayt):
 *   [0] = 0x01            (report ID)
 *   [1] = butonlar (düşük bayt):  bit0=A bit1=B bit2=X bit3=Y bit4=L bit5=R
 *   [2] = butonlar (yüksek bayt)
 *   [3] = X ekseni   0..255 (127 = orta)
 *   [4] = Y ekseni   0..255 (yukarı = büyük değer)
 *   [5] = RX ekseni  0..255
 *   [6] = RY ekseni  0..255
 *   [7] = Rz ekseni  0..255 (dolu olarak 127)
 *   [8] = hat'lar:   düşük nibble = Hat1 (sol stick yönü),
 *                    yüksek nibble = Hat2 (yön tuşları)
 *                    0=orta, 1=yukarı, 2=sağ-yukarı, 3=sağ, 4=sağ-aşağı,
 *                    5=aşağı, 6=sol-aşağı, 7=sol, 8=sol-yukarı
 */
object HidGamepad {

    const val REPORT_ID: Int = 1

    const val BTN_A = 0x01
    const val BTN_B = 0x02
    const val BTN_X = 0x04
    const val BTN_Y = 0x08
    const val BTN_L = 0x10
    const val BTN_R = 0x20

    /**
     * Standart gamepad rapor betimleyicisi: 16 buton + 5 ekseni (X, Y, RX, RY, Rz)
     * + 2 hat (Hat1, Hat2). Rapor boyutu tam 64 bit = 8 bayt (+ 1 report ID).
     * Windows/macOS/Linux bu betimleyiciyi "Oyun kumandası" olarak tanır.
     */
    val REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01,       // USAGE_PAGE (Generic Desktop)
        0x09, 0x05,                // USAGE (Gamepad)
        0xA1.toByte(), 0x01,       // COLLECTION (Application)
        0x85, 0x01,                //   REPORT_ID (1)
        // 16 buton
        0x19, 0x01,                //   USAGE_MINIMUM (Button 1)
        0x29, 0x10,                //   USAGE_MAXIMUM (Button 16)
        0x15, 0x00,                //   LOGICAL_MINIMUM (0)
        0x25, 0x01,                //   LOGICAL_MAXIMUM (1)
        0x75, 0x01,                //   REPORT_SIZE (1)
        0x95, 0x10,                //   REPORT_COUNT (16)
        0x81, 0x02,                //   INPUT (Data,Var,Abs)
        // 5 ekseni: X, Y, RX, RY, Rz — 0..255, 127 = orta
        0x09, 0x30,                //   USAGE (X)
        0x09, 0x31,                //   USAGE (Y)
        0x09, 0x33,                //   USAGE (RX)
        0x09, 0x34,                //   USAGE (RY)
        0x09, 0x35,                //   USAGE (Rz)
        0x15, 0x00,                //   LOGICAL_MINIMUM (0)
        0x26, 0xFF, 0x00,          //   LOGICAL_MAXIMUM (255)
        0x75, 0x08,                //   REPORT_SIZE (8)
        0x95, 0x05,                //   REPORT_COUNT (5)
        0x81, 0x02,                //   INPUT (Data,Var,Abs)
        // 2 hat: Hat1 + Hat2 — 4 bit, 0..7 (0 = boşta/orta)
        0x05.toByte(), 0x01,       //   USAGE_PAGE (Generic Desktop)
        0x09, 0x39,                //   USAGE (Hat switch)
        0x15, 0x00,                //   LOGICAL_MINIMUM (0)
        0x25, 0x07,                //   LOGICAL_MAXIMUM (7)
        0x75, 0x04,                //   REPORT_SIZE (4)
        0x95, 0x02,                //   REPORT_COUNT (2)
        0x81.toByte(), 0x42,       //   INPUT (Data,Var,Abs,Rel,BitField)
        0xC0.toByte()              // END_COLLECTION (üst oyun kumandası künmesi)
    )

    /** HID Information (2A4A): BCD 1.11, country 0, flags 0x01. */
    val HID_INFORMATION: ByteArray = byteArrayOf(0x11, 0x01, 0x00, 0x01)

    /** GAP Appearance: 0x00C5 = "Gamepad". */
    val APPEARANCE: ByteArray = byteArrayOf(0xC5, 0x00)

    /**
     * Rapor bayt dizisini üretir: [ID, btnLo, btnHi, X, Y, RX, RY, Rz, hats].
     * Sıralama, yukarıdaki betimleyicinin INPUT madde sırasıyla birebir aynıdır.
     */
    fun pack(
        buttons: Int,
        x: Int,
        y: Int,
        rx: Int,
        ry: Int,
        rz: Int,
        hat1: Int,
        hat2: Int
    ): ByteArray = byteArrayOf(
        REPORT_ID.toByte(),
        (buttons and 0xFF).toByte(),
        ((buttons ushr 8) and 0xFF).toByte(),
        x.toByte(), y.toByte(), rx.toByte(), ry.toByte(), rz.toByte(),
        ((hat1 and 0x0F) or ((hat2 and 0x0F) shl 4)).toByte()
    )
}
