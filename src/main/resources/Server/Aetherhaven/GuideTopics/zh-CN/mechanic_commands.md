---
name: 命令
description: 城镇和服务器工具的聊天命令
author: Hexvane
---

# 命令

`/aetherhaven` 和 `/ah` 的功能相同。大多数玩家只需要阅读下面的“玩家须知”部分。

**访问权限** 指示该命令默认在哪个游戏模式下生效。**权限** 是服务器所有者在访问权限不足时手动授予的权限。尖括号是必需的；方括号是可选的。

## 玩家须知

### 城镇成员

- **`/ah town invite <player> [townName]`** — 邀请在线玩家加入你的城镇。

- `<player>` — 玩家用户名（必须在线）。

- `[townName]` — 完整的城镇名称（包含空格）。如果你的城镇名称为空，则省略此项。

- 权限：`hexvane.aetherhaven.command.aetherhaven.town.invite`

- 访问权限：冒险

- **`/ah town accept [townName]`** — 加入邀请你的城镇。

- `[townName]` — 当你收到多个待处理的邀请时，输入完整的城镇名称。如果你只有一个邀请，则省略此项。

- 权限：`hexvane.aetherhaven.command.aetherhaven.town.accept`

- 访问：冒险

- **`/ah town decline [townName]`** — 拒绝城镇邀请。

- `[townName]` — 如果您有多个待处理的邀请，请填写完整的城镇名称。如果您只有一个邀请，则省略此项。

- 权限：`hexvane.aetherhaven.command.aetherhaven.town.decline`

- 访问：冒险

- **`/ah town kick <player> [townName]`** — 从您的城镇中移除成员。

- `<player>` — 成员用户名（必须在线）。

- `[townName]` — 包含空格的完整城镇名称。如果您创建了自己的城镇，则省略此项。

- 权限：`hexvane.aetherhaven.command.aetherhaven.town.kick`

- 访问：冒险

- **`/ah town role <player> <role> [townName]`** — 设置成员角色：建造者、任务者或两者兼有。

- `<player>` — 成员用户名（必须在线）。

- `<role>` — `BUILD`、`QUEST` 或 `BOTH`。

- `[townName]` — 包含空格的完整城镇名称。创建自己的城镇时请省略此项。

- 权限：`hexvane.aetherhaven.command.aetherhaven.town.role`

- 访问：冒险

- **`/ah town leave`** — 离开您所属的城镇（非创建者）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.town.leave`

- 访问：冒险

### 漂浮礼物

- **`/ah floatinggift next`** — 查看您的下一个漂浮礼物气球何时出现。

- 权限：`hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- 访问：冒险

### 路径工具

- **`/ah path revert <id>`** — 使用放置路径时聊天记录中的 ID 撤销已铺设的路径。您还需要在游戏中拥有路径工具权限。

- `<id>` — 路径放置后，会在聊天框中显示路径还原 ID (UUID)。

- 权限：`hexvane.aetherhaven.command.aetherhaven.path.revert`

- 访问：冒险模式

## 适用于世界主机

这些选项适用于创造模式或服务器运行者。普通城镇游戏不需要这些选项。

- **`/ah difficulty`** — 打开世界难度菜单以查看建筑成本。

- 权限：`hexvane.aetherhaven.command.aetherhaven.difficulty`

- 访问：创造模式

- **`/ah reload`** — 从磁盘重新加载模组配置和数据文件。

- 权限：`hexvane.aetherhaven.command.aetherhaven.reload`

- 访问：创造模式

- **`/ah starterkit`** — 给自己初始工具（放置人员、章程、规划台、建筑人员）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.starterkit`

- 访问：创造模式

- **`/ah exportskin [path]`** — 将您的角色皮肤保存为模型文件。

- `[path]` — 可选输出路径。默认输出为插件数据 `avatar_exports`，文件名带有时间戳。

- 权限：`hexvane.aetherhaven.command.aetherhaven.exportskin`

- 访问权限：创造模式

- **`/ah exportskin <player> [path]`** — 保存其他玩家的头像皮肤（需要 `.other` 权限）。

- `<player>` — 目标玩家。

- `[path]` — 可选输出路径（同上）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- 访问权限：创造模式

- **`/ah time <hour>`** — 设置游戏内时间表（村民的日常活动使用此时间表）。

- `<hour>` — 小时 0 到 23（例如，`14` 表示下午 2 点）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.time`

- 访问：创造模式

- **`/ah time dawn`** — 将时钟设置为早上 6:00。

- 权限：`hexvane.aetherhaven.command.aetherhaven.time.dawn`

- 访问：创造模式

- **`/ah plots finishassembly`** — 立即完成城镇中所有仍在建造的建筑。

- 权限：`hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- 访问：创造模式

- **`/ah plots remove <plotId>`** — 根据 ID 从城镇中移除一个地块。

- `<plotId>` — 地块 ID 来自 `plots list`。

- 权限：`hexvane.aetherhaven.command.aetherhaven.plots.remove`

- 访问：创造模式

## 调试命令

用于测试和修复世界。不属于正常游戏的一部分。

- **`/ah replace-charter [townName]`** — 如果契约方块损坏，将其放回城镇的保存位置。

- `[townName]` — 城镇完整名称（含空格）。您的城镇可省略此项。

- 权限：`hexvane.aetherhaven.command.aetherhaven.replace-charter`

- 访问模式：冒险

- **`/ah towns`** — 列出此世界中的所有城镇。

- 权限：`hexvane.aetherhaven.command.aetherhaven.towns`

- 访问模式：创造

- **`/ah poi list [town]`** — 列出城镇的兴趣点。

- `[town]` — 城镇 ID，`me`，或您的城镇可省略此项。

- 权限：`hexvane.aetherhaven.command.aetherhaven.poi.list`

- 访问模式：创造

- **`/ah poi dump`** — 列出世界注册表中的所有兴趣点。

- 权限：`hexvane.aetherhaven.command.aetherhaven.poi.dump`

- 访问模式：创造

- **`/ah plots list`** — 列出您城镇中的地块实例。

- 权限：`hexvane.aetherhaven.command.aetherhaven.plots.list`

- 访问方式：创造模式

- **`/ah needs inspect`** — 列出附近有需求计量表的村民。

- 权限：`hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- 访问方式：创造模式

