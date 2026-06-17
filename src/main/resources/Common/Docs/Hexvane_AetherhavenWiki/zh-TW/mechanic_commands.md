---
name: 命令
description: 城鎮和伺服器工具的聊天命令
author: Hexvane
---

# 指令

`/aetherhaven` 和 `/ah` 的功能相同。大多數玩家只需要閱讀下面的「玩家須知」部分。

**存取權限** 指示該指令預設在哪個遊戲模式下生效。 **權限** 是伺服器擁有者在存取權限不足時手動授予的權限。尖括號是必需的；方括號是可選的。

## 玩家須知

### 城鎮成員

- **`/ah town invite <player> [townName]`** — 邀請線上玩家加入你的城鎮。

- `<player>` — 玩家使用者名稱（必須在線上）。

- `[townName]` — 完整的城鎮名稱（含空格）。如果你的城鎮名稱為空，則省略此項。

- 權限：`hexvane.aetherhaven.command.aetherhaven.town.invite`

- 存取權限：冒險

- **`/ah town accept [townName]`** — 加入邀請你的城鎮。

- `[townName]` — 當你收到多個待處理的邀請時，輸入完整的城鎮名稱。如果你只有一個邀請，則省略此項。

- 權限：`hexvane.aetherhaven.command.aetherhaven.town.accept`

- 參觀：冒險

- **`/ah town decline [townName]`** — 拒絕城鎮邀請。

- `[townName]` — 如果您有多個待處理的邀請，請填寫完整的城鎮名稱。如果您只有一個邀請，則省略此項目。

- 權限：`hexvane.aetherhaven.command.aetherhaven.town.decline`

- 參觀：冒險

- **`/ah town kick <player> [townName]`** — 從您的城鎮中移除成員。

- `<player>` — 成員使用者名稱（必須在線上）。

- `[townName]` — 包含空格的完整城鎮名稱。如果您建立了自己的城鎮，則省略此項目。

- 權限：`hexvane.aetherhaven.command.aetherhaven.town.kick`

- 參觀：冒險

- **`/ah town role <player> <role> [townName]`** — 設定成員角色：建造者、任務者或兩者兼有。

- `<player>` — 成員使用者名稱（必須在線上）。

- `<role>` — `BUILD`、`QUEST` 或 `BOTH`。

- `[townName]` — 包含空格的完整城鎮名稱。創建自己的城鎮時請省略此項目。

- 權限：`hexvane.aetherhaven.command.aetherhaven.town.role`

- 參觀：冒險

- **`/ah town leave`** — 離開您所屬的城鎮（非創建者）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.town.leave`

- 參觀：冒險

### 漂浮禮物

- **`/ah floatinggift next`** — 看看您的下一個漂浮禮物氣球何時出現。

- 權限：`hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- 參觀：冒險

### 路徑工具

- **`/ah path revert <id>`** — 使用放置路徑時聊天記錄中的 ID 撤銷已鋪設的路徑。您還需要在遊戲中擁有路徑工具權限。

- `<id>` — 路徑放置後，會在聊天方塊中顯示路徑還原 ID (UUID)。

- 權限：`hexvane.aetherhaven.command.aetherhaven.path.revert`

- 訪問：冒險模式

## 適用於世界主機

這些選項適用於創造模式或伺服器運行者。普通城鎮遊戲不需要這些選項。

- **`/ah difficulty`** — 打開世界難度選單以查看建築成本。

- 權限：`hexvane.aetherhaven.command.aetherhaven.difficulty`

- 訪問：創造模式

- **`/ah reload`** — 從磁碟重新載入模組配置和資料檔案。

- 權限：`hexvane.aetherhaven.command.aetherhaven.reload`

- 訪問：創造模式

