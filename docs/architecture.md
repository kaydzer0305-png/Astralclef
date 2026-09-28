# Architecture

Astralclef is a **tick-driven state machine**. Three layers cooperate, and each
one only knows about the layer below it:

```
AstralclefMod  ──END_SERVER_TICK──▶  TaskRunner  ──▶  Task  ──▶  CreateRecipeExecutor  ──▶  CreateRecipeJob
   (entrypoint)                       (scheduler)      (logic)         (job registry)          (one machine)
```

---

## 1. Entrypoint — `AstralclefMod.java`

`onInitialize()` does four things and nothing else:

```java
CreateRecipeKinds.init();                      // populate the Kind enum's machine hints
KubeJsAwareCatalogue.shared().refresh();       // snapshot the live recipe manager
AstralCommands.register();                     // brigadier command tree
ServerTickEvents.END_SERVER_TICK.register(server -> {
    TaskRunner.getInstance().tick();           // advance the task tree
    CreateRecipeExecutor.getInstance().tick(server);  // advance machine jobs
});
```

The ordering matters: the task tree runs first so a subtask can enqueue a job on
the same tick the executor then picks up.

There is no datapack-reload listener, so recipe binds are **not** re-resolved on
`/reload` — see [roadmap](roadmap.md).

---

## 2. Task / TaskRunner — `task/`

### `Task` (abstract)

A deliberately tiny contract with **no Minecraft coupling**, so it stays
trivially compilable and testable:

| Member | Purpose |
|---|---|
| `onStart()` | First tick only. Reset state here. |
| `onTick()` | Returns a `Task` to run as a **subtask**, or `null` to continue. |
| `onStop(Task interrupt)` | Cleanup. `interrupt` is the task that displaced you, or `null`. |
| `isEqual(Task other)` | Identity for subtask **reuse** — "same goal means keep running". |
| `isFinished()` | Override for a real completion condition. Default `false`. |
| `toDebugString()` | Label for `/astralclef status`. |

`isFinished()` is what advances a phase — so a phase root drives its subtasks by
holding a candidate, checking `isFinished()` on it, and advancing when true.
`FullProgressionTask`, `ChMoonTask`, `ChMarsTask`, `ChMercuryTask`, and
`ChAstralSingularityTask` all share that identical `drive` / `advance` shape.

### `TaskRunner` (singleton)

Holds exactly **one user task** and **one subtask**.

- `runUserTask(task)` — stops the previous chain and installs the new one. A no-op
  if the same task is already active, so commands are safe to spam.
- `tick()` — ticks the user task; if it returned a subtask, ticks that. **Nesting
  is one level deep** — a nested return value from a subtask is logged at debug
  and ignored.
- `cancel()` — stops both.

This single-level limit is why phases hand their subtask *up* to the runner
rather than chaining phases themselves.

---

## 3. Create jobs — `tasks/create/`

### `CreateRecipeExecutor` (singleton)

Owns the job registry and the world search context.

- Jobs are keyed by `kind + '\0' + recipeId`, so requesting the same kind+recipe
  returns the **existing** in-flight job rather than starting a duplicate.
- `getWorldContext()` is a mutable `CreateWorldContext` — world, origin, and
  search radius (default **16**).
- When a job needs a world and the context is unset, the executor auto-binds from
  the first online player. `/astralclef context` does it manually.
- `tick(server)` advances every in-flight job one tick.

