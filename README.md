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
| **Ch0.5–1** | Getting started (early Create + Astral basics) | Bindings + typed BE I/O + Create jobs |
| Moon | Lunar progression | Stub |
| Mars | Martian progression | Stub |
| Mercury | Mercurial progression | Stub |
| Singularity | Astral Singularity (endgame win) | Stub |

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

**Remaining gaps / soft:**
- Spout / `compound_mixture` **fluid** Transfer
- Basin filter **write**; crafter **pattern** encode (grid slots only)
- Press/Mixer PROCESS: reflective `running` hint only (no RPM/stress)
- Exact pack-local recipe ids (bind placeholders resolve via RecipeManager; `Ch01RecipeIds` constants for swap)
- DepotBehaviour / Create package renames when reflection soft-fails

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
| `/astralclef ch01` | Start Ch0.5–1 via TaskRunner |
| `/astralclef status` | Show active task + Create job/context summary |
| `/astralclef context` | Bind locate origin to your world + block pos |
| `/astralclef cancel` | Cancel active task |
| `/astralclef tick` | Manual TaskRunner + Create executor tick |

## Build

```bash
./gradlew build
```

Requires JDK 17+. Create resolves from DevOS snapshots (`mvn.devos.one`).

## License

MIT — kaydzer0305-png
