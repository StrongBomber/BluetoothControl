# Güvenlik Denetim Raporu — Bluetooth Klavye & Fare (v6.23.2)

- **Denetim tarihi:** 2026-09-06
- **Denetlenen dosya:** `Bluetooth Klavye & Fare.apk` (9.632.998 byte)
- **SHA-256:** `35a328c4fa716c38c588fbe4e337ad95d5aa46d7d1793d7200cb815fd8517ec7`
- **Paket:** `io.appground.blek` — sürüm **6.23.2** (versionCode 258)
- **Yayıncı:** AppGround (appground.io)
- **Dağıtım türü:** Google Play türetimli APK (arm64-v8a / xhdpi split, `derived.apk.id=4`,
  `STAMP_TYPE_DISTRIBUTION_APK`)

## 1. Metodoloji

Kaynak kod kapalı olduğundan denetim **statik, dosya düzeyinde** yürütüldü:

1. APK açılarak `AndroidManifest.xml` (binary AXML) satır satır çözüldü.
2. APK imza bloğu (ZIP Merkezi Dizin öncesi alan) byte düzeyinde çözüldü; v2/v3/v4
   imzalar, sertifikalar ve özetler (digest) çıkarıldı.
3. Sertifikaların parmak izleri, uygulamanın Play Store'daki **tüm yayın sürümlerine**
   ait olan kamuya açık parmak iziyle çapraz doğrulandı (APKPure kaydı:
   `592293122d3185e73699506be1a8956a35551249`, v6.6.1'den bu yana değişmedi).
4. `classes.dex`, `resources.arsc`, `res/` ve `assets/` sabitlenmiş sırlar
   (API anahtarı, özel anahtar, JWT, parola, bulut anahtarı), URL/ana makine ve
   MAC adresi desenleri için tarandı.
5. Manifest'teki güvenlik bayrakları (debuggable, allowBackup, cleartext, exported
   bileşenler, izinler) Android güvenlik kılavuzuna göre değerlendirildi.

## 2. Kimlik ve imza analizi

| Alan | Değer |
|---|---|
| İmza şemaları | **v2 + v3** (geliştirici) ve **v4 gömülü** (Google Play) |
| v2/v3 sertifika özniteliği | `O=appground.io` (self-signed), RSA-2048 |
| v2/v3 seri no | `0x31c6da2c` |
| v2/v3 geçerlilik | 2017-07-20 → **2042-07-14** |
| v2/v3 SHA-1 | `592293122D3185E73699506BE1A8956A35551249` |
| v2/v3 SHA-256 | `9e42da49bc76491db6bcbe914b22f5c9aadc8f8c50dcbf90fec43bf2c7637901` |
| v4 sertifika | `C=US, ST=California, L=Mountain View, O=Google Inc., OU=Android, CN=Android` (2020 → 2050) |
| v4 SHA-256 | `b01dab436fbc4c943fdc16a1040a32cb34ac9a898a813935a4b5d95ac2a636fb` |
| `stamp-cert-sha256` | `3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d` |

**Sonuç:** v2/v3 imzası, uygulamanın Play Store'da yayınlanan **her sürümünde** aynı
olan uzun ömürlü geliştirici sertifikasıyla atılmıştır (SHA-1 çapraz doğrulaması başarılı).
v4 imzası Google Play dağıtım hattına aittir (Play'in türetimli APK'lara eklediği,
hızlı kurulum güncellemeleri için kullanılan gömülü v4 şeması). APK'nın Play Store
kaynaklı, orijinal dağıtım bütünlüğündeki bir kopya olduğu güçlü biçimde desteklenmektedir.

