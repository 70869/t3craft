"""Original pixel-art interpretations of the four requested gaming looks.
Each paints its own 64x64 wide-arm skin; no inherited headphones, beanie or hair.
"""
from skinlib import Skin, FACES


def _skin(colors, head, chest, arms, legs, hair):
    sk = Skin(slim=False)
    legend = dict(zip("ABCDShdHGewmqY", [(*c, 255) for c in colors]))
    for part in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
        for face in FACES:
            _, _, w, h = sk.rect(part, "base", face)
            tone = "B" if face in ("front", "top") else "C" if face in ("left", "right") else "D"
            sk.grid(part, "base", face, [tone * w] * h, legend)
    for face in ("front", "back", "right", "left", "top"):
        sk.grid("head", "base", face, head if face == "front" else hair if face == "back" else ["H"*8]*8, legend)
    sk.grid("head", "base", "bottom", ["d"*8]*8, legend)
    sk.grid("body", "base", "front", chest, legend)
    for side in ("right", "left"):
        sk.grid(side + "_arm", "base", "front", arms, legend)
        sk.grid(side + "_leg", "base", "front", legs, legend)
    return sk, legend


def spartan():
    colors = [(132,160,99),(91,119,63),(61,86,45),(31,46,30),
              (211,163,58),(239,193,82),(136,91,35),(75,104,52),(51,70,42),
              (242,209,114),(19,28,23),(184,135,49),(224,191,92),(172,187,139)]
    sk, l = _skin(colors,
        ["CBAAABBC","BCCCCCCB","BSShhSSB","CSqqqSdC","CBCCCCBC","CDDDDDDC","BCBDDBCB","CCDDDDCC"],
        ["AACDDCAA","ABCDDCBA","BBBAABBB","BCCBBCCB","BCDYYDCB","BCDDDDCB","BCBBBB CB".replace(" ",""),"CCBCCBCC","DDDDDDDD","BCBCCBCB","BCBCCBCB","CCCD DCCC".replace(" ","")],
        ["AAAA","ABBA","BCCB","BCCB","CCCC","DDDD","BCCC","BCCC","CCCD","DDDD","CDDC","DDDD"],
        ["BCCB","BCCB","BCCC","CCCC","DDDD","CAAC","CBBC","CCDC","BCCC","BCCC","DDDD","DDDD"], ["BBBCCBBB"]*8)
    # Helmet brow, cheek armor and gold visor on the protruding outer layer.
    sk.grid("head", "overlay", "front", [".AAAAAA.","ABCCCCBA","CShhhhSC",".SqqqqS.","CBCCCCBC",".CDDDDC.","..CDDC..","........"], l)
    return sk


def naruto():
    colors = [(255,184,80),(235,117,38),(190,76,31),(49,53,66),
              (238,187,145),(255,216,175),(192,137,100),(249,209,59),(216,162,31),
              (61,109,168),(249,245,228),(141,69,50),(195,207,212),(170,177,188)]
    sk, l = _skin(colors,
        ["HGHGGHGH","HHHHHHHH","DDqqqqDD","SSSddSSS","SewSSweS","dSSSSSSd","SdmSSmdS","dSSmmSSd"],
        ["DDDqqDDD","DDDBBDDD","BBBqqBBB","BBBDDBBB","ABBqDBBA","ABBqDBBA","BBBqDBBB","BBBqDBBB","BBBqDBBB","BCBqDBCB","CCCqDCCC","DDDqqDDD"],
        ["DDDD","DBBD","BBBB","BBBB","ABBA","BBBC","CCCC","DDDD","SSSS","SSSS","SddS","dddd"],
        ["BBBB","BBBB","BBBC","BBBC","BBBC","CCCD","CCCD","CCCD","DDDD","DDDD","SSSS","DDDD"], ["HGHGHHGH"]*8)
    sk.grid("head", "overlay", "front", ["H.HHHH.H",".HHHHHH.","DDqqqqDD","..qDDq..","........","........","........","........"], l)
    sk.grid("head", "overlay", "back", ["........"]*2+["DDDDDDDD","....DD..","....DD..",".....D..","........","........"], l)
    return sk


def snake():
    colors = [(131,145,156),(91,109,123),(60,77,91),(30,42,51),
              (212,173,140),(233,194,157),(153,112,87),(78,62,48),(48,40,34),
              (79,107,120),(225,226,207),(98,67,52),(192,193,178),(160,175,185)]
    sk, l = _skin(colors,
        ["HHHHHHHH","HHHHHHHH","DDDDDDDD","SSSddSSS","SewSSweS","dSSSSSSd","SGmSSmGS","dGGmmGGd"],
        ["DDCSSCDD","DCCDDCCD","BBqDDqBB","BCDYYDCB","BCDYYDCB","CCDDDDCC","BCqDDqCB","BCqDDqCB","CCqDDqCC","DDDDDDDD","BCDDDDCB","CCDDDDCC"],
        ["AAAA","ABBA","BCCC","CCCD","BCCD","CDDD","CCCC","DDDD","DDDD","DDDD","DDDD","DDDD"],
        ["BCCB","BCCB","CCCD","CCCD","BDDB","BDDB","DDDD","BCCC","BCCC","CCCD","DDDD","DDDD"], ["HHGGHHGG"]*8)
    sk.grid("head", "overlay", "front", [".HHHHHH.","HHHHHHHH","DDDDDDDD","........","........","........","........","........"], l)
    sk.grid("head", "overlay", "back", ["........"]*2+["DDDDDDDD",".....DD.",".....DD.","......D.","........","........"], l)
    return sk


def mario():
    colors = [(239,81,65),(204,43,43),(146,30,38),(45,74,139),
              (237,185,134),(255,213,164),(181,128,83),(87,55,36),(56,36,27),
              (45,114,191),(255,248,234),(123,69,42),(245,220,94),(63,145,220)]
    sk, l = _skin(colors,
        ["BBBBBBBB","BBBBBBBB","SHSSSSHS","SewSSweS","SSShhSSS","SHHhhHHS","dSHHHHSd","dSSmmSSd"],
        ["BBBBBBBB","BeBBBB eB".replace(" ",""),"BeBBBB eB".replace(" ",""),"BeYBBYeB","Beeee eeB".replace(" ",""),"BDeeeeDB","DDeeeeDD","DDeeeeDD","DDeeeeDD","DDDeeDDD","DDDeeDDD","DDDDDDDD"],
        ["BBBB","BBBB","BBBC","BBBC","BBBC","BBBC","CCCC","wwww","wwww","wwww","wqqw","qqqq"],
        ["eeee","eeee","eeee","eeee","eeee","eeeD","eeeD","DDDD","HHHH","HHHH","GGGG","GGGG"], ["BBBBBBBB","BBBBBBBB","HHHHHHHH","HHHHHHHH","HHHSSHHH","HHSSSSHH","dSSSSSSd","dddddddd"])
    sk.grid("head", "overlay", "front", [".AAAAAA.","ABwww wBA".replace(" ",""),"BBwBBwBB","BBBBBBBB","........","........","........","........"], l)
    return sk
