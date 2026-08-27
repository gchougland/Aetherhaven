# Crossmod villager festivals and birthdays

Small add-ons you can put on a crossmod villager: Wintertide gifts, festival greetings, and a birthday. Use these with the villager file under `Server/Aetherhaven/Villagers/` and English lang files under `Server/Languages/en-US/`.

Replace `angler` with your villager’s `dialogueVillagerKind`. Replace `your_villager` with your dialogue id.

## Wintertide gifts

What the villager **gives the player** on Wintertide. Add this on the villager JSON:

```json
"wintertideGifts": [
  { "itemId": "Fish_Salmon_Item", "count": 8 },
  { "itemId": "CozyFishing_Wooden_Rod", "count": 1 },
  {
    "pickOne": ["Plant_Flower_Orchid_Blue", "Plant_Flower_Orchid_Red"],
    "count": 1,
    "repeats": 16
  }
]
```

Leave `count` out to give 1. `pickOne` rolls one item from the list. `repeats` rolls that row more than once.

If you omit `wintertideGifts`, Aetherhaven uses the built-in table for that kind when there is one.

Optional Wintertide talk (same kind name). Put it in `aetherhaven_dialogue_festival_wintertide.lang` so the keys merge:

```lang
aetherhaven.dialogue.festival.wintertide.gift.angler.love=This is the best Wintertide gift I could have hoped for.
aetherhaven.dialogue.festival.wintertide.gift.angler.like=A lovely Wintertide gift. I will keep it close.
aetherhaven.dialogue.festival.wintertide.gift.angler.neutral=A Wintertide gift is still a gift. Thank you.
aetherhaven.dialogue.festival.wintertide.gift.angler.dislike=I will take it. Next time, maybe something else.
aetherhaven.dialogue.festival.wintertide.incoming.angler=From my stores to you. Stay warm.
```

`gift.*` is their reply when the player gives them something. `incoming` is what they say when they give the player a gift.

## Festival greetings

Hub lines while a festival is running. Add lang keys; you do not need to edit Aetherhaven festival JSON.

Any file under `Server/Languages/en-US/` works, for example `your_mod_dialogue_festival_wintertide.lang`:

```lang
your_mod.dialogue.festival.wintertide.greeting.angler.0=Wintertide is the one night I put the nets away.
your_mod.dialogue.festival.wintertide.greeting.angler.1=If you do not know who to give to, ask the merchant first.
your_mod.dialogue.festival.carnival.greeting.angler.0=I will try the wheel after I check the stall.
```

Pattern: `*.dialogue.festival.<festivalId>.greeting.<dialogueVillagerKind>.<index>`

Festival ids: `new_life`, `pig_race`, `carnival`, `tree_climbing`, `market`, `hallows_eve`, `snowball`, `wintertide`.

Use numbered suffixes `0`, `1`, `2`, and so on. One line is picked per player, villager, and day. A villager’s own birthday greeting still wins over the festival line.

## Birthdays

On the villager JSON:

```json
"birthdaySeason": "Summer",
"birthdayDay": 14,
"dialogueBirthdayGreetingLangKeys": [
  "aetherhaven_dialogue_your_villager.aetherhaven.dialogue.your_villager.birthday.greeting"
]
```

Season is `Spring`, `Summer`, `Autumn`, or `Winter`. Day is 1 to 28. Gifts on their birthday give double friendship.

Lang for the greeting and gift replies:

```lang
aetherhaven.dialogue.your_villager.birthday.greeting=You remembered my birthday. That means a lot.
aetherhaven.dialogue.your_villager.gift.love.birthday=It is my birthday and this gift is wonderful.
aetherhaven.dialogue.your_villager.gift.like.birthday=A birthday gift like this. You have good taste.
aetherhaven.dialogue.your_villager.gift.neutral.birthday=A simple gift on my birthday still means a lot.
aetherhaven.dialogue.your_villager.gift.dislike.birthday=It is my birthday and you brought that. I still appreciate the thought.
```

In their dialogue tree, add nodes named `gift_love_birthday`, `gift_like_birthday`, `gift_neutral_birthday`, and `gift_dislike_birthday` (same shape as the normal gift nodes).
