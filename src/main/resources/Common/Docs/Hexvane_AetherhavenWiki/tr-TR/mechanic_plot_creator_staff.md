---
name: "Senaryo oluşturucu ekip"
description: "Bir yapıyı özel bir kasaba binası tanımına dönüştürün"
author: Hexvane
---

# Arsa Oluşturma Ekibi

Arsa oluşturma ekibi, dünyada inşa ettiğiniz **özel binaları** kaydeder: taban alanı, özel bloklar, prefabrik dışa aktarma, maliyetler ve (isteğe bağlı olarak) kasabanızdaki canlı bir arsa. Çoğu oyuncu, aşağıdaki tam iş yeri türleri yerine **Dekorasyon** veya **Varyant** kullanmalıdır.

Oyunun akışını öğrenirken **kasaba günlüğünüzü → Kılavuz → Mekanikler → Arsa oluşturma ekibi** (bu sayfa) açın. Oyunda: ekibi donatın, başlatmak veya mevcut adım panelini açmak için **F** tuşuna basın, dünyadaki bloklara **birincil tıklayın**, **Q** / **E** ile önceki / sonraki adıma geçin, **R** ile iptal edin.

## Önerilen yollar

| Amaç | Bina türü | Notlar |

|------|----------------|-------|

| Kozmetik yapı, iş yok | **Dekorasyon** | İsteğe bağlı kasaba kayıtları rafı; üretim veya köylü mantığı yok. |

| Mevcut bir bina için alternatif görünüm | **Varyant** | Hangi **ana** bina olarak sayılacağını seçin (ev, ahır, han, vb.). Alt adımlar bu ana türe göre ilerler. |

| Yeni iş yeri / üretim alanı (modlama) | **Çalışma** | Yeni iş yeri türleri eklemek için; yönetim bloğu, üretim deposu ve çalışma yüzeyi POI'sine ihtiyaç duyar. |

## Bina türleri (seçici)

**Dekorasyon** — Ev, dükkan veya iş yeri olarak **kullanılmaması** gereken parklar, eşyalar ve yapılar. Minimum gerekli noktalar.

**Varyant** — Modda zaten bulunan başka bir bina kimliği olarak **sayılan** bir prefabrik (örneğin `plot_house` olarak sayılan özel bir ev). Açılır menüden ana türü seçersiniz; önemli noktalar bu ana binayla eşleşir.

**Ev** — Konut alanı: kasaba kayıtları rafı + uyku POI'si.

**Çalışma Alanı** — **Geliştirici/içerik yazarı kullanımı.** Yeni bir **iş yeri** tarzı alan tanımlar: kasaba kayıtları rafı, üretim deposu ve çalışma yüzeyi POI'si. Yeni bir üretim veya iş binası türü eklerken kullanın, normal kozmetik varyantlar için değil.

**Tesis** — Eğlence veya dinlenme (park, sunak tarzı tesis): raf + eğlence/oturma POI'si.

**Dükkan** — Tezgah veya dükkan tezgahı: raf + dükkan etiketli çalışma POI'si.

**Han** — Tam han düzeni: raf, çalışma yüzeyi, yataklar, yemek alanı, hancı ve ziyaretçi spawn noktaları (ve isteğe bağlı lonca ustası spawn noktası).

**Belediye Binası** — Sivil merkez: raf, hazine bloğu, planlama masası POI'si.

**Lonca Binası** — Maceracı loncası: raf, çalışma yüzeyi, maceracı spawn noktaları.

### Çakışma ve Karışıklık

- **Varyant** vs **Ev / İş / …** — Varyant, “farklı görünüyor, X gibi davranıyor” anlamına gelir. Mevcut bir oyun binasını yeniden tasarlıyorsanız, **Ev** yerine **Varyant** + ana türü seçin.

- **İş** vs **Dükkan** — **Dükkan**, tüccar tezgahları içindir. **İş**, çiftlikler, değirmenler, demirhaneler ve diğer üretim iş yerleri içindir.

- **Tesis** vs **Dekorasyon** — **Dekorasyon**'un neredeyse hiç oyun bağlantısı yoktur. **Tesis**, köylü programları için eğlence/tesis etiketleri ve ilgi noktaları belirler.

- **Han**, **Belediye Binası** ve **Lonca Binası** tam şablonlardır; **Varyant**'ı yalnızca bu şablonlardan birini özellikle eşleştiriyorsanız kullanın.

## Akış (kısa)

1. İki zıt köşeyi ve bir **dış** arsa işareti köşesini işaretleyin. 2. **Bina tipini** (ve varsa **varyantını**) seçin.

3. **Önemli noktaları** yerleştirin (bloklar her alt adımda birer birer verilir).

4. **Ad ve kimlik** girin (prefab dosya adı kimlikten sonra gelir).

5. Gerekirse **etiketleri** düzenleyin.

6. **Bina ayarlarını** (F) açın: hazine altın maliyeti, kendi kendine inşa günleri, boş alan prefab seçeneği ve montaj bölümleri.

7. Kaydetme-şekli adımında F ile **prefabı dışa aktarın** (6. adımdaki ayarları kullanır).

8. **Yapı malzemelerini** ayarlayın (sanal sandık; devam ettiğinizde eşyalar size geri döner).

9. Gözden geçirin ve kaydedin.

## İzinler

Varsayılan olarak, yapılandırma herkesin arsa oluşturucuyu kullanmasına izin verebilir; sunucular bunun yerine `aetherhaven.plot.creator` iznini gerektirebilir.
