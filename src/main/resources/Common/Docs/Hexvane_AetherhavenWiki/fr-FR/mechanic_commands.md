---
name: Commandes
description: "Commandes de chat pour les villes et les outils de serveur"
author: Hexvane
---

# Commandes

`/aetherhaven` et `/ah` sont identiques. La plupart des joueurs n'ont besoin que de la section **Pour les joueurs** ci-dessous.

**Accès** indique le mode de jeu par défaut associé à la commande. **Permission** correspond aux autorisations accordées manuellement par les administrateurs du serveur si l'accès seul est insuffisant. Les chevrons sont obligatoires ; les crochets sont facultatifs.

## Pour les joueurs

### Membres de la ville

- **`/ah town invite <player> [townName]`** — Inviter un joueur en ligne dans votre ville.

- `<player>` — Nom d'utilisateur du joueur (doit être en ligne).

- `[townName]` — Nom complet de la ville (espaces compris). Omettre ce champ pour votre propre ville.

- Permission : `hexvane.aetherhaven.command.aetherhaven.town.invite`

- Accès : Aventure

- **`/ah town accept [townName]`** — Rejoindre une ville qui vous a invité.

- `[townName]` — Nom complet de la ville si vous avez plusieurs invitations en attente. Omettre ce champ si vous n'en avez qu'une. - Autorisation : `hexvane.aetherhaven.command.aetherhaven.town.accept`

- Accès : Aventure

- **`/ah town decline [townName]`** — Refuser une invitation à rejoindre votre ville.

- `[townName]` — Nom complet de votre ville si vous avez plusieurs invitations en attente. Omettez ce champ si vous n'en avez qu'une.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.town.decline`

- Accès : Aventure

- **`/ah town kick <player> [townName]`** — Retirer un membre de votre ville.

- `<player>` — Nom d'utilisateur du membre (doit être en ligne).

- `[townName]` — Nom complet de votre ville (espaces compris). Omettez ce champ pour votre propre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.town.kick`

- Accès : Aventure

- **`/ah town role <player> <role> [townName]`** — Attribuer un rôle à un membre : CONSTRUCTION, QUÊTE ou LES DEUX.

- `<player>` — Nom d'utilisateur du membre (doit être en ligne). - `<role>` — `BUILD`, `QUEST` ou `BOTH`.

- `[townName]` — Nom complet de la ville (espaces compris). Omettez cette option pour votre propre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.town.role`

- Accès : Aventure

- **`/ah town leave`** — Quitter une ville dont vous êtes membre (mais pas le fondateur).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.town.leave`

- Accès : Aventure

### Cadeaux flottants

- **`/ah floatinggift next`** — Consulter l’heure d’apparition de votre prochain ballon cadeau flottant.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- Accès : Aventure

### Outil de création de chemins

- **`/ah path revert <id>`** — Annuler la création d’un chemin en utilisant l’identifiant fourni dans le chat lors de sa création. L’accès à l’outil de création de chemins est également requis en jeu. - `<id>` — Identifiant de retour du chemin (UUID) affiché dans le chat lors de son placement.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.path.revert`

- Accès : Aventure

## Pour les hôtes du monde

Ces options sont destinées au mode Créatif ou aux personnes qui gèrent le serveur. Elles ne sont pas nécessaires pour jouer en mode normal.

- **`/ah difficulty`** — Ouvrir le menu de difficulté du monde pour consulter les coûts de construction.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.difficulty`

- Accès : Créatif

- **`/ah reload`** — Recharger les fichiers de configuration et de données du mod depuis le disque.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.reload`

- Accès : Créatif

- **`/ah starterkit`** — S'attribuer les outils de départ (personnel de placement, charte, bureau de planification, personnel de construction).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.starterkit`

- Accès : Créatif

- **`/ah exportskin [path]`** — Enregistrer l'apparence de son avatar comme fichier de modèle. - `[path]` — Chemin de sortie optionnel. Par défaut : données du plugin `avatar_exports` avec un nom de fichier horodaté.

- Permission : `hexvane.aetherhaven.command.aetherhaven.exportskin`

- Accès : Créatif

- **`/ah exportskin <player> [path]`** — Enregistrer l'apparence de l'avatar d'un autre joueur (nécessite la permission `.other`).

- `<player>` — Joueur cible dans le monde.

- `[path]` — Chemin de sortie optionnel (identique à ci-dessus).

- Permission : `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- Accès : Créatif

- **`/ah time <hour>`** — Définir l'horloge du jeu (les routines des villageois utilisent cette horloge).

- `<hour>` — Heure de 0 à 23 (exemple : `14` pour 14 h).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.time`

- Accès : Créatif

- **`/ah time dawn`** — Régler l'horloge à 6h00 du matin.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- Accès : Créatif

- **`/ah plots finishassembly`** — Terminer instantanément la construction de tous les bâtiments en cours dans votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- Accès : Créatif

- **`/ah plots remove <plotId>`** — Supprimer une parcelle de votre ville par son identifiant.

- `<plotId>` — Identifiant de la parcelle de `plots list`.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- Accès : Créatif

## Commandes de débogage

Pour tester et corriger les mondes. Ne fait pas partie du jeu normal.

- **`/ah replace-charter [townName]`** — Replacer le bloc de la charte à son emplacement sauvegardé dans votre ville s'il était cassé.

- `[townName]` — Nom complet de la ville avec espaces. Omettre pour votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- Accès : Aventure

