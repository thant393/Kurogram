# Kurogram Theme Builder

Architecture notes for the in-app, no-code theme editor. The approved proposal and decisions D1–D10 are in the project's
design document; this file is the working reference kept next to the code.

## Principles
- **The skin JSON is the draft.** `ThemeDraft` holds the document itself and changes it only at exact paths through commands
  (`set`, `unset`, `listAdd/Remove/Move`, `group`). Everything not addressed is never touched, so unknown fields, `meta`,
  future-version properties, extensions and `night` survive any edit. Round-trip tests compare **semantically** (key order and
  number formatting may differ; content may not).
- **Rules are the only source of types and ranges.** `docs/skin-rules.manifest.json` (identical to the Java `SkinSpec`/`LayoutSpec` and
  the backend, checked by `Tools/check_rules.py`) is loaded by `RuleSet`; the Builder adds no second schema.
- **Catalog = presentation only.** `docs/skin-editor.catalog.json` gives each rule a group, Basic/Advanced level, the components that
  open it when tapped, the screens that show it, the phase that builds its control, and text in every language.
- **Bilingual from the start.** Every label/description/component/group has `en` and `my`. Lookup falls back language → English →
  raw id. Burmese entries are generated from a glossary and listed as *not reviewed* (`reviewed: ["en"]`) until a native speaker confirms.
- **No manual JSON.** The only way to change the draft is through rule-checked commands; invalid values are refused with a reason and
  change nothing (and add no undo step).

## Preview safety protocol
`KuroSkinManager.preview(json)` is in-memory only. Rules the Builder must follow:
1. Never call `apply()` for a preview. Apply/publish happen only on an explicit user action.
2. Call `endPreview()` on: leaving preview, leaving the Builder, cancelling an edit, `onPause` (app to background), and in `finally`/`catch`
   of any preview call that fails or throws.
3. If the process dies, nothing was persisted, so the original theme is back on the next start (also covered by `SkinSafeMode`).

## Capability coverage
`python3 Tools/check_catalog.py --write` produces `docs/skin-editor.coverage.{json,md}`. Status per rule:
`EDITABLE_PREVIEWED`, `EDITABLE_REAL_ONLY`, `PLANNED_P1..P5`, `ENGINE_NOT_WIRED`, `ENGINE_UNSUPPORTED`, `UNMAPPED`.
`UNMAPPED` (a rule without a catalog entry) fails CI. `--final` also fails on any `PLANNED_*`; it becomes the release gate when
the Builder is complete. Engine wiring is recorded as `unverified` per rule until checked on a device.

## Tests
- `Tools/jvm-tests/run.sh` — JDK only. Shared vectors (all), manifest counts, catalog completeness, locale fallback, round trip of
  every preset/template, preservation, refusals, undo/redo, drag coalescing, grouped edits, sample value of every rule.
- `Tools/check_rules.py`, `Tools/check_catalog.py` — run in `.github/workflows/quick-check.yml` on every push.

## Adding a rule later
Add it to the engine and manifest as usual, then `python3 Tools/gen_catalog.py` (adds a draft entry only; never alters existing ones),
refine the entry, run `check_catalog.py --write`.
