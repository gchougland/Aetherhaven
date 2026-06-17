---
name: コマンド
description: 町やサーバーツール用のチャットコマンド
author: Hexvane
---

# コマンド

`/aetherhaven` と `/ah` は同じです。ほとんどのプレイヤーは、以下の「プレイヤー向け」セクションのみを参照してください。

**アクセス** は、デフォルトでコマンドが適用されるゲームモードを指定します。**権限** は、アクセス権限だけでは不十分な場合に、サーバーオーナーが手動で付与する権限です。山括弧は必須、角括弧は省略可能です。

## プレイヤー向け

### 町のメンバー

- **`/ah town invite <player> [townName]`** — オンラインのプレイヤーを自分の町に招待します。

- `<player>` — プレイヤーのユーザー名（オンラインである必要があります）。

- `[townName]` — スペースを含む町名。自分の町の場合は省略してください。

- 権限: `hexvane.aetherhaven.command.aetherhaven.town.invite`

- アクセス: アドベンチャー

- **`/ah town accept [townName]`** — 招待してくれた町に参加します。

- `[townName]` — 招待待ちの相手が複数いる場合の町名。招待待ちの相手が1人だけの場合は省略してください。

- 権限: `hexvane.aetherhaven.command.aetherhaven.town.accept`

- アクセス: アドベンチャー

- **`/ah town decline [townName]`** — 町への招待を拒否します。

- `[townName]` — 招待が複数ある場合は、町の正式名称を入力してください。招待が1件のみの場合は省略してください。

- 権限: `hexvane.aetherhaven.command.aetherhaven.town.decline`

- アクセス: アドベンチャー

- **`/ah town kick <player> [townName]`** — 町からメンバーを削除します。

- `<player>` — メンバーのユーザー名（オンラインである必要があります）。

- `[townName]` — スペースを含む町の正式名称を入力してください。自分の町の場合は省略してください。

- 権限: `hexvane.aetherhaven.command.aetherhaven.town.kick`

- アクセス: アドベンチャー

- **`/ah town role <player> <role> [townName]`** — メンバーの役割を設定します: 建築、クエスト、または両方。

- `<player>` — メンバーのユーザー名（オンラインである必要があります）。

- `<role>` — `BUILD`、`QUEST`、または`BOTH`。

- `[townName]` — スペースを含む町名を入力してください。自分の町の場合は省略してください。

- 権限: `hexvane.aetherhaven.command.aetherhaven.town.role`

- アクセス: アドベンチャー

- **`/ah town leave`** — 所属している町から退出します（創設者として退出する場合を除く）。

- 権限: `hexvane.aetherhaven.command.aetherhaven.town.leave`

- アクセス: アドベンチャー

### フローティングギフト

- **`/ah floatinggift next`** — 次にフローティングギフトバルーンが出現するタイミングを確認します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- アクセス: アドベンチャー

### パスツール

- **`/ah path revert <id>`** — 設置時にチャットで表示されたIDを使用して、固定されたパスを元に戻します。プレイ中にパスツールへのアクセス権限が必要です。

- `<id>` — パスが配置された際にチャットに表示されるパス復元ID（UUID）。

- 権限：`hexvane.aetherhaven.command.aetherhaven.path.revert`

- アクセス：アドベンチャー

## ワールドホスト向け

これらはクリエイティブモードまたはサーバー運営者向けです。通常のタウンプレイでは必要ありません。

- **`/ah difficulty`** — 建築コストを確認するためにワールド難易度メニューを開きます。

- 権限：`hexvane.aetherhaven.command.aetherhaven.difficulty`

- アクセス：クリエイティブ

- **`/ah reload`** — ディスクからMODの設定ファイルとデータファイルを再読み込みします。

- 権限：`hexvane.aetherhaven.command.aetherhaven.reload`

- アクセス：クリエイティブ

- **`/ah starterkit`** — スターターツール（配置スタッフ、チャーター、プランニングデスク、建築スタッフ）を自分に付与します。

