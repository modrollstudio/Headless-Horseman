# Reference

## Spawning

At midnight in the Overworld, near a random player under the open sky who isn't in creative or spectator. By the
server's real date: at every midnight on 31 October (only the first with `every_halloween_midnight` off), a 10% chance
at each midnight during the rest of October, never otherwise unless `all_year` is on. One at a time. He rides off at sunrise if still alive; if summoned by day, he stays.

## Stats

| | Health | AC | Attack | Saves | Damage |
|---|---|---|---|---|---|
| Headless Horseman | 150 | 15 | +5 | +3 | `2d4`, plus `1d4` fire while he has his head |
| Gallows Steed | 80 | 13 | +5 | +2 | `1d6` |

Both crit only on a natural 20. His sword skips armor reduction: the attack roll against AC already counts the
armor. His hazards and his steed's kicks are armor reduced. Sword reach is 4 blocks; anyone within 2.5 blocks or behind
him for 3 seconds triggers a sweep (6 second cooldown). Charges every 8 seconds, attacks every 2, head throw 12 seconds
after he takes the head back.

| Hazard | Save | Damage | Hazard id |
|---|---|---|---|
| Charge | DEX DC 13 | `2d6+1`, half on a success; knocked down for 2.5 s on a failure | `critfall_horseman:charge` |
| Line of fire | DEX DC 14 | `2d8+1` fire, half on a success | `critfall_horseman:head_throw` |
| Lantern breath (worn head) | DEX DC 13 | `3d6` fire, half on a success; credited to the player | `critfall_horseman:lantern_breath` |
| The Last Hangman's Rope | STR DC 13 | none; Bound for 3 s on a failure | `critfall_horseman:hangmans_rope` |

## Rewards

When he dies, one set drops at his body, open to anyone: everything in `loot_table/rewards/horseman.json` (his head),
one pick from `loot_table/rewards/horseman_pick.json` by its weights (1 each), and with `second_reward_chance` (0.25) a
second pick, rolled again until it differs from the first. A dropped head is lit.

| Item | Chance | Power | Charges |
|---|---|---|---|
| The Horseman's Head | always | Worn: H switches its light, B breathes a 5 block, 53 degree cone of fire | 1 a day |
| Rusted Executioner's Sword | a third of first picks | | |
| The Last Hangman's Rope | a third of first picks | Aimed up to 16 blocks; everything within 1.5 blocks saves or is Bound | 3 a day |
| Horseman's Treats | a third of first picks | A d6: darkness 6 s and his laugh, a teleport up to 8 blocks, Speed II 20 s, Wraithwalk 10 s, night vision 3 min, `2d4+2` healing | 3 a day |

Charges come back at the next in game dawn. Players other than the user are caught only where PvP is on. Bound stops
walking, flying and jumping; Wraithwalk means neither pushing nor being pushed. The lit head is matched by
`assets/critfall_horseman/dynamiclights/item/horseman_head.json` (LambDynamicLights' format) and is in
`#critfall_depths:carried_light`. The keys are in the controls under Headless Horseman.

Rusted Executioner's Sword: iron tier, 9 attack damage, 0.8 attacks per second, Critfall dice `2d6` plus modifier,
crits on 19–20.

## Config

`config/critfall_horseman/critfall_horseman.json`, dates as `MM-DD`. Keys: `enabled` (master switch),
`natural_spawning`, `halloween_night`, `season_start`, `season_end`, `all_year`, `season_chance`, `sunrise_exit`,
`fire_damage`, `charge`, `head_throw`, `sweep`, `head_passing`, `boss_bar`, `lines`, `hoofbeats`, `drops`, `effects`,
`every_halloween_midnight`, `lantern_light`, `lantern_breath`, `hangmans_rope`, `treats`.
On a server the server's file decides.

`config/critfall_horseman/critfall_horseman-client.json` holds `fog`, `camera_shake` and `ambient_particles` (the
smoke, embers and fire trails around him), which only change your own screen. The server's `effects` covers the
particle bursts every player sees.

Both files can be edited in game from NeoForge's mod list or Mod Menu on Fabric; Save applies them at once. On a
server the screen shows only the client file's settings.

## Data packs

- Critfall profiles: `data/critfall_horseman/critfall/entity_profile/{horseman,gallows_steed}.json` and
  `critfall/item_profile/executioners_sword.json`.
- `data/critfall_horseman/horseman_stats/horseman.json`: health, blade fire dice, and DC and damage of the charge and
  the line of fire, and `second_reward_chance`. `gallows_steed.json`: the steed's health.
- `data/critfall_horseman/item_stats/{horseman_head,hangmans_rope,horsemans_treats}.json`: the rewards' DCs, dice,
  ranges, durations and charges.
- `data/critfall_horseman/loot_table/rewards/horseman.json`: what he always drops, and
  `rewards/horseman_pick.json`: the weighted pick of the sword, the rope and the treats.
- His sword deals `critfall_horseman:blade`, which is in `#minecraft:bypasses_armor`.
- Both entities are in `#initiative:never_joins`. His lines are in the lang file.

## For other mods

`HorsemanSpawner.spawn(level, position, target)` spawns him mounted with his arrival. The hazard ids above reach
Critfall's `HazardRollEvent`.

## Development

- `tools/test-all.sh`: unit tests, Spotless, the release jar check and the GameTests on both loaders, with and
  without Checks. Summary in `build/test-summary.txt`.
- Assets are generated by `tools/horseman_art.py`, `icon_art.py`, `banner_art.py` and `sounds.py` (Python 3, Pillow,
  numpy, ffmpeg, rsvg-convert). Edit the scripts, not their output. `render_models.py` writes preview renders to
  `design/renders`.
