---
name: Команды
description: "Команды чата для городов и инструменты сервера."
author: Hexvane
---

# Команды

`/aetherhaven` и `/ah` одинаковы. Большинству игроков достаточно раздела **Для игроков** ниже.

**Доступ** указывает, к какому режиму игры команда применяется по умолчанию. **Разрешение** — это то, что владельцы сервера предоставляют вручную, если доступа недостаточно. Угловые скобки обязательны; квадратные скобки необязательны.

## Для игроков

### Члены города

- **`/ah town invite <player> [townName]`** — Пригласить кого-либо онлайн в свой город.

- `<player>` — Имя пользователя игрока (должен быть онлайн).

- `[townName]` — Полное название города с пробелами. Опустите для своего города.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.town.invite`

- Доступ: Приключения

- **`/ah town accept [townName]`** — Присоединиться к городу, который вас пригласил.

- `[townName]` — Полное название города, если у вас более одного ожидающего приглашения. Пропустите, если у вас только одно приглашение.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.town.accept`

- Доступ: Приключения

- **`/ah town decline [townName]`** — Отклонить приглашение в город.

- `[townName]` — Полное название города, если у вас более одного ожидающего приглашения. Пропустите, если у вас только одно.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.town.decline`

- Доступ: Приключения

- **`/ah town kick <player> [townName]`** — Удалить участника из вашего города.

- `<player>` — Имя пользователя участника (должен быть онлайн).

- `[townName]` — Полное название города с пробелами. Пропустите, если у вас свой город.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.town.kick`

- Доступ: Приключения

- **`/ah town role <player> <role> [townName]`** — Установить роль участника: СТРОИТЕЛЬСТВО, КВЕСТ или ОБА.

- `<player>` — Имя пользователя участника (должен быть онлайн).

- `<role>` — `BUILD`, `QUEST` или `BOTH`.

- `[townName]` — Полное название города с пробелами. Опустите, если хотите свой город.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.town.role`

- Доступ: Приключения

- **`/ah town leave`** — Покинуть город, к которому вы принадлежите (не как основатель).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.town.leave`

- Доступ: Приключения

### Плавающие подарки

- **`/ah floatinggift next`** — Узнать, когда может появиться ваш следующий плавающий подарочный шар.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- Доступ: Приключения

### Инструмент для прокладки дорожек

- **`/ah path revert <id>`** — Отменить прокладку цементированной дорожки, используя идентификатор из чата, когда вы её проложили. Вам также необходим доступ к инструменту для прокладки дорожек в игре.

- `<id>` — Идентификатор отмены прокладки (UUID), отображаемый в чате при её прокладке.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.path.revert`

- Доступ: Приключения

## Для хостов миров

Это для творческого режима или игроков, управляющих сервером. Не требуется для обычной игры в городе.

- **`/ah difficulty`** — Открыть меню сложности мира для расчета стоимости строительства.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.difficulty`

- Доступ: Творческий

- **`/ah reload`** — Перезагрузить файлы конфигурации и данных мода с диска.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.reload`

- Доступ: Творческий

- **`/ah starterkit`** — Предоставьте себе стартовые инструменты (персонал размещения, устав, стол планирования, персонал здания).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.starterkit`

- Доступ: Творческий

- **`/ah exportskin [path]`** — Сохраните скин своего аватара как файл модели.

- `[path]` — Необязательный путь вывода. По умолчанию — файл данных плагина `avatar_exports` с именем файла с меткой времени.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.exportskin`

- Доступ: Творческий

- **`/ah exportskin <player> [path]`** — Сохраните скин аватара другого игрока (требуется разрешение `.other`).

- `<player>` — Выберите целевого игрока в мире.

- `[path]` — Необязательный путь вывода (тот же, что и выше).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- Доступ: Творческий режим

- **`/ah time <hour>`** — Установить внутриигровое расписание (эти часы используются для выполнения распорядка дня жителя).

- `<hour>` — Час с 0 до 23 (например, `14` для 14:00).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.time`

- Доступ: Творческий режим

- **`/ah time dawn`** — Установить время на 6:00 утра.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- Доступ: Творческий режим

- **`/ah plots finishassembly`** — Мгновенно завершить строительство всех зданий в вашем городе.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- Доступ: Творческий

- **`/ah plots remove <plotId>`** — Удалить один участок из вашего города по идентификатору.

- `<plotId>` — Участок с идентификатором из `plots list`.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- Доступ: Творческий

## Команды отладки

Для тестирования и исправления миров. Не является частью обычной игры.

- **`/ah replace-charter [townName]`** — Вернуть блок устава на место сохранения вашего города, если он был поврежден.

- `[townName]` — Полное название города с пробелами. Опустите для вашего города.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- Доступ: Приключения

- **`/ah towns`** — Вывести список всех городов в этом мире.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.towns`

- Доступ: Творческий режим

- **`/ah poi list [town]`** — Список достопримечательностей города.

- `[town]` — Идентификатор города, `me`, или опустите для вашего города.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.poi.list`

