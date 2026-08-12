---
name: Команди
description: "Команди чату для міст та інструментів сервера"
author: Hexvane
---

# Команди

`/aetherhaven` та `/ah` однакові. Більшості гравців потрібен лише розділ **Для гравців** нижче.

**Доступ** вказує, який ігровий режим отримує команду за замовчуванням. **Дозвіл** – це те, що власники сервера надають вручну, якщо доступу недостатньо. Кутові дужки обов'язкові; квадратні дужки необов'язкові.

## Для гравців

### Учасники міста

- **`/ah town invite <player> [townName]`** — Запросіть когось онлайн до свого міста.
- `<player>` — Ім'я користувача гравця (має бути онлайн).
- `[townName]` — Повна назва міста з пробілами. Пропустіть для вашого міста.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.town.invite`
- Доступ: Пригоди

- **`/ah town accept [townName]`** — Приєднатися до міста, яке вас запросило.
- `[townName]` — Повна назва міста, якщо у вас є більше одного запрошення, що очікує на розгляд. Пропустіть, якщо у вас є лише одне.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.town.accept`
- Доступ: Пригоди

- **`/ah town decline [townName]`** — Відхилити запрошення міста.
- `[townName]` — Повна назва міста, якщо у вас є більше одного запрошення, що очікує на розгляд. Пропустіть, якщо у вас є лише одне.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.town.decline`
- Доступ: Пригоди

- **`/ah town kick <player> [townName]`** — Видалити учасника з вашого міста.
- `<player>` — Ім'я користувача учасника (має бути онлайн).
- `[townName]` — Повна назва міста з пробілами. Пропустіть для вашого міста.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.town.kick`
- Доступ: Пригоди

- **`/ah town role <player> <role> [townName]`** — Встановити роль учасника: БУДІВНИЦТВО, ЗАВДАННЯ або ОБИДВА.
- `<player>` — Ім'я користувача учасника (має бути онлайн).
- `<role>` — `BUILD`, `QUEST` або `BOTH`.
- `[townName]` — Повна назва міста з пробілами. Пропустіть для вашого власного міста.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.town.role`
- Доступ: Пригоди

- **`/ah town leave`** — Залиште місто, до якого ви належите (не як засновник).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.town.leave`
- Доступ: Пригоди

### Плаваючі подарунки

- **`/ah floatinggift next`** — Дізнайтеся, коли може з'явитися ваша наступна плаваюча подарункова кулька.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`
- Доступ: Пригоди

### Інструмент «Шлях»

- **`/ah path revert <id>`** — Скасуйте закріплений шлях, використовуючи ідентифікатор з чату під час його розміщення. Вам також потрібен доступ до інструмента «Шлях» у грі.
- `<id>` — Ідентифікатор повернення шляху (UUID), надрукований у чаті під час розміщення шляху.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.path.revert`
- Доступ: Пригода

## Для господарів світу

Це для творчого режиму або людей, які керують сервером. Не потрібно для звичайної гри в місті.

- **`/ah difficulty`** — Відкрити меню складності світу для вартості будівництва.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.difficulty`
- Доступ: Творчий

- **`/ah reload`** — Перезавантажити конфігурацію мода та файли даних з диска.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.reload`
- Доступ: Творчий

- **`/ah starterkit`** — Отримайте початкові інструменти (персонал розміщення, статут, стіл планування, персонал будівництва).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.starterkit`
- Доступ: Творчий

- **`/ah exportskin [path]`** — Збережіть скін вашого аватара як файл моделі.
- `[path]` — Додатковий вихідний шлях. За замовчуванням використовуються дані плагіна `avatar_exports` з назвою файлу з міткою часу.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.exportskin`
- Доступ: Творчий

- **`/ah exportskin <player> [path]`** — Зберегти скін аватара іншого гравця (потрібен дозвіл `.other`).
- `<player>` — Цільовий гравець у світі.
- `[path]` — Додатковий вихідний шлях (такий самий, як вище).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`
- Доступ: Творчий

- **`/ah time <hour>`** — Встановити годинник ігрового розкладу (цей годинник використовується для розпорядку селян).
- `<hour>` — Година від 0 до 23 (наприклад, `14` для 14:00).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.time`
- Доступ: Творчий

- **`/ah time dawn`** — Встановити цей годинник на 6:00 ранку.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.time.dawn`
- Доступ: Творчий

- **`/ah plots finishassembly`** — Миттєво завершити кожну будівлю, що все ще будується у вашому місті.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`
- Доступ: Творчий

- **`/ah plots remove <plotId>`** — Видалити одну ділянку з вашого міста за ідентифікатором.
- `<plotId>` — Ідентифікатор ділянки з `plots list`.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.plots.remove`
- Доступ: Творчий

## Команди налагодження

Для тестування та виправлення світів. Не є частиною звичайної гри.

- **`/ah replace-charter [townName]`** — Поверніть блок статуту на збережене місце вашого міста, якщо він був зламаний.
- `[townName]` — Повна назва міста з пробілами. Пропустіть для вашого міста.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.replace-charter`
- Доступ: Пригодницький

- **`/ah towns`** — Перелічіть усі міста в цьому світі.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.towns`
- Доступ: Творчий

- **`/ah poi list [town]`** — Перелік визначних місць міста.
- `[town]` — Ідентифікатор міста, `me`, або пропустити для вашого міста.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.poi.list`
- Доступ: Творчий

- **`/ah poi dump`** — Перелік усіх визначних місць у світовому реєстрі.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.poi.dump`
- Доступ: Творчий

