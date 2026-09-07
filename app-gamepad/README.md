# PS BLE Gamepad (Android)

Telefonu, Bluetooth üzerinden **PlayStation tarzı oyun kumandası** olarak gösteren
açık kaynak Android uygulaması. Arayüz DualSense düzenindedir (△○✕□, L1/L2/R1/R2,
D-pad, Share/PS/Options, iki stick + stick click).

## Mimari

Bu uygulama, Blek'in (`io.appground.blehid`) dekonpile edilmiş kodundan rekonstrükte
edilen kanıtlı BLE HID mimarisinin aynısını kullanır (bkz. kök dizindeki
[`extracted/ANALYSIS.md`](../extracted/ANALYSIS.md) §3):

- **GATT sunucusu** (telefon = HID cihazı):
  - `0x1812` HID servisi: HID Information (`2A4A`), HID Control Point (`2A4B`),
    Report Map (`2A4E`), **Report** (`2A4D`, READ|WRITE|NOTIFY + CCCC)
  - `0x1800` Generic Access: cihaz adı + Appearance `0x00C5` (Gamepad)
  - `0x180F` PnP: kimlik/ürün bilgisi
- **Report formatı** (Blek ile birebir aynı model): `[reportID, payload]`
  - Bu uygulamanın gamepad raporu (Report ID `0x01`, 9 bayt):
    `[0x01, butonlar(2B), X, Y, RX, RY, Rz, hats(1B)]`
- **Reklam**: connectable, `0x1812` UUID + cihaz adı; Appearance 0x00C5 sayesinde
  bağlanacak taraf cihazı "oyun kumandası" olarak listeler
- **Keep-alive**: bağlıyken her 5 dakikada rapor tekrarı (Blek davranışı)
- **Stick click**: merkezde basılı tutma → L3/R3; sürüklenince analoge geçer
- **Haptik**: her tuş basısında `VIRTUAL_KEY` geri bildirimi

Gamepad HID profili: usage page `0x01` (Generic Desktop), usage `0x05` (Gamepad),
16 buton + 5 ekseni (X/Y/RX/RY/Rz, 0–255) + 2 hat (standart oyun kumandası
betimleyicisi; rapor tam 64 bit). Bu, tüm BLE oyun kumandalarının kullandığı
evrensel profildir — PlayStation kumandasının BLE'ye kendi protokolüyle bağlanması
(licanslı oyunlar için) konsol tarafından kısıtlıdır, ancak evrensel gamepad profili
aşağıdaki platformlarda standart yoldan çalışır.

## Tuş haritası

| Arayüz | Rapor alanı |
|---|---|
| ✕ (cross, alt) | buton 0 → PC'de A |
| ○ (circle, sağ) | buton 1 → PC'de B |
| □ (square, sol) | buton 2 → PC'de X |
| △ (triangle, üst) | buton 3 → PC'de Y |
| L1 / R1 / L2 / R2 | buton 4 / 5 / 6 / 7 |
| Share / Options / PS | buton 8 / 9 / 10 |
| L3 / R3 | buton 11 / 12 (stick click) |
| Sol stick | X, Y ekseni + Hat1 (8 yön) |
| Sağ stick | RX, RY ekseni |
| D-pad (▲▼◀▶) | Hat2 (8 yön) |

Rz ekseni 127 (nötr) sabit tutulur; 13–15 butonlar yedek.

## Kullanım

1. Uygulamayı açın → izinleri verin.
2. **Bağlanabilir Ol**'a basın (cihaz 30 sn+ reklam yapar).
3. Kontrol cihazında (PC/TV/telefon) Bluetooth ayarlarından **"PS BLE Gamepad"**
   adıyla cihazınızı bulun ve bağlanın.
4. Bağlandıktan sonra oyun kumandası olarak kullanın; uygulama arka planda
   foreground bildirimiyle çalışmaya devam eder.
5. **Durdur** → reklam ve bağlantı kapanır.

## Platform uyumu

| Platform | Durum |
|---|---|
| **Windows 10/11** | ✅ Çıktı olarak çalışır: Ayarlar → Oyuncular → Girdi cihazlarında listelenir; XInput benzeri davranış için DS4Windows/Steam desteği (Steam tüm generic gamepad'leri otomatik kabul eder) |
| **Linux** | ✅ Çıktı olarak çalışır: `lsusb`/BLE taramasında "HID gamepad" olarak görünür, `evdev` üzerinden oyunlara aktarılır |
| **Android** | ✅ GamePad API'li oyunlar/uygulamalar (Google Play "controller destekli" etiketi) ve RetroArch gibi uygulamalarla çalışır |
| **iOS / iPadOS** | ⚠️ Sınırlı: iOS, MFI sertifikası olmayan BLE gamepad'leri oyunlarda kullanıma kapatmıştır; cihaz eşleşebilir ancak oyun içi girdi Apple kısıtı nedeniyle çalışmayabilir |
| **PlayStation 4/5** | ⚠️ Lisanslı oyunlarda yalnızca resmi DualShock/DualSense kabul edilir. Ev ortamında (PS4 dev-kitsi, HEN/firmware modifiyeli kurulumlar, emülatörler) standart BLE gamepad profili çalışır |
| **Nintendo Switch** | ❌ Resmî olarak yalnızca sertifikalı/kendi kontrol cihazları; üçüncü parti BLE kumandalar Switch'in oyun modunda kabul edilmez |

## Derleme

```bash
cd app-gamepad
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Gereksinimler: JDK 17, Android SDK (platform 34, build-tools 34.0.0).
Debug APK otomatik olarak Android'in debug anahtarıyla imzalanır ve kurulumaya hazırdır.

## Otomatik derleme (GitHub Actions)

`.github/workflows/gamepad.yml` (kök dizinde):

- `app-gamepad/**` altında değişiklik olan her push/PR'da derler + APK artifact'ı üretir
- `gamepad-v*` etiketi atıldığında (veya Actions'da **Run workflow → release girdisi**
  verilerek) APK'yı GitHub Release'e yükler

## Yasal

Bu bağımsız, açık kaynak uygulamadır; Blek ile kod paylaşımı yoktur — yalnızca
kamuya açık Bluetooth HID/GATT standartları ve dekonpile analiziyle belgelenen
protokol davranışları (UUID'ler, report yapısı) kullanılmıştır. "PlayStation"
ifadesi yalnızca arayüz düzenini tanımlar; uygulama Sony ile ilişkili değildir.
Telif: © 2026, MIT lisanslı.
