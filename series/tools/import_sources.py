"""Build CE content and plugin catalogs from pinned, unmodified upstream commits.

Usage: python series/tools/import_sources.py --assets series/build/packs
Assets are local build outputs; their upstream licenses are kept with the packs.
"""
from __future__ import annotations

import copy
from deco_interactions import rules as deco_rules, link_variants, selected_model

import argparse
import io
import json
import re
import subprocess
import tarfile
from zipfile import ZipFile, ZIP_DEFLATED
from collections import Counter
from pathlib import Path, PurePosixPath
from typing import Any

SUITE = Path(__file__).resolve().parents[1]
WORKSPACE = SUITE.parent.parent
LOCK = SUITE.parent / "docs/research/organization-checkouts.json"
MODULE_SPECS = SUITE / "tools/module-specs.json"
PACK_VERSION = "0.1.0-dev"  # fallback for callers of pack_groups in older tests
PACK_MODULES = ("doll", "tavern", "deco", "music", "shared")
ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+")
COLORS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
          "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")
# Forge tags have no registry on a vanilla server; retain their vanilla members.
VANILLA_TAG_MEMBERS = {
    **{f"forge:ingots/{metal}": [f"minecraft:{metal}_ingot"] for metal in ("iron", "gold", "copper")},
    **{f"forge:nuggets/{metal}": [f"minecraft:{metal}_nugget"] for metal in ("iron", "gold")},
    **{f"forge:dyes/{color}": [f"minecraft:{color}_dye"] for color in COLORS},
    "forge:gems/diamond": ["minecraft:diamond"],
    "forge:glass_panes": ["minecraft:glass_pane", *[f"minecraft:{color}_stained_glass_pane" for color in COLORS]],
}


def pairs_unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    values: dict[str, Any] = {}
    for key, value in pairs:
        if key in values:
            raise ValueError(f"Duplicate JSON key: {key}")
        values[key] = value
    return values


def parse_json(content: bytes) -> Any:
    return json.loads(content.decode("utf-8-sig"), object_pairs_hook=pairs_unique)


def load_module_specs() -> tuple[str, tuple[dict[str, str], ...], str]:
    raw = parse_json(MODULE_SPECS.read_bytes())
    if not isinstance(raw, dict) or set(raw) != {"pack_version", "modules", "shared_pack"}:
        raise ValueError("module-specs.json has unsupported fields")
    version = raw["pack_version"]
    shared = raw["shared_pack"]
    modules = raw["modules"]
    if not isinstance(version, str) or not re.fullmatch(r"[A-Za-z0-9._-]+", version):
        raise ValueError("module-specs.json has an invalid pack version")
    if not isinstance(shared, str) or not re.fullmatch(r"[a-z0-9_-]+", shared):
        raise ValueError("module-specs.json has an invalid shared pack id")
    if not isinstance(modules, list) or not modules:
        raise ValueError("module-specs.json has no modules")
    result: list[dict[str, str]] = []
    seen: set[str] = set()
    for module in modules:
        required = {"module", "sourceRepository", "namespace", "packNamespace", "converter"}
        if not isinstance(module, dict) or set(module) != required:
            raise ValueError("module-specs.json has an invalid module entry")
        module_id = module["module"]
        if (not isinstance(module_id, str) or not re.fullmatch(r"[a-z0-9_-]+", module_id)
                or module_id in seen):
            raise ValueError("module-specs.json has duplicate or invalid module id")
        if not isinstance(module["sourceRepository"], str) or not module["sourceRepository"]:
            raise ValueError("module-specs.json has an invalid repository")
        for field in ("namespace", "packNamespace"):
            if not isinstance(module[field], str) or not re.fullmatch(r"[a-z0-9_-]+", module[field]):
                raise ValueError(f"module-specs.json has an invalid {field}")
        if not isinstance(module["converter"], str) or not module["converter"]:
            raise ValueError("module-specs.json has an invalid converter")
        if module["namespace"] == module["packNamespace"]:
            raise ValueError("module-specs.json must separate source and pack namespaces")
        seen.add(module_id)
        result.append(module)
    if shared in seen:
        raise ValueError("shared pack id collides with a module")
    return version, tuple(result), shared


def git(root: Path, *args: str) -> bytes:
    return subprocess.run(["git", "-C", str(root), *args], check=True, capture_output=True).stdout


