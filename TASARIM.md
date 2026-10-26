# Shamela Library — Tasarım Analizi ve Düzeltmeler

Tarih: 2026-10-02 · Emülatörde açık/koyu tema, Arapça (sağdan sola) ve Türkçe (soldan sağa) arayüzde her ekran gezilerek yapıldı.

## Kök sebepler

Sorunların çoğu tek tek ekranlardan değil, ortak bir tasarım temeli olmamasından kaynaklanıyordu:

1. **Renk şemaları yarımdı.** Her tema sadece birkaç renk tanımlıyordu (primary, secondary, surface…). Arka plan, kartlar, menüler, çipler, kenarlıklar ve hata rengi Material'ın varsayılan mor/pembe paletinde kalıyordu. Pembemsi arka plan, bej çubuklar, lavanta çipler ve mor menüler bundandı.
2. **Tipografi eksikti.** Sadece `bodyLarge` tanımlıydı. Üst çubuk, düğmeler, sekmeler, menüler ve alt menü varsayılan stillerle; ekranlar ise kendi `AppFonts.textX` stilleriyle çiziliyordu. Boyutlar ve hiyerarşi tutarsızdı.
3. **Metin yönü ele alınmamıştı.** Türkçe arayüzde Arapça başlıklar soldan sağa paragrafta çiziliyordu. Sayılar ve noktalama yer değiştiriyordu: "48 سؤالاً" → "سؤالاً … 48", açıklamalarda nokta satır başına geçiyordu.
4. **Yazı tipi seçimi Latin harfleri bozuyordu.** Kullanıcının seçtiği Arapça yazı tipi (ör. Amiri) Türkçe metinlerde de kullanılıyordu; Türkçe arayüz serif ve dengesiz görünüyordu.
5. **Bileşenler kopyalanmıştı.** Aynı özel sekme kontrolü 5 yerde, kitap satırı 3 ayrı sürümde vardı; her biri farklı boşluk, ayırıcı ve renk kullanıyordu.

## Bulunan sorunlar ve yapılan düzeltmeler

