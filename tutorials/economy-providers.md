# Economy providers

Another mod can make its currency Aetherhaven's. Aetherhaven then pays, earns, stores and shows gold through that mod's accounts, and its gold coin item is retired. Without such a mod, or when the server keeps the coin, nothing changes.

In Aetherhaven's server `config.json`, `EconomyProvider` accepts:

| Value | Behavior |
| --- | --- |
| `AUTO` (default) | Use the economy mod that registered a provider, if any. |
| `COINS` | Keep the gold coin item even when a mod has registered. |

Restart after changing it. Switching back to `COINS` leaves what a mod's accounts hold with the mod: treasuries start again from zero on Aetherhaven's side.

## Registering

Implement `EconomyProvider` from `com.hexvane.aetherhaven.economy.api` and register it from your plugin's `setup()`. List Aetherhaven as a hard `Dependencies` entry so it is set up first:

```java
public class MyCurrencyPlugin extends JavaPlugin {
    private final EconomyProvider provider = new MyProvider();

    @Override
    protected void setup() {
        AetherhavenEconomy.register(provider);
    }

    @Override
    protected void shutdown() {
        AetherhavenEconomy.unregister(provider);
    }
}
```

One provider per server: a second registration is a configuration error and throws. The log says `Economy provider: <id>` when yours is taken, or warns when `COINS` overrides it.

## What a provider answers

Every `long` Aetherhaven passes is in Aetherhaven gold coins, never negative: a price, a loot roll, the tithe, a refund. A mod with another unit converts.

- `account(ref, store)`, `townAccount(town)`, `shopSafe(town, player)` return a `GoldAccount`: `balance()`, `withdraw(amount)` and `deposit(amount)` in whole coins, each returning whether it happened. The mod persists its balances, Aetherhaven persists nothing for it. A treasury or a safe a town already held under the coin item is deposited into the mod's account the first time it is reached, once.
- `goldItems(source, itemId, amount)` returns the items to hand out where Aetherhaven gives gold that way, per `GoldSource`: `LOOT_CHEST` (dungeon and ruin chests), `BREAKABLE_CONTAINER` (pots and crates), `RECIPE` (salvage recipes that gave coins). `itemId` is what the server configured for that source. Return your own items (one token worth the amount), or an empty list for nothing from that source: chests and pots then give no gold, and a recipe with nothing to give is hidden.

The rest has defaults, in whole coins, that a mod overrides to use its own unit:

- `amount(long)` writes an amount for the places that hold text alone (tooltips, chat, notifications), `"5 gold"` by default. It is passed as a message parameter, so it may be a translation or coloured spans.
- `show(builder, selector, amount, fontSize)` draws an amount into an empty group of a page (a price tag, a tithe column, a dialogue choice). `fontSize` is the size of the text next to the group. Aetherhaven clears the group first, the provider only appends. The coin icon and the number by default.
- `balance(accounts...)` gives what accounts hold together, exact in the mod's unit, to draw (`show`) or to write (`message`). Their sum by default.
- `transfer(from, to, text)` moves what a player typed in the treasury page between two of the mod's accounts, at the mod's own precision. Blank text moves everything. The default parses a whole number of coins.

## Under a provider

- Prices, refunds, the tithe, sales, quest and reputation rewards go through the accounts.
- The gold coin is no longer an item. Salvage recipes that gave coins are rewritten as they load with what `goldItems(RECIPE, ...)` returns, or hidden. Villagers stop asking for the coin in gifts and thoughts. Coins a player still holds from before the switch are deposited into their account.
- The treasury page shows an amount field, read by `transfer` in the mod's unit. Empty means everything.
- The HUD, the pages and the dialogues draw amounts and balances through `show`. Tooltips, chat and notifications write them through `amount`.

Not covered: the festival seed cart barters some of its items for coin items, which an account cannot pay. Everything else in Aetherhaven that involves gold goes through the provider.

## Verification

Run `gradlew test --tests "com.hexvane.aetherhaven.economy.*"`. In game, with your mod registered: buy at a shop, open the treasury and deposit an amount in your unit, claim land, build from a plot sign, salvage a plot token, open a dungeon chest and break a pot, finish a quest that rewards coins. Then start the same world with `COINS` and check that the coin item is back everywhere.
