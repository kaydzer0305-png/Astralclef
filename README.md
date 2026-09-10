# Astralclef

Phased automation bot for **Create: Astral** on Fabric 1.18.2.

## Goal

Win condition: complete **FTB Quests Chapter 6 — Astral Singularity**.

## Dependencies

| Dep | Version | Notes |
|-----|---------|-------|
| Minecraft / Fabric | 1.18.2 / Fabric API | Loom 1.0+
| **Create Fabric** | `0.5.1-f-build.1415+mc1.18.2` | **Hard** `depends` in `fabric.mod.json` (`"create": "*"`). DevOS maven `com.simibubi.create:create-fabric-1.18.2`. Fallback: Modrinth / CurseMaven. |
| Flywheel | JiJ inside Create (0.6.10-39) | Do not pin 0.6.4; transitive=false on Create jar keeps Astral-verified pin. |

Runtime **requires Create** installed. Compile classpath includes Create via `build.gradle`.

## Phases

| Phase | Focus | Status |
|-------|--------|--------|
| **Auto** | Full progression `ch01→moon→mars→mercury→singularity` | `FullProgressionTask` + `/astralclef auto` |
| **Ch0.5–1** | Getting started (early Create + Astral basics) | Bindings + typed BE I/O + Create jobs; gather/place unlock, material pre-gather, casing + grout gates |
| Moon | Lunar progression | `ChMoonTask` `LAUNCH_PREP→LUNAR_SURFACE→MOON_DUNGEON` + `/astralclef moon` |
| Mars | Martian progression | `ChMarsTask` `MARS_PREP→MARS_SURFACE→MARS_DUNGEON` + `/astralclef mars` |
| Mercury | Mercurial progression | `ChMercuryTask` `MERCURY_PREP→MERCURY_SURFACE→MERCURY_VAULT` + `/astralclef mercury` |
| Singularity | Astral Singularity (Ch6 win) | `ChAstralSingularityTask` `GREAT_BEAST→CRAFT_SINGULARITY→QUEST_COMPLETION` + `/astralclef singularity`; `GreatBeastPhase` + `/astralclef beast` |

### Ch01 recipe bindings (`Ch01RecipeBindings`)

Authoritative Astral item ids + `RecipeSpec` (type, inputs, output) with `astralclef:bind/*` placeholders.
Resolve live datapack/KubeJS recipe ids via **RecipeManager type + I/O** (do not hard-depend on guessed `kubejs:` auto-ids).

| Bind | Type | I/O (Astral kubejs) |
|------|------|---------------------|
| `astralclef:bind/bronze_smith` | `minecraft:smithing` | Cu + Sn → `createastral:bronze_ingot` |
| `astralclef:bind/compound_shaped` | `minecraft:crafting_shaped` | 3 andesite / 3 zinc_nugget\|`#create:alloy_nuggets` / 3 clay → compound (BBB/AAA/CCC) |
| `astralclef:bind/compound_smelt` | `minecraft:smelting` | compound → `create:andesite_alloy` |
| `astralclef:bind/compound_blast` | `minecraft:blasting` | same (stock Create alloy recipes removed) |
| `astralclef:bind/press_dust` | `create:pressing` | cobble → `techreborn:andesite_dust` |
| `astralclef:bind/compact_andesite` | `create:compacting` | 4× dust → andesite |
| `astralclef:bind/mixer_compound_mixture` | `create:mixing` | andesite+nugget+clay → `kubejs:compound_mixture` (**not** mixer→alloy) |
| `astralclef:bind/grout` | `create:mixing` | alloy + zinc + 8 gravel → 8 `tconstruct:grout` |

Items: bronze/sheet `createastral:*`; compound `createastral:andesite_compound`; alloy `create:andesite_alloy`; dust `techreborn:andesite_dust`; grout `tconstruct:grout`; fluid `kubejs:compound_mixture`.

`CreateRecipeExecutor` seeds INSERT stacks from bindings; `KubeJsAwareCatalogue.shared()` / `ch01()` expose the catalogue.
Swap opaque ids in `Ch01RecipeIds` (aliases to `Ch01RecipeBindings.BIND_*`) when Research lands exact JEI ids.

### Create world locate + machine I/O

Package `com.ezquest.astralclef.tasks.create.world`:

- `CreateMachineType` — Kind → block ids (`create:basin`, `mechanical_press`, `mechanical_mixer`, `depot`, `belt`, `mechanical_crafter`, `spout`, `millstone`, `andesite_casing`, plus vanilla furnace/smith)
- `CreateWorldContext` — ServerWorld + origin + searchRadius (default 16)
- `CreateMachineLocator.locate` — cube scan; nearest matching block
- `CreateMachineIO` — **typed Create BE** (`CreateBlockEntityIO`: Basin / Depot / Press / Mixer / Spout / Crafter via reflection) → Fabric Transfer → Inventory fallback
- `CreateRecipeJob` — LOCATE → multi-INSERT (binding inputs) → PROCESS → EXTRACT (output filter)