- **`/ah starterkit`** — 給自己初始工具（放置人員、章程、規劃台、建築人員）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.starterkit`

- 訪問：創造模式

- **`/ah exportskin [path]`** — 將您的角色皮膚儲存為模型檔案。

- `[path]` — 可選輸出路徑。預設輸出為插件資料 `avatar_exports`，檔案名稱帶有時間戳記。

- 權限：`hexvane.aetherhaven.command.aetherhaven.exportskin`

- 存取權限：創造模式

- **`/ah exportskin <player> [path]`** — 保存其他玩家的頭像皮膚（需要 `.other` 權限）。

- `<player>` — 目標玩家。

- `[path]` — 可選輸出路徑（同上）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- 存取權限：創造模式

- **`/ah time <hour>`** — 設定遊戲內時間表（村民的日常活動使用此時間表）。

- `<hour>` — 小時 0 到 23（例如，`14` 表示下午 2 點）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.time`

- 訪問：創造模式

- **`/ah time dawn`** — 將時鐘設定為早上 6:00。

- 權限：`hexvane.aetherhaven.command.aetherhaven.time.dawn`

- 訪問：創造模式

- **`/ah plots finishassembly`** — 立即完成城鎮中所有仍在建造的建築物。

- 權限：`hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- 訪問：創造模式

- **`/ah plots remove <plotId>`** — 根據 ID 從城鎮中移除一個地塊。

- `<plotId>` — 地塊 ID 來自 `plots list`。

- 權限：`hexvane.aetherhaven.command.aetherhaven.plots.remove`

- 訪問：創造模式

## 偵錯指令

用於測試和修復世界。不屬於正常遊戲的一部分。

- **`/ah replace-charter [townName]`** — 如果契約方塊損壞，將其放回城鎮的保存位置。

- `[townName]` — 城鎮完整名稱（含空格）。您的城鎮可省略此項。

- 權限：`hexvane.aetherhaven.command.aetherhaven.replace-charter`

- 訪問模式：冒險

- **`/ah towns`** — 列出此世界中的所有城鎮。

- 權限：`hexvane.aetherhaven.command.aetherhaven.towns`

- 訪問模式：創造

- **`/ah poi list [town]`** — 列出城鎮的興趣點。

- `[town]` — 城鎮 ID，`me`，或您的城鎮可省略此項。

- 權限：`hexvane.aetherhaven.command.aetherhaven.poi.list`

- 訪問模式：創造

- **`/ah poi dump`** — 列出世界註冊表中的所有興趣點。

- 權限：`hexvane.aetherhaven.command.aetherhaven.poi.dump`

- 訪問模式：創造

- **`/ah plots list`** — 列出您城鎮中的地塊實例。

- 權限：`hexvane.aetherhaven.command.aetherhaven.plots.list`

- 訪問方式：創造模式

- **`/ah needs inspect`** — 列出附近有需求計量表的村民。

- 權限：`hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- 訪問方式：創造模式

- **`/ah needs set <target> <which> <value>`** — 設定村民的飢餓值、精力值或樂趣值。

- `<target>` — 村民的句號、`Elder` 或實體 ID。

- `<which>` — `hunger`、`energy` 或 `fun`。

- `<value>` — 0 到 100（100 表示已滿）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.needs.set`

- 訪問方式：創造模式

- **`/ah tax breakdown`** — 顯示城鎮財政的稅收項目。

- 權限：`hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- 存取權限：創造模式

- **`/ah tax now`** — 立即進行早晨的稅收徵收。

- 權限：`hexvane.aetherhaven.command.aetherhaven.tax.now`

- 存取權限：創造模式

- **`/ah quest grant [questId]`** — 將城鎮中的任務標記為啟動狀態。

- `[questId]` — 任務 ID。省略時預設為 `q_build_inn`。

- 權限：`hexvane.aetherhaven.command.aetherhaven.quest.grant`

- 存取權限：創造模式

- **`/ah quest complete [questId]`** — 將城鎮中的任務標記為已完成。

- `[questId]` — 任務 ID。省略時預設為 `q_build_inn`。

- 權限：`hexvane.aetherhaven.command.aetherhaven.quest.complete`

- 存取權限：創造模式

- **`/ah quest clear [questId]`** — 從城鎮的啟動清單中移除任務。

