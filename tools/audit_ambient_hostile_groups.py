"""Find NPC group names hostile to players but outside LivingWorld Aggressive."""
import json
import fnmatch
from pathlib import Path

ROOT = Path(
    r"C:\Users\gchou\OneDrive\Documents\Hytale-Modding\HytaleSourceCode\hytale-shared-source\HytaleAssets\Server\NPC"
)
AETHER_ROOT = Path(
    r"c:\Users\gchou\OneDrive\Documents\Hytale-Modding\Aetherhaven\src\main\resources\Server\NPC"
)
GROUPS_DIR = ROOT / "Groups"
ROLES_DIR = ROOT / "Roles"

NEUTRAL_STEMS = {
    "Aggressive",
    "Passive",
    "Neutral",
    "Tests",
    "Test_Dummy",
    "Flock",
    "FlockPrey",
    "Player",
    "Self",
    "Critters",
    "Prey",
    "PreyBig",
    "Undead",
    "Kweebec",
    "Kweebec_Civilian",
    "Kweebec_Pets",
    "Kweebec_Prisoner",
    "Feran",
    "Feran_Civilian",
    "Eats_Cactees",
    "Empty",
    "Friendly",
}


def load_group_defs(groups_dir: Path) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for p in groups_dir.rglob("*.json"):
        data = json.loads(p.read_text(encoding="utf-8"))
        out[p.stem] = {
            "includes": data.get("IncludeGroups", []),
            "roles": data.get("IncludeRoles", []),
        }
    return out


def expand_seeds(seeds: set[str], includes: dict[str, dict]) -> set[str]:
    seen = set(seeds)
    stack = list(seeds)
    while stack:
        g = stack.pop()
        for inc in includes.get(g, {}).get("includes", []):
            if inc not in seen:
                seen.add(inc)
                stack.append(inc)
    return seen


def role_matches_pattern(role_name: str, pattern: str) -> bool:
    if pattern.startswith("*") and pattern.endswith("*"):
        return pattern[1:-1] in role_name
    return fnmatch.fnmatch(role_name, pattern)


def groups_for_role(role_name: str, group_defs: dict[str, dict]) -> set[str]:
    tags: set[str] = set()
    for group_name, spec in group_defs.items():
        for pat in spec.get("roles", []):
            if isinstance(pat, str) and role_matches_pattern(role_name, pat):
                tags.add(group_name)
                break
    return tags


def default_player_attitude(data: object) -> str | None:
    if isinstance(data, dict):
        dpa = data.get("DefaultPlayerAttitude")
        if isinstance(dpa, str):
            return dpa
        mod = data.get("Modify")
        if isinstance(mod, dict):
            mdpa = mod.get("DefaultPlayerAttitude")
            if isinstance(mdpa, str):
                return mdpa
        for v in data.values():
            found = default_player_attitude(v)
            if found:
                return found
    return None


def is_player_hostile_role(data: dict) -> bool:
    ref = data.get("Reference", "")
    if isinstance(ref, str) and (
        ref == "Template_Predator"
        or ref == "Template_Spirit"
        or ref.startswith("Template_Trork")
        or ref.startswith("Template_Goblin")
        or ref == "Template_Eye"
    ):
        return True
    if isinstance(ref, str) and ref == "Template_Intelligent":
        mod = data.get("Modify")
        if isinstance(mod, dict) and mod.get("DefaultPlayerAttitude") == "Neutral":
            return False
        return mod is not None and "Attack" in json.dumps(mod)
    dpa = default_player_attitude(data)
    return dpa == "Hostile"


def suggest_role_patterns(role_names: list[str]) -> list[str]:
    exact: list[str] = []
    prefixes: set[str] = set()
    for name in role_names:
        if "_" in name:
            prefixes.add(name.split("_", 1)[0] + "_*")
        else:
            exact.append(name)
    lines = sorted(set(exact))
    lines.extend(sorted(prefixes))
    return lines


def audit_vanilla_extra_groups() -> list[str]:
    group_defs = load_group_defs(GROUPS_DIR)
    agg_seeds = set(
        json.loads((GROUPS_DIR / "LivingWorld" / "Aggressive.json").read_text(encoding="utf-8"))[
            "IncludeGroups"
        ]
    )
    in_agg = expand_seeds(agg_seeds | {"Aggressive"}, group_defs)
    ia_groups_root = GROUPS_DIR / "Intelligent" / "Aggressive"
    ia_top: set[str] = set()
    if ia_groups_root.exists():
        for faction_dir in ia_groups_root.iterdir():
            if faction_dir.is_dir() and (faction_dir / f"{faction_dir.name}.json").exists():
                ia_top.add(faction_dir.name)
    suggested = sorted(ia_top - in_agg - NEUTRAL_STEMS)
    print("=== Vanilla assets ===")
    print("Suggested EXTRA_AMBIENT_HOSTILE_GROUPS:", suggested)
    print()
    return suggested


def emit_orphan_role_patterns() -> None:
    group_defs = load_group_defs(GROUPS_DIR)
    includes = {name: spec for name, spec in group_defs.items()}
    agg_seeds = set(
        json.loads((GROUPS_DIR / "LivingWorld" / "Aggressive.json").read_text(encoding="utf-8"))[
            "IncludeGroups"
        ]
    )
    vanilla_agg = expand_seeds(agg_seeds | {"Aggressive"}, includes)
    guard_group_coverage = vanilla_agg | {"Outlander", "Scarak"}

    orphans: list[str] = []
    for p in ROLES_DIR.rglob("*.json"):
        if "Tests" in p.parts or p.name.startswith("Test_") or p.name.startswith("Template_"):
            continue
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        if data.get("Type") == "Abstract":
            continue
        if not is_player_hostile_role(data):
            continue
        tags = groups_for_role(p.stem, group_defs)
        if tags & guard_group_coverage:
            continue
        orphans.append(p.stem)

    print("=== Orphan player-hostile roles (add to Java EXTRA_AMBIENT_HOSTILE_ROLE_PATTERNS) ===")
    print(f"count={len(orphans)}")
    for name in sorted(orphans):
        print(f"  {name}")
    print()
    print("Suggested patterns:")
    for line in suggest_role_patterns(sorted(orphans)):
        print(f"  {line}")


def main() -> None:
    groups = audit_vanilla_extra_groups()
    print("=== Java EXTRA_AMBIENT_HOSTILE_GROUPS ===")
    print(groups)
    print()
    emit_orphan_role_patterns()


if __name__ == "__main__":
    main()
