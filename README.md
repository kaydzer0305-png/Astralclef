# Astralclef

Phased automation bot for **Create: Astral** on Fabric 1.18.2.

## Goal

Win condition: complete **FTB Quests Chapter 6 — Astral Singularity**.

## Phases

| Phase | Focus | Status |
|-------|--------|--------|
| Ch0.5–1 | Getting started (early Create + Andesite) | **In progress** — Task API + Create loop |
| Moon | Lunar progression | Stub only |
| Mars | Martian progression | Stub only |
| Mercury | Mercurial progression | Stub only |
| Singularity | Astral Singularity (endgame win) | Stub only |

## Ch0.5–1 task loop

Minimal Altoclef-style API under `com.ezquest.astralclef.task`:

- `Task` — `onStart` / `onTick` (may return a subtask) / `onStop` / `isEqual`
- `TaskRunner` — holds the active user task and ticks it

`Ch01GettingStartedTask` drives:

1. Gather basics  
2. Andesite alloy path  
3. Early Create machine placement  

Create recipe kinds (`SEQUENCED_ASSEMBLY`, `MECHANICAL_CRAFTING`, `FILLING`, `BASIN`) are registered as stub handlers in `CreateRecipeKinds`.

### Starting the loop

On mod init, `AstralclefMod` loads `TaskRunner`, `CreateRecipeKinds`, and command hooks.

Invoke the placeholder command hook (from code or a future `/astralclef start`):

```java
AstralCommands.startCh01();  // runs Ch01GettingStartedTask on TaskRunner
AstralCommands.status();     // active task debug label
AstralCommands.stop();       // cancel
```

Wire a server tick to `TaskRunner.getInstance().tick()` when integrating with gameplay.

## Build

```bash
./gradlew build
```

Requires JDK 17+.

## License

MIT — kaydzer0305-png