- **`/ah towns`** — Lister toutes les villes de ce monde.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.towns`

- Accès : Créatif

- **`/ah poi list [town]`** — Lister les points d'intérêt d'une ville.

- `[town]` — Identifiant de la ville, `me` ou omettre pour votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.poi.list`

- Accès : Créatif

- **`/ah poi dump`** — Lister tous les points d'intérêt du monde.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- Accès : Créatif

- **`/ah plots list`** — Lister les parcelles de terrain de votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.plots.list`

- Accès : Créatif

- **`/ah needs inspect`** — Lister les villageois à proximité de leurs jauges de besoins. - Autorisation : `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- Accès : Créatif

- **`/ah needs set <target> <which> <value>`** — Définir la jauge de faim, d'énergie ou de divertissement d'un villageois.

- `<target>` — Identifiant du villageois, `Elder` ou ID de l'entité.

- `<which>` — `hunger`, `energy` ou `fun`.

- `<value>` — De 0 à 100 (100 : plein).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.needs.set`

- Accès : Créatif

- **`/ah tax breakdown`** — Afficher les lignes de recettes fiscales de votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- Accès : Créatif

- **`/ah tax now`** — Lancer immédiatement la collecte des impôts du matin. - Autorisation : `hexvane.aetherhaven.command.aetherhaven.tax.now`

- Accès : Créatif

- **`/ah quest grant [questId]`** — Marquer une quête comme active dans votre ville.

- `[questId]` — Identifiant de la quête. Par défaut : `q_build_inn` si omis.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.quest.grant`

- Accès : Créatif

- **`/ah quest complete [questId]`** — Marquer une quête comme terminée dans votre ville.

- `[questId]` — Identifiant de la quête. Par défaut : `q_build_inn` si omis.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- Accès : Créatif

- **`/ah quest clear [questId]`** — Supprimer une quête de la liste des quêtes actives de votre ville.

- `[questId]` — Identifiant de la quête. Par défaut : `q_build_inn` si omis.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- Accès : Créatif

- **`/ah quest status`** — Afficher les quêtes actives et terminées de votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.quest.status`

- Accès : Créatif

- **`/ah reputation set <villager> <value>`** — Définir votre réputation auprès d'un villageois.

- `<villager>` — Identifiant de l'entité ou du rôle du villageois dans votre ville.

- `<value>` — Niveau de réputation (de 0 à 100).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- Accès : Créatif

- **`/ah reputation reward list [roleId]`** — Lister les récompenses liées aux paliers de réputation.

- `[roleId]` — Filtre optionnel par identifiant de rôle (exemple : `Aetherhaven_Merchant`).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- Accès : Créatif

- **`/ah reputation reward grant <villager> <rewardId>`** — Accorder une récompense de réputation immédiatement. - `<villager>` — Identifiant de l'entité villageoise ou de son rôle dans votre ville.

- `<rewardId>` — Identifiant de la récompense (exemple : `rep_merchant_50`).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- Accès : Créatif

- **`/ah villager list`** — Liste les identifiants des entités villageoises dans votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.villager.list`

- Accès : Créatif

- **`/ah villager locate <villager> [--tp]`** — Affiche la position d'un villageois (téléportation optionnelle pour les opérateurs).

- `<villager>` — Identifiant de l'entité villageoise ou de son rôle dans votre ville.

- `[teleport]` ou `--tp` — `true` ou `--tp` pour se téléporter (opérateurs uniquement). - Autorisation : `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- Accès : Créatif

- **`/ah villager reset`** — Faire réapparaître tous les villageois de votre ville à proximité.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- Accès : Créatif

- **`/ah villager fixinn`** — Résoudre les problèmes de fréquentation de l'auberge de votre ville.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- Accès : Créatif

- **`/ah gift resetLimits`** — Réinitialiser les limites de cadeaux pour tous les joueurs et villageois du monde.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- Accès : Créatif

- **`/ah gift fillHistory <roleId>`** — Remplir les lignes d'aperçu de l'historique des cadeaux pour les tests.

- `<roleId>` — Identifiant du rôle du villageois (exemple : `Aetherhaven_Merchant`).

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- Accès : Créatif

- **`/ah debug-autonomy toggle`** — Active/désactive le débogage d'autonomie pour le villageois que vous regardez.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- Accès : Créatif

- **`/ah debug-autonomy show`** — Indique si le débogage d'autonomie est activé pour le villageois que vous regardez.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- Accès : Créatif

- **`/ah debug-autonomy clear`** — Désactive le débogage d'autonomie pour le villageois que vous regardez.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- Accès : Créatif

- **`/ah debug-lootchest fill`** — Force l'obtention de butin bonus sur le coffre que vous regardez.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- Accès : Créatif

- **`/ah dialogue <treeId> [entryNode]`** — Ouvre un arbre de dialogue par son identifiant pour effectuer des tests. - `<treeId>` — Identifiant de l'arbre de dialogue (exemple : `aetherhaven_merchant`).

- `[entryNode]` — Nœud de départ. Par défaut : `root`.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.dialogue`

- Accès : Créatif

- **`/ah floatinggift spawn`** — Faire apparaître un ballon cadeau flottant à votre position.

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- Accès : Créatif

- **`/ah path navviz`** — Activer/désactiver les lignes de débogage pour la navigation des villageois. Nécessite l'autorisation « Outil de navigation ».

- Autorisation : `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- Accès : Créatif