- Доступ: Творческий режим

- **`/ah poi dump`** — Список всех достопримечательностей в мировом реестре.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- Доступ: Творческий режим

- **`/ah plots list`** — Список участков в вашем городе.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.plots.list`

- Доступ: Творческий режим

- **`/ah needs inspect`** — Список жителей с индикаторами потребности поблизости.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- Доступ: Творческий режим

- **`/ah needs set <target> <which> <value>`** — Установите шкалу голода, энергии или веселья жителя деревни.

- `<target>` — Идентификатор жителя, `Elder` или идентификатор сущности.

- `<which>` — `hunger`, `energy` или `fun`.

- `<value>` — От 0 до 100 (100 — полный уровень).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.needs.set`

- Доступ: Творческий режим

- **`/ah tax breakdown`** — Отобразите налоговые строки для казначейства вашего города.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- Доступ: Творческий режим

- **`/ah tax now`** — Немедленно запустить сбор утренних налогов.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.tax.now`

- Доступ: Творческий режим

- **`/ah quest grant [questId]`** — Отметить задание как активное в вашем городе.

- `[questId]` — Идентификатор задания. По умолчанию `q_build_inn`, если не указан.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.quest.grant`

- Доступ: Творческий режим

- **`/ah quest complete [questId]`** — Отметить задание как выполненное в вашем городе.

- `[questId]` — Идентификатор задания. По умолчанию `q_build_inn`, если не указан.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- Доступ: Творческий режим

- **`/ah quest clear [questId]`** — Удалить задание из списка активных заданий вашего города.

- `[questId]` — Идентификатор задания. По умолчанию `q_build_inn`, если не указан.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- Доступ: Творческий режим

- **`/ah quest status`** — Отображать активные и выполненные задания для вашего города.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.quest.status`

- Доступ: Творческий режим

- **`/ah reputation set <villager> <value>`** — Установить свою репутацию у жителя.

- `<villager>` — Идентификатор сущности или роли жителя в вашем городе.

- `<value>` — Репутация от 0 до 100.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- Доступ: Творческий режим

- **`/ah reputation reward list [roleId]`** — Список наград за достижение репутации.

- `[roleId]` — Необязательный фильтр по идентификатору роли (например, `Aetherhaven_Merchant`).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- Доступ: Творческий режим

- **`/ah reputation reward grant <villager> <rewardId>`** — Выдать одну награду за репутацию сейчас.

- `<villager>` — Идентификатор сущности жителя или идентификатор роли в вашем городе.

- `<rewardId>` — Идентификатор награды (например, `rep_merchant_50`).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- Доступ: Творческий режим

- **`/ah villager list`** — Отображение идентификаторов жителей вашего города.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.villager.list`

- Доступ: Творческий режим

- **`/ah villager locate <villager> [--tp]`** — Отображение местоположения жителя (дополнительная телепортация для операторов).

- `<villager>` — Идентификатор жителя или идентификатор его роли в вашем городе.

- `[teleport]` или `--tp` — `true` или `--tp` для телепортации (только для операторов).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- Доступ: Творческий режим

- **`/ah villager reset`** — Перезапуск всех жителей города рядом с вами.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- Доступ: Творческий режим

- **`/ah villager fixinn`** — Исправление проблем с пулом посетителей гостиницы в вашем городе.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- Доступ: Творческий режим

- **`/ah gift resetLimits`** — Сброс лимитов подарков для всех игроков и жителей в мире.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- Доступ: Творческий режим

- **`/ah gift fillHistory <roleId>`** — Заполнение строк предварительного просмотра истории подарков для тестирования.

- `<roleId>` — Идентификатор роли жителя (например, `Aetherhaven_Merchant`).

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- Доступ: Творческий режим

- **`/ah debug-autonomy toggle`** — Включение/выключение отладки автономии для жителя города, которого вы просматриваете.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- Доступ: Творческий режим

- **`/ah debug-autonomy show`** — Показать, включена ли отладка автономии для просматриваемого жителя.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- Доступ: Творческий режим

- **`/ah debug-autonomy clear`** — Отключить отладку автономии для просматриваемого жителя.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- Доступ: Творческий режим

- **`/ah debug-lootchest fill`** — Принудительно применять бонусные броски добычи к просматриваемому сундуку.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- Доступ: Творческий режим

- **`/ah dialogue <treeId> [entryNode]`** — Открыть дерево диалогов по идентификатору для тестирования.

- `<treeId>` — Идентификатор дерева диалогов (например, `aetherhaven_merchant`).

- `[entryNode]` — Начальный узел. По умолчанию `root`.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.dialogue`

- Доступ: Творческий режим

- **`/ah floatinggift spawn`** — Создать плавающий подарочный воздушный шар в вашей позиции.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- Доступ: Творческий режим

- **`/ah path navviz`** — Включить отладочные линии для навигации по пути жителей. Требуется разрешение на использование инструмента «Путь» в игре.

- Разрешение: `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- Доступ: Творческий режим