class Source:
    def __init__(self, name: str, commit: str) -> None:
        self.name = name
        self.commit = commit
        root = WORKSPACE / ".references" / name
        if git(root, "rev-parse", "HEAD").decode().strip() != commit:
            raise ValueError(f"{name}: checkout does not match pinned commit {commit}")
        if git(root, "status", "--porcelain", "--untracked-files=no").strip():
            raise ValueError(f"{name}: tracked source changes would invalidate the import")
        self.files: dict[str, bytes] = {}
        archive = git(root, "archive", "--format=tar", commit)
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            for entry in tar:
                if not entry.isfile():
                    continue
                path = PurePosixPath(entry.name)
                if path.is_absolute() or ".." in path.parts:
                    raise ValueError(f"Unsafe archive member: {entry.name}")
                stream = tar.extractfile(entry)
                if stream is None:
                    raise ValueError(f"Unreadable archive member: {entry.name}")
                self.files[path.as_posix()] = stream.read()

    def json(self, path: str) -> Any:
        return parse_json(self.files[path])

    def resources(self, folder: str) -> dict[str, bytes]:
        merged: dict[str, bytes] = {}
        # Generated assets are the same layer that the upstream Gradle source set adds last.
        for tree in ("main", "generated"):
            prefix = f"src/{tree}/resources/{folder}/"
            for path, content in self.files.items():
                if path.startswith(prefix):
                    merged[path[len(prefix):]] = content
        return merged


def item(id_: str, model: str, name: str, *, max_stack: int = 64) -> dict[str, Any]:
    if not ID.fullmatch(id_) or not ID.fullmatch(model):
        raise ValueError(f"Invalid item/model ID: {id_}, {model}")
    return {"material": "paper", "data": {"item_name": name, "max_stack_size": max_stack},
            "model": {"type": "minecraft:model", "path": model}}


def furniture(id_: str, behavior: str | None = None, height: float = 0.75,
              seated: bool = False) -> dict[str, Any]:
    hitbox: dict[str, Any] = {"type": "interaction", "width": 0.75, "height": height,
                              "position": "0,0,0"}
    if seated:
        hitbox["seats"] = ["0,0.4,0 0"]
    definition: dict[str, Any] = {
        "settings": {"item": id_, "hit_times": 3},
        "variants": {"ground": {
            "elements": [{"item": id_, "display_transform": "none", "position": "0,0.5,0"}],
            "hitboxes": [hitbox]}},
        "loot": {"pools": [{"rolls": 1, "entries": [{"type": "furniture_item"}]}]},
    }
    if behavior:
        definition["behaviors"] = [{"type": behavior}]
    return definition


def placeable(definition: dict[str, Any], id_: str) -> None:
    definition["behavior"] = {"type": "furniture_item", "furniture": id_,
                              "rules": {"ground": {"rotation": "sixteen", "alignment": "center"}}}


def crafting(tags: dict[str, bytes], items: dict[str, Any]) -> tuple[dict, list]:
    recipes, pending = {}, []

    def ingredient(value: Any) -> Any:
        if isinstance(value, list):
            return [ingredient(v) for v in value]
        if not isinstance(value, dict) or len(value) != 1:
            raise ValueError(f"Unsupported crafting ingredient: {value}")
        if "item" in value:
            id_ = value["item"]
            if not id_.startswith("minecraft:") and id_ not in items:
                raise ValueError(f"Missing crafting input: {id_}")
            return id_
        if "tag" in value and value["tag"].startswith("minecraft:"):
            return "#" + value["tag"]
        return resolve_ingredient(value, tags)

    for path, content in sorted(tags.items()):
        if "/recipes/" not in path or not path.endswith(".json"):
            continue
        recipe = parse_json(content)
        kind = recipe.get("type", "")
        if kind not in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
            continue
        id_ = path.split("/")[0] + ":" + path.split("/recipes/")[1][:-5]
        try:
            output = recipe["result"]
            if set(output) - {"item", "count"}:
                raise ValueError("Crafting output has additional components")
            if not output["item"].startswith("minecraft:") and output["item"] not in items:
                raise ValueError(f"Missing crafting output: {output['item']}")
            converted = {"type": kind.removeprefix("minecraft:crafting_"),
                         "result": {"id": output["item"], "count": output.get("count", 1)}}
            if kind.endswith("_shaped"):
                converted.update(pattern=recipe["pattern"], ingredients={k: ingredient(v) for k, v in recipe["key"].items()})
            else:
                converted["ingredients"] = [ingredient(v) for v in recipe["ingredients"]]
            recipes[id_] = converted
        except ValueError as error:
            pending.append(f"Crafting {id_}: {error}")
    return recipes, pending


