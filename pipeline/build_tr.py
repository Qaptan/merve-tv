#!/usr/bin/env python3
"""Builds tr.json for the Merve app from the public iptv-org API.

Keeps only Turkish-language channels registered in Turkey, tests every
stream and writes a small file the TV downloads instead of ~25 MB of raw data.
Standard library only, so it runs anywhere (including GitHub Actions).
"""
import argparse
import concurrent.futures as cf
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://iptv-org.github.io/api/"
UA = "Mozilla/5.0 (Linux; Android 14) Merve/1.0"

# Turkish group name for each iptv-org category, in display order.
GROUPS = [
    ("haber", "Haber", {"news", "business", "weather", "legislative"}),
    ("spor", "Spor", {"sports", "outdoor"}),
    ("cocuk", "Çocuk", {"kids", "animation", "education", "family"}),
    ("muzik", "Müzik", {"music"}),
    ("belgesel", "Belgesel", {"documentary", "science", "travel", "culture", "cooking"}),
    ("din", "Dini", {"religious"}),
]
# Big national channels first, the rest alphabetical.
PRIORITY = ["TRT1.tr", "ATV.tr", "KanalD.tr", "StarTV.tr", "NOW.tr", "TV8.tr", "Kanal7.tr",
            "TRTHaber.tr", "NTV.tr", "Haberturk.tr", "HalkTV.tr", "AHaber.tr", "ASpor.tr", "TRTCocuk.tr"]


# iptv-org marks many city channels as nationwide (c/TR), so "ulusal" is an
# explicit list; every other general-interest channel is treated as local.
NATIONAL = {"TRT1.tr", "TRT2.tr", "TRT3.tr", "TRTTurk.tr", "TRTAvaz.tr", "ATV.tr", "KanalD.tr", "StarTV.tr",
            "NOW.tr", "TV8.tr", "Kanal7.tr", "A2TV.tr", "BeyazTV.tr", "EuroD.tr", "ATVAvrupa.tr",
            "Kanal7Avrupa.tr", "ShowTurk.tr", "FX.tr", "MovieSmartTurk.tr", "TV4.tr", "VavTV.tr", "BenguturkTV.tr"}
GROUP_OVERRIDE = {"NTV.tr": "haber", "Tele1.tr": "haber", "TVNET.tr": "haber", "UlkeTV.tr": "haber",
                  "TBMMTV.tr": "haber", "TGRTBelgesel.tr": "belgesel", "PowerTV.tr": "muzik",
                  "PowerTurkTV.tr": "muzik", "SemerkandTV.tr": "din", "BricveSatrancTV.tr": "spor"}


def get(name):
    with urllib.request.urlopen(urllib.request.Request(API + name, headers={"User-Agent": UA}), timeout=120) as r:
        return json.load(r)


def _fetch(url, headers, limit):
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10) as r:
        return r.status, r.geturl(), r.read(limit)


def _uris(playlist, base):
    """Non-comment lines of an m3u8, resolved against the playlist URL."""
    text = playlist.decode("utf-8", "ignore")
    return [urllib.parse.urljoin(base, ln.strip()) for ln in text.splitlines() if ln.strip() and not ln.startswith("#")]


