---
name: Comandos
description: "Comandos de chat para ciudades y herramientas de servidor"
author: Hexvane
---

# Comandos

`/aetherhaven` y `/ah` son iguales. La mayoría de los jugadores solo necesitan la sección **Para jugadores** que aparece a continuación.

**Acceso** indica qué modo de juego recibe el comando por defecto. **Permiso** es el permiso que otorgan manualmente los administradores del servidor si el acceso no es suficiente. Los corchetes angulares son obligatorios; los corchetes cuadrados son opcionales.

## Para jugadores

### Miembros del pueblo

- **`/ah town invite <player> [townName]`** — Invita a alguien conectado a tu pueblo.

- `<player>` — Nombre de usuario del jugador (debe estar conectado).

- `[townName]` — Nombre completo del pueblo con espacios. Omítelo si es tu pueblo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.town.invite`

- Acceso: Aventura

- **`/ah town accept [townName]`** — Únete a un pueblo que te haya invitado.

- `[townName]` — Nombre completo del pueblo si tienes más de una invitación pendiente. Omítelo si solo tienes una.
- Permiso: `hexvane.aetherhaven.command.aetherhaven.town.accept`

- Acceso: Aventura

- **`/ah town decline [townName]`** — Rechazar una invitación a tu pueblo.

- `[townName]` — Nombre completo del pueblo si tienes más de una invitación pendiente. Omitir si solo tienes una.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.town.decline`

- Acceso: Aventura

- **`/ah town kick <player> [townName]`** — Eliminar a un miembro de tu pueblo.

- `<player>` — Nombre de usuario del miembro (debe estar en línea).

- `[townName]` — Nombre completo del pueblo con espacios. Omitir si se trata de tu propio pueblo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.town.kick`

- Acceso: Aventura

- **`/ah town role <player> <role> [townName]`** — Asignar un rol al miembro: CONSTRUIR, MISIÓN o AMBOS.

- `<player>` — Nombre de usuario del miembro (debe estar en línea).
- `<role>` — `BUILD`, `QUEST` o `BOTH`.

- `[townName]` — Nombre completo del pueblo con espacios. Omítelo si es tu propio pueblo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.town.role`

- Acceso: Aventura

- **`/ah town leave`** — Abandona un pueblo al que perteneces (no como fundador).

- Permiso: `hexvane.aetherhaven.command.aetherhaven.town.leave`

- Acceso: Aventura

### Regalos flotantes

- **`/ah floatinggift next`** — Consulta cuándo aparecerá tu próximo globo de regalo flotante.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`

- Acceso: Aventura

### Herramienta de caminos

- **`/ah path revert <id>`** — Deshaz un camino cementado usando el ID del chat cuando lo colocaste. También necesitas acceso a la herramienta de caminos en el juego.
- `<id>` — ID de reversión de ruta (UUID) que se muestra en el chat al colocar la ruta.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.path.revert`

- Acceso: Aventura

## Para anfitriones de mundos

Esto es para el modo creativo o para quienes administran el servidor. No es necesario para jugar en una ciudad normal.

- **`/ah difficulty`** — Abre el menú de dificultad del mundo para ver los costos de construcción.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.difficulty`

- Acceso: Creativo

- **`/ah reload`** — Recarga los archivos de configuración y datos del mod desde el disco.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.reload`

- Acceso: Creativo

- **`/ah starterkit`** — Obtén las herramientas iniciales (personal de colocación, estatuto, mesa de planificación, personal de construcción).

- Permiso: `hexvane.aetherhaven.command.aetherhaven.starterkit`

- Acceso: Creativo

- **`/ah exportskin [path]`** — Guarda la apariencia de tu avatar como un archivo de modelo.
- `[path]` — Ruta de salida opcional. Por defecto, se utilizan los datos del plugin `avatar_exports` con un nombre de archivo con marca de tiempo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.exportskin`

- Acceso: Creativo

- **`/ah exportskin <player> [path]`** — Guarda la apariencia del avatar de otro jugador (requiere el permiso `.other`).

- `<player>` — Jugador objetivo en el mundo.

- `[path]` — Ruta de salida opcional (igual que la anterior).

- Permiso: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`

- Acceso: Creativo

- **`/ah time <hour>`** — Establece el reloj del juego (las rutinas de los aldeanos usan este reloj).

- `<hour>` — Horas de 0 a 23 (ejemplo: `14` para las 14:00).
- Permiso: `hexvane.aetherhaven.command.aetherhaven.time`

- Acceso: Creativo

- **`/ah time dawn`** — Ajusta el reloj a las 6:00 de la mañana.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.time.dawn`

- Acceso: Creativo

- **`/ah plots finishassembly`** — Finaliza instantáneamente todos los edificios que aún se estén construyendo en tu ciudad.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`

- Acceso: Creativo

- **`/ah plots remove <plotId>`** — Elimina una parcela de tu ciudad por su ID.

- `<plotId>` — ID de parcela de `plots list`.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.plots.remove`

- Acceso: Creativo

## Comandos de depuración

Para probar y corregir mundos. No forma parte del juego normal.

- **`/ah replace-charter [townName]`** — Vuelve a colocar el bloque de estatutos en su lugar guardado en tu ciudad si estaba roto.
- `[townName]` — Nombre completo del pueblo con espacios. Omítelo si corresponde.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.replace-charter`

- Acceso: Aventura

- **`/ah towns`** — Lista todos los pueblos de este mundo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.towns`

- Acceso: Creativo

- **`/ah poi list [town]`** — Lista los puntos de interés de un pueblo.

- `[town]` — ID del pueblo, `me`, o omítelo si corresponde.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.poi.list`

