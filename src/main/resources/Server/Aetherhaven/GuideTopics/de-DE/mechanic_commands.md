---
name: Befehle
description: "Chat-Befehle für Städte und Server-Tools"
author: Hexvane
---

# Befehle

`/aetherhaven` und `/ah` sind identisch. Die meisten Spieler benötigen nur den Abschnitt **Für Spieler** weiter unten.

**Zugriff** gibt an, in welchem Spielmodus der Befehl standardmäßig ausgeführt wird. **Berechtigung** wird vom Serverbesitzer manuell erteilt, falls der Zugriff nicht ausreicht. Spitze Klammern sind erforderlich; eckige Klammern sind optional.

## Für Spieler

### Stadtmitglieder

- **`/ah town invite <player> [townName]`** — Lade jemanden online in deine Stadt ein.

- `<player>` — Benutzername des Spielers (muss online sein).

- `[townName]` — Vollständiger Stadtname mit Leerzeichen. Für deine Stadt weglassen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.town.invite`

- Zugriff: Abenteuer

- **`/ah town accept [townName]`** — Tritt einer Stadt bei, die dich eingeladen hat.

- `[townName]` — Vollständiger Stadtname, wenn du mehrere ausstehende Einladungen hast. Weglassen, wenn du nur eine hast.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.town.accept`
- Zugriff: Abenteuer

- **`/ah town decline [townName]`** — Eine Stadteinladung ablehnen.

- `[townName]` — Vollständiger Stadtname, wenn mehrere Einladungen vorliegen. Weglassen, wenn nur eine vorliegt.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.town.decline`

- Zugriff: Abenteuer

- **`/ah town kick <player> [townName]`** — Ein Mitglied aus der eigenen Stadt entfernen.

- `<player>` — Benutzername des Mitglieds (online erforderlich).

- `[townName]` — Vollständiger Stadtname mit Leerzeichen. Für die eigene Stadt weglassen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.town.kick`

- Zugriff: Abenteuer

- **`/ah town role <player> <role> [townName]`** — Rolle eines Mitglieds festlegen: BAUEN, ABRECHNEN oder BEIDES.

- `<player>` — Benutzername des Mitglieds (online erforderlich).

- `<role>` — `BUILD`, `QUEST` oder `BOTH`.

- `[townName]` — Vollständiger Stadtname mit Leerzeichen. Für deine eigene Stadt weglassen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.town.role`

- Zugriff: Abenteuer

- **`/ah town leave`** — Verlasse eine Stadt, der du angehörst (nicht als Gründer).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.town.leave`

- Zugriff: Abenteuer

### Schwebende Geschenke

- **`/ah floatinggift next`** — Sieh dir an, wann dein nächster schwebender Geschenkballon erscheinen kann.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- Zugriff: Abenteuer

### Pfadwerkzeug

- **`/ah path revert <id>`** — Entferne einen befestigten Pfad mithilfe der ID aus dem Chat, mit der du ihn platziert hast. Du benötigst außerdem Zugriff auf das Pfadwerkzeug im Spiel.

- `<id>` — Pfad-Revert-ID (UUID), die beim Platzieren des Pfades im Chat angezeigt wird.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.path.revert`

- Zugriff: Abenteuer

## Für Weltenhosts

Diese Berechtigungen sind für den Kreativmodus oder Serverbetreiber. Sie werden im normalen Stadtspiel nicht benötigt.

- **`/ah difficulty`** — Öffnet das Menü für den Weltenschwierigkeitsgrad und die Baukosten.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.difficulty`

- Zugriff: Kreativ

- **`/ah reload`** — Lädt die Mod-Konfigurations- und Datendateien von der Festplatte neu.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.reload`

- Zugriff: Kreativ

- **`/ah starterkit`** — Gibt dir die Startausrüstung (Platzierungsstab, Charta, Planungstisch, Baustab).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.starterkit`

- Zugriff: Kreativ

