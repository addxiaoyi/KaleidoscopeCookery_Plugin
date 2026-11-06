"""Translate the pinned datapack's declarative interactions, without executing commands."""
from __future__ import annotations

import copy
import json
import re
from typing import Any


def deco_id(index: str) -> str:
    return "kaleidoscope_deco:" + index.replace(":", "/")


def flatten(event: dict) -> list[dict]:
    if event.get("event") == "group":
        return [leaf for child in event.get("args", {}).get("events", []) for leaf in flatten(child)]
    return [event]


def function_sounds(files: dict[str, bytes], name: str) -> list[str]:
    if not name:
        return []
    namespace, path = name.split(":", 1)
    suffix = f"/data/{namespace}/function/{path}.mcfunction"
    matches = [content for file, content in files.items() if re.match(r"src/[^/]+/data/", file) and file.endswith(suffix)]
    if len(matches) != 1:
        raise ValueError(f"Unresolved function: {name}")
    sounds = []
    for line in matches[0].decode("utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("playsound "):
            sound = line.split()[1]
            sounds.append(sound if ":" in sound else "minecraft:" + sound)
        elif not line.startswith("particle "):
            raise ValueError(f"Function has additional behavior: {name}")
    return sounds


def rules(definition: dict, files: dict[str, bytes], pending: list[str], index: str) -> list[dict]:
    click = definition.get("events", {}).get("right_click", {})
    converted = []
    for event in [*click.get("criteria", []), click.get("fallback", {})]:
        if not event or event.get("event") in ("sit", "__nothing__"):
            continue
        rule: dict[str, Any] = {"inputs": [], "wand": False, "sneak": -1, "target": "", "sounds": [], "pending": False}
        predicate = event.get("predicate", "")
        if predicate:
            if predicate not in ("kaleidoscope:shift", "!kaleidoscope:shift"):
                pending.append(f"Interaction predicate pending: {predicate}")
                continue
            rule["sneak"] = 0 if predicate.startswith("!") else 1
        condition = event.get("item", {})
        components = condition.get("components", {})
        if components:
            if components == {"minecraft:custom_data": {"kaleidoscope": "trans"}}:
                rule["wand"] = True
            else:
                pending.append(f"Interaction item components pending: {components}")
                continue
        elif "id" in condition:
            item = condition["id"]
            if item == "*":
                rule["inputs"] = ["*"]
            elif item.startswith("#"):
                # Vanilla registry tags are checked by Bukkit; custom datapack tags are explicit pending rules.
                if item.startswith("#minecraft:") or ":" not in item:
                    rule["inputs"] = [item if ":" in item else "#minecraft:" + item[1:]]
                else:
                    pending.append(f"Interaction item tag pending: {item}")
                    continue
            else:
                rule["inputs"] = [item if ":" in item else "minecraft:" + item]
        try:
            for action in flatten(event):
                kind, args = action.get("event"), action.get("args", {})
                if kind == "trans":
                    if rule["target"]:
                        raise ValueError("Multiple transitions in one interaction")
                    rule["target"] = deco_id(args["index"])
                    function = args.get("func", "")
                    if function in ("kaleidoscope:bar/events/counter_stool/switch_to_short/trans",
                                    "kaleidoscope:bar/events/counter_stool/switch_to_tall/trans"):
                        rule["sounds"].append("minecraft:entity.copper_golem.step")
                    else:
                        rule["sounds"].extend(function_sounds(files, function))
                elif kind == "sound":
                    sound = args["sound"]
                    rule["sounds"].append(sound if ":" in sound else "minecraft:" + sound)
                elif kind == "custom" and args.get("func") in ("kaleidoscope:events/wand", "kaleidoscope:doll/events/note"):
                    continue
                else:
                    raise ValueError(f"Event pending: {kind}: {args.get('func', '')}")
            if rule["target"] or rule["sounds"]:
                converted.append(rule)
        except ValueError as error:
            rule["target"] = ""
            rule["sounds"] = []
            rule["pending"] = True
            converted.append(rule)
            pending.append(f"Interaction action pending: {error}")
    return converted


def selected_model(model: dict, strings: list[str], flags: list[bool]) -> dict:
    """Resolve known CMD state to a static model, so CE cannot overwrite selector components."""
    kind = model["type"].removeprefix("minecraft:")
    if kind == "select" and model["property"].removeprefix("minecraft:") == "custom_model_data":
        slot = model.get("index", 0)
        value = strings[slot] if slot < len(strings) else ""
        child = model.get("fallback", {"type": "minecraft:empty"})
        for case in model["cases"]:
            if value in (case["when"] if isinstance(case["when"], list) else [case["when"]]):
                child = case["model"]
                break
        return selected_model(child, strings, flags)
    if kind == "condition" and model["property"].removeprefix("minecraft:") == "custom_model_data":
        slot = model.get("index", 0)
        return selected_model(model["on_true"] if slot < len(flags) and flags[slot] else model["on_false"], strings, flags)
    return copy.deepcopy(model)


def link_variants(entries: list[dict], furnishings: dict, pending: list[str]) -> None:
    by_id = {entry["id"]: entry for entry in entries}
    originals = {id_: copy.deepcopy(furniture["variants"]["ground"]) for id_, furniture in furnishings.items()}
    for entry in entries:
        for rule in entry["rules"]:
            if rule["target"] and rule["target"] not in by_id:
                pending.append(f"Transition target not exported: {entry['id']} -> {rule['target']}")
                rule.update(target="", pending=True)
    for origin in entries:
        seen, queue = set(), [origin["id"]]
        while queue:
            id_ = queue.pop()
            if id_ in seen:
                continue
            seen.add(id_)
            furnishings[origin["id"]]["variants"][id_.replace(":", "/")] = copy.deepcopy(originals[id_])
            queue.extend(rule["target"] for rule in by_id[id_]["rules"] if rule["target"])
