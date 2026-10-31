# Skin schema: versions, minors, warnings, rule sync

## schemaVersion / schemaMinor
- `schemaVersion: 2` is the only skin format the app accepts (v1 is still read). Anything else is rejected exactly as before.
- `schemaMinor` (optional, integer 0-99, default 0) is **informational**. Minor 1 only **adds optional keys** (see `skinMinor1` in
  `skin-rules.manifest.json`). An app that does not know a key ignores it, so a document may use minor-1 keys and still keep
  `min_app_version = 1`. Never change the meaning of an existing key.
- A newer `schemaMinor` than the app knows is applied anyway (warning `NEWER_MINOR`).
- Minor-1 keys are read only where a screen opts in. Phase 2 pilot: `TextSettingsCell` and `HeaderCell` on the **Privacy** screen
  read `components.cell.{heightDp,titleSizeSp,valueSizeSp,titleWeight,dividerVisible,dividerInsetDp,dividerColor}` and
  `components.section.{headerSizeSp,headerWeight,headerColor}` (`implementedMinor1` in the manifest). Every other minor-1 key still
  raises `RECOGNIZED_NOT_YET_SUPPORTED`. Absent keys always mean "Telegram's own behaviour"; a cell that did not opt in
  (`useSkinTokens()`), or has a custom ResourcesProvider, never reads them.
- Known limit of the pilot: tokens are applied when the cell is created/attached. A skin change while Privacy is open takes effect
  when the screen is reopened; `headerColor` can be reset by Telegram's own day/night re-tint until then.

## Warnings (never reject, never change how a skin is applied)
| Code | Meaning |
|---|---|
| `UNKNOWN_FIELD` | key is not in the schema (typo / foreign key). Never raised below `meta`. |
| `RECOGNIZED_NOT_YET_SUPPORTED` | valid key that no screen reads yet |
| `NEWER_MINOR` | `schemaMinor` is higher than this app knows |
| `UNSUPPORTED_ON_SCREEN`, `CLAMPED`, `LOCKED_ROW` | reserved for later phases |

An invalid **value** of a known key is not a warning: it rejects the skin (app at parse time, API on upload), as before.
Where to see them: in the app `KuroSkinManager.get().spec().warnings()`; before pasting a document into Atlas run
`python scripts/validate_theme.py doc.json [--previous published.json]` in the backend repo (Atlas edits are never validated by the API).

## One rule list, three places
`docs/skin-rules.manifest.json` (this repo, canonical) = Java arrays (`SkinSpec.RULES_V2 / RULES_V2_MINOR1 / STRUCTURE / LAYOUT_NOT_YET`,
`LayoutSpec.RULES`) = backend Python lists (`theme.py`, `layout_rules.py`) and the backend's vendored copy.
`python3 Tools/check_rules.py [--backend ../backend-api]` checks all of it; `--update` rewrites the manifest from the Java sources after an
intended change. `docs/skin-rules.vectors.json` holds shared value cases both validators must agree on.
Change order: Java rule -> `--update` -> copy manifest + vectors into the backend repo -> backend rule -> redeploy backend -> app.

## Screen capabilities
`tw.nekomimi.nekogram.skin.ScreenCapabilities` records what a skin can customise on each screen (S/P/N/X). Information only.
`BaseFragment.screenId()` (default null) lets a screen declare its id.