def names(assets: dict[str, bytes], namespace: str) -> dict[str, str]:
    language = parse_json(assets[f"{namespace}/lang/zh_cn.json"])
    return {namespace + ":" + key.split(".", 2)[2]: value for key, value in language.items()
            if key.startswith((f"item.{namespace}.", f"block.{namespace}."))}


def doll(source: Source) -> tuple[dict, dict, dict, list]:
    assets = source.resources("assets")
    tags = source.resources("data")
    labels = names(assets, "kaleidoscope_doll")

    def tag(name: str) -> list[str]:
        values = parse_json(tags[f"kaleidoscope_doll/tags/items/{name}.json"])["values"]
        if any(not isinstance(v, str) or not ID.fullmatch(v) for v in values):
            raise ValueError(f"Doll tag contains a nonliteral item: {name}")
        return values

    ids = [v for v in tag("all_dolls") if v != "kaleidoscope_doll:custom_doll"]
    catalog = {"dolls": ids, "players": tag("player_dolls"),
               "tiers": [tag(f"tier_{tier}_dolls") for tier in range(3)],
               "tokens": [tag(f"tier_{tier}_machine_tokens")[0] for tier in range(3)], "names": labels}
    items, furnishings = {}, {}
    extra = ["doll_machine", "computer", "tweaks_tool", "green_doll_gift_box", "yellow_doll_gift_box", "purple_doll_gift_box"]
    for id_ in ids + ["kaleidoscope_doll:" + p for p in extra]:
        path = id_.split(":")[1]
        model_path = f"kaleidoscope_doll/models/item/{path}.json"
        if model_path not in assets:
            raise ValueError(f"Missing doll model: {model_path}")
        items[id_] = item(id_, f"kaleidoscope_doll:item/{path}", labels.get(id_, path))
        behavior = {"doll_machine": "lottery", "computer": "computer"}.get(path)
        if id_ in ids:
            behavior = "doll"
        if behavior:
            placeable(items[id_], id_)
            furnishings[id_] = furniture(id_, "kaleidoscope_doll:" + behavior,
                                          1.5 if path == "doll_machine" else 0.75)
            if behavior == "doll":
                furnishings[id_]["variants"]["ground"]["elements"] = []
    recipes, pending = crafting(tags, items)
    return catalog, {"items": items, "furniture": furnishings, "recipes": recipes}, assets, pending + [
        "custom_doll and doll_entity_item use client entity renderers; not exported as invisible items",
        "doll_machine currently uses the item model; exact double-block animation is pending",
        "tweaks tool adjusts display pose only; moving dolls use native armor stand physics/head rendering; Bedrock custom dolls and held-item transforms pending",
    ]


def resolve_ingredient(value: Any, tags: dict[str, bytes], trail: tuple[str, ...] = ()) -> list[str]:
    if isinstance(value, list):
        return sorted({id_ for child in value for id_ in resolve_ingredient(child, tags, trail)})
    if not isinstance(value, dict) or len(value) != 1:
        raise ValueError(f"Unsupported ingredient: {value}")
    if "item" in value:
        id_ = value["item"]
        if not isinstance(id_, str) or not ID.fullmatch(id_):
            raise ValueError(f"Invalid ingredient item: {id_}")
        return [id_]
    tag = value.get("tag")
    if not isinstance(tag, str) or tag in trail or not ID.fullmatch(tag):
        raise ValueError(f"Cyclic or invalid ingredient tag: {tag}")
    namespace, name = tag.split(":", 1)
    key = f"{namespace}/tags/items/{name}.json"
    if key not in tags:
        if tag in VANILLA_TAG_MEMBERS:
            return list(VANILLA_TAG_MEMBERS[tag])
        raise ValueError(f"Missing ingredient tag: {tag}")
    members = parse_json(tags[key])["values"]
    return sorted({id_ for member in members for id_ in resolve_ingredient(
        {"tag": member[1:]} if isinstance(member, str) and member.startswith("#") else {"item": member},
        tags, (*trail, tag))})


