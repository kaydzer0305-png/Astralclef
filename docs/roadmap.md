# Roadmap & Known Limitations

Astralclef does **not** currently complete its stated win condition. This page
documents what is broken, why, and what to do about it.

Findings below come from static code review. They have **not** been reproduced
in a running game, so treat the line numbers as starting points rather than
proven reproductions — and please open an issue if you confirm one either way.

---

## High

These block the full progression chain.

### 1. FTB Quests integration always returns `false`

`quests/FtbQuestsHelper.java:48` loads
`net.minecraft.resources.ResourceLocation` by name. That is the **Mojmap** class
name, in a project that compiles against **Yarn** mappings.

```
build.gradle:29   mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
```

`Class.forName` therefore throws, the `catch (Throwable)` at `:79` swallows it,
and `isQuestComplete` returns `false` unconditionally.

This kills every quest gate at once: the three planet dimension gates,
`CH2_ANDESITE_CASING`, `CH4_DESH`, `DRAGON_KILL`, and `WIN_ASTRAL_SINGULARITY`.

> **Do not "fix" this by switching to `net.minecraft.util.Identifier`.** That is
> the Yarn name, which is equally unloadable at runtime: a Fabric production
> environment is in **intermediary** mappings, where both classes are
> `net.minecraft.class_2960`.
>
> The correct fix is to stop reflecting on Minecraft classes altogether —
> enumerate the FTB quests and compare `quest.getID().toString()` against the
> known id. That is mapping-independent.

### 2. The Chapter 6 win gate never waits

`tasks/phases/singularity/SingularityQuestSubtask.java:34-45`

```java
if (!dragon) {
    LOGGER.info("Ch6 gate: dragon kill {} not yet complete — waiting", ...);
    break;            // exits the `if`, not the `switch`
}
...
}                       // end try
step = Step.CLAIM_REWARDS; break;   // :45 — always runs
```

The `break` leaves the `if` block, and execution falls through to line 45, which
always advances. `/astralclef singularity` reports the win without ever checking
the win quest.

### 3. Planet dimension gates pass unconditionally

`LunarSurfaceSubtask.java:77-87`, and the same shape in
`MarsSurfaceSubtask.java:73-83` and `MercurySurfaceSubtask.java:54-64`:

```java
if (ctx == null || !ctx.isValid() || ...) return true;
if (!FtbQuestsHelper.isQuestsPresent(server)) return true;
if (player == null) return true;
...
} catch (Throwable t) { return true; }
```

Every failure mode — no context, FTB absent, no player, **any exception** — means
"dimension reached". Combined with issue 1 the gate is either vacuous or a
deadlock, but never correct. A failed check should not be read as success.

### 4. The Ch0.5 furnace is un-craftable

`tasks/phases/ch01/Ch05UnlockSubtask.java:110-121` gathers
`minecraft:cobblestone`, then line 114 tries to place a `minecraft:furnace`
**item**, which was never gathered. `BlockPlacementHelper.java:57` compounds it
with a replace-with-itself no-op:

```java
if (!InventoryHelper.hasItem(player, blockId.replace("create:", "create:"), 1)) {
```

`BotActions.placeBlock` only scans the hotbar for an exact item match, and the
direct-set fallback then also fails. The furnace is never placed, so
`BotSmelting.findFurnace` returns `null` and all smelting stalls.

Either craft the furnace from the gathered cobblestone (there is already a
`BotCrafting` path for this) or gather the item directly.

### 5. Andesite casing requires manual input

`tasks/phases/ch01/AlloyCasingSubtask.java:159-178` gates Ch0.5–1 on stripping
a log and right-clicking the alloy — an interaction the bot never automates.
Combined with issue 4 the step either hangs or loops logging a manual
instruction.

### 6. `GatherTask` has no failure state

`tasks/gather/GatherTask.java:92-101` logs on timeout and returns `null`, leaving
`isFinished()` false forever. Several call sites target craft-only items with no
block mapping, so they are unobtainable *and* un-timeouttable:

| Target | Called from |
|---|---|
| `create:andesite_alloy` | `AlloyCasingSubtask.java:170` |
| `ad_astra:oxygen_tank` | all three planet prep subtasks |
| `ad_astra:tier_2/3/4_rocket` | all three planet prep subtasks |
| `minecraft:crafting_table` | `Ch05UnlockSubtask.java:84` |

A `NEEDED_IMPOSSIBLE` terminal state would let parents advance or abort cleanly.

### 7. Rocket launch is a stub

`LaunchPrepSubtask.java:108`, `MarsPrepSubtask.java:100`, and
`MercuryPrepSubtask.java:67` all log a "launch committed" line and advance,
without a rocket entity or a dimension change. The phase proceeds to the surface
stage while the player is still in the Overworld.

This one is a **feature gap, not a bug** — it needs Ad Astra on the compile
classpath, which the project deliberately avoids.

