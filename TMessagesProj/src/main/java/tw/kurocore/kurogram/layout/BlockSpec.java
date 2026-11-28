package tw.kurocore.kurogram.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Screens a layout file builds out of ready-made blocks ("screens": {"contacts": {"title": "Friends", "blocks": [...]}}).
 * Pure data and checks, no Android: the app draws these with one generic screen, the Theme Studio previews them
 * with the same list. Only the block types, props and data sources below exist; anything else is rejected, so a
 * downloaded layout can never run code or reach something the app did not offer.
 */
public final class BlockSpec {

    public static final int MAX_SCREENS = 8, MAX_BLOCKS = 24;

    public static final List<String> SOURCES = Collections.unmodifiableList(Arrays.asList("contacts", "online", "unread", "chats"));
    public static final List<String> CHIPS = Collections.unmodifiableList(Arrays.asList("all", "online", "contacts"));

    /** type -> allowed props as "name|kind", the same kinds LayoutSpec uses. */
    private static final Map<String, String[]> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("header", new String[]{"title|str:32", "pin|enum:scroll,top,bottom"});
        TYPES.put("storyRail", new String[]{"style|enum:cards,circles", "source|enum:contacts,online"});
        TYPES.put("chips", new String[]{"items|list:all,online,contacts:1:3"});
        TYPES.put("note", new String[]{"text|str:120"});
        TYPES.put("section", new String[]{"title|str:32"});
        TYPES.put("list", new String[]{"source|enum:contacts,online,unread,chats", "row|enum:compact,card", "limit|int:1:200"});
        TYPES.put("chatList", new String[]{"filter|enum:all,unread,people,groups,channels", "row|enum:compact,card", "limit|int:1:100"});
        TYPES.put("menu", new String[]{"items|list:settings,profile,contacts,calls,savedMessages:1:5"});
        TYPES.put("profileCard", new String[]{});
        TYPES.put("spacer", new String[]{"heightDp|int:0:64"});
    }

    public static List<String> types() {
        return Collections.unmodifiableList(new ArrayList<>(TYPES.keySet()));
    }

    public static final class Block {
        public final String type;
        public final Map<String, Object> props;

        Block(String type, Map<String, Object> props) {
            this.type = type;
            this.props = props;
        }

        public String str(String name, String def) {
            Object v = props.get(name);
            return v instanceof String ? (String) v : def;
        }

        public int intValue(String name, int def) {
            Object v = props.get(name);
            return v instanceof Number ? ((Number) v).intValue() : def;
        }

        @SuppressWarnings("unchecked")
        public List<String> list(String name, List<String> def) {
            Object v = props.get(name);
            return v instanceof List ? (List<String>) v : def;
        }
    }

    public static final class Screen {
        public final String id;
        public final String title;
        public final List<Block> blocks;

        Screen(String id, String title, List<Block> blocks) {
            this.id = id;
            this.title = title;
            this.blocks = blocks;
        }
    }

    private BlockSpec() {
    }

    /**
     * Checks the "screens" node (already parsed into Map/List/String/Number/Boolean). An unknown block type is
     * skipped so a layout written for a newer app still opens; a known block with a bad prop is an error.
     */
    public static Map<String, Screen> parse(Object node) {
        Map<String, Screen> out = new LinkedHashMap<>();
        if (node == null) {
            return out;
        }
        if (!(node instanceof Map)) {
            throw new IllegalArgumentException("screens must be an object");
        }
        Map<?, ?> screens = (Map<?, ?>) node;
        if (screens.size() > MAX_SCREENS) {
            throw new IllegalArgumentException("too many screens");
        }
        for (Map.Entry<?, ?> e : screens.entrySet()) {
            String id = String.valueOf(e.getKey());
            if (!id.matches("[a-z][A-Za-z0-9]{1,23}")) {
                throw new IllegalArgumentException("screen id is invalid: " + id);
            }
            if (!(e.getValue() instanceof Map)) {
                throw new IllegalArgumentException("screen " + id + " must be an object");
            }
            Map<?, ?> s = (Map<?, ?>) e.getValue();
            Object t = s.get("title");
            String title = t instanceof String ? (String) t : id;
            if (title.length() > 32) {
                throw new IllegalArgumentException("screen title is too long");
            }
            Object bl = s.get("blocks");
            if (!(bl instanceof List)) {
                throw new IllegalArgumentException("screen " + id + " needs a blocks list");
            }
            List<?> raw = (List<?>) bl;
            if (raw.size() > MAX_BLOCKS) {
                throw new IllegalArgumentException("too many blocks in " + id);
            }
            List<Block> blocks = new ArrayList<>();
            for (Object o : raw) {
                if (!(o instanceof Map)) {
                    throw new IllegalArgumentException("a block must be an object");
                }
                Block b = block((Map<?, ?>) o);
                if (b != null) {
                    blocks.add(b);
                }
            }
            out.put(id, new Screen(id, title, Collections.unmodifiableList(blocks)));
        }
        return out;
    }

    private static Block block(Map<?, ?> m) {
        Object ty = m.get("type");
        if (!(ty instanceof String)) {
            throw new IllegalArgumentException("a block needs a type");
        }
        String[] props = TYPES.get(ty);
        if (props == null) {
            return null; // from a newer app: skip it
        }
        Map<String, Object> vals = new LinkedHashMap<>();
        for (String rule : props) {
            int bar = rule.indexOf('|');
            String name = rule.substring(0, bar);
            Object v = m.get(name);
            if (v == null) {
                continue;
            }
            vals.put(name, check(name, rule.substring(bar + 1), v));
        }
        return new Block((String) ty, Collections.unmodifiableMap(vals));
    }

    private static Object check(String name, String kind, Object v) {
        String[] k = kind.split(":");
        switch (k[0]) {
            case "str":
                if (v instanceof String && ((String) v).length() <= Integer.parseInt(k[1])) {
                    return v;
                }
                break;
            case "int":
                if (v instanceof Number && ((Number) v).doubleValue() == ((Number) v).intValue()) {
                    int n = ((Number) v).intValue();
                    if (n >= Integer.parseInt(k[1]) && n <= Integer.parseInt(k[2])) {
                        return n;
                    }
                }
                break;
            case "enum":
                if (v instanceof String && Arrays.asList(k[1].split(",")).contains(v)) {
                    return v;
                }
                break;
            case "list":
                if (v instanceof List) {
                    List<?> l = (List<?>) v;
                    List<String> allowed = Arrays.asList(k[1].split(","));
                    List<String> out = new ArrayList<>();
                    for (Object o : l) {
                        if (!(o instanceof String) || !allowed.contains(o)) {
                            throw new IllegalArgumentException("bad value in " + name);
                        }
                        out.add((String) o);
                    }
                    if (out.size() >= Integer.parseInt(k[2]) && out.size() <= Integer.parseInt(k[3])) {
                        return Collections.unmodifiableList(out);
                    }
                }
                break;
            default:
                break;
        }
        throw new IllegalArgumentException("bad value for " + name);
    }
}
