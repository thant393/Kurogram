package tw.nekomimi.nekogram.layout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A layout pack: declarative JSON that positions Telegram's own screens (never code). Parsed and
 * validated whole; an invalid known key rejects the layout (the app then keeps "classic").
 * RULES is kept identical to RULES_LAYOUT in the backend (app/schemas/layout_rules.py).
 */
public final class LayoutSpec {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_JSON_BYTES = 64 * 1024;

    public final String id;
    public final String name;
    private final Map<String, Object> values;

    private LayoutSpec(String id, String name, Map<String, Object> values) {
        this.id = id;
        this.name = name;
        this.values = values;
    }

    /** Telegram's own layout: no values set, so every screen uses its built-in design. */
    public static LayoutSpec classic() {
        return new LayoutSpec("classic", "Classic", new HashMap<>());
    }

    public boolean isClassic() {
        return values.isEmpty();
    }

    public boolean has(String path) {
        return values.containsKey(path);
    }

    public int intValue(String path, int def) {
        Object v = values.get(path);
        return v instanceof Integer ? (Integer) v : def;
    }

    public boolean boolValue(String path, boolean def) {
        Object v = values.get(path);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public String stringValue(String path, String def) {
        Object v = values.get(path);
        return v instanceof String ? (String) v : def;
    }

    @SuppressWarnings("unchecked")
    public List<String> listValue(String path, List<String> def) {
        Object v = values.get(path);
        return v instanceof List ? (List<String>) v : def;
    }

    // path | kind: bool, int:lo:hi, enum:a,b,c, list:a,b,c:min:max
    private static final String[] RULES = {
            "chatList.row.heightDp|int:56:96",
            "chatList.row.avatar.sizeDp|int:32:72", "chatList.row.avatar.shape|enum:circle,rounded",
            "chatList.row.avatar.onlineDot.sizeDp|int:6:20", "chatList.row.avatar.onlineDot.color|enum:success,accent",
            "chatList.row.avatar.lastActive|enum:none,badge",
            "chatList.row.title.weight|enum:normal,medium,bold", "chatList.row.title.sizeSp|int:12:22",
            "chatList.row.title.unreadWeight|enum:normal,medium,bold",
            "chatList.row.preview.lines|int:1:3", "chatList.row.preview.sizeSp|int:11:20",
            "chatList.row.preview.color|enum:textPrimary,textSecondary",
            "chatList.row.preview.unreadColor|enum:textPrimary,textSecondary",
            "chatList.row.meta.time|enum:top-right,bottom-right,hidden",
            "chatList.row.meta.seenAvatar|enum:none,bottom-right", "chatList.row.meta.unreadDot|enum:none,right",
            "chatList.row.pinned|enum:none,icon-left-of-time", "chatList.row.dividers|bool",
            "chatList.header.left|list:menu,compose,search:0:2", "chatList.header.right|list:menu,compose,search:0:3",
            "chatList.storyRail.enabled|bool", "chatList.storyRail.avatarSizeDp|int:40:80", "chatList.storyRail.showCreate|bool",
            "chatList.folders.style|enum:chips,tabs,none", "chatList.folders.position|enum:below-header,below-rail",
            "chatList.folders.collapsible|bool",
            "chatList.specialRows.archive|enum:row,hidden",
            "bottomTabs.items|list:chats,people,settings,profile:2:4", "bottomTabs.labels|bool", "bottomTabs.style|enum:flat,glass",
            "chat.header.avatarLeft|bool", "chat.header.actions|list:call,video,more,search:0:4",
            "chat.input.style|enum:classic,pill", "chat.input.leading|list:plus,attach,emoji:0:2",
            "chat.input.trailing|list:emoji,mic,attach:0:3",
    };

    public static LayoutSpec parse(String json) {
        if (json == null || json.getBytes().length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Layout file is missing or too large");
        }
        try {
            JSONObject root = new JSONObject(json);
            int version = root.optInt("schemaVersion", 0);
            if (version > SCHEMA_VERSION) {
                throw new IllegalArgumentException("This layout needs a newer version of Kurogram");
            }
            if (version != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported layout schemaVersion");
            }
            Map<String, Object> values = new HashMap<>();
            for (String rule : RULES) {
                int bar = rule.indexOf('|');
                String path = rule.substring(0, bar);
                Object v = get(root, path);
                if (v == null) {
                    continue;
                }
                values.put(path, check(path, rule.substring(bar + 1), v));
            }
            JSONObject meta = root.optJSONObject("meta");
            String id = meta == null ? "" : meta.optString("id", "");
            if (!id.matches("[A-Za-z0-9_.-]{1,48}")) {
                throw new IllegalArgumentException("meta.id is invalid");
            }
            String name = meta.optString("name", id);
            if (name.length() > 48) {
                throw new IllegalArgumentException("meta text is too long");
            }
            // a layout may not remove what is needed to use the app
            List<String> tabs = listOr(values, "bottomTabs.items");
            if (tabs != null && !tabs.contains("chats")) {
                throw new IllegalArgumentException("bottomTabs.items must include chats");
            }
            if (get(root, "chatList.header") != null) {
                List<String> both = new ArrayList<>(listOr(values, "chatList.header.left") == null ? new ArrayList<String>() : listOr(values, "chatList.header.left"));
                List<String> right = listOr(values, "chatList.header.right");
                if (right != null) both.addAll(right);
                if (!both.contains("search")) {
                    throw new IllegalArgumentException("chatList.header must include search");
                }
            }
            List<String> trailing = listOr(values, "chat.input.trailing");
            List<String> leading = listOr(values, "chat.input.leading");
            if (trailing != null && !trailing.contains("mic") && (leading == null || !leading.contains("mic"))) {
                throw new IllegalArgumentException("chat.input must keep the mic");
            }
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Layout sets nothing");
            }
            return new LayoutSpec(id, name, values);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Layout file is not valid JSON");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> listOr(Map<String, Object> values, String path) {
        Object v = values.get(path);
        return v instanceof List ? (List<String>) v : null;
    }

    private static Object get(JSONObject root, String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof JSONObject)) {
                return null;
            }
            JSONObject o = (JSONObject) cur;
            if (!o.has(part) || o.isNull(part)) {
                return null;
            }
            cur = o.opt(part);
        }
        return cur;
    }

    private static Object check(String path, String kind, Object v) {
        String[] k = kind.split(":");
        switch (k[0]) {
            case "bool":
                if (v instanceof Boolean) return v;
                break;
            case "int":
                if (v instanceof Integer) {
                    int n = (Integer) v;
                    if (n >= Integer.parseInt(k[1]) && n <= Integer.parseInt(k[2])) return v;
                }
                break;
            case "enum":
                if (v instanceof String && Arrays.asList(k[1].split(",")).contains(v)) return v;
                break;
            case "list":
                if (v instanceof JSONArray) {
                    JSONArray a = (JSONArray) v;
                    List<String> allowed = Arrays.asList(k[1].split(","));
                    List<String> out = new ArrayList<>();
                    boolean ok = a.length() >= Integer.parseInt(k[2]) && a.length() <= Integer.parseInt(k[3]);
                    for (int i = 0; ok && i < a.length(); i++) {
                        Object item = a.opt(i);
                        ok = item instanceof String && allowed.contains(item) && !out.contains(item);
                        if (ok) out.add((String) item);
                    }
                    if (ok) return out;
                }
                break;
            default:
                break;
        }
        throw new IllegalArgumentException(path + " has an invalid value");
    }
}