`CreateRecipeExecutor.tick(server)` auto-binds context from the first online player when jobs need it. Manual: `/astralclef context`.

#### Ch01 harden status (`feat(create): harden Ch01 fluid+kinetic`)

| Area | Status |
|------|--------|
| Basin fluid `kubejs:compound_mixture` | **Hardened** — `inputTank`/`outputTank` (SmartFluidTankBehaviour) + Fabric `Storage<FluidVariant>` / `fluidCapability` insert+extract |
| Mixer/Press kinetic PROCESS | **Hardened** — watch `running`, `runningTicks`, `processingTicks`, `currentRecipe`, `getSpeed()`, `getBasin()`; wait start→complete or clear `failReason` timeout (no blind soft-complete) |
| Pack-id confirm | **Hardened** — `Ch01RecipeBindings.confirmMatched` logs RecipeManager `ResourceLocation`; `/astralclef recipes` dumps Ch01 binds (type+IO bind retained) |
| Create pin | `create-fabric-1.18.2:0.5.1-f-build.1415+mc1.18.2` |

**Stabilize pass — done:**
- Spout fluid via generic Transfer fallback (`tryInsertFluid`/`tryExtractFluid` no longer basin-gated; tank path stays basin-only) — basin Transfer still preferred for mixture
- Basin filter **write** via `setBasinFilter` (FilteringBehaviour `setFilter`/field, best-effort; auto-applied before basin INSERT when expected output known)
- Crafter **group** insert via `insertCrafterGroup` (3x3 crafters around locate pos, recipe-order distribute for BBB/AAA/CCC)
- Furnace COMPOUND_SMELT polls `AbstractFurnaceBlockEntity` output/LIT instead of blind dwell; smithing table fails fast (no BE — needs player UI)
- RPM floor `MIN_KINETIC_SPEED=32` with low-speed warn every 40 ticks (stress/network still not modeled beyond `getSpeed()`)
- DepotBehaviour rename warns with class name when reflection misses

**Still open by design:**
- Exact pack-local KubeJS auto-ids resolve at datapack load (`Ch01RecipeIds` swap hooks; `refresh` on context set/auto-bind)

#### Phase completion status

- **Ch0.5 unlock** (`Ch05UnlockSubtask`) — gathers + places crafting table and furnace (idempotent nearby-block check), mines Fe/Sn/Cu, ensures andesite + clay essential mats
- **Alloy/Casing** (`AlloyCasingSubtask`) — gathers andesite + zinc/iron nuggets up front, awaits bronze/compound/smelt jobs, gates casing on inventory or FTB `CH2_ANDESITE_CASING`
- **Mixer loop** (`MixerLoopSubtask`) — fires kinetics/mill/press/mixer crafts, places hand crank or water wheel + mechanical press when items exist, awaits press-dust / compact / mixture jobs
- **Moon/Mars/Mercury surface** — FTB dimension gate, then stockpile desh / ostrum / calorite via `GatherTask`
- **Dungeon/vault clears** — gated on planet loot (desh / ostrum / calorite) or the matching FTB quest; otherwise stockpile + wait (no automated combat)
- **Launch prep** (`LaunchPrepSubtask`) — oxygen gear → rocket (Create craft pipeline, else gather) → fuel → `ad_astra:launch_pad` nearby gate (`RocketHelper.hasLaunchPad`)
- **Singularity** (`CraftSingularitySubtask`) — stockpiles desh/ostrum/calorite, fires the sequenced-assembly job, completes on `createastral:astral_singularity` in inventory or job finish
- **Great Beast** (`GreatBeastPhase`) — paths to the nearest hostile (Baritone when present), swings via server-side `attack`, gates loot on the area going clear; soft-advances with a warning if nothing shows up (never stalls `/astralclef auto`)
- **Structures** (`StructureLocator`) — direct 1.18.2 `ServerWorld.locateStructure(TagKey, ...)` path first, reflection fallbacks after; null-safe stub degrade
- **Inventory** (`InventoryHelper`) — `#tag` checks resolve via `Registry.ITEM` entry lists (used by `#create:alloy_nuggets` and friends)

#### Autonomy / embodiment (`bot` package, Altoclef-style)

Server-side hands and feet — no Baritone required, works on dedicated servers:

- `BotActions.breakBlock` — survival break via `interactionManager.tryBreakBlock` (real drops, tool wear, reach-checked, bedrock refused); auto-selects a suitable hotbar tool
- `BotActions.eatIfHungry` — consumes one real food item for its `FoodComponent` values when hungry (honest economy, no free saturation)
- `BotMovement.stepToward` — small direct teleports (always legal, no anti-cheat kick) landing only on verified standable columns (passable feet+head, solid ground, no fluids); fall distance zeroed
- `GatherTask` mine loop — scan (with unbreakable/unreachable skip-list) → step into vacuum range (≤2.2 blocks, so drops land on the player) → break; Baritone `MineProcess` still preferred when present
- `GreatBeastPhase` — server-side `attack` swings + area-clear loot gate
- `BotCrafting` — crafts against the live `RecipeManager` (pack/KubeJS included) from real inventory: compound BBB/AAA/CCC rows tried in all orders, ingredients consumed, result + remainders given back; no screens, no conjured items
- `BotSmelting.tickSmelt` — real placed-furnace smelting (input + fuel moved from the player, product pulled back, exact staging so the furnace frees up for the next metal); Ch0.5 smelts Cu/Sn/Fe to ingots, coal gathered as fuel
- `BotActions.placeBlock` — survival click via `interactionManager.interactBlock` (support-face search, sneak so tables don't swallow the use); `BlockPlacementHelper` prefers it, direct set remains as fallback
- `CreateRecipeJob` INSERT economy — takes stacks from the player first (`player inventory` in logs), seeds only the shortfall (`binding seed`); leftovers that don't fit are handed back, never voided
- `CreateRecipeJob` EXTRACT delivery — item products are given to the player (jobs previously voided their own output); fluid outputs are verify-only, left in the basin for downstream use
- Furnace jobs self-stoke: `COMPOUND_SMELT` INSERT pulls one fuel from the player (`BotSmelting.stokeFurnaceAt`), so the output poll can actually observe smelting
- `GreatBeastPhase` kiting — backs off under 3 blocks, strafes in on alternating flanks, swings in reach; area-clear loot gate with soft timeout
- Rocket assembly gates log NASA-workbench proximity (`RocketHelper.hasWorkbench`); slot-driving needs Ad Astra internals (not on the compile classpath — Create only, `transitive=false`)

Not yet autonomous: multi-component rocket assembly, machine-blueprint building, Ad Astra rocket launch/entity ride, boss kiting. Smithing-table bronze still needs the player UI (no block entity to drive).

## Build

```bat
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
.\gradlew.bat build
```

Requires JDK 17+. Create resolves from DevOS snapshots (`mvn.devos.one`). Gradle wrapper (`gradle-7.3.3`) is
checked in via `gradle/wrapper/` + `gradlew.bat`. Loom 1.0.18 pins `create-fabric-1.18.2:0.5.1-f-build.1415+mc1.18.2`.
Fabric Transfer 1.6.0 (1.18.2) iteration uses `storage.iterable(tx)` with an outer `Transaction`.

### Ch0.5–1 Create loop (ship order)

`Ch01GettingStartedTask` via `TaskRunner` (`/astralclef ch01`):

1. **Ch0.5 unlock** — Crafting/Hephaestus or copper tools → Furnace → Fe/Sn/Cu → Essential Materials
2. **Alloy / Casing** — Bronze smith → Compound shaped → Smelt/Blast → Alloy stockpile → Andesite Casing
3. **Mixer loop** — Kinetics → Mill/Press/Mixer → press-dust → compact → early mixer mixture
4. **Grout gate** — Grout via Mixer → Chapter 2 unlock

Subtasks fire `CreateRecipeKinds` with bind ids and wait for job completion.

## Commands

| Command | Action |
|---------|--------|
| `/astralclef auto` | Full auto `ch01→moon→mars→mercury→singularity` (`FullProgressionTask`) |
| `/astralclef ch01` | Start Ch0.5–1 via TaskRunner |
| `/astralclef moon` | Start Moon (`ChMoonTask`) |
| `/astralclef mars` | Start Mars (`ChMarsTask`) |
| `/astralclef mercury` | Start Mercury (`ChMercuryTask`) |
| `/astralclef singularity` | Start Singularity Ch6 win (`ChAstralSingularityTask`) |
| `/astralclef beast` | Start Great Beast (`GreatBeastPhase`) |
| `/astralclef gather <item> [count]` | Gather via `GatherTask`+`BlockLocator` (Baritone soft) |
| `/astralclef status` | Show active task + Create job/context summary |
| `/astralclef context` | Bind locate origin to your world + block pos |
| `/astralclef cancel` | Cancel active task |
| `/astralclef tick` | Manual TaskRunner + Create executor tick |
| `/astralclef recipes` | Dump Ch01 binds → resolved RecipeManager pack ids |

## License

MIT — kaydzer0305-png
