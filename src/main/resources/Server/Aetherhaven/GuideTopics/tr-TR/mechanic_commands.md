---
name: Komutlar
description: "Şehirler ve sunucu araçları için sohbet komutları"
author: Hexvane
---

# Komutlar

`/aetherhaven` ve `/ah` aynıdır. Çoğu oyuncu yalnızca aşağıdaki **Oyuncular için** bölümüne ihtiyaç duyar.

**Erişim**, komutun varsayılan olarak hangi oyun moduna verileceğini belirtir. **İzin**, erişim yeterli değilse sunucu sahiplerinin manuel olarak verdiği izindir. Köşeli parantezler zorunludur; kare parantezler isteğe bağlıdır.

## Oyuncular için

### Kasaba üyeleri

- **`/ah town invite <player> [townName]`** — Çevrimiçi birini kasabanıza davet edin.

- `<player>` — Oyuncu kullanıcı adı (çevrimiçi olmalıdır).

- `[townName]` — Boşluklu tam kasaba adı. Kasabanız için atlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.town.invite`

- Erişim: Macera

- **`/ah town accept [townName]`** — Sizi davet eden bir kasabaya katılın.

- `[townName]` — Birden fazla bekleyen davetiniz varsa tam kasaba adı. Yalnızca bir tane varsa atlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.town.accept`

- Erişim: Macera

- **`/ah town decline [townName]`** — Kasaba davetini reddet.

- `[townName]` — Birden fazla bekleyen davetiniz varsa kasabanın tam adını girin. Sadece bir tane varsa atlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.town.decline`

- Erişim: Macera

- **`/ah town kick <player> [townName]`** — Kasabanızdan bir üyeyi kaldırın.

- `<player>` — Üye kullanıcı adı (çevrimiçi olmalıdır).

- `[townName]` — Boşluklu tam kasaba adı. Kendi kasabanız için atlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.town.kick`

- Erişim: Macera

- **`/ah town role <player> <role> [townName]`** — Üye rolü belirleyin: İNŞA ET, GÖREV veya HER İKİSİ.

- `<player>` — Üye kullanıcı adı (çevrimiçi olmalıdır).

- `<role>` — `BUILD`, `QUEST` veya `BOTH`.

- `[townName]` — Boşluklu tam kasaba adı. Kendi kasabanız için atlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.town.role`

- Erişim: Macera

- **`/ah town leave`** — Ait olduğunuz bir kasabadan ayrılın (kurucusu olarak değil).

- İzin: `hexvane.aetherhaven.command.aetherhaven.town.leave`

- Erişim: Macera

### Uçan hediyeler

- **`/ah floatinggift next`** — Bir sonraki uçan hediye balonunuzun ne zaman görüneceğini görün.

- İzin: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- Erişim: Macera

### Yol aracı

- **`/ah path revert <id>`** — Yerleştirdiğiniz sırada sohbetten aldığınız kimliği kullanarak sabitlenmiş bir yolu geri alın. Ayrıca oyunda yol aracı erişimine sahip olmanız gerekir.
- `<id>` — Yol yerleştirildiğinde sohbette yazdırılan yol geri alma kimliği (UUID).

- İzin: `hexvane.aetherhaven.command.aetherhaven.path.revert`

- Erişim: Macera

## Dünya sunucuları için

Bunlar yaratıcı mod veya sunucuyu çalıştıran kişiler içindir. Normal kasaba oyunu için gerekli değildir.

- **`/ah difficulty`** — Bina maliyetleri için dünya zorluk menüsünü açın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.difficulty`

- Erişim: Yaratıcı

- **`/ah reload`** — Mod yapılandırma ve veri dosyalarını diskten yeniden yükleyin.

- İzin: `hexvane.aetherhaven.command.aetherhaven.reload`

- Erişim: Yaratıcı

- **`/ah starterkit`** — Kendinize başlangıç araçlarını verin (yerleştirme personeli, sözleşme, planlama masası, bina personeli).

- İzin: `hexvane.aetherhaven.command.aetherhaven.starterkit`

- Erişim: Yaratıcı

- **`/ah exportskin [path]`** — Avatar görünümünüzü model dosyası olarak kaydedin.