- **`/ah needs set <target> <which> <value>`** — 设置村民的饥饿值、精力值或乐趣值。

- `<target>` — 村民的句号、`Elder` 或实体 ID。

- `<which>` — `hunger`、`energy` 或 `fun`。

- `<value>` — 0 到 100（100 表示已满）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.needs.set`

- 访问方式：创造模式

- **`/ah tax breakdown`** — 显示城镇财政的税收项目。

- 权限：`hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- 访问权限：创造模式

- **`/ah tax now`** — 立即进行早晨的税收征收。

- 权限：`hexvane.aetherhaven.command.aetherhaven.tax.now`

- 访问权限：创造模式

- **`/ah quest grant [questId]`** — 将城镇中的任务标记为激活状态。

- `[questId]` — 任务 ID。省略时默认为 `q_build_inn`。

- 权限：`hexvane.aetherhaven.command.aetherhaven.quest.grant`

- 访问权限：创造模式

- **`/ah quest complete [questId]`** — 将城镇中的任务标记为已完成。

- `[questId]` — 任务 ID。省略时默认为 `q_build_inn`。

- 权限：`hexvane.aetherhaven.command.aetherhaven.quest.complete`

- 访问权限：创造模式

- **`/ah quest clear [questId]`** — 从城镇的激活列表中移除任务。

- `[questId]` — 任务 ID。省略时默认值为 `q_build_inn`。

- 权限：`hexvane.aetherhaven.command.aetherhaven.quest.clear`

- 访问权限：创造模式

- **`/ah quest status`** — 显示您城镇中正在进行和已完成的任务。

- 权限：`hexvane.aetherhaven.command.aetherhaven.quest.status`

- 访问权限：创造模式

- **`/ah reputation set <villager> <value>`** — 设置您与村民的声望。

- `<villager>` — 您城镇中村民的实体 ID 或角色 ID。

- `<value>` — 声望值，范围 0 到 100。

- 权限：`hexvane.aetherhaven.command.aetherhaven.reputation.set`

- 访问权限：创造模式

- **`/ah reputation reward list [roleId]`** — 列出声望里程碑奖励。

- `[roleId]` — 可选的角色 ID 筛选器（例如 `Aetherhaven_Merchant`）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- 访问方式：创造模式

- **`/ah reputation reward grant <villager> <rewardId>`** — 立即发放一个声望奖励。

- `<villager>` — 您城镇中的村民实体 ID 或角色 ID。

- `<rewardId>` — 奖励 ID（例如 `rep_merchant_50`）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- 访问方式：创造模式

- **`/ah villager list`** — 列出您城镇中的村民实体 ID。

- 权限：`hexvane.aetherhaven.command.aetherhaven.villager.list`

- 访问方式：创造模式

- **`/ah villager locate <villager> [--tp]`** — 显示村民的位置（管理员可选择传送）。

- `<villager>` — 您城镇中的村民实体 ID 或角色 ID。

- 使用 `[teleport]` 或 `--tp` — 使用 `true` 或 `--tp` 进行传送（仅限操作员）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.villager.locate`

- 访问模式：创造模式

- **`/ah villager reset`** — 刷新你附近所有城镇村民。

- 权限：`hexvane.aetherhaven.command.aetherhaven.villager.reset`

- 访问模式：创造模式

- **`/ah villager fixinn`** — 修复你城镇的旅店访客数量问题。

- 权限：`hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- 访问模式：创造模式

- **`/ah gift resetLimits`** — 重置世界上所有玩家和村民的礼物赠送上限。

- 权限：`hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- 访问模式：创造模式

- **`/ah gift fillHistory <roleId>`** — 填充礼物历史记录预览行以进行测试。

- `<roleId>` — 村民角色 ID（例如 `Aetherhaven_Merchant`）。

- 权限：`hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- 访问方式：创造模式

- **`/ah debug-autonomy toggle`** — 开启或关闭当前查看的村民的自主调试模式。

- 权限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- 访问方式：创造模式

- **`/ah debug-autonomy show`** — 显示当前查看的村民是否开启了自主调试模式。

- 权限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- 访问方式：创造模式

- **`/ah debug-autonomy clear`** — 关闭当前查看的村民的自主调试模式。

- 权限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- 访问方式：创造模式

- **`/ah debug-lootchest fill`** — 强制开启当前查看的宝箱的额外掉落物品。

- 权限：`hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- 访问方式：创造模式

- **`/ah dialogue <treeId> [entryNode]`** — 通过 ID 打开对话树进行测试。

- `<treeId>` — 对话树 ID（例如 `aetherhaven_merchant`）。

- `[entryNode]` — 起始节点。默认值为 `root`。

- 权限：`hexvane.aetherhaven.command.aetherhaven.dialogue`

- 访问方式：创造模式

- **`/ah floatinggift spawn`** — 在你当前位置生成一个漂浮的礼物气球。

- 权限：`hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- 访问方式：创造模式

- **`/ah path navviz`** — 切换村民路径导航的调试信息。需要开启路径工具权限。

- 权限：`hexvane.aetherhaven.command.aetherhaven.path.navviz`

- 访问方式：创造模式
