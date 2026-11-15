#!/usr/bin/env python3
"""Seeds / extends docs/skin-editor.catalog.json from docs/skin-rules.manifest.json.

The catalog says how the Theme Builder presents every engine rule (group, basic/advanced, which component a tap
opens it from, words in each language). It is edited by hand afterwards; this script only ADDS entries for rules
that have none (new engine rules) and never changes an entry that exists.

  python3 Tools/gen_catalog.py            add entries for rules that are missing
  python3 Tools/gen_catalog.py --init     (re)create the file from scratch (loses hand edits)

Tools/check_catalog.py is the gate: it fails when a rule has no entry.
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, "docs/skin-rules.manifest.json")
CATALOG = os.path.join(ROOT, "docs/skin-editor.catalog.json")

LANGS = ["en", "my"]

SCREENS = {
    "chats": ("Chats", "ချက်တ်စာရင်း"), "chat": ("Chat", "ချက်တ်ဖန်သားပြင်"), "contacts": ("Contacts", "ဆက်သွယ်ရန်စာရင်း"),
    "people": ("People", "လူများ"), "unread": ("Unread", "မဖတ်ရသေးသော"), "settings": ("Settings", "ဆက်တင်များ"),
    "kuroSettings": ("Kuro settings", "Kuro ဆက်တင်များ"), "profile": ("Profile", "ပရိုဖိုင်"),
    "themeStore": ("Theme store", "Theme စတိုး"), "privacy": ("Privacy", "ကိုယ်ရေးလုံခြုံမှု"),
    "notifications": ("Notifications", "အကြောင်းကြားချက်များ"), "dataStorage": ("Data and storage", "ဒေတာနှင့် သိုလှောင်မှု"),
    "devices": ("Devices", "စက်များ"),
}

COMPONENTS = {
    "global": ("Whole app", "app တစ်ခုလုံး"), "screenBackground": ("Screen background", "မျက်နှာပြင်နောက်ခံ"),
    "appBar": ("Top bar", "အပေါ်ဘား"), "bottomTabs": ("Bottom tabs", "အောက်ခြေ tab များ"),
    "bottomTabs.item": ("Bottom tab item", "အောက်ခြေ tab တစ်ခု"), "fab": ("Compose button", "ရေးသားခလုတ်"),
    "drawer": ("Side menu", "ဘေးမီနူး"), "card": ("Card", "ကတ်"), "section": ("Section", "အပိုင်း"),
    "cell": ("List row", "စာရင်းအတန်း"), "switch": ("Switch", "ခလုတ်ဖွင့်ပိတ်"), "button": ("Button", "ခလုတ်"),
    "input": ("Input field", "ရိုက်ထည့်ရန်ကွက်"), "dialog": ("Dialog", "ဒိုင်ယာလော့"), "search": ("Search field", "ရှာဖွေရန်ကွက်"),
    "session": ("Device row", "စက်အတန်း"),
    "chatRow": ("Chat row", "ချက်တ်အတန်း"), "chatRow.avatar": ("Chat avatar", "ချက်တ်ပရိုဖိုင်ပုံ"),
    "chatRow.title": ("Chat title", "ချက်တ်အမည်"), "chatRow.preview": ("Message preview", "မက်ဆေ့ချ်အကျဉ်း"),
    "chatRow.time": ("Time and status", "အချိန်နှင့် အခြေအနေ"), "chatRow.badge": ("Unread badge", "မဖတ်ရသေးသော အရေအတွက်"),
    "chatList.header": ("Chat list header", "ချက်တ်စာရင်း ခေါင်းပိုင်း"), "chatList.storyRail": ("Story row", "Story တန်း"),
    "chatList.folders": ("Folders bar", "Folder ဘား"), "chatList.search": ("Chat search", "ချက်တ်ရှာဖွေခြင်း"),
    "chatList.grid": ("Chat grid", "ချက်တ်ဇယားကွက်"),
    "settingsRow": ("Settings row", "ဆက်တင်အတန်း"), "settingsHeader": ("Settings header", "ဆက်တင် ခေါင်းပိုင်း"),
    "contactRow": ("Contact row", "ဆက်သွယ်ရန်အတန်း"), "contactsGrid": ("Contacts grid", "ဆက်သွယ်ရန် ဇယားကွက်"),
    "peopleRail": ("People row", "လူများတန်း"), "profileHeader": ("Profile header", "ပရိုဖိုင် ခေါင်းပိုင်း"),
    "infoCard": ("Profile info card", "ပရိုဖိုင်အချက်အလက်ကတ်"), "themeCard": ("Theme card", "Theme ကတ်"),
    "bubble.in": ("Incoming bubble", "ဝင်လာသော မက်ဆေ့ချ်ဘူး"), "bubble.out": ("Outgoing bubble", "ပို့သော မက်ဆေ့ချ်ဘူး"),
    "chatHeader": ("Chat header", "ချက်တ် ခေါင်းပိုင်း"), "chatInput": ("Message input", "မက်ဆေ့ချ်ရိုက်ကွက်"),
    "datePill": ("Date label", "ရက်စွဲအမှတ်အသား"), "chatPanel": ("Chat menu and panels", "ချက်တ်မီနူးနှင့် panel များ"),
}

GROUPS = {
    "structure": ("Theme", "Theme"), "colors": ("Colours", "အရောင်များ"), "gradient": ("Gradient", "အရောင်ဖျော့ဆင်း"),
    "typography": ("Text", "စာသား"), "spacing": ("Spacing", "အကွာအဝေး"), "shapes": ("Shapes", "ပုံသဏ္ဍာန်"),
    "elevation": ("Elevation", "အမြင့်အရိပ်"), "chat": ("Chat", "ချက်တ်"), "effects": ("Effects", "Effect များ"),
    "ux": ("Motion and touch", "လှုပ်ရှားမှုနှင့် ထိတွေ့မှု"), "assets": ("Assets", "ပုံနှင့်ဖောင့်များ"),
    "comp.appBar": ("Top bar", "အပေါ်ဘား"), "comp.navigationBar": ("Navigation bar", "လမ်းညွှန်ဘား"),
    "comp.bottomNavigation": ("Bottom navigation", "အောက်ခြေလမ်းညွှန်"), "comp.card": ("Cards", "ကတ်များ"),
    "comp.button": ("Buttons", "ခလုတ်များ"), "comp.input": ("Input fields", "ရိုက်ထည့်ကွက်များ"),
    "comp.dialog": ("Dialogs", "ဒိုင်ယာလော့များ"), "comp.cell": ("List rows", "စာရင်းအတန်းများ"),
    "comp.section": ("Sections", "အပိုင်းများ"), "comp.switch": ("Switches", "ဖွင့်ပိတ်ခလုတ်များ"),
    "comp.search": ("Search fields", "ရှာဖွေရန်ကွက်များ"), "comp.fab": ("Compose button", "ရေးသားခလုတ်"),
    "comp.session": ("Device rows", "စက်အတန်းများ"),
    "screen.chats": ("Chats screen", "ချက်တ်စာရင်း screen"), "screen.contacts": ("Contacts screen", "ဆက်သွယ်ရန် screen"),
    "screen.profile": ("Profile screen", "ပရိုဖိုင် screen"), "screen.settings": ("Settings screen", "ဆက်တင် screen"),
    "screen.themeStore": ("Theme store screen", "Theme စတိုး screen"), "screen.privacy": ("Privacy screen", "ကိုယ်ရေးလုံခြုံမှု screen"),
    "screen.notifications": ("Notifications screen", "အကြောင်းကြားချက် screen"),
    "screen.dataStorage": ("Data and storage screen", "ဒေတာနှင့် သိုလှောင်မှု screen"), "screen.devices": ("Devices screen", "စက်များ screen"),
    "layout.chatList": ("Chat list layout", "ချက်တ်စာရင်း ဖွဲ့စည်းပုံ"), "layout.bottomTabs": ("Bottom tabs layout", "အောက်ခြေ tab ဖွဲ့စည်းပုံ"),
    "layout.drawer": ("Side menu layout", "ဘေးမီနူး ဖွဲ့စည်းပုံ"), "layout.settings": ("Settings layout", "ဆက်တင် ဖွဲ့စည်းပုံ"),
    "layout.contacts": ("Contacts layout", "ဆက်သွယ်ရန် ဖွဲ့စည်းပုံ"), "layout.people": ("People layout", "လူများ ဖွဲ့စည်းပုံ"),
    "layout.themeStore": ("Theme store layout", "Theme စတိုး ဖွဲ့စည်းပုံ"), "layout.chat": ("Chat layout", "ချက်တ် ဖွဲ့စည်းပုံ"),
    "layout.profile": ("Profile rows", "ပရိုဖိုင်အတန်းများ"), "layout.privacy": ("Privacy rows", "ကိုယ်ရေးလုံခြုံမှုအတန်းများ"),
    "layout.notifications": ("Notification rows", "အကြောင်းကြားချက်အတန်းများ"),
    "layout.dataStorage": ("Data and storage rows", "ဒေတာနှင့် သိုလှောင်မှု အတန်းများ"),
}

# Words used when a label is built from a key. Burmese entries are a first draft and are flagged as not reviewed.
GLOSS = {
    "accent": "အဓိကအရောင်", "background": "နောက်ခံ", "surface": "မျက်နှာပြင်", "elevated": "မြင့်တက်သော", "text": "စာသား",
    "primary": "အဓိက", "secondary": "ဒုတိယ", "divider": "ပိုင်းခြားမျဉ်း", "icon": "အိုင်ကွန်", "error": "အမှား", "success": "အောင်မြင်မှု",
    "overlay": "ဖုံးအုပ်အလွှာ", "top": "အပေါ်", "middle": "အလယ်", "bottom": "အောက်", "direction": "ဦးတည်ချက်", "font": "ဖောင့်",
    "family": "မျိုးစုံ", "body": "ခန္ဓာကိုယ်စာသား", "title": "ခေါင်းစဉ်", "caption": "ကပ်ရှင်", "weight": "အထူ", "line": "စာကြောင်း",
    "height": "အမြင့်", "width": "အကျယ်", "size": "အရွယ်အစား", "radius": "ထောင့်ကွေးမှု", "card": "ကတ်", "button": "ခလုတ်",
    "input": "ရိုက်ထည့်ကွက်", "dialog": "ဒိုင်ယာလော့", "bubble": "မက်ဆေ့ချ်ဘူး", "in": "ဝင်လာ", "out": "ပို့", "send": "ပို့ရန်",
    "date": "ရက်စွဲ", "wallpaper": "နောက်ခံပုံ", "angle": "ထောင့်", "menu": "မီနူး", "reaction": "တုံ့ပြန်မှု", "panel": "panel",
    "header": "ခေါင်းပိုင်း", "tabs": "tab များ", "list": "စာရင်း", "style": "ပုံစံ", "mode": "မုဒ်", "grid": "ဇယားကွက်", "columns": "ကော်လံ",
    "avatar": "ပရိုဖိုင်ပုံ", "row": "အတန်း", "show": "ပြမည်", "hidden": "ဖျောက်ထားသော", "rows": "အတန်းများ", "items": "ပစ္စည်းများ",
    "unread": "မဖတ်ရသေး", "pinned": "ပင်တွဲထား", "preview": "မက်ဆေ့ချ်အကျဉ်း", "time": "အချိန်", "name": "အမည်", "badge": "အရေအတွက်အမှတ်အသား",
    "dividers": "ပိုင်းခြားမျဉ်းများ", "search": "ရှာဖွေရန်", "bar": "ဘား", "folders": "Folder များ", "position": "နေရာ", "labels": "အညွှန်းများ",
    "indicator": "အညွှန်း", "border": "ဘောင်", "blur": "မှုန်ဝါး", "glass": "ဖန်", "animations": "လှုပ်ရှားမှု", "motion": "လှုပ်ရှားမှု",
    "reduced": "လျှော့ထား", "ripple": "ထိတွေ့လှိုင်း", "transition": "screen ကူးပြောင်းမှု", "scale": "အတိုင်းအတာ", "animation": "လှုပ်ရှားမှု",
    "padding": "အတွင်းအကွာ", "start": "အစ", "gap": "ကွာဟချက်", "icon": "အိုင်ကွန်", "pressed": "ဖိထားသော", "color": "အရောင်", "hint": "အရိပ်အမြွက်",
    "section": "အပိုင်း", "switch": "ခလုတ်", "track": "လမ်းကြောင်း", "thumb": "လက်မ", "story": "Story", "rail": "တန်း", "create": "ဖန်တီးရန်",
    "collapse": "ခေါက်ရန်", "left": "ဘယ်", "right": "ညာ", "special": "အထူး", "archive": "သိမ်းဆည်းထား", "online": "အွန်လိုင်း",
    "dot": "အစက်", "last": "နောက်ဆုံး", "active": "အသုံးပြုချိန်", "seen": "ဖတ်ပြီး", "meta": "အချက်အလက်", "lines": "စာကြောင်းများ",
    "shape": "ပုံသဏ္ဍာန်", "unreadweight": "မဖတ်ရသေးသော စာလုံးအထူ", "sort": "စီရန်", "status": "အခြေအနေ", "accounts": "အကောင့်များ",
    "content": "အကြောင်းအရာ", "labels": "အညွှန်းများ", "creator": "ဖန်တီးသူ", "downloads": "ဒေါင်းလုဒ်များ", "chips": "chip များ",
    "layout": "ဖွဲ့စည်းပုံ", "group": "အုပ်စု", "icons": "အိုင်ကွန်များ", "descriptions": "ဖော်ပြချက်များ", "aspect": "အချိုး", "gap": "ကွာဟချက်",
    "session": "စက်", "fab": "ရေးသားခလုတ်", "transition": "ကူးပြောင်းမှု", "wallpaperColor": "နောက်ခံပုံအရောင်", "visible": "မြင်ရမည်",
    "inset": "အတွင်းကွာ", "weight": "အထူ", "normal": "ပုံမှန်", "radiusdp": "ထောင့်ကွေးမှု",
}


def words(key):
    key = re.sub(r"(Dp|Sp)$", "", key)
    parts = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", key).split()
    return [p.lower() for p in parts]


def unit(key):
    return {"Dp": " (dp)", "Sp": " (sp)"}.get(key[-2:], "") if key.endswith(("Dp", "Sp")) else ""


def label_en(segs):
    w = []
    for s in segs:
        w.extend(words(s))
    text = " ".join(w)
    return text[:1].upper() + text[1:] + unit(segs[-1])


def label_my(segs):
    out = []
    for s in segs:
        for w in words(s):
            out.append(GLOSS.get(w, w))
    return " ".join(out) + unit(segs[-1])


def classify(doc, kind):
    """-> group, level, phase, screens, targets for one document path."""
    p = doc.split(".")
    top = p[0]
    basic = False
    screens = ["*"]
    targets = ["global"]
    phase = 2
    if top == "tokens":
        sub = p[1]
        group = {"colors": "colors", "gradient": "gradient", "typography": "typography", "spacing": "spacing",
                 "shapes": "shapes", "elevation": "elevation"}[sub]
        leaf = p[-1]
        phase = 1 if sub in ("colors", "gradient") else 2
        if sub == "colors":
            basic = leaf in ("accent", "onAccent", "background", "surface", "textPrimary", "textSecondary")
            targets = {"background": ["screenBackground"], "surface": ["card", "section"], "surfaceElevated": ["card", "dialog"],
                       "textPrimary": ["chatRow.title", "cell"], "textSecondary": ["chatRow.preview", "chatRow.time"],
                       "divider": ["chatRow", "cell"], "icon": ["bottomTabs.item", "appBar"],
                       "accent": ["fab", "bottomTabs.item", "button"], "onAccent": ["fab", "button"],
                       "error": ["chatRow.badge"], "success": ["chatRow.badge"], "overlay": ["dialog"]}.get(leaf, ["global"])
        elif sub == "gradient":
            basic = True
            targets = ["screenBackground"]
        elif sub == "typography":
            basic = leaf == "bodySizeSp"
            targets = ["chatRow.time"] if leaf == "captionSizeSp" else ["chatRow.title", "chatRow.preview", "cell"]
        elif sub == "shapes":
            basic = leaf in ("cardRadiusDp", "buttonRadiusDp")
            targets = {"cardRadiusDp": ["card", "section"], "buttonRadiusDp": ["button"], "inputRadiusDp": ["input"],
                       "dialogRadiusDp": ["dialog"], "bubbleRadiusDp": ["bubble.in", "bubble.out"]}.get(leaf, ["global"])
        else:
            targets = ["card", "dialog"]
    elif top == "components":
        name = p[1]
        group = "comp." + name
        targets = {"appBar": ["appBar"], "navigationBar": ["bottomTabs"], "bottomNavigation": ["bottomTabs", "bottomTabs.item"],
                   "card": ["card"], "button": ["button"], "input": ["input"], "dialog": ["dialog"], "cell": ["cell"],
                   "section": ["section"], "switch": ["switch"], "search": ["search"], "fab": ["fab"], "session": ["session"]}[name]
        screens = {"fab": ["chats"], "session": ["devices"], "search": ["themeStore"], "button": ["themeStore"]}.get(name, ["*"])
    elif top == "screens":
        scr = p[1]
        group = "screen." + scr
        phase = 3
        screens = [scr]
        leaf = p[-1]
        basic = doc in ("screens.chats.listStyle", "screens.settings.groupStyle")
        if scr == "chats":
            targets = ["chatRow.avatar"] if leaf == "avatarSizeDp" else ["chatList.folders"] if leaf == "tabsRadiusDp" \
                else ["screenBackground"] if leaf == "background" else ["chatRow"]
        else:
            targets = {"contacts": ["contactRow"], "profile": ["profileHeader", "infoCard"], "settings": ["settingsRow"],
                       "themeStore": ["themeCard"]}.get(scr, ["screenBackground"])
            if leaf == "background":
                targets = ["screenBackground"]
    elif top == "chat":
        group, screens = "chat", ["chat"]
        leaf = p[-1]
        basic = leaf in ("bubbleIn", "bubbleOut", "bubbleInText", "bubbleOutText")
        targets = {"bubbleIn": ["bubble.in"], "bubbleInText": ["bubble.in"], "bubbleOut": ["bubble.out"], "bubbleOutText": ["bubble.out"],
                   "bubbleRadiusDp": ["bubble.in", "bubble.out"], "fontSizeSp": ["bubble.in", "bubble.out"], "sendButton": ["chatInput"],
                   "dateBackground": ["datePill"], "dateText": ["datePill"], "dateRadiusDp": ["datePill"], "headerRadiusDp": ["chatHeader"],
                   "inputRadiusDp": ["chatInput"], "panelRadiusDp": ["chatPanel"], "buttonRadiusDp": ["chatInput"], "menuRadiusDp": ["chatPanel"],
                   "reactionRadiusDp": ["chatPanel"]}.get(leaf, ["screenBackground"])
    elif top in ("effects", "ux"):
        group = top
        basic = doc == "effects.glass"
    elif top == "assets":
        group, phase = "assets", 5
    elif top == "layout":
        sec = p[1]
        group = "layout." + sec
        phase = 3
        rest = ".".join(p[2:])
        screens = {"chatList": ["chats"], "bottomTabs": ["*"], "drawer": ["chats"], "settings": ["settings"], "contacts": ["contacts"],
                   "people": ["people"], "themeStore": ["themeStore"], "chat": ["chat"], "profile": ["profile"], "privacy": ["privacy"],
                   "notifications": ["notifications"], "dataStorage": ["dataStorage"]}[sec]
        basic = doc in ("layout.chatList.row.heightDp", "layout.chatList.mode", "layout.bottomTabs.items", "layout.bottomTabs.labels", "layout.bottomTabs.style")
        if sec == "chatList":
            targets = ["chatRow"]
            for pre, t in (("row.avatar", ["chatRow.avatar"]), ("row.title", ["chatRow.title"]), ("row.preview", ["chatRow.preview"]),
                           ("row.meta.time", ["chatRow.time"]), ("row.meta", ["chatRow.time", "chatRow.badge"]), ("grid", ["chatList.grid"]),
                           ("header", ["chatList.header"]), ("storyRail", ["chatList.storyRail"]), ("folders", ["chatList.folders"]),
                           ("searchBar", ["chatList.search"]), ("mode", ["chatRow", "chatList.grid"])):
                if rest == pre or rest.startswith(pre + "."):
                    targets = t
                    break
        elif sec == "bottomTabs":
            targets = ["bottomTabs.item"] if rest == "items" else ["bottomTabs"]
        elif sec == "drawer":
            targets = ["drawer"]
        elif sec == "settings":
            targets = ["settingsHeader"] if rest in ("header", "accounts") else ["settingsRow"]
        elif sec == "contacts":
            targets = ["contactsGrid"] if rest.startswith("grid") or rest == "mode" else ["contactRow"]
        elif sec == "people":
            targets = ["peopleRail"]
        elif sec == "themeStore":
            targets = ["themeCard"]
        elif sec == "chat":
            targets = ["chatHeader"] if rest.startswith("header") else ["chatInput"]
        elif sec == "profile":
            targets = ["infoCard"]
        else:
            targets = ["section"]
    else:
        raise SystemExit("cannot classify " + doc)
    return group, ("basic" if basic else "advanced"), phase, screens, targets


def entry_for(doc, section, kind):
    group, level, phase, screens, targets = classify(doc, kind)
    segs = doc.split(".")
    # keep labels short: drop the leading namespace words that the group already says
    drop = {"tokens": 2, "components": 2, "screens": 2, "layout": 2}.get(segs[0], 1 if segs[0] in ("chat", "effects", "ux", "assets") else 0)
    short = segs[drop:] or segs[-1:]
    if segs[0] == "layout" and len(segs) > 3:
        short = segs[2:]
    en = label_en(short)
    my = label_my(short)
    return {
        "path": doc, "group": group, "level": level, "phase": phase, "built": False,
        "screens": screens, "targets": targets,
        "label": {"en": en, "my": my},
        "description": {"en": "", "my": ""},
        "reviewed": ["en"],
    }


STRUCTURE = [
    ("appearance", "structure", "basic", 1, ["*"], ["global"], "Light / Dark", "အလင်း / အမှောင်"),
    ("night", "structure", "advanced", 4, ["*"], ["global"], "Night colours", "ညအရောင်များ"),
    ("meta", "structure", "basic", 1, ["*"], ["global"], "Name and description", "အမည်နှင့် ဖော်ပြချက်"),
    ("layout", "structure", "advanced", 3, ["*"], ["global"], "Layout section", "ဖွဲ့စည်းပုံ အပိုင်း"),
    ("schemaVersion", "structure", "advanced", 1, ["*"], ["global"], "Format version", "ဖော်မတ်ဗားရှင်း"),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--init", action="store_true")
    args = ap.parse_args()
    man = json.load(open(MANIFEST, encoding="utf-8"))
    if os.path.exists(CATALOG) and not args.init:
        cat = json.load(open(CATALOG, encoding="utf-8"))
    else:
        cat = {"catalogVersion": 1, "languages": LANGS, "screens": {}, "components": {}, "groups": {}, "entries": []}
    for table, src in (("screens", SCREENS), ("components", COMPONENTS), ("groups", GROUPS)):
        for k, (en, my) in src.items():
            cat[table].setdefault(k, {"label": {"en": en, "my": my}, "reviewed": ["en"]})
    have = {e["path"] for e in cat["entries"]}
    added = 0
    for section, prefix in (("skin", ""), ("skinMinor1", ""), ("layout", "layout.")):
        for r in man[section]:
            path = r.split("|")[0]
            doc = prefix + path
            if path == "schemaMinor" or doc in have:
                continue
            cat["entries"].append(entry_for(doc, section, r.split("|")[1]))
            have.add(doc)
            added += 1
    for key, group, level, phase, screens, targets, en, my in STRUCTURE:
        if key not in have:
            cat["entries"].append({"path": key, "group": group, "level": level, "phase": phase, "built": False, "screens": screens,
                                   "targets": targets, "label": {"en": en, "my": my}, "description": {"en": "", "my": ""},
                                   "reviewed": ["en"], "note": "structure"})
            added += 1
    # schemaMinor is informational (the engine ignores its value); it gets an entry so nothing is unaccounted for
    if "schemaMinor" not in have:
        cat["entries"].append({"path": "schemaMinor", "group": "structure", "level": "advanced", "phase": 1, "built": False, "screens": ["*"],
                               "targets": ["global"], "label": {"en": "Format minor version", "my": "ဖော်မတ် minor ဗားရှင်း"},
                               "description": {"en": "", "my": ""}, "reviewed": ["en"],
                               "note": "set automatically by the Builder; never shown as a control"})
        added += 1
    with open(CATALOG, "w", encoding="utf-8") as f:
        json.dump(cat, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("catalog entries: %d (added %d)" % (len(cat["entries"]), added))


if __name__ == "__main__":
    sys.exit(main())
