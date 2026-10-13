"""Original 64x64 pixel interpretations for the expanded campus cast.

Wide player silhouettes, opaque base faces, and sparse outer-layer details.
These are hand-painted grids, not downloaded character skins.
"""
from skinlib import Skin, FACES

# Primary, shade, highlight, skin/muzzle, ink, white, accent, hair/boot.
DESIGNS = {
    "yoshi": ([(78,170,57),(39,111,48),(130,210,74),(244,235,201),(32,55,34),(255,251,230),(208,66,45),(153,91,42)],
        [".CC..CC.","CCCCCCCC","CwwCCwwC","CwKCCKwC","CCCCCCCC","SSSSSSSS","SSSKKSSS",".SSSSSS."],
        ["AAAS SAAA".replace(" ",""),"AASSSSAA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","AASSSSAA","AAAA AAAA".replace(" ",""),"AAAAAAAA"]),
    "megaman": ([(35,100,203),(23,58,132),(57,189,235),(239,182,132),(22,37,70),(247,247,230),(69,215,245),(32,75,173)],
        ["BBCCCCBB","BACCCCAB","BAACCAAB","BAAS SAAB".replace(" ",""),"SSwKKwSS","SSSSSSSS","BSSKKSSB","BBSSSSBB"],
        ["CCAAAA CC".replace(" ",""),"CAAAAAAC","AAAAAAAA","AAACCAAA","AAACCAAA","AAAAAAAA","AAAAAAAA","AAAAAAAA","AAAAAAAA","BAAAAAAB","BAAAAAAB","BBBBBBBB"]),
    "link": ([(57,130,62),(32,79,48),(93,174,74),(239,190,139),(39,40,38),(249,244,219),(196,159,64),(209,165,51)],
        ["AAAAAAAA","HHHHHHHH","SHHSS HHS".replace(" ",""),"SSwKKwSS","SSSSSSSS","SSSSSSSS","SSSKKSSS","BSSSSSSB"],
        ["AASS SSAA".replace(" ",""),"AASS SSAA".replace(" ",""),"AAHHAAAA","AAAHHAAA","AAAAHHAA","AAAAAHHA","AAAAAAHH","AAAAAAAA","HHHHGHHH","AAAAGAAA","AAAAAAAA","BBBBBBBB"]),
    "sonic": ([(38,100,203),(20,53,133),(69,158,231),(232,198,148),(25,35,50),(251,250,229),(217,47,43),(128,86,56)],
        ["AABBBBAA","ASAAAA SA".replace(" ",""),"AAwwwwAA","AwwKKwwA","AAwKKwAA","SSSSKSSS","SSSSSSSS","BSSKKSSB"],
        ["AAA SSAAA".replace(" ",""),"AASSSSAA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","AASSSSAA","AAAS SAAA".replace(" ",""),"AAAAAAAA","AAAAAAAA","BBBBBBBB"]),
    "samus": ([(232,143,42),(163,84,30),(253,191,57),(212,51,42),(38,45,39),(243,226,178),(73,185,93),(187,53,34)],
        ["HHHSSHHH","HSSSSSSH","HSAGGASH","HGGGGGGH","HBGGGGBH","HSSKKSSH","HSSKKSSH","HHSSSSHH"],
        ["SSSSSSSS","SHSSSSHS","HHSGGSHH","HHSGGSHH","AHSSSSHA","AAHHHHAA","AAAAAAAA","AAACCAAA","AAACCAAA","AAAAAAAA","BBBHHBBB","BBBHHBBB"]),
    "kirby": ([(237,141,181),(185,87,136),(255,187,211),(237,141,181),(35,36,58),(252,248,243),(198,53,98),(147,52,94)],
        ["CCCCCCCC","CAAAAAAC","AAwAAwAA","AAKAAKAA","AAKAAKAA","GGAAAA GG".replace(" ",""),"AAAA AAAA".replace(" ",""),"AAA KKAAA".replace(" ","")],
        ["CCCCCCCC","CAAAAAAC"]+["AAAAAAAA"]*8+["BAAAAAAB","BBBBBBBB"]),
    "pikachu": ([(241,201,54),(188,145,38),(255,228,99),(241,201,54),(43,36,32),(253,245,209),(220,63,50),(142,89,41)],
        ["KKAAAAKK","KKAAAAKK","AAAAAAAA","AAKAAKAA","AAwAAwAA","GGA KKAGG".replace(" ",""),"GGAAAA GG".replace(" ",""),"AAAKKAAA"],
        ["CCCCCCCC","CAAAAAAC"]+["AAAAAAAA"]*7+["AAAAAAAA","BAAAAAAB","BBBBBBBB"]),
    "luigi": ([(51,151,62),(29,93,46),(83,190,84),(241,191,145),(40,53,75),(253,245,225),(62,102,174),(99,61,37)],
        ["AAAAAAAA","AAwwwAAA","SHSSSSHS","SSwKKwSS","SSSSSSSS","SHHSS HHS".replace(" ",""),"SSHHHHSS","SSSKKSSS"],
        ["AAAA AAAA".replace(" ",""),"AGA AAAGA".replace(" ",""),"AGA AAAGA".replace(" ",""),"AGC AACGA".replace(" ",""),"AGGGGGGA","AGGGGGGA","GGGGGGGG","GGGGGGGG","GGGGGGGG","GGGGGGGG","GGGGGGGG","BBBBBBBB"]),
    "peach": ([(229,116,166),(159,67,127),(253,164,197),(245,201,165),(44,48,71),(252,245,222),(224,179,56),(232,192,75)],
        ["HGGGGGGH","HHHHHHHH","HHSSSSHH","SSwKKwSS","HSSSSSSH","HSSSSSSH","HSSKKSSH","HHSSSSHH"],
        ["AAAS SAAA".replace(" ",""),"AACwwCAA","AAA GGAAA".replace(" ",""),"AAACCAAA","AAACCAAA","AAAAAAAA","AAACCAAA","AAACCAAA","AAACCAAA","AAAAAAAA","CCCCCCCC","BBBBBBBB"]),
    "zelda": ([(204,176,213),(134,111,167),(241,221,232),(244,201,161),(46,42,53),(251,248,230),(220,181,62),(214,175,77)],
        ["HHGGGGHH","HHHHHHHH","HSSSSSSH","SSwKKwSS","HSSSSSSH","HSSSSSSH","HSSKKSSH","HHSSSSHH"],
        ["wwwSSwww","wwwwwwww","wGAAAAGw","wAGGGGAw","wAAGGAAw","wAAAAAAw","wAAAAAAw","wAAGGAAw","wAAAAAAw","wAAGGAAw","wwwwwwww","BBBBBBBB"]),
    "donkeykong": ([(132,84,46),(88,53,32),(171,115,64),(219,176,120),(43,33,28),(246,239,213),(202,42,36),(103,61,34)],
        ["HHCCCC HH".replace(" ",""),"HCCCC CCH".replace(" ",""),"CCSSSSCC","CSwKKwSC","SSSSSSSS","SSSSSSSS","SSSKKSSS","CSSSSSSC"],
        ["AASS SSAA".replace(" ",""),"ASSGGSSA","ASSGGSSA","ASSGGSSA","ASGGGGSA","ASGGGGSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","AASSSSAA","BBBBBBBB"]),
    "pacman": ([(244,203,41),(194,142,24),(255,231,93),(244,203,41),(31,35,42),(253,247,217),(229,104,36),(184,71,32)],
        ["CCCCCCCC","CAAAAAAC","AAKAAKAA","AAKAAKAA","AAAAAAAA","AAKKKKAA","AAKwwKAA","AAAKKAAA"],
        ["CCCCCCCC","CAAAAAAC"]+["AAAAAAAA"]*8+["BAAAAAAB","BBBBBBBB"]),
    "lara": ([(61,159,157),(39,105,112),(100,189,177),(220,168,122),(39,40,37),(243,227,197),(133,91,51),(83,55,34)],
        ["HHHHHHHH","HHHHHHHH","HHSSSSSH","SSwKKwSS","SSSSSSSS","SSSSSSSS","SSSKKSSS","HSSSSSSH"],
        ["AASS SSAA".replace(" ",""),"AASS SSAA".replace(" ",""),"AAAAAAAA","AAAAAAAA","AAAAAAAA","AAAAAAAA","AAAAAAAA","AAAAAAAA","GGGGGGGG","GKKGGKKG","GGGGGGGG","BBBBBBBB"]),
    "crash": ([(228,115,35),(168,65,26),(250,161,51),(246,199,126),(47,34,25),(251,246,220),(62,104,183),(129,53,30)],
        ["HHHHHHHH","HHCCCC HH".replace(" ",""),"ACAAAA CA".replace(" ",""),"AwwKKwwA","AAKKKKAA","SSSSSSSS","SSKwwKSS","ASSKKSSA"],
        ["AAAS SAAA".replace(" ",""),"AASSSSAA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","ASSSSSSA","AASSSSAA","GGGGGGGG","GGGGGGGG","GGGGGGGG","BBBBBBBB"]),
}


