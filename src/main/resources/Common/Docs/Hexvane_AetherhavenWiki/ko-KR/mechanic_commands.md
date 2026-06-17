---
name: 명령어
description: "마을 및 서버 도구용 채팅 명령어"
author: Hexvane
---

# 명령어

`/aetherhaven`과 `/ah`은 동일합니다. 대부분의 플레이어는 아래 **플레이어용** 섹션만 필요하시면 됩니다.

**접근 권한**은 해당 명령어가 기본적으로 적용되는 게임 모드를 나타냅니다. **권한**은 접근 권한만으로는 부족한 경우 서버 소유자가 수동으로 부여하는 권한입니다. 꺾쇠괄호(<)는 필수이며, 대괄호(<)는 선택 사항입니다.

## 플레이어용

### 마을 구성원

- **`/ah town invite <player> [townName]`** — 온라인 상태인 플레이어를 내 마을로 초대합니다.

- `<player>` — 플레이어 사용자 이름 (온라인 상태여야 함).

- `[townName]` — 마을 이름 (공백 포함). 자신의 마을인 경우 생략 가능.

- 권한: `hexvane.aetherhaven.command.aetherhaven.town.invite`

- 접근 권한: 모험 모드

- **`/ah town accept [townName]`** — 나를 초대한 마을에 참여합니다.

- `[townName]` — 초대 대기 중인 마을이 여러 개인 경우 마을 이름. 초대 대기 중인 마을이 하나인 경우 생략 가능.

- 권한: `hexvane.aetherhaven.command.aetherhaven.town.accept`

- 접근 권한: 모험

- **`/ah town decline [townName]`** — 마을 초대 거절

- `[townName]` — 초대가 여러 개 있을 경우 마을 이름 전체를 입력합니다. 초대가 하나만 있을 경우 생략하세요.

- 권한: `hexvane.aetherhaven.command.aetherhaven.town.decline`

- 접근 권한: 모험

- **`/ah town kick <player> [townName]`** — 마을에서 멤버 삭제

- `<player>` — 멤버 사용자 이름 (온라인 상태여야 함)

- `[townName]` — 마을 이름 전체 (공백 포함). 본인 마을인 경우 생략하세요.

- 권한: `hexvane.aetherhaven.command.aetherhaven.town.kick`

- 접근 권한: 모험

- **`/ah town role <player> <role> [townName]`** — 멤버 역할 설정: 건설, 퀘스트 또는 둘 다

- `<player>` — 멤버 사용자 이름 (온라인 상태여야 함)

- `<role>` — `BUILD`, `QUEST`, 또는 `BOTH`.

- `[townName]` — 마을 이름(공백 포함). 본인 소유 마을인 경우 생략하세요.

- 권한: `hexvane.aetherhaven.command.aetherhaven.town.role`

- 접근 권한: 어드벤처

- **`/ah town leave`** — 본인이 속한 마을(창립자가 아닌 경우)을 나갑니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.town.leave`

- 접근 권한: 어드벤처

### 떠다니는 선물

- **`/ah floatinggift next`** — 다음 떠다니는 선물 풍선이 나타날 시간을 확인합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- 접근 권한: 어드벤처

### 길 도구

- **`/ah path revert <id>`** — 길을 설치할 때 채팅창에 표시된 ID를 사용하여 설치한 길을 취소합니다. 게임 내에서 길 도구 사용 권한이 필요합니다.

- `<id>` — 경로 배치 시 채팅창에 출력되는 경로 되돌리기 ID(UUID).

- 권한: `hexvane.aetherhaven.command.aetherhaven.path.revert`

- 접근 권한: 어드벤처

## 월드 호스트용

크리에이티브 모드 또는 서버 운영자를 위한 설정입니다. 일반 마을 플레이에는 필요하지 않습니다.

- **`/ah difficulty`** — 건축 비용을 표시하는 월드 난이도 메뉴를 엽니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.difficulty`
- 접근 권한: 크리에이티브

