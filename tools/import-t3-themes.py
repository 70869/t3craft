"""Refresh the shipped color snapshot from the pinned T3 Code source checkout.

python tools/import-t3-themes.py
No network or credentials; palette data only. See THIRD-PARTY-NOTICES.md.
"""
import json
import math
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REV = "250e052f44dd313b658abebc707242a7b25be340"


def rgb(value):
    if value.startswith("#"):
        return value.upper()
    l, c, h = map(float, re.fullmatch(r"oklch\(([^ ]+) ([^ ]+) ([^)]+)\)", value).groups())
    a, b = c * math.cos(math.radians(h)), c * math.sin(math.radians(h))
    ll, mm, ss = (l + .3963377774*a + .2158037573*b)**3, (l - .1055613458*a - .0638541728*b)**3, (l - .0894841775*a - 1.291485548*b)**3
    channels = (4.0767416621*ll - 3.3077115913*mm + .2309699292*ss,
                -1.2684380046*ll + 2.6097574011*mm - .3413193965*ss,
                -.0041960863*ll - .7034186147*mm + 1.707614701*ss)
    def srgb(x):
        return round(255 * max(0, min(1, 12.92*x if x <= .0031308 else 1.055*x**(1/2.4)-.055)))
    return "#" + "".join(f"{srgb(x):02X}" for x in channels)


def colors(block):
    return {key: rgb(value) for key, value in re.findall(r'([A-Za-z]+): "([^"\n]+)"', block)}


def main():
    source = subprocess.check_output(["git", "-C", str(ROOT/".references/t3code"), "show", f"{REV}:packages/shared/src/themePalettes.ts"], text=True)
    themes = []
    stock = []
    for appearance in ("LIGHT", "DARK"):
        block = source.split(f"export const T3_CODE_{appearance}_THEME_COLORS: ThemeColors = {{", 1)[1].split("};", 1)[0]
        stock.append(colors(block))
    themes.append({"id": "t3-code", "label": "T3 Code", "light": stock[0], "dark": stock[1]})
    for name in ("T3_CHAT", "GROVE", "OCEAN", "EMBER", "IRIS"):
        block = source.split(f"export const {name}_THEME: ThemeDefinition = {{", 1)[1].split("\n};", 1)[0]
        light, dark = block.split("  colors: {", 1)[1].split("  variants: {", 1)
        themes.append({"id": re.search(r'id: "([^"]+)"', block)[1], "label": re.search(r'label: "([^"]+)"', block)[1], "light": colors(light), "dark": colors(dark)})
    for theme in themes:
        for appearance in ("light", "dark"):
            assert len(theme[appearance]) == 57, (theme["id"], appearance, len(theme[appearance]))
    target = ROOT/"assets-src/t3-themes.json"
    target.write_text(json.dumps({"source": "pingdotgg/t3code packages/shared/src/themePalettes.ts", "revision": REV, "conversion": "OKLCH to sRGB; out-of-gamut channels clamped", "themes": themes}, indent=2)+"\n", encoding="utf-8")
    print(f"Imported {len(themes)} T3 palettes, light and dark variants: {target}")


if __name__ == "__main__":
    main()
