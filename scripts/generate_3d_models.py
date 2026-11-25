#!/usr/bin/env python3
"""
Generates Create-style 3D multi-element JSON models, blockstates, 32x32 shaded pixel-art PNG textures,
and Blockbench-compatible (.bbmodel) project files with embedded Base64 textures and outliner groups.
"""

from __future__ import annotations

import base64
import io
import json
import math
import random
import uuid
from pathlib import Path
from typing import Any, Dict, List, Tuple

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
BB_DIR = ROOT / "blockbench_models"
RES_DIR = ROOT / "src" / "main" / "resources" / "assets" / "hydroerosion"
TEX_BLOCK_DIR = RES_DIR / "textures" / "block"
TEX_ITEM_DIR = RES_DIR / "textures" / "item"
MODEL_BLOCK_DIR = RES_DIR / "models" / "block"
MODEL_ITEM_DIR = RES_DIR / "models" / "item"
BLOCKSTATE_DIR = RES_DIR / "blockstates"

VALID_JAVA_ANGLES = (-45, -22.5, 0, 22.5, 45)


def make_uuid(seed_str: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_DNS, f"hydroerosion.{seed_str}"))


def clamp_rgb(r: int, g: int, b: int, a: int = 255) -> Tuple[int, int, int, int]:
    return (max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b))), a)


def shade(rgb: Tuple[int, int, int], delta: int) -> Tuple[int, int, int, int]:
    return clamp_rgb(rgb[0] + delta, rgb[1] + delta, rgb[2] + delta)


