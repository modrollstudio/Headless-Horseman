# Critfall: Headless Horseman

A Halloween boss for Minecraft 1.21.1 that uses [Critfall](https://github.com/modrollstudio/Critfall) for all of its
dice. A headless rider on a black horse comes at midnight in October, with a rusted sword and his burning head.

Before he was an executioner's ghost, he was a father. He was the town's headsman, and in the evenings he made sweets for
his children. Then the town took his head on his own block, and now he rides at midnight with it burning in his hand.

## The fight

- His sword hits add fire damage while he has his head. Armor makes him miss more but doesn't soften a hit.
- He charges on his steed: fail a Dexterity save and you are knocked down.
- He throws his head in a line of fire: a Dexterity save halves the damage.
- The head lands as an item that players can carry and throw to each other. Without it he has no fire and rides
  straight for whoever holds it.
- Kill his steed and he fights on foot.

## Rewards

He drops one set of rewards at his body for the party to share: his head, one of his sword, The Last Hangman's Rope or
Horseman's Treats, equally likely, and a 25% chance of one more of the two that didn't drop.

- The Horseman's Head is a lantern you wear. H lights it or puts it out, for dynamic light mods; once a day B breathes a
  cone of fire, a Dexterity save for half.
- The Last Hangman's Rope: three times a day, ropes burst from the ground where you aim. A Strength save, or held in
  place for 3 seconds.
- Horseman's Treats: three a day. Critfall rolls a d6 for each: a fright, a short teleport, speed, passing through
  creatures, night vision or healing.

Charges come back at dawn.

## Spawning

At every midnight on Halloween and by chance on other October midnights, near a player under the open sky. He rides off at
sunrise. Spawn eggs and `/summon critfall_horseman:horseman` also work.

Everything is configurable in `config/critfall_horseman/critfall_horseman.json`, or in game from the mod list.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.150+ or Fabric Loader 0.17.3+ with Fabric API, on server and clients
- [Critfall](https://github.com/modrollstudio/Critfall) 0.2.19+
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.9.3+
- Optional: [Checks](https://github.com/modrollstudio/Checks) 0.1.3+, so saves use your character's Dexterity
- Optional: [Initiative](https://github.com/modrollstudio/Initiative) 0.2.0+; he stays out of its encounters
- Optional: a dynamic light mod such as LambDynamicLights, so the lit head lights the world
- Optional on Fabric: [Mod Menu](https://modrinth.com/mod/modmenu), to open the config screen from its mod list

Stats, config keys, data pack files and the build tools are in [docs/reference.md](docs/reference.md).

## Building

`./gradlew build` puts both jars in `neoforge/build/libs` and `fabric/build/libs`.

## License

MIT. See [LICENSE](LICENSE).
