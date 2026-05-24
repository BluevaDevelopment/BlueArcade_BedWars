# BlueArcade - Bed Wars

This resource is a **BlueArcade 3 module** and requires the core plugin to run.
Get BlueArcade 3 here: https://store.blueva.net/resources/resource/1-blue-arcade/

## Description
Protect your bed, destroy enemy beds, and be the last team standing. Collect resources from spawners, buy items and upgrades from shop NPCs, and eliminate all opposing teams.

## Game type notes
This is a **Minigame**: it is designed for standalone arenas, but it can also be used inside party rotations. Supports both team mode and solo mode (team size of 1).

## What you get with BlueArcade 3 + this module
- Party system (lobbies, queues, and shared party flow).
- Store-ready menu integration and vote menus.
- Victory effects and end-game celebrations.
- Scoreboards, timers, and game lifecycle management.
- Player stats tracking and placeholders.
- XP system, leaderboards, and achievements.
- Arena management tools and setup commands.

## Features
- Team size and team count configuration (supports solo mode with team size 1). Up to 8 teams supported.
- Per-team bed placement and destruction tracking.
- Resource spawners (iron, gold, diamond, emerald) with configurable intervals and per-type holograms with live countdown.
- Shop NPC villagers (item shop and team upgrades) that spawn on game start and despawn on game end.
- Final kill system: players without a bed are permanently eliminated.
- Block protection: only player-placed blocks can be broken (beds are an exception for enemies).
- Respawn on death with configurable delay (only if team bed is intact).
- Teams with no players assigned are automatically excluded from scoreboards and win conditions.
- Timed generator upgrade events (Hypixel-style): configurable timed boosts for diamond and emerald spawners.
- Dynamic scoreboards supporting 2 to 8 active teams, with per-team bed status and "YOU" marker.

## Arena setup
### Common steps
Use these steps to register the arena and attach the module:

- `/baa create [id] <standalone|party>` — Create a new arena in standalone or party mode.
- `/baa arena [id] setname [name]` — Give the arena a friendly display name.
- `/baa arena [id] setlobby` — Set the lobby spawn for the arena.
- `/baa arena [id] minplayers [amount]` — Define the minimum players required to start.
- `/baa arena [id] maxplayers [amount]` — Define the maximum players allowed.
- `/baa game [arena_id] add [minigame]` — Attach this minigame module to the arena.
- `/baa stick` — Get the setup tool to select regions.
- `/baa game [arena_id] [minigame] bounds set` — Save the game bounds for this arena.
- ~~`/baa game [arena_id] [minigame] spawn add`~~ — Not used in Bed Wars.
  Use **`/baa game [arena_id] bed_wars team spawn <team_id>`** to configure team spawns.
- ~~`/baa game [arena_id] [minigame] time [minutes]`~~ — Not used in Bed Wars.
  The match ends when only one team remains alive.

### Module-specific steps
Finish the setup with the commands below:

#### 1. Configure teams
- `/baa game [arena_id] bed_wars team count <value>` — Set the number of teams (minimum 2).
- `/baa game [arena_id] bed_wars team size <value>` — Set the players per team (minimum 1 for solo, 2+ for teams).
- `/baa game [arena_id] bed_wars team spawn <team_id>` — Set the spawn point for a team at your current location.

#### 2. Configure beds
- `/baa game [arena_id] bed_wars bed set <team_id>` — Set the bed for a team. Look at the bed block and run this command.

#### 3. Configure resource spawners
- `/baa game [arena_id] bed_wars spawner add <iron|gold|diamond|emerald> <true|false>` — Add a spawner. Look at the block where the spawner should be and run this command. The second argument controls whether a hologram is shown above the spawner (`true` or `false`). Spawn intervals are configured globally in `settings.yml` under `spawners.defaults.*` — they are not set per-spawner.
- `/baa game [arena_id] bed_wars spawner list` — Show all configured spawners (type and hologram flag).
- `/baa game [arena_id] bed_wars spawner remove` — Remove a spawner. Look at the spawner block and run this command. Breaking a spawner block during gameplay also unregisters it.

#### 4. Configure shop NPCs
- `/baa game [arena_id] bed_wars npc add <store|upgrade>` — Add a shop NPC at your current location. NPCs are villagers that spawn when the game starts and despawn when it ends. `store` opens the item shop menu, `upgrade` opens the team upgrades menu.
- `/baa game [arena_id] bed_wars npc list` — Show all configured NPCs.
- `/baa game [arena_id] bed_wars npc remove <id>` — Remove an NPC by its ID.

#### 5. Configure region
- `/baa game [arena_id] bed_wars region set` — Select and save the regeneration region.
- `/baa game [arena_id] bed_wars region clear` — Clear the regeneration region if needed.

> **Important:** `team_id` values are numeric-only (`1`, `2`, `3`, ...) and must be between `1` and your configured `team count`.

## Technical details
- **Minigame ID:** `bed_wars`
- **Module Type:** `MINIGAME`

## Links & Support
- Website: https://www.blueva.net
- Documentation: https://docs.blueva.net/books/blue-arcade
- Support: https://discord.com/invite/CRFJ32NdcK
