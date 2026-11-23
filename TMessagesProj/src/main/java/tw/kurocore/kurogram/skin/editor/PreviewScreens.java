package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Studio's phone draws for the app's list-style screens (Kuro Settings, Number Finder, Privacy, Devices ...).
 * Pure data, no Android classes: the painter in BuilderPreviewView turns each row into pixels with the theme being edited.
 * All names, numbers and addresses are made up.
 */
public final class PreviewScreens {

    public enum Kind {
        HEADER, ROW, TOGGLE, RADIO, LINK, PERSON, DEVICE, SLIDER, INFO, HERO, FIELD, BUTTON, CARD_BUTTON, GAP,
        BUBBLES, SWATCHES, LIST_TILES, APP_ICONS, TEXT_ROW, LOGO, MODE_SLIDER
    }

    public static final class Item {
        public final Kind kind;
        public final String title;
        public String sub, value, icon, hide;
        public boolean on, star, chevron, menu, plain, error, badge, accent, split, handle, lock;
        public String chipText;
        public int tile; // colour of an icon tile (0 = none)

        Item(Kind kind, String title) {
            this.kind = kind;
            this.title = title;
        }

        Item sub(String s) {
            sub = s;
            return this;
        }

        Item value(String s) {
            value = s;
            return this;
        }

        Item icon(String s) {
            icon = s;
            return this;
        }

        Item hide(String s) {
            hide = s;
            return this;
        }

        Item on(boolean b) {
            on = b;
            return this;
        }

        Item star() {
            star = true;
            return this;
        }

        Item chevron() {
            chevron = true;
            return this;
        }

        Item menu() {
            menu = true;
            return this;
        }

        Item error() {
            error = true;
            return this;
        }

        Item tile(int c) {
            tile = c;
            return this;
        }

        Item badge() {
            badge = true;
            return this;
        }

        Item plainCentered() {
            plain = true;
            return this;
        }

        Item accentTitle() {
            accent = true;
            return this;
        }

        Item split() {
            split = true;
            return this;
        }

        Item handle() {
            handle = true;
            return this;
        }

        Item lock() {
            lock = true;
            return this;
        }

        Item chip(String c) {
            chipText = c;
            return this;
        }
    }

    private static Item h(String t) {
        return new Item(Kind.HEADER, t);
    }

    private static Item row(String t) {
        return new Item(Kind.ROW, t);
    }

    private static Item tog(String t) {
        return new Item(Kind.TOGGLE, t);
    }

    private static Item info(String t) {
        return new Item(Kind.INFO, t);
    }

    private static Item gap() {
        return new Item(Kind.GAP, "");
    }

    private static Item person(String n, String s) {
        return new Item(Kind.PERSON, n).sub(s);
    }

    private static Item link(String t) {
        return new Item(Kind.LINK, t);
    }

    private static final int GREEN = 0xFF34C759, ORANGE = 0xFFFF9500, BLUE = 0xFF3390EC, PURPLE = 0xFFAF52DE, TEAL = 0xFF30B0C7;

    private static final String[] STORE_KIDS = {"themeStore", "themeStore.stickers", "themeStore.profile"};

    /** The screens that open from inside {@code scene} (empty = none): shown as a small strip over the phone. Only Kuro Themes has any for now. */
    public static String[] children(String scene) {
        if ("themeStore".equals(scene) || scene.startsWith("themeStore.")) {
            return STORE_KIDS;
        }
        return new String[0];
    }

    /** The screen a strip belongs to, or null when the scene has no sub-screens. */
    public static String parentOf(String scene) {
        return children(scene).length == 0 ? null : "themeStore";
    }

    /** The screens that have a detailed drawing. */
    public static boolean has(String scene) {
        return screen(scene) != null;
    }

    public static String title(String scene) {
        switch (scene) {
            case "kuroSettings": return "";
            case "numberFinder": return "Number Finder";
            case "specialContacts": return "Special Contacts";
            case "onlineContacts": return "Online Contacts";
            case "contactChanges": return "Contacts Changes";
            case "rows.profile": return "Account";
            case "chatSettings": return "Chat Settings";
            case "rows.privacy": return "Privacy and Security";
            case "rows.notifications": return "Notifications and Sounds";
            case "rows.dataStorage": return "Data and Storage";
            case "folders": return "Chat Folders";
            case "devices": return "Devices";
            case "powerSaving": return "Power Saving";
            case "language": return "Language";
            case "themeStore.stickers": return "Kuro Theme";
            case "themeStore.profile": return "Kuro Theme";
            default: return null;
        }
    }

    /** Which top-bar actions a screen shows: "search", "trash", "more". */
    public static String actions(String scene) {
        switch (scene) {
            case "kuroSettings":
            case "language":
                return "search";
            case "contactChanges":
                return "trash";
            case "chatSettings":
                return "more";
            default:
                return "";
        }
    }