def tavern(source: Source) -> tuple[dict, dict, dict, list]:
    assets, tags = source.resources("assets"), source.resources("data")
    labels = names(assets, "kaleidoscope_tavern")
    java = source.files["src/main/java/com/github/ysbbbbbb/kaleidoscopetavern/init/ModItems.java"].decode()
    registrations = re.findall(r'ITEMS\.register\("([a-z0-9_]+)"', java)
    if len(registrations) != java.count("ITEMS.register(") or len(set(registrations)) != len(registrations):
        raise ValueError("Unrecognized Tavern item registration")
    catalog: dict[str, Any] = {"barrel": [], "pressing": [], "shaker": [], "effects": {}, "names": labels,
        "effectTags": {name: parse_json(tags[f"kaleidoscope_tavern/tags/{kind}/{name}.json"])["values"]
                       for kind, name in [("blocks", "ardent_heat_breakable"), ("blocks", "grass_stealth_plants"),
                                          ("entity_types", "tomb_raider_disarmable")]}}
    for path, content in sorted(tags.items()):
        if "/datamap/drink_effect/" in path and path.endswith(".json"):
            effect = parse_json(content)
            catalog["effects"][effect["item"]] = effect["effects"]
        if "/recipes/" not in path or not path.endswith(".json"):
            continue
        recipe = parse_json(content)
        recipe_id = "kaleidoscope_tavern:" + path.split("/recipes/")[1][:-5]
        if recipe.get("type") == "kaleidoscope_tavern:barrel":
            catalog["barrel"].append({"id": recipe_id, "fluid": recipe.get("fluid", "minecraft:water"),
                "ingredients": [resolve_ingredient(v, tags) for v in recipe.get("ingredients", [])],
                "carriers": resolve_ingredient(recipe["carrier"], tags), "output": recipe["result"]["item"],
                "unitTime": recipe.get("unit_time", 2400)})
        elif recipe.get("type") == "kaleidoscope_tavern:pressing_tub":
            fluid = recipe["fluid"]
            catalog["pressing"].append({"id": recipe_id, "ingredients": resolve_ingredient(recipe["ingredient"], tags),
                "fluid": fluid, "amount": recipe.get("fluid_amount", 125),
                "bucket": fluid.replace("_juice", "_bucket")})
        elif recipe.get("type") == "kaleidoscope_tavern:shaker":
            catalog["shaker"].append({"id": recipe_id,
                "ingredients": [resolve_ingredient(v, tags) for v in recipe["ingredients"]],
                "output": recipe["result"]["item"]})
    buckets = {r["fluid"]: r["bucket"] for r in catalog["pressing"]}
    buckets.update({"minecraft:water": "minecraft:water_bucket", "minecraft:lava": "minecraft:lava_bucket"})
    catalog["buckets"] = buckets
    items, furnishings, pending = {}, {}, []
    for path in registrations:
        id_ = "kaleidoscope_tavern:" + path
        key = f"kaleidoscope_tavern/models/item/{path}.json"
        if key not in assets:
            pending.append(f"No item model: {id_}")
            continue
        model = parse_json(assets[key])
        if "loader" in model:
            if model["loader"] != "forge:separate_transforms" or "base" not in model:
                raise ValueError(f"Unsupported model loader: {key}")
            assets[key] = json_bytes(model["base"])
            pending.append(f"{id_}: uses base model in all views; Forge perspective renderer not ported")
        items[id_] = item(id_, "kaleidoscope_tavern:item/" + path, labels.get(id_, path),
                          max_stack=1 if path.endswith("_bucket") or path == "shaker" else 16
                          if id_ in catalog['effects'] or id_ in {r['output'] for r in catalog['barrel']} else 64)
        if id_ in catalog["effects"] or path == "signature_cocktail":
            items[id_]["data"]["components"] = {"minecraft:consumable": {
                "consume_seconds": 1.6, "animation": "drink", "sound": "minecraft:entity.generic.drink"}}
            items[id_]["settings"] = {"consume_replacement": "kaleidoscope_tavern:empty_glassware"
                                     if len(catalog["effects"].get(id_, [])) <= 1 else "kaleidoscope_tavern:empty_bottle"}
        behavior = {"barrel": "barrel", "pressing_tub": "pressing_tub", "shaker": "shaker",
                    "trellis": "grape", "tap": "tap", "empty_bottle": "bottle", "empty_glassware": "bottle"}.get(path)
        seated = path.endswith(("_sofa", "_bar_stool"))
        if behavior or seated:
            placeable(items[id_], id_)
            furnishings[id_] = furniture(id_, "kaleidoscope_tavern:" + behavior if behavior else None,
                                          2 if path == "barrel" else 0.5, seated)
    from tavern_furniture import add_variants
    add_variants(items, furnishings, catalog, assets)
    for recipe in catalog["barrel"]:
        if recipe["output"] not in items:
            raise ValueError(f"Missing brewing output: {recipe['output']}")
    pending += ["handheld shaker uses vanilla use animation; native mod hand animation is unavailable",
                "tap sources support barrels, melon, dragon heads, hives, waterlogged blocks and cauldrons; wild grape worldgen, thrown molotovs and remaining furniture behavior pending",
                "grape growth uses staggered loaded-furniture pulses; spread is restricted to same-owner trellises",
                "barrel uses its inventory item model; full multi-block appearance pending"]
    recipes, skipped = crafting(tags, items)
    return catalog, {"items": items, "furniture": furnishings, "recipes": recipes}, assets, pending + skipped


