package tw.nekomimi.nekogram.stickerstore;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.AndroidUtilities;

import java.util.HashSet;

/**
 * Which sticker sets the signed-in account created, asked of Telegram itself
 * (messages.getMyStickers). This works for every account id - a 64-bit user id never has to fit
 * into a sticker set id - so there is no size limit and nothing is ever assumed or bypassed:
 * a pack counts as "mine" only when Telegram lists it among the signed-in user's own sets.
 */
public final class OwnerCheck {

    private static final int PAGE = 100;
    private static final int MAX_PAGES = 20;

    public interface Callback {
        void onResult(HashSet<String> shortNames, HashSet<Long> setIds);

        void onError();
    }

    private OwnerCheck() {
    }

    public static void fetchMine(int account, Callback callback) {
        fetchPage(account, 0L, 0, new HashSet<>(), new HashSet<>(), callback);
    }

    private static void fetchPage(int account, long offsetId, int page, HashSet<String> names, HashSet<Long> ids, Callback callback) {
        TLRPC.TL_messages_getMyStickers req = new TLRPC.TL_messages_getMyStickers();
        req.offset_id = offsetId;
        req.limit = PAGE;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> {
            if (!(res instanceof TLRPC.TL_messages_myStickers)) {
                AndroidUtilities.runOnUIThread(callback::onError);
                return;
            }
            TLRPC.TL_messages_myStickers my = (TLRPC.TL_messages_myStickers) res;
            long last = 0;
            for (TLRPC.StickerSetCovered covered : my.sets) {
                if (covered != null && covered.set != null) {
                    if (covered.set.short_name != null) {
                        names.add(covered.set.short_name.toLowerCase());
                    }
                    ids.add(covered.set.id);
                    last = covered.set.id;
                }
            }
            if (my.sets.size() >= PAGE && last != 0 && page + 1 < MAX_PAGES) {
                fetchPage(account, last, page + 1, names, ids, callback);
            } else {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(names, ids));
            }
        });
    }

    /** True only if Telegram lists this set (by id or short name) among the user's own. */
    public static boolean contains(TLRPC.StickerSet set, HashSet<String> names, HashSet<Long> ids) {
        if (set == null) {
            return false;
        }
        if (ids.contains(set.id)) {
            return true;
        }
        return set.short_name != null && names.contains(set.short_name.toLowerCase());
    }
}
