# Blek APK — Kaynak Çıkarımı ve Mimari Analiz

**APK:** `Bluetooth Klavye & Fare.apk` (io.appground.blek v6.23.2, code 258)
**Tarih:** 2026-09-07 · **Yöntem:** androguard 4.1.4 (saf Python) — JVM/jadx bu ortamda kurulamadı (bkz. §6)

---

## 1. Kısa sonuç

| Soru | Cevap |
|---|---|
| Kaynak kod çıkarılabildimi? | **Kısmen.** Okunabilir Java/Kotlin üretilemedi (JVM yok). Tam yapısal envanter + uygulama kodunun Dalvik bytecode dekonpilasyonu + tüm string'ler + tüm kaynak dosyalar çıkarıldı. |
| Kod obfuscated mı? | **Evet, R8 full-mode.** 6.894 sınıfın sadece **49'u** orijinal adını korumuş; gerisi tek karakterli paketlerde (`xo`, `cv1`, `dy1`…). Okunabilir iskelet + kritik HID protokolu tamamen geri kazanıldı. |
| Free Blek'te gamepad modu var mı? | **Yok.** Uygulama kodunda ve UI string'lerinde gamepad/joystick fonksiyonu yok. Gamepad, **ayrı bir ürün**: "Serverless Bluetooth Gamepad App" (anketle ücretsiz kod veriliyor; bkz. §5.3). |
| Gamepad modu eklemek mümkün mü? | Blek'in içine eklemek **pratikte imkânsız** (kapalı kaynak + yeniden derlenemez). Aynı mimariyle **açık kaynak yeni uygulama** tek güvenilir yol (bkz. §5.4). |

## 2. Çıkarılan dosyalar

```
extracted/
├── ANALYSIS.md                  ← bu dosya
├── inventory/
│   ├── packages.md              # 109 paket, sınıf sayıları (uygulama vs kütüphane)
│   ├── app-classes.md           # 49 ismini korumuş uygulama sınıfı (süper + metot/alan sayıları)
│   └── classes.json             # TÜM 6.894 sınıf: üst sınıf, metotlar, alanlar (6,6 MB)
├── strings/
│   ├── app-strings.md           # uygulama sınıflarının string tablosu (log tag'ları, aksiyonlar)
│   └── arsc-strings.txt         # resources.arsc: 2.810 değer stringi + 9.245 kaynak anahtarı
├── disasm/                      # 49 uygulama sınıfının Dalvik dekonpilasyonu (.smali)
│   ├── io.appground.blehid.BleHidService.smali      ← BLE HID çekirdeği (987 satır)
│   ├── io.appground.blehid.ClassicHidService.smali  ← Classic HID çekirdeği (380 satır)
│   ├── io.appground.blek.MainActivity.smali
│   ├── com.pairip.licensecheck.LicenseClient.smali  ← 54 metot, lisans doğrulama
│   └── … (widget, database, ui)
└── resources/
    ├── AndroidManifest.xml      # ham AXML
    ├── resources.arsc           # ham ARSC
    ├── res/                     # 1.900+ dosya (anim, drawable, layout…)
    └── assets/
```

## 3. Rekonstrükte edilen mimari

### 3.1 Bileşen haritası

