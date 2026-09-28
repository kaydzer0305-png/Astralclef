# Contributing

## Build

Requires **JDK 17** — `build.gradle` compiles with `options.release = 17`.

```bat
set JAVA_HOME=C:\path\to\jdk-17
.\gradlew.bat build
```

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew build
```

The Gradle wrapper (`gradle-7.3.3`) is checked in, so no separate Gradle install
is needed. The first build downloads Minecraft, Create, and the mappings and
takes several minutes; later builds are incremental. CI runs the same command on
every push and pull request.

Create resolves from DevOS snapshots (`mvn.devos.one`) — if that repository is
unreachable, `build.gradle:37` documents a Modrinth/CurseMaven fallback.

## Before opening a pull request

1. `./gradlew build` passes.
2. If you touched the Create I/O layer, confirm `/astralclef recipes` still
   resolves every bind.
3. Add or update an entry in [docs/roadmap.md](docs/roadmap.md) if you fix or
   introduce a known limitation.

## Project layout

See the tree in the [README](README.md#project-layout). In short:

| Package | Holds |
|---|---|
| `task/` | `Task` / `TaskRunner` — the tick-driven state machine. No Minecraft coupling. |
| `tasks/phases/` | Per-planet phase roots and their subtasks. |
| `tasks/create/` | `CreateRecipeJob` + the executor that owns in-flight jobs. |
| `tasks/create/world/` | Machine locate and typed block-entity I/O. |
| `tasks/gather/` | `GatherTask` + `BlockLocator`. |
| `bot/` | Server-side embodiment — break, place, move, craft, smelt. |
| `recipes/` | Ch01 recipe bindings and the pack catalogue. |
| `quests/` | FTB Quests helpers and quest-id tables. |

## Code conventions

The codebase uses **tabs** for indentation and **Allman braces**. Match the
surrounding file. `.editorconfig` encodes this.

## Things worth knowing before you touch the code

**Reflection is load-bearing.** Create's block entities expose items and fluids
through behaviours rather than a plain `Inventory`, so `CreateBlockEntityIO`
(~1,400 lines) reaches into Create internals by name. Create renames these
between builds — the `DepotBehaviour` handling is the current example of a
mitigation. If a bind or machine stops working after a Create upgrade, suspect
this layer first and check `/astralclef recipes`.

**Never reflect on Minecraft classes by name.** A Fabric production environment
is in **intermediary** mappings, so both the Yarn name
(`net.minecraft.util.Identifier`) and the Mojmap name
(`net.minecraft.resources.ResourceLocation`) are unloadable at runtime. This is
the cause of the FTB Quests bug tracked in [docs/roadmap.md](docs/roadmap.md#1-ftb-quests-integration-always-returns-false).
Compare `toString()` values instead.

**Keep the economy honest.** The `bot` package sources everything from real
inventory — mining, live-`RecipeManager` crafting, placed furnaces. Please don't
add a shortcut that conjures items; it would invalidate the interesting part of
this project.

## Documentation

| Document | Covers |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Package map, task/job flow, machine I/O tiers |
| [docs/commands.md](docs/commands.md) | Every `/astralclef` subcommand |
| [docs/create-recipes.md](docs/create-recipes.md) | Recipe binds and block-entity I/O |
| [docs/roadmap.md](docs/roadmap.md) | Known limitations, prioritized |
