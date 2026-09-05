# Astralclef

Phased automation bot for **Create: Astral** on Fabric 1.18.2.

## Goal

Win condition: complete **FTB Quests Chapter 6 — Astral Singularity**.

## Phases

| Phase | Focus | Status |
|-------|--------|--------|
| **Ch0.5–1** | Getting started (early Create + Astral basics) | **Stubbed** — Task API + ship-order loop |
| Moon | Lunar progression | Stub |
| Mars | Martian progression | Stub |
| Mercury | Mercurial progression | Stub |
| Singularity | Astral Singularity (endgame win) | Stub |

### Ch0.5–1 Create loop (ship order)

Stubbed in `Ch01GettingStartedTask` via `TaskRunner` (`/astralclef ch01`):

1. **Ch0.5 unlock** — Crafting/Hephaestus or copper tools → Furnace → Fe/Sn/Cu (Pickadze) → Essential Materials (*quests31*)
2. **Alloy / Casing** — Bronze (Cu+Sn, not Brass) → Andesite Compound smelt (*quests26*) → Alloy stockpile (*quests5*) → Andesite Casing
3. **Mixer loop** — Hand Crank / shafts / water wheel → Millstone / Press / Mixer+Basin → sheets, fans, drill/saw → press-dust
4. **Grout gate** — Grout via Mixer (*quests25*) → Chapter 2 unlock

Astral flags wired as `CreateRecipeKinds` hooks: Compound smelt, Bronze smith, press-dust, Mixer+Basin, Grout.  
Deferred: trains, ComputerCraft, Astral Signals. FTB SNBT edges unverified.

### Tick wiring + Create recipe executor

`AstralclefMod` registers `ServerTickEvents.END_SERVER_TICK` and each tick:

1. `TaskRunner.getInstance().tick()` — active user / subtask
2. `CreateRecipeExecutor.getInstance().tick()` — advance in-flight Create recipe jobs

`CreateRecipeKinds` handlers start jobs on the executor (`LOCATE_MACHINE → INSERT → PROCESS → EXTRACT → DONE`) with kind-specific step notes (sequenced assembly, mechanical crafting, filling, basin, plus Astral compound smelt / bronze / press-dust / mixer+basin / grout).

**Status:** executor framework is real and extensible. Machine I/O is still **placeholder** (log + short tick delay) until Create block APIs are on the classpath.

**Remaining TODOs**

- Plug Create block-entity / inventory APIs into `CreateRecipeJob` step bodies
- World locate for depot/belt, crafter grid, spout, basin, press, mixer, furnace/smith
- Item extract verification + fail paths when machines missing
- Bind Ch01 subtasks to concrete `recipeId`s / KubeJS keys via `KubeJsAwareCatalogue`

## Commands

| Command | Action |
|---------|--------|
| `/astralclef ch01` | Start Ch0.5–1 via TaskRunner |
| `/astralclef status` | Show active task |
| `/astralclef cancel` | Cancel active task |
| `/astralclef tick` | Manual TaskRunner tick (debug) |

## Build

```bash
./gradlew build
```

Requires JDK 17+.

## License

MIT — kaydzer0305-png