def remap_deco(value: Any, namespaces: set[str]) -> Any:
    if isinstance(value, str):
        for namespace in namespaces:
            if value.startswith(namespace + ":"):
                return "kdeco_" + value
        return value
    if isinstance(value, list):
        return [remap_deco(v, namespaces) for v in value]
    if isinstance(value, dict):
        return {remap_deco(k, namespaces): remap_deco(v, namespaces) for k, v in value.items()}
    return value


def strip_trailing_commas(text: str) -> str:
    """nbtlib rejects commas accepted by Minecraft; preserve quoted SNBT byte-for-byte."""
    output = []
    quote = None
    escaped = False
    for index, char in enumerate(text):
        if quote is not None:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ("'", '"'):
            quote = char
        if char == "," and text[index + 1:].lstrip().startswith(("}", "]")):
            continue
        output.append(char)
    return "".join(output)


def model_tree(model: dict[str, Any], namespaces: set[str]) -> dict[str, Any]:
    kind = model["type"].removeprefix("minecraft:")
    converted = remap_deco(model, namespaces)
    converted["type"] = "minecraft:" + kind
    if kind == "model":
        converted["path"] = converted.pop("model")
    elif kind == "composite":
        converted["models"] = [model_tree(child, namespaces) for child in model["models"]]
    elif kind in ("select", "range_dispatch"):
        field = "cases" if kind == "select" else "entries"
        converted[field] = [{**row, "model": model_tree(row["model"], namespaces)} for row in model[field]]
        if "fallback" in model:
            converted["fallback"] = model_tree(model["fallback"], namespaces)
    elif kind == "condition":
        for field in ("on_true", "on_false"):
            converted[field] = model_tree(model[field], namespaces)
    elif kind != "empty":
        raise ValueError(f"Unsupported item model type: {kind}")
    if "property" in converted and ":" not in converted["property"]:
        converted["property"] = "minecraft:" + converted["property"]
    return converted


def deco_indexes(source: Source) -> tuple[dict, dict, dict, list]:
    import nbtlib
    indexes, templates, tables, pending = {}, {}, {}, []
    for path, content in sorted(source.files.items()):
        if not re.match(r"src/[^/]+/data/", path):
            continue
        if "/loot_table/" in path and path.endswith(".json"):
            namespace = path.split("/data/")[1].split("/")[0]
            tables[namespace + ":" + path.split("/loot_table/")[1][:-5]] = parse_json(content)
        if not path.endswith(".mcfunction"):
            continue
        text = re.sub(r"\\\r?\n[ \t]*", "", content.decode("utf-8"))
        for line in text.splitlines():
            match = re.fullmatch(r"data modify storage dc:(index input\.|template )([^\s]+) set value (.*)", line.strip())
            if not match:
                continue
            name = match[2]
            if name.startswith(('"', "'")):
                name = str(nbtlib.parse_nbt(name))
            try:
                definition = nbtlib.parse_nbt(strip_trailing_commas(match[3])).unpack(json=True)
            except ValueError as error:
                raise ValueError(f"Invalid Deco SNBT in {path}, index {name}: {error}") from error
            target = indexes if match[1].startswith("index") else templates
            if name in target and target[name][0] != definition:
                pending.append(f"Overlay behavior requires porting: {path} -> {name}")
                continue
            target[name] = (definition, path)
    return indexes, templates, tables, pending