- **`/ah plots list`** — Перелік екземплярів сюжетів у вашому місті.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.plots.list`
- Доступ: Творчий

- **`/ah needs inspect`** — Перелік жителів села з лічильниками потреб поблизу.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`
- Доступ: Творчий

- **`/ah needs set <target> <which> <value>`** — Встановити лічильник голоду, енергії або веселощів жителя села.
- `<target>` — Ім'я мешканця села, `Elder` або ідентифікатор сутності.
- `<which>` — `hunger`, `energy` або `fun`.
- `<value>` — від 0 до 100 (100 — повне поле).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.needs.set`
- Доступ: Творчий

- **`/ah tax breakdown`** — Показати податкові рядки для вашої міської скарбниці.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`
- Доступ: Творчий

- **`/ah tax now`** — Негайно розпочати ранковий збір податків.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.tax.now`
- Доступ: Творчий

- **`/ah quest grant [questId]`** — Позначити квест як активний у вашому місті.
- `[questId]` — Ідентифікатор квесту. За замовчуванням `q_build_inn`, якщо пропущено.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.quest.grant`
- Доступ: Творчий

- **`/ah quest complete [questId]`** — Позначити завдання як виконане у вашому місті.
- `[questId]` — Ідентифікатор завдання. За замовчуванням `q_build_inn`, якщо пропущено.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.quest.complete`
- Доступ: Творчий

- **`/ah quest clear [questId]`** — Видалити завдання зі списку активних завдань вашого міста.
- `[questId]` — Ідентифікатор завдання. За замовчуванням `q_build_inn`, якщо пропущено.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.quest.clear`
- Доступ: Творчий

- **`/ah quest status`** — Показати активні та виконані завдання для вашого міста.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.quest.status`
- Доступ: Творчий

- **`/ah reputation set <villager> <value>`** — Встановити свою репутацію у селянина.
- `<villager>` — Ідентифікатор сутності селянина або ідентифікатор ролі у вашому місті.
- `<value>` — Репутація від 0 до 100.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.reputation.set`
- Доступ: Творчий

- **`/ah reputation reward list [roleId]`** — Перелік нагород за досягнення репутації.
- `[roleId]` — Додатковий фільтр ідентифікатора ролі (приклад `Aetherhaven_Merchant`).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`
- Доступ: Творчий

- **`/ah reputation reward grant <villager> <rewardId>`** — Надайте одну нагороду за репутацію зараз.
- `<villager>` — Ідентифікатор сутності селянина або ідентифікатор ролі у вашому місті.
- `<rewardId>` — Ідентифікатор нагороди (приклад `rep_merchant_50`).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`
- Доступ: Творчий

- **`/ah villager list`** — Перелік ідентифікаторів сутностей селянина у вашому місті.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.villager.list`
- Доступ: Творчий

- **`/ah villager locate <villager> [--tp]`** — Показати місцезнаходження селянина (необов'язковий телепорт для операторів).
- `<villager>` — Ідентифікатор сутності селянина або ідентифікатор ролі у вашому місті.
- `[teleport]` або `--tp` — `true` або `--tp` для телепортації (лише для операторів).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.villager.locate`
- Доступ: Творчий

- **`/ah villager reset`** — Відродити всіх селян поблизу вас.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.villager.reset`
- Доступ: Творчий

- **`/ah villager fixinn`** — Виправити проблеми з пулом відвідувачів готелю для вашого міста.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`
- Доступ: Творчий

- **`/ah gift resetLimits`** — Скинути ліміти подарунків для всіх гравців та жителів села у світі.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`
- Доступ: Творчий

- **`/ah gift fillHistory <roleId>`** — Заповнити рядки попереднього перегляду історії подарунків для тестування.
- `<roleId>` — Ідентифікатор ролі жителя села (наприклад `Aetherhaven_Merchant`).
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`
- Доступ: Творчий

- **`/ah debug-autonomy toggle`** — Увімкнути/вимкнути налагодження автономії для жителя села, якого ви переглядаєте.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`
- Доступ: Творчий

- **`/ah debug-autonomy show`** — Показати, чи ввімкнено налагодження автономії для жителя села, якого ви переглядаєте.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`
- Доступ: Творчий

- **`/ah debug-autonomy clear`** — Вимкнути налагодження автономії для селянина, на якого ви дивитеся.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`
- Доступ: Творчий

- **`/ah debug-lootchest fill`** — Примусово виконати бонусні кидки здобичі на скриню, на яку ви дивитеся.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`
- Доступ: Творчий

- **`/ah dialogue <treeId> [entryNode]`** — Відкрити дерево діалогів за ідентифікатором для тестування.
- `<treeId>` — Ідентифікатор дерева діалогів (наприклад `aetherhaven_merchant`).
- `[entryNode]` — Початковий вузол. За замовчуванням `root`.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.dialogue`
- Доступ: Творчий

- **`/ah floatinggift spawn`** — Створити плавучу подарункову кульку на вашій позиції.
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`
- Доступ: Творчий

- **`/ah path navviz`** — Перемикання ліній налагодження для навігації шляхом селян. Потрібен дозвіл на використання інструмента «Шлях».
- Дозвіл: `hexvane.aetherhaven.command.aetherhaven.path.navviz`
- Доступ: Творчий
