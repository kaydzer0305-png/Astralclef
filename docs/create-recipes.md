# Create Recipes & Machine I/O

How Astralclef turns a recipe into a machine operation, and what it knows about
the Create: Astral recipe set.

---

## The binding model

Astralclef never hardcodes pack recipe ids. It declares a **bind** — a stable
placeholder like `astralclef:bind/bronze_smith` — carrying a *recipe type*, an
*input list*, and an *output*. At runtime `Ch01RecipeBindings` walks the live
`RecipeManager` and resolves each bind to a real recipe by matching **type + I/O**.

```
astralclef:bind/bronze_smith
        │  type: minecraft:smithing
        │  in:   1x minecraft:copper_ingot, 1x techreborn:tin_ingot
        │  out:  1x createastral:bronze_ingot
        ▼
RecipeManager scan ──▶ create:smithing/bronze_ingot   (cached in RecipeSpec)
```

Two consequences:

- **Pack updates don't break binds.** New KubeJS auto-ids are discovered rather
  than assumed.
- **Resolution can fail.** If the pack changes a recipe, the bind stays
  unresolved and the job never progresses. `/astralclef recipes` is the
  diagnostic.

`KubeJsAwareCatalogue` (`recipes/`) exposes the snapshot;
`Ch01RecipeBindings.refresh(server)` re-resolves, and is currently called on
context set and auto-bind — **not** on `/reload`.

---

## Ch0.5–1 binds

| Bind | Recipe type | Inputs → Output |
|---|---|---|
| `bind/bronze_smith` | `minecraft:smithing` | `minecraft:copper_ingot` + `techreborn:tin_ingot` → `createastral:bronze_ingot` |
| `bind/compound_shaped` | `minecraft:crafting_shaped` | 3× andesite / 3× `create:zinc_nugget` / 3× `clay_ball` → `createastral:andesite_compound` (BBB/AAA/CCC) |
| `bind/compound_smelt` | `minecraft:smelting` | `andesite_compound` → `create:andesite_alloy` |
| `bind/compound_blast` | `minecraft:blasting` | same as above (Astral removes the stock Create alloy recipes) |
| `bind/press_dust` | `create:pressing` | `minecraft:cobblestone` → `techreborn:andesite_dust` |
| `bind/compact_andesite` | `create:compacting` | 4× `andesite_dust` → `minecraft:andesite` |
| `bind/mixer_compound_mixture` | `create:mixing` | andesite + alloy nugget + clay → **`kubejs:compound_mixture`** (a fluid — *not* alloy) |
| `bind/grout` | `create:mixing` | alloy + zinc + 8× gravel → 8× `tconstruct:grout` |

### Item and fluid ids

| Constant | Id |
|---|---|
| Bronze ingot / sheet | `createastral:bronze_ingot` / `createastral:bronze_sheet` |
| Andesite compound | `createastral:andesite_compound` |
| Andesite alloy | `create:andesite_alloy` |
| Andesite dust | `techreborn:andesite_dust` |
| Grout | `tconstruct:grout` |
| Compound mixture (fluid) | `kubejs:compound_mixture` |
| Zinc nugget / ingot | `create:zinc_nugget` / `create:zinc_ingot` |
| Alloy nuggets (tag) | `#create:alloy_nuggets` |

> **Tag handling caveat.** `IngredientRef` distinguishes `#tag` references from
> concrete ids, and `InventoryHelper` can resolve `#tag` checks through
> `Registry.ITEM` entry lists. But `Ch01RecipeBindings` currently substitutes a
> hardcoded `create:zinc_nugget` for the alloy-nugget tag at insert time, and the
> `ALLOY_NUGGETS_TAG` constant is otherwise unused. If the pack's alloy nuggets
> differ, the compound-shaped recipe will insert the wrong item.

---

## Machine targeting

`CreateMachineType` resolves each recipe `Kind` to an ordered list of candidate
blocks. `CreateMachineLocator` cube-scans the search radius (default **16** from
`/astralclef context`) and picks the **nearest** match.

| Kind | Locate candidates | Rationale |
|---|---|---|
| `SEQUENCED_ASSEMBLY` | Depot, Belt | Items are staged, not mixed |
| `FILLING` | Spout, Basin, Depot | Fluid fill prefers a spout |
| `MIXER_BASIN`, `GROUT` | Basin, Mechanical Mixer | The basin holds the mix; the mixer drives it |
| `COMPOUND_SMELT` | `furnace`, `blast_furnace`, `smoker` | Vanilla smelting |
| `BRONZE_SMITH` | `smithing_table` | |
| `PRESS_DUST` | `mechanical_press` | |
| `MECHANICAL_CRAFTING` | `mechanical_crafter` | |

> Two known defects live here: `compound_blast` is routed through
> `COMPOUND_SMELT`, which targets a plain furnace and can never match a
> `minecraft:blasting` recipe; and `bronze_smith` always resolves to a smithing
> table, which has no block entity to drive. Both are in the
> [roadmap](roadmap.md#high).

---

## Typed block-entity I/O

`CreateBlockEntityIO` is the largest and most delicate part of the mod — roughly
1,400 lines of reflection against Create's internals, because Create's block
entities expose items and fluids through behaviours rather than a plain
`Inventory`.

### Transfer tiers

Each `insert` / `extract` tries three strategies in order:

**1. Typed Create behaviours.** Reach the block entity's own API directly:

- **Basin** — `SmartFluidTankBehaviour` `inputTank` / `outputTank` for
  `kubejs:compound_mixture`, plus a `FilteringBehaviour` written via
  `setBasinFilter` so the basin targets the expected output before inserting.
- **Depot** — `DepotBehaviour`, whose name has moved between Create builds. A
  miss logs the actual class name found, so the rename is diagnosable.
- **Press / Mixer** — kinetic state: `running`, `runningTicks`,
  `processingTicks`, `currentRecipe`, `getSpeed()`, `getBasin()`.
- **Spout** — fluid via a generic Transfer fallback (`tryInsertFluid` /
  `tryExtractFluid`), which is no longer basin-gated; the typed tank path stays
  basin-only.
- **Crafter** — `insertCrafterGroup` distributes inputs across the 3×3 crafter
  group around the located position, in recipe row order, so the BBB/AAA/CCC
  compound pattern lands correctly.

**2. Fabric Transfer API.** `ItemStorage.SIDED` and the `fluidCapability`
capability, wrapped in an outer `Transaction`; iteration uses
`storage.iterable(tx)`.

**3. Vanilla `Inventory`.** For anything implementing the vanilla interface —
including furnace block entities, which are polled for output and `LIT` rather
than a blind dwell timer.

### Constants worth knowing

| Constant | Value | Meaning |
|---|---|---|
| `MIN_KINETIC_SPEED` | `32.0f` | RPM floor below which a machine is not considered turning |
| Droplets per ingot | `9000` | Fluid volume used when seeding basin amounts |

> Kinetic checking is deliberately shallow: stress and network propagation are
> **not** modelled. A machine with enough locally-attached speed is treated as
> running even if the wider contraption could not actually sustain it.
