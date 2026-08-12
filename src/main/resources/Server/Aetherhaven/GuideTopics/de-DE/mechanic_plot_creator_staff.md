---
name: "Stab für Grundstückserstellung"
description: "Wandeln Sie ein Gebäude in eine benutzerdefinierte Stadtgebäudedefinition um"
author: Hexvane
---

# Stab für Grundstückserstellung

Der Stab für Grundstückserstellung erfasst **benutzerdefinierte Gebäude**, die du in der Welt errichtet hast: Grundfläche, Spezialblöcke, Export von Fertigbauteilen, Kosten und (optional) ein aktives Grundstück in deiner Stadt. Die meisten Spieler sollten **Dekoration** oder **Variante** verwenden, nicht die vollständigen Arbeitsplatztypen weiter unten.

Öffne dein **Stadtjournal → Anleitung → Mechaniken → Stab für Grundstückserstellung** (diese Seite), um den Ablauf zu lernen. Im Spiel: Rüste den Stab aus, drücke **F**, um zu starten oder das aktuelle Schrittfenster zu öffnen, **klicke mit der rechten Maustaste** auf Blöcke in der Welt, **Q** / **E** für den vorherigen/nächsten Schritt, **R** zum Abbrechen.

## Empfohlene Wege

| Ziel | Gebäudetyp | Notizen |

|------|----------------|-------|

| Kosmetischer Bau, keine Arbeit | **Dekoration** | Optionales Stadtarchiv-Regal; keine Produktion oder Dorfbewohner-Logik. |

| Alternatives Aussehen für ein bestehendes Gebäude | **Variante** | Wähle das **Hauptgebäude** (Haus, Scheune, Gasthaus usw.). Die Unterschritte richten sich nach diesem Haupttyp. |

| Neuer Arbeitsplatz/Produktionsplatz (Modding) | **Arbeit** | Zum Hinzufügen neuer Arbeitsplatztypen; benötigt einen Verwaltungsblock, ein Produktionslager und einen Arbeitsflächen-POI. |

## Gebäudetypen (Auswahl)

**Dekoration** – Parks, Requisiten und Gebäude, die **nicht** als Wohnhäuser, Geschäfte oder Arbeitsplätze dienen sollen. Mindestens benötigte Plätze.

**Variante** – Ein Fertigbau, der als eine andere Gebäude-ID im Mod **zählt** (z. B. ein benutzerdefiniertes Haus, das als `plot_house` zählt). Wähle den Haupttyp aus dem Dropdown-Menü; wichtige Plätze entsprechen diesem Hauptgebäude.

**Haus** – Wohngrundstück: Stadtarchivregal + Schlaf-POI.

**Arbeit** – **Für Entwickler/Inhaltsautoren.** Definiert ein neues **Arbeitsplatz**-Grundstück: Stadtarchivregal, Produktionslager und einen Arbeitsflächen-POI. Verwenden Sie diese Option, wenn Sie einen neuen Produktions- oder Arbeitsplatzgebäudetyp hinzufügen, nicht für normale kosmetische Varianten.

**Einrichtung** – Freizeit oder Erholung (Park, Altar-ähnliche Einrichtung): Regal + Freizeit-/Sitz-POI.

**Laden** – Stand oder Ladentheke: Regal + mit Laden-Tag versehener Arbeitsplatz-POI.

**Gasthaus** – Vollständige Gasthaus-Grundrisse: Regal, Arbeitsfläche, Betten, Essbereich, Spawnpunkte für Wirt und Gäste (und optionaler Spawnpunkt für Gildenmeister).

**Rathaus** – Bürgerzentrum: Regal, Schatzkammer, Planungsbüro-POI.

**Gildenhaus** – Abenteurergilde: Regal, Arbeitsfläche, Spawnpunkte für Abenteurer.

### Überschneidungen und Verwirrung

- **Variante** vs. **Zuhause/Arbeit/…** – Eine Variante bedeutet „sieht anders aus, verhält sich wie X“. Wählen Sie **Variante** + Haupttyp, nicht **Zuhause**, wenn Sie ein bestehendes Spielgebäude umgestalten.

- **Arbeit** vs. **Laden** – **Laden** ist für Händlerstände. **Arbeit** ist für Bauernhöfe, Mühlen, Schmieden und andere Produktionsstätten.

- **Ausstattung** vs. **Dekoration** – **Dekoration** hat kaum spielmechanische Auswirkungen. **Ausstattung** legt Freizeit-/Ausstattungsmerkmale und POIs für die Dorfbewohner fest.

- **Gasthaus**, **Rathaus** und **Gildenhalle** sind vollständige Vorlagen; verwenden Sie **Varianten** nur, wenn Sie eine dieser Vorlagen gezielt nachbilden möchten.

## Ablauf (kurz)

1. Markieren Sie zwei gegenüberliegende Ecken und eine **äußere** Grundstücksecke.

2. Wählen Sie den **Gebäudetyp** (und ggf. die **Variante).

3. Platzieren Sie **wichtige Stellen** (Blöcke werden in jedem Teilschritt einzeln vergeben).

4. Geben Sie **Name und ID** ein (der Dateiname des vorgefertigten Gebäudes folgt der ID).

5. Bearbeiten Sie die **Tags** bei Bedarf.

6. Öffne die **Gebäudeeinstellungen** (F): Goldkosten der Schatzkammer, Tage für den Selbstbau, Option für vorgefertigte Gebäude im leeren Raum und Montageabschnitte.

7. **Exportiere das vorgefertigte Gebäude** mit F im Schritt „Form speichern“ (verwendet die Einstellungen aus Schritt 6).

8. Lege die **Baumaterialien** fest (virtuelle Truhe; die Gegenstände werden dir beim Fortfahren wieder gutgeschrieben).

9. Überprüfe und speichere.

## Berechtigungen

Standardmäßig kann die Konfiguration jedem die Nutzung des Grundstückseditors erlauben; Server können stattdessen die Berechtigung `aetherhaven.plot.creator` vorschreiben.
