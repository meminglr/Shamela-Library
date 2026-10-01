# Shamela Library — Eksiklik Analizi

Tarih: 2026-10-02 · Kapsam: `app`, `apptheme`, `folioreader` modülleri, build yapılandırması, emülatörde (API 36) elle test.

Etiketler: **[Test edildi]** emülatörde gözlendi · **[Kodda doğrulandı]** kod okunarak kesinleşti · **[Muhtemel]** kod okunarak çıkarıldı, test edilmeli.

---

## 1. Kritik — veri kaybı / ana özellik bozuk

### 1.1 Favoriler her açılışta sıfırlanıyor [Kodda doğrulandı]
`LibraryViewModel` her açılışta diskteki epub'ları tarayıp `saveDownloadedBook()` ile veritabanına yeniden yazıyor ([LibraryViewModel.kt:49](app/src/main/java/com/shamela/library/presentation/screens/library/LibraryViewModel.kt:49)). Dosyadan okunan `Book` nesnesinde `isFavorite = false` oluyor ve DAO `OnConflictStrategy.REPLACE` kullanıyor ([BooksDao.kt:12](app/src/main/java/com/shamela/library/data/local/db/BooksDao.kt:12)). Bu yüzden kullanıcının favori işaretleri her açılışta siliniyor.

### 1.2 Kaydedilen alıntılar silinebilir [Muhtemel]
`Quote` tablosunun `Book` tablosuna `ON DELETE CASCADE` foreign key'i var ([Quote.kt:8](app/src/main/java/com/shamela/library/domain/model/Quote.kt:8)). SQLite'ta `REPLACE`, satırı silip yeniden ekliyor. Bu silme cascade'i tetiklerse, yukarıdaki yeniden kayıt her açılışta tüm alıntıları da siler. Test edilmeli.

### 1.3 Bölümlerde/kütüphanede tam metin arama hemzeli ve tâ-merbûtalı kelimeleri bulmuyor [Kodda doğrulandı]
Sayfa içerikleri indekslenirken normalize ediliyor: أ/إ/آ → ا, ة → ه, harekeler siliniyor ([BookPreparationWorker.kt](apptheme/src/main/java/com/shamela/apptheme/presentation/worker/BookPreparationWorker.kt)). Kitap içi aramada (`searchBook`) sorgu da normalize ediliyor. Ama **`searchCategory` ve `searchLibrary`'de sorgu normalize edilmiyor** ([DatabaseHelper.kt:124](apptheme/src/main/java/com/shamela/apptheme/data/db/DatabaseHelper.kt:124)). Sonuç olarak "أحكام", "صلاة" ya da harekeli bir sorgu bölüm aramasında hiçbir sonuç vermiyor. Sonuç sayfasındaki `indexOf(query)` vurgulaması da aynı nedenle eşleşmiyor.

