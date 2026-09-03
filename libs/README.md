# Optional compile-only dependencies

These JARs are **not** shipped with Aetherhaven. They exist so local builds can type-check optional mod integrations.

## LootrHytale

1. Download **Lootr 0.2.11** (or matching version) from [LootrHytale releases](https://github.com/LootrMinecraft/LootrHytale/releases).
2. Place the file here as `Lootr-0.2.11.jar`.
3. For `runServer`, also copy the same JAR into `run/mods/` so the Lootr plugin loads at runtime.

Without `libs/Lootr-0.2.11.jar`, Gradle skips the Lootr `compileOnly` dependency and the Lootr adapter sources will not compile. Core Aetherhaven still runs without Lootr installed.

## Loot4Everyone

Loot4Everyone compatibility is reflection-only. No JAR is needed under `libs/`.
