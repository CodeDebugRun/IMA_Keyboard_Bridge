# IMA Keyboard Bridge - Kurulum Rehberi

## Gereksinimler

- **Java JDK 21** veya üzeri
- **Maven 3.8+**
- **Windows 10/11** (Global Keyboard Hook için)
- **WiX Toolset 3.x** (EXE oluşturmak için - opsiyonel)

---

## 1. Geliştirme Ortamı Kurulumu

### Java JDK 21 Kurulumu

1. [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21) veya [Adoptium](https://adoptium.net/) adresinden indirin
2. Kurulumu tamamlayın
3. Ortam değişkenlerini ayarlayın:
   ```
   JAVA_HOME = C:\Program Files\Java\jdk-21
   PATH += %JAVA_HOME%\bin
   ```
4. Doğrulama:
   ```cmd
   java -version
   ```

### Maven Kurulumu

1. [Maven](https://maven.apache.org/download.cgi) adresinden indirin
2. `C:\Program Files\Maven` klasörüne çıkartın
3. Ortam değişkenlerini ayarlayın:
   ```
   MAVEN_HOME = C:\Program Files\Maven\apache-maven-3.9.x
   PATH += %MAVEN_HOME%\bin
   ```
4. Doğrulama:
   ```cmd
   mvn -version
   ```

---

## 2. Projeyi Derleme

### Kaynak Koddan Derleme

```cmd
cd C:\001_IMA_PRINT\keyboard_bridge

# Bağımlılıkları indir ve derle
mvn clean install

# Uygulamayı çalıştır (geliştirme)
mvn javafx:run
```

### Fat JAR Oluşturma

```cmd
mvn clean package shade:shade
```

Çıktı: `target/keyboard_bridge-1.0-SNAPSHOT.jar`

---

## 3. EXE Oluşturma (jpackage)

### WiX Toolset Kurulumu (Gerekli)

1. [WiX Toolset 3.14](https://github.com/wixtoolset/wix3/releases) indirin
2. Kurulumu tamamlayın
3. PATH'e ekleyin: `C:\Program Files (x86)\WiX Toolset v3.14\bin`

### jpackage ile EXE Oluşturma

```cmd
cd C:\001_IMA_PRINT\keyboard_bridge

# Önce JAR oluştur
mvn clean package

# jpackage ile EXE oluştur
jpackage ^
  --type exe ^
  --name "IMA Keyboard Bridge" ^
  --app-version 1.0.0 ^
  --vendor "LEBO" ^
  --icon src/main/resources/icon.ico ^
  --input target ^
  --main-jar keyboard_bridge-1.0-SNAPSHOT.jar ^
  --main-class de.lebo.keyboard_bridge.Launcher ^
  --dest dist ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --java-options "-Xmx256m"
```

Çıktı: `dist/IMA Keyboard Bridge-1.0.0.exe`

---

## 4. Konfigürasyon

### config.properties Dosyası

Uygulama ile aynı klasörde `config.properties` dosyası bulunmalıdır:

```properties
# Server Ayarları
server.host=localhost        # Socket sunucu adresi
server.port=4000             # Socket port
api.port=8080                # REST API port

# Export Klasörü
export.folder=C:\\DOCUMENTS\\Exported_ZPL_Etiketten_Code
```

### Production Ayarları

Sunucuya deploy ederken `server.host` değerini güncelleyin:

```properties
server.host=192.168.1.100    # veya sunucu hostname
```

---

## 5. Dağıtım

### Gerekli Dosyalar

```
IMA_Keyboard_Bridge/
├── IMA Keyboard Bridge.exe   (veya keyboard_bridge.jar)
├── config.properties
└── (JRE dahil - jpackage ile)
```

### Manuel JAR Dağıtımı

Eğer EXE yerine JAR kullanıyorsanız:

```cmd
java -jar keyboard_bridge.jar
```

---

## 6. Sorun Giderme

### Uygulama Başlamıyor

1. Java sürümünü kontrol edin: `java -version` (21+ olmalı)
2. `config.properties` dosyasının mevcut olduğunu kontrol edin
3. Firewall ayarlarını kontrol edin (port 4000, 8080)

### Barkod Okuyucu Çalışmıyor

1. Uygulamayı **Yönetici olarak** çalıştırın
2. Başka bir uygulama keyboard hook kullanıyor olabilir
3. Windows Defender/Antivirüs istisnalarına ekleyin

### Sunucuya Bağlanamıyor

1. `config.properties` içindeki `server.host` değerini kontrol edin
2. Sunucunun çalıştığını doğrulayın
3. Ağ bağlantısını test edin: `ping <server.host>`

---

## 7. API Endpoints

| Endpoint | Açıklama |
|----------|----------|
| `/api/export/auftrag/{nr}` | Auftrag CSV export |
| `/api/export/auftrag/{nr}/json` | Auftrag JSON export |
| `/api/export/auftrag/{nr}/pos/{pos}` | Position CSV export |
| `/api/export/auftrag/{nr}/pos/{pos}/json` | Position JSON export |

---

## Versiyon Geçmişi

| Versiyon | Tarih | Değişiklikler |
|----------|-------|---------------|
| 1.0.0 | 2025-01 | İlk sürüm - Global keyboard hook, config.properties |

---

**Destek:** LEBO IT Departmanı