- **`/ah exportskin [path]`** — Speichert deinen Avatar-Skin als Modelldatei.

- `[path]` – Optionaler Ausgabepfad. Standardmäßig werden die Plugin-Daten (`avatar_exports`) mit einem Zeitstempel im Dateinamen gespeichert.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.exportskin`

- Zugriff: Kreativmodus

- **`/ah exportskin <player> [path]`** – Speichert den Avatar-Skin eines anderen Spielers (benötigt die Berechtigung `.other`).

- `<player>` – Zielspieler in der Welt.

- `[path]` – Optionaler Ausgabepfad (wie oben).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- Zugriff: Kreativmodus

- **`/ah time <hour>`** – Legt die Uhrzeit für den Spielplan fest (die Dorfbewohner-Routinen verwenden diese Uhrzeit).

- `<hour>` – Stunde 0 bis 23 (Beispiel: `14` für 14:00 Uhr).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.time`
- Zugriff: Kreativ

- **`/ah time dawn`** — Stelle die Uhr auf 6:00 Uhr morgens.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- Zugriff: Kreativ

- **`/ah plots finishassembly`** — Stelle alle noch im Bau befindlichen Gebäude in deiner Stadt sofort fertig.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- Zugriff: Kreativ

- **`/ah plots remove <plotId>`** — Entferne ein Grundstück aus deiner Stadt anhand der ID.

- `<plotId>` — Grundstücks-ID von `plots list`.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- Zugriff: Kreativ

## Debug-Befehle

Zum Testen und Reparieren von Welten. Nicht Teil des normalen Spielablaufs.

- **`/ah replace-charter [townName]`** — Platziere den Stadtplanblock wieder an seinem gespeicherten Ort, falls er beschädigt war.

- `[townName]` — Vollständiger Stadtname mit Leerzeichen. Für deine Stadt weglassen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- Zugriff: Abenteuer

- **`/ah towns`** — Alle Städte dieser Welt auflisten.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.towns`

- Zugriff: Kreativ

- **`/ah poi list [town]`** — Sehenswürdigkeiten einer Stadt auflisten.

- `[town]` — Stadt-ID, `me` oder für deine Stadt weglassen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.poi.list`

- Zugriff: Kreativ

- **`/ah poi dump`** — Alle Sehenswürdigkeiten im Weltregister auflisten.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- Zugriff: Kreativ

- **`/ah plots list`** — Grundstücksinstanzen in deiner Stadt auflisten.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.plots.list`
- Zugriff: Kreativ

- **`/ah needs inspect`** — Dorfbewohner mit Bedarfsanzeige in der Nähe auflisten.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- Zugriff: Kreativ

- **`/ah needs set <target> <which> <value>`** — Hunger-, Energie- oder Spaßanzeige für Dorfbewohner festlegen.

- `<target>` — Dorfbewohner-Name, `Elder` oder Entitäts-ID.

- `<which>` — `hunger`, `energy` oder `fun`.

- `<value>` — 0 bis 100 (100 = voll).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.needs.set`

- Zugriff: Kreativ

- **`/ah tax breakdown`** — Steuerzeilen für die Stadtkasse anzeigen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`
- Zugriff: Kreativ

- **`/ah tax now`** — Morgensteuereinzug sofort durchführen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.tax.now`

- Zugriff: Kreativ

- **`/ah quest grant [questId]`** — Eine Quest in deiner Stadt als aktiv markieren.

- `[questId]` — Quest-ID. Standardwert: `q_build_inn`, falls nicht angegeben.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.quest.grant`

- Zugriff: Kreativ

- **`/ah quest complete [questId]`** — Eine Quest in deiner Stadt als abgeschlossen markieren.

- `[questId]` — Quest-ID. Standardwert: `q_build_inn`, falls nicht angegeben.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- Zugriff: Kreativ

- **`/ah quest clear [questId]`** — Eine Quest aus der Liste der aktiven Quests deiner Stadt entfernen.

- `[questId]` — Quest-ID. Standardwert: `q_build_inn`, falls nicht angegeben.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- Zugriff: Kreativmodus

- **`/ah quest status`** — Zeigt aktive und abgeschlossene Quests für deine Stadt an.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.quest.status`

