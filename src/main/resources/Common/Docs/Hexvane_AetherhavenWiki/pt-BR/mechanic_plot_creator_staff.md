---
name: "Equipe de criação de enredo"
description: "Transforme uma construção em uma definição personalizada de construção urbana."
author: Hexvane
---

# Equipe de criação de terrenos

A equipe de criação de terrenos registra **construções personalizadas** que você construiu no mundo: área ocupada, blocos especiais, exportação de pré-fabricados, custos e (opcionalmente) um terreno ativo na sua cidade. A maioria dos jogadores deve usar **Decoração** ou **Variante**, e não os tipos de locais de trabalho completos abaixo.

Abra seu **diário da cidade → Guia → Mecânicas → Equipe de criação de terrenos** (esta página) enquanto aprende o fluxo. No jogo: equipe a equipe, pressione **F** para iniciar ou abrir o painel da etapa atual, **clique com o botão direito** nos blocos do mundo, **Q** / **E** para a etapa anterior/seguinte, **R** para cancelar.

## Caminhos recomendados

| Objetivo | Tipo de construção | Observações |

|------|----------------|-------|

| Construção cosmética, sem empregos | **Decoração** | Prateleira de registros da cidade opcional; sem lógica de produção ou aldeões. |

| Aparência alternativa para uma construção existente | **Variante** | Selecione qual edifício **principal** ele representa (casa, celeiro, pousada, etc.). As subetapas seguem esse tipo principal. |
| Novo local de trabalho/lote de produção (modificação) | **Trabalho** | Para adicionar novos tipos de locais de trabalho; requer bloco de gerenciamento, armazenamento de produção e um Ponto de Interesse (POI) de superfície de trabalho. |

## Tipos de construção (seletor)

**Decoração** — Parques, adereços e construções que **não** devem funcionar como casas, lojas ou locais de trabalho. Número mínimo de locais necessários.

**Variante** — Um prefab que **representa** outro ID de construção já existente no mod (por exemplo, uma casa personalizada que representa `plot_house`). Você escolhe o tipo principal no menu suspenso; os locais importantes correspondem a esse edifício principal.

**Casa** — Lote residencial: estante de registros da cidade + Ponto de Interesse para dormir.

**Trabalho** — **Uso do desenvolvedor/autor de conteúdo.** Define um novo lote no estilo **local de trabalho**: estante de registros da cidade, armazenamento de produção e um Ponto de Interesse (POI) de superfície de trabalho. Use quando estiver adicionando um novo tipo de edifício de produção ou trabalho, não para variantes cosméticas comuns.

**Comodidade** — Diversão ou lazer (parque, altar): prateleira + ponto de interesse (POI) de diversão/descanso.

**Loja** — Banca ou balcão de loja: prateleira + ponto de interesse (POI) de trabalho com a tag loja.

**Estalagem** — Layout completo de estalagem: prateleira, bancada de trabalho, camas, área para refeições, pontos de surgimento do estalajadeiro e visitantes (e espaço opcional para surgimento do mestre da guilda).

**Prefeitura** — Centro cívico: prateleira, bloco do tesouro, ponto de interesse (POI) da mesa de planejamento.

**Sede da guilda** — Guilda de aventureiros: prateleira, bancada de trabalho, pontos de surgimento de aventureiros.

### Sobreposição e confusão

- **Variante** vs **Casa / Trabalho / …** — A variante serve para "aparência diferente, comportamento semelhante a X". Escolha **Variante** + tipo principal, não **Casa**, se estiver alterando a aparência de um edifício existente no jogo.
- **Trabalho** vs **Loja** — **Loja** é para barracas de comerciantes. **Trabalho** é para fazendas, moinhos, forjas e outros locais de produção.
- **Comodidade** vs **Decoração** — **Decoração** quase não tem elementos de jogabilidade. **Comodidade** define tags de diversão/comodidade e Pontos de Interesse (POIs) para a programação dos aldeões.

- **Estalagem**, **Prefeitura** e **Sede da Guilda** são modelos completos; use **Variante** somente se você estiver combinando um desses modelos de propósito.

## Fluxo (resumido)

1. Marque dois cantos opostos e um canto **externo** para a placa do terreno.

2. Escolha o **tipo de construção** (e a **variante**, se aplicável).

3. Coloque os **pontos importantes** (os blocos são fornecidos um de cada vez por subetapa).

4. Insira o **nome e o ID** (o nome do arquivo prefab segue o ID).

5. Edite as **tags**, se necessário.

6. Abra as **configurações de construção** (F): custo em ouro do tesouro, dias de autoconstrução, opção de pré-fabricado para espaço vazio e seções de montagem.
7. **Exporte o pré-fabricado** com F na etapa de salvar forma (usa as configurações da etapa 6).

8. Defina os **materiais de construção** (baú virtual; os itens retornam a você quando você continua).

9. Revise e salve.

## Permissões

Por padrão, a configuração pode permitir que todos usem o criador de terrenos; servidores podem exigir a permissão `aetherhaven.plot.creator` em vez disso.