### 8. Failed Create jobs lock out permanently

`tasks/create/CreateRecipeExecutor.java:68-81` returns the existing job for a
given `kind + recipeId`. `clearFinished()` (`:234`) exists to evict finished and
failed jobs but has **no call sites**. Once a job fails — e.g. the machine was
not placed yet — re-submitting the same kind+recipe returns the dead job forever.

### 9. Bronze smithing has no path

`tasks/create/CreateRecipeJob.java:203-208` hard-fails when the located machine
is a smithing table, correctly noting it has no block entity to drive. Since
`CreateMachineType.fromKind(BRONZE_SMITH)` always targets a smithing table, the
job always fails. `BotCrafting.craftFirstMatch` already handles shaped recipes
and could handle smithing the same way.

---

## Medium

These degrade behaviour or silently produce wrong results.

| Area | Issue |
|---|---|
| `world/BlockPlacementHelper.java:57` | `replace("create:", "create:")` no-op — the "common item fallback" was never written. |
| `bot/BotActions.java:139-166` | The support-face loop returns unconditionally inside its own body, so only the **first** valid direction is ever tried. |
| `tasks/create/world/CreateMachineIO.java:365` | Inserting into a non-`Inventory` block entity returns `ItemStack.EMPTY`, which the caller reads as "fully inserted" — items vanish when all three transfer tiers decline. |
| `tasks/create/CreateRecipeExecutor.java:246` | `acceptOrProgress` ends with a branchless `return true`, so every `tryExecute` call reports success. |
| `tasks/create/CreateRecipeKinds.java:115` | `compoundBlast` dispatches `Kind.COMPOUND_SMELT`, which targets a plain furnace and can never match `minecraft:blasting`. |
| `tasks/phases/moon/MoonDungeonSubtask.java:90` | The dungeon step counts as **cleared** when no player is online. Same in the Mars and Mercury equivalents. |
| `world/StructureLocator.java:137` | `tagForStructure` is a `return null` stub, making the `TagKey` branch dead. The 1.19+ `Registry.STRUCTURE` probes do not exist in 1.18.2. |
| `recipes/Ch01RecipeBindings.java:246` | The `#create:alloy_nuggets` tag is faked with a hardcoded `zinc_nugget` stand-in; `ALLOY_NUGGETS_TAG` is otherwise unused. |
| `recipes/Ch01RecipeBindings.java:396` | For the mixer mixture bind the output check is unconditionally `true`, so it can resolve to **any** `create:mixing` recipe in registry order. |
| `world/AdAstraRoutes.java:58` | `ensureCatalogued()` is a no-op; `planRoute()` is an instance method on a class with a private constructor, so it is unreachable. |
| `AstralclefMod.java:24` | No datapack-reload listener, so binds are never re-resolved on `/reload`. |
| `quests/AstralQuests.java:94` | `CHAPTER_6_ALL` contains 16 duplicate entries and is never referenced. `CHAPTER_4_ALT` and `CHAPTER_5` are 40 hex chars where real FTB uids are 16 — they look like pasted hashes. |
| — | Dungeon structure ids are guesses: `ad_astra:moon_ruins` and friends do not exist, so overworld structures (`minecraft:village`, `minecraft:stronghold`, …) stand in. |
| — | Server-thread cube scans run every few ticks with no chunk guard — `BlockLocator.findNearest` at radius 32 is ~275k `getBlockState` calls. |
| `combat/GreatBeastPhase.java:157` | No survivability handling: no death/respawn recovery, and attack reach (4.0) and rate (2.5/s) both exceed vanilla. |

---

## Not implemented

Known feature gaps, listed so they are not mistaken for bugs.

- Multi-component rocket assembly and machine-blueprint building.
- Ad Astra rocket launch and entity riding (needs Ad Astra on the compile classpath).
- Boss kiting beyond the Great Beast.
- Smithing-table bronze (see issue 9 — fixable via `BotCrafting`).
- Trains, ComputerCraft, and Astral Signals.
- Real fluid networks — kinetic checks read `getSpeed()` locally and never model
  stress propagation.

---

## Verifying these yourself

1. `/astralclef recipes` — an `<unresolved>` bind means the pack no longer
   exposes that recipe and every dependent job is dead.
2. `/astralclef status` — reports the active phase and in-flight Create jobs.
3. `/astralclef tick` — advance the machine one tick by hand to inspect a step.
4. The loggers are namespaced (`astralclef/...`) — `astralclef/create-job`,
   `astralclef/ftb`, `astralclef/moon/surface`, and so on.

## Contributing

Issues that come with a reproduction — the pack version, the phase, the relevant
log lines, and what `/astralclef status` showed — are far more useful than
speculation. See [CONTRIBUTING.md](../CONTRIBUTING.md) for the build and test
setup.