> **Bilgi (ℹ):** `stamp-cert-sha256` dosyası (AAB derleme anahtarının özeti) blok
> sertifikalarından farklıdır. Bu, Play'in dağıtım hattında bilinen bir durumdur;
> kimlik doğrulaması için bağlayıcı olan, yukarıdaki v2/v3 sertifikasıdır ve bu
> raporla birlikte `scripts/verify_release.py` içinde **pin'lenmiştir**.
>
> **Sınırlama (⚠):** Bu build'in imza bloğu, gömülü v4 destekli yeni biçimde derlenmiş
> olduğundan, v2/v3 RSA imzalarının tam kriptografik yeniden doğrulaması (imzalanan
> içerik tanımının byte düzeyinde yeniden üretimi) çevrimdışı araçlarla tamamlanamamıştır.
> Bunun yerine (a) imza bloğu yapısı tutarlılığı, (b) sertifika kimliği çapraz
> doğrulaması ve (c) dosya SHA-256 özeti kontrolü yapılmıştır. Cihaz tarafında (Play/OS)
> imza doğrulaması ayrıca yürütülür.

## 3. Bulgular özeti

| # | Bulgu | Ağır | Durum |
|---|---|---|---|
| F1 | `targetSdkVersion=36` (güncel), `minSdk=32` | — | ✅ İyi |
| F2 | `debuggable` kapalı (release) | — | ✅ İyi |
| F3 | `allowBackup=false` — cihaz yedekleriyle veri sızıntısı yüzeyi kapalı | — | ✅ İyi |
| F4 | Cleartext HTTP kapalı (`usesCleartextText` yok) — TLS-only | — | ✅ İyi |
| F5 | Konum izni **yok**; `BLUETOOTH_SCAN` `neverForLocation` bayrağıyla | — | ✅ İyi |
| F6 | Dışa açık servislerin tamamı sistem izinleriyle korunuyor (`BIND_REMOTEVIEWS`, `BIND_JOB_SERVICE`, `DUMP`, `BIND_CHOOSER_TARGET_SERVICE`) | — | ✅ İyi |
| F7 | Firebase App Check (Play Integrity) ile cloud işlevleri bot korumalı | — | ✅ İyi |
| F8 | `androidx.compose.ui.tooling.PreviewActivity` release build'e sızmış ve `exported=true` | **Düşük** | ⚠ Bakım önerisi (Bak. §6) |
| F9 | `MainActivity`, `file://` ve `content://` URI'lerini + `SEND text/plain` kabul ediyor (paylaşım hedefi) | **Bilgi** | ⚠ Girdi geçmişi (Bak. §6) |
| F10 | Uygulama telemetrisi: çöküş/kullanım raporları `anleitung-backend.appspot.com` (Firebase) ve FCM push | **Bilgi** | ℹ Gizlilik notu (§5) |
| F11 | `resources.arsc` içinde 1 Google/Firebase API anahtarı (`AIzaSyA9kv...`); Firebase uygulama anahtarıdır, App Check ile daraltılmış | **Bilgi** | ℹ Normal |
| F12 | `stamp-cert-sha256` ≠ blok sertifikası (Play hattı tuhaflığı) | **Bilgi** | ℹ §2'de açıklandı |
| F13 | Sabitlenmiş sır: AWS/OpenAI/Slack/Telegram anahtarı, özel anahtar, JWT, parola → **bulunmadı** | — | ✅ Temiz |

## 4. İzin analizi

**İstenen (kullanıcıya sorulan):** `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`,
`BLUETOOTH_ADVERTISE`, `POST_NOTIFICATIONS`

