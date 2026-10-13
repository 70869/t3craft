"""AgentCraft agent skins: six hand-authored skins plus eighteen CC0 community looks.

Each original agent lives in gen/chars/<id>.py and paints every face as a literal pixel grid
(see skinlib.py for face orientation rules). Shared letter conventions keep grids readable:
  skin  h S s d  (hi, base, shade, deep)     hair  J H G K (hi, base, shade, deep)
  eyes  w e                                   mouth m
  '.' transparent (overlay only)
Left limbs are mirrored from right limbs, then touched up where asymmetry matters.

python gen/skins.py [id ...]
"""
from __future__ import annotations

import importlib
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageOps

sys.path.insert(0, str(Path(__file__).parent))
from common import OUT, save_png  # noqa: E402

CAST = ["marlow", "juniper", "kit", "wren", "rowan", "tove", "spartan", "naruto", "snake", "mario", "yoshi", "megaman", "link", "sonic", "samus", "kirby", "pikachu", "luigi", "peach", "zelda", "donkeykong", "pacman", "lara", "crash"]

# Stable look IDs preserve saved per-world NPC selections while the artwork comes from the CC0 pack.
COMMUNITY_SKINS = {
    "spartan": 25, "naruto": 9, "snake": 5, "mario": 6, "yoshi": 3, "megaman": 4,
    "link": 13, "sonic": 7, "samus": 14, "kirby": 8, "pikachu": 2, "luigi": 10,
    "peach": 18, "zelda": 11, "donkeykong": 12, "pacman": 17, "lara": 16, "crash": 23,
}
COMMUNITY_PACK = Path(__file__).parents[1] / "third_party" / "isaiah658-minetest-skins" / "minetest_skins_pack_1.zip"


def community_skin(index):
    """Read one legacy 64x32 skin and map its mirrored limbs into the modern 64x64 atlas."""
    with zipfile.ZipFile(COMMUNITY_PACK) as pack:
        with pack.open(f"Minetest Skins Pack 1/Skin_{index}.png") as source:
            legacy = Image.open(source).convert("RGBA")
    if legacy.size != (64, 32):
        raise SystemExit(f"CC0 skin {index}: expected 64x32, got {legacy.size}")
    modern = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    modern.alpha_composite(legacy, (0, 0))
    modern.alpha_composite(ImageOps.mirror(legacy.crop((40, 16, 56, 32))), (32, 48))
    modern.alpha_composite(ImageOps.mirror(legacy.crop((0, 16, 16, 32))), (16, 48))
    return modern


def build(ids=None):
    out = OUT / "textures" / "entity" / "agent"
    results = {}
    for cid in CAST:
        if ids and cid not in ids:
            continue
        if cid in COMMUNITY_SKINS:
            image = community_skin(COMMUNITY_SKINS[cid])
        else:
            mod = importlib.import_module(f"chars.{cid}")
            sk = mod.build()
            probs = sk.validate()
            if probs:
                raise SystemExit(f"{cid}: skin validation failed:\n  " + "\n  ".join(probs[:20]))
            image = sk.img
        save_png(image, out / f"{cid}.png")
        results[cid] = out / f"{cid}.png"
        print("skin", cid, "ok")
    return results


if __name__ == "__main__":
    build(sys.argv[1:] or None)