    public static List<Item> screen(String scene) {
        List<Item> s = new ArrayList<>();
        switch (scene) {
            case "kuroSettings":
                s.add(new Item(Kind.LOGO, "Kurogram").sub("12.10.6 (7112)"));
                s.add(row("General").icon("ui_grid"));
                s.add(row("Appearance").icon("kuro_theme"));
                s.add(row("Chat").icon("ui_chat"));
                s.add(row("Passcode").icon("ui_lock"));
                s.add(row("Other").icon("ui_star"));
                s.add(gap());
                s.add(row("Official channel").icon("ui_megaphone").value("@KuroUpdates"));
                s.add(row("Support Group").icon("ui_people").value("@SupportGroup"));
                s.add(row("View source code").icon("ui_link").value("GitHub"));
                s.add(row("Donate").icon("ui_heart").sub("Support our development."));
                break;
            case "numberFinder":
                s.add(info("Find a contact by their phone number, with their country code.").plainCentered());
                s.add(new Item(Kind.FIELD, "Country").value("Choose a country").chevron());
                s.add(new Item(Kind.FIELD, "Phone Number").value("Phone number").sub("+"));
                s.add(new Item(Kind.BUTTON, "Search"));
                break;
            case "specialContacts":
                s.add(tog("Enable Special Contacts").on(true));
                s.add(info("This feature helps by notifying you when your selected contacts do some specific actions. Like reading messages, changing profile details or going online."));
                s.add(tog("Enable for Background").on(true));
                s.add(info("Use Special Contacts while app is closed.\nKeep in mind that this might increase your phone battery usage."));
                s.add(link("Add New Contact"));
                s.add(person("Alex Morgan", "No new action").menu());
                s.add(person("Sam Rivera", "No new action").menu());
                s.add(person("Jamie Lee", "No new action").menu());
                s.add(person("Taylor Quinn", "No new action").menu());
                s.add(person("Robin Hale", "No new action").menu());
                s.add(person("Casey Ford", "No new action").menu());
                break;
            case "onlineContacts":
                s.add(person("Alex Morgan", "online"));
                s.add(person("Jamie Lee", "online"));
                s.add(person("Robin Hale", "online"));
                s.add(person("Casey Ford", "online"));
                break;
            case "contactChanges":
                s.add(person("Alex Morgan", "changed his/her Profile Photo.").value("11:49"));
                s.add(person("Sam Rivera", "changed his/her Profile Photo.").value("11:49"));
                s.add(person("Jamie Lee", "changed his/her Profile Photo.").value("Sat"));
                s.add(person("Taylor Quinn", "changed his/her name from \"Tay\" to \"Taylor Quinn\".").value("Fri"));
                s.add(person("Robin Hale", "changed his/her Profile Photo.").value("Fri"));
                s.add(person("Casey Ford", "changed his/her Profile Photo.").value("Thu"));
                s.add(person("Alex Morgan", "changed his/her Profile Photo.").value("Thu"));
                s.add(person("Sam Rivera", "changed his/her name from \"Sam\" to \"Sam Rivera\".").value("Wed"));
                break;
            case "rows.profile":
                s.add(h("Your name"));
                s.add(new Item(Kind.TEXT_ROW, "Alex"));
                s.add(new Item(Kind.TEXT_ROW, "Morgan"));
                s.add(gap());
                s.add(new Item(Kind.TEXT_ROW, "Everything is Temporary").value("47").hide("bio"));
                s.add(info("Choose who can see your bio in Settings."));
                s.add(h("Your Info"));
                s.add(row("+00 000 000 000").sub("Tap to change phone number").icon("ui_phone").tile(GREEN).hide("phone"));
                s.add(row("@username").sub("Username").icon("ui_at").tile(ORANGE).hide("username"));
                s.add(row("Jan 1, 2000").sub("Birthday").icon("ui_cake").tile(BLUE).hide("birthday"));
                s.add(info("Choose who can see your birthday in Settings."));
                s.add(row("Personal channel").value("Add").icon("ui_megaphone").tile(ORANGE));
                s.add(row("Chat Automation").icon("ui_ai").tile(PURPLE).badge());
                s.add(info("Add a bot to answer messages on your behalf."));
                s.add(link("Add Account").icon("ui_userplus"));
                s.add(link("Log Out").icon("ui_logout").error());
                break;
            case "chatSettings":
                s.add(h("Message text size"));
                s.add(new Item(Kind.SLIDER, "").value("16").on(false).sub("0.25"));
                s.add(new Item(Kind.BUBBLES, ""));
                s.add(link("Change Chat Wallpaper").icon("ui_wallpaper"));
                s.add(row("Change Name Color").icon("ui_namecolor").value("Alex").chevron().accentTitle());
                s.add(gap());
                s.add(h("Color theme"));
                s.add(new Item(Kind.SWATCHES, ""));
                s.add(link("Switch to Night Mode").icon("ui_night"));
                s.add(link("Browse Themes").icon("ui_brush"));
                s.add(gap());
                s.add(h("Message corners"));
                s.add(new Item(Kind.SLIDER, "").value("17").sub("1"));
                s.add(gap());
                s.add(h("Chat list view"));
                s.add(new Item(Kind.LIST_TILES, ""));
                s.add(gap());
                s.add(h("App Icon"));
                s.add(new Item(Kind.APP_ICONS, ""));
                break;
            case "rows.privacy":
                s.add(h("Security"));
                s.add(row("Two-Step Verification").icon("settings_privacy").value("On"));
                s.add(row("Auto-Delete Messages").icon("ui_timer").value("Off"));
                s.add(row("Passcode Lock").icon("ui_lock").value("Off"));
                s.add(row("Passkeys").icon("ui_key").value("Off"));
                s.add(row("Login Email").icon("ui_mail").value("a•••@example.com"));
                s.add(row("Blocked Users").icon("ui_hand").value("12"));
                s.add(row("Devices").icon("settings_devices").value("3"));
                s.add(info("Review the list of devices where you are logged in to your Telegram account."));
                s.add(h("Privacy"));
                s.add(row("Phone Number").value("Nobody").hide("phoneNumber"));
                s.add(row("Last Seen & Online").value("Nobody").hide("lastSeen"));
                s.add(row("Profile Photos").value("Everybody").hide("profilePhoto"));
                s.add(row("Forwarded Messages").value("Nobody").hide("forwards"));
                s.add(row("Calls").value("Everybody").hide("calls"));
                s.add(row("Voice Messages").value("Everybody").star().hide("voices"));
                s.add(row("Messages").value("Everybody").star().hide("noncontacts"));
                s.add(row("Birthday").value("Everybody").hide("birthday"));
                s.add(row("Gifts").value("Everybody").hide("gifts"));
                s.add(row("Bio").value("Nobody").hide("bio"));
                break;
            case "rows.notifications":
                s.add(h("Notifications for chats"));
                s.add(tog("Private Chats").sub("On, 3 exceptions").icon("ui_user").on(true).split());
                s.add(tog("Groups").sub("Off, 4 exceptions").icon("ui_people").split());
                s.add(tog("Channels").sub("Off, 9 exceptions").icon("ui_megaphone").split());
                s.add(tog("Stories").sub("Off, 2 automatic exceptions").icon("ui_stories").split());
                s.add(tog("Reactions").sub("Messages, Stories").icon("ui_heart").on(true).split().hide("reactions"));
                s.add(gap());
                s.add(h("Calls"));
                s.add(row("Vibrate").value("Default").hide("callsVibrate"));
                s.add(row("Ringtone").value("Default").hide("callsRingtone"));
                s.add(gap());
                s.add(h("Badge Counter"));
                s.add(tog("Show Badge Icon").on(true));
                s.add(tog("Include Muted Chats"));
                s.add(tog("Count Unread Messages").on(true));
                s.add(gap());
                s.add(h("In-app notifications"));
                s.add(tog("In-App Sounds").on(true).hide("inappSound"));
                s.add(tog("In-App Vibrate").on(true).hide("inappVibrate"));
                s.add(tog("In-App Preview").on(true).hide("inappPreview"));
                s.add(tog("In-Chat Sounds").on(true).hide("inchatSound"));
                s.add(tog("In-App Pop on Screen").sub("Show pop-up notifications while in app.").on(true).hide("inappPriority"));
                break;
            case "rows.dataStorage":
                s.add(h("Disk and network usage"));
                s.add(row("Storage Usage").value("233.0 MB").icon("ui_storage").tile(BLUE).hide("storageUsage"));
                s.add(row("Data Usage").value("315.8 MB").icon("ui_bars").tile(GREEN).hide("dataUsage"));
                s.add(gap());
                s.add(h("Automatic media download"));
                s.add(tog("When using mobile data").sub("Disabled").split());
                s.add(tog("When connected to Wi-Fi").sub("Disabled").split());
                s.add(tog("When roaming").sub("Photos").on(true).split());
                s.add(link("Reset Auto-Download Settings").error());
                s.add(gap());
                s.add(h("Save to Gallery"));
                s.add(tog("Private Chats").sub("Off").split());
                s.add(tog("Groups").sub("Off").split());
                s.add(tog("Channels").sub("Off").split());
                s.add(gap());
                s.add(h("Streaming"));
                s.add(tog("Stream Videos and Audio Files").on(true));
                s.add(info("When possible, Telegram will start playing videos and music right away, without waiting for the files to fully download."));
                s.add(h("Calls"));
                s.add(row("Use Less Data for Calls").value("Only while roaming"));
                break;
            case "folders":
                s.add(new Item(Kind.HERO, "folder").sub("Create folders for different groups of chats and quickly switch between them."));
                s.add(h("Chat Folders"));
                s.add(new Item(Kind.TEXT_ROW, "All Chats").handle());
                s.add(new Item(Kind.TEXT_ROW, "Personal").handle().menu());
                s.add(new Item(Kind.TEXT_ROW, "Channels").handle().menu());
                s.add(new Item(Kind.TEXT_ROW, "Groups").handle().menu());
                s.add(new Item(Kind.TEXT_ROW, "Bots").handle().menu());
                s.add(new Item(Kind.TEXT_ROW, "Work").handle().menu());
                s.add(new Item(Kind.TEXT_ROW, "Movies").handle().menu());
                s.add(link("Create New Folder").icon("ui_plus"));
                s.add(gap());
                s.add(tog("Show Folder Tags").sub("").lock());
                s.add(info("Subscribe to Telegram Premium to display folder names for each chat in the chat list."));
                break;
            case "devices":
                s.add(new Item(Kind.HERO, "laptop").sub("Log into Telegram Desktop or Telegram Web by scanning a QR code."));
                s.add(new Item(Kind.CARD_BUTTON, "Add Device").icon("ui_qr"));
                s.add(gap());
                s.add(h("This device"));
                s.add(new Item(Kind.DEVICE, "Phone Model X").sub("Kurogram 12.10.6").value("City, Country • online"));
                s.add(link("Terminate All Other Sessions").icon("ui_hand").error());
                s.add(info("Logs out all devices except for this one."));
                s.add(h("Active sessions"));
                s.add(new Item(Kind.DEVICE, "Phone Model X").sub("Telegram Android 13.2.2").value("City, Country • 16:07"));
                s.add(new Item(Kind.DEVICE, "Phone Model X").sub("Telegram Android X 0.29.1").value("City, Country • 15:40"));
                s.add(new Item(Kind.DEVICE, "Phone Model X").sub("Kurogram 12.10.6").value("City, Country • Thu"));
                s.add(new Item(Kind.DEVICE, "Phone Model X").sub("Kurogram 12.10.6").value("City, Country • Nov 14"));
                s.add(info("The official Telegram app is available for Android, iPhone, iPad, Windows, macOS and Linux."));
                s.add(h("Automatically terminate old sessions"));
                s.add(row("If inactive for").value("1 year"));
                break;
            case "powerSaving":
                s.add(h("Power Saving Mode").chip("OFF"));
                s.add(new Item(Kind.MODE_SLIDER, "When below 10%"));
                s.add(info("Automatically reduce power usage and animations when your battery is below 10%."));
                s.add(h("Power saving options"));
                s.add(tog("Animated Stickers").icon("ui_sticker").value("0/2").chevron().split());
                s.add(tog("Animated Emoji").icon("ui_smile").value("0/3").chevron().split());
                s.add(tog("Effects in Chats").icon("ui_chatdots").value("1/5").chevron().on(true).split());
                s.add(tog("Animations in Calls").icon("ui_calls").split());
                s.add(tog("Autoplay Videos").icon("ui_video"));
                s.add(tog("Autoplay GIFs").icon("ui_gif").on(true));
                s.add(tog("Particles").icon("ui_star").on(true));
                s.add(gap());
                s.add(tog("Enable Smooth Transitions").on(true));
                s.add(info("You can disable animated transitions between different sections of the app."));
                break;
            case "language":
                s.add(h("Translate Messages"));
                s.add(row("Kurogram Settings"));
                s.add(gap());
                s.add(h("Language"));
                s.add(new Item(Kind.RADIO, "English").sub("English").on(true));
                s.add(new Item(Kind.RADIO, "日本語").sub("Japanese"));
                s.add(new Item(Kind.RADIO, "العربية").sub("Arabic"));
                s.add(new Item(Kind.RADIO, "Беларуская").sub("Belarusian"));
                s.add(new Item(Kind.RADIO, "Català").sub("Catalan"));
                s.add(new Item(Kind.RADIO, "简体中文").sub("Chinese (Simplified)"));
                s.add(new Item(Kind.RADIO, "Hrvatski").sub("Croatian"));
                s.add(new Item(Kind.RADIO, "Čeština").sub("Czech"));
                s.add(new Item(Kind.RADIO, "Nederlands").sub("Dutch"));
                s.add(new Item(Kind.RADIO, "Suomi").sub("Finnish"));
                s.add(new Item(Kind.RADIO, "Français").sub("French"));
                s.add(new Item(Kind.RADIO, "Deutsch").sub("German"));
                break;
            default:
                return null;
        }
        return s;
    }
}