| Alan | Sorun | Düzeltme |
|---|---|---|
| Renkler | Paletle uyumsuz pembe arka plan, mor menü/çipler, sabit `Color.Red`, `0xFF8B0000`, sarı vurgu, `Color.Gray` | 7 temanın tamamı tek bir ana renkten (seed) üretilen tam Material 3 paletine çevrildi (`material-kolor`). Açık ve koyu tema tüm roller. Sabit renkler tema rollerine bağlandı. |
| Tipografi | Eksik type scale, ters hiyerarşi (sayfa sayısı başlıktan büyük görünüyordu) | Tüm Material type scale seçilen yazı tipi ve boyut tercihinden üretiliyor. Başlık `titleMedium`, ikincil bilgi `bodyMedium`/`onSurfaceVariant`. |
| Metin yönü | Arapça içerikte sayı ve noktalama kayması | Tüm stillerde `TextDirection.Content`. Hizalama ekran yönünü izliyor. Tek satırlık Arapça başlıklarda doğal hizalama (sola zorlanınca Android ilk harfleri kırpıyordu). |
| Yazı tipi | Türkçe arayüzde serif/uyumsuz Latin harfler | Türkçe'de arayüz metinleri sistem yazı tipinde. Arapça içerik (kitap adı, yazar, alıntı, açıklama) kullanıcının seçtiği Arapça yazı tipinde. |
| Üst çubuk | `surfaceColorAtElevation(15.dp)` ile rastgele ton, başlık iki satıra taşıp küçülüyordu | `surfaceContainer`, tek satır + üç nokta. Bölüm ekranında alt satırda kitap sayısı. Okuyucuda başlık tek satır. |
| Alt menü | Sadece seçili sekmenin etiketi görünüyordu | Tüm etiketler her zaman görünür. Renkler paletten. |
| Sekmeler | 5 kopya özel kontrol; "Harici kitaplar" kenara taşıyordu | Tek `SegmentedTabs` bileşeni (Material 3 segmentli düğme): Kütüphane, İndir, Favoriler, Ayarlar, okuyucu. |
| Kitap satırı | Başlık küçük ve tek satırda kesik; meta veri büyük; ikonlar farklı renklerde | Tek ortak `BookRow`: kapak kutucuğu, 2 satır başlık, "yazar · N sayfa", tutarlı ikonlar, girintili ayırıcı. Yazar "-" ise gizleniyor. |
| Bölüm/alıntı satırı | Farklı iç boşluklar; alıntı metni küçük; "Sayfa: 0" | Klasör kutucuğu ve kitap sayısı; alıntılar blok alıntı çubuğuyla. Sayfa numarası 1'den başlıyor. |
| Gruplama | "4", "6", "_" gibi tek kitaplık başlıklar; "آ" ve "ا" ayrı gruplarda | Elif biçimleri tek grupta; rakam ve simgeler sonda tek "#" grubunda. Başlıklar kompakt. |
| Seçim modu | "İptal" düğmesinde okunmayan renk; sayı yok | Bağlamsal çubuk: kapat, "N seçildi", kırmızı "Sil". Seçili satır vurgulu. |
| Kaydırarak silme | Saf kırmızı arka plan | `errorContainer`/`onErrorContainer`. |
| Arama | Bölüm seçilmeden metin kutusu devre dışı; görünür arama düğmesi yok; seçim penceresi her tıklamada kapanıyordu | Kutu hep etkin. Seçili bölümler çip olarak. "Tümünü seç". Pencere açık kalıyor. Belirgin "Ara" düğmesi. |
| Arama sonuçları | Boş durum ve yükleniyor göstergesi listeyi ekran dışına itiyordu | Liste üzerinde katman olarak gösteriliyor. |
| Bölüm indirme | Tek dokunuşla yüzlerce kitap onaysız iniyordu | Kitap sayısını gösteren onay penceresi. |
| Ayarlar | Ortalanmış/sola yaslı karışık seçenekler; 70dp'lik yazı tipi kutuları; kaydırıcı etiketleri işaretlerle hizasız | Renk örnekli ve ikonlu `FilterChip`'ler. Önizlemeli radyo listesi. Canlı önizlemeli boyut kaydırıcısı. Bölüm başlıkları tutarlı. |
| Kitap detayı | Lavanta kategori etiketi; yapay zekâ notunda kırmızı "Model"; Arapça açıklamada yön hatası | Palet renkleri, nötr bilgi kartı, içerik yönü, alt düğme gezinme çubuğu boşluğuna uyumlu. |
| Hakkında | "Henüz yayınlanmış sürüm yok" hata kırmızısıyla | Bilgi rengiyle. |
| İçindekiler | Sabit 48dp çerçeveli kutular; uzun başlıkların ikinci satırı kesik | Satırlar içeriğe göre uzuyor; alt seviyeler girintili; sade ayırıcılar. |
| Kitap içi arama | Arama çubuğu durum çubuğunun altına giriyordu; sarı/siyah vurgu | Doğru pencere boşlukları; tema renkli vurgu. |
| Seçim menüsü (okuyucu) | Türkçe'de 4. düğme taşıyordu | Kısa etiketler, tek satır, daha dar iç boşluk. |
| Boş/yükleniyor durumları | Her ekranda farklı; 80dp renk değiştiren gösterge | Ortak `EmptyState` (tonlu ikon, başlık, açıklama). Standart gösterge. |
| Diyaloglar | Her biri farklı | Tek `ConfirmationDialog`: silme için hata rengi, diğerleri için ana renk. |
| Erişilebilirlik | Geri, ara ve menü düğmelerinde açıklama yoktu | İçerik açıklamaları eklendi. |

## Not

- Bazı kitap açıklamaları (yapay zekâ ile üretilmiş veriler, `assets/book-details`) içinde Kiril harfli kelimeler var (ör. "сторон"). Bu veri hatası; tasarımla ilgili değil, ayrıca temizlenmeli.

## M3 Expressive

Uygulama Material 3 Expressive'e taşındı ([building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)).

**Sürüm:** Expressive bileşenleri `material3`'ün kararlı 1.4.0 sürümünde yok (sadece token'lar var). En yeni 1.5 alpha'ları compileSdk 37 ve AGP 9.1 istiyor. Projenin mevcut araçlarıyla (Compose 1.8, AGP 8.9, Kotlin 2.1.20) çalışan `material3 1.5.0-alpha14` kullanılıyor (`versions.gradle`). Bu bir alpha kütüphane; API'leri ileride değişebilir.