def build(cid):
    colors, head, torso = DESIGNS[cid]
    legend = {key: (*rgb, 255) for key, rgb in zip("ABCSKwGH", colors)}
    legend["."] = (0, 0, 0, 0)
    sk = Skin(slim=False)
    for part in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
        for face in FACES:
            _, _, w, h = sk.rect(part, "base", face)
            tone = "A" if face in ("front", "top") else "B"
            sk.grid(part, "base", face, [tone*w]*h, legend)
    sk.grid("head", "base", "front", [row.replace(".", "A") for row in head], legend)
    sk.grid("body", "base", "front", torso, legend)
    human = cid in ("link", "luigi", "peach", "zelda", "lara")
    for side in ("right", "left"):
        for face in ("front", "back", "left", "right"):
            arm = ["AAAA"]*7+["SSSS"]*5 if human else ["CCCC"]*3+["AAAA"]*6+["BBBB"]*3
            leg = ["GGGG"]*9+["HHHH"]*3 if human else ["AAAA"]*8+["GGGG"]*4
            sk.grid(side+"_arm", "base", face, arm, legend)
            sk.grid(side+"_leg", "base", face, leg, legend)
    if cid in ("link", "luigi", "megaman", "samus"):
        sk.grid("head", "overlay", "front", [".CCCCCC.","CAAAAAAC","AAAAAAAA"]+["........"]*5, legend)
    elif human:
        sk.grid("head", "overlay", "front", ["HHHHHHHH","H......H"]+["H......H"]*6, legend)
    elif cid == "yoshi":
        sk.grid("head", "overlay", "front", ["........"]*4+[".SSSSSS.","SSSSSSSS","SSSKKSSS",".SSSSSS."], legend)
        sk.grid("body", "base", "back", ["AAAAAAAA"]*2+["AAGGGGAA"]*7+["AAAAAAAA"]*3, legend)
    else:
        sk.grid("head", "overlay", "front", [".CCCCCC."]+["........"]*7, legend)
    return sk
