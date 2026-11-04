# Changelog

## 0.2.0 — 2026-10-20

Requires Critfall 0.2.19 or newer.

- New rewards, one set per kill at his body, shared by the party: his head always, one of the sword, The Last
  Hangman's Rope or Horseman's Treats, equally likely (weights in the loot table `rewards/horseman_pick`), and a 25%
  chance (`second_reward_chance`) of one more of the two that didn't drop.
- The Horseman's Head is a lantern when worn. H switches its light (in `#critfall_depths:carried_light`, and lit for
  dynamic light mods; no light block is ever placed). Once a day B breathes a cone of fire: a DEX save through
  Critfall for half, hazard id `critfall_horseman:lantern_breath`, credited to the player.
- The Last Hangman's Rope: three charges a day. Ropes burst from the ground where you aim; a STR save through
  Critfall (`critfall_horseman:hangmans_rope`), or Bound in place for 3 seconds.
- Horseman's Treats: three a day. Critfall rolls a d6 for each: darkness and his laugh for 6 seconds, a short teleport,
  Speed II for 20 seconds, Wraithwalk (pass through creatures) for 10 seconds, night vision for 3 minutes, or `2d4+2`
  healing. Every treat shows what it did, the last one too.
- Charges come back at the next in game dawn. The rewards' numbers are in `item_stats/`.
- On Halloween he comes at every in game midnight, not only the first (`every_halloween_midnight`).
- New config keys: `every_halloween_midnight`, `lantern_light`, `lantern_breath`, `hangmans_rope` and `treats`, all on
  by default.

## 0.1.2 — 2026-11-03

Requires Critfall 0.2.18 or newer, where armor reduces damage again and gives half the AC it did.

- Retuned so he deals as much damage per attack in full iron as he did on Critfall 0.2.17. In full netherite his sword
  does about two thirds of that, his charge and line of fire about half.
- His sword skips armor reduction, as his attack roll against your AC already counts your armor. His attack bonus goes
  from +9 to +5, so he still hits full iron 65% of the time.
- Charge damage goes from `1d4+2` to `2d6+1`, line of fire from `2d4+1` to `2d8+1`. Armor reduces both.
- His sword kills get their own death message.

## 0.1.1

- An in-game config screen, opened from NeoForge's mod list or Mod Menu on Fabric. On a server it shows only your
  own screen settings. Fog, camera shake and the smoke and embers around him moved to `critfall_horseman-client.json`,
  so each player can turn them off on a server.

## 0.1.0

First release, for Minecraft 1.21.1 on NeoForge and Fabric. Requires Critfall 0.2.17+ and GeckoLib 4.9.3+. Checks
0.1.3+ and Initiative 0.2.0+ are optional.

- The Headless Horseman and the Gallows Steed, two entities with their own health and Critfall profiles.
- Midnight spawning in the Overworld near a player under the open sky: once on 31 October, a 10% chance on other
  October nights, optionally all year. He rides off at sunrise.
- Fight: fire damage on sword hits, a charge with a Dexterity save to avoid being knocked down, and a thrown head
  that leaves a line of fire with a Dexterity save for half. Players can pick up, carry and throw the head while he
  rides after it. He fights on foot once his steed dies, and sweeps when crowded.
- All attacks, damage and saves are rolled by Critfall, with hazard ids `critfall_horseman:charge` and
  `critfall_horseman:head_throw`. Health, DCs and damage dice are in data files.
- Boss bars, lines shown as titles or on the action bar, particles, fog, camera shake and sounds.
- Drops: the Rusted Executioner's Sword, with a Critfall item profile, and The Horseman's Head, which can be worn.
- Spawn eggs and `/summon`.
- A config key for every feature.
- He and his steed are tagged `#initiative:never_joins`.
