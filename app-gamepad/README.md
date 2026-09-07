# BLE Gamepad (Android)

Telefonu, Bluetooth üzerinden **oyun kumandası (gamepad)** olarak gösteren açık kaynak
Android uygulaması. PC / TV / konsol tarafında Android cihazı "Oyun kumandası" olarak
görünür; iki joystick + 6 aksiyon tuşu + yön tuşları ile kontrol gönderir.

## Mimari

Bu uygulama, Blek'in (`io.appground.blehid`) dekonpile edilmiş kodundan rekonstrükte
edilen kanıtlı BLE HID mimarisinin aynısını kullanır (bkz. kök dizindeki
[`extracted/ANALYSIS.md`](../extracted/ANALYSIS.md) §3):

- **GATT sunucusu** (telefon = HID cihazı):
  - `0x1812` HID servisi: HID Information (`2A4A`), HID Control Point (`2A4B`),
    Report Map (`2A4E`), **Report** (`2A4D`, READ|WRITE|NOTIFY + CCCC)
  - `0x1800` Generic Access: cihaz adı + Appearance `0x00C5` (Gamepad)
  - `0x180F` PnP: kimlik/ürün bilgisi
- **Report formatı** (Blek ile birebir aynı model): `[reportID, payload…]`
  - Bu uygulamanın gamepad raporu (Report ID `0x01`, 9 bayt):
    `[0x01, butonlar(2B), X, Y, RX, RY, Rz, hats(1B)]`
- **Reklam**: connectable, `0x1812` UUID + cihaz adı
- **Keep-alive**: bağlıyken her 5 dakikada rapor tekrarı (Blek davranışı)

Gamepad HID profili: usage page `0x01` (Generic Desktop), usage `0x05` (Gamepad),
16 buton + 5 ekseni (X/Y/RX/RY/Rz, 0–255) + 2 hat (standart oyun kumandası
betimleyicisi; rapor tam 64 bit).

## Tuş eşlemesi

| Arayüz | Rapor alanı |
|---|---|
| Sol joystick | X, Y ekseni + Hat1 (8 yön) |
| Sağ joystick | RX, RY ekseni |
| Yön tuşları (▲▼◀▶) | Hat2 (8 yön) |
| A B X Y L R | buton bitleri 0–5 |

## Kullanım

1. Uygulamayı açın → izinleri verin.
2. **Bağlanabilir Ol**'a basın (cihaz 30 sn+ reklam yapar).
3. Kontrol cihazında (PC/TV) Bluetooth ayarlarından **"BLE Gamepad"** adıyla
   cihazınızı bulun ve bağlanın.
4. Bağlandıktan sonra oyun kumandası olarak kullanın; uygulama arka planda
   foreground bildirimiyle çalışmaya devam eder.
5. **Durdur** → reklam ve bağlantı kapanır.

### Uyum notları

- Windows: cihaz "Oyun kumandası" olarak tanımlanır; Ayarlar → Oyuncular bölümünden
  test edebilirsiniz.
- Android 12+ (API 31+) gerektirir (BLE reklam izni modeli).
- v1 yalnızca **BLE** modudur. Classic (BR/EDR) `BluetoothHidDevice` modu sonraki
  sürümlere planlanmıştır.

## Derleme

```bash
cd app-gamepad
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Gereksinimler: JDK 17, Android SDK (platform 34, build-tools 34.0.0).
Debug APK otomatik olarak Android'in debug anahtarıyla imzalanır ve kurulumaya hazırdır.

## Otomatik derleme (GitHub Actions)

`.github/workflows/gamepad.yml`:

- `app-gamepad/**` altında değişiklik olan her push/PR'da derler + APK artifact'ı üretir
- `gamepad-v*` etiketi atıldığında APK'yı GitHub Release'e yükler

## Yasal

Bu bağımsız, açık kaynak uygulamadır; Blek ile kod paylaşımı yoktur — yalnızca
kamuya açık Bluetooth HID/GATT standartları ve dekonpile analiziyle belgelenen
protokol davranışları (UUID'ler, report yapısı) kullanılmıştır. Telif: © 2026,
MIT lisanslı.