- **`/ah reload`** — 디스크에서 모드 설정 및 데이터 파일을 다시 불러옵니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.reload`

- 접근 권한: 크리에이티브

- **`/ah starterkit`** — 시작 도구(배치 지팡이, 헌장, 계획 데스크, 건축 지팡이)를 자신에게 지급합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.starterkit`

- 접근 권한: 크리에이티브

- **`/ah exportskin [path]`** — 아바타 스킨을 모델 파일로 저장합니다.

- `[path]` — 선택적 출력 경로입니다. 기본값은 타임스탬프가 포함된 파일 이름을 가진 플러그인 데이터 `avatar_exports`입니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.exportskin`

- 접근 권한: 크리에이티브

- **`/ah exportskin <player> [path]`** — 다른 플레이어의 아바타 스킨을 저장합니다(`.other` 권한 필요).

- `<player>` — 게임 내 대상 플레이어입니다.

- `[path]` — 선택적 출력 경로(위와 동일).

- 권한: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- 접근 권한: 크리에이티브

- **`/ah time <hour>`** — 게임 내 일정 시계를 설정합니다(주민 루틴에 사용됨).

- `<hour>` — 0시부터 23시까지(예: 오후 2시는 `14`).

- 권한: `hexvane.aetherhaven.command.aetherhaven.time`

- 접근 권한: 크리에이티브

- **`/ah time dawn`** — 시계를 아침 6시로 설정합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- 접근 권한: 크리에이티브

- **`/ah plots finishassembly`** — 마을에서 아직 건설 중인 모든 건물을 즉시 완성합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`
- 접근 권한: 크리에이티브

- **`/ah plots remove <plotId>`** — ID로 마을에서 구획 하나를 제거합니다.

- `<plotId>` — `plots list`에서 가져온 구획 ID입니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- 접근 권한: 크리에이티브

## 디버그 명령어

월드 테스트 및 수정용입니다. 일반적인 플레이에는 포함되지 않습니다.


- **`/ah replace-charter [townName]`** — 마을의 저장된 위치에 파괴된 헌장 블록을 다시 놓습니다.

- `[townName]` — 마을 이름 (공백 포함). 본인의 마을인 경우 생략하세요.

- 권한: `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- 접근 권한: 어드벤처

- **`/ah towns`** — 이 월드에 있는 모든 마을 목록을 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.towns`

- 접근 권한: 크리에이티브

- **`/ah poi list [town]`** — 마을의 관심 지점(POI) 목록을 표시합니다.

- `[town]` — 마을 ID, `me`, 본인의 마을인 경우 생략하세요.

- 권한: `hexvane.aetherhaven.command.aetherhaven.poi.list`
- 접근 권한: 크리에이티브

- **`/ah poi dump`** — 월드 레지스트리에 등록된 모든 POI 목록을 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- 접근 권한: 크리에이티브

- **`/ah plots list`** — 본인의 마을에 있는 플롯 인스턴스 목록을 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.plots.list`

- 접근 권한: 크리에이티브

- **`/ah needs inspect`** — 근처에 있는 주민의 욕구 게이지 목록을 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- 접근 권한: 크리에이티브

- **`/ah needs set <target> <which> <value>`** — 주민의 허기, 에너지 또는 즐거움 게이지를 설정합니다.

- `<target>` — 주민 핸들, `Elder` 또는 엔티티 ID.

- `<which>` — `hunger`, `energy` 또는 `fun`.

`<value>` — 0에서 100까지 (100은 가득 찬 상태).

- 권한: `hexvane.aetherhaven.command.aetherhaven.needs.set`

- 접근 권한: 크리에이티브

- **`/ah tax breakdown`** — 마을 재정의 세금 항목을 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- 접근 권한: 크리에이티브

- **`/ah tax now`** — 아침 세금 징수를 즉시 실행합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.tax.now`

- 접근 권한: 크리에이티브

- **`/ah quest grant [questId]`** — 마을에서 퀘스트를 활성화 상태로 표시합니다.

