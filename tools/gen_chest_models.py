"""
Generates static block models for chests (baked into the chunk mesh instead of a per-frame
block entity renderer). Geometry and UVs are converted 1:1 from vanilla's ChestModel
(net.minecraft.client.model.object.chest.ChestModel) and ModelPart.Cube UV layout.

Run from the project root:  python tools/gen_chest_models.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets")
TEX_SIZE = 64.0

# (from xyz, size xyz, texOffs uv, excluded face) in model pixels, y up.
# lid / lock are offset by PartPose (0, 9, 1) in vanilla - already applied here.
SHAPES = {
    "single": [
        ((1, 0, 1), (14, 10, 14), (0, 19), None),
        ((1, 9, 1), (14, 5, 14), (0, 0), None),
        ((7, 7, 15), (2, 4, 1), (0, 0), None),
    ],
    "right": [
        ((1, 0, 1), (15, 10, 14), (0, 19), "east"),
        ((1, 9, 1), (15, 5, 14), (0, 0), "east"),
        ((15, 7, 15), (1, 4, 1), (0, 0), "east"),
    ],
    "left": [
        ((0, 0, 1), (15, 10, 14), (0, 19), "west"),
        ((0, 9, 1), (15, 5, 14), (0, 0), "west"),
        ((0, 7, 15), (1, 4, 1), (0, 0), "west"),
    ],
}


def cube_faces(frm, size, tex):
    """Vanilla ModelPart.Cube: per face, map of vertex position -> (u, v) in texture pixels."""
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + size[0], y0 + size[1], z0 + size[2]
    w, h, d = size
    u, v = tex
    f7, f8, f9, f10, f11, f12 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f13, f14, f15 = v, v + d, v + d + h

    v0, v1, v2, v3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v4, v5, v6, v7 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)

    def poly(verts, u1, vv1, u2, vv2):
        # Polygon: vertex[0] -> (u2, v1), [1] -> (u1, v1), [2] -> (u1, v2), [3] -> (u2, v2)
        uvs = [(u2, vv1), (u1, vv1), (u1, vv2), (u2, vv2)]
        return dict(zip(verts, uvs))

    return {
        "down": poly([v5, v4, v0, v1], f8, f13, f9, f14),
        "up": poly([v2, v3, v7, v6], f9, f14, f10, f13),
        "west": poly([v0, v4, v7, v3], f7, f14, f8, f15),
        "north": poly([v1, v0, v3, v2], f8, f14, f9, f15),
        "east": poly([v5, v1, v2, v6], f9, f14, f11, f15),
        "south": poly([v4, v5, v6, v7], f11, f14, f12, f15),
    }


# For each JSON face: which corner is (u-start, v-start) and which is (u-end, v-end),
# expressed as functions picking min/max per axis (block model default UV orientation).
JSON_CORNERS = {
    "down": (lambda a, b: (a[0], None, b[2]), lambda a, b: (b[0], None, a[2])),
    "up": (lambda a, b: (a[0], None, a[2]), lambda a, b: (b[0], None, b[2])),
    "north": (lambda a, b: (b[0], b[1], None), lambda a, b: (a[0], a[1], None)),
    "south": (lambda a, b: (a[0], b[1], None), lambda a, b: (b[0], a[1], None)),
    "west": (lambda a, b: (None, b[1], a[2]), lambda a, b: (None, a[1], b[2])),
    "east": (lambda a, b: (None, b[1], b[2]), lambda a, b: (None, a[1], a[2])),
}


def lookup(mapping, partial):
    for pos, uv in mapping.items():
        if all(p is None or abs(p - q) < 1e-6 for p, q in zip(partial, pos)):
            return uv
    raise KeyError(partial)


def element(frm, size, tex, excluded):
    to = tuple(f + s for f, s in zip(frm, size))
    faces = {}
    for name, mapping in cube_faces(frm, size, tex).items():
        if name == excluded:
            continue
        start_fn, end_fn = JSON_CORNERS[name]
        su, sv = lookup(mapping, start_fn(frm, to))
        eu, ev = lookup(mapping, end_fn(frm, to))
        k = 16.0 / TEX_SIZE
        faces[name] = {"uv": [round(su * k, 4), round(sv * k, 4), round(eu * k, 4), round(ev * k, 4)], "texture": "#chest"}
    return {"from": list(frm), "to": list(to), "faces": faces}


def write(path, data):
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")


# --- templates ---
for shape, boxes in SHAPES.items():
    write(f"silicium/models/block/chest/template_{shape}.json", {
        "ambientocclusion": False,
        "textures": {"particle": "#particle"},
        "elements": [element(*b) for b in boxes],
    })

# --- chest kinds: block id -> (texture base, particle, double-chest capable) ---
KINDS = {
    "chest": ("normal", "minecraft:block/oak_planks", True),
    "trapped_chest": ("trapped", "minecraft:block/oak_planks", True),
    "ender_chest": ("ender", "minecraft:block/obsidian", False),
}
for prefix, tex in [("", "copper"), ("exposed_", "copper_exposed"), ("weathered_", "copper_weathered"), ("oxidized_", "copper_oxidized")]:
    for waxed in ["", "waxed_"]:
        KINDS[f"{waxed}{prefix}copper_chest"] = (tex, "minecraft:block/copper_block", True)

# Blockstate y-rotation per facing (model front faces south at y=0)
FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}

made_models = set()
for block, (tex, particle, double) in KINDS.items():
    shapes = ["single", "left", "right"] if double else ["single"]
    for shape in shapes:
        name = f"{tex}_{shape}"
        if name in made_models:
            continue
        made_models.add(name)
        texture = f"minecraft:entity/chest/{tex}" + ("" if shape == "single" else f"_{shape}")
        write(f"silicium/models/block/chest/{name}.json", {
            "parent": f"silicium:block/chest/template_{shape}",
            "textures": {"chest": texture, "particle": particle},
        })

    variants = {}
    for facing, y in FACING_Y.items():
        if double:
            for shape in shapes:
                variants[f"facing={facing},type={shape}"] = {"model": f"silicium:block/chest/{tex}_{shape}", "y": y}
        else:
            variants[f"facing={facing}"] = {"model": f"silicium:block/chest/{tex}_single", "y": y}
    for v in variants.values():
        if v["y"] == 0:
            del v["y"]
    write(f"minecraft/blockstates/{block}.json", {"variants": variants})

# Chest entity textures must be in the blocks atlas to be used by block models.
write("minecraft/atlases/blocks.json", {
    "sources": [{"type": "minecraft:directory", "source": "entity/chest", "prefix": "entity/chest/"}]
})

print(f"Generated {len(made_models)} chest models and {len(KINDS)} blockstates")