- Zugriff: Kreativmodus

- **`/ah reputation set <villager> <value>`** — Legt deinen Ruf bei einem Dorfbewohner fest.

- `<villager>` — Dorfbewohner-Entitäts-ID oder Rollen-ID in deiner Stadt.

- `<value>` — Ruf (0 bis 100).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- Zugriff: Kreativmodus

- **`/ah reputation reward list [roleId]`** — Listet Belohnungen für Ruf-Meilensteine auf.

- `[roleId]` — Optionaler Rollen-ID-Filter (Beispiel: `Aetherhaven_Merchant`).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`
- Zugriff: Kreativ

- **`/ah reputation reward grant <villager> <rewardId>`** — Gewährt sofort eine Rufbelohnung.

- `<villager>` — Dorfbewohner-ID oder Rollen-ID in deiner Stadt.

- `<rewardId>` — Belohnungs-ID (Beispiel: `rep_merchant_50`).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- Zugriff: Kreativ

- **`/ah villager list`** — Listet die Dorfbewohner-IDs in deiner Stadt auf.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.villager.list`

- Zugriff: Kreativ

- **`/ah villager locate <villager> [--tp]`** — Zeigt den Standort eines Dorfbewohners an (optionale Teleportation für Operatoren).

- `<villager>` — Dorfbewohner-ID oder Rollen-ID in deiner Stadt.

- `[teleport]` oder `--tp` — `true` oder `--tp` zum Teleportieren (nur für Operatoren).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- Zugriff: Kreativmodus

- **`/ah villager reset`** — Alle Dorfbewohner in deiner Nähe neu erscheinen lassen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- Zugriff: Kreativmodus

- **`/ah villager fixinn`** — Probleme mit der Besucherliste des Gasthauses in deiner Stadt beheben.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- Zugriff: Kreativmodus

- **`/ah gift resetLimits`** — Geschenklimits für alle Spieler und Dorfbewohner in der Welt zurücksetzen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- Zugriff: Kreativmodus

- **`/ah gift fillHistory <roleId>`** — Vorschau der Geschenkhistorie für Testzwecke füllen.

- `<roleId>` — Dorfbewohner-Rollen-ID (Beispiel: `Aetherhaven_Merchant`).

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- Zugriff: Kreativ

- **`/ah debug-autonomy toggle`** — Autonomie-Debug für den betrachteten Dorfbewohner aktivieren/deaktivieren.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- Zugriff: Kreativ

- **`/ah debug-autonomy show`** — Zeigt an, ob der Autonomie-Debug für den betrachteten Dorfbewohner aktiviert ist.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- Zugriff: Kreativ

- **`/ah debug-autonomy clear`** — Autonomie-Debug für den betrachteten Dorfbewohner deaktivieren.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- Zugriff: Kreativ

- **`/ah debug-lootchest fill`** — Bonus-Beutewürfe für die betrachtete Truhe erzwingen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`
- Zugriff: Kreativ

- **`/ah dialogue <treeId> [entryNode]`** — Dialogbaum anhand der ID zum Testen öffnen.

- `<treeId>` — Dialogbaum-ID (Beispiel: `aetherhaven_merchant`).

- `[entryNode]` — Startknoten. Standard: `root`.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.dialogue`

- Zugriff: Kreativ

- **`/ah floatinggift spawn`** — Einen schwebenden Geschenkballon an deiner Position erzeugen.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- Zugriff: Kreativ

- **`/ah path navviz`** — Debug-Zeilen für die Dorfbewohner-Pfadnavigation ein-/ausschalten. Erfordert die Pfadwerkzeug-Berechtigung im Spiel.

- Berechtigung: `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- Zugriff: Kreativ
