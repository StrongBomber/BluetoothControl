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

    /**
     * PlayStation düzeni buton eşlemesi (bit → buton).
     *
     *  X (cross/alt), O (circle/sağ), □ (square/sol), △ (triangle/üst),
     *  L1, R1, L2, R2, Share, Options, L3, R3, PS.
     *
     * PC (XInput'a otomatik harflenen standart gamepad) tarafında
     * cross=A, circle=B, square=X, triangle=Y olarak görünür.
     */
    const val BTN_CROSS = 0x0001    // ✕ (alt)      → XInput A
    const val BTN_CIRCLE = 0x0002   // ○ (sağ)      → XInput B
    const val BTN_SQUARE = 0x0004   // □ (sol)      → XInput X
    const val BTN_TRIANGLE = 0x0008 // △ (üst)      → XInput Y
    const val BTN_L1 = 0x0010
    const val BTN_R1 = 0x0020
    const val BTN_L2 = 0x0040
    const val BTN_R2 = 0x0080
    const val BTN_SHARE = 0x0100
    const val BTN_OPTIONS = 0x0200
    const val BTN_L3 = 0x0400       // sol stick click
    const val BTN_R3 = 0x0800       // sağ stick click
    const val BTN_PS = 0x1000

    /**
     * Standart gamepad rapor betimleyicisi: 16 buton + 5 ekseni (X, Y, RX, RY, Rz)
     * + 2 hat (Hat1, Hat2). Rapor boyutu tam 64 bit = 8 bayt (+ 1 report ID).
     * Windows/macOS/Linux bu betimleyiciyi "Oyun kumandası" olarak tanır.
     */
    val REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x05.toByte(), // USAGE (Gamepad)
        0xA1.toByte(), 0x01.toByte(), // COLLECTION (Application)
        0x85.toByte(), 0x01.toByte(), //   REPORT_ID (1)
        // 16 buton
        0x19.toByte(), 0x01.toByte(), //   USAGE_MINIMUM (Button 1)
        0x29.toByte(), 0x10.toByte(), //   USAGE_MAXIMUM (Button 16)
        0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(), //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(), //   REPORT_SIZE (1)
        0x95.toByte(), 0x10.toByte(), //   REPORT_COUNT (16)
        0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
        // 5 ekseni: X, Y, RX, RY, Rz — 0..255, 127 = orta
        0x09.toByte(), 0x30.toByte(), //   USAGE (X)
        0x09.toByte(), 0x31.toByte(), //   USAGE (Y)
        0x09.toByte(), 0x33.toByte(), //   USAGE (RX)
        0x09.toByte(), 0x34.toByte(), //   USAGE (RY)
        0x09.toByte(), 0x35.toByte(), //   USAGE (Rz)
        0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // LOGICAL_MAXIMUM (255)
        0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
        0x95.toByte(), 0x05.toByte(), //   REPORT_COUNT (5)
        0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
        // 2 hat: Hat1 + Hat2 — 4 bit, 0..7 (0 = boşta/orta)
        0x05.toByte(), 0x01.toByte(), //   USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x39.toByte(), //   USAGE (Hat switch)
        0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x07.toByte(), //   LOGICAL_MAXIMUM (7)
        0x75.toByte(), 0x04.toByte(), //   REPORT_SIZE (4)
        0x95.toByte(), 0x02.toByte(), //   REPORT_COUNT (2)
        0x81.toByte(), 0x42.toByte(), //   INPUT (Data,Var,Abs,Rel,BitField)
        0xC0.toByte()                 // END_COLLECTION (üst oyun kumandası künmesi)
    )

    /** HID Information (2A4A): BCD 1.11, country 0, flags 0x01. */
    val HID_INFORMATION: ByteArray = byteArrayOf(0x11.toByte(), 0x01, 0x00, 0x01)

    /** GAP Appearance: 0x00C5 = "Gamepad". */
    val APPEARANCE: ByteArray = byteArrayOf(0xC5.toByte(), 0x00)

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
