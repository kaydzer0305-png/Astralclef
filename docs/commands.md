# Command Reference

All commands live under `/astralclef`. They are registered by
`command/AstralCommands.java` via Brigadier's
`CommandRegistrationCallback`.

> **Permission level:** the command tree is built with
> `CommandManager.literal(...)` and no `.requires(...)` override, so Brigadier
> applies its default requirement of **permission level 2 (op)**. Run Astralclef
> from an operator account.

---

## Progression

### `/astralclef auto`

Runs the entire progression chain as one task.

```
Ch0.5–1  →  Moon  →  Mars  →  Mercury  →  Singularity
```

Implemented by `tasks/phases/FullProgressionTask.java`, which simply drives each
per-planet phase task in sequence and advances when the current one reports
`isFinished()`.

- Re-running an already-active task is a no-op (`TaskRunner.runUserTask` checks
  `isEqual` first), so this is safe to spam.
- `/astralclef status` reports the active phase as `Auto/<PHASE>`.

### `/astralclef ch01`

Getting Started only — early Create and Astral basics. Runs four subtasks in
order:

1. **Ch0.5 unlock** — crafting table → furnace → mine Fe/Sn/Cu → smelt
2. **Alloy / Casing** — bronze → andesite compound → alloy → andesite casing
3. **Mixer loop** — kinetics, millstone, press, mixer; press-dust → compact
4. **Grout gate** — produce `tconstruct:grout`, which unlocks Chapter 2

> The furnace and casing steps are [currently blocked](roadmap.md#high) — the
> bot gathers cobblestone but then tries to place a furnace *item*, and casing
> requires a manual strip-and-right-click.

### `/astralclef moon` / `mars` / `mercury`

One planet at a time. Each is a three-stage phase:

| Phase | Stages |
|---|---|
| Moon | `LAUNCH_PREP` → `LUNAR_SURFACE` → `MOON_DUNGEON` |
| Mars | `MARS_PREP` → `MARS_SURFACE` → `MARS_DUNGEON` |
| Mercury | `MERCURY_PREP` → `MERCURY_SURFACE` → `MERCURY_VAULT` |

Each planet's *prep* stage builds launch infrastructure (oxygen gear, rocket,
fuel, launch pad) and gates on an `ad_astra:launch_pad` being nearby.

> Rocket **launch** is a stub — the phase logs and advances without changing
> dimension. See [roadmap](roadmap.md#high).

### `/astralclef singularity`

Chapter 6, the win condition. Runs `GREAT_BEAST` → `CRAFT_SINGULARITY` →
`QUEST_COMPLETION`.

### `/astralclef beast`

The Great Beast fight on its own (`combat/GreatBeastPhase.java`). Paths toward
the nearest hostile, strafes in alternating flanks, and swings server-side via
`ServerPlayerEntity#attack`. Loot gating waits for the area to go clear.

---

## Gathering

### `/astralclef gather <item> [count]`

Mine toward a specific item.

| Argument | Type | Range | Default |
|---|---|---|---|
| `item` | string | — | required |
| `count` | integer | 1–640 | `1` |

```mcfunction
/astralclef gather minecraft:iron_ore
/astralclef gather ad_astra:desh_ingot 16
```

Each call **replaces** the active task — it does not queue.

**Important:** `item` must be something minable. `GatherTask` maps item ids to
block ids to mine and has no crafting path, so craft-only items (`create:andesite_alloy`,
`ad_astra:oxygen_tank`, the rockets) will hang until the task is cancelled.
Use the progression commands for those.

The mine loop throttles to one world scan per 5 ticks, steps to within 2.2
blocks so drops land on the player, then breaks via
`interactionManager.tryBreakBlock`. If Baritone is installed its `MineProcess`
is preferred instead.

---

## Setup & diagnostics

### `/astralclef context`

Binds the Create machine-search origin to your current world and block position.

```mcfunction
/astralclef context
```

Sets the search origin to where you are standing with the default radius (16
blocks), then re-resolves the Ch01 recipe bindings against the live recipe
manager.

- **Requires a player.** From the console it returns
  `Astralclef context requires a player`.
- Usually optional — `CreateRecipeExecutor` auto-binds from the first online
  player when a job needs a world context. Run it manually when you want the
  search to reliably cover your base.

### `/astralclef status`

Four lines, sender-only:

```
Astralclef task: Auto/MARS
Create: 2 job(s) — PRESS_DUST=PROCESS
FTB Quests: present
Baritone: absent (stub movement)
```

The task line is `idle` when nothing is running. `Create` summarises in-flight
`CreateRecipeJob`s by kind and current step. The FTB/Baritone lines are
availability probes only.

### `/astralclef recipes`

Dumps every Ch01 bind against the id it actually resolved to in the live
`RecipeManager`, one line each.

```
bind/press_dust -> create:pressing/andesite_dust   [create:pressing]
bind/compound_smelt -> <unresolved>
```

Use this first when a Create step stalls — an `<unresolved>` bind means the pack
no longer exposes a matching recipe, and the job will never progress.

### `/astralclef tick`

Forces one `TaskRunner.tick()` plus one `CreateRecipeExecutor.tick(server)`.

The server tick hook already runs both every tick, so this is only useful when
you want to step the machine by hand — e.g. from a script, or to confirm a task
advances without waiting for real ticks.

### `/astralclef cancel`

Stops the active task and its subtask, and clears the Create context.
