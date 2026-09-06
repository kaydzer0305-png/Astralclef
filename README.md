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
| **Ch0.5–1** | Getting started (early Create + Astral basics) | Stubbed + Create locate/IO wired |
| Moon | Lunar progression | Stub |
| Mars | Martian progression | Stub |
| Mercury | Mercurial progression | Stub |
| Singularity | Astral Singularity (endgame win) | Stub |

### Create world locate + machine I/O

Package `com.ezquest.astralclef.tasks.create.world`:

- `CreateMachineType` — Kind → block ids (`create:basin`, `mechanical_press`, `mechanical_mixer`, `depot`, `belt`, `mechanical_crafter`, `spout`, `millstone`, `andesite_casing`, plus vanilla furnace/smith)
- `CreateWorldContext` — ServerWorld + origin + searchRadius (default 16)
- `CreateMachineLocator.locate` — cube scan; nearest matching block
- `CreateMachineIO` — Fabric Transfer insert/extract with Inventory fallback; verifies BE present

`CreateRecipeJob` stores `machinePos` after LOCATE; fails after 100 ticks if no context/machine. INSERT/PROCESS/EXTRACT call CreateMachineIO.

`CreateRecipeExecutor.tick(server)` auto-binds context from the first online player when jobs need it. Manual: `/astralclef context`.

**Remaining gaps:** typed Create BE behaviours (BasinBehaviour, DepotBehaviour, crafter grid fill); full Transfer for all Create storages; recipe input ItemStacks from catalogue; kinetic PROCESS detection.

### Ch0.5–1 Create loop (ship order)

Stubbed in `Ch01GettingStartedTask` via `TaskRunner` (`/astralclef ch01`):

1. **Ch0.5 unlock** — Crafting/Hephaestus or copper tools → Furnace → Fe/Sn/Cu → Essential Materials
2. **Alloy / Casing** — Bronze (Cu+Sn) → Andesite Compound smelt → Alloy stockpile → Andesite Casing
3. **Mixer loop** — Hand Crank / shafts / water wheel → Millstone / Press / Mixer+Basin
4. **Grout gate** — Grout via Mixer → Chapter 2 unlock

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
