package tw.kurocore.kurogram.skin.editor;

import java.util.HashMap;
import java.util.Map;

/** Words of the Builder's own screen (buttons, hints), English and Burmese from the start. Burmese is a first draft, not yet reviewed. */
public final class BuilderText {
    private static final Map<String, String[]> T = new HashMap<>();

    private static void t(String key, String en, String my) {
        T.put(key, new String[]{en, my});
    }

    static {
        t("title", "Theme Builder", "Theme ဖန်တီးသူ");
        t("undo", "Undo", "နောက်ပြန်");
        t("redo", "Redo", "ရှေ့ပြန်");
        t("basic", "Basic", "ရိုးရိုး");
        t("advanced", "Advanced", "အဆင့်မြင့်");
        t("scene", "Screen", "မျက်နှာပြင်");
        t("scene.chats", "Chats", "ချက်တ်များ");
        t("scene.people", "People", "လူများ");
        t("scene.contacts", "Contacts", "ဆက်သွယ်ရန်စာရင်း");
        t("scene.profile", "Profile", "ပရိုဖိုင်");
        t("scene.themeStore", "Theme store", "Theme စတိုး");
        t("scene.rows.privacy", "Privacy rows", "ကိုယ်ရေးလုံခြုံမှု အတန်းများ");
        t("scene.rows.notifications", "Notification rows", "အကြောင်းကြားချက် အတန်းများ");
        t("scene.rows.dataStorage", "Data and storage rows", "ဒေတာနှင့် သိုလှောင်မှု အတန်းများ");
        t("scene.rows.profile", "Profile rows", "ပရိုဖိုင် အတန်းများ");
        t("contrast.title", "Readability", "ဖတ်ရလွယ်မှု");
        t("contrast.ok", "Text and background colours are easy to read.", "စာသားနှင့် နောက်ခံအရောင်များ ဖတ်ရလွယ်ပါသည်။");
        t("contrast.low", "Hard to read", "ဖတ်ရခက်သည်");
        t("scene.settings", "Settings", "ဆက်တင်");
        t("scene.chat", "Conversation", "စကားပြောခန်း");
        t("scene.controls", "Components", "အစိတ်အပိုင်းများ");
        t("scene.drawer", "Side menu", "ဘေးမီနူး");
        t("scene.rows", "Setting rows", "ဆက်တင်အတန်းများ");
        t("unsupported", "Not supported by the app yet", "App က မထောက်ပံ့သေးပါ");
        t("none", "none", "မရှိ");
        t("customize", "Customize this list", "ဤစာရင်းကို ကိုယ်တိုင်ပြင်မည်");
        t("variant.day", "Day", "နေ့");
        t("variant.night", "Night", "ည");
        t("inherited", "from Day", "နေ့မှ ဆက်ခံ");
        t("look", "Look", "ပုံစံ");
        t("look.base", "Base theme", "အခြေခံ theme");
        t("look.newStyle", "New style", "ပုံစံသစ်");
        t("look.newWay", "New colourway", "အရောင်စုံသစ်");
        t("look.limit", "Limit reached", "ကန့်သတ်ချက်ပြည့်ပါပြီ");
        t("value", "Value", "တန်ဖိုး");
        t("live", "Real app preview", "App အစစ်ဖြင့် ကြည့်ရန်");
        t("live.on", "Real app preview is ON", "App အစစ်ဖြင့် ကြည့်နေသည်");
        t("live.off", "Real app preview is off", "App အစစ်ဖြင့် မကြည့်တော့ပါ");
        t("simulated", "Simulated preview: tap a part to edit it", "ပုံစံတူ preview: ပြင်လိုသောအပိုင်းကို နှိပ်ပါ");
        t("selected", "Selected", "ရွေးထားသည်");
        t("noSettings", "This part has no settings in the current mode.", "လက်ရှိ မုဒ်တွင် ဤအပိုင်းအတွက် ဆက်တင်မရှိပါ။");
        t("later", "More settings for this part arrive in later updates.", "ဤအပိုင်း၏ ဆက်တင်များ နောက်ထပ် update တွင် ထပ်ပါလာမည်။");
        t("reset", "Reset", "မူလပြန်");
        t("done", "Done", "ပြီးပြီ");
        t("cancel", "Cancel", "မလုပ်တော့ပါ");
        t("next", "Next", "ရှေ့ဆက်");
        t("use", "Use on this phone", "ဤဖုန်းတွင် သုံးမည်");
        t("useConfirm", "Apply this theme to Kurogram on this phone now?", "ဤ theme ကို ဤဖုန်းရှိ Kurogram တွင် အခု သုံးမလား?");
        t("invalid", "The theme is not valid yet: ", "Theme မမှန်သေးပါ: ");
        t("applied", "Theme applied", "Theme ကို သုံးလိုက်ပြီ");
        t("exit", "Leave the Builder? Your draft is kept on this phone.", "Builder မှ ထွက်မလား? သင့် draft ကို ဤဖုန်းတွင် သိမ်းထားမည်။");
        t("leave", "Leave", "ထွက်မည်");
        t("stay", "Stay", "ဆက်ရှိမည်");
        t("startOver", "Start over", "အစကပြန်စမည်");
        t("name", "Theme name", "Theme အမည်");
        t("appearance", "Light / Dark", "အလင်း / အမှောင်");
        t("auto", "Auto", "အလိုအလျောက်");
        t("light", "Light", "အလင်း");
        t("dark", "Dark", "အမှောင်");
        t("notSet", "default", "မူလအတိုင်း");
        t("refused", "Change refused: ", "ပြောင်း၍မရပါ: ");
        t("allSettings", "All settings", "ဆက်တင်အားလုံး");
        t("language", "မြန်မာ", "English");
        t("globalColors", "Global Colors", "အရောင်ပေါင်းစုံ (အားလုံး)");
        t("moreGlobal", "More whole-app settings", "App တစ်ခုလုံး ဆက်တင်များ");
        t("close", "Close", "ပိတ်မည်");
        t("locked", "Locked", "ပြင်၍မရသော");
        t("screens.main", "Main screens", "အဓိက မျက်နှာပြင်များ");
        t("screens.sub", "Sub-screens", "အပိုင်းခွဲ မျက်နှာပြင်များ");
        t("search.other","On another screen","အခြားမျက်နှာပြင်တွင်");
        t("section.appearance", "Appearance", "အသွင်အပြင်");
        t("section.layout", "Layout", "အနေအထား");
        t("section.typography", "Text", "စာသား");
        t("section.advanced", "Advanced", "အဆင့်မြင့်");
        t("mode.edit", "Edit", "ပြင်မည်");
        t("mode.preview", "Preview", "ကြည့်မည်");
        t("draft.saved", "Draft saved on this phone", "Draft ကို ဤဖုန်းတွင် သိမ်းထားသည်");
        t("draft.published", "Editing a published theme", "တင်ပြီးသား theme ကို ပြင်နေသည်");
        t("layers", "Layers", "အလွှာများ");
        t("screens", "Screens", "မျက်နှာပြင်များ");
        t("search", "Find a component", "အစိတ်အပိုင်း ရှာမည်");
        t("search.hint", "Type a name, for example chat or tabs", "အမည်ရိုက်ပါ၊ ဥပမာ chat သို့မဟုတ် tabs");
        t("search.none", "Nothing found", "မတွေ့ပါ");
        t("device", "Preview size", "Preview အရွယ်အစား");
        t("device.320", "Small phone (320)", "ဖုန်းသေး (320)");
        t("device.360", "Standard phone (360)", "ပုံမှန်ဖုန်း (360)");
        t("device.412", "Large phone (412)", "ဖုန်းကြီး (412)");
        t("device.note", "Limited by this screen's width.", "ဤဖန်သားပြင်၏ အကျယ်ဖြင့် ကန့်သတ်ထားသည်။");
        t("copyStyle", "Copy style", "ပုံစံ ကူးမည်");
        t("pasteStyle", "Paste style", "ပုံစံ ကပ်မည်");
        t("copied", "Style copied", "ပုံစံ ကူးပြီး");
        t("pasted", "Style applied", "ပုံစံ ကပ်ပြီး");
        t("nothingCommon", "Nothing in common with this part.", "ဤအပိုင်းနှင့် တူသောဆက်တင် မရှိပါ။");
        t("nothingToCopy", "This part has no changes to copy.", "ကူးစရာ ပြောင်းလဲမှု မရှိပါ။");
        t("resetComponent", "Reset this part", "ဤအပိုင်း မူလပြန်");
        t("resetSection", "Reset this tab", "ဤ tab မူလပြန်");
        t("resetAsk", "Remove your changes to this part? Other parts stay as they are.", "ဤအပိုင်းရှိ သင့်ပြောင်းလဲမှုများ ဖယ်မလား? အခြားအပိုင်းများ မပြောင်းပါ။");
        t("resetDone", "Reset", "မူလပြန်ပြီး");
        t("warn.review", "Review", "စစ်ကြည့်မည်");
        t("warn.fix", "Go to setting", "ဆက်တင်သို့ သွားမည်");
        t("warn.suggest", "Use suggested colour", "အကြံပြုအရောင် သုံးမည်");
        t("warn.line", "is hard to read on", "ကို ဖတ်ရခက်သည်၊ နောက်ခံ");
        t("structural", "Not editable", "ပြင်၍မရပါ");
        t("tapHint", "Tap a part of the screen to edit it", "ပြင်လိုသောအပိုင်းကို နှိပ်ပါ");
        t("previewHint", "Preview mode: nothing is selected", "Preview မုဒ်: ဘာမျှ မရွေးထားပါ");
        t("sheet.none", "Nothing to edit here in this mode.", "ဤမုဒ်တွင် ပြင်စရာ မရှိပါ။");
        t("beginner", "Beginner", "အစပြု");
        t("level", "Level", "အဆင့်");
        t("more", "More", "နောက်ထပ်");
        t("rename", "Theme name", "Theme အမည်");
        t("menu.advanced", "Show advanced settings", "အဆင့်မြင့် ဆက်တင်များ ပြမည်");
        t("menu.basic", "Show basic settings only", "ရိုးရိုး ဆက်တင်များသာ ပြမည်");
        t("publish", "Publish", "တင်မည်");
        t("update", "Update", "ပြင်ပြီးတင်မည်");
        t("publishAsk", "Publish this theme to the store?", "ဤ theme ကို စတိုးသို့ တင်မလား?");
        t("updateAsk", "Publish your changes as a new version?", "ပြင်ထားသည်များကို version အသစ်အဖြစ် တင်မလား?");
        t("publishing", "Please wait…", "ခဏစောင့်ပါ…");
        t("published", "Done! Your theme is in the store.", "ပြီးပါပြီ! သင့် theme ကို စတိုးတွင် တင်ပြီး။");
        t("stylesWarn", "Theme saved, but its styles were not: ", "Theme သိမ်းပြီး၊ သို့သော် ပုံစံများ မသိမ်းနိုင်ပါ: ");
        t("needName", "Give your theme a name first (Whole app > Theme name).", "Theme အမည်ပေးပါ (App တစ်ခုလုံး > Theme အမည်)။");
        t("removeStore", "Remove from store", "စတိုးမှ ဖယ်ရှားမည်");
        t("removeAsk", "It will disappear from the store. People who already applied it keep it.", "စတိုးမှ ပျောက်သွားမည်။ သုံးထားပြီးသူများတွင် ဆက်ရှိမည်။");
        t("removed", "Removed from the store", "စတိုးမှ ဖယ်ရှားပြီး");
    }

    /** Every screen the preview can show, in the order of the picker. */
    public static final String[] SCENES = {"chats", "chat", "settings", "controls", "drawer", "people", "contacts", "profile",
            "themeStore", "rows.privacy", "rows.notifications", "rows.dataStorage", "rows.profile"};

    private BuilderText() {
    }

    public static String get(String lang, String key) {
        String[] v = T.get(key);
        if (v == null) {
            return key;
        }
        String s = "my".equals(lang) ? v[1] : v[0];
        return s == null || s.isEmpty() ? v[0] : s;
    }

    public static boolean complete() {
        for (String[] v : T.values()) {
            if (v[0].isEmpty() || v[1].isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
