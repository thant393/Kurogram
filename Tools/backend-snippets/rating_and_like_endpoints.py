"""
Reference code for the Sticker Store BACKEND (the FastAPI service on Render) - this file is
NOT part of the Android app and is never run from this repo. It is what the app's rating
and like buttons expect to talk to; copy the two endpoints into the backend project.

What the app sends (see StickerStoreApi.java):

  PUT  /api/v1/stickers/{pack_id}/rating   {"user_telegram_id": 123, "rating": 4, ...}
  POST /api/v1/stickers/{pack_id}/like     {"user_telegram_id": 123, "liked": true}

{pack_id} is the pack's Mongo _id (hex string); the short_name also works here.

Rating rules (what "dynamic average" means):
  * one rating per (user, pack): rating again REPLACES the earlier stars (upsert), it does
    not add a second rating;
  * after every write the pack's numbers are recalculated from the ratings collection:
        average = sum(stars) / number of ratings
    and saved back onto the pack as  rating: {avg, count}  - the same shape the list
    endpoint already returns, so the app's grid and detail screen pick it up unchanged;
  * the reply carries the new {avg, count} so the app can show the exact server numbers.

Adjust the collection / field names below to match your existing documents (they are
named after the collections you described: stickers_packs, stickers_ratings, stickers_likes).
"""
from datetime import datetime, timezone

from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from pymongo import ReturnDocument

# `db` = your existing pymongo Database handle (database "Kuro").
# from .database import db
router = APIRouter(prefix="/api/v1/stickers")


class RatingIn(BaseModel):
    user_telegram_id: int
    rating: int = Field(ge=1, le=5)


class LikeIn(BaseModel):
    user_telegram_id: int
    liked: bool


def _find_pack(db, pack_id: str):
    try:
        pack = db.stickers_packs.find_one({"_id": ObjectId(pack_id)})
    except (InvalidId, TypeError):
        pack = None
    if pack is None:
        pack = db.stickers_packs.find_one({"short_name": pack_id})
    if pack is None:
        raise HTTPException(status_code=404, detail="Sticker pack not found")
    return pack


@router.put("/{pack_id}/rating")
def rate_pack(pack_id: str, body: RatingIn):
    pack = _find_pack(db, pack_id)
    now = datetime.now(timezone.utc)

    # 1) Upsert this user's rating for this pack.
    db.stickers_ratings.update_one(
        {"pack_id": pack["_id"], "user_telegram_id": body.user_telegram_id},
        {"$set": {"stars": body.rating, "updated_at": now},
         "$setOnInsert": {"created_at": now}},
        upsert=True,
    )

    # 2) Recalculate average + count from ALL ratings of the pack.
    agg = list(db.stickers_ratings.aggregate([
        {"$match": {"pack_id": pack["_id"]}},
        {"$group": {"_id": None, "avg": {"$avg": "$stars"}, "count": {"$sum": 1}}},
    ]))
    avg = float(agg[0]["avg"]) if agg else 0.0
    count = int(agg[0]["count"]) if agg else 0

    # 3) Save them back on the pack.
    db.stickers_packs.update_one(
        {"_id": pack["_id"]},
        {"$set": {"rating": {"avg": round(avg, 2), "count": count}}},
    )
    return {"rating": {"avg": round(avg, 2), "count": count}}


@router.post("/{pack_id}/like")
def like_pack(pack_id: str, body: LikeIn):
    pack = _find_pack(db, pack_id)
    key = {"pack_id": pack["_id"], "user_telegram_id": body.user_telegram_id}
    if body.liked:
        db.stickers_likes.update_one(
            key, {"$setOnInsert": {"created_at": datetime.now(timezone.utc)}}, upsert=True)
    else:
        db.stickers_likes.delete_one(key)

    total = db.stickers_likes.count_documents({"pack_id": pack["_id"]})
    db.stickers_packs.update_one({"_id": pack["_id"]}, {"$set": {"total_likes": total}})
    return {"total_likes": total}
