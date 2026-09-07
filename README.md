# BluetoothControl

Uçtan uca doğrulanabilir bir dağıtım deposu: **Bluetooth Klavye & Fare** uygulamasının
(APK) bu depodaki kopyasının bütünlüğü, imzası ve güvenlik tabanı her sürümde otomatik
doğrulanır.

> Bu APK, **AppGround (appground.io)** tarafından geliştirilen **"Bluetooth Keyboard & Mouse"
> (Blek)** uygulamasının Google Play'den alınmış dağıtım kopyasıdır. Uygulamanın telif hakkı
> AppGround'a aittir; uygulama kapalı kaynaklıdır. Bu depo, uygulamanın kendisinin
> geliştirildiği kaynak kodu **değildir** — yalnızca dağıtım ve doğrulama altyapısı sunar.

## İçerik

| Dosya / Klasör | Açıklama |
|---|---|
| `Bluetooth Klavye & Fare.apk` | Uygulama (v6.23.2, Play Store dağıtım kopyası) |
| `CHECKSUMS.sha256` | APK'nın SHA-256 özeti (bütünlük kanıtı) |
| `scripts/verify_release.py` | Bağımsız doğrulama aracı (yalnızca Python 3 standart kütüphanesi) |
| `scripts/gen_checksums.sh` | Yeni APK yüklemeden sonra checksum dosyasını yeniden üretir |
| `docs/SECURITY.md` | Güvenlik denetim raporu (2026-09-06) |
| `docs/APK-METADATA.md` | APK metadata'sı: paket, sürüm, izinler, bileşenler, imzalar, bağımlılıklar |
| `extracted/ANALYSIS.md` | APK kaynak çıkartımı + rekonstrükte edilmiş BLE HID protokolü analizi (2026-09-07) |
| `app-gamepad/` | **BLE Gamepad** — bu analizden yola çıkarak geliştirilen, açık kaynak Android uygulaması (telefonu BLE oyun kumandası olarak gösterir); kendi Gradle projesi ve otomatik APK derlemesi |
| `.github/workflows/` | CI (her push/PR'da doğrulama), otomatik Release (etiketle) ve gamepad APK derlemesi |

## Hızlı doğrulama

Gereksinim: yalnızca `python3` (3.8+).

```bash
# 1) Bütünlük + imza yapısı + sertifika parmak izi + manifest güvenlik kontrolü
python3 scripts/verify_release.py "Bluetooth Klavye & Fare.apk"

# 2) Makine okunur rapor (CI'da kullanılır)
python3 scripts/verify_release.py "Bluetooth Klavye & Fare.apk" --json

# 3) Manuel SHA-256 kontrolü
sha256sum -c CHECKSUMS.sha256
```

Doğrulama aracı şunları denetler:

1. APK'nın SHA-256 değeri `CHECKSUMS.sha256` ile eşleşiyor mu?
2. APK imza bloğu (v2/v3/v4) mevcut ve tutarlı mı?
3. İmzalayıcı sertifika, uygulamanın **sabitlenmiş (pinned)** uzun ömürlü geliştirici
   kimliğiyle eşleşiyor mu?
   - SHA-1: `592293122D3185E73699506BE1A8956A35551249` (tüm yayın sürümleriyle aynı)
   - SHA-256: `9e42da49bc76491db6bcbe914b22f5c9aadc8f8c50dcbf90fec43bf2c7637901`
4. Manifest güvenlik tabanı: `debuggable` kapalı, `allowBackup=false`,
   `targetSdk >= 34`, açık (cleartext) HTTP kapalı, `intent-filter` içeren her bileşen
   `exported`'ı açıkça belirtiyor mu?

**Çıkış kodu `0` = tüm zorunlu kontroller geçti.**

## Kaynak çıkartımı (APK extraction)

Uygulama kapalı kaynaklıdır ve R8 full-mode ile obfuscated'dır; bu yüzden dekonpile
çıktısı yeniden derlenebilir bir kaynak değildir. Yine de APK'nın tamamı çalışma
amacıyla çıkarıldı ve protokol düzeyinde rekonstrükte edildi:

- **`extracted/ANALYSIS.md`** — çıkartım yöntemi, rekonstrükte edilmiş mimari
  (BLE GATT sunucusu, report formatı `[reportID, payload]`, classic `BluetoothHidDevice`
  yolu, lisans/telemetri), gamepad araştırması ve sonraki adımlar.
- Tam çıkartım ağacı (sınıf envanteri, Dalvik dekonpilasyonu, string'ler, kaynak dosyalar)
  **bilerek depoya commit edilmez** — kapalı kaynak bir uygulamanın dekonpile kodu
  kamu deposunda dağıtılmaz (bkz. Yasal bölüm). Depodan bağımsız olarak
  `extracted/` çalışma alanında tutulur; `.gitignore` yalnızca `ANALYSIS.md`'yi istisna olarak alır.

## Yeni sürüm yükleme akışı

```bash
# 1) Yeni APK'yı kök klasöre koy (mevcut dosyanın yerini alsın)
# 2) Checksum'ları yeniden üret
./scripts/gen_checksums.sh
# 3) Doğrula (sertifika parmak izi değiştiyse raporu inceleyip pin'i güncelle)
python3 scripts/verify_release.py "Bluetooth Klavye & Fare.apk"
# 4) docs/APK-METADATA.md ve docs/SECURITY.md'yi güncelle
# 5) Commit et ve sürüm etiketi at (etiket GitHub Release oluşturur):
git tag v6.24.0 && git push origin v6.24.0
```

## Otomasyon

- **CI (`verify.yml`)**: Her push/PR'da yukarıdaki doğrulamayı çalıştırır; APK değiştikçe
  doğrulama aracı dosya özeti / sertifika pinini kıyasladığı için sahte veya bozulmuş bir
  APK CI'da reddedilir.
- **Release (`release.yml`)**: `v*` etiketi atıldığında APK + checksum dosyasıyla bir
  GitHub Release oluşturur (önce doğrulamayı çalıştırır, geçmezse release açılmaz).
- **Gamepad derlemesi (`gamepad.yml`)**: `app-gamepad/**` altında değişiklik olan her
  push/PR'da Gradle ile APK derler ve artifact olarak yükleştirir; `gamepad-v*` etiketi
  atıldığında APK'yı GitHub Release'e ekler.

## BLE Gamepad (app-gamepad/)

Blek'in kapalı kaynak olması ve oyun kumandasının ayrı bir uygulama olması nedeniyle
(`extracted/ANALYSIS.md` §4), aynı protokolle bağımsız ve **açık kaynak** bir Android
uygulaması geliştirildi: **`app-gamepad/`** — paketi `io.github.strongbomber.blegamepad`.

- Telefon, BLE GATT sunucusu olarak bağlanan cihaza (PC/TV/konsol) **HID gamepad**
  görünümünde: 2 joystick + 6 aksiyon tuşu + yön tuşları.
- Kullanılan protokol, `extracted/ANALYSIS.md` §3.2'de belgelenen rekonstrükte edilmiş
  Blek mimarisinin aynısıdır (0x1812 HOGP + Report `2A4D` + `[reportID, payload]`
  bildirimi + 5 dakikalık keep-alive).
- Sıfır harici bağımlılık; yalnızca Android platform API'leri.
- Derleme: `cd app-gamepad && ./gradlew assembleDebug` (JDK 17 + Android SDK 34).
- Kullanım ve tuş eşlemesi: [`app-gamepad/README.md`](app-gamepad/README.md).

## Yasal / kaynak

- Uygulama: [AppGround – Bluetooth Keyboard & Mouse](https://appground.io) (Google Play:
  `io.appground.blek`)
- Destek / sorun bildirimi: [AppGround-io/bluetooth-keyboard-and-mouse-support](https://github.com/AppGround-io/bluetooth-keyboard-and-mouse-support)
- Bu depodaki ek dosyalar (doküman, script, workflow) "olduğu gibi" sağlanır; uygulama
  içeriği üzerinde herhangi bir hak iddiası taşımaz.
- `extracted/ANALYSIS.md` biz tarafından hazırlanan bağımsız bir analizdir; dekonpile
  edilmiş kod parçaları dağıtılmaz (çıkartım ağacı yalnızca çalışma alanında tutulur).
- `app-gamepad/` bağımsız, MIT lisanslı, açık kaynak bir uygulamadır; Blek ile paylaşılan
  kod yoktur — yalnızca Bluetooth HID/GATT standartları ve belgelenen protokol
  davranışları (UUID'ler, report yapısı) kullanılır.
