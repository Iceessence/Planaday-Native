# Planaday Native — Shared Design System & Platform UX (`DESIGN_SYSTEM.md`)

**Philosophy:** One tactile, calm, high-legibility design language (`core:design-tokens`) powering two independently optimized native interaction models: touch-first adaptive Android (`apps:android`) and dense multi-pane keyboard/mouse Windows Desktop (`apps:desktop`).

---

## 1. Design Tokens (`core:design-tokens`)

- **Color Palettes:** Light Warm Paper (default tactile notebook feel), Crisp Daylight, Deep Slate Dark, High-Contrast Light (WCAG AAA ≥7:1), High-Contrast Dark (WCAG AAA ≥7:1), plus user-customizable accent tokens and workspace color coding (Personal Warm Amber `#A97434`, Lab Deep Teal `#0F766E`, Creative Indigo `#4F46E5`, Kitchen Sage `#3F6212`, Finance Slate `#1E3A8A`).
- **Surface Materials:** Warm Paper, Cream Linen, Dot Grid, Ruled Notebook, Graph Grid, Clean Flat — rendered natively via Compose `DrawScope` without CSS bloat.
- **Typography Scale:** 4 selectable pairings (Editorial Serif + Sans, Modern Clean Sans, Technical Mono + Sans, Warm Handwritten Accent), scalable `80%–130%` honoring OS font-scale accessibility settings.
- **Density Modes:** `COMPACT` (dense desktop tables/lab grids), `STANDARD`, `COMFORTABLE` (touch-first).
- **Accessibility Non-Negotiables:**
  - Minimum `48.dp` interactive touch targets on Android (`32.dp` minimum clickable targets on dense Windows desktop controls with full keyboard focus rings).
  - Explicit `contentDescription` / semantics labels on every icon button (`PD-013`).
  - Zero horizontal overflow down to `320.dp` width on Android (`PD-011`, `PD-012`, `PD-019`).
  - Honors OS `reducedMotion`, RTL layout mirroring (`LayoutDirection.Rtl`), high contrast, and full screen-reader traversal (TalkBack on Android, Windows UI Automation via Compose Desktop semantics).

---

## 2. Android Interaction Model (`apps:android`)

- **Window Size Classes:**
  - Compact (`320dp–599dp`): 4-slot user-configurable Bottom Navigation Bar + More/All-Modules Drawer + Quick Capture FAB + safe `imePadding()` / `navigationBarsPadding()`.
  - Medium (`600dp–839dp`): Navigation Rail + adaptive 2-column grids.
  - Expanded (`840dp+` tablets/foldables): Permanent Navigation Rail + Master-Detail Split Pane.
- **State Preservation:** Full process-death state restoration (`rememberSaveable` + durable SQLite drafts).
- **Native Integrations:** Android `AlarmManager` / `WorkManager`, Photo Picker / CameraX, Share Sheet intake, PrintManager, Glance home screen widgets, runtime permission explanations with graceful offline fallbacks.

---

## 3. Windows Desktop Interaction Model (`apps:desktop`)

- **Multi-Pane Shell:** Collapsible grouped Sidebar (reordable/hideable tabs + Workspace switcher + Schedule override badge), primary Workspace Canvas, optional toggleable Right Inspector Pane (linked records, properties, revision diffs, quick timers), and bottom Status Bar (Save state indicator, Sync & Blob Receipt indicator, Active Context, Release Identity).
- **Keyboard-First Workflows:**
  - Global Command Palette (`Ctrl+K`) searching all 54+ modules, commands, and FTS5 records.
  - Quick Capture (`Ctrl+Shift+N`), Global Search (`Ctrl+F`), Save Checkpoint (`Ctrl+S`), Undo/Redo (`Ctrl+Z` / `Ctrl+Y`), New Window (`Ctrl+Shift+W`), Print Preview (`Ctrl+P`).
  - Full keyboard-only Tab/Arrow navigation and user-rebindable shortcuts (`N-004`).
- **Multi-Window Consistency:** Multiple desktop windows observe the same scoped Kotlin `Flow` streams backed by Room SQLite so edits in Window A reflect immediately in Window B without stale state.