def inherit_template(definition: dict, templates: dict, trail: tuple[str, ...] = ()) -> dict:
    name = definition.get("template")
    if not name:
        return definition.copy()
    if name in trail or name not in templates:
        raise ValueError(f"Missing or cyclic Deco template: {name}")
    base = inherit_template(templates[name][0], templates, (*trail, name))

    def merge(parent: dict, child: dict) -> dict:
        merged = parent.copy()
        for key, value in child.items():
            merged[key] = merge(merged[key], value) if isinstance(value, dict) and isinstance(merged.get(key), dict) else value
        return merged

    return merge(base, definition)


def loot_components(table: dict) -> dict:
    components = {}
    for pool in table.get("pools", []):
        for entry in pool.get("entries", []):
            for function in entry.get("functions", []):
                if function.get("function") in ("set_components", "minecraft:set_components"):
                    for key, value in function["components"].items():
                        if key in components and components[key] != value:
                            raise ValueError(f"Ambiguous Deco loot component: {key}")
                        components[key] = value
    return components


def deco(source: Source) -> tuple[dict, dict, dict, list]:
    original: dict[str, bytes] = {}
    for path, content in source.files.items():
        if re.match(r"src/[^/]+/assets/", path):
            key = path.split("/assets/", 1)[1]
            if key in original and content != original[key]:
                if "/font/" in key and key.endswith(".json"):
                    providers = parse_json(original[key])["providers"]
                    providers.extend(p for p in parse_json(content)["providers"] if p not in providers)
                    original[key] = json_bytes({"providers": providers})
                    continue
                raise ValueError(f"Conflicting base Deco assets: {key}")
            original[key] = content
    namespaces = {key.split("/")[0] for key in original}
    # These two upstream models still use the pre-move texture paths.
    for plate in ("plate", "porcelain_plate"):
        old = f"kaleidoscope_cookery/textures/block/{plate}.png"
        new = f"kaleidoscope_cookery/textures/block/meals/{plate}.png"
        if old not in original and new in original:
            original[old] = original[new]
    assets = {"kdeco_" + key: json_bytes(remap_deco(parse_json(content), namespaces))
              if key.endswith(".json") else content for key, content in original.items()}
    indexes, templates, tables, pending = deco_indexes(source)
    items, furnishings, entries, styles = {}, {}, [], []
    for index, (raw, path) in sorted(indexes.items()):
        id_ = "kaleidoscope_deco:" + index.replace(":", "/")
        if not ID.fullmatch(id_):
            raise ValueError(f"Invalid Deco index: {index}")
        try:
            definition = inherit_template(raw, templates)
            components = loot_components(tables.get(definition.get("loot_table"), {}))
            components.update(definition.get("item", {}).get("components", {}))
            model_id = components.get("minecraft:item_model")
            if model_id is None:
                raise ValueError("No literal item model after resolving template and loot")
            namespace, model_path = model_id.split(":", 1)
            key = f"{namespace}/items/{model_path}.json"
            if key not in original:
                raise ValueError(f"Referenced item model is absent: {key}")
            selector = parse_json(original[key])["model"]
            cmd = components.get("minecraft:custom_model_data", {})
            if not isinstance(cmd, dict):
                cmd = {}
            flags = cmd.get("flags", [])
            if index in ("kaleidoscope_bar:counter_stool_short", "kaleidoscope_bar:counter_stool_tall"):
                flags = [index.endswith("short")]
            model = model_tree(selected_model(selector, cmd.get("strings", []), flags), namespaces)
            item_definition = {"material": "paper", "data": {"item_name": index}, "model": model}
            placeable(item_definition, id_)
            furnishing = furniture(id_, "kaleidoscope_deco:interaction", seated=definition.get("events", {}).get("right_click", {}).get("fallback", {}).get("event") == "sit")
            variant = furnishing["variants"]["ground"]
            dimensions = definition.get("interactsize", {})
            if not isinstance(dimensions, dict):
                raise ValueError("Multiple interaction boxes require adaptation")
            variant["hitboxes"][0].update(height=float(dimensions.get("height", 0.75)), width=float(dimensions.get("width", 0.75)))
            if "modsize" in definition:
                variant["elements"][0]["scale"] = ",".join(str(float(v)) for v in definition["modsize"])
            items[id_] = item_definition
            furnishings[id_] = furnishing
            entries.append({"id": id_, "category": path.split("/")[1], "source": path,
                            "rules": deco_rules(definition, source.files, pending, index)})
            if index == "kaleidoscope_bar:sofa":
                for color in ("none", *COLORS):
                    for mode in ("single", "left", "right", "middle"):
                        style = id_.replace(":", "/") + "/style/" + color + "/" + mode
                        visual_id = id_ + "/style/" + color + "/" + mode
                        items[visual_id] = {"material": "paper", "data": {"item_name": index},
                                            "model": model_tree(selected_model(selector, [color, mode], []), namespaces)}
                        styled = copy.deepcopy(variant)
                        styled["elements"][0]["item"] = visual_id
                        furnishing["variants"][style] = styled
                        styles.append(style)
            if any(k.removeprefix("minecraft:") == "custom_model_data" for k in components):
                pending.append(f"Component-selected appearance requires porting: {index}")
        except ValueError as error:
            pending.append(f"Furniture {index}: {error}")
    link_variants(entries, furnishings, pending)
    pending = [f"{message} ({count} uses)" if count > 1 else message for message, count in Counter(pending).items()]
    items["kaleidoscope_deco:state_tool"] = item("kaleidoscope_deco:state_tool", "minecraft:item/stick", "家具状态工具", max_stack=1)
    return {"entries": entries, "styles": styles}, {"items": items, "furniture": furnishings}, assets, pending + [
        "only literal registered furniture exported; GUI-only models are not items",
        "source-driven simple transitions and sofa dye/modes supported; additional scripted events are individually reported",
        "Deco namespaces are prefixed with kdeco_ to prevent collisions with the full gameplay mods",
    ]