- `[path]` — İsteğe bağlı çıktı yolu. Varsayılan olarak, zaman damgalı dosya adına sahip eklenti verileri `avatar_exports` kullanılır.

- İzin: `hexvane.aetherhaven.command.aetherhaven.exportskin`

- Erişim: Yaratıcı

- **`/ah exportskin <player> [path]`** — Başka bir oyuncunun avatar görünümünü kaydedin (`.other` izni gerektirir).

- `<player>` — Dünyadaki hedef oyuncu.

- `[path]` — İsteğe bağlı çıktı yolu (yukarıdakiyle aynı).

- İzin: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- Erişim: Yaratıcı

- **`/ah time <hour>`** — Oyun içi zaman çizelgesi saatini ayarlayın (köylü rutinleri bu saati kullanır).

- `<hour>` — Saat 0 ile 23 arası (örnek `14`, saat 14:00 için).
- İzin: `hexvane.aetherhaven.command.aetherhaven.time`

- Erişim: Yaratıcı

- **`/ah time dawn`** — Saati sabah 6:00'ya ayarlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- Erişim: Yaratıcı

- **`/ah plots finishassembly`** — Kasabanızda hala yapım aşamasında olan tüm binaları anında tamamlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- Erişim: Yaratıcı

- **`/ah plots remove <plotId>`** — Kasabanızdan kimliğine göre bir arsayı kaldırın.

- `<plotId>` — `plots list`'dan arsa kimliği.

- İzin: `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- Erişim: Yaratıcı

## Hata ayıklama komutları

Dünyaları test etmek ve düzeltmek için. Normal oyunun bir parçası değildir.

- **`/ah replace-charter [townName]`** — Eğer kırılmışsa, tüzük bloğunu kasabanızın kaydedilmiş yerine geri koyun.

- `[townName]` — Kasabanın tam adı (boşluklarla birlikte). Kendi kasabanız için bunu atlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- Erişim: Macera

- **`/ah towns`** — Bu dünyadaki tüm kasabaları listeleyin.

- İzin: `hexvane.aetherhaven.command.aetherhaven.towns`

- Erişim: Yaratıcı

- **`/ah poi list [town]`** — Bir kasaba için ilgi noktalarını listeleyin.

- `[town]` — Kasaba kimliği, `me` veya kendi kasabanız için bunu atlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.poi.list`

- Erişim: Yaratıcı

- **`/ah poi dump`** — Dünya kayıt defterindeki tüm ilgi noktalarını listeleyin.

- İzin: `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- Erişim: Yaratıcı

- **`/ah plots list`** — Kasabanızdaki arsa örneklerini listeleyin.

- İzin: `hexvane.aetherhaven.command.aetherhaven.plots.list`

- Erişim: Yaratıcı

- **`/ah needs inspect`** — Yakındaki ihtiyaç göstergelerine sahip köylüleri listele.

- İzin: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- Erişim: Yaratıcı

- **`/ah needs set <target> <which> <value>`** — Bir köylünün açlık, enerji veya eğlence göstergesini ayarla.

- `<target>` — Köylü kullanıcı adı, `Elder` veya varlık kimliği.

- `<which>` — `hunger`, `energy` veya `fun`.

- `<value>` — 0 ile 100 arası (100 dolu).

- İzin: `hexvane.aetherhaven.command.aetherhaven.needs.set`

- Erişim: Yaratıcı

- **`/ah tax breakdown`** — Kasaba hazineniz için vergi satırlarını göster.
- İzin: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- Erişim: Yaratıcı

- **`/ah tax now`** — Sabah vergi tahsilatını hemen çalıştırın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.tax.now`

- Erişim: Yaratıcı

- **`/ah quest grant [questId]`** — Kasabanızda bir görevi aktif olarak işaretleyin.

- `[questId]` — Görev kimliği. Atlandığında varsayılan `q_build_inn`.

- İzin: `hexvane.aetherhaven.command.aetherhaven.quest.grant`

- Erişim: Yaratıcı

- **`/ah quest complete [questId]`** — Kasabanızda bir görevi tamamlanmış olarak işaretleyin.

- `[questId]` — Görev kimliği. Atlandığında varsayılan `q_build_inn`.

