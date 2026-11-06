"""CE variants for the tavern controllers, derived from the upstream block models."""
from __future__ import annotations

import copy

VANILLA_BOTTLES = {
    "minecraft:potion": "water_bottle",
    "minecraft:honey_bottle": "honey_bottle",
    "minecraft:dragon_breath": "dragon_breath_bottle",
}


def add_variants(items: dict, furnishings: dict, catalog: dict, assets: dict) -> None:
    trellis = furnishings["kaleidoscope_tavern:trellis"]
    for fruit in ("grape", "ice_grape", "gold_grape"):
        plant = fruit.replace("grape", "grapevine_trellis")
        for vine_age in range(4):
            visual_id = f"kaleidoscope_tavern:visual/{fruit}/vine_{vine_age}"
            model = f"kaleidoscope_tavern:block/plant/{plant}/single_stage{vine_age}"
            require_model(model, assets)
            items[visual_id] = visual(visual_id, model)
            variant = copy.deepcopy(trellis["variants"]["ground"])
            variant["elements"][0]["item"] = visual_id
            trellis["variants"][f"{fruit}_{vine_age}_0"] = variant
        for fruit_age in range(6):
            visual_id = f"kaleidoscope_tavern:visual/{fruit}/fruit_{fruit_age}"
            model = f"kaleidoscope_tavern:block/plant/{fruit}_crop/stage{fruit_age}"
            require_model(model, assets)
            items[visual_id] = visual(visual_id, model)
            variant = copy.deepcopy(trellis["variants"][f"{fruit}_3_0"])
            variant["elements"].append({"item": visual_id, "display_transform": "none", "position": "0,-0.5,0"})
            variant["hitboxes"].append({"type": "interaction", "position": "0,-1,0", "height": 1, "width": 0.75})
            trellis["variants"][f"{fruit}_3_{fruit_age + 1}"] = variant
    for carrier in ("empty_bottle", "empty_glassware"):
        bottle = furnishings["kaleidoscope_tavern:" + carrier]
        for output in sorted({*catalog["effects"], *(recipe["output"] for recipe in catalog["barrel"]),
                              "kaleidoscope_tavern:signature_cocktail", "kaleidoscope_tavern:watermelon_juice", *VANILLA_BOTTLES}):
            variant = copy.deepcopy(bottle["variants"]["ground"])
            display = output
            if output in VANILLA_BOTTLES:
                name = VANILLA_BOTTLES[output]
                display = f"kaleidoscope_tavern:visual/{name}"
                model = f"kaleidoscope_tavern:block/brew/{name}"
                require_model(model, assets)
                items[display] = visual(display, model)
            variant["elements"][0]["item"] = display
            bottle["variants"][output.replace(":", "/")] = variant


def visual(id_: str, model: str) -> dict:
    return {"material": "paper", "data": {"item_name": id_}, "model": {"type": "minecraft:model", "path": model}}


def require_model(model: str, assets: dict) -> None:
    namespace, path = model.split(":", 1)
    if f"{namespace}/models/{path}.json" not in assets:
        raise ValueError(f"Missing tavern state model: {model}")