def music(source: Source) -> tuple[dict, dict, dict, list]:
    # Original instrument uses builtin/entity; an explicit vanilla note-block substitute is visible without a client mod.
    id_ = "musicplus:handheld_instrument"
    return {}, {"items": {id_: item(id_, "minecraft:block/note_block", "森罗乐器", max_stack=1)}}, {}, [
        "handheld instrument has an explicit note-block placeholder model",
        "MIDI is played as scheduled vanilla note sounds, without the mod's client synthesizer or sustained voice rendering",
    ]


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def write(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(content)
    temporary.replace(path)


def pack_groups(bundle: dict[str, bytes], modules: tuple[str, ...] = PACK_MODULES) -> dict[str, dict[str, bytes]]:
    """Split the aggregate bundle into independent CE pack file trees."""
    groups = {module: {} for module in modules}
    for path, content in bundle.items():
        module, separator, relative = path.partition("/")
        if not separator or module not in groups or not relative:
            raise ValueError(f"Unscoped CE bundle entry: {path}")
        groups[module][relative] = content
    for module in modules[:-1]:
        if "pack.yml" not in groups[module] or "configuration/content.yml" not in groups[module]:
            raise ValueError(f"Incomplete CE pack: {module}")
    shared = modules[-1]
    if groups[shared] and "pack.yml" not in groups[shared]:
        raise ValueError("Incomplete CE pack: shared")
    return groups


def write_zip(path: Path, files: dict[str, bytes]) -> None:
    if path.exists():
        raise ValueError(f"Archive output already exists: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(path, "x", compression=ZIP_DEFLATED) as archive:
        for name, content in sorted(files.items()):
            archive.writestr(name, content)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, help="New directory for local CE packs (will not overwrite)")
    parser.add_argument("--archive", type=Path, help="New ZIP of the CE packs (will not overwrite)")
    parser.add_argument("--module-archives-dir", type=Path,
                        help="New directory containing one CE ZIP per module plus optional shared CE ZIP")
    parser.add_argument("--check", action="store_true", help="Verify generated catalogs match the pinned sources")
    args = parser.parse_args()
    if args.assets and args.assets.exists():
        raise ValueError(f"Asset output already exists: {args.assets}; use a new output directory")
    if args.archive and args.archive.exists():
        raise ValueError(f"Archive output already exists: {args.archive}")
    if args.module_archives_dir and args.module_archives_dir.exists():
        raise ValueError(f"Module archive output already exists: {args.module_archives_dir}")
    pack_version, specs, shared_pack = load_module_specs()
    modules = tuple(spec["module"] for spec in specs) + (shared_pack,)
    records = parse_json(LOCK.read_bytes())["repositories"]
    pinned = {r["name"]: r for r in records}
    converter_functions = {"doll": doll, "tavern": tavern, "deco": deco, "music": music}
    converters = []
    for spec in specs:
        converter = converter_functions.get(spec["converter"])
        if converter is None:
            raise ValueError(f"Unknown module converter: {spec['converter']}")
        converters.append((spec, converter))
    prepared = []
    for spec, converter in converters:
        module = spec["module"]
        repository = spec["sourceRepository"]
        source = Source(repository, pinned[repository]["commit"])
        catalog, definitions, assets, pending = converter(source)
        report = {"module": module, "source": repository, "commit": source.commit,
                  "namespace": spec["namespace"], "pack_namespace": spec["packNamespace"],
                  "converter": spec["converter"], "item_definitions": len(definitions['items']),
                  "furniture_definitions": len(definitions.get('furniture', {})), "crafting_recipes": len(definitions.get('recipes', {})),
                  "assets": len(assets), "pending": pending}
        prepared.append((spec, source, catalog, definitions, assets, report))
    atlases: dict[str, list[dict]] = {}
    for _, _, _, _, assets, report in prepared:
        for path in list(assets):
            if path.startswith("minecraft/atlases/") and path.endswith(".json"):
                atlas = parse_json(assets.pop(path))
                if set(atlas) != {"sources"}:
                    raise ValueError(f"Unsupported atlas fields: {path}")
                merged = atlases.setdefault(path, [])
                merged.extend(entry for entry in atlas["sources"] if entry not in merged)
        report["assets"] = len(assets)
    bundle: dict[str, bytes] = {}
    for spec, source, catalog, definitions, assets, report in prepared:
        module = spec["module"]
        catalog_path = SUITE / module / "src/main/resources/catalog.json"
        if args.check:
            if catalog_path.read_bytes() != json_bytes(catalog):
                raise ValueError(f"Stale generated catalog: {catalog_path}")
        else:
            write(catalog_path, json_bytes(catalog))
        bundle[f"{module}/pack.yml"] = json_bytes({"namespace": spec["packNamespace"], "name": "Kaleidoscope " + module, "version": pack_version})
        bundle[f"{module}/configuration/content.yml"] = json_bytes(definitions)
        for path, content in assets.items():
            bundle[f"{module}/resourcepack/assets/{path}"] = content
        for path, content in source.files.items():
            if "/" not in path and ("LICENSE" in path.upper() or path.lower().startswith("readme")):
                bundle[f"{module}/upstream-notices/{path}"] = content
        bundle[f"{module}/IMPORT-REPORT.json"] = json_bytes(report)
        if not args.check:
            write(SUITE / "docs" / f"{module}-import.json", json_bytes(report))
        print(f"{module}: {report['item_definitions']} items, {report['furniture_definitions']} furniture; {len(report['pending'])} explicit limitations")
    if atlases:
        bundle[f"{shared_pack}/pack.yml"] = json_bytes({"namespace": "kseries_shared", "name": "Kaleidoscope shared atlases", "version": pack_version})
        for path, sources in atlases.items():
            bundle[f"{shared_pack}/resourcepack/assets/{path}"] = json_bytes({"sources": sources})
    from validate_packs import validate
    validation = validate(bundle)
    if validation["errors"]:
        raise ValueError("Pack validation failed: " + "\n".join(validation["errors"]))
    if not args.check:
        write(SUITE / "docs/pack-validation.json", json_bytes(validation))
    if args.assets:
        for path, content in bundle.items():
            write(args.assets / path, content)
    if args.archive:
        write_zip(args.archive, bundle)
    if args.module_archives_dir:
        groups = pack_groups(bundle, modules)
        args.module_archives_dir.mkdir(parents=True, exist_ok=False)
        for module in modules:
            if groups[module]:
                write_zip(args.module_archives_dir / f"{module}-ce-pack-{pack_version}.zip", groups[module])


if __name__ == "__main__":
    main()
