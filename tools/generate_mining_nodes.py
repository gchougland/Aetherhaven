"""Generate only the entity overlay used to mark mining locations in the editor."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT.parent / "HytaleSourceCode/hytale-shared-source/HytaleAssets/Server/Item/Items"
def main():
    # An entity-only authoring overlay. A slightly larger cube prevents coplanar faces
    # against the original rock; nothing is placed into the author's prefab.
    preview_dir = ROOT / "src/main/resources/Common/Items/Aetherhaven/Mining_Node_Preview"
    preview_dir.mkdir(parents=True, exist_ok=True)
    # ModelAsset restricts entity textures to Items/Characters/NPC/VFX roots.
    native_common = NATIVE.parents[2] / "Common"
    for source, name in (("BlockTextures/Rock_Stone.png", "Stone.png"),
                         ("Resources/Ores/Ore_Textures/Copper.png", "Copper.png")):
        shutil.copyfile(native_common / source, preview_dir / name)
    ore = json.loads((native_common / "Resources/Ores/Ore_Large.blockymodel").read_text())
    # Native block models are centered on the cell; this entity uses a feet origin.
    # Shift the attachment meshes, not the shared Root socket which is merged at load.
    for root in ore["nodes"]:
        for mesh in root.get("children", []):
            mesh.setdefault("position", {}).setdefault("y", 0)
            mesh["position"]["y"] += 16
    (preview_dir / "Ore.blockymodel").write_text(json.dumps(ore, indent=2) + "\n", encoding="utf-8")
    face = {"offset": {"x": 0, "y": 0}, "mirror": {"x": False, "y": False}, "angle": 0}
    cube = {"id": "100", "name": "MiningPreviewCube", "position": {"x": 0, "y": 16, "z": 0},
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
            "shape": {"type": "box", "offset": {"x": 0, "y": 0, "z": 0},
                      "stretch": {"x": 1.015, "y": 1.015, "z": 1.015},
                      "settings": {"isPiece": False, "size": {"x": 32, "y": 32, "z": 32}, "isStaticBox": True},
                      "textureLayout": {side: face for side in ("front", "back", "left", "right", "top", "bottom")},
                      "unwrapMode": "custom", "visible": True, "doubleSided": False, "shadingMode": "flat"}}
    model = {"format": "prop", "lod": "auto", "nodes": [{"id": "101", "name": "Root", "children": [cube]}]}
    (preview_dir / "Preview.blockymodel").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    asset = {"Model": "Items/Aetherhaven/Mining_Node_Preview/Preview.blockymodel", "Texture": "Items/Aetherhaven/Mining_Node_Preview/Stone.png",
             "DefaultAttachments": [{"Model": "Items/Aetherhaven/Mining_Node_Preview/Ore.blockymodel", "Texture": "Items/Aetherhaven/Mining_Node_Preview/Copper.png"}],
             "HitBox": {"Min": {"X": -.25, "Y": 0, "Z": -.25}, "Max": {"X": .25, "Y": .75, "Z": .25}},
             "MinScale": 2, "MaxScale": 2}
    (ROOT / "src/main/resources/Server/Models/Aetherhaven/Aetherhaven_Mining_Node_Preview.json").write_text(json.dumps(asset, indent=2) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