- İzin: `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- Erişim: Yaratıcı

- **`/ah quest clear [questId]`** — Kasabanızın aktif görev listesinden bir görevi kaldırın.

- `[questId]` — Görev kimliği. Atlandığında varsayılan değer `q_build_inn`'dır.

- İzin: `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- Erişim: Yaratıcı

- **`/ah quest status`** — Kasabanız için aktif ve tamamlanmış görevleri göster.

- İzin: `hexvane.aetherhaven.command.aetherhaven.quest.status`

- Erişim: Yaratıcı

- **`/ah reputation set <villager> <value>`** — Bir köylü ile itibarınızı ayarlayın.

- `<villager>` — Kasabanızdaki köylü varlık kimliği veya rol kimliği.

- `<value>` — İtibar 0 ile 100 arası.

- İzin: `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- Erişim: Yaratıcı

- **`/ah reputation reward list [roleId]`** — İtibar kilometre taşı ödüllerini listele.

- `[roleId]` — İsteğe bağlı rol kimliği filtresi (örnek `Aetherhaven_Merchant`).

- İzin: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- Erişim: Yaratıcı

- **`/ah reputation reward grant <villager> <rewardId>`** — Şimdi bir itibar ödülü verin.

- `<villager>` — Kasabanızdaki köylü varlık kimliği veya rol kimliği.

- `<rewardId>` — Ödül kimliği (örnek `rep_merchant_50`).

- İzin: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- Erişim: Yaratıcı

- **`/ah villager list`** — Kasabanızdaki köylü varlık kimliklerini listeleyin.

- İzin: `hexvane.aetherhaven.command.aetherhaven.villager.list`

- Erişim: Yaratıcı

- **`/ah villager locate <villager> [--tp]`** — Bir köylünün nerede olduğunu gösterin (operatörler için isteğe bağlı ışınlanma).

- `<villager>` — Kasabanızdaki köylü varlık kimliği veya rol kimliği.

- `[teleport]` veya `--tp` — `true` veya `--tp` ışınlanma için (sadece operatörler).

- İzin: `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- Erişim: Yaratıcı

- **`/ah villager reset`** — Yakınınızdaki tüm kasaba sakinlerini yeniden canlandırın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- Erişim: Yaratıcı

- **`/ah villager fixinn`** — Kasabanızdaki han ziyaretçi havuzu sorunlarını onarın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- Erişim: Yaratıcı

- **`/ah gift resetLimits`** — Dünyadaki tüm oyuncular ve köylüler için hediye limitlerini sıfırlayın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- Erişim: Yaratıcı

- **`/ah gift fillHistory <roleId>`** — Test için hediye geçmişi önizleme satırlarını doldurun.
- `<roleId>` — Köylü rol kimliği (örnek `Aetherhaven_Merchant`).

- İzin: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- Erişim: Yaratıcı

- **`/ah debug-autonomy toggle`** — Baktığınız kasaba köylüsünün özerklik hata ayıklamasını açıp kapatın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- Erişim: Yaratıcı

- **`/ah debug-autonomy show`** — Baktığınız köylü için özerklik hata ayıklamasının açık olup olmadığını gösterin.

- İzin: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- Erişim: Yaratıcı

- **`/ah debug-autonomy clear`** — Baktığınız köylü için özerklik hata ayıklamasını kapatın.

- İzin: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- Erişim: Yaratıcı

- **`/ah debug-lootchest fill`** — Baktığınız sandıkta bonus ganimet zarlarını zorunlu kılın.
- İzin: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- Erişim: Yaratıcı

- **`/ah dialogue <treeId> [entryNode]`** — Test için kimliğe göre bir diyalog ağacı açın.

- `<treeId>` — Diyalog ağacı kimliği (örnek `aetherhaven_merchant`).

- `[entryNode]` — Başlangıç düğümü. Varsayılan `root`.

- İzin: `hexvane.aetherhaven.command.aetherhaven.dialogue`

- Erişim: Yaratıcı

- **`/ah floatinggift spawn`** — Bulunduğunuz konumda yüzen bir hediye balonu oluşturun.

- İzin: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- Erişim: Yaratıcı

- **`/ah path navviz`** — Köylü yol navigasyonu için hata ayıklama satırlarını açıp kapatın. Oyunda yol aracı izni gerektirir.

- İzin: `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- Erişim: Yaratıcı
