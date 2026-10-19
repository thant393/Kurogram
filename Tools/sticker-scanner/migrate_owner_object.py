"""
One-off (re-runnable) migration: flat `owner_telegram_id: "429000"` -> nested `owner` object

    "owner": {
        "telegram_id":    "429000",
        "display_name":   "Stickers",
        "username":       "Stickers",
        "avatar_url":     "https://t.me/i/userpic/320/Stickers.jpg",   # null if no public username
        "avatar_file_id": "5123456789012345678",                       # Telegram photo id, null if no photo
        "updated_at":     ISODate(...)
    }

and the legacy `owner_telegram_id` field is $unset in the same update.

Why this is Python and not Node: it reuses the scanner's existing Telethon login
(TELEGRAM_SESSION_STRING). A Telethon session string cannot be loaded by a Node/gramjs client,
so a Node version would need a second login. It runs from GitHub Actions
(.github/workflows/owner-migration.yml) with the same secrets as the scanner.

How it resolves owners: many packs share one owner (all scanner-added packs are owned by
@Stickers, id 429000), so each DISTINCT owner id is looked up on Telegram once and cached -
not once per pack. A lookup tries the numeric id first (works when Telegram's session already
knows the user) and then, for the official owner, the username "Stickers" (always resolvable).
Lookups are spaced out and a FloodWait from Telegram is honoured (sleep, then retry).
Documents are written in bulk batches.

Safe to re-run: only documents without an `owner` object are touched (plus, with --refresh,
those whose owner has no display_name yet). If an owner can't be resolved the document is still
migrated with telegram_id kept and display_name/username null - nothing is lost - and
`--refresh` fills it in on a later run.

Usage:
    python3 migrate_owner_object.py --dry-run     # show what would change, write nothing
    python3 migrate_owner_object.py               # migrate
    python3 migrate_owner_object.py --refresh     # also retry owners that stayed unresolved

Environment: API_ID, API_HASH, TELEGRAM_SESSION_STRING, MONGODB_URI, MONGODB_DB (default Kuro)
"""
import argparse
import os
import sys
import time
from datetime import datetime, timezone

from pymongo import UpdateOne
from telethon.sync import TelegramClient
from telethon.sessions import StringSession
from telethon import errors
from telethon.tl.types import PeerUser, User

OFFICIAL_OWNER_ID = os.environ.get("OFFICIAL_OWNER_ID", "429000").strip() or "429000"
OFFICIAL_OWNER_USERNAME = os.environ.get("OFFICIAL_OWNER_USERNAME", "Stickers").strip() or "Stickers"
LOOKUP_DELAY_SECONDS = 1.5
BATCH_SIZE = 500
MAX_FLOOD_WAIT_SECONDS = 600  # don't sleep longer than this for a single wait


def env(name, required=True, default=None):
    value = os.environ.get(name, default)
    if required and not value:
        print(f"::error::Missing required environment variable {name}", file=sys.stderr)
        sys.exit(1)
    return value


def with_flood_wait(fn):
    """Runs a Telegram call, honouring FloodWait (sleep the requested time, retry up to 3x)."""
    for attempt in range(1, 4):
        try:
            return fn()
        except errors.FloodWaitError as e:
            wait = min(int(e.seconds) + 1, MAX_FLOOD_WAIT_SECONDS)
            print(f"  Telegram asked us to wait {e.seconds}s (attempt {attempt}/3); sleeping {wait}s...")
            time.sleep(wait)
    return fn()


def build_owner(user, telegram_id):
    """The owner object for a resolved Telegram user/bot (or just the id if unresolved)."""
    owner = {
        "telegram_id": str(telegram_id),
        "display_name": None,
        "username": None,
        "avatar_url": None,
        "avatar_file_id": None,
        "updated_at": datetime.now(timezone.utc),
    }
    if user is None:
        return owner
    name = " ".join(p for p in (getattr(user, "first_name", None), getattr(user, "last_name", None)) if p).strip()
    owner["display_name"] = name or getattr(user, "title", None) or None
    username = getattr(user, "username", None)
    if not username:
        for u in (getattr(user, "usernames", None) or []):
            if getattr(u, "active", True) and getattr(u, "username", None):
                username = u.username
                break
    owner["username"] = username or None
    # t.me serves public profile pictures at this URL for any account with a public username.
    owner["avatar_url"] = f"https://t.me/i/userpic/320/{username}.jpg" if username and getattr(user, "photo", None) else None
    photo = getattr(user, "photo", None)
    photo_id = getattr(photo, "photo_id", None)
    owner["avatar_file_id"] = str(photo_id) if photo_id else None
    return owner