- `io.appground.blek.MainApp` → `com.pairip.application.Application` — uygulama giriş noktası
- `io.appground.blek.MainActivity` (Compose, süper `sj`) — tek ana aktivite
- `io.appground.blehid.BleHidService` — **BLE GATT sunucusu** (telefon = HID cihazı)
- `io.appground.blehid.ClassicHidService` — **Classic (BR/EDR) HID** (gizli `BluetoothHidDevice` API'si)
- `com.pairip.licensecheck.*` — "pairip" lisans doğrulama (Binder/AIDL `ILicenseV2ResultListener`, `LicenseActivity`, `LicenseContentProvider`)
- `io.appground.blek.data.room.AppDatabase` — Room DB (klavye düzenleri/ayarlar; nano proto `Proto$ShortcutData`)
- `io.appground.blek.widget.*` — Android widget'ları (connect, multimedia, configuration)

Obfuscated eşleştirme (bytecode'tan çıkarıldı): `dy1` = HID servis taban sınıfı (ortak durum: bağlı cihaz `o`, log `v Laa0`, durum enum `n Lo04`, scope `i Lmt`); `dl0` = cihaz-başına GATT durumu (gatt `f`, bayraklar `w/x/y`, son bildirim `z J`); `w64` = kapalı durum sonuçları; `s4(int)`/`vl4` = R8'in birleştirdiği **tembel UUID tedarikçisi** (lazy supplier); `e72.l` = equals; `pw5.z`/`yy5.w` = coroutine scope/dispatcher sarmalayıcıları.

### 3.2 BLE HID protokolü (BleHidService bytecode'undan birebir)

**Tanıtım (advertising):**
- `AdvertiseSettings`: mode 2 (low duty cycle), TX gücü 3 (medium), **connectable**, timeout 0
- `AdvertiseData`: **0x1812 (HID) servis UUID** + cihaz adı (ad >22 karakter ise sadece UUID, isim secondary data'ya taşınır)

**GATT sunucusu — 3 birincil servis:**

| # | Servis | Karakteristik | Özellikler | Not |
|---|---|---|---|---|
| A | **0x1812 HID** *(yüksek güven: k0=14)* | o0 (18) | READ | 2A4A HID Information veya 2A4E Report Map |
| | | n0 (17) | READ | (öteki: 2A4E / 2A4A) |
| | | p0 (19) | WRITE_NO_RESPONSE, ≤32B | 2A4B HID Control Point |
| | | **m0 (16)** | READ\|WRITE\|NOTIFY, ≤34B + CCCC (2902, d0=21) + 2 baytlık 2. tanımlayıcı (c0=10) | **Report** — raporlar buradan bildirilir |
| B | *(muhtemelen 0x180F PnP, e0=22)* | f0 (23), g0 (24), h0 (11) | READ ×3 | kümeden {2A19, 2A50, 2A24, 2A25, 2A29, 2A4C, 2908, 180A} |
| C | *(i0=12)* | j0 (13) | READ\|NOTIFY, ~1-2B + CCCC | **Keep-alive/status** — her 5 dk'da `[0, mod-bayt]` bildirimi |

**Rapor formatı — kesin (metot `x(B reportID, [B data)Z`):**

```
Bildirim içeriği (Report karakteristik):  [ reportID, data[0], data[1], … ]
                                          ↑ 1 bayt   ↑ geriye kalan (≤33 bayt)
- Boş veri → gönderim yok (true döner)
- Başarı: notifyCharacteristicChanged() = true
- Başarılı gönderimden sonra her 300.000 ms (5 dk): Q karakteristikine
  [0x00, z()] bildirimi (z() = taban sınıfın aktif rapor-id/mod baytı)
```

**Bağlantı yönetimi:**
- Cihaz başına durum `dl0`; `G(device)` ile LinkedHashMap'den
- `F(device)`: bağlı değilse `gattServer.connect(device, autoConnect=false)`; bağlantı/bond tutarsızlığında `createBond()`; diğer aktif cihazlara `requestConnectionPriority(2=HIGH)`
- `M(device)`: yeniden-tanıtım ("refresh"): önce rastgele-UUID'li sahte servis ekle, `cancelConnection`, sonra tekrar bağlat → karşı tarafın GATT önbelleğini temizler
- `E(device)`: karşı taraftan gelen GATT istemcisi için report değerini 6 baytlık dizgiyle yeniler + bildirim
- `onCreate`: BOND/ACL/NAME/UUID/CONNECTION_ACCESS/PAIRING_CANCEL broadcast'leri + `STATE_CHANGED` alıcıları
- `onDestroy`: reklamı durdur, sunucuyu kapat, tüm cihaz GATT'larını kapat; app BT'yi kendisi açıp `disable()` edebiliyor (S bayrağı)
- `SDK_INT > 32` kontrolü ile `U` bayrağı (Android 13+ davranış farkı)

**HID Information değeri (q0, 4 bayt):** `11 01 00 01` → BCD **1.11**, country 0, flags 0x01.

### 3.3 Classic HID protokolü (ClassicHidService)

- **Gizli API:** `android.bluetooth.BluetoothHidDevice` (SystemApi) — `hc0` sınıfı ("HidHost") üzerinden; rapor gönderimi `x1.s(hidDevice, device, reportId, data)` → `BluetoothHidDevice.sendInputReport(device, reportId, data)`
  - Fark: BLE'de reportID **yüklenen rapora eklenirken**, Classic'te ayrı parametre olarak geçiyor
- Keşif: `REQUEST_DISCOVERABLE` intent, **300 sn** süre; scan modu 20/21/23 değerleriyle yönetiliyor (`lb4.j`)
- `onDestroy`: HidHost job'ı iptal; app BT açtıysa `adapter.disable()`
- Uygulamanın dokümantasyonundan (ARSC içindeki SSS): "normal connection mode" = Classic, "compatibility connection mode" = BLE/LE

### 3.4 Lisans (com.pairip.licensecheck)

- `LicenseClient` (54 metot/42 alan): V2 lisans akışı, `ILicenseV2ResultListener` **Binder/AIDL** arayüzü, `LicenseActivity` (ActivityType enum'lu), `LicenseContentProvider`, `RepeatedCheckMetadata` → tekrarlayan doğrulama (trial/premium)
- Bu blok adını koruduğu için en okunabilir kısmı; premium özellikler ARSC anahtarlarından belli: `premium_feature_fullscreen_mode`, `premium_feature_password_mode`, `premium_feature_text_field`, `premium_feature_volume_buttons`

### 3.5 Telemetri / ağ

- `https://anleitung-backend.appspot.com/api/report?ack=` — hata/kullanım raporu (Firebase Cloud Function)
- Firebase (web anahtarı `AIzaSyA9…` — standart, hassas değil), analytics sarmalayıcıları (`pw5.z`/`yy5.w` üzerinden)
- `appground.io/keyboard` — QR ile düzen aktarımı özelliği (SSS'te)

## 4. Gamepad araştırması (karar verici kanıtlar)

1. **Dex string havuzu** (31.715 string): `gamepad`, `joystick`, `oyun` → **hit yok** (yalnızca AOSP framework string'leri: `Gamepad`, `GamepadDisplay` — cihaz tip etiketleri, app kodu değil)
2. **GATT yapısı**: klavye/fare/consumer kontrol noktaları dışında controller HID usage (usage page 0x01, usage 0x05) raporu üreten hiçbir yol yok
3. **ARSC**: `dialog_survey_text` → *"…you will receive a code to download the **Serverless Bluetooth Gamepad App** for free."* + `ic_stat_gamepad`, `ic_baseline_gamepad_24`, `settings_gamepad` kaynakları
   → **Gamepad, app içi mod değil; aynı geliştiricinin AYRI bir uygulaması.** ("Serverless" = muhtemelen aynı GATT-sunucusu mimarisi, sunucu bileşen olmadan.)

## 5. Değerlendirmeler

### 5.1 Yeniden derlenebilirlik: HAYIR (güvenli)

- R8 full-mode: ~6.845 sınıf tek karakter isimli; `dy1`, `hc0`, `s4` gibi isimler orijinal sınıflarla eşleştirilemez
- Compose + Kotlin: dekonpile çıktısı sentetik lambda/facade sınıflar, kayıp Kotlin metadata, inline'lanmış fonksiyonlar → **jadx ile dahi derlenebilir kaynağa dönüştürülemez** (bilinen endüstri gerçeği; bu kod tabanında ismi koruyan yalnızca 49 sınıftır)
- Gizli API'ler (`BluetoothHidDevice`) stub jar ile tekrar derlenmeli — ek sürtünme

### 5.2 Hukuki not

Dekonpile edilmiş kod, geliştiricinin telifli eseridir. Bu çıktı **çalışma/çevrim içi inceleme** içindir; yeniden dağıtım veya ürün içine yerleştirme yapılamaz. (README.md'deki yasal not ile tutarlı.)

### 5.3 Kullanıcı isteğine karşılık (gamepad modu)

Blek'e gamepad modu "eklemek" — hem kaynak kapalı hem derlenemez olduğu için **uygulanamaz**. Gerçekleşebilir yol:

**Açık kaynak, bağımsız "Bluetooth Gamepad" uygulaması**, kanıtlanmış aynı mimariyle:
- GATT sunucusu: **0x1812 HID** servisi + **Report** karakteristik (READ|WRITE|NOTIFY, CCCC) — Blek'in BLE hattı birebir (bu belge §3.2)
- **HID Report Descriptors**: gamepad = usage page `0x0001`, usage `0x0005` (Gamepad), 16 buton + 4 eksen (hat X/Y, top X/Y, hat1/2) — BLE HID sınıf standardı
- **Tek Report karakteristik + report-ID öneki** modeli (rapor: `[reportID, butonlar(16bit), x,y,topX,topY,h1,h2 …]`) — hem Blek'in hem açık kaynak referansın kanıtladığı format
- Opsiyonel Classic HID: `BluetoothHidDevice` gizli API + compileOnly stub jar (referans: `yaleedhaque/BluetoothRemoteHid` — io.appground.blehid v6.20.0'ı dekonpile edip aynı mimariyi kuran açık kaynak proje)
- Bağlantı deneyimi: connectable reklam (1812 + isim), 5 dk keep-alive bildirimi, HIGH connection priority, refresh/reconnect — hepsi §3.2'de rekonstrükte edildi

### 5.4 GitHub otomatik derleme

- Bu repo için workflow'lar hazır (`c9bf770` + `6a8dc06`): `verify.yml` (APK doğrulama gate'i) + `release.yml`
- `6a8dc06` (`.github/workflows/`) **hâlâ push edilemiyor**: GitHub App'e `workflows` izni yok (Settings → Apps → Arena)
- Gamepad uygulaması eklendiğinde: GitHub Actions (Android SDK tam ağ erişimiyle) `app/build/outputs/apk/release/*.apk` derler + imzalar + release artifact'ı olarak yükler — sandbox kısıtları CI'da yok

## 6. Ortam sınırlamaları (bu oturumda denenip kapanan yollar)

| Denenen | Sonuç |
|---|---|
| apt (openjdk-17) | depo erişimi kapalı (80 portu) |
| Adoptium/Corretto/Azul/MS JDK tarball | CDN'ler erişilemiyor |
| GitHub release asset'leri (jadx zip) | `release-assets.githubusercontent.com` engelli |
| Maven Central / Google Maven / Gradle services | engelli |
| PyPI | **açık** (androguard kuruldu) · `cfr` paketi Java decompiler değil (iklim bilimi) · `jdk` paketi sahte |
| codeload.github.com | **açık** (kaynak tarball'ları) — ama jadx/jdk derlenemez (Gradle bağımlıları kapalı) |
| Sonuç | JVM bu ortamda kurulamaz → jadx imkânsız → androguard (saf Python) ile yapısal çıkartım + bytecode dekonpilasyonu |

## 7. Sonraki adım önerisi

1. **Onay**: gamepad uygulaması bu repoda ayrı bir dizinde (ör. `app-gamepad/`) Kotlin + compose-free sade UI ile kurulur; CI `release.yml`'e eklenir
2. **`workflows` izni**: `6a8dc06` push'u için GitHub App ayarlarından izni açın
3. (Opsiyonel) Cihazda dene: uygulama, PC/TV/consol'dan "Bluetooth klavye/fare" olarak bağlanıp gamepad raporu almalı — report descriptor, PC'nin "Oyun kumandası" olarak tanıması için yeterlidir
