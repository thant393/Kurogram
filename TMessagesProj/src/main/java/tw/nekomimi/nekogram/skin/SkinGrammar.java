package tw.nekomimi.nekogram.skin;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The shape of a skin / layout document, DERIVED from the rule lists (every "path|kind" rule adds its
 * dotted path to a tree) plus a few structure nodes added by the caller. It is only used to find keys
 * that are not part of the schema; it never validates values (the rules do that) and never throws.
 *
 * Node kinds:
 *   fixed  children are enumerated; an unknown child key is reported as UNKNOWN_FIELD
 *   leaf   a rule target (value checked by the rule). Lists are leaves: their items are values, not keys
 *   open   children are unconstrained and never reported (meta)
 *   map    dynamic keys: children are accepted when the key matches the node's pattern (none exist yet;
 *          declared with {@link Node#asMap}) and are then scanned as {@link Node#valueNode}
 */
final class SkinGrammar {

    static final class Node {
        final Map<String, Node> children = new HashMap<>();
        boolean leaf;
        boolean open;
        Pattern keyPattern;
        Node valueNode;

        Node asMap(String keyRegex, Node value) {
            keyPattern = Pattern.compile(keyRegex);
            valueNode = value;
            return this;
        }
    }

    final Node root = new Node();

    /** Adds every "path|kind" rule as a leaf under its dotted path. */
    SkinGrammar addRules(String[] rules) {
        for (String rule : rules) {
            int bar = rule.indexOf('|');
            String path = bar < 0 ? rule : rule.substring(0, bar);
            Node cur = root;
            for (String part : path.split("\\.")) {
                Node next = cur.children.get(part);
                if (next == null) {
                    next = new Node();
                    cur.children.put(part, next);
                }
                cur = next;
            }
            cur.leaf = true;
        }
        return this;
    }

    SkinGrammar leaf(String name) {
        child(name).leaf = true;
        return this;
    }

    SkinGrammar open(String name) {
        child(name).open = true;
        return this;
    }

    Node child(String name) {
        Node n = root.children.get(name);
        if (n == null) {
            n = new Node();
            root.children.put(name, n);
        }
        return n;
    }

    /** Reports every key of {@code obj} that the grammar does not know, below {@code path} ("" = top level). */
    static void scan(JSONObject obj, Node node, String path, SkinWarnings out) {
        if (obj == null || node == null || node.open) {
            return;
        }
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String p = path.isEmpty() ? key : path + "." + key;
            Node child = node.children.get(key);
            if (child == null && node.keyPattern != null && node.keyPattern.matcher(key).matches()) {
                child = node.valueNode;
            }
            if (child == null) {
                out.add(SkinWarnings.UNKNOWN_FIELD, p);
                continue;
            }
            Object v = obj.opt(key);
            if (v instanceof JSONObject && !child.open && (!child.children.isEmpty() || child.keyPattern != null)) {
                scan((JSONObject) v, child, p, out);
            }
        }
    }
}
