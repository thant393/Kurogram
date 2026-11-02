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

## Screens that read the minor-1 tokens (Phase 2/3)

- Cells (`components.cell.*`, `components.section.header*`/`infoSizeSp`, `components.switch.*`): Privacy, Notifications, Data and Storage, Devices (styling only; rows, order and the locked Devices rows are untouched), Settings and every Kuro Settings screen (plain Telegram cells only).
- Section cards (`components.section.style|radiusDp|gapDp`): the same screens. `gapDp` is the card's side inset (Telegram's 12 dp). `style: flat` = full-width square sections.
- Settings grid (layout doc, `settings.mode: grid`, `settings.grid.columns` 2..4, default 3): the Settings entries become icon tiles. Rows with a bot object and headers stay full width. Rules for `settings.*` are no longer "not yet supported".
- Still native: Chats/Contacts/People/Unread component tokens, Profile and Theme Store slots, per-screen row maps, UX presets.
- Known limit: tokens are read when a cell or list is created; changing the skin while a screen is open needs the screen reopened.

## Row maps, screen backgrounds and session tokens (Phase 4/5)

- `layout.privacy.hiddenRows` / `layout.dataStorage.hiddenRows` (layout rules, list, may be empty): ids of rows to drop. Allowed ids exclude every security row (Two-Step, passcode, sessions, blocked, login email, delete account) and, on Data and Storage, mobile/wifi/roaming (other code addresses them by position). Absent list = unchanged row ints. Hiding the last row of a card can leave its neighbour's divider showing.
- `screens.privacy|notifications|dataStorage|devices.background` (minor 1, colour): the screen's background. Unset = the theme's own.
- `components.session.avatarRadiusDp|nameSizeSp` (minor 1): Devices session rows.
- When rows are hidden the row that ends a card drops its divider (LastInCard on TextSettingsCell/TextCheckCell/TextCell/NotificationsCheckCell, applied by RecyclerListView.trimLastDividers). NotificationsCheckCell (Notifications, Data and Storage) now reads the cell title/value size, title weight, divider and switch tokens too.
- `layout.notifications.hiddenRows` (reactions, call vibrate/ringtone, in-app sound/vibrate/preview, in-chat sound, priority, contact joined, pinned message, background service rows, repeat): independent rows only; private/group/channel/stories and the account rows stay.
- `layout.profile.hiddenRows` (phone, bio, username, birthday, bizHours, bizLocation, note): info rows of a user profile (ProfileActivity). The notifications row, restriction notice and every action stay.
- Not wired yet, still reported as RECOGNIZED_NOT_YET_SUPPORTED: Profile slots, Theme Store slots. The Devices screen has no hideable rows (its three row groups are locked or hold the session list).

## Wired in the last Phase 2 batch (schemaMinor 1)

- `components.cell.paddingStartDp` - start inset of TextCell, TextSettingsCell and TextCheckCell (text and icon). Absent = Telegram's own.
- `components.cell.iconSizeDp` - icon box of TextCell (scaled to fit).
- `components.cell.pressedColor` - selector colour of lists that use sections (`#AARRGGBB`).
- `components.fab.sizeDp|radiusDp` - the Chats compose button only. Sub buttons and other screens are untouched.
- `components.search.background|radiusDp|hintColor|iconColor` - `SearchField` (story viewers and trending stickers). The Chats search field stays hidden and is not touched.
- `ux.ripple` - false removes the press highlight on section lists.
- `ux.animationScale` (0..2) - multiplies the system animator scale and `KuroUi.anim` durations.

## Contacts rows

`UserCell` (the Contacts list rows) reads `components.cell.titleSizeSp|titleWeight|dividerVisible|dividerInsetDp|dividerColor`. Only the Contacts list opts in; other lists that use UserCell (member pickers, admin lists) are unchanged. The Chats list rows (`DialogCell`) are not styled: their text paints are shared across the app, so a per-skin size would leak into other screens.

## ux.transition

Screen open/close animation: `default` (fade + 48 dp slide, Telegram's own), `fade` (fade only), `slide` (96 dp slide, no fade), `none` (instant). Swipe-back and message-preview animations are not affected. With the token absent the animation is exactly Telegram's.