def probe(stream):
    """Walks the stream the way a player does: master -> variant -> first video segment.

    Returns "ok" or a short Turkish reason. A playlist that answers but whose
    segments are blocked (common with geo/token protection) counts as broken.
    """
    headers = {"User-Agent": stream["user_agent"] or UA}
    if stream["referrer"]:
        headers["Referer"] = stream["referrer"]
    try:
        status, url, body = _fetch(stream["url"], headers, 200_000)
        if status != 200 or b"#EXTM3U" not in body[:1024]:
            return "liste yok"
        for _ in range(3):  # master playlists may nest
            if b"#EXT-X-STREAM-INF" not in body:
                break
            variants = _uris(body, url)
            if not variants:
                return "kalite listesi boş"
            status, url, body = _fetch(variants[0], headers, 200_000)
            if status != 200:
                return "kalite listesi açılmıyor"
        segments = _uris(body, url)
        if not segments:
            return "video parçası yok"
        # The newest segment may not be on the CDN edge yet, so try older ones first.
        for seg in (segments[0], segments[len(segments) // 2]):
            try:
                status, _, data = _fetch(seg, headers, 64_000)
                if status == 200 and len(data) >= 1000:
                    return "ok"
            except urllib.error.HTTPError:
                continue
        return "video parçası inmiyor"
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code}"
    except Exception as e:
        return "zaman aşımı" if "timed out" in str(e) else "bağlantı hatası"


def check(stream):
    # One retry: live CDNs hiccup, and hiding a working channel is worse than a slow check.
    stream["reason"] = probe(stream)
    if stream["reason"] != "ok":
        time.sleep(3)
        stream["reason"] = probe(stream)
    return stream["reason"] == "ok"


def group_of(cid, categories):
    if cid in GROUP_OVERRIDE:
        return GROUP_OVERRIDE[cid]
    cats = set(categories)
    for key, _, ids in GROUPS:
        if cats & ids:
            return key
    return "ulusal" if cid in NATIONAL else "yerel"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-o", "--out", default="tr.json")
    ap.add_argument("--no-check", action="store_true", help="skip stream tests")
    args = ap.parse_args()

    channels = {c["id"]: c for c in get("channels.json")}
    feeds, streams, logos = get("feeds.json"), get("streams.json"), get("logos.json")
    blocked = {b["channel"] for b in get("blocklist.json")}

    tr_ids = {cid for cid, c in channels.items()
              if c["country"] == "TR" and not c["is_nsfw"] and not c["closed"] and cid not in blocked}
    langs = {}
    for f in feeds:
        if f["channel"] in tr_ids:
            langs.setdefault(f["channel"], set()).update(f["languages"])
    tr_ids = {cid for cid in tr_ids if "tur" in langs.get(cid, ())}

    by_channel = {}
    for s in streams:
        if s["channel"] in tr_ids:
            by_channel.setdefault(s["channel"], []).append(s)

    if not args.no_check:
        flat = [s for lst in by_channel.values() for s in lst]
        with cf.ThreadPoolExecutor(32) as ex:
            for s, ok in zip(flat, ex.map(check, flat)):
                s["ok"] = ok

    best_logo = {}
    for lg in logos:
        cid = lg["channel"]
        if cid not in by_channel or not lg["in_use"]:
            continue
        score = (lg["feed"] is None, lg["format"] in ("PNG", "SVG", "WebP"), min(lg["width"] or 0, 512))
        if cid not in best_logo or score > best_logo[cid][0]:
            best_logo[cid] = (score, lg["url"])

    def quality_rank(s):
        q = (s["quality"] or "0p").rstrip("pi")
        return int(q) if q.isdigit() else 0

    out = []
    for cid, lst in by_channel.items():
        c = channels[cid]
        lst.sort(key=lambda s: (not s.get("ok", True), -quality_rank(s)))
        out.append({
            "id": cid,
            "name": c["name"],
            "logo": best_logo.get(cid, (None, None))[1],
            "group": group_of(cid, c["categories"]),
            "categories": c["categories"],
            "alive": any(s.get("ok", True) for s in lst),
            "streams": [{
                "url": s["url"],
                "quality": s["quality"],
                "userAgent": s["user_agent"],
                "referrer": s["referrer"],
                "labels": s["labels"],
                "ok": s.get("ok", True),
            } for s in lst],
            "reason": next((s.get("reason") for s in lst if s.get("ok", True)), lst[0].get("reason")) if lst else None,
        })
    rank = {cid: i for i, cid in enumerate(PRIORITY)}
    out.sort(key=lambda c: (rank.get(c["id"], len(rank)), c["name"].casefold()))

    data = {
        "version": 1,
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "source": "https://github.com/iptv-org/iptv",
        "groups": [{"id": "ulusal", "name": "Ulusal"}] + [{"id": k, "name": n} for k, n, _ in GROUPS] + [{"id": "yerel", "name": "Yerel"}],
        "channels": out,
    }
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))
    alive = sum(c["alive"] for c in out)
    print(f"{len(out)} kanal yazıldı, {alive} tanesi çalışıyor -> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
