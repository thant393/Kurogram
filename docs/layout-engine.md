# Kurogram Layout Engine (design)

Goal: a theme can change not only colours but the **layout** of Telegram's screens (e.g. a Messenger-style
chat list), while every feature stays Telegram's. Layouts live in the store/DB and are downloaded on demand.
No layout is hard-coded in the app; the app only contains a generic engine.

## Principles
1. **Data, never code.** A layout is JSON. The app never downloads or executes code (no dex, no scripts):
   Play/Android policy, security and crash risk. The JSON is interpreted by a fixed, sandboxed set of primitives.
2. **Telegram's behaviour is untouched.** The engine only decides *where* things go and *how* they are drawn.
   Taps, swipes, folders, stories, calls, search keep calling Telegram's own code (named "capabilities").
3. **Always a way back.** Unknown slot/capability/primitive -> ignored. Invalid layout -> "classic" (Telegram's own).
   `engine_level` gate like skins (`min_app_version`).
4. **Skin and layout stay separate.** Layout = structure, skin = colours/radii/type. Any layout works with any skin.

## What a layout is
A layout document has one entry per *screen template*. Each template exposes named **slots** that the app
fills with Telegram data; the layout positions, sizes and styles them.

```json
{
  "schemaVersion": 1,
  "meta": {"id": "bubble-style", "name": "Bubble"},
  "chatList": {
    "row": {
      "heightDp": 72,
      "avatar": {"sizeDp": 56, "shape": "circle", "onlineDot": {"sizeDp": 14, "color": "success"}, "lastActive": "badge"},
      "title": {"weight": "medium", "sizeSp": 16, "unreadWeight": "bold"},
      "preview": {"lines": 1, "sizeSp": 14, "color": "textSecondary", "unreadColor": "textPrimary"},
      "meta": {"time": "top-right", "seenAvatar": "bottom-right", "unreadDot": "right"},
      "pinned": "icon-left-of-time",
      "dividers": false
    },
    "header": {"title": "text", "left": ["menu"], "right": ["compose", "search"]},
    "storyRail": {"enabled": true, "avatarSizeDp": 64, "showCreate": true},
    "folders": {"style": "chips", "position": "below-rail", "collapsible": true},
    "specialRows": {"archive": "row", "requests": "row"}
  },
  "bottomTabs": {"items": ["chats", "people", "settings"], "labels": true, "style": "flat"},
  "chat": {"header": {"avatarLeft": true, "actions": ["call", "video", "more"]},
           "input": {"style": "pill", "leading": ["plus"], "trailing": ["emoji", "mic"]}}
}
```

Slots / capabilities are the app's vocabulary (a fixed registry): `menu, compose, search, archive,
folders, stories, notes, call, video, more, plus, emoji, mic, attach ...`. A layout can only *use* these.

## Messenger-style mapping (from the reference screenshot)
| Messenger element | Telegram feature it maps to | Notes |
|---|---|---|
| Header: hamburger + title, compose, search | Telegram drawer, "new message", search | compose = new chat sheet; keep Telegram search |
| Story rail with "Create story" and online dots | Telegram Stories | hide rail when the user has stories disabled |
| Notes bubble above avatar | (none) | omit; Telegram has no notes |
| Row: big avatar, online dot, "10m" last-seen badge | online status / last seen | badge only when status is known |
| Row: bold title + preview, time right, seen-avatar | unread state, read receipts | unread = bold + dot; seen avatar only in 1:1 |
| "Message requests" row | Archived chats | same row style, opens Telegram archive |
| Bottom tabs Chats / People / Notifications | Chats / Contacts / Settings (Profile in drawer) | Telegram has no notification feed: use Settings |
| Floating action | Telegram write button | optional |

### UX decisions (recommended)
- **Folders** are Telegram's core navigation, Messenger has none: show them as a horizontal chip row directly under
  the header and above the list; stories rail sits above the chips and collapses on scroll to give the list room.
- **Pinned chats** stay at the top with a small pin glyph next to the time (not a separate section).
- **Unread** = bold title + accent dot; muted chats dim the dot to grey. Mention/reaction badges keep Telegram's counters.
- **Swipe actions** (mute, pin, archive, delete) unchanged: users keep their muscle memory.
- **Search** opens Telegram's full search; the header icon is the only entry point.
- **Bottom tabs**: 3 tabs; Profile moves into the drawer. Tab bar height 56dp, labels on, accent for selected.
- **Touch targets** >= 48dp; avatar 56dp; row height 72dp; text >= 14sp; AA contrast checked by the validator
  against the active skin's colours.
- Accessibility: layouts may not remove capabilities required for basic use (open chat, back, send); validator rejects them.

## Engine (what the app contains)
- `LayoutSpec` (parse + validate, same rules mirrored in the backend like `RULES_V2`), `LayoutManager`
  (active layout, listeners, fallback to classic), registry of capabilities.
- **Slot-based chat row**: one generic cell that reads the row spec. This is the main engineering cost: Telegram's
  `DialogCell` is drawn imperatively; the plan is a new `SlotDialogCell` that reuses DialogCell's data/state
  (title, message, time, counters, status) and only does positioning/drawing from the spec.
- Header and bottom tabs: thin renderers that place the existing Telegram controls per spec.
- Chat input: last (highest risk). Reuses `ChatActivityEnterView` logic; only skin + visible controls change.

## Store / DB
- New pack `type: "layout"` (same collections: packs/versions, same hash/ETag/rollback). A skin may reference a layout
  by id (`screens.layout.pack`) or embed a small one. Apply = download layout + (optional) skin, like skins today.
- `engine_level` for layouts is separate from the skin engine level.

## Phases
- **L0 (done)** `screens.chats.layout` token in the skin schema (only `classic`).
- **L1** LayoutSpec + validator (app + backend) + LayoutManager, classic fallback, no visual change.
- **L2** SlotDialogCell + chat-list header/rail/folders/special rows (Messenger layout renders).
- **L3** Bottom tabs + drawer changes.
- **L4** Chat header, then input bar.
- **L5** Contacts/Settings/Profile templates; more layouts (e.g. "Telegram 2016").
Each phase is built (CI) and checked on device with screenshots before the next one.

## Risks
Telegram upstream merges (keep changes in new files, hook at few points); DialogCell performance with many rows
(measure on armeabi-v7a, cache layouts per row type); Burmese/RTL text shaping and long names; story rail needs
Telegram's stories API availability; layouts must degrade gracefully on small/large font scales.


## Scope of a theme (decided)

Like a phone theme (lock screen, home screen, icons, status bar) a Kurogram theme only reaches a fixed list of places:

- **Full structure (JSON):** Chats list, Contacts, Settings, Profile, Chat. Everything a layout can place, show or hide on these screens is a JSON key; new looks are new JSON, not new app code. Each key is a primitive built once in the app and reused by every layout.
- **Colour only:** every other screen. They keep Telegram's structure and only take the skin colours (header, main background, cards, text, accents).
- **Not themeable:** wallpaper, font, icon pack. The app has none, so there is nothing to ship or to break.

Rules that follow from this: a layout never adds a feature, it only arranges what Telegram or Kurogram already has; a skin that carries a `layout` section replaces the previous skin's layout, and a skin without one clears it.

### Layout is never a separate theme
There are no layout-only or skin-only themes. A theme is one skin document; its optional top-level `layout` object
carries the arrangement. A missing `layout`, `"classic"` or `{}` means Telegram's own layout. Applying any theme
always resets the previous layout first.

### A theme is one complete document
Only `type: "skin"` exists (no `colors` themes). The server accepts a theme only as schemaVersion 2 with every standard
value filled (all tokens, components, screens, chat, effects) and an explicit `layout` (object, or `"classic"`).
`docs/theme-template.json` lists every key with its default; copy it and change values.

### Chats list engine (JSON-driven)
`chatList.mode` = `list` | `grid`; grid: `chatList.grid.columns` (2-5), `avatarDp`, `showName`, `showBadge`.
List rows: `chatList.row.heightDp`, `row.avatar.sizeDp`, `row.preview.show`, `row.meta.time=hidden`.
Bottom bar: `bottomTabs.items/labels/labelSizeSp/indicator(none|pill)/border/style`.
Grid is a tile mode of DialogCell, so open/long-press/swipe/selection stay Telegram's own.

### Day / night
A theme without a `night` section follows Telegram: day uses the theme's colours, night shows Telegram's own night colours
(layout stays). A `night` section (only what differs from day) is used in night mode. `"appearance": "light"|"dark"` pins
one palette whatever Telegram does; default is `"auto"`.

### Settings and Contacts keys
`settings.header` = centered | cover; `settings.items` = list of row names to keep (same names as drawer.items).
`contacts.mode` = list | grid; `contacts.grid.columns/avatarDp`; `contacts.row.avatarSizeDp/heightDp/status`; `contacts.sort` = name | time.
`docs` demo: Grid Demo theme.
