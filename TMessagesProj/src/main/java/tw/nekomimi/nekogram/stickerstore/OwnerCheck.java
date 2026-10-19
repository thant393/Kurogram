package tw.nekomimi.nekogram.stickerstore;

import org.telegram.tgnet.TLRPC;

/**
 * Who created a Telegram sticker set. A set has no "owner" field, but its 64-bit id is built
 * from its creator's user id in the upper 32 bits, so comparing those bits with an account's id
 * tells whether that account made the set.
 */
public final class OwnerCheck {

    private OwnerCheck() {
    }

    /** TRUE: this user created the set. FALSE: someone else did. null: can't be told (a user id
     *  that doesn't fit in 32 bits can't be represented in the set id's upper half). */
    public static Boolean isCreatedBy(TLRPC.StickerSet set, long userId) {
        if (set == null || userId <= 0) {
            return null;
        }
        if (userId > 0xFFFFFFFFL) {
            return null;
        }
        long creator = (set.id >>> 32) & 0xFFFFFFFFL;
        return creator == userId;
    }
}
