"""
Finds the sticker packs Telegram publicly features in its app and adds any that aren't in our backend yet.

IMPORTANT LIMITATION (read this before relying on it): Telegram has no API that lists
"every official sticker pack that exists" - no such catalog exists on their side at
all. The closest real thing is the "featured stickers" list: a set of sticker packs
Telegram's own team curates and periodically updates (messages.getFeaturedStickers),
plus an older/paginated backlog of previously-featured ones
(messages.getOldFeaturedStickers). That's what this script scans. It is a good proxy
for "official stickers, and alert me to new ones" (new official packs do get added to
this list over time), but it is NOT guaranteed to be literally 100% of every sticker
pack Telegram has ever published - there is no such complete list to query in the
first place.

What it does each run:
  1. Logs into Telegram with a pre-created session (see login.py - run that once).
  2. Fetches the current featured sticker sets, then pages through the older featured
     sticker backlog, keeping every sticker set Telegram publishes there (not just ones flagged `official`; emoji and mask packs are skipped).
  3. Fetches the short_names already in our own backend (paginating the existing
     GET /api/v1/stickers list endpoint) so we never try to add a duplicate.
  4. Adds any newly-found short_name to the backend as a new pack, with
     category "Custom" (Telegram's API has no per-pack category/genre field at all,
     so there is nothing real to auto-detect here - every auto-added pack lands in
     "Custom" until/unless a smarter categorizer is built later) and
     owner_telegram_id set to the pack's REAL Telegram id (see owner_id_from_set_id).
  5. Migration: any existing record whose owner_telegram_id is still the old placeholder
     "telegram_official" is resolved through Telegram and corrected (MONGODB_URI mode).

Environment variables (see .github/workflows/sticker-scan.yml for how these are
supplied in CI, or export them yourself to run this locally):
  API_ID, API_HASH            - same Telegram app credentials the Android build uses
  TELEGRAM_SESSION_STRING     - from running login.py once
  STICKER_API_BASE_URL        - e.g. https://backend-api-gn09.onrender.com
  STICKER_API_KEY             - optional; sent as "Authorization: Bearer <key>" if the
                                 backend's create-pack endpoint needs it. Leave unset
                                 if it doesn't.
"""
import os
import sys
import time
from datetime import datetime, timezone

import requests
from telethon.sync import TelegramClient
from telethon.sessions import StringSession
from telethon.tl.functions.messages import (
    GetFeaturedStickersRequest,
    GetOldFeaturedStickersRequest,
    GetStickerSetRequest,
)
from telethon.tl.types import InputStickerSetShortName

LEGACY_OWNER_MARKER = "telegram_official"  # old placeholder; the migration replaces it
DEFAULT_CATEGORY = "Custom"
OLD_FEATURED_PAGE_SIZE = 50
OLD_FEATURED_MAX_PAGES = 200  # safety cap - stop even if Telegram keeps paginating
REQUEST_DELAY_SECONDS = 0.3
REQUEST_TIMEOUT_SECONDS = 90
REQUEST_RETRIES = 4


def request_with_retry(method, url, **kwargs):
    """The backend is on Render's free tier, which sleeps when idle and can take a minute
    or more to wake on the first request - so use a long timeout and retry a few times
    (on timeouts, connection errors, and 502/503/504 'still waking up' responses)."""
    last_error = None
    for attempt in range(1, REQUEST_RETRIES + 1):
        try:
            resp = requests.request(method, url, timeout=REQUEST_TIMEOUT_SECONDS, **kwargs)
            if resp.status_code not in (502, 503, 504):
                return resp
            last_error = f"HTTP {resp.status_code}"
        except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
            last_error = type(e).__name__
        print(f"  backend not ready (attempt {attempt}/{REQUEST_RETRIES}: {last_error}), retrying...")
        time.sleep(10 * attempt)
    raise RuntimeError(f"Backend did not respond after {REQUEST_RETRIES} attempts ({last_error}): {url}")


def env(name, required=True, default=None):
    value = os.environ.get(name, default)
    if required and not value:
        hint = (
            " - add it under repo Settings -> Secrets and variables -> Actions "
            "(for TELEGRAM_SESSION_STRING, run Tools/sticker-scanner/login.py once)."
            if name == "TELEGRAM_SESSION_STRING" else ""
        )
        print(f"::error::Missing required environment variable {name}{hint}", file=sys.stderr)
        sys.exit(1)
    return value


def is_wanted_set(stickerset):
    """Every sticker set Telegram itself publishes to all users in its app (its
    featured / trending lists) counts - NOT only sets carrying the `official` flag.
    Custom-emoji packs and mask packs are different features, so they are skipped."""
    if not stickerset.short_name:
        return False
    if getattr(stickerset, "emojis", False) or getattr(stickerset, "masks", False):
        return False
    return True


def owner_id_from_set_id(set_id):
    """Telegram gives a sticker set no 'owner' field, but the set's 64-bit id is built as
    (creator user id << 32 | random), so the high 32 bits are the creator's numeric
    Telegram id. Returns it as a digit string (the backend's owner_telegram_id is a
    string field), or None if it can't be read (zero / missing)."""
    try:
        owner = (int(set_id) >> 32) & 0xFFFFFFFF
    except (TypeError, ValueError):
        return None
    return str(owner) if owner > 0 else None