### 1.4 Uygulama arka plandayken biten indirmeler yarım kalıyor [Kodda doğrulandı]
`DownloadCompleteReceiver` sadece `onStart`/`onStop` arasında kayıtlı; manifest'teki receiver'ın intent-filter'ı yok ([MainActivity.kt:80](app/src/main/java/com/shamela/library/presentation/MainActivity.kt:80)). Kullanıcı indirmeyi başlatıp uygulamadan çıkarsa:
- kitap veritabanına eklenmiyor (bir sonraki açılıştaki tarama kurtarıyor ama 1.1'deki sorunla);
- **tam metin arama indeksi (`BookPreparationWorker`) hiç oluşturulmuyor**, yani kitap içinde ve bölümlerde arama o kitap için hiçbir zaman çalışmıyor.
Toplu "bölüm indirme" de uzun sürdüğü için bu duruma çok açık.

### 1.5 Ana uygulamadaki bölüm araması ölü kod [Kodda doğrulandı]
`app/.../search/SearchViewModel.kt` içindeki `DoSearch` sonuçları hesaplayıp atıyor (`val bookPages = ...` kullanılmıyor) ve `isLoading` hiç `false` olmuyor ([SearchViewModel.kt:101](app/src/main/java/com/shamela/library/presentation/screens/search/SearchViewModel.kt:101)). Ekran şu an `SearchActivity`'ye yönlendirdiği için görünmüyor ama kafa karıştırıcı ölü kod.

---

## 2. Yüksek — hatalı davranış / çökme riski

### 2.1 İndirme listesi yüklenirken sıralama zıplıyor, yanlış kitap indirilebiliyor [Test edildi]
`DownloadViewModel` her kategori okununca ara bir liste yayınlıyor, en sonda alfabetik sıralı listeyle değiştiriyor ([DownloadViewModel.kt:57](app/src/main/java/com/shamela/library/presentation/screens/download/DownloadViewModel.kt:57)). Testte ilk satıra bastığımda liste yeniden sıralanmıştı ve başka bir kitap indirildi.

### 2.2 FTS sorgusunda çift tırnak çökme sebebi [Muhtemel]
Sorgu `"\"$query\""` şeklinde FTS5 ifadesine gömülüyor. Kullanıcı `"` yazarsa FTS sözdizimi hatası oluşur ve yakalanmamış istisna `viewModelScope` içinde uygulamayı çökertir.

### 2.3 Aynı kitap yeniden indirilince arama sonuçları çoğalıyor [Kodda doğrulandı]
FTS tablosunda `_id` UNINDEXED ve benzersiz değil. `insertBookPages` eski sayfaları silmeden ekliyor, bu yüzden yeniden indirme ya da yeniden içe aktarma her sayfayı iki kez indeksliyor.

### 2.4 Kitap silinirken indeksleme sürüyorsa yetim kayıtlar kalıyor [Muhtemel]
`DeleteBook`, `BookPreparationWorker`'ı iptal etmiyor. Silme işleminden sonra biten worker sayfaları geri yazıyor ve silinmiş kitap arama sonuçlarında çıkıyor.

### 2.5 Uygulama içi güncelleme orijinal repoya bakıyor [Kodda doğrulandı]
`AboutAppViewModel` `MahmoudRH/Shamela-Library` reposunun release'lerini kontrol ediyor ([AboutAppViewModel.kt:55](app/src/main/java/com/shamela/library/presentation/screens/about/AboutAppViewModel.kt:55)). Senin fork'un için:
- yanlış sürüm bilgisi gösteriyor;
- orijinal APK farklı imzayla geldiği için kurulum başarısız oluyor.
Ayrıca `Downloads/shamela-update.apk` zaten varsa DownloadManager dosyayı `-1.apk` olarak kaydediyor ve eski APK kurulmaya çalışılıyor.

### 2.6 Veritabanı şeması değişince tüm kullanıcı verisi siliniyor [Kodda doğrulandı]
- Room için `fallbackToDestructiveMigration()` ve `exportSchema = false` ayarlı ([DataModule.kt:46](app/src/main/java/com/shamela/library/data/di/DataModule.kt:46)). Versiyon her artırıldığında favoriler, alıntılar ve indirilen kitap kayıtları silinir.
- FTS veritabanı v2 dışındaki her yükseltmede tabloyu düşürüyor. Arama indeksi kaybolur ve yeniden oluşturulmaz.

### 2.7 Alıntı kimliği çakışması [Kodda doğrulandı]
`quoteId = text.hashCode()` ve `OnConflictStrategy.IGNORE` kullanılıyor. Aynı metin iki farklı kitaptan alıntılanırsa ikincisi sessizce kaydedilmiyor. Hash çakışmasında da farklı bir alıntı kaybolur.

### 2.8 Yedekleme / taşıma yok [Kodda doğrulandı]
`allowBackup="false"` ve dışa/içe aktarma özelliği yok. Telefon değişince ya da uygulama silinince favoriler ve alıntılar geri getirilemiyor.

---

## 3. Orta — eksik özellikler / zayıf işlevler

- **Kütüphanede ad araması zayıf:** `FilesBooksRepoImpl.searchBooksByName` her kategoride sadece **ilk** eşleşmeyi döndürüyor, normalize etmiyor ve yazar adında aramıyor ([FilesBooksRepoImpl.kt:111](app/src/main/java/com/shamela/library/data/local/files/FilesBooksRepoImpl.kt:111)).
- **Bölüm adı araması normalize değil:** `category.name.contains(query)`.
- **Normalizer eksik:** ى/ي, tatvîl (ـ), ؤ/ئ ve üstteki elif (U+0670) birleştirilmiyor.
- **Tam metin arama sonuç limiti yok:** `searchLibrary`/`searchCategory` eşleşen her sayfanın **tüm içeriğini** belleğe alıyor. Büyük kütüphanede bellek tükenmesi ve yavaşlık beklenir.
- **İndirme hataları kullanıcıya gösterilmiyor:** `STATUS_FAILED` durumunda sessizce "indirilmedi"ye dönüyor. Ağ yokken, sunucu 404 dönerken ya da disk dolduğunda mesaj çıkmıyor. Yeniden deneme ve Wi-Fi'ye özel indirme seçeneği de yok.
- **Depolama yönetimi yok:** kitapların kapladığı alan görünmüyor ve "tümünü sil" seçeneği yok.
- **Okuma ilerlemesi kütüphanede görünmüyor:** son okunan sayfa ve yüzde listede yok. "Okumaya devam et" özelliği de yok.
- **Kitap meta verisi eksik:** dışarıdan eklenen ya da arka planda tamamlanan kitaplarda yazar "-" veya boş, sayfa sayısı 0 kalıyor.
- **Arayüz sadece Arapça:** `values` ve `values-ar` var. Kotlin kodunda Arapça metin içeren **52 dosya** var, metinlerin çoğu strings.xml'de değil. Türkçe ya da İngilizce arayüz eklemek için önce bunların taşınması gerekiyor.
- **Orijinal sunucuya bağımlılık:** kitaplar orijinal geliştiricinin Cloudflare R2 deposundan iniyor. Depo kapanırsa indirme tamamen durur.

---

## 4. Teknik borç / kod kalitesi

- **Durum yönetimi dağınık:** `BooksDownloadManager` global mutable durum tutan bir `companion object` ([BooksDownloadManager.kt](app/src/main/java/com/shamela/library/presentation/utils/BooksDownloadManager.kt)). Uygulama süreci ölünce indirme–kitap eşlemesi kayboluyor, `reconcileOnReceive` yazar/sayfa bilgisi olmadan yarım kayıt oluşturuyor. `FilesBooksRepoImpl` Hilt yerine `object`. `subscribers` listesi thread-safe değil.
- **ViewModel'lerde `Toast` ve `Application` context** kullanılıyor. Aynı indirme/iptal/durum kodu 4 ViewModel'de kopyalanmış (Download, SearchResults, SectionBooks, BookDetails).
- **Toplu silme yarış koşulu:** `DeleteSelectedBooks` her kitap için ayrı coroutine açıyor ve her biri kategori listesini sıfırlayıp yeniden dolduruyor. Kategori listesi titreyebilir ya da tekrarlı görünebilir.
- **Gereksiz yük:** `_getBooksByCategory` her çağrıda JSON'u baştan parse ediyor ve önbellek yok. Her "tüm kitaplarda ara" sorgusu 72 JSON dosyasını yeniden okuyor. `categoryBookCounts` thread-safe değil.
- **Log kirliliği:** `Log.e("Mah ", ...)` gibi debug logları release'te de kalıyor. StrictMode, ana thread'de disk erişimini sürekli raporluyor (örn. `downloadBook`, `openEpub`).
- **Testler neredeyse yok:** sadece `BookSearchMatcher`, `BooksGrouping`, `ArabicNormalizer` ve bir worker testi var. ViewModel, DAO, indirme ve arama akışları test edilmiyor.
- **Build yapılandırması:**
  - `kapt` kullanılıyor (KSP'ye geçilmeli).
  - `versions.gradle` içinde kullanılmayan değerler var (`compose_compiler`, `androidGradlePlugin = 8.0.2`).
  - Release'te lint kapalı.
  - `accompanist-webview` artık desteklenmiyor.
  - folioreader'da eski ve güvenlik açıklı bağımlılıklar var: jackson 2.12.7, spring-core 4.3.19, fuel, eventbus. Kullanılıp kullanılmadıkları ayrıca kontrol edilmeli.
- **APK boyutu:** debug APK 36 MB. Asset'ler ~38 MB: `book-details` 27 MB, sözlük 9 MB. Sıkıştırma ya da sunucudan isteğe bağlı indirme düşünülebilir.

## 5. Güvenlik / mağaza uyumluluğu

- `REQUEST_INSTALL_PACKAGES` izni Play Store politikalarında özel gerekçe istiyor. Play'e çıkılacaksa uygulama içi APK güncellemesi kaldırılmalı.
- `DownloadCompleteReceiver` `RECEIVER_EXPORTED` ile kayıtlı. Herhangi bir uygulama sahte "indirme bitti" yayını gönderebilir. Etkisi düşük ama `RECEIVER_NOT_EXPORTED` yeterli.
- Okuyucunun yerel HTTP sunucusu (NanoHttpd, port 8080+) host belirtmeden açılıyor. Aynı ağdaki cihazlardan erişilebilir olabilir [Muhtemel]; sadece `127.0.0.1`'e bağlanmalı.
- Firebase yapılandırması bu kurulumda yer tutucu. Crash raporu ve push bildirimi çalışmıyor; kendi Firebase projen bağlanmalı.

## 6. Kurulumla ilgili notlar (bu oturumda yapılanlar, henüz commit edilmedi)

- NanoHttpd JitPack'ten kalktığı için kaynaktan derlenip `folioreader/libs/` klasörüne kondu, kök `build.gradle` güncellendi.
- `local.properties` dosyasına `BASE_URL` eklendi.
- `app/google-services.json` yer tutucu olarak oluşturuldu (gitignore'da).

---

## Önerilen öncelik sırası

1. Veri kaybı: 1.1 favoriler, 1.2 alıntılar (önce test), 2.6 göç stratejisi, 2.7 alıntı kimliği.
2. Arama: 1.3 normalizasyon, 2.2 tırnak çökmesi, 2.3 tekrarlı indeks, normalizer'ı genişletmek.
3. İndirme güvenilirliği: 1.4 manifest receiver ya da WorkManager tabanlı indirme, 2.1 liste zıplaması, hata mesajları.
4. Güncelleme mekanizmasını kendi repona yönlendirmek ya da kaldırmak (2.5), kendi Firebase projeni bağlamak.
5. Teknik borç: indirme mantığını tek repository'de toplamak, testler, KSP, bağımlılık temizliği.
6. Yeni özellikler: Türkçe arayüz, yedekleme ve dışa aktarma, okuma ilerlemesi, depolama yönetimi.

---

## Durum (2026-10-02, `fix/analysis-issues` dalı)

**Düzeltildi ve emülatörde doğrulandı**
- 1.1 Favoriler artık yeniden başlatmada sıfırlanmıyor. Eski sürümde hata yeniden üretildi; yeni sürümde iki yeniden başlatmadan sonra favori duruyor.
- 1.2 / 2.7 Kitaplar artık REPLACE ile değil, kullanıcı verisini koruyan "ekle ya da güncelle" ile kaydediliyor. Alıntı kimliği kararlı bir anahtar (kitap + sayfa + metin). Room 3→4 geçişi gerçek veriyle test edildi.
- 1.3 Bölüm/kütüphane aramasında sorgu normalize ediliyor. "حجية" araması 58 sonuç verdi; eski kodda 0'dı. Normalizer genişletildi (ى/ي, ؤ, ئ, tatvîl, Kur'an işaretleri); mevcut indeks FTS v3 geçişiyle yeniden normalize ediliyor.
- 1.4 İndirme alıcısı artık manifest'te. Uygulama süreci öldürülmüşken biten indirme kütüphaneye eklendi ve indekslendi (283 sayfa). Açılışta eksik indeksler de tamamlanıyor.
- Türkçe dil desteği: Ayarlar → Genel → Dil (Sistem / العربية / Türkçe). Türkçe'de arayüz soldan sağa; okuyucu ekranı Arapça kitap için sağdan sola kalıyor.

**Düzeltildi (birim testi veya kod düzeyinde)**
- 1.5 Ölü arama kodu kaldırıldı.
- 2.1 İndirme listesi tek seferde, sıralı yükleniyor.
- 2.2 FTS sorgusu kaçışlanıyor, arama hataları yakalanıyor.
- 2.3 Yeniden indekslemede sayfalar çoğalmıyor.
- 2.4 Silmede indeksleme işi iptal ediliyor.
- 2.5 Güncelleme `UPDATE_REPO` (varsayılan meminglr/Shamela-Library) reposuna bakıyor, sürümleri sayısal karşılaştırıyor, APK uygulamaya özel klasöre iniyor.
- 2.6 Yıkıcı Room geçişi kaldırıldı, şema dışa aktarılıyor.
- 2.8 Favori ve alıntılar için yedek dışa/içe aktarma eklendi.
- 3 Depolama bilgisi ve "tüm kitapları sil" eklendi. İndirme hataları gösteriliyor. Kütüphanede ad araması normalize ediliyor ve tüm eşleşmeleri buluyor.
- 4 kapt yerine KSP. Kullanılmayan bağımlılıklar kaldırıldı (spring-core, swipelayout, accompanist-webview, lifecycle-extensions). Kategori JSON'ları önbellekte. Release lint açıldı. Yeni birim testleri eklendi (toplam 59 test geçiyor).
- 5 Okuyucu sunucusu sadece 127.0.0.1'e bağlanıyor. İndirme alıcısı yalnızca sistem DownloadManager'ına açık. FileProvider sadece güncelleme klasörünü paylaşıyor.

**Yapılmadı / senin kararını bekliyor**
- Kendi Firebase projenin bağlanması (`app/google-services.json` hâlâ yer tutucu).
- Kitapların kendi deponda barındırılması (şu an orijinal geliştiricinin R2 deposu kullanılıyor).
- Kütüphanede okuma ilerlemesi gösterimi ve "okumaya devam et".
- 4 ViewModel'deki tekrarlı indirme kodunun tek bir yerde toplanması.
- APK boyutunun küçültülmesi (asset'ler ~38 MB).
- Okuyucunun arama ekranından alıntı ekleme (orijinal kodda devre dışı bırakılmış).