| Expressive özelliği | Uygulamadaki karşılığı |
|---|---|
| `MaterialExpressiveTheme` + `MotionScheme.expressive()` | Tüm bileşenlerde yaylı (spring) hareket |
| Vurgulu tipografi (`*Emphasized`) | Seçilen yazı tipi ve boyutla tam emphasized type scale; ayar başlıkları, boş durumlar, kitap başlığı |
| Birleşik düğme grubu (connected `ToggleButton`) | Segmentli düğmelerin yerine: Kütüphane, İndir, Favoriler, Ayarlar, okuyucu |
| `ShortNavigationBar` | Alt menü |
| `MediumFlexibleTopAppBar` | Ana sekmelerde kaydırınca küçülen büyük başlık; bölüm ekranında alt başlıkta kitap sayısı |
| Gruplu listeler (`SegmentedListItem`) | Kitap, bölüm, alıntı ve yazı tipi listeleri: yuvarlak bloklar, ince boşluklar, basınca şekil değişimi, seçili satır vurgusu |
| `MaterialShapes` | Kapaklar (Cookie4), bölümler (Clover4Leaf), alıntılar (Sunny), boş durum (Cookie12) |
| Şekil değiştiren düğmeler (`ButtonDefaults.shapes()`, `IconButtonDefaults.toggleableShapes()`) | Ana eylem düğmeleri; favori düğmesi seçilince daireden yuvarlak kareye dönüşüyor |
| `HorizontalFloatingToolbar` | Kütüphanede seçim modu (kapat / "N seçildi" / sil) |
| `LoadingIndicator`, `CircularWavyProgressIndicator` | Yükleniyor göstergeleri; indirme ilerlemesi dalgalı |

Bu aşamada ayrıca:
- **Durum çubuğu:** İkonların rengi artık temaya göre belirleniyor. Açık temada beyaz kalıp okunmuyordu.
- **Varsayılan yazı tipi:** Adı iki yerde harekeler farklı sırayla yazıldığı için hiç eşleşmiyordu; uygulama sessizce Noto Naskh'a düşüyordu. Anahtarlar Unicode NFC ile normalize edildi.

## Okuyucu: okuma ayarları ve çubuklar

**Önceki durum:**
- Okuma ayarları ayrı bir ekrandaydı ve sadece yazı tipi, 5 kademeli boyut ve gece modunu içeriyordu.
- Yazı boyutu ve tema tüm uygulamayla ortaktı; okurken yazıyı büyütmek uygulamanın menülerini de büyütüyordu.
- Alt çubukta 0'dan başlayan bir sayfa kutusu vardı; ilerleme göstergesi yoktu.

**Yeni:**
- **Üst çubuk:** Kitap adının altında bulunulan bölümün adı (içindekilerden; kendi başlığı olmayan bölümler bir önceki başlığa bağlanır). Arama düğmesi. (Okuma ayarları yalnızca alt çubukta; üstteki tekrar eden düğme kaldırıldı.)
- **Alt çubuk:** "Bölüm X / N", kitap yönünde (sağdan sola) çalışan bölüm kaydırıcısı (sürüklerken hedef bölüm canlı gösterilir), önceki/sonraki bölüm, İçindekiler ve Okuma ayarları düğmeleri.
- **Okuma ayarları** (kompakt alt sayfa: her ayar tek satırda, solda ad, sağda küçük kontrol; tamamı kaydırmadan tek ekrana sığar; ayarlar başlıklı gruplarda — Sayfa, Metin, Ekran — her grup kendi yuvarlak tonlu zemininde; okuyucudan çıkmadan anında uygulanır, uygulamanın kendi ayarlarından bağımsız ve kalıcı):
  - Sayfa rengi: Otomatik (uygulama temasına göre) / Açık / Sepya / Koyu / Siyah
  - Yazı boyutu: 12–36 px, A−/değer/A+
  - Satır aralığı ve kenar boşlukları: Dar / Normal / Geniş
  - Hizalama: Sağa yaslı / İki yana yaslı
  - Okuma yazı tipi: uygulamanın yazı tipi veya 10 Arapça yazı tipinden biri (adlar kendi yazı tipleriyle gösterilir)
  - Harekeleri gizle
  - Ekranı açık tut
  - Parlaklık: sistem veya okuyucuya özel
  - Sıfırla
