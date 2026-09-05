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
