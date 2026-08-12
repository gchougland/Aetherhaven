---
name: "Équipe de création de l'intrigue"
description: "Transformer une construction en une définition de bâtiment de ville personnalisée"
author: Hexvane
---

# Bâton de création de parcelle

Le bâton de création de parcelle enregistre les **bâtiments personnalisés** que vous avez construits dans le monde : emprise au sol, blocs spéciaux, exportation de préfabriqués, coûts et (optionnel) une parcelle active dans votre ville. La plupart des joueurs devraient utiliser **Décoration** ou **Variante**, et non les types de lieux de travail complets ci-dessous.

Ouvrez votre **journal de ville → Guide → Mécanismes → Bâton de création de parcelle** (cette page) pour vous familiariser avec le fonctionnement. En jeu : équipez le bâton, appuyez sur **F** pour commencer ou ouvrir le panneau de l’étape en cours, **clic droit** sur les blocs du monde, **Q** / **E** pour l’étape précédente/suivante, **R** pour annuler.

## Chemins recommandés

| Objectif | Type de bâtiment | Remarques |

|------|----------------|-------|

| Construction esthétique, sans emplois | **Décoration** | Étagère d’archives de ville optionnelle ; sans production ni logique villageoise. |

| Apparence alternative pour un bâtiment existant | **Variante** | Choisissez le type de bâtiment **principal** (maison, grange, auberge, etc.). Les sous-étapes correspondent à ce type principal. |

| Nouvel espace de travail/production (modification) | **Travail** | Pour ajouter de nouveaux types d'espaces de travail ; nécessite un bloc de gestion, un entrepôt de production et un point d'intérêt (POI) de surface de travail. |

## Types de bâtiments (sélecteur)

**Décoration** — Parcs, accessoires et constructions qui ne doivent **pas** servir de maisons, de boutiques ou d'espaces de travail. Emplacements requis minimaux.

**Variante** — Un préfabriqué qui **compte comme** un autre bâtiment déjà présent dans le mod (par exemple, une maison personnalisée qui compte comme `plot_house`). Choisissez le type principal dans la liste déroulante ; les emplacements importants correspondent à ce bâtiment principal.

**Maison** — Terrain résidentiel : étagère des archives municipales + point d'intérêt (POI) de couchage.

**Travail** — **Utilisation par les développeurs/auteurs de contenu.** Définit un nouvel espace de travail : étagère des archives municipales, entrepôt de production et point d'intérêt (POI) de surface de travail. À utiliser lors de l'ajout d'un nouveau type de bâtiment de production ou d'emploi, et non pour les variantes cosmétiques classiques.

**Équipement** — Loisirs (parc, autel) : étagère + point d'intérêt (placement libre/détente).

**Boutique** — Échoppe ou comptoir : étagère + point d'intérêt (travail) associé à la mention « Boutique ».

**Auberge** — Agencement complet : étagère, plan de travail, lits, coin repas, points d'apparition de l'aubergiste et des visiteurs (et emplacement optionnel pour le maître de guilde).

**Hôtel de ville** — Centre civique : étagère, bloc de trésorerie, point d'intérêt (bureau de planification).

**Salle de guilde** — Guilde des aventuriers : étagère, plan de travail, points d'apparition des aventuriers.

### Chevauchement et confusion

- **Variante** vs **Domicile / Travail / …** — Une variante est un bâtiment « apparence différente, comportement identique à X ». Choisissez **Variante** + le type principal, et non **Domicile**, si vous modifiez l'apparence d'un bâtiment existant. - **Travail** vs **Boutique** — La **Boutique** est réservée aux étals de marchands. Le **Travail** est destiné aux fermes, moulins, forges et autres lieux de production.

- **Aménagement** vs **Décoration** — La **Décoration** n'a quasiment aucun impact sur le gameplay. L'**Aménagement** définit les activités et les points d'intérêt pour les villageois.

- **Auberge**, **Mairie** et **Salle de guilde** sont des modèles complets ; utilisez **Variante** uniquement si vous souhaitez reproduire l'un de ces modèles.

## Déroulement (version abrégée)

1. Marquez deux coins opposés et un coin **extérieur** pour le panneau de parcelle.

2. Choisissez le **type de bâtiment** (et sa **variante** le cas échéant).

3. Placez les **emplacements importants** (les blocs sont fournis un par un à chaque sous-étape).

4. Saisissez le **nom et l'identifiant** (le nom du fichier préfabriqué suit l'identifiant).

5. Modifiez les **étiquettes** si nécessaire. 6. Ouvrez les **paramètres de construction** (F) : coût en or du trésor, nombre de jours d'autoconstruction, option de préfabrication pour les espaces vides et sections d'assemblage.

7. **Exportez le préfabriqué** avec F lors de l'étape d'enregistrement de la forme (utilise les paramètres de l'étape 6).

8. Définissez les **matériaux de construction** (coffre virtuel ; les objets vous seront restitués lorsque vous continuerez).

9. Vérifiez et enregistrez.

## Autorisations

Par défaut, la configuration peut autoriser tout le monde à utiliser l'éditeur de parcelles ; les serveurs peuvent exiger l'autorisation `aetherhaven.plot.creator`.
