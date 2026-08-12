---
name: Comandos
description: "Comandos de bate-papo para cidades e ferramentas de servidor"
author: Hexvane
---

# Comandos

`/aetherhaven` e `/ah` são iguais. A maioria dos jogadores só precisa da seção **Para jogadores** abaixo.

**Acesso** indica qual modo de jogo recebe o comando por padrão. **Permissão** é o que os administradores do servidor concedem manualmente se o acesso não for suficiente. Os colchetes angulares são obrigatórios; os colchetes retos são opcionais.

## Para jogadores

### Membros da cidade

- **`/ah town invite <player> [townName]`** — Convidar alguém online para a sua cidade.

- `<player>` — Nome de usuário do jogador (deve estar online).

- `[townName]` — Nome completo da cidade com espaços. Omita se for a sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.town.invite`

- Acesso: Aventura

- **`/ah town accept [townName]`** — Entrar em uma cidade que te convidou.

- `[townName]` — Nome completo da cidade quando você tiver mais de um convite pendente. Omita se tiver apenas um.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.town.accept`

- Acesso: Aventura

- **`/ah town decline [townName]`** — Recusar um convite para a cidade.

- `[townName]` — Nome completo da cidade quando houver mais de um convite pendente. Omita se houver apenas um.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.town.decline`

- Acesso: Aventura

- **`/ah town kick <player> [townName]`** — Remover um membro da sua cidade.

- `<player>` — Nome de usuário do membro (deve estar online).

- `[townName]` — Nome completo da cidade com espaços. Omita se for sua própria cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.town.kick`

- Acesso: Aventura

- **`/ah town role <player> <role> [townName]`** — Definir a função de um membro: CONSTRUIR, MISSÃO ou AMBOS.

- `<player>` — Nome de usuário do membro (deve estar online).

- `<role>` — `BUILD`, `QUEST` ou `BOTH`.
- `[townName]` — Nome completo da cidade com espaços. Omita se for sua própria cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.town.role`

- Acesso: Aventura

- **`/ah town leave`** — Sair de uma cidade à qual você pertence (não como fundador).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.town.leave`

- Acesso: Aventura

### Presentes flutuantes

- **`/ah floatinggift next`** — Ver quando seu próximo balão de presente flutuante poderá aparecer.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- Acesso: Aventura

### Ferramenta de caminho

- **`/ah path revert <id>`** — Desfazer um caminho cimentado usando o ID do chat quando você o colocou. Você também precisa ter acesso à ferramenta de caminho durante o jogo.
- `<id>` — ID de reversão do caminho (UUID) impresso no chat quando o caminho foi colocado.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.path.revert`

- Acesso: Aventura

## Para anfitriões do mundo

Essas permissões são para o modo criativo ou para quem administra o servidor. Não são necessárias para jogar normalmente na cidade.

- **`/ah difficulty`** — Abre o menu de dificuldade do mundo para ver os custos de construção.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.difficulty`

- Acesso: Criativo

- **`/ah reload`** — Recarrega os arquivos de configuração e dados do mod do disco.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.reload`

- Acesso: Criativo

- **`/ah starterkit`** — Dá a si mesmo as ferramentas iniciais (equipe de posicionamento, carta, mesa de planejamento, equipe de construção).
- Permissão: `hexvane.aetherhaven.command.aetherhaven.starterkit`

- Acesso: Criativo

- **`/ah exportskin [path]`** — Salvar a skin do seu avatar como um arquivo de modelo.

- `[path]` — Caminho de saída opcional. O padrão é dados do plugin `avatar_exports` com um nome de arquivo com data e hora.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.exportskin`

- Acesso: Criativo

- **`/ah exportskin <player> [path]`** — Salvar a skin do avatar de outro jogador (requer a permissão `.other`).

- `<player>` — Jogador alvo no mundo.

- `[path]` — Caminho de saída opcional (igual ao anterior).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- Acesso: Criativo

- **`/ah time <hour>`** — Definir o relógio da programação do jogo (as rotinas dos aldeões usam este relógio).
- `<hour>` — Hora 0 a 23 (exemplo: `14` para 14h).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.time`

- Acesso: Criativo

- **`/ah time dawn`** — Ajuste o relógio para 6h da manhã.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- Acesso: Criativo

- **`/ah plots finishassembly`** — Conclua instantaneamente todas as construções ainda em andamento na sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- Acesso: Criativo

- **`/ah plots remove <plotId>`** — Remova um lote da sua cidade pelo ID.

- `<plotId>` — ID do lote de `plots list`.
- Permissão: `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- Acesso: Criativo

## Comandos de depuração

Para testar e corrigir mundos. Não fazem parte do jogo normal.

- **`/ah replace-charter [townName]`** — Recoloque o bloco de registro no local salvo da sua cidade, caso tenha sido danificado.

- `[townName]` — Nome completo da cidade com espaços. Omita se for a sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- Acesso: Aventura

- **`/ah towns`** — Lista todas as cidades deste mundo.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.towns`

- Acesso: Criativo

- **`/ah poi list [town]`** — Lista os pontos de interesse de uma cidade.

- `[town]` — ID da cidade, `me`, ou omita se for a sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.poi.list`