> Note: `clearFinished()` exists to evict done/failed jobs but currently has no
> call sites, so a **failed** job stays in the registry permanently and blocks
> re-submission. See [roadmap](roadmap.md#high).

### `CreateRecipeJob`

One job drives one machine through a fixed five-step pipeline:

```
LOCATE_MACHINE → INSERT → PROCESS → EXTRACT → DONE
```

| Step | What happens | Timeout |
|---|---|---|
| `LOCATE_MACHINE` | Cube-scan the search radius for the machine block | 100 ticks |
| `INSERT` | Add real ingredients, one binding input at a time; may span ticks | 60 ticks |
| `PROCESS` | Wait for kinetic start, then completion | 120 start / 200 complete |
| `EXTRACT` | Pull the output, filtered against the expected result | 60 ticks |
| `DONE` | Terminal | — |

Design points worth knowing:

- **No conjured items.** `INSERT` takes stacks from the player inventory first
  and only seeds the shortfall from the binding spec.
- **`PROCESS` never blind-completes.** It watches `running`, `processingTicks`,
  `currentRecipe`, `getSpeed()`, and `failReason`, and fails on a clear error
  rather than assuming success after a dwell timer.
- **Fluid outputs stay in the machine.** Item products are handed to the player;
  fluids are verified but left in the basin for the next job.

---

## 4. Machine I/O — `tasks/create/world/`

This is the layer that turns "a block position" into "items moved in and out".

### Locating

`CreateMachineType` maps a recipe `Kind` to candidate block ids and, separately,
to an **ordered candidate list** for locate:

| Kind | Locate candidates |
|---|---|
| `SEQUENCED_ASSEMBLY` | Depot, Belt |
| `FILLING` | Spout, Basin, Depot |
| `MIXER_BASIN`, `GROUT` | Basin, Mechanical Mixer |
| `COMPOUND_SMELT` | Furnace / Blast Furnace / Smoker |
| `BRONZE_SMITH` | Smithing Table |
| `PRESS_DUST` | Mechanical Press |
| `MECHANICAL_CRAFTING` | Mechanical Crafter |

`CreateMachineLocator` then cube-scans from the context origin and takes the
**nearest** match across all candidates.

### Moving items — three tiers, in order

`CreateMachineIO.insert` / `.extract` degrade through:

1. **Typed Create block entity** (`CreateBlockEntityIO`) — real Create behaviours
   via reflection: `BasinBlockEntity`, `DepotBlockEntity`,
   `MechanicalPressBlockEntity`, `MechanicalMixerBlockEntity`,
   `SpoutBlockEntity`, `CrafterBlockEntity`. Fluids go through
   `SmartFluidTankBehaviour` and Fabric's `Storage<FluidVariant>`.
2. **Fabric Transfer API** — `ItemStorage.SIDED` / `fluidCapability`.
3. **Vanilla `Inventory`** — the block entity implements `Inventory`.

Reflection against Create internals is the fragile part; misses are handled but
several failure paths are currently silent. See [roadmap](roadmap.md).

---

## 5. Embodiment — `bot/`

Server-side hands and feet, so Astralclef works on a dedicated server with no
Baritone and no client.

| Class | Responsibility |
|---|---|
| `BotActions` | `breakBlock` (survival break, real drops and tool wear, reach-checked, bedrock refused), `eatIfHungry`, `placeBlock` (survival click with support-face search), tool selection |
| `BotMovement` | `stepToward` — short direct teleports that land only on verified standable columns (passable feet + head, solid ground, no fluids), zeroing fall distance |
| `BotCrafting` | Crafts against the **live `RecipeManager`** from real inventory — pack and KubeJS recipes included. Tries shaped patterns in all row orders, consumes ingredients, returns result and remainders. No screens, no conjured items. |
| `BotSmelting` | Real placed-furnace smelting — moves input and fuel from the player, pulls the product back, and stages exactly enough to free the furnace for the next metal |

Baritone, when installed, is preferred for pathing and mining — but never
required.

---

## 6. Soft dependencies

Everything optional is reached by reflection behind a `try` / `catch (Throwable)`
so a missing mod degrades instead of crashing:

| Dependency | Helper | Behaviour when absent |
|---|---|---|
| FTB Quests | `quests/FtbQuestsHelper` | Quest checks return `false`; status reports `absent` |
| Baritone | `movement/BaritoneHelper` | Falls back to `bot/` embodiment |
| Ad Astra | `world/RocketHelper`, `AdAstraRoutes` | Proximity gates report absent |
| Flywheel | — | Ships inside Create; never referenced directly |

> Reflection against **Minecraft's own** classes is the sharp edge here. At
> runtime a Fabric production environment is in **intermediary** mappings, so
> neither the Yarn name (`net.minecraft.util.Identifier`) nor the Mojmap name
> (`net.minecraft.resources.ResourceLocation`) is loadable. `FtbQuestsHelper`
> currently does the latter and therefore always fails. See
> [roadmap](roadmap.md#high).

---

## 7. Quest ids — `quests/`

`AstralQuests` holds the FTB Quests ids the bot gates on (Chapter 2–6 constants
such as `CH2_ANDESITE_CASING`, `CH3_MOON_DIMENSION`, `CH4_DESH`,
`DRAGON_KILL`, `WIN_ASTRAL_SINGULARITY`), transcribed from the pack's quest
files. Because ids are pack-version-specific, treat them as data to be
re-verified when the pack updates rather than as constants.

`InventoryHelper` resolves `#tag`-style item references through
`Registry.ITEM` entry lists, which is how checks like `#create:alloy_nuggets`
work without a compile-time dependency on the tag.
