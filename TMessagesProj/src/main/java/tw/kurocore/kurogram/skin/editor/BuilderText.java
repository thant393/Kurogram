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
        t("scene.chats", "Chats", "ချက်တ်များ");
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
    }

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