def resolve_owner(client, telegram_id):
    """Looks one owner up on Telegram: numeric id first, then the known official username."""
    candidates = [("id", lambda: client.get_entity(PeerUser(int(telegram_id))))]
    if str(telegram_id) == OFFICIAL_OWNER_ID:
        candidates.append(("username", lambda: client.get_entity(OFFICIAL_OWNER_USERNAME)))
    for label, getter in candidates:
        try:
            entity = with_flood_wait(getter)
            if isinstance(entity, User):
                return entity
        except (ValueError, errors.RPCError) as e:
            print(f"  lookup by {label} failed for {telegram_id}: {type(e).__name__}")
        time.sleep(LOOKUP_DELAY_SECONDS)
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="resolve and report, but write nothing")
    parser.add_argument("--refresh", action="store_true", help="also re-resolve owners that were migrated without a display_name")
    args = parser.parse_args()

    from pymongo import MongoClient
    mongo = MongoClient(env("MONGODB_URI"), serverSelectionTimeoutMS=30000)
    collection = mongo[env("MONGODB_DB", required=False, default="Kuro") or "Kuro"]["stickers_packs"]

    query = {"$or": [{"owner": {"$exists": False}}, {"owner": None}]}
    if args.refresh:
        query["$or"].append({"owner.display_name": None})
    docs = list(collection.find(query, {"owner_telegram_id": 1, "owner": 1}))
    print(f"{len(docs)} document(s) to migrate.")
    if not docs:
        return

    # Distinct owner ids to resolve (from the legacy field, or the existing owner object).
    def owner_id_of(doc):
        legacy = doc.get("owner_telegram_id")
        if legacy == "telegram_official":  # the old placeholder meant "the official account"
            return OFFICIAL_OWNER_ID
        if legacy not in (None, ""):
            return str(legacy)
        existing = doc.get("owner") or {}
        return str(existing.get("telegram_id") or "") or None

    ids = sorted({owner_id_of(d) for d in docs if owner_id_of(d)})
    print(f"{len(ids)} distinct owner id(s): {', '.join(ids[:20])}{' ...' if len(ids) > 20 else ''}")

    client = TelegramClient(StringSession(env("TELEGRAM_SESSION_STRING").strip()), int(env("API_ID")), env("API_HASH"))
    owners = {}
    try:
        client.connect()
        if not client.is_user_authorized():
            print("::error::TELEGRAM_SESSION_STRING is not logged in (expired/revoked). Re-run login.py.", file=sys.stderr)
            sys.exit(1)
        for i, tid in enumerate(ids, 1):
            if not tid.isdigit():
                print(f"  [{i}/{len(ids)}] '{tid}' is not a numeric id - kept as is, no lookup")
                owners[tid] = build_owner(None, tid)
                continue
            user = resolve_owner(client, tid)
            owners[tid] = build_owner(user, tid)
            o = owners[tid]
            print(f"  [{i}/{len(ids)}] {tid} -> {o['display_name']!r} @{o['username']}" + ("" if user else "  (UNRESOLVED)"))
            time.sleep(LOOKUP_DELAY_SECONDS)
    finally:
        client.disconnect()

    migrated = skipped = 0
    batch = []
    for doc in docs:
        tid = owner_id_of(doc)
        if not tid:
            skipped += 1
            continue
        update = {"$set": {"owner": owners[tid]}}
        if "owner_telegram_id" in doc:
            update["$unset"] = {"owner_telegram_id": ""}
        batch.append(UpdateOne({"_id": doc["_id"]}, update))
        migrated += 1
        if len(batch) >= BATCH_SIZE and not args.dry_run:
            collection.bulk_write(batch, ordered=False)
            print(f"  wrote {migrated} so far...")
            batch = []
    if batch and not args.dry_run:
        collection.bulk_write(batch, ordered=False)

    unresolved = sum(1 for o in owners.values() if not o["display_name"])
    print(f"\n{'DRY RUN - nothing written. Would migrate' if args.dry_run else 'Migrated'} {migrated} document(s); "
          f"skipped {skipped} with no owner id; {unresolved} owner(s) unresolved (kept id only).")


if __name__ == "__main__":
    main()