def draw_beveled_panel(
    px: Any,
    x0: int,
    y0: int,
    w: int,
    h: int,
    base: Tuple[int, int, int],
    hi: int = 26,
    lo: int = -28,
    noise_seed: int = 1,
    noise_amp: int = 4,
) -> None:
    rng = random.Random(noise_seed)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = rng.randint(-noise_amp, noise_amp) if noise_amp > 0 else 0
            if x == x0 or y == y0:
                px[x, y] = shade(base, hi + n // 2)
            elif x == x0 + w - 1 or y == y0 + h - 1:
                px[x, y] = shade(base, lo + n // 2)
            else:
                px[x, y] = shade(base, n)


def draw_rivet(px: Any, x: int, y: int, metal: Tuple[int, int, int]) -> None:
    px[x, y] = shade(metal, 52)
    if x + 1 < 32:
        px[x + 1, y] = shade(metal, 10)
    if y + 1 < 32:
        px[x, y + 1] = shade(metal, -12)
    if x + 1 < 32 and y + 1 < 32:
        px[x + 1, y + 1] = shade(metal, -44)


def draw_screw_head(px: Any, x: int, y: int, metal: Tuple[int, int, int]) -> None:
    px[x, y] = shade(metal, 40)
    if x + 1 < 32:
        px[x + 1, y] = shade(metal, -32)
    if y + 1 < 32:
        px[x, y + 1] = shade(metal, -35)


# ==============================================================================
# 32x32 PROCEDURAL PIXEL-ART TEXTURE GENERATORS
# ==============================================================================

def gen_turbine_housing_tex() -> Image.Image:
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    brass = (206, 152, 54)
    iron = (64, 70, 80)
    dark_steel = (36, 40, 48)
    bronze = (158, 114, 52)

    # Quadrant (0,0)-(15,15): Brass outer frame + iron chamfer + recessed inner backing
    draw_beveled_panel(px, 0, 0, 16, 16, brass, hi=32, lo=-34, noise_seed=101, noise_amp=5)
    draw_beveled_panel(px, 2, 2, 12, 12, iron, hi=20, lo=-22, noise_seed=102, noise_amp=4)
    draw_beveled_panel(px, 4, 4, 8, 8, dark_steel, hi=-12, lo=10, noise_seed=103, noise_amp=3)
    draw_rivet(px, 1, 1, brass)
    draw_rivet(px, 13, 1, brass)
    draw_rivet(px, 1, 13, brass)
    draw_rivet(px, 13, 13, brass)

    # Quadrant (16,0)-(31,15): Side water nozzle flange & dark inlet bore
    draw_beveled_panel(px, 16, 0, 16, 16, brass, hi=28, lo=-32, noise_seed=104, noise_amp=5)
    draw_beveled_panel(px, 18, 2, 12, 12, iron, hi=18, lo=-24, noise_seed=105, noise_amp=4)
    for y in range(4, 12):
        for x in range(20, 28):
            dist = math.hypot(x - 23.5, y - 7.5)
            if dist <= 2.9:
                px[x, y] = (14, 24, 38, 255) if dist < 2.1 else (42, 76, 116, 255)
            elif dist <= 4.1:
                px[x, y] = shade(brass, 22 if y < 8 else -22)
    draw_screw_head(px, 19, 3, brass)
    draw_screw_head(px, 27, 3, brass)
    draw_screw_head(px, 19, 11, brass)
    draw_screw_head(px, 27, 11, brass)

    # Quadrant (0,16)-(15,31): Heavy iron & brass structural frame beams
    draw_beveled_panel(px, 0, 16, 16, 8, brass, hi=30, lo=-32, noise_seed=106, noise_amp=5)
    draw_beveled_panel(px, 0, 24, 16, 8, iron, hi=22, lo=-26, noise_seed=107, noise_amp=4)
    for x in (2, 7, 12):
        draw_screw_head(px, x, 19, brass)
        draw_screw_head(px, x, 27, iron)

    # Quadrant (16,16)-(31,31): Bronze shaft bearing block
    draw_beveled_panel(px, 16, 16, 16, 16, iron, hi=24, lo=-26, noise_seed=108, noise_amp=4)
    for y in range(18, 30):
        for x in range(18, 30):
            dist = math.hypot(x - 23.5, y - 23.5)
            if dist <= 2.3:
                px[x, y] = (24, 26, 30, 255)
            elif dist <= 4.3:
                px[x, y] = shade(bronze, 26 if (x + y) < 47 else -22)
            elif dist <= 5.6:
                px[x, y] = shade(brass, 20 if y < 24 else -24)
    draw_rivet(px, 17, 17, iron)
    draw_rivet(px, 29, 17, iron)
    draw_rivet(px, 17, 29, iron)
    draw_rivet(px, 29, 29, iron)
    return img


def gen_turbine_rotor_tex() -> Image.Image:
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    steel = (148, 160, 176)
    hub_iron = (94, 104, 118)
    blade_aqua_steel = (118, 152, 184)

    # (0,0)-(15,15): Create-style longitudinal machined shaft texture
    for y in range(0, 16):
        band = int(math.sin((y / 15.0) * math.pi) * 32) - 10
        for x in range(0, 16):
            grain = ((x * 7 + y * 3) % 7) - 3
            if y in (0, 15):
                px[x, y] = shade(steel, -34)
            else:
                px[x, y] = shade(steel, band + grain)

    # (16,0)-(31,15): Bolted rotor hub drum
    draw_beveled_panel(px, 16, 0, 16, 16, hub_iron, hi=28, lo=-30, noise_seed=201, noise_amp=5)
    for y in range(2, 14):
        for x in range(18, 30):
            dist = math.hypot(x - 23.5, y - 7.5)
            if 3.2 <= dist <= 5.2:
                px[x, y] = shade((204, 152, 54), 18 if y < 8 else -22)
    draw_rivet(px, 18, 2, hub_iron)
    draw_rivet(px, 28, 2, hub_iron)
    draw_rivet(px, 18, 12, hub_iron)
    draw_rivet(px, 28, 12, hub_iron)

    # (0,16)-(31,31): Brushed hydro-steel curved turbine blades
    draw_beveled_panel(px, 0, 16, 32, 16, blade_aqua_steel, hi=36, lo=-32, noise_seed=202, noise_amp=5)
    for y in range(17, 31):
        for x in range(1, 31):
            streak = ((x * 5) % 9) - 4
            edge_hi = 28 if y <= 19 else (-20 if y >= 28 else 0)
            px[x, y] = shade(blade_aqua_steel, streak + edge_hi)
    for x in (3, 11, 19, 27):
        draw_rivet(px, x, 27, steel)
    return img


def gen_sensor_tex(powered: bool) -> Image.Image:
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    iron = (108, 114, 124)
    brass = (206, 152, 54)
    glass_bg = (34, 62, 86)

    # (0,0)-(15,15): Riveted iron & brass casing with vent louvers
    draw_beveled_panel(px, 0, 0, 16, 16, iron, hi=26, lo=-28, noise_seed=301, noise_amp=4)
    for x in range(0, 16):
        px[x, 0] = shade(brass, 24)
        px[x, 1] = shade(brass, -10)
        px[x, 14] = shade(brass, 8)
        px[x, 15] = shade(brass, -28)
    for y in (5, 8, 11):
        for x in range(4, 12):
            px[x, y] = (34, 38, 44, 255)
            px[x, y + 1] = shade(iron, 18)
    draw_rivet(px, 1, 2, brass)
    draw_rivet(px, 13, 2, brass)
    draw_rivet(px, 1, 12, brass)
    draw_rivet(px, 13, 12, brass)

    # (16,0)-(31,15): Recessed tempered glass window with specular diagonal gloss
    draw_beveled_panel(px, 16, 0, 16, 16, brass, hi=30, lo=-30, noise_seed=302, noise_amp=4)
    for y in range(18, 30):
        pass
    for y in range(2, 14):
        for x in range(18, 30):
            px[x, y] = shade(glass_bg, ((x + y) % 5) - 2)
    # Diagonal specular streaks
    for d in range(2, 10):
        px[18 + d, 13 - d] = (170, 224, 248, 255)
        if d < 8:
            px[20 + d, 13 - d] = (112, 184, 220, 255)

    # (0,16)-(15,31): White porcelain dial with pressure arc & red pointer swatch
    draw_beveled_panel(px, 0, 16, 16, 16, (238, 236, 226), hi=14, lo=-22, noise_seed=303, noise_amp=2)
    for deg in range(-60, 61, 15):
        rad = math.radians(deg - 90)
        tx = int(round(7.5 + math.cos(rad) * 5.2))
        ty = int(round(25.5 + math.sin(rad) * 5.2))
        if 0 <= tx < 16 and 16 <= ty < 32:
            px[tx, ty] = (205, 42, 42, 255) if deg >= 25 else (44, 52, 62, 255)
    # Red needle strip at bottom-right of dial quadrant
    for y in range(27, 31):
        for x in range(12, 15):
            px[x, y] = (224, 42, 38, 255) if x == 13 else (156, 24, 24, 255)

    # (16,16)-(31,31): Rear redstone output terminal socket
    draw_beveled_panel(px, 16, 16, 16, 16, iron, hi=22, lo=-24, noise_seed=304, noise_amp=3)
    core_col = (255, 72, 48) if powered else (98, 22, 24)
    ring_col = (212, 48, 36) if powered else (62, 16, 18)
    draw_beveled_panel(px, 19, 19, 10, 10, brass, hi=20, lo=-24, noise_seed=305, noise_amp=2)
    draw_beveled_panel(px, 21, 21, 6, 6, ring_col, hi=18, lo=-18, noise_seed=306, noise_amp=2)
    for y in range(22, 26):
        for x in range(22, 26):
            px[x, y] = shade(core_col, 24 if (x in (23, 24) and y in (23, 24)) else -10)
    return img


def gen_intake_tex() -> Image.Image:
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    marine_steel = (62, 82, 108)
    light_steel = (126, 146, 168)
    brass = (196, 146, 54)
    valve_red = (182, 48, 44)

    # (0,0)-(15,15): Heavy dark marine-steel casing
    draw_beveled_panel(px, 0, 0, 16, 16, marine_steel, hi=26, lo=-26, noise_seed=401, noise_amp=5)
    for x in range(1, 15):
        px[x, 8] = shade(marine_steel, -24)
        px[x, 9] = shade(marine_steel, 14)
    draw_rivet(px, 1, 1, light_steel)
    draw_rivet(px, 13, 1, light_steel)
    draw_rivet(px, 1, 13, light_steel)
    draw_rivet(px, 13, 13, light_steel)

    # (16,0)-(31,15): Intake grate bars & deep dark water cavity
    draw_beveled_panel(px, 16, 0, 16, 8, light_steel, hi=30, lo=-28, noise_seed=402, noise_amp=4)
    draw_beveled_panel(px, 16, 8, 16, 8, (18, 28, 42), hi=-8, lo=8, noise_seed=403, noise_amp=3)
    for x in range(18, 30, 3):
        px[x, 3] = shade(light_steel, -28)
        px[x, 4] = shade(light_steel, 24)

    # (0,16)-(15,31): Bolted rear steel pipe flange & bore
    draw_beveled_panel(px, 0, 16, 16, 16, light_steel, hi=28, lo=-30, noise_seed=404, noise_amp=4)
    for y in range(18, 30):
        for x in range(2, 14):
            dist = math.hypot(x - 7.5, y - 23.5)
            if dist <= 3.1:
                px[x, y] = (14, 22, 32, 255)
            elif dist <= 4.4:
                px[x, y] = shade(marine_steel, 22 if y < 24 else -22)
    draw_screw_head(px, 2, 18, light_steel)
    draw_screw_head(px, 12, 18, light_steel)
    draw_screw_head(px, 2, 28, light_steel)
    draw_screw_head(px, 12, 28, light_steel)

    # (16,16)-(31,31): Brass valve stem & crimson cast-iron valve wheel
    draw_beveled_panel(px, 16, 16, 16, 8, valve_red, hi=30, lo=-28, noise_seed=405, noise_amp=4)
    draw_beveled_panel(px, 16, 24, 16, 8, brass, hi=26, lo=-26, noise_seed=406, noise_amp=4)
    return img


def gen_concrete_tex() -> Image.Image:
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    rng = random.Random(501)
    concrete = (124, 128, 132)

    for y in range(32):
        for x in range(32):
            coarse = ((x // 2 + y // 2) % 3 - 1) * 3
            fine = rng.randint(-7, 7)
            px[x, y] = shade(concrete, coarse + fine)

    # Formwork panel seams (horizontal at y=0, 15..16, 31 and vertical at x=0, 16)
    for i in range(32):
        px[i, 0] = shade(concrete, 16)
        px[i, 15] = shade(concrete, -24)
        px[i, 16] = shade(concrete, 16)
        px[i, 31] = shade(concrete, -22)
        px[0, i] = shade(concrete, 14)
        px[15, i] = shade(concrete, -20)
        px[16, i] = shade(concrete, 14)
        px[31, i] = shade(concrete, -20)

    # Recessed form-tie cones with protruding rebar steel hex bolt heads
    bolt_metal = (164, 174, 184)
    for cy in (7, 23):
        for cx in (7, 23):
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    if abs(dx) == 2 or abs(dy) == 2:
                        px[cx + dx, cy + dy] = shade(concrete, -28 if (dx + dy) < 0 else 12)
                    else:
                        px[cx + dx, cy + dy] = shade(bolt_metal, 34 if (dx <= 0 and dy <= 0) else -28)
    return img


def gen_pressure_gauge_tex() -> Image.Image:
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    brass = (212, 158, 56)
    copper = (198, 108, 66)

    # (0,0)-(15,15): Shaded circular brass meter casing & backplate
    draw_beveled_panel(px, 0, 0, 16, 16, brass, hi=32, lo=-32, noise_seed=601, noise_amp=5)
    draw_rivet(px, 2, 2, brass)
    draw_rivet(px, 12, 2, brass)
    draw_rivet(px, 2, 12, brass)
    draw_rivet(px, 12, 12, brass)

    # (16,0)-(31,15): White dial face with green/yellow/red pressure ticks & glass sheen
    draw_beveled_panel(px, 16, 0, 16, 16, (242, 240, 232), hi=12, lo=-18, noise_seed=602, noise_amp=2)
    for deg in range(-120, 121, 20):
        rad = math.radians(deg - 90)
        tx = int(round(23.5 + math.cos(rad) * 5.6))
        ty = int(round(7.5 + math.sin(rad) * 5.6))
        if 16 <= tx < 32 and 0 <= ty < 16:
            if deg >= 40:
                px[tx, ty] = (212, 38, 38, 255)
            elif deg >= -10:
                px[tx, ty] = (216, 158, 28, 255)
            else:
                px[tx, ty] = (46, 156, 86, 255)
    px[19, 3] = (255, 255, 255, 255)
    px[20, 2] = (255, 255, 255, 255)

    # (0,16)-(15,31): Copper connection socket, hex nut, & threaded stem
    draw_beveled_panel(px, 0, 16, 16, 8, copper, hi=34, lo=-32, noise_seed=603, noise_amp=4)
    for y in range(24, 32):
        stripe = 22 if (y % 2 == 0) else -22
        for x in range(0, 16):
            px[x, y] = shade(copper, stripe + (14 if x < 6 else (-18 if x > 10 else 0)))

    # (16,16)-(31,31): Red pointer needle & brass center pin
    draw_beveled_panel(px, 16, 16, 8, 16, (216, 44, 38), hi=30, lo=-28, noise_seed=604, noise_amp=3)
    draw_beveled_panel(px, 24, 16, 8, 16, brass, hi=34, lo=-30, noise_seed=605, noise_amp=3)
    return img


# ==============================================================================
# MODEL & BLOCKBENCH (.bbmodel) BUILDER HELPERS
# ==============================================================================

def quad_uv(u1: float, v1: float, u2: float, v2: float) -> Dict[str, Any]:
    return {
        "north": {"uv": [u1, v1, u2, v2], "texture": "#0"},
        "east": {"uv": [u1, v1, u2, v2], "texture": "#0"},
        "south": {"uv": [u1, v1, u2, v2], "texture": "#0"},
        "west": {"uv": [u1, v1, u2, v2], "texture": "#0"},
        "up": {"uv": [u1, v1, u2, v2], "texture": "#0"},
        "down": {"uv": [u1, v1, u2, v2], "texture": "#0"},
    }


def make_cube(
    name: str,
    group: str,
    frm: List[float],
    to: List[float],
    faces: Dict[str, Any],
    origin: List[float] | None = None,
    rotation: Dict[str, Any] | None = None,
) -> Dict[str, Any]:
    if rotation is not None:
        angle = rotation["angle"]
        axis = rotation["axis"]
        assert angle in VALID_JAVA_ANGLES, f"Invalid Java Block rotation angle: {angle}"
        assert axis in ("x", "y", "z"), f"Invalid rotation axis: {axis}"
    return {
        "name": name,
        "group": group,
        "from": frm,
        "to": to,
        "faces": faces,
        "origin": origin if origin is not None else [8, 8, 8],
        "rotation": rotation,
    }


def to_minecraft_json(
    elements: List[Dict[str, Any]],
    textures: Dict[str, str],
    render_type: str | None = "minecraft:cutout",
    display: Dict[str, Any] | None = None,
) -> Dict[str, Any]:
    mc_elems = []
    for elem in elements:
        obj: Dict[str, Any] = {
            "name": elem["name"],
            "from": elem["from"],
            "to": elem["to"],
            "faces": elem["faces"],
        }
        if elem.get("rotation") is not None:
            obj["rotation"] = {
                "angle": elem["rotation"]["angle"],
                "axis": elem["rotation"]["axis"],
                "origin": elem["rotation"].get("origin", elem["origin"]),
            }
        mc_elems.append(obj)

    model: Dict[str, Any] = {
        "credit": "Generated by Hydro-Erosion 3D Asset Pipeline",
        "texture_size": [32, 32],
        "textures": textures,
        "elements": mc_elems,
    }
    if render_type:
        model["render_type"] = render_type
    if display:
        model["display"] = display
    return model


def img_to_base64(img: Image.Image) -> str:
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


def to_bbmodel(
    model_name: str,
    elements: List[Dict[str, Any]],
    tex_specs: List[Tuple[str, str, Image.Image]],
) -> Dict[str, Any]:
    """
    Exports standard Blockbench java_block (.bbmodel) with 32x32 resolution,
    hierarchical outliner groups (pivot [8, 8, 8]), and embedded Base64 textures.
    """
    tex_key_to_idx: Dict[str, int] = {}
    bb_textures = []
    for idx, (tex_key, rel_name, img) in enumerate(tex_specs):
        tex_key_to_idx[tex_key] = idx
        folder, filename = rel_name.split("/", 1)
        bb_textures.append({
            "path": f"src/main/resources/assets/hydroerosion/textures/{rel_name}",
            "name": filename,
            "folder": folder,
            "namespace": "hydroerosion",
            "id": str(idx),
            "particle": idx == 0,
            "render_mode": "default",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": "",
            "frame_interpolate": False,
            "visible": True,
            "mode": "bitmap",
            "saved": True,
            "uuid": make_uuid(f"{model_name}.tex.{idx}"),
            "source": img_to_base64(img),
        })

    bb_elements = []
    groups: Dict[str, List[str]] = {}
    for idx, elem in enumerate(elements):
        cube_uuid = make_uuid(f"{model_name}.cube.{idx}.{elem['name']}")
        rot_vec = [0.0, 0.0, 0.0]
        origin = elem.get("origin", [8, 8, 8])
        if elem.get("rotation") is not None:
            axis_idx = {"x": 0, "y": 1, "z": 2}[elem["rotation"]["axis"]]
            rot_vec[axis_idx] = elem["rotation"]["angle"]
            origin = elem["rotation"].get("origin", origin)

        bb_faces = {}
        for face_name, fdata in elem["faces"].items():
            tref = fdata["texture"].lstrip("#")
            tidx = tex_key_to_idx.get(tref, 0)
            bb_faces[face_name] = {
                "uv": fdata["uv"],
                "texture": tidx,
            }

        bb_elements.append({
            "name": elem["name"],
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "from": elem["from"],
            "to": elem["to"],
            "autouv": 0,
            "color": idx % 8,
            "origin": origin,
            "rotation": rot_vec,
            "faces": bb_faces,
            "type": "cube",
            "uuid": cube_uuid,
        })
        groups.setdefault(elem["group"], []).append(cube_uuid)

    outliner = []
    for gname, child_uuids in groups.items():
        outliner.append({
            "name": gname,
            "origin": [8, 8, 8],
            "color": 0,
            "uuid": make_uuid(f"{model_name}.group.{gname}"),
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": child_uuids,
        })

    return {
        "meta": {
            "format_version": "4.9",
            "model_format": "java_block",
            "box_uv": False,
        },
        "name": model_name,
        "model_identifier": "",
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": 32, "height": 32},
        "elements": bb_elements,
        "outliner": outliner,
        "textures": bb_textures,
    }


STANDARD_BLOCK_DISPLAY = {
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
}


# ==============================================================================
# 1. HIGH-PRESSURE TURBINE (BASE + ROTOR)
# ==============================================================================

def build_turbine_base_elements(tex_ref: str = "#0") -> List[Dict[str, Any]]:
    elems: List[Dict[str, Any]] = []
    frame_faces = {
        k: {"uv": [0, 8, 8, 12], "texture": tex_ref} for k in ("north", "east", "south", "west", "up", "down")
    }
    iron_faces = {
        k: {"uv": [0, 12, 8, 16], "texture": tex_ref} for k in ("north", "east", "south", "west", "up", "down")
    }
    bearing_faces = {
        k: {"uv": [8, 8, 16, 16], "texture": tex_ref} for k in ("north", "east", "south", "west", "up", "down")
    }
    nozzle_faces = {
        "north": {"uv": [0, 8, 8, 12], "texture": tex_ref},
        "south": {"uv": [0, 8, 8, 12], "texture": tex_ref},
        "up": {"uv": [0, 8, 8, 12], "texture": tex_ref},
        "down": {"uv": [0, 8, 8, 12], "texture": tex_ref},
        "west": {"uv": [8, 0, 16, 8], "texture": tex_ref},
        "east": {"uv": [8, 0, 16, 8], "texture": tex_ref},
    }

    # Frame pillars: 4 longitudinal brass/iron rails along Z + 4 vertical corner struts on front/back
    for name, frm, to in [
        ("rail_bottom_left", [0, 0, 0], [2.5, 2.5, 16]),
        ("rail_bottom_right", [13.5, 0, 0], [16, 2.5, 16]),
        ("rail_top_left", [0, 13.5, 0], [2.5, 16, 16]),
        ("rail_top_right", [13.5, 13.5, 0], [16, 16, 16]),
        ("strut_front_left", [0, 2.5, 0], [2.5, 13.5, 2.5]),
        ("strut_front_right", [13.5, 2.5, 0], [16, 13.5, 2.5]),
        ("strut_back_left", [0, 2.5, 13.5], [2.5, 13.5, 16]),
        ("strut_back_right", [13.5, 2.5, 13.5], [16, 13.5, 16]),
    ]:
        elems.append(make_cube(name, "frame_pillars", frm, to, frame_faces))

    # Crossbars and bearings on Z=0..2.5 and Z=13.5..16 (so shaft axis Z passes cleanly through [5.5..10.5])
    for name, frm, to in [
        ("crossbar_front_bottom", [2.5, 0, 0], [13.5, 2.2, 2.2]),
        ("crossbar_front_top", [2.5, 13.8, 0], [13.5, 16, 2.2]),
        ("crossbar_back_bottom", [2.5, 0, 13.8], [13.5, 2.2, 16]),
        ("crossbar_back_top", [2.5, 13.8, 13.8], [13.5, 16, 16]),
        ("stanchion_front_bottom", [6.5, 2.2, 0.3], [9.5, 5.5, 2.2]),
        ("stanchion_front_top", [6.5, 10.5, 0.3], [9.5, 13.8, 2.2]),
        ("stanchion_back_bottom", [6.5, 2.2, 13.8], [9.5, 5.5, 15.7]),
        ("stanchion_back_top", [6.5, 10.5, 13.8], [9.5, 13.8, 15.7]),
    ]:
        elems.append(make_cube(name, "frame_pillars", frm, to, iron_faces))

    elems.append(make_cube("bearing_front", "bearings", [5.2, 5.2, 0.0], [10.8, 10.8, 2.6], bearing_faces))
    elems.append(make_cube("bearing_back", "bearings", [5.2, 5.2, 13.4], [10.8, 10.8, 16.0], bearing_faces))

    # Side high-pressure water nozzles on West and East faces
    elems.append(make_cube("nozzle_west_flange", "side_nozzles", [0.0, 4.0, 4.0], [1.4, 12.0, 12.0], nozzle_faces))
    elems.append(make_cube("nozzle_west_throat", "side_nozzles", [1.4, 5.2, 5.2], [2.6, 10.8, 10.8], iron_faces))
    elems.append(make_cube("nozzle_east_flange", "side_nozzles", [14.6, 4.0, 4.0], [16.0, 12.0, 12.0], nozzle_faces))
    elems.append(make_cube("nozzle_east_throat", "side_nozzles", [13.4, 5.2, 5.2], [14.6, 10.8, 10.8], iron_faces))
    return elems


def build_turbine_rotor_elements(tex_ref: str = "#0") -> List[Dict[str, Any]]:
    elems: List[Dict[str, Any]] = []
    shaft_faces = {
        k: {"uv": [0, 0, 8, 8], "texture": tex_ref} for k in ("north", "east", "south", "west", "up", "down")
    }
    hub_faces = {
        k: {"uv": [8, 0, 16, 8], "texture": tex_ref} for k in ("north", "east", "south", "west", "up", "down")
    }
    blade_faces = {
        k: {"uv": [0, 8, 16, 16], "texture": tex_ref} for k in ("north", "east", "south", "west", "up", "down")
    }

    # Central shaft & octagonal runner hub centered at [8, 8, 8]
    elems.append(make_cube("central_shaft", "shaft", [6.2, 6.2, 0.0], [9.8, 9.8, 16.0], shaft_faces, [8, 8, 8]))
    elems.append(make_cube("runner_hub_a", "shaft", [5.2, 5.2, 5.0], [10.8, 10.8, 11.0], hub_faces, [8, 8, 8]))
    elems.append(make_cube(
        "runner_hub_b", "shaft", [5.2, 5.2, 5.2], [10.8, 10.8, 10.8], hub_faces, [8, 8, 8],
        {"angle": 45, "axis": "z", "origin": [8, 8, 8]}
    ))

    # 8 curved metal blades around [8, 8, 8] staying strictly inside radius <= 4.7 from center (3.3..12.7)
    # 4 cardinal directions (0, 90, 180, 270) + 4 diagonal directions (rotated 45 on Z)
    cardinal_roots = [
        ("blade_n", [7.4, 10.2, 5.3], [8.6, 12.1, 10.7], [7.2, 11.6, 5.4], [8.4, 12.8, 10.6], 22.5),
        ("blade_s", [7.4, 3.9, 5.3], [8.6, 5.8, 10.7], [7.6, 3.2, 5.4], [8.8, 4.4, 10.6], 22.5),
        ("blade_e", [10.2, 7.4, 5.3], [12.1, 8.6, 10.7], [11.6, 7.6, 5.4], [12.8, 8.8, 10.6], 22.5),
        ("blade_w", [3.9, 7.4, 5.3], [5.8, 8.6, 10.7], [3.2, 7.2, 5.4], [4.4, 8.4, 10.6], 22.5),
    ]
    for bname, r_from, r_to, t_from, t_to, tip_ang in cardinal_roots:
        elems.append(make_cube(f"{bname}_root", "blades", r_from, r_to, blade_faces, [8, 8, 8]))
        elems.append(make_cube(
            f"{bname}_tip", "blades", t_from, t_to, blade_faces, [8, 8, 8],
            {"angle": tip_ang, "axis": "z", "origin": [8, 8, 8]}
        ))

    diag_defs = [
        ("blade_ne", [7.4, 10.2, 5.3], [8.6, 12.5, 10.7], 45),
        ("blade_nw", [7.4, 10.2, 5.3], [8.6, 12.5, 10.7], -45),
        ("blade_se", [7.4, 3.5, 5.3], [8.6, 5.8, 10.7], -45),
        ("blade_sw", [7.4, 3.5, 5.3], [8.6, 5.8, 10.7], 45),
    ]
    for bname, r_from, r_to, ang in diag_defs:
        elems.append(make_cube(
            f"{bname}_paddle", "blades", r_from, r_to, blade_faces, [8, 8, 8],
            {"angle": ang, "axis": "z", "origin": [8, 8, 8]}
        ))
        # Subtle scoop lip angled 22.5 deg for curved appearance
        lip_y0 = 11.7 if r_to[1] > 8 else 3.2
        lip_y1 = 12.6 if r_to[1] > 8 else 4.1
        elems.append(make_cube(
            f"{bname}_lip", "blades", [7.8, lip_y0, 5.4], [8.9, lip_y1, 10.6], blade_faces, [8, 8, 8],
            {"angle": 22.5 if ang > 0 else -22.5, "axis": "z", "origin": [8, 8, 8]}
        ))
    return elems


# ==============================================================================
# 2. WATER PRESSURE SENSOR
# ==============================================================================

def build_sensor_elements(powered: bool) -> List[Dict[str, Any]]:
    elems: List[Dict[str, Any]] = []
    casing_faces = {
        k: {"uv": [0, 0, 8, 8], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    bezel_faces = {
        k: {"uv": [8, 0, 16, 2], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    glass_faces = {
        k: {"uv": [9, 1, 15, 7], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    dial_faces = {
        k: {"uv": [0, 8, 8, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    needle_faces = {
        k: {"uv": [6, 13.5, 7.5, 15.5], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    terminal_faces = {
        k: {"uv": [8, 8, 16, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }

    # Main casing + top/bottom brass plates
    elems.append(make_cube("body_core", "casing", [1, 2, 2], [15, 14, 14], casing_faces))
    elems.append(make_cube("plate_bottom", "casing", [0, 0, 1], [16, 2, 15], bezel_faces))
    elems.append(make_cube("plate_top", "casing", [0, 14, 1], [16, 16, 15], bezel_faces))

    # Recessed front brass bezel (facing NORTH z=0.5..2.0)
    elems.append(make_cube("bezel_top", "front_window", [2, 11.5, 0.5], [14, 13.5, 2.0], bezel_faces))
    elems.append(make_cube("bezel_bottom", "front_window", [2, 2.5, 0.5], [14, 4.5, 2.0], bezel_faces))
    elems.append(make_cube("bezel_left", "front_window", [2, 4.5, 0.5], [4, 11.5, 2.0], bezel_faces))
    elems.append(make_cube("bezel_right", "front_window", [12, 4.5, 0.5], [14, 11.5, 2.0], bezel_faces))

    # Recessed white porcelain dial & protective glass window
    elems.append(make_cube("dial_plate", "front_window", [4, 4.5, 1.6], [12, 11.5, 2.0], dial_faces))
    elems.append(make_cube("glass_pane", "front_window", [4, 4.5, 0.8], [12, 11.5, 1.05], glass_faces))

    # 3D Red pressure pointer needle inside the recessed window
    needle_angle = 22.5 if powered else -22.5
    elems.append(make_cube(
        "pressure_needle", "needle", [7.5, 5.8, 1.2], [8.5, 10.6, 1.55], needle_faces,
        [8, 6.2, 1.4], {"angle": needle_angle, "axis": "z", "origin": [8, 6.2, 1.4]}
    ))
    elems.append(make_cube("needle_hub", "needle", [7.3, 5.5, 1.1], [8.7, 6.9, 1.6], bezel_faces, [8, 6.2, 1.4]))

    # Rear redstone terminal socket (SOUTH z=14..16)
    elems.append(make_cube("terminal_collar", "rear_terminal", [4.5, 4.5, 14.0], [11.5, 11.5, 15.2], bezel_faces))
    elems.append(make_cube("terminal_core", "rear_terminal", [6.0, 6.0, 15.2], [10.0, 10.0, 16.0], terminal_faces))
    return elems


# ==============================================================================
# 3. DEEP PRESSURE INTAKE
# ==============================================================================

def build_intake_elements() -> List[Dict[str, Any]]:
    elems: List[Dict[str, Any]] = []
    steel_faces = {
        k: {"uv": [0, 0, 8, 8], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    grate_faces = {
        k: {"uv": [8, 0, 16, 4], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    cavity_faces = {
        k: {"uv": [8, 4, 16, 8], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    flange_faces = {
        k: {"uv": [0, 8, 8, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    wheel_faces = {
        k: {"uv": [8, 8, 16, 12], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    stem_faces = {
        k: {"uv": [8, 12, 16, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }

    # Heavy marine-steel housing & base/top plates
    elems.append(make_cube("base_plate", "housing", [0, 0, 0], [16, 2, 16], steel_faces))
    elems.append(make_cube("top_plate", "housing", [0, 12, 1], [16, 14, 14], steel_faces))
    elems.append(make_cube("main_chamber", "housing", [1.5, 2, 2.0], [14.5, 12, 13.0], cavity_faces))
    elems.append(make_cube("cheek_left", "housing", [0.5, 2, 0], [3.0, 12, 3.0], steel_faces))
    elems.append(make_cube("cheek_right", "housing", [13.0, 2, 0], [15.5, 12, 3.0], steel_faces))

    # Front gridded intake louvers (z=0.3..1.9)
    elems.append(make_cube("louver_bar_1", "intake_slits", [3.0, 3.4, 0.3], [13.0, 4.8, 1.9], grate_faces))
    elems.append(make_cube("louver_bar_2", "intake_slits", [3.0, 6.3, 0.3], [13.0, 7.7, 1.9], grate_faces))
    elems.append(make_cube("louver_bar_3", "intake_slits", [3.0, 9.2, 0.3], [13.0, 10.6, 1.9], grate_faces))
    elems.append(make_cube("louver_center_rib", "intake_slits", [7.2, 2.0, 0.5], [8.8, 12.0, 1.9], grate_faces))

    # Rear bolted steel pipe flange (z=13..16)
    elems.append(make_cube("pipe_neck", "flange_pipe", [4.0, 3.0, 13.0], [12.0, 11.0, 14.8], steel_faces))
    elems.append(make_cube("pipe_flange", "flange_pipe", [2.2, 1.5, 14.8], [13.8, 12.5, 16.0], flange_faces))

    # Top pressure valve stem + crimson handwheel
    elems.append(make_cube("valve_stem", "valve_wheel", [7.0, 14.0, 6.5], [9.0, 15.4, 8.5], stem_faces))
    elems.append(make_cube("wheel_rim_n", "valve_wheel", [5.0, 15.2, 4.5], [11.0, 16.0, 5.5], wheel_faces))
    elems.append(make_cube("wheel_rim_s", "valve_wheel", [5.0, 15.2, 9.5], [11.0, 16.0, 10.5], wheel_faces))
    elems.append(make_cube("wheel_rim_w", "valve_wheel", [5.0, 15.2, 5.5], [6.0, 16.0, 9.5], wheel_faces))
    elems.append(make_cube("wheel_rim_e", "valve_wheel", [10.0, 15.2, 5.5], [11.0, 16.0, 9.5], wheel_faces))
    elems.append(make_cube("wheel_spoke_x", "valve_wheel", [6.0, 15.3, 7.1], [10.0, 15.9, 7.9], wheel_faces))
    elems.append(make_cube("wheel_spoke_z", "valve_wheel", [7.6, 15.3, 5.5], [8.4, 15.9, 9.5], wheel_faces))
    return elems


# ==============================================================================
# 4. REINFORCED DAM CONCRETE
# ==============================================================================

def build_concrete_elements() -> List[Dict[str, Any]]:
    faces = {
        k: {"uv": [0, 0, 16, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    return [make_cube("dam_concrete_block", "concrete_block", [0, 0, 0], [16, 16, 16], faces)]


# ==============================================================================
# 5. WATER PRESSURE GAUGE (3D ITEM)
# ==============================================================================

def build_pressure_gauge_elements() -> List[Dict[str, Any]]:
    elems: List[Dict[str, Any]] = []
    brass_faces = {
        k: {"uv": [0, 0, 8, 8], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    dial_faces = {
        k: {"uv": [8, 0, 16, 8], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    copper_faces = {
        k: {"uv": [0, 8, 8, 12], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    thread_faces = {
        k: {"uv": [0, 12, 8, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    needle_faces = {
        k: {"uv": [8, 8, 12, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }
    cap_faces = {
        k: {"uv": [12, 8, 16, 16], "texture": "#0"} for k in ("north", "east", "south", "west", "up", "down")
    }

    # Circular octagonal brass body centered at [8, 9.5, 8]
    elems.append(make_cube("housing_oct_a", "brass_body", [4.2, 5.7, 7.0], [11.8, 13.3, 9.5], brass_faces, [8, 9.5, 8]))
    elems.append(make_cube(
        "housing_oct_b", "brass_body", [4.2, 5.7, 7.1], [11.8, 13.3, 9.4], brass_faces, [8, 9.5, 8],
        {"angle": 45, "axis": "z", "origin": [8, 9.5, 8]}
    ))
    # Front raised brass bezel rim
    elems.append(make_cube("bezel_top", "brass_body", [5.0, 12.2, 6.4], [11.0, 13.2, 7.0], brass_faces, [8, 9.5, 8]))
    elems.append(make_cube("bezel_bottom", "brass_body", [5.0, 5.8, 6.4], [11.0, 6.8, 7.0], brass_faces, [8, 9.5, 8]))
    elems.append(make_cube("bezel_left", "brass_body", [4.3, 6.8, 6.4], [5.3, 12.2, 7.0], brass_faces, [8, 9.5, 8]))
    elems.append(make_cube("bezel_right", "brass_body", [10.7, 6.8, 6.4], [11.7, 12.2, 7.0], brass_faces, [8, 9.5, 8]))

    # White dial face
    elems.append(make_cube("white_dial", "dial_face", [5.1, 6.6, 6.75], [10.9, 12.4, 7.02], dial_faces, [8, 9.5, 8]))

    # Red needle + brass center pivot cap
    elems.append(make_cube(
        "red_needle", "needle", [7.65, 9.2, 6.48], [8.35, 12.1, 6.72], needle_faces, [8, 9.5, 8],
        {"angle": -22.5, "axis": "z", "origin": [8, 9.5, 8]}
    ))
    elems.append(make_cube("center_pin", "needle", [7.4, 8.9, 6.38], [8.6, 10.1, 6.75], cap_faces, [8, 9.5, 8]))

    # Bottom copper connection socket & hex nut
    elems.append(make_cube("socket_neck", "copper_socket", [7.0, 3.8, 7.3], [9.0, 5.7, 9.2], brass_faces, [8, 8, 8]))
    elems.append(make_cube("copper_hex_nut", "copper_socket", [6.4, 2.7, 6.9], [9.6, 4.0, 9.6], copper_faces, [8, 8, 8]))
    elems.append(make_cube("copper_thread_stem", "copper_socket", [7.1, 0.8, 7.4], [8.9, 2.8, 9.1], thread_faces, [8, 8, 8]))
    return elems


PRESSURE_GAUGE_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2.5, 1.0], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 2.5, 1.0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, 150, 0], "translation": [1.0, 3.0, 0.5], "scale": [0.65, 0.65, 0.65]},
    "firstperson_lefthand": {"rotation": [0, 150, 0], "translation": [1.0, 3.0, 0.5], "scale": [0.65, 0.65, 0.65]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
    "gui": {"rotation": [15, 195, 0], "translation": [0, 0.5, 0], "scale": [0.95, 0.95, 0.95]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0.5, -0.5], "scale": [0.9, 0.9, 0.9]},
}


# ==============================================================================
# FILE OUTPUT DRIVER
# ==============================================================================

def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    for d in (BB_DIR, TEX_BLOCK_DIR, TEX_ITEM_DIR, MODEL_BLOCK_DIR, MODEL_ITEM_DIR, BLOCKSTATE_DIR):
        d.mkdir(parents=True, exist_ok=True)

    # 1. Generate 32x32 shaded textures
    tex_housing = gen_turbine_housing_tex()
    tex_rotor = gen_turbine_rotor_tex()
    tex_sensor_off = gen_sensor_tex(False)
    tex_sensor_on = gen_sensor_tex(True)
    tex_intake = gen_intake_tex()
    tex_concrete = gen_concrete_tex()
    tex_gauge = gen_pressure_gauge_tex()

    tex_housing.save(TEX_BLOCK_DIR / "high_pressure_turbine_housing.png")
    tex_rotor.save(TEX_BLOCK_DIR / "high_pressure_turbine_rotor.png")
    tex_sensor_off.save(TEX_BLOCK_DIR / "water_pressure_sensor_off.png")
    tex_sensor_on.save(TEX_BLOCK_DIR / "water_pressure_sensor_on.png")
    tex_intake.save(TEX_BLOCK_DIR / "deep_pressure_intake.png")
    tex_concrete.save(TEX_BLOCK_DIR / "reinforced_dam_concrete.png")
    tex_gauge.save(TEX_ITEM_DIR / "pressure_gauge.png")

    # 2. High-Pressure Turbine (2-part base + rotor, combined item model, 6-way blockstate, 3 .bbmodel files)
    base_elems = build_turbine_base_elements("#0")
    rotor_elems = build_turbine_rotor_elements("#0")

    write_json(
        MODEL_BLOCK_DIR / "high_pressure_turbine_base.json",
        to_minecraft_json(
            base_elems,
            {"0": "hydroerosion:block/high_pressure_turbine_housing", "particle": "hydroerosion:block/high_pressure_turbine_housing"},
            "minecraft:cutout",
            STANDARD_BLOCK_DISPLAY,
        ),
    )
    write_json(
        MODEL_BLOCK_DIR / "high_pressure_turbine_rotor.json",
        to_minecraft_json(
            rotor_elems,
            {"0": "hydroerosion:block/high_pressure_turbine_rotor", "particle": "hydroerosion:block/high_pressure_turbine_rotor"},
            "minecraft:cutout",
            STANDARD_BLOCK_DISPLAY,
        ),
    )

    combined_turbine_elems = build_turbine_base_elements("#0") + build_turbine_rotor_elements("#1")
    write_json(
        MODEL_ITEM_DIR / "high_pressure_turbine.json",
        to_minecraft_json(
            combined_turbine_elems,
            {
                "0": "hydroerosion:block/high_pressure_turbine_housing",
                "1": "hydroerosion:block/high_pressure_turbine_rotor",
                "particle": "hydroerosion:block/high_pressure_turbine_housing",
            },
            "minecraft:cutout",
            STANDARD_BLOCK_DISPLAY,
        ),
    )
    write_json(
        BLOCKSTATE_DIR / "high_pressure_turbine.json",
        {
            "variants": {
                "facing=south": {"model": "hydroerosion:block/high_pressure_turbine_base"},
                "facing=north": {"model": "hydroerosion:block/high_pressure_turbine_base", "y": 180},
                "facing=west": {"model": "hydroerosion:block/high_pressure_turbine_base", "y": 90},
                "facing=east": {"model": "hydroerosion:block/high_pressure_turbine_base", "y": 270},
                "facing=up": {"model": "hydroerosion:block/high_pressure_turbine_base", "x": 270},
                "facing=down": {"model": "hydroerosion:block/high_pressure_turbine_base", "x": 90},
            }
        },
    )

    write_json(
        BB_DIR / "high_pressure_turbine_base.bbmodel",
        to_bbmodel("high_pressure_turbine_base", base_elems, [("0", "block/high_pressure_turbine_housing.png", tex_housing)]),
    )
    write_json(
        BB_DIR / "high_pressure_turbine_rotor.bbmodel",
        to_bbmodel("high_pressure_turbine_rotor", rotor_elems, [("0", "block/high_pressure_turbine_rotor.png", tex_rotor)]),
    )
    write_json(
        BB_DIR / "high_pressure_turbine.bbmodel",
        to_bbmodel(
            "high_pressure_turbine",
            combined_turbine_elems,
            [
                ("0", "block/high_pressure_turbine_housing.png", tex_housing),
                ("1", "block/high_pressure_turbine_rotor.png", tex_rotor),
            ],
        ),
    )

    # 3. Water Pressure Sensor
    sensor_off_elems = build_sensor_elements(False)
    sensor_on_elems = build_sensor_elements(True)
    write_json(
        MODEL_BLOCK_DIR / "water_pressure_sensor_off.json",
        to_minecraft_json(
            sensor_off_elems,
            {"0": "hydroerosion:block/water_pressure_sensor_off", "particle": "hydroerosion:block/water_pressure_sensor_off"},
            "minecraft:cutout",
            STANDARD_BLOCK_DISPLAY,
        ),
    )
    write_json(
        MODEL_BLOCK_DIR / "water_pressure_sensor_on.json",
        to_minecraft_json(
            sensor_on_elems,
            {"0": "hydroerosion:block/water_pressure_sensor_on", "particle": "hydroerosion:block/water_pressure_sensor_on"},
            "minecraft:cutout",
            STANDARD_BLOCK_DISPLAY,
        ),
    )
    write_json(MODEL_ITEM_DIR / "water_pressure_sensor.json", {"parent": "hydroerosion:block/water_pressure_sensor_off"})

    sensor_variants: Dict[str, Any] = {}
    facing_y = {"north": 0, "east": 90, "south": 180, "west": 270}
    for f, yrot in facing_y.items():
        for p in range(16):
            model_id = f"hydroerosion:block/water_pressure_sensor_{'on' if p > 0 else 'off'}"
            entry: Dict[str, Any] = {"model": model_id}
            if yrot != 0:
                entry["y"] = yrot
            sensor_variants[f"facing={f},power={p}"] = entry
    write_json(BLOCKSTATE_DIR / "water_pressure_sensor.json", {"variants": sensor_variants})
    write_json(
        BB_DIR / "water_pressure_sensor.bbmodel",
        to_bbmodel("water_pressure_sensor", sensor_off_elems, [("0", "block/water_pressure_sensor_off.png", tex_sensor_off)]),
    )

    # 4. Deep Pressure Intake
    intake_elems = build_intake_elements()
    write_json(
        MODEL_BLOCK_DIR / "deep_pressure_intake.json",
        to_minecraft_json(
            intake_elems,
            {"0": "hydroerosion:block/deep_pressure_intake", "particle": "hydroerosion:block/deep_pressure_intake"},
            "minecraft:cutout",
            STANDARD_BLOCK_DISPLAY,
        ),
    )
    write_json(MODEL_ITEM_DIR / "deep_pressure_intake.json", {"parent": "hydroerosion:block/deep_pressure_intake"})
    write_json(
        BLOCKSTATE_DIR / "deep_pressure_intake.json",
        {
            "variants": {
                "facing=north": {"model": "hydroerosion:block/deep_pressure_intake"},
                "facing=east": {"model": "hydroerosion:block/deep_pressure_intake", "y": 90},
                "facing=south": {"model": "hydroerosion:block/deep_pressure_intake", "y": 180},
                "facing=west": {"model": "hydroerosion:block/deep_pressure_intake", "y": 270},
            }
        },
    )
    write_json(
        BB_DIR / "deep_pressure_intake.bbmodel",
        to_bbmodel("deep_pressure_intake", intake_elems, [("0", "block/deep_pressure_intake.png", tex_intake)]),
    )

    # 5. Reinforced Dam Concrete
    concrete_elems = build_concrete_elements()
    write_json(
        MODEL_BLOCK_DIR / "reinforced_dam_concrete.json",
        to_minecraft_json(
            concrete_elems,
            {"0": "hydroerosion:block/reinforced_dam_concrete", "particle": "hydroerosion:block/reinforced_dam_concrete"},
            None,
            STANDARD_BLOCK_DISPLAY,
        ),
    )
    write_json(MODEL_ITEM_DIR / "reinforced_dam_concrete.json", {"parent": "hydroerosion:block/reinforced_dam_concrete"})
    write_json(
        BLOCKSTATE_DIR / "reinforced_dam_concrete.json",
        {"variants": {"": {"model": "hydroerosion:block/reinforced_dam_concrete"}}},
    )
    write_json(
        BB_DIR / "reinforced_dam_concrete.bbmodel",
        to_bbmodel("reinforced_dam_concrete", concrete_elems, [("0", "block/reinforced_dam_concrete.png", tex_concrete)]),
    )

    # 6. Water Pressure Gauge (3D item model)
    gauge_elems = build_pressure_gauge_elements()
    write_json(
        MODEL_ITEM_DIR / "pressure_gauge.json",
        to_minecraft_json(
            gauge_elems,
            {"0": "hydroerosion:item/pressure_gauge", "particle": "hydroerosion:item/pressure_gauge"},
            "minecraft:cutout",
            PRESSURE_GAUGE_DISPLAY,
        ),
    )
    write_json(
        BB_DIR / "pressure_gauge.bbmodel",
        to_bbmodel("pressure_gauge", gauge_elems, [("0", "item/pressure_gauge.png", tex_gauge)]),
    )

    print("Generated 32x32 textures, 3D JSON models, blockstates, and .bbmodel files successfully.")


if __name__ == "__main__":
    main()