- 権限：`hexvane.aetherhaven.command.aetherhaven.starterkit`

- アクセス：クリエイティブ

- **`/ah exportskin [path]`** — アバターのスキンをモデルファイルとして保存します。

- `[path]` — オプションの出力パス。デフォルトはタイムスタンプ付きファイル名のプラグインデータ `avatar_exports` です。

- 権限: `hexvane.aetherhaven.command.aetherhaven.exportskin`

- アクセス: クリエイティブ

- **`/ah exportskin <player> [path]`** — 他のプレイヤーのアバタースキンを保存します（`.other` の権限が必要です）。

- `<player>` — ワールド内の対象プレイヤーを指定します。

- `[path]` — オプションの出力パス（上記と同じ）。

- 権限: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- アクセス: クリエイティブ

- **`/ah time <hour>`** — ゲーム内のスケジュール時計を設定します（村人のルーチンはこの時計を使用します）。

- `<hour>` — 0時から23時までの時間（例：午後2時 `14`）。

- 権限: `hexvane.aetherhaven.command.aetherhaven.time`

- アクセス: クリエイティブ

- **`/ah time dawn`** — 時計を午前6時に設定します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- アクセス: クリエイティブ

- **`/ah plots finishassembly`** — 町で組み立て中の建物をすべて即座に完成させます。

- 権限: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- アクセス: クリエイティブ

- **`/ah plots remove <plotId>`** — 指定したIDの区画を町から削除します。

- `<plotId>` — `plots list`で指定した区画IDを使用します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- アクセス: クリエイティブ

## デバッグコマンド

ワールドのテストと修正に使用します。通常のプレイには含まれません。

- **`/ah replace-charter [townName]`** — 破損していたチャーターブロックを町の保存場所に戻します。

- `[townName]` — 町名（スペースを含む）を入力してください。ご自身の町の場合は省略してください。

- 権限: `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- アクセス: アドベンチャー

- **`/ah towns`** — このワールドにあるすべての町を一覧表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.towns`

- アクセス: クリエイティブ

- **`/ah poi list [town]`** — 町の興味のある地点を一覧表示します。

- `[town]` — 町のIDを入力してください。`me` または、ご自身の町の場合は省略してください。

- 権限: `hexvane.aetherhaven.command.aetherhaven.poi.list`

- アクセス: クリエイティブ

- **`/ah poi dump`** — ワールドレジストリに登録されているすべての興味のある地点を一覧表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- アクセス: クリエイティブ

- **`/ah plots list`** — ご自身の町にある区画インスタンスを一覧表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.plots.list`

- アクセス: クリエイティブ

- **`/ah needs inspect`** — 近くにニーズメーターがある村人を一覧表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- アクセス: クリエイティブ

- **`/ah needs set <target> <which> <value>`** — 村人の空腹度、エネルギー、または楽しさメーターを設定します。

- `<target>` — 村人のハンドル名、`Elder`、またはエンティティIDを指定します。

- `<which>` — `hunger`、`energy`、または`fun`を指定します。

- `<value>` — 0～100（100が満タン）を指定します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.needs.set`

- アクセス: クリエイティブ

- **`/ah tax breakdown`** — 町の財政の税収明細を表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- アクセス: クリエイティブ

- **`/ah tax now`** — 朝の税金徴収をすぐに実行する。

- 権限: `hexvane.aetherhaven.command.aetherhaven.tax.now`

- アクセス: クリエイティブ

- **`/ah quest grant [questId]`** — 町でクエストをアクティブにする。

- `[questId]` — クエストID。省略した場合、デフォルト値は `q_build_inn` です。

- 権限: `hexvane.aetherhaven.command.aetherhaven.quest.grant`

- アクセス: クリエイティブ

- **`/ah quest complete [questId]`** — 町でクエストを完了する。

- `[questId]` — クエストID。省略した場合、デフォルト値は `q_build_inn` です。