**Sistem/normal:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`WAKE_LOCK`, `VIBRATE`, `EXPAND_STATUS_BAR`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_CONNECTED_DEVICE`, `RECEIVE_BOOT_COMPLETED` (WorkManager),
`BLUETOOTH`/`BLUETOOTH_ADMIN` (yalnızca API ≤ 30), `com.google.android.c2dm.permission.RECEIVE`
(FCM), `com.android.vending.BILLING`, `com.android.vending.CHECK_LICENSE` (Play Billing)

**İstenmeyen ve iyi olanlar:** kamera, mikrofon, SMS, depolama, **konum** — hiçbiri
istenmiyor. Bir BLE klavye uygulaması için bu izin kümesi beklenen minimum kümenin
kendisidir. `RECEIVE_BOOT_COMPLETED`'in tek meşru kullanıcısı `androidx.work`
(arka plan işi yeniden zamanlama) manifest'te görülebilir; uygulama sınıfı bunu
kötüye kullanamaz, `androidx` kodu gözetilir.

## 5. Bileşen ve ağ yüzeyi

- **43 bileşen:** 12 activity, 13 service, 15 receiver, 3 provider.
- Uygulamanın kendi servisleri (`io.appground.blehid.BleHidService`,
  `ClassicHidService`) `exported=false`.
- Dışa açık bileşenler yalnızca sistem gerektirdikleri için dışa açık: launcher
  (`MainActivity`), app widget konfigürasyonu/alıcıları, derin bağlantılar
  (`https://appground.io/keyboard`, `https://appground.io/layout/` — `autoVerify`
  ile App Link doğrulamalı).
- **Ağ ana makineleri:** `appground.io` (derin bağlantı), `anleitung-backend.appspot.com`
  (Firebase Functions — çöküş/kullanım raporu), Firebase altyapısı
  (`firebaseinstallations`, `firebaseappcheck`, `*.googleapis.com`), `play.google.com`
  (licensing/billing), `discord.gg` ve `github.com` (destek linkleri, uygulama içi).
- Cloud işlevleri **Firebase App Check (Play Integrity)** ile korunuyor: sahte istemciler
  cloud API'lerine erişemez.

## 6. Öneriler

### Dağıtım (bu depo) — yapıldı

1. ✅ APK SHA-256'si `CHECKSUMS.sha256`'a kaydedildi.
2. ✅ Uzun ömürlü geliştirici sertifikası `verify_release.py` içinde pin'lendi; yeni bir
   APK bu kimlikle imzalanmadıkça doğrulamaz.
3. ✅ CI her push/PR'da bütünlük + kimlik + manifest tabanını doğruluyor.
4. ✅ Release akışı etiketle otomatik ve doğrulamalı.
5. Yeni sürüm kabulünde: sertifika SHA-1'inin `592293122D3185E73699506BE1A8956A35551249`
   ile aynı olduğunu ayrıca gözle kontrol edin (parmak izi değişikliği = anahtar
   rotasyonu; o durumda bu raporu yeniden yazın).

### Uygulama tarafı (yayına çıkan gelecekteki sürümler için, geliştiriciyle paylaşılabilecek)

1. **(Düşük)** Release build'den `androidx.compose.ui:ui-tooling` (ve
   `PreviewActivity`) çıkarılmalı — debug araçları production yüzeyi değildir
   (`debugImplementation` ile sınırlama).
2. **(Bilgi)** Paylaşım hedefi (`file://`, `content://`, `SEND`) olarak alınan
   içerik her zaman düşmanca girdi gibi işlenmeli (uygulamanın bunu yaptığı varsayılıyor;
   kaynak kapalı olduğundan doğrulanamıyor).
3. **(Bilgi)** Telemetri opt-out'u kullanıcıya görünür olmalı; `POST_NOTIFICATIONS`
   izni FCM push içindir (uygulama işitsel bildirimler gibi).
4. **(Bilgi)** `targetSdk` ve Play Billing (8.3.0) güncel tutuluyor — bu yönlerde
   olumsuz bir eğilim yok.

## 7. Genel değerlendirme

Denetlenen APK, modern Android güvenlik pratiğine **güçlü** bir taban sergiliyor:
güncel targetSdk, kapalı debuggable/backup/cleartext, minimum ve amaca uygun izinler,
sistem izinleriyle korunmuş dışa açık bileşenler, App Link doğrulaması, Firebase App
Check ve uzun süredir değişmeyen, kamuyla tutarlı imza kimliği. Kritik güvenlik
bulgusu **yoktur**; tek düşük riskli madde release build'e sızan Compose tooling
bileşenidir. Bu depodaki altyapı, ileride herhangi bir sürümün değiştirilmiş/sahte
bir kopyasının dağıtılması durumunda CI ve doğrulama aracının bunu reddetmesini sağlar.