- `[questId]` — 任務 ID。省略時預設值為 `q_build_inn`。

- 權限：`hexvane.aetherhaven.command.aetherhaven.quest.clear`

- 存取權限：創造模式

- **`/ah quest status`** — 顯示您城鎮中正在進行和已完成的任務。

- 權限：`hexvane.aetherhaven.command.aetherhaven.quest.status`

- 存取權限：創造模式

- **`/ah reputation set <villager> <value>`** — 設定您與村民的聲望。

- `<villager>` — 您城鎮中村民的實體 ID 或角色 ID。

- `<value>` — 聲望值，範圍 0 到 100。

- 權限：`hexvane.aetherhaven.command.aetherhaven.reputation.set`

- 存取權限：創造模式

- **`/ah reputation reward list [roleId]`** — 列出聲望里程碑獎勵。

- `[roleId]` — 可選的角色 ID 篩選器（例如 `Aetherhaven_Merchant`）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- 訪問方式：創造模式

- **`/ah reputation reward grant <villager> <rewardId>`** — 立即發放一個聲望獎勵。

- `<villager>` — 您城鎮中的村民實體 ID 或角色 ID。

- `<rewardId>` — 獎勵 ID（例如 `rep_merchant_50`）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- 訪問方式：創造模式

- **`/ah villager list`** — 列出您城鎮中的村民實體 ID。

- 權限：`hexvane.aetherhaven.command.aetherhaven.villager.list`

- 訪問方式：創造模式

- **`/ah villager locate <villager> [--tp]`** — 顯示村民的位置（管理員可選擇傳送）。

- `<villager>` — 您城鎮中的村民實體 ID 或角色 ID。

- 使用 `[teleport]` 或 `--tp` — 使用 `true` 或 `--tp` 進行傳送（僅限操作者）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.villager.locate`

- 訪問模式：創造模式

- **`/ah villager reset`** — 刷新你附近所有城鎮村民。

- 權限：`hexvane.aetherhaven.command.aetherhaven.villager.reset`

- 訪問模式：創造模式

- **`/ah villager fixinn`** — 修復你城鎮的旅館訪客數量問題。

- 權限：`hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- 訪問模式：創造模式

- **`/ah gift resetLimits`** — 重置世界上所有玩家和村民的禮物贈送上限。

- 權限：`hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- 訪問模式：創造模式

- **`/ah gift fillHistory <roleId>`** — 填入禮物歷史記錄預覽行以進行測試。

- `<roleId>` — 村民角色 ID（例如 `Aetherhaven_Merchant`）。

- 權限：`hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- 訪問方式：創造模式

- **`/ah debug-autonomy toggle`** — 開啟或關閉目前檢視的村民的自主除錯模式。

- 權限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- 訪問方式：創造模式

- **`/ah debug-autonomy show`** — 顯示目前檢視的村民是否開啟了自主除錯模式。

- 權限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- 訪問方式：創造模式

- **`/ah debug-autonomy clear`** — 關閉目前檢視的村民的自主除錯模式。

- 權限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- 訪問方式：創造模式

- **`/ah debug-lootchest fill`** — 強制開啟目前檢視的寶箱的額外掉落物品。

- 權限：`hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- 訪問方式：創造模式

- **`/ah dialogue <treeId> [entryNode]`** — 透過 ID 開啟對話樹進行測試。

- `<treeId>` — 對話樹 ID（例如 `aetherhaven_merchant`）。

- `[entryNode]` — 起始節點。預設值為 `root`。

- 權限：`hexvane.aetherhaven.command.aetherhaven.dialogue`

- 訪問方式：創造模式

- **`/ah floatinggift spawn`** — 在你目前位置產生一個漂浮的禮物氣球。

- 權限：`hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- 訪問方式：創造模式

- **`/ah path navviz`** — 切換村民路徑導航的偵錯資訊。需要開啟路徑工具權限。

- 權限：`hexvane.aetherhaven.command.aetherhaven.path.navviz`

- 訪問方式：創造模式
