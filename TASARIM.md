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