- Acesso: Criativo

- **`/ah poi dump`** — Lista todos os Pontos de Interesse (POIs) no registro mundial.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- Acesso: Criativo

- **`/ah plots list`** — Lista as instâncias de terreno na sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.plots.list`

- Acesso: Criativo

- **`/ah needs inspect`** — Lista os moradores com medidores de necessidade próximos.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- Acesso: Criativo

- **`/ah needs set <target> <which> <value>`** — Define um medidor de fome, energia ou diversão para os moradores.

- `<target>` — Identificador do morador, `Elder` ou ID da entidade.
- `<which>` — `hunger`, `energy` ou `fun`.
- `<value>` — 0 a 100 (100 é o limite máximo).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.needs.set`

- Acesso: Criativo

- **`/ah tax breakdown`** — Exibe as linhas de impostos do tesouro da sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- Acesso: Criativo

- **`/ah tax now`** — Executa a cobrança de impostos da manhã imediatamente.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.tax.now`

- Acesso: Criativo

- **`/ah quest grant [questId]`** — Marca uma missão como ativa na sua cidade.

- `[questId]` — ID da missão. O padrão é `q_build_inn` quando omitido.
- Permissão: `hexvane.aetherhaven.command.aetherhaven.quest.grant`

- Acesso: Criativo

- **`/ah quest complete [questId]`** — Marca uma missão como concluída na sua cidade.

- `[questId]` — ID da missão. O padrão é `q_build_inn` quando omitido.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- Acesso: Criativo

- **`/ah quest clear [questId]`** — Remove uma missão da lista de missões ativas da sua cidade.

- `[questId]` — ID da missão. O padrão é `q_build_inn` quando omitido.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- Acesso: Criativo

- **`/ah quest status`** — Mostra as missões ativas e concluídas da sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.quest.status`

- Acesso: Criativo

- **`/ah reputation set <villager> <value>`** — Define sua reputação com um aldeão. - `<villager>` — ID da entidade ou função do aldeão na sua cidade.

- `<value>` — Reputação de 0 a 100.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- Acesso: Criativo

- **`/ah reputation reward list [roleId]`** — Listar recompensas por marcos de reputação.

- `[roleId]` — Filtro opcional por ID de função (exemplo: `Aetherhaven_Merchant`).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- Acesso: Criativo

- **`/ah reputation reward grant <villager> <rewardId>`** — Conceder uma recompensa de reputação agora.

- `<villager>` — ID da entidade ou função do aldeão na sua cidade.

- `<rewardId>` — ID da recompensa (exemplo: `rep_merchant_50`).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- Acesso: Criativo

- **`/ah villager list`** — Lista os IDs das entidades dos aldeões na sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.villager.list`

- Acesso: Criativo

- **`/ah villager locate <villager> [--tp]`** — Mostra a localização de um aldeão (teletransporte opcional para operadores).

- `<villager>` — ID da entidade ou ID da função do aldeão na sua cidade.

- `[teleport]` ou `--tp` — `true` ou `--tp` para teletransportar (somente operadores).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- Acesso: Criativo

- **`/ah villager reset`** — Faz com que todos os aldeões próximos a você reapareçam na cidade.
- Permissão: `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- Acesso: Criativo

- **`/ah villager fixinn`** — Corrige problemas com a quantidade de visitantes na sua cidade.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- Acesso: Criativo

- **`/ah gift resetLimits`** — Redefine os limites de presentes para todos os jogadores e moradores do mundo.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- Acesso: Criativo

- **`/ah gift fillHistory <roleId>`** — Preenche as linhas de pré-visualização do histórico de presentes para testes.

- `<roleId>` — ID da função do morador (exemplo: `Aetherhaven_Merchant`).

- Permissão: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- Acesso: Criativo

- **`/ah debug-autonomy toggle`** — Ativa/desativa o modo de depuração de autonomia do morador da cidade que você está visualizando.
- Permissão: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- Acesso: Criativo

- **`/ah debug-autonomy show`** — Mostra se o modo de depuração de autonomia está ativado para o aldeão que você está visualizando.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- Acesso: Criativo

- **`/ah debug-autonomy clear`** — Desativa o modo de depuração de autonomia para o aldeão que você está visualizando.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- Acesso: Criativo

- **`/ah debug-lootchest fill`** — Força a rolagem de itens bônus no baú que você está visualizando.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- Acesso: Criativo

- **`/ah dialogue <treeId> [entryNode]`** — Abre uma árvore de diálogo por ID para testes.

- `<treeId>` — ID da árvore de diálogo (exemplo: `aetherhaven_merchant`).
- `[entryNode]` — Nó inicial. Padrão: `root`.
- Permissão: `hexvane.aetherhaven.command.aetherhaven.dialogue`
- Acesso: Criativo

- **`/ah floatinggift spawn`** — Gera um balão de presente flutuante na sua posição.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`
- Acesso: Criativo

- **`/ah path navviz`** — Ativa/desativa as linhas de depuração para a navegação dos aldeões. Requer permissão de ferramenta de caminho no jogo.

- Permissão: `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- Acesso: Criativo
