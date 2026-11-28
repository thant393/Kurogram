package tw.kurocore.kurogram.skin.editor;

import java.util.HashMap;
import java.util.Map;

/** Words of the Builder's own screen (buttons, hints), English only. */
public final class BuilderText {
    private static final Map<String, String[]> T = new HashMap<>();

    private static void t(String key, String en) {
        T.put(key, new String[]{en});
    }

    static {
        t("title", "Theme Builder");
        t("undo", "Undo");
        t("redo", "Redo");
        t("basic", "Basic");
        t("advanced", "Advanced");
        t("scene", "Screen");
        t("scene.chats", "Chats");
        t("scene.contacts", "Contacts");
        t("scene.profile", "Profile");
        t("scene.themeStore", "Kuro Themes");
        t("scene.rows.privacy", "Privacy & Security");
        t("scene.rows.notifications", "Notifications");
        t("scene.rows.dataStorage", "Data and Storage");
        t("scene.rows.profile", "Account");
        t("scene.themeStore.stickers", "Kuro Stickers");
        t("scene.themeStore.profile", "Kuro Profile");
        t("strip.settings", "Settings");
        t("strip.themeStore", "Themes");
        t("strip.themeStore.stickers", "Stickers");
        t("strip.themeStore.profile", "Profile");
        t("scene.kuroSettings", "Kurogram Settings");
        t("scene.numberFinder", "Number Finder");
        t("scene.specialContacts", "Special Contacts");
        t("scene.onlineContacts", "Online Contacts");
        t("scene.contactChanges", "Contacts Changes");
        t("scene.chatSettings", "Chat Settings");
        t("scene.folders", "Chat Folders");
        t("scene.devices", "Devices");
        t("scene.powerSaving", "Power Saving");
        t("scene.language", "Language");
        t("contrast.title", "Readability");
        t("contrast.ok", "Text and background colours are easy to read.");
        t("contrast.low", "Hard to read");
        t("scene.settings", "Settings");
        t("scene.chat", "Conversation");
        t("scene.controls", "Components");
        t("scene.drawer", "Side menu");
        t("scene.rows", "Setting rows");
        t("unsupported", "Not supported by the app yet");
        t("none", "none");
        t("customize", "Customize this list");
        t("variant.day", "Day");
        t("variant.night", "Night");
        t("inherited", "from Day");
        t("look", "Look");
        t("blocks", "Screen blocks");
        t("blocks.use", "Build this screen from blocks");
        t("blocks.reset", "Back to the built-in screen");
        t("blocks.add", "Add a block");
        t("look.base", "Base theme");
        t("look.newStyle", "New style");
        t("look.newWay", "New colourway");
        t("look.limit", "Limit reached");
        t("value", "Value");
        t("live", "Real app preview");
        t("live.on", "Real app preview is ON");
        t("live.off", "Real app preview is off");
        t("simulated", "Simulated preview: tap a part to edit it");
        t("selected", "Selected");
        t("noSettings", "This part has no settings in the current mode.");
        t("later", "More settings for this part arrive in later updates.");
        t("reset", "Reset");
        t("done", "Done");
        t("cancel", "Cancel");
        t("next", "Publish");
        t("use", "Use on this phone");
        t("useConfirm", "Apply this theme to Kurogram on this phone now?");
        t("invalid", "The theme is not valid yet: ");
        t("applied", "Theme applied");
        t("exit", "Leave the Builder? Your draft is kept on this phone.");
        t("leave", "Leave");
        t("stay", "Stay");
        t("startOver", "Start over");
        t("name", "Theme name");
        t("appearance", "Light / Dark");
        t("auto", "Auto");
        t("light", "Light");
        t("dark", "Dark");
        t("notSet", "default");
        t("refused", "Change refused: ");
        t("allSettings", "All settings");
        t("globalColors", "Global Colors");
        t("moreGlobal", "More whole-app settings");
        t("close", "Close");
        t("locked", "Locked");
        t("screens.main", "Main screens");
        t("screens.fromSettings", "Opened from Settings");
        t("activities", "Activities");
        t("preview", "Preview");
        t("chip.colours", "Colours");
        t("chipHelp", "Tap a colour to change it. The parts it changes light up.");
        t("chip.0", "Accent");
        t("chip.1", "Background");
        t("chip.2", "Cards");
        t("chip.3", "Text");
        t("chip.4", "Top bar");
        t("chipAbout.0", "Changes: buttons, the selected tab, unread badges and links");
        t("chipAbout.1", "Changes: the page behind everything");
        t("chipAbout.2", "Changes: chat rows, settings rows and cards");
        t("chipAbout.3", "Changes: names and titles");
        t("chipAbout.4", "Changes: the bar at the top of each screen");
        t("screens.sub", "Sub-screens");
        t("search.other", "On another screen");
        t("section.appearance", "Appearance");
        t("section.layout", "Layout");
        t("section.typography", "Text");
        t("section.advanced", "Advanced");
        t("mode.edit", "Edit");
        t("mode.preview", "Preview");
        t("draft.saved", "Draft saved on this phone");
        t("draft.published", "Editing a published theme");
        t("layers", "Layers");
        t("screens", "Screens");
        t("search", "Find a component");
        t("search.hint", "Type a name, for example chat or tabs");
        t("search.none", "Nothing found");
        t("device", "Preview size");
        t("device.320", "Small phone (320)");
        t("device.360", "Standard phone (360)");
        t("device.412", "Large phone (412)");
        t("device.note", "Limited by this screen's width.");
        t("copyStyle", "Copy style");
        t("pasteStyle", "Paste style");
        t("copied", "Style copied");
        t("pasted", "Style applied");
        t("nothingCommon", "Nothing in common with this part.");
        t("nothingToCopy", "This part has no changes to copy.");
        t("resetComponent", "Reset this part");
        t("resetSection", "Reset this tab");
        t("resetAsk", "Remove your changes to this part? Other parts stay as they are.");
        t("resetDone", "Reset");
        t("warn.review", "Review");
        t("warn.fix", "Go to setting");
        t("warn.suggest", "Use suggested colour");
        t("warn.line", "is hard to read on");
        t("structural", "Not editable");
        t("tapHint", "Tap a part of the phone, or pick it from the list on the left");
        t("previewHint", "Preview mode: nothing is selected");
        t("sheet.none", "Nothing to edit here in this mode.");
        t("beginner", "Beginner");
        t("level", "Level");
        t("more", "More");
        t("rename", "Theme name");
        t("sidemenu", "Preview the side menu");
        t("menu.basic", "Show basic settings only");
        t("publish", "Publish");
        t("update", "Update");
        t("publishAsk", "Publish this theme to the store?");
        t("updateAsk", "Publish your changes as a new version?");
        t("publishing", "Please wait…");
        t("published", "Done! Your theme is in the store.");
        t("stylesWarn", "Theme saved, but its styles were not: ");
        t("needName", "Give your theme a name first (Whole app > Theme name).");
        t("removeStore", "Remove from store");
        t("removeAsk", "It will disappear from the store. People who already applied it keep it.");
        t("removed", "Removed from the store");
    }

    /** Every screen the preview can show, in the order of the picker. */
    public static final String[] SCENES = {"chats", "chat", "settings", "controls", "drawer", "contacts", "profile",
            "themeStore", "kuroSettings", "numberFinder", "specialContacts", "onlineContacts", "contactChanges", "rows.profile", "chatSettings",
            "rows.privacy", "rows.notifications", "rows.dataStorage", "folders", "devices", "powerSaving", "language"};

    private BuilderText() {
    }

    /** The language argument is kept for callers; the Builder is English only. */
    public static String get(String lang, String key) {
        String[] v = T.get(key);
        return v == null ? key : v[0];
    }

    public static boolean complete() {
        for (String[] v : T.values()) {
            if (v[0].isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