# short_name -> sticker set id, filled while listing featured sets (no extra requests).
set_ids = {}


def fetch_featured_short_names(client):
    """Returns a dict of {short_name: title} for every sticker set Telegram publicly
    features in its app, from both the live featured list and the older featured
    backlog (paginated). Also records each set's id in `set_ids`."""
    found = {}

    current = client(GetFeaturedStickersRequest(hash=0))
    for covered in getattr(current, "sets", []):
        stickerset = covered.set
        if is_wanted_set(stickerset):
            found[stickerset.short_name] = stickerset.title
            set_ids[stickerset.short_name] = stickerset.id

    offset = 0
    for _ in range(OLD_FEATURED_MAX_PAGES):
        time.sleep(REQUEST_DELAY_SECONDS)
        page = client(GetOldFeaturedStickersRequest(offset=offset, limit=OLD_FEATURED_PAGE_SIZE, hash=0))
        sets = getattr(page, "sets", [])
        if not sets:
            break
        for covered in sets:
            stickerset = covered.set
            if is_wanted_set(stickerset):
                found[stickerset.short_name] = stickerset.title
                set_ids[stickerset.short_name] = stickerset.id
        offset += len(sets)
        if len(sets) < OLD_FEATURED_PAGE_SIZE:
            break  # reached the end of the backlog

    return found


def telegram_created_at(client, short_name):
    """Telegram has no 'pack created' field on a sticker set. The closest real value is the
    upload date of the pack's stickers (each sticker document carries a `date`), so use the
    EARLIEST one as the pack's creation time. Returns an aware datetime, or None if it
    can't be determined (caller falls back to 'now')."""
    try:
        result = client(GetStickerSetRequest(stickerset=InputStickerSetShortName(short_name), hash=0))
        dates = [d.date for d in getattr(result, "documents", []) if getattr(d, "date", None)]
        if not dates:
            return None
        earliest = min(dates)
        return earliest if earliest.tzinfo else earliest.replace(tzinfo=timezone.utc)
    except Exception as e:  # one bad pack must not abort the whole run
        print(f"  (couldn't read creation date for {short_name}: {type(e).__name__})")
        return None


def fetch_existing_short_names(base_url, api_key):
    """Pages through our own GET /api/v1/stickers list endpoint and returns the set of
    short_names already stored, so we never try to insert a duplicate. Parsing mirrors
    the Android client's defensive parsing (StickerStoreApi.java) since the exact
    response shape isn't formally documented."""
    headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
    existing = set()
    page = 1
    limit = 50
    while True:
        resp = request_with_retry(
            "GET",
            f"{base_url}/api/v1/stickers",
            params={"page": page, "limit": limit},
            headers=headers,
        )
        resp.raise_for_status()
        body = resp.json()

        if isinstance(body, list):
            items = body
        elif isinstance(body, dict):
            items = next(
                (body[key] for key in ("items", "results", "packs", "data", "stickers") if isinstance(body.get(key), list)),
                [],
            )
        else:
            items = []

        if not items:
            break

        for item in items:
            if isinstance(item, dict):
                short_name = item.get("short_name") or item.get("shortName") or item.get("name")
            elif isinstance(item, str):
                short_name = item
            else:
                short_name = None
            if short_name:
                existing.add(short_name)

        if len(items) < limit:
            break
        page += 1
        time.sleep(REQUEST_DELAY_SECONDS)

    return existing


def add_pack(base_url, api_key, short_name, created_at, owner_id):
    headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
    # Full document shape, matching the existing stickers_packs documents (the backend's
    # create endpoint didn't fill these defaults in by itself - a pack created with only
    # short_name/category/owner came out missing every other field).
    payload = {
        "short_name": short_name,
        "category": DEFAULT_CATEGORY,
        "owner_telegram_id": owner_id or "0",
        "download_count": 0,
        "total_likes": 0,
        "rating": {"avg": 0, "count": 0},
        "status": "published",
        "is_active": True,
        "report_count": 0,
        "published_at": (created_at or datetime.now(timezone.utc)).astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z"),
    }
    resp = request_with_retry("POST", f"{base_url}/api/v1/stickers", json=payload, headers=headers)
    return resp


def build_pack_document(short_name, created_at, owner_id):
    """The exact stickers_packs document shape. published_at is a real datetime (stored as
    an ISODate), which the HTTP API can't do - it stores whatever string it's sent."""
    return {
        "short_name": short_name,
        "category": DEFAULT_CATEGORY,
        "download_count": 0,
        "total_likes": 0,
        "rating": {"avg": 0.0, "count": 0},
        "owner_telegram_id": owner_id or "0",
        "status": "published",
        "is_active": True,
        "report_count": 0,
        "published_at": (created_at or datetime.now(timezone.utc)).astimezone(timezone.utc),
    }


