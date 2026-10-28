# Changelog

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
