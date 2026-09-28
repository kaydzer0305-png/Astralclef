<div align="center">

# Astralclef

**A phased automation bot for the Create: Astral modpack on Fabric 1.18.2.**

Play the modpack's questline hands-free: gather, craft, smelt, press, mix,
compact, and fight — from first logs all the way to the Astral Singularity.

[![Build](https://github.com/kaydzer0305-png/Astralclef/actions/workflows/build.yml/badge.svg)](https://github.com/kaydzer0305-png/Astralclef/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Minecraft 1.18.2](https://img.shields.io/badge/Minecraft-1.18.2-green.svg)
![Fabric](https://img.shields.io/badge/Fabric-1.18.2-orange.svg)

</div>

---

> ### ⚠️ Status: work in progress
>
> The full progression chain **does not complete yet**. Several quest-gate and
> automation paths are known-broken, so `/astralclef auto` will stall before
> the Singularity. This is documented and prioritized rather than hidden —
> see **[Known limitations](docs/roadmap.md)** for the current blocker list and
> how to work around each one.

## What it does

Astralclef drives your player through the questline one **phase** at a time.
Each phase breaks into subtasks, and each subtask issues **jobs** — locate a
machine, insert real ingredients, wait for processing, extract the product.

```
/astralclef auto
        │
        ├── Ch0.5–1  Getting Started ── gather → craft → smelt → alloy → grout
        ├── Moon     Launch Prep → Lunar Surface → Moon Dungeon
        ├── Mars     Launch Prep → Mars Surface → Mars Dungeon
        ├── Mercury  Launch Prep → Mercury Surface → Mercury Vault
        └── Chapter 6  Great Beast → Craft Singularity → Quest Completion
```

## Key properties

| Property | What it means |
|---|---|
| **Honest economy** | Items come from real mining, real crafting against the live `RecipeManager`, and real placed furnaces. No conjured stacks in the `bot` package. |
| **No Baritone required** | Movement, mining, placing, and combat run server-side, so it works on dedicated servers. Baritone is used opportunistically when present. |
| **Pack-aware** | Recipes resolve from the live datapack/KubeJS recipe manager by type + I/O, so pack-local ids are discovered rather than hardcoded. |
| **Soft dependencies** | FTB Quests, Baritone, Ad Astra, and Flywheel are all reached by reflection. Absent mods degrade instead of crashing. |

## Requirements

| Dependency | Version | Notes |
|---|---|---|
| Minecraft | 1.18.2 | Fabric Loader `>=0.14.0`, Java 17+ |
| Fabric API | `0.77.0+1.18.2` | Required. |
| **Create (Fabric)** | `0.5.1-f-build.1415+mc1.18.2` | **Hard** dependency — `"create": "*"` in `fabric.mod.json`. Resolved from DevOS snapshots (`mvn.devos.one`). |
| Flywheel | `0.6.10-39` | Ships JiJ inside Create. **Do not** pin `0.6.4`; the Astral-verified version is `0.6.10-39`. |
| Create: Astral | — | The target modpack. |

## Installation

Astralclef is a client-agnostic, server-side Fabric mod.

1. Build it, or grab the `.jar` from `build/libs/`.
2. Drop `astralclef-<version>.jar` into the `mods/` folder of a **Fabric 1.18.2**
   instance that already has **Create Fabric** and **Create: Astral** installed.
3. Launch. Confirm with `/astralclef status`.

Then set your base down and run:

```
/astralclef context     # bind the machine-search origin to your position
/astralclef auto        # run the full progression chain
```

> `/astralclef context` is optional — the Create context auto-binds from the first
> online player when a job needs it. Setting it manually just makes the search
> radius predictable.

## Commands

| Command | Does |
|---|---|
| `/astralclef auto` | Full progression, Ch0.5–1 through Singularity |
| `/astralclef ch01` | Getting Started phase only |
| `/astralclef moon` | Moon phase |
| `/astralclef mars` | Mars phase |
| `/astralclef mercury` | Mercury phase |
| `/astralclef singularity` | Chapter 6 — the win condition |
| `/astralclef beast` | Great Beast combat on its own |
| `/astralclef gather <item> [count]` | Mine toward an item; `count` is 1–640 |
| `/astralclef status` | Active task, Create job summary, FTB/Baritone presence |
| `/astralclef context` | Rebind the Create machine-search origin |
| `/astralclef recipes` | Dump Ch01 binds against resolved pack recipe ids |
| `/astralclef tick` | Force one TaskRunner + executor tick by hand |
| `/astralclef cancel` | Stop the active task |

Full reference with arguments and examples: **[docs/commands.md](docs/commands.md)**.

## Building

```bat
set JAVA_HOME=C:\path\to\jdk-17
.\gradlew.bat build
```

Requires **JDK 17** (`build.gradle` compiles with `options.release = 17`).
The Gradle wrapper (`gradle-7.3.3`) is checked in, so no separate Gradle
install is needed. The output jar lands in `build/libs/`.

Create resolves from DevOS snapshots; first build downloads Minecraft assets and
takes a few minutes.

## Documentation

| Document | What's in it |
|---|---|
| **[docs/architecture.md](docs/architecture.md)** | Package map, the Task → TaskRunner → CreateRecipeJob flow, and how machines are located and driven. |
| **[docs/commands.md](docs/commands.md)** | Every `/astralclef` subcommand with arguments and examples. |
| **[docs/create-recipes.md](docs/create-recipes.md)** | Ch01 recipe bindings, item ids, and the typed block-entity I/O layer. |
| **[docs/roadmap.md](docs/roadmap.md)** | Known limitations, prioritized blockers, and unimplemented features. |

## Project layout

```
src/main/java/com/ezquest/astralclef/
├── AstralclefMod.java      entrypoint — wires TaskRunner + executor to server tick
├── bot/                    embodiment: break, place, eat, move, craft, smelt
├── combat/                 Great Beast kiting and swings
├── command/                /astralclef brigadier registration
├── inventory/              InventoryHelper — count/has, #tag resolution
├── movement/               BaritoneHelper — optional, reflection-only
├── quests/                 FTB Quests + quest-id tables (reflection-only)
├── recipes/                Ch01 recipe bindings and the pack catalogue
├── task/                   Task / TaskRunner — the tick-driven state machine
├── tasks/
│   ├── create/             CreateRecipeJob + executor
│   │   └── world/          machine locate + typed block-entity I/O
│   ├── gather/             GatherTask + BlockLocator
│   └── phases/             ch01 / moon / mars / mercury / singularity subtasks
└── world/                  structures, block placement, rocket + route helpers
```

## License

[MIT](LICENSE) © kaydzer0305-png