- `[questId]` — 퀘스트 ID. 생략 시 기본값은 `q_build_inn`입니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.quest.grant`
- 접근 권한: 크리에이티브

- **`/ah quest complete [questId]`** — 마을에서 퀘스트를 완료 상태로 표시합니다.

- `[questId]` — 퀘스트 ID. 생략 시 기본값은 `q_build_inn`입니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- 접근 권한: 크리에이티브

- **`/ah quest clear [questId]`** — 마을의 활성화된 퀘스트 목록에서 퀘스트를 제거합니다.

- `[questId]` — 퀘스트 ID. 생략 시 기본값은 `q_build_inn`입니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- 접근 권한: 크리에이티브

- **`/ah quest status`** — 마을의 활성 및 완료된 퀘스트를 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.quest.status`

- 접근 권한: 크리에이티브

- **`/ah reputation set <villager> <value>`** — 주민과의 평판을 설정합니다.

- `<villager>` — 주민 엔티티 ID 또는 마을의 역할 ID입니다.

- `<value>` — 평판(0~100)

- 권한: `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- 접근 권한: 크리에이티브

- **`/ah reputation reward list [roleId]`** — 평판 마일스톤 보상 목록을 표시합니다.

- `[roleId]` — 선택적 역할 ID 필터(예: `Aetherhaven_Merchant`)입니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- 접근 권한: 크리에이티브

- **`/ah reputation reward grant <villager> <rewardId>`** — 평판 보상 하나를 지금 지급합니다.

- `<villager>` — 마을 주민 엔티티 ID 또는 마을 내 역할 ID입니다.

- `<rewardId>` — 보상 ID (예: `rep_merchant_50`)

- 권한: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- 접근 권한: 크리에이티브

- **`/ah villager list`** — 마을 주민 엔티티 ID 목록을 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.villager.list`

- 접근 권한: 크리에이티브

- **`/ah villager locate <villager> [--tp]`** — 마을 주민의 위치를 표시합니다 (운영자용 순간 이동 기능 포함).

- `<villager>` — 마을 주민 엔티티 ID 또는 마을 내 역할 ID입니다.

- `[teleport]` 또는 `--tp` — `true` 또는 `--tp`를 사용하여 순간 이동 (운영자 전용).

- 권한: `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- 접근 권한: 크리에이티브

- **`/ah villager reset`** — 주변 마을 주민들을 모두 부활시킵니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- 접근 권한: 크리에이티브

- **`/ah villager fixinn`** — 마을 여관 방문객 풀 문제를 해결합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- 접근 권한: 크리에이티브

- **`/ah gift resetLimits`** — 월드 내 모든 플레이어와 마을 주민의 선물 한도를 초기화합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- 접근 권한: 크리에이티브

- **`/ah gift fillHistory <roleId>`** — 테스트를 위해 선물 내역 미리보기 행을 채웁니다.

- `<roleId>` — 주민 역할 ID (예: `Aetherhaven_Merchant`)

- 권한: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- 접근 권한: 크리에이티브

- **`/ah debug-autonomy toggle`** — 현재 보고 있는 주민의 자율성 디버그 모드를 켜고 끕니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- 접근 권한: 크리에이티브

- **`/ah debug-autonomy show`** — 현재 보고 있는 주민의 자율성 디버그 모드 켜짐 여부를 표시합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- 접근 권한: 크리에이티브

- **`/ah debug-autonomy clear`** — 현재 보고 있는 주민의 자율성 디버그 모드를 끕니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- 접근 권한: 크리에이티브

- **`/ah debug-lootchest fill`** — 현재 보고 있는 상자에서 보너스 전리품이 나오도록 강제합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- 접근 권한: 크리에이티브

- **`/ah dialogue <treeId> [entryNode]`** — 테스트를 위해 ID로 대화 트리를 엽니다.

- `<treeId>` — 대화 트리 ID (예: `aetherhaven_merchant`)

- `[entryNode]` — 시작 노드. 기본값은 `root`입니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.dialogue`

- 접근 권한: 크리에이티브

- **`/ah floatinggift spawn`** — 현재 위치에 떠 있는 선물 풍선을 생성합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- 접근 권한: 크리에이티브

- **`/ah path navviz`** — 주민 이동 경로에 대한 디버그 메시지를 켜고 끕니다. 경로 도구 권한이 필요합니다.

- 권한: `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- 접근 권한: 크리에이티브