- Acceso: Creativo

- **`/ah poi dump`** — Lista todos los puntos de interés del registro mundial.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.poi.dump`

- Acceso: Creativo

- **`/ah plots list`** — Lista las parcelas de tu pueblo.
- Permiso: `hexvane.aetherhaven.command.aetherhaven.plots.list`

- Acceso: Creativo

- **`/ah needs inspect`** — Muestra los aldeanos con medidores de necesidades cercanos.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`

- Acceso: Creativo

- **`/ah needs set <target> <which> <value>`** — Establece un medidor de hambre, energía o diversión para los aldeanos.

- `<target>` — Identificador del aldeano, `Elder` o ID de entidad.

- `<which>` — `hunger`, `energy` o `fun`.

- `<value>` — De 0 a 100 (100 es lleno).

- Permiso: `hexvane.aetherhaven.command.aetherhaven.needs.set`

- Acceso: Creativo

- **`/ah tax breakdown`** — Muestra las líneas de impuestos para la tesorería de tu pueblo.
- Permiso: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`

- Acceso: Creativo

- **`/ah tax now`** — Ejecuta la recaudación de impuestos matutina de inmediato.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.tax.now`

- Acceso: Creativo

- **`/ah quest grant [questId]`** — Marca una misión como activa en tu pueblo.

- `[questId]` — ID de la misión. Por defecto, `q_build_inn` si se omite.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.quest.grant`

- Acceso: Creativo

- **`/ah quest complete [questId]`** — Marca una misión como completada en tu pueblo.

- `[questId]` — ID de la misión. Por defecto, `q_build_inn` si se omite.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.quest.complete`

- Acceso: Creativo

- **`/ah quest clear [questId]`** — Elimina una misión de la lista de misiones activas de tu pueblo.

- `[questId]` — ID de la misión. Valor predeterminado: `q_build_inn` cuando se omite.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.quest.clear`

- Acceso: Creativo

- **`/ah quest status`** — Muestra las misiones activas y completadas de tu pueblo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.quest.status`

- Acceso: Creativo

- **`/ah reputation set <villager> <value>`** — Establece tu reputación con un aldeano.

- `<villager>` — ID de entidad o rol del aldeano en tu pueblo.

- `<value>` — Reputación de 0 a 100.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.reputation.set`

- Acceso: Creativo

- **`/ah reputation reward list [roleId]`** — Lista las recompensas por hitos de reputación.

- `[roleId]` — Filtro opcional de ID de rol (ejemplo: `Aetherhaven_Merchant`).
- Permiso: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`

- Acceso: Creativo

- **`/ah reputation reward grant <villager> <rewardId>`** — Otorga una recompensa de reputación ahora.

- `<villager>` — ID de entidad o rol de aldeano en tu pueblo.

- `<rewardId>` — ID de recompensa (ejemplo: `rep_merchant_50`).

- Permiso: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`

- Acceso: Creativo

- **`/ah villager list`** — Lista los ID de entidad de aldeano en tu pueblo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.villager.list`

- Acceso: Creativo

- **`/ah villager locate <villager> [--tp]`** — Muestra la ubicación de un aldeano (teletransporte opcional para operadores).

- `<villager>` — ID de entidad o rol de aldeano en tu pueblo.
- `[teleport]` o `--tp` — `true` o `--tp` para teletransportarse (solo operadores).

- Permiso: `hexvane.aetherhaven.command.aetherhaven.villager.locate`

- Acceso: Creativo

- **`/ah villager reset`** — Regenera a todos los aldeanos cercanos.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.villager.reset`

- Acceso: Creativo

- **`/ah villager fixinn`** — Soluciona los problemas de la reserva de visitantes de la posada.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`

- Acceso: Creativo

- **`/ah gift resetLimits`** — Restablece los límites de regalos para todos los jugadores y aldeanos del mundo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`

- Acceso: Creativo

- **`/ah gift fillHistory <roleId>`** — Rellena las filas de vista previa del historial de regalos para realizar pruebas.
- `<roleId>` — ID del rol del aldeano (ejemplo: `Aetherhaven_Merchant`).

- Permiso: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`

- Acceso: Creativo

- **`/ah debug-autonomy toggle`** — Activa o desactiva la depuración de autonomía para el aldeano que estás viendo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`

- Acceso: Creativo

- **`/ah debug-autonomy show`** — Muestra si la depuración de autonomía está activada para el aldeano que estás viendo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`

- Acceso: Creativo

- **`/ah debug-autonomy clear`** — Desactiva la depuración de autonomía para el aldeano que estás viendo.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`

- Acceso: Creativo

- **`/ah debug-lootchest fill`** — Fuerza tiradas de botín adicionales en el cofre que estás viendo.
- Permiso: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`

- Acceso: Creativo

- **`/ah dialogue <treeId> [entryNode]`** — Abre un árbol de diálogo por ID para realizar pruebas.

- `<treeId>` — ID del árbol de diálogo (ejemplo: `aetherhaven_merchant`).

- `[entryNode]` — Nodo inicial. Predeterminado: `root`.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.dialogue`

- Acceso: Creativo

- **`/ah floatinggift spawn`** — Genera un globo de regalo flotante en tu posición.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`

- Acceso: Creativo

- **`/ah path navviz`** — Activa o desactiva las líneas de depuración para la navegación de los aldeanos. Requiere el permiso de la herramienta de rutas en el juego.

- Permiso: `hexvane.aetherhaven.command.aetherhaven.path.navviz`

- Acceso: Creativo
