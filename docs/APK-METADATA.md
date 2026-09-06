# APK Metadata — Bluetooth Klavye & Fare

Son güncelleme: **2026-09-06** (v6.23.2)

## Genel

| Alan | Değer |
|---|---|
| Uygulama adı | Bluetooth Keyboard & Mouse (Blek) |
| Paket adı | `io.appground.blek` |
| Sürüm | `6.23.2` (versionCode **258**) |
| Geliştirici | AppGround (appground.io) |
| Dosya boyutu | 9.632.998 byte (~9,2 MB) |
| SHA-256 | `35a328c4fa716c38c588fbe4e337ad95d5aa46d7d1793d7200cb815fd8517ec7` |
| Türetilmiş APK id | 4 (`arm64-v8a`, `xhdpi` split) |
| Derleme | compileSdk 37, platformBuild 17 |
| Destek | [Play Store](https://play.google.com/store/apps/details?id=io.appground.blek) |

## SDK

| Alan | Değer |
|---|---|
| minSdkVersion | 32 (Android 12L) |
| targetSdkVersion | 36 (Android 16) |

## İzinler

### Runtime (kullanıcı onaylı)

| İzin | Açıklama |
|---|---|
| `android.permission.BLUETOOTH_SCAN` | `neverForLocation` bayrağıyla (konum izni gerekmez) |
| `android.permission.BLUETOOTH_CONNECT` | Bağlı cihazlara klavye/fare emri gönderimi |
| `android.permission.BLUETOOTH_ADVERTISE` | Cihaz keşfedilebilirliği (bağlantı kurulumu) |
| `android.permission.POST_NOTIFICATIONS` | Bildirim (çözülmemiş bağlantı, durum) |

### Normal (otomatik)

| İzin | Açıklama |
|---|---|
| `android.permission.INTERNET` | Firebase cloud işlevleri, licensing |
| `android.permission.ACCESS_NETWORK_STATE` | Ağ durumu kontrolü |
| `android.permission.ACCESS_WIFI_STATE` | Ağ ortamı tespiti |
| `android.permission.WAKE_LOCK` | Arka plan işlemleri |
| `android.permission.VIBRATE` | Tuş geri bildirimi (haptik) |
| `android.permission.EXPAND_STATUS_BAR` | Durum çubuğu bildirimleri |
| `android.permission.FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE` | BLE HID ön planda servis (foregroundServiceType=connectedDevice) |
| `android.permission.RECEIVE_BOOT_COMPLETED` | WorkManager yeniden zamanlama |
| `android.permission.BLUETOOTH` / `BLUETOOTH_ADMIN` | Yalnızca API ≤ 30 (maxSdkVersion=30) |
| `com.google.android.c2dm.permission.RECEIVE` | FCM push |
| `com.android.vending.BILLING` / `com.android.vending.CHECK_LICENSE` | Play Billing (Pro sürüm lisansı) |

### Uygulamanın tanımladığı izin

| İzin | protectionLevel |
|---|---|
| `io.appground.blek.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | signature (0x2) — androidx dinamik receiver koruması |

## Bileşenler (43)

| Tür | Sayı | Dikkat çekenler |
|---|---|---|
| activity | 12 | `MainActivity` (launcher + derin bağlantı + paylaşım hedefi), widget konfigürasyonu, Play Billing, GoogleApi, Glance trampoline'ları, `PreviewActivity` (⚠ tooling sızıntısı — Bkz. SECURITY.md F8) |
| service | 13 | `BleHidService`, `ClassicHidService` (exported=false, foregroundServiceType=connectedDevice), Firebase/MLKit keşif, WorkManager, GlanceRemoteViews (BIND_REMOTEVIEWS) |
| receiver | 15 | App widget alıcıları (APPWIDGET_UPDATE), FCM, WorkManager constraint proxy'leri (DUMP/BIND_JOB_SERVICE korumalı) |
| provider | 3 | androidx-startup, MLKit init, FirebaseInit (hepsi exported=false) |

### Derin bağlantılar (App Link, autoVerify)

- `https://appground.io/keyboard`
- `https://appground.io/layout/*`
- Paylaşım: `file://`, `content://` (`application/octet-stream`, `*.layout`), `SEND text/plain`

## İmza

| Şema | İmzalayıcı | Sertifika SHA-256 |
|---|---|---|
| v2 | `O=appground.io` (RSA-2048, seri `0x31c6da2c`, 2017→2042) | `9e42da49bc76491db6bcbe914b22f5c9aadc8f8c50dcbf90fec43bf2c7637901` |
| v3 | aynı | aynı |
| v4 (gömülü) | Google Inc. / Android (2020→2050) | `b01dab436fbc4c943fdc16a1040a32cb34ac9a898a813935a4b5d95ac2a636fb` |

Uzun ömürlü geliştirici parmak izleri (tüm Play sürümlerinde sabit):
SHA-1 `592293122D3185E73699506BE1A8956A35551249`.
`stamp-cert-sha256` = `3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d`
(Play hattı ayrıntısı — Bkz. SECURITY.md §2).

## Önemli bağımlılıklar

| Kütüphane | Sürüm | Amaç |
|---|---|---|
| Play Billing (com.android.billingclient) | 8.3.0 | Pro lisans/satın alma |
| Firebase (App Check, Functions, Installations, Messaging) | güncel | Bulut senkronizasyon, push, bot koruma |
| Play Integrity | güncel | App Check arka ucu |
| ML Kit Barcode Scanning | güncel | QR ile cihaz eşleştirme |
| WorkManager / Room / Glance | güncel | Arka plan iş, yerel DB, widget'lar |
| Jetpack Compose (M3) | güncel | Arayüz |

## Ağ yüzeyi

| Ana makine | Amaç |
|---|---|
| `appground.io` | Derin bağlantı (App Link) |
| `anleitung-backend.appspot.com` | Firebase Functions: çöküş/kullanım raporu |
| `firebaseinstallations.googleapis.com`, `firebaseappcheck.googleapis.com`, `*.googleapis.com` | Firebase altyapısı |
| `play.google.com` | Licensing / billing |
| `github.com`, `discord.gg` | Destek/kaynak linkleri (uygulama içi açılır) |
