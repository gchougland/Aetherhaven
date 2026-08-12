---
name: "Equipo de creadores de la trama"
description: "Convertir una construcción en una definición de edificio de ciudad personalizada"
author: Hexvane
---

# Personal de creación de parcelas

El personal de creación de parcelas registra los **edificios personalizados** que construyas en el mundo: superficie, bloques especiales, exportación de prefabricados, costes y (opcionalmente) una parcela activa en tu ciudad. La mayoría de los jugadores deberían usar **Decoración** o **Variante**, no los tipos de espacio de trabajo completos que se describen a continuación.

Abre tu **diario de la ciudad → Guía → Mecánicas → Personal de creación de parcelas** (esta página) mientras aprendes a usarlo. Durante el juego: equipa el personal, pulsa **F** para empezar o abrir el panel del paso actual, **haz clic con el botón derecho** en los bloques del mundo, **Q** / **E** para el paso anterior/siguiente, **R** para cancelar.

## Rutas recomendadas

| Objetivo | Tipo de edificio | Notas |

|------|----------------|-------|

| Construcción cosmética, sin trabajos | **Decoración** | Estante de registros de la ciudad opcional; sin lógica de producción ni de aldeanos. |

| Aspecto alternativo para un edificio existente | **Variante** | Selecciona qué edificio **principal** se considera (casa, granero, posada, etc.). Los subpasos se corresponden con ese tipo principal. |

| Nuevo espacio de trabajo / parcela de producción (modding) | **Trabajo** | Para añadir nuevos tipos de espacios de trabajo; requiere un bloque de gestión, un almacén de producción y un punto de interés (POI) de superficie de trabajo. |

## Tipos de edificios (selector)

**Decoración** — Parques, objetos y construcciones que **no** deben funcionar como viviendas, tiendas o lugares de trabajo. Espacios mínimos requeridos.

**Variante** — Un prefab que **se considera** otro ID de edificio ya presente en el mod (por ejemplo, una casa personalizada que se considera `plot_house`). Elige el tipo principal del menú desplegable; los espacios importantes coinciden con ese edificio principal.

**Vivienda** — Parcela residencial: estantería de registros municipales + POI de descanso.

**Trabajo** — **Uso del desarrollador/creador de contenido.** Define una nueva parcela de estilo **espacio de trabajo**: estantería de registros municipales, almacén de producción y un POI de superficie de trabajo. Úsalo al añadir un nuevo tipo de edificio de producción o trabajo, no para variantes cosméticas normales.

**Servicio** — Ocio o entretenimiento (parque, servicio tipo altar): estante + punto de interés (POI) de ocio/asiento.

**Tienda** — Puesto o mostrador: estante + POI de trabajo con etiqueta de tienda.

**Posada** — Diseño completo de posada: estante, superficie de trabajo, camas, zona de comedor, puntos de aparición del posadero y visitantes (y espacio opcional para el maestro del gremio).

**Ayuntamiento** — Centro cívico: estante, bloque de tesorería, POI de escritorio de planificación.

**Salón del gremio** — Gremio de aventureros: estante, superficie de trabajo, puntos de aparición de aventureros.

### Superposición y confusión

- **Variante** vs **Casa / Trabajo / …** — Variante significa «tiene un aspecto diferente, se comporta como X». Elige **Variante** + tipo principal, no **Casa**, si estás modificando la apariencia de un edificio de juego existente.
- **Trabajo** vs. **Tienda**: **Tienda** se usa para puestos de comerciantes. **Trabajo** se usa para granjas, molinos, forjas y otros centros de producción.

- **Comodidades** vs. **Decoración**: **Decoración** casi no tiene elementos de jugabilidad. **Comodidades** establece etiquetas de diversión/comodidades y puntos de interés para los horarios de los aldeanos.

- **Posada**, **Ayuntamiento** y **Salón del Gremio** son plantillas completas; usa **Variante** solo si quieres que coincida con una de esas plantillas a propósito.

## Flujo (breve)

1. Marca dos esquinas opuestas y una esquina **exterior** para el letrero de la parcela.

2. Elige el **tipo de edificio** (y la **variante** si corresponde).

3. Coloca los **lugares importantes** (los bloques se proporcionan de uno en uno en cada subpaso).

4. Introduce el **nombre y la ID** (el nombre del archivo prefab sigue a la ID).

5. Edita las **etiquetas** si es necesario.
6. Abre la **configuración de construcción** (F): coste de oro del tesoro, días de autoconstrucción, opción de prefabricación para espacios vacíos y secciones de ensamblaje.

7. **Exporta la prefabricación** con F en el paso de guardar forma (utiliza la configuración del paso 6).

8. Configura los **materiales de construcción** (cofre virtual; los objetos se te devolverán al continuar).

9. Revisa y guarda.

## Permisos

Por defecto, la configuración puede permitir que todos utilicen el creador de parcelas; los servidores pueden requerir el permiso `aetherhaven.plot.creator`.