- 権限: `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- アクセス: クリエイティブ

- **`/ah quest clear [questId]`** — 町のアクティブリストからクエストを削除する。

- `[questId]` — クエストID。省略した場合のデフォルト値は `q_build_inn` です。

- 権限: `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- アクセス: クリエイティブ

- **`/ah quest status`** — 町の進行中および完了済みのクエストを表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.quest.status`

- アクセス: クリエイティブ

- **`/ah reputation set <villager> <value>`** — 村人との評判を設定します。

- `<villager>` — 町の村人のエンティティIDまたはロールID。

- `<value>` — 評判（0～100）。

- 権限: `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- アクセス: クリエイティブ

- **`/ah reputation reward list [roleId]`** — 評判のマイルストーン報酬を一覧表示します。

- `[roleId]` — オプションのロールIDフィルター（例: `Aetherhaven_Merchant`）。

- 権限: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- アクセス: クリエイティブ

- **`/ah reputation reward grant <villager> <rewardId>`** — 評判報酬を1つ付与します。

- `<villager>` — あなたの町の村人エンティティIDまたはロールIDを表示します。

- `<rewardId>` — 報酬ID（例: `rep_merchant_50`）を表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- アクセス: クリエイティブ

- **`/ah villager list`** — あなたの町の村人エンティティIDを一覧表示します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.villager.list`

- アクセス: クリエイティブ

- **`/ah villager locate <villager> [--tp]`** — 村人の現在位置を表示します（オペレーターはオプションでテレポートできます）。

- `<villager>` — あなたの町の村人エンティティIDまたはロールIDを表示します。

- `[teleport]` または `--tp` — `true` または `--tp` でテレポートします（オペレーターのみ）。

- 権限: `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- アクセス: クリエイティブ

- **`/ah villager reset`** — 周囲の村人をすべてリスポーンさせます。

- 権限: `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- アクセス: クリエイティブ

- **`/ah villager fixinn`** — 村の宿屋の訪問者プールに関する問題を修正します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- アクセス: クリエイティブ

- **`/ah gift resetLimits`** — 世界中のすべてのプレイヤーと村人のギフト制限をリセットします。

- 権限: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- アクセス: クリエイティブ

- **`/ah gift fillHistory <roleId>`** — テスト用にギフト履歴のプレビュー行を入力します。

- `<roleId>` — 村人の役割ID（例：`Aetherhaven_Merchant`）。

- 権限：`hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- アクセス：クリエイティブ

- **`/ah debug-autonomy toggle`** — 表示されている村人の自律行動デバッグを切り替えます。

- 権限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- アクセス：クリエイティブ

- **`/ah debug-autonomy show`** — 表示されている村人の自律行動デバッグが有効になっているかどうかを表示します。

- 権限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- アクセス：クリエイティブ

- **`/ah debug-autonomy clear`** — 表示されている村人の自律行動デバッグを無効にします。

- 権限：`hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- アクセス：クリエイティブ

- **`/ah debug-lootchest fill`** — 表示されている宝箱のボーナスアイテムロールを強制します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- アクセス: クリエイティブ

- **`/ah dialogue <treeId> [entryNode]`** — テスト用にIDを指定してダイアログツリーを開きます。

- `<treeId>` — ダイアログツリーのID（例: `aetherhaven_merchant`）。

- `[entryNode]` — 開始ノード。デフォルトは `root`。

- 権限: `hexvane.aetherhaven.command.aetherhaven.dialogue`

- アクセス: クリエイティブ

- **`/ah floatinggift spawn`** — プレイヤーの位置に浮遊するギフトバルーンを生成します。

- 権限: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- アクセス: クリエイティブ

- **`/ah path navviz`** — 村人の経路ナビゲーションのデバッグ行の表示/非表示を切り替えます。プレイ中にパスツール権限が必要です。

- 権限: `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- アクセス: クリエイティブ