- **Teknik:** Ayarlar her sayfaya `Style.css`'ten sonra gelen bir stil bloğu olarak ekleniyor; açık sayfalarda JavaScript ile anında değiştiriliyor. Harekeleri gizlemek metni değiştirdiği için sayfalar yeniden alınıyor; her WebView hangi HTML'i gösterdiğini hatırlayıp içerik değişince kendini yeniden yüklüyor.
- **Yön:** Çubuklar ve ayar sayfası arayüz dilini izliyor (Türkçe'de soldan sağa); kitap sayfaları ve bölüm kaydırıcısı her dilde sağdan sola.

## Kitap içinde arama

**Bulunan sorunlar ve düzeltmeler:**
- **Arama çubuğu tüm ekranı kaplıyordu:** Metin alanının iç kutusu `fillMaxSize` kullandığı için üst çubuk ekran boyunca uzuyor, geri oku ve alan ekranın ortasına kayıyordu. Düzeltildi (bu bileşen ana arama ekranında da kullanılıyor).
- **Sonuçlar soldan sağa diziliyordu:** Türkçe arayüzde Arapça alıntıların kelime sırası ters okunuyordu. Alıntılar artık her dilde sağdan sola; boşluklar sadeleştirildi, kesik başı/sonu "…" ile işaretleniyor.
- **Hangi bölümde olduğu belli değildi:** Sonuçlar artık bölüm başlıkları altında gruplanıyor (yapışkan başlık).
- **İpucu:** "Ara..." yerine "Kitapta ara".
- **Sonuca gidince eşleşme görünmüyordu:** (1) Okuma ayarlarının stili vurgunun rengini eziyordu; (2) WebView, sayfanın kaydırma kabının içinde tam yüksekliğiyle durduğu için JavaScript'teki `scrollIntoView` hiçbir şeyi kaydırmıyordu; (3) her zaman sayfadaki ilk eşleşme seçiliyordu. Artık tıklanan eşleşme (önündeki metinle) bulunuyor, kalıcı bir renkle vurgulanıyor ve sayfa yerleştikten sonra o konum ekranın ortasına kaydırılıyor. Komut yalnızca sonucun bulunduğu sayfaya gönderiliyor.

## Ayarlar ekranı: bölüm ayrımı

Tercihler, Genel ve Harici kitaplar sekmelerindeki bölümler (Tema, Renk, Yazı boyutu, Dil, Depolama, Yedekleme, Harici kitap ekle) zemin üzerinde sınırsız duruyordu; sadece yazı tipi listesi kart içindeydi ve sayfa karmaşık görünüyordu. Artık her bölüm başlığın altında kendi yuvarlak tonlu kartında (`SettingsCard`); yazı tipi listesi aynı renkte gruplu liste olarak kalıyor. Yazı boyutu bölümündeki ikinci başlık gibi görünen "Normal" etiketi sade bir değer etiketine çevrildi, önizleme kartın içinde açık zeminde.

## Okuyucu: sayfa çevirme efekti

Bölümler arasında geçerken sayfalar artık kaymıyor, kitap sayfası gibi çevriliyor. Çevrilen sayfa sırtı (Arapça kitaplarda sağ kenar) etrafında okuyucuya doğru 3B döner ve kalkarken kararır; alttaki sayfa üstteki sayfanın gölgesinde başlar, açıldıkça aydınlanır. Geri kaydırınca aynı hareket tersine oynar. Sayfa alanı kırpıldığı için dönen sayfa üst ve alt çubukların üzerine taşmaz. Okuma ayarları → Ekran → "Sayfa çevirme efekti" ile kapatılabilir (kapalıyken eski kaydırma geçişi).

## Sekmeler, renkler, tema simgeleri, iki parmakla yazı boyutu

- **Sekmeler:** Kütüphane, İndir, Favoriler ve Ayarlar'daki segmented düğmeler kaldırıldı. Yerine Flutter'daki TabBar + TabBarView'in Compose karşılığı geldi: `PrimaryTabRow` (kaydırmayı izleyen alt çizgi göstergesi) + `HorizontalPager` (sayfalar parmakla kaydırılabilir). Ortak bileşen: `TabbedPager`.
- **Renk seçimi:** Etiketli büyük çipler yerine yatayda kaydırılabilen yuvarlak renk daireleri; seçili olanda halka ve onay işareti, adı altında yazıyor. Liste açılışta seçili renge kaydırılıyor.
- **Tema simgeleri:** Açık/koyu/otomatik simgelerinin çizimleri bozuktu; Material'in güneş, ay ve yarım daire simgeleriyle değiştirildi.
- **Okuyucu:** İki parmakla açınca yazı büyüyor, kapatınca küçülüyor (12–36). Değişiklik canlı uygulanıyor, okuma ayarlarına kaydediliyor ve hareket sırasında ortada yazı boyutu gösteriliyor. Bu hareket bölüm değiştirmiyor, sayfayı da kaydırmıyor.

## İkonlar ve sıkı sekmeler

- **Sekmeler:** `PrimaryScrollableTabRow` + `LeadingIconTab` (Flutter'da `isScrollable: true` TabBar). Sekmeler ekrana yayılmıyor, başta yan yana duruyor; her birinde ikon var: Bölümler, Kitaplar, Alıntılar, Tercihler, Genel, Harici kitaplar. Kitap detayındaki Özet / Konular / Ayrıntılar sekmeleri de aynı tasarımda.
- **Ayar başlıkları:** Tema, Renk, Yazı boyutu, Yazı tipi, Dil, Eski uygulamadan aktar, Depolama, Yedekleme, Harici kitap ekle ve aramadaki Bölümler başlığında ikon var. Dışa/İçe aktar düğmelerine de ikon eklendi.
- **Okuma ayarları:** Her satırın başında küçük bir ikon var (sayfa rengi, kenar boşlukları, hizalama, yazı boyutu, satır aralığı, yazı tipi, harekeler, sayfa çevirme, ekran açık, parlaklık).
- Menülerde, onay pencerelerinde ve boş ekranlarda ikonlar zaten vardı.

## Okuma ayarları paneli (Apple Books / Kindle tarzı)

Panel ekranın yaklaşık üçte birini kaplıyor ve arkayı karartmıyor; sayfa görünür kalıyor ve her değişiklik anında üzerinde görülüyor. Üstte her zaman sayfa rengi ile A− / boyut / A+ duruyor. Altında ikonlu üç sekme var: Sayfa, Metin, Ekran. "Sıfırla" sekmelerin yanında. Ayrıntılı karşılaştırma ve geliştirme önerileri için: OKUYUCU_ARASTIRMA.md.

## Okuyucu: kalınan yer, aramada gezinme, vurgu ve not, sistem seçim çubuğu

- **Kalınan yer:** Her kitap için bölüm ve bölüm içindeki kaydırma oranı kaydediliyor (`ReadingPositionStore`); kitap açılınca tam o satıra dönülüyor. Kitap bir arama sonucuyla ya da belirli bir sayfayla açılırsa kayıtlı yer kullanılmıyor. Açılış sırasında kayıtlı yerin üzerine yazılmasını önleyen bir koruma var.
- **Aramada önceki/sonraki (Kindle tarzı):** Arama sonucuna dokununca tüm sonuç listesi okuyucuya aktarılıyor (`SearchSession`). Okuyucunun altında "sorgu · 3 / 70 · Sonuçlara dön" ile ‹ › düğmeleri çıkıyor. Sonraki eşleşme başka bölümdeyse oraya geçilip eşleşme işaretleniyor ve ortalanıyor. "Sonuçlara dön" listeyi yeniden aramadan açıyor. Arama eşleşmesi turuncu ve çerçeveli; kullanıcı vurgularının sarısından ayrılıyor.
- **Vurgu ve not:** Seçim çubuğunda "Vurgula" var. 4 renk (sarı, yeşil, mavi, pembe). Vurguya dokununca açılan panelden renk değiştirme, not yazma ve silme yapılıyor; panel arkayı karartmıyor. Notlu vurguların altında noktalı çizgi var. Alt çubuktaki "Vurgular" listesi okuma sırasında; bölüm adını, metni ve notu gösteriyor, dokununca vurgunun yerine gidiliyor. Vurgular her kitap için JSON dosyasında saklanıyor ve aynı kelimenin bölümdeki diğer geçişleriyle karışmasın diye öncesindeki metinle birlikte tutuluyor.
- **Seçim çubuğu:** Eskiden özel çizilmiş bir pencere vardı; artık Android'in kendi seçim araç çubuğu kullanılıyor. Çubukta Vurgula, Anlam ve Kopyala doğrudan görünüyor; Paylaş, Tümünü seç ve Favori "⋮" altında.

## Büyük kütüphanede performans (1000+ kitap aktarımı)
- **Sorun:** Aktarılan her kitap için ayrı bir "arama için hazırla" işi başlatılıyordu. 1071 kitapta 1071 iş aynı anda çalışıyor, her biri kitabın tamamını belleğe alıyor ve sesli, titreşimli bir bildirim gösteriyordu. Ayrıca her açılışta kütüphanedeki tüm EPUB'lar yeniden çözümlenip veritabanına tek tek yazılıyordu. Her yazma tüm kitap listelerini yeniden hesaplattığı için kütüphane 1000 kez yenileniyordu.
- **Tek arama kuyruğu:** `BookIndexing` artık kütüphane genelinde tek bir iş başlatıyor (`LibraryIndexWorker`, `APPEND_OR_REPLACE`). Bu iş indekslenmemiş kitapları düşük öncelikli tek bir iş parçacığında sırayla hazırlıyor ve tek, sessiz bir ilerleme bildirimi gösteriyor ("Kitaplar arama için hazırlanıyor · 77 / 1067"). Hangi kitapların hazır olduğu tek sorguyla öğreniliyor (`indexedBookIds`). Daha önce kitap başına bir sorgu yapılıyordu ve her biri FTS tablosunu baştan sona tarıyordu. Eski sürümlerin kuyruğa attığı kitap başına işler açılışta iptal ediliyor.
- **Aktarma arka planda:** `LibraryImportWorker` ekran ya da uygulama kapansa da devam ediyor. Kitapları 50'şerlik gruplar halinde tek işlemle kaydediyor. Arama hazırlığı kopyalama bittikten sonra başlıyor, yani ikisi birbirini yavaşlatmıyor. Ayarlar'daki ilerleme çubuğu bu işi izliyor.
- **Açılış:** Yalnızca veritabanında olmayan dosyalar çözümleniyor, onlar da toplu kaydediliyor.
- **Veritabanı:** Arama veritabanında WAL açık. Arka planda indeksleme sürerken arama ve okuma beklemiyor.
- **Ölçüm (emülatör, 1071 kitap):** Kopyalama yaklaşık 10 saniye, arama hazırlığı yaklaşık 4 dakika sürdü. Bu sırada kütüphane listesi kaydırılırken karelerin %5'i takıldı. Yeniden açılışta hiçbir kitap tekrar çözümlenmedi ve hazırlık kaldığı yerden devam etti.
- **Eski uygulamanın klasöründen doğrudan okuma neden yok:** Android 11 ve sonrasında başka bir uygulamanın `Android/media` klasöründeki dosyalara dosya yoluyla erişilemiyor, yalnızca seçilen klasör üzerinden (SAF) erişilebiliyor. Okuyucu ve arama motoru ise gerçek dosya yolu istiyor. Ayrıca eski uygulama kaldırılınca bu klasör de siliniyor. Bu yüzden kitaplar kopyalanıyor.

## Okuyucu: çubuklar sayfanın üstünde ve sayfa renginde
- **Sorun:** Menü çubukları açılınca sayfa iki çubuğun arasına sıkışıyordu. Sayfa yine de durum çubuğu ve gezinme çubuğu kadar iç boşluk bıraktığı için üst çubuğun altında ve alt çubuğun üstünde sayfa renginde (açık temada beyaz) fazladan bantlar oluşuyordu. Çubuklar da sayfa renginden bağımsız olarak açık gri kalıyordu; koyu veya sepya sayfada beyaz şerit gibi duruyorlardı.
- **Çözüm:** Çubuklar artık sayfanın üstünde yüzüyor (Kindle'daki gibi). Sayfa küçülmüyor ve çubuklar açılıp kapanınca metin yerinden oynamıyor. Çubuklar sayfa renginin bir ton farklısını, yazılar sayfanın metin rengini kullanıyor. Kaydırıcının boş izi ve arama gezgini de sayfaya uyumlu. Durum çubuğu simgeleri sayfaya göre açık ya da koyu oluyor; daha önce koyu sayfada simgeler de koyuydu ve okunmuyordu.

## Bölüm içinde kitap silme
- **Eksik:** Bölüm içindeki kitap listesinde silme yoktu, yalnızca bilgi ve açma vardı. Kütüphanenin "Kitaplar" sekmesindeki davranış artık burada da var: sola kaydırarak silme, uzun basıp çoklu seçim (seçim çubuğunda "Tümünü seç" ile), favori kalbi. Yerel bölümün başlığında indirme simgesinin yerinde "Bölümü sil" var. Bölüm boşalınca ekran kapanıyor, boş klasör de siliniyor. "Tümünü seç" kütüphanedeki seçim çubuğuna da eklendi.
- **Çökme:** Kitap bilgisini okuyan EPUB ayrıştırıcısı açtığı zip dosyasını hiç kapatmıyordu (`EpubParser().parse`). Dosya silindikten sonra çöp toplayıcı bu dosyayı kapatmaya çalışınca G/Ç hatası veriyor ve uygulama çöküyordu. Artık her ayrıştırma `parseEpubAndClose` ile kapanıyor. Okuyucu da açık kitabın dosyasını çıkışta kapatıyor.
- **Hız:** Arama tablosunda kitap kimliği indekslenmemiş bir sütundu. Her kitabın sayfalarını silmek ya da kontrol etmek tüm kütüphanenin sayfalarını tarıyordu: 1000 kitapta kitap başına yaklaşık 3 saniye. Yeni, indeksli bir `BookPageRows` tablosu (kitap kimliği → sayfa satırı, veritabanı sürümü 4) bunu anında yapıyor. Sonuç: 177 kitaplık bir bölüm 8 dakika yerine 5 saniyede siliniyor. Eski sürümden güncellemede çalışan yeniden normalleştirme de sayfaları satır numarasıyla güncelliyor; önceden her sayfa için tabloyu tarıyordu.
- Bölüm sayıları silme bitince tek seferde yeniden hesaplanıyor (önceden üst üste binen sayımlar eski bir sayıyı gösterebiliyordu). Arapça ad içeren Türkçe onay mesajlarında ad Unicode yalıtımıyla sarılıyor, cümle sırası bozulmuyor.

## Kitap içi arama: çökme ve kitaptan çıkma
- **Çökme (telefon raporu, 1.7):** Sonuç listesi bölüm başlıklarına göre gruplanırken ilk sonucun bölümü henüz bilinmiyorsa (`chapterByHref` boş) `groups.lastOrNull()?.first == chapter` ifadesi boş listede `null == null` sonucunu veriyordu. Ardından `groups.last()` çağrısı çöküyordu. Bölüm adları kitap ayrıca okunarak bulunuyor. Büyük kitapta bu okuma uzun sürerken "Sonuçlara dön" sonuçları anında getirdiği için hata özellikle büyük kitaplarda çıkıyordu. Düzeltildi.
- **Kitaptan çıkma (telefon kaydı, 1.9):** "Sonuçlara dön"den sonra gelen iki geri olayı önce sonuç listesini, sonra okuyucuyu kapatıyordu. Arama gezgini açıkken geri artık yalnızca arama modunu kapatıyor (Kindle'daki gibi). Kitap ancak bir sonraki geri ile kapanıyor.

## Okuyucu: metin çubukların altında kalmasın
- Çubukları yüzer yapınca boşluklar gitmişti, ama çubuklar açıkken metnin üstünü ve altını örtüyorlardı. Artık sayfanın üst ve alt iç boşluğu çubukların ölçülen yüksekliğini izliyor (`pageTop` / `pageBottom`, kısa bir animasyonla). Çubuklar açıkken metin üst çubuğun hemen altında başlayıp alt çubuğun (ve arama gezgininin) hemen üstünde bitiyor. Kapalıyken durum çubuğundan gezinme çubuğuna kadar uzanıyor. Ne örtme ne de arada boş şerit kalıyor.
- Gizlenen bir çubuk son boyunu bildirmeye devam ettiği için üst boşluk ölçüme değil çubukların açık/kapalı durumuna bağlı.

## Kitap silme: indirme yöneticisi gibi yüzen çubuk ve panel
- **Sorun:** Toplu silmede yalnızca dönen bir çember vardı; ne kadarının silindiği görünmüyordu.
- **`BookDeletionManager`:** Uygulama genelinde arka planda silen yönetici (indirmelerdeki `BooksDownloadManager` gibi). Silme isteyen ekrandan çıkılsa da devam ediyor. Yeni istekler sıraya ekleniyor. Silinen her kitabın kimliği yayınlanıyor, açık bölüm ekranı listeden anında düşürüyor; bölüm boşalınca ekran kapanıyor.
- **Yüzen çubuk (`DeletionBar`):** Her ekranda, indirme çubuğunun üstünde: dolan halka, "84 / 300 silindi" ve silinen kitabın adı. Bitince birkaç saniye "300 kitap silindi" gösterip kayboluyor.
- **Panel (`DeletionSheet`):** Çubuğa dokununca: ilerleme çubuğu, sayaç, o an silinen kitap, silinen kitapların listesi ve **Durdur**. Durdur, o an silinmekte olan kitabı bitirip duruyor; silinenler silinmiş kalıyor.
- Bölüm ekranı (tek, seçili, bölümün tamamı), kütüphanedeki seçimle silme ve Ayarlar'daki "Tüm kitapları sil" bu yöneticiyi kullanıyor. "Tüm kitapları sil" durdurulursa, veritabanında olmayan yarım dosyaları temizleme adımı atlanıyor (yoksa kalan kitapları da silerdi).
- Ölçüm (emülatör): 300 kitap yaklaşık 10 saniye, 400 kitap yaklaşık 15 saniye.

## Bölümler: konu grupları ve alfabetik sıra
- Shamela'nın 72 bölümü (37 konu ve her birinin "مرقم آليا" sürümü) tek düz liste yerine konu gruplarında: Kur'an ve Tefsir, Hadis, Akaid, **Fıkıh ve Usul** (dört mezhep, genel fıkıh, usul ve kaideler, fetvalar, siyaset ve kaza), Tarih ve Biyografi, Dil ve Edebiyat, Rekaik ve Davet, Âlimlerin Eserleri, Genel ve Kaynaklar, Diğer (harici kitaplar, bilinmeyen klasörler).
- Grup içinde Arapça alfabetik sıra (`Collator`, "ال" takısı yok sayılarak). "مرقم آليا" sürümü aslının hemen altında. Her grup başlığında gruptaki toplam kitap sayısı var.
- Kütüphane ve İndir sekmelerinin Bölümler listeleri aynı düzeni kullanıyor (`SectionGroup`, `groupedSectionItems`).

## İndirme: mobil veride "Connection reset" (operatör engeli)
- **Belirti:** Ev wifi'ında ve VPN ile indirme çalışıyor, mobil veride çalışmıyor. Telefon kaydında her denemede TLS bağlantısı hemen `Connection reset` ile kesiliyor. DNS ve ping çalışıyor.
- **Sebep:** Operatör, TLS'in ilk mesajındaki (ClientHello) sunucu adını (SNI) okuyup `*.r2.dev` adresine giden bağlantıları kesiyor (DPI). Kitap sunucusu Cloudflare R2'nin `pub-….r2.dev` adresinde.
- **Çözüm (`BookServerConnection`):** Önce normal bağlanılıyor. Bağlantı sıfırlanırsa aynı istek, ClientHello iki TLS kaydına bölünüp ayrı TCP paketleri halinde gönderilerek tekrar deneniyor. Sunucu bunu normal şekilde birleştiriyor, filtre ise adı tek parça halinde göremiyor. Hangi yolun çalıştığı süreç boyunca hatırlanıyor; engelsiz ağlarda hiçbir şey değişmiyor. Sertifika ve ana bilgisayar adı doğrulaması aynen sürüyor.
- **Uygulama ayrıntısı:** Android'in yerleşik TLS'i şifreli veriyi doğrudan soketin dosya tanıtıcısına yazdığı için bölme yapılamıyor. Bu yüzden yalnızca yedek yolda Conscrypt'in (`org.conscrypt:conscrypt-android`) "engine" modu kullanılıyor; bu mod Java akışından yazıyor. `HttpsURLConnection` ham soketi kendisi açıp verdiği için, verilen soket bölen bir akışla sarılıyor (`SplittingWrapper`). Release derlemesi için ProGuard kuralı eklendi.
- **Doğrulama:** Telefonda (Redmi Note 10 Pro, mobil veri / 4G) önce `Connection reset`, ardından "split handshake works on this network" görüldü ve kitap indi.