def migrate_legacy_owners(client, collection):
    """Finds records whose owner_telegram_id is the old placeholder (or any non-numeric
    text), resolves each pack through Telegram to get its real set id, and writes the real
    owner id back. Packs Telegram can't resolve are left untouched and reported."""
    query = {"$or": [
        {"owner_telegram_id": LEGACY_OWNER_MARKER},
        {"owner_telegram_id": {"$type": "string", "$not": {"$regex": "^[0-9]+$"}}},
    ]}
    legacy = list(collection.find(query, {"short_name": 1}))
    print(f"Migration: {len(legacy)} record(s) still have a non-numeric owner_telegram_id.")
    fixed = unresolved = 0
    for doc in legacy:
        short_name = doc.get("short_name")
        set_id = set_ids.get(short_name)
        if set_id is None and short_name:
            time.sleep(REQUEST_DELAY_SECONDS)
            try:
                result = client(GetStickerSetRequest(stickerset=InputStickerSetShortName(short_name), hash=0))
                set_id = result.set.id
            except Exception as e:
                print(f"  could not resolve {short_name}: {type(e).__name__}")
        owner = owner_id_from_set_id(set_id)
        if owner is None:
            unresolved += 1
            continue
        collection.update_one({"_id": doc["_id"]}, {"$set": {"owner_telegram_id": owner}})
        fixed += 1
        print(f"  fixed: {short_name} -> {owner}")
    print(f"Migration done. Fixed {fixed}, unresolved {unresolved}.")


def open_mongo_collection(uri, db_name):
    from pymongo import MongoClient
    client = MongoClient(uri, serverSelectionTimeoutMS=30000)
    return client[db_name]["stickers_packs"]


def main():
    api_id = env("API_ID")
    api_hash = env("API_HASH")
    session_string = env("TELEGRAM_SESSION_STRING")
    base_url = env("STICKER_API_BASE_URL").rstrip("/")
    api_key = env("STICKER_API_KEY", required=False)

    # If MONGODB_URI is set, read/write MongoDB directly (exact document types, e.g. a real
    # ISODate for published_at). Otherwise fall back to the backend's HTTP API, which stores
    # published_at as a plain string.
    mongo_uri = env("MONGODB_URI", required=False)
    collection = open_mongo_collection(mongo_uri, env("MONGODB_DB", required=False) or "Kuro") if mongo_uri else None

    # Backend first: it may be asleep (Render free tier), so wake it while we do the rest.
    print("Fetching already-known packs from our own backend...")
    if collection is not None:
        existing = {d["short_name"] for d in collection.find({}, {"short_name": 1}) if d.get("short_name")}
    else:
        existing = fetch_existing_short_names(base_url, api_key)
    print(f"Backend already has {len(existing)} packs.")

    print("Connecting to Telegram...")
    client = TelegramClient(StringSession(session_string.strip()), int(api_id), api_hash)
    # IMPORTANT: use connect() + is_user_authorized(), NOT `with TelegramClient(...)` /
    # client.start(). start() falls back to asking for a phone number on stdin whenever
    # the session isn't logged in, which crashes with EOFError in CI (no terminal). A
    # session string that is present but invalid/expired/revoked looks exactly like that,
    # so check explicitly and fail with a clear message instead.
    created_dates = {}
    try:
        client.connect()
        if not client.is_user_authorized():
            print(
                "::error::TELEGRAM_SESSION_STRING is set but Telegram says it is not logged in "
                "(expired, revoked, or copied incorrectly). Re-run Tools/sticker-scanner/login.py "
                "to generate a fresh one and update the TELEGRAM_SESSION_STRING secret.",
                file=sys.stderr,
            )
            sys.exit(1)
        print("Fetching Telegram's public sticker sets (featured + old featured)...")
        official = fetch_featured_short_names(client)
        print(f"Found {len(official)} public sticker sets from Telegram.")

        new_short_names = [name for name in official if name not in existing]
        print(f"{len(new_short_names)} new pack(s) to add.")

        if collection is not None:
            migrate_legacy_owners(client, collection)

        # Only look up creation dates for packs we're actually adding (one request each).
        for short_name in new_short_names:
            time.sleep(REQUEST_DELAY_SECONDS)
            created_dates[short_name] = telegram_created_at(client, short_name)
    finally:
        client.disconnect()

    added, failed = 0, 0
    for short_name in new_short_names:
        if collection is not None:
            try:
                collection.insert_one(build_pack_document(short_name, created_dates.get(short_name), owner_id_from_set_id(set_ids.get(short_name))))
                added += 1
                print(f"  added: {short_name}")
            except Exception as e:
                failed += 1
                print(f"  FAILED: {short_name} - {type(e).__name__}: {str(e)[:200]}")
            continue
        resp = add_pack(base_url, api_key, short_name, created_dates.get(short_name), owner_id_from_set_id(set_ids.get(short_name)))
        if resp.ok:
            added += 1
            print(f"  added: {short_name}")
        else:
            failed += 1
            print(f"  FAILED ({resp.status_code}): {short_name} - {resp.text[:200]}")
        time.sleep(REQUEST_DELAY_SECONDS)

    print(f"\nDone. Added {added}, failed {failed}, already had {len(existing)}, skipped (duplicates) {len(official) - len(new_short_names)}.")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
