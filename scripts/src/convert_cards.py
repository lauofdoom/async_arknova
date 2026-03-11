#!/usr/bin/env python3
"""
Card Converter — Next-Ark-Nova-Cards → base_game.json
──────────────────────────────────────────────────────
Parses the TypeScript data files from the Next-Ark-Nova-Cards community
repository and outputs base_game.json for the bot's CardDatabaseLoader.

No TypeScript runtime needed — reads source files as text and resolves
i18n keys from public/locales/en/common.json.

USAGE:
    python3 scripts/src/convert_cards.py
    SOURCE_REPO=/path/to/repo python3 scripts/src/convert_cards.py

EXPECTS (cloned adjacent to this repo by default):
    ../Next-Ark-Nova-Cards/

OUTPUT:
    bot/src/main/resources/cards/base_game.json
"""

import json
import os
import re
import sys
from pathlib import Path
from typing import Optional

# ── Paths ─────────────────────────────────────────────────────────────────────

SCRIPT_DIR  = Path(__file__).parent.resolve()
REPO_ROOT   = SCRIPT_DIR.parent.parent
SOURCE_REPO = Path(os.environ.get("SOURCE_REPO", REPO_ROOT / "../Next-Ark-Nova-Cards"))
OUTPUT_PATH = REPO_ROOT / "bot/src/main/resources/cards/base_game.json"

# Only include cards from these sources at v1.0 (base game only)
INCLUDED_SOURCES = {"BASE", "PROMO"}


# ── Helpers ───────────────────────────────────────────────────────────────────

def log(msg: str) -> None:
    print(f"[convert-cards] {msg}")

def warn(msg: str) -> None:
    print(f"[convert-cards] WARN: {msg}", file=sys.stderr)

def normalize_tag(value: str) -> str:
    """'seaAnimal' → 'SEA_ANIMAL', 'Partner Zoo' → 'PARTNER_ZOO'"""
    # Insert underscore before uppercase letters (camelCase → SNAKE_CASE)
    s = re.sub(r"([a-z])([A-Z])", r"\1_\2", value)
    # Replace spaces and hyphens with underscores, then uppercase
    return re.sub(r"[\s\-]+", "_", s).upper()


# ── Translation lookup ────────────────────────────────────────────────────────

def load_translations() -> dict:
    path = SOURCE_REPO / "public/locales/en/common.json"
    with open(path, encoding="utf-8") as f:
        return json.load(f)

def resolve_i18n(key: str, translations: dict) -> Optional[str]:
    """'sponsors.s201_desc1' → 'Take 1 card from the deck or in reputation range.'"""
    obj = translations
    for part in key.split("."):
        if isinstance(obj, dict) and part in obj:
            obj = obj[part]
        else:
            return None
    return obj if isinstance(obj, str) else None


# ── Tag map ────────────────────────────────────────────────────────────────────
# Built from Tags.ts. Maps enum member name → normalised tag string.
# e.g. 'Bird' → 'BIRD', 'SeaAnimal' → 'SEA_ANIMAL'

def build_tag_map() -> dict[str, str]:
    """
    Parse Tags.ts and return a map of member_name → normalized_tag.
    Handles all three enums: AnimalTag, ContinentTag, OtherTag.
    """
    content = (SOURCE_REPO / "src/types/Tags.ts").read_text(encoding="utf-8")
    tag_map: dict[str, str] = {}

    # Match: MemberName = 'value'  or  MemberName = "value"
    member_re = re.compile(r"(\w+)\s*=\s*['\"]([^'\"]+)['\"]")

    # Walk enum blocks
    for enum_match in re.finditer(r"export enum (\w+)\s*\{([^}]+)\}", content, re.DOTALL):
        for m in member_re.finditer(enum_match.group(2)):
            member_name = m.group(1)   # e.g. 'SeaAnimal'
            value       = m.group(2)   # e.g. 'seaAnimal'
            tag_map[member_name] = normalize_tag(value)

    return tag_map


# ── Keyword → i18n map ────────────────────────────────────────────────────────

def build_keyword_map() -> dict[str, str]:
    """
    Parse KeyWords.ts and return a map of keyword_member → i18n_key.
    e.g. 'CLEVER' → 'abilities.clever_description'
    """
    content = (SOURCE_REPO / "src/types/KeyWords.ts").read_text(encoding="utf-8")
    kw_map: dict[str, str] = {}

    # static CLEVER = new KeyWord(IconName.CLEVER, 'abilities.clever_description', ...)
    pattern = re.compile(
        r"static\s+(\w+)\s*=\s*new\s+KeyWord\s*\([^,)]+,\s*['\"]([^'\"]+)['\"]",
        re.DOTALL,
    )
    for m in pattern.finditer(content):
        kw_map[m.group(1)] = m.group(2)   # 'CLEVER' → 'abilities.clever_description'

    return kw_map


# ── Card block extractor ──────────────────────────────────────────────────────

def extract_blocks(ts_content: str) -> list[str]:
    """
    Extract individual { ... } card object blocks from a TypeScript array.
    Uses bracket-depth tracking with string-literal awareness.
    """
    # Find the opening '[' of the exported array
    array_start = ts_content.find("[")
    if array_start < 0:
        return []

    blocks: list[str] = []
    depth   = 0
    start   = -1
    in_str  = False
    str_ch  = ""
    i       = array_start + 1

    while i < len(ts_content):
        ch = ts_content[i]

        if in_str:
            if ch == "\\" :         # escape — skip next char
                i += 2
                continue
            if ch == str_ch:
                in_str = False
        elif ch in ('"', "'", "`"):
            in_str = True
            str_ch = ch
        elif ch == "{":
            if depth == 0:
                start = i
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0 and start >= 0:
                blocks.append(ts_content[start : i + 1])
                start = -1
        i += 1

    return blocks


# ── Per-field extractors ──────────────────────────────────────────────────────

def get_str(block: str, field: str) -> Optional[str]:
    m = re.search(rf"\b{field}\s*:\s*['\"]([^'\"]*)['\"]", block)
    return m.group(1) if m else None

def get_int(block: str, field: str, default: int = 0) -> int:
    m = re.search(rf"\b{field}\s*:\s*(-?\d+)", block)
    return int(m.group(1)) if m else default

def get_opt_int(block: str, field: str) -> Optional[int]:
    m = re.search(rf"\b{field}\s*:\s*(\d+)", block)
    return int(m.group(1)) if m else None

def get_source(block: str) -> str:
    """CardSource.BASE → 'BASE', CardSource.PROMO → 'PROMO', etc."""
    source_values = {
        "BASE": "BASE",
        "MARINE_WORLD": "MARINE_WORLD",
        "PROMO": "PROMO",
        "FAN_MADE": "FAN_MADE",
        "ALTERNATIVE": "ALTERNATIVE",
        "BEGINNER": "BEGINNER",
    }
    m = re.search(r"\bsource\s*:\s*CardSource\.(\w+)", block)
    return source_values.get(m.group(1), "BASE") if m else "BASE"

def get_tags(block: str, tag_map: dict[str, str], field: str = "tags") -> list[str]:
    """Extract tags or requirements array and normalise using the tag_map."""
    m = re.search(rf"\b{field}\s*:\s*\[([^\]]*)\]", block, re.DOTALL)
    if not m:
        return []
    tags: list[str] = []
    for ref in re.finditer(r"(\w+Tag|OtherTag)\.(\w+)", m.group(1)):
        member = ref.group(2)
        tags.append(tag_map.get(member, normalize_tag(member)))
    return tags

def get_ability_text_animal(
    block: str, kw_map: dict[str, str], translations: dict
) -> Optional[str]:
    """
    Resolve animal ability text from new Ability(KeyWord.X, value) patterns.
    Falls back to the KeyWord member name when no translation is found.
    """
    texts: list[str] = []

    # new Ability(KeyWord.CLEVER) or new Ability(KeyWord.CLEVER, 3)
    for m in re.finditer(
        r"new\s+Ability\s*\(\s*KeyWord\.(\w+)(?:\s*,\s*([^)]+))?\s*\)", block
    ):
        member = m.group(1)
        value  = (m.group(2) or "").strip().strip("'\"")

        i18n_key = kw_map.get(member)
        text = resolve_i18n(i18n_key, translations) if i18n_key else None

        if text:
            if value:
                text = text.replace("{}", value)
            texts.append(text)
        else:
            texts.append(f"{member}: {value}" if value else member)

    return " / ".join(texts) if texts else None

def get_ability_text_effects(block: str, translations: dict) -> Optional[str]:
    """
    Resolve effectDesc i18n keys from plain-object effect arrays
    (used by Sponsors, Projects, EndGames).
    """
    texts: list[str] = []
    seen: set[str] = set()

    for m in re.finditer(r"\beffectDesc\s*:\s*['\"]([^'\"]+)['\"]", block):
        key  = m.group(1)
        text = resolve_i18n(key, translations)
        if text and text not in seen:
            texts.append(text)
            seen.add(text)

    return " / ".join(texts) if texts else None


# ── Per-type converters ───────────────────────────────────────────────────────

def convert_animals(tag_map: dict, kw_map: dict, translations: dict) -> list[dict]:
    content = (SOURCE_REPO / "src/data/Animals.ts").read_text(encoding="utf-8")
    blocks  = extract_blocks(content)
    cards: list[dict] = []

    for block in blocks:
        card_id = get_str(block, "id")
        name    = get_str(block, "name")
        if not card_id or not name:
            continue
        source = get_source(block)
        if source not in INCLUDED_SOURCES:
            continue

        cards.append({
            "id":                card_id,
            "name":              name.replace("_", " ").title(),
            "card_type":         "ANIMAL",
            "base_cost":         get_int(block, "price"),
            "min_enclosure_size": get_opt_int(block, "size"),
            "tags":              get_tags(block, tag_map, "tags"),
            "requirements":      get_tags(block, tag_map, "requirements"),
            "appeal_value":      get_int(block, "appeal"),
            "conservation_value": get_int(block, "conservationPoint"),
            "reputation_value":  get_int(block, "reputation"),
            "ability_text":      get_ability_text_animal(block, kw_map, translations),
            "effect_code":       None,
            "image_url":         None,
            "source":            source,
            "card_number":       card_id,
        })

    return cards


def convert_sponsors(tag_map: dict, translations: dict) -> list[dict]:
    content = (SOURCE_REPO / "src/data/Sponsors.ts").read_text(encoding="utf-8")
    blocks  = extract_blocks(content)
    cards: list[dict] = []

    for block in blocks:
        card_id = get_str(block, "id")
        name    = get_str(block, "name")
        if not card_id or not name:
            continue
        source = get_source(block)
        if source not in INCLUDED_SOURCES:
            continue

        cards.append({
            "id":                card_id,
            "name":              name.replace("_", " ").title(),
            "card_type":         "SPONSOR",
            "base_cost":         get_int(block, "strength"),
            "min_enclosure_size": None,
            "tags":              get_tags(block, tag_map, "tags"),
            "requirements":      get_tags(block, tag_map, "requirements"),
            "appeal_value":      get_int(block, "appeal"),
            "conservation_value": get_int(block, "conservationPoint"),
            "reputation_value":  get_int(block, "reputation"),
            "ability_text":      get_ability_text_effects(block, translations),
            "effect_code":       None,
            "image_url":         None,
            "source":            source,
            "card_number":       card_id,
        })

    return cards


def convert_projects(tag_map: dict, translations: dict) -> list[dict]:
    content = (SOURCE_REPO / "src/data/Projects.ts").read_text(encoding="utf-8")
    blocks  = extract_blocks(content)
    cards: list[dict] = []

    for block in blocks:
        card_id = get_str(block, "id")
        name    = get_str(block, "name")
        if not card_id or not name:
            continue
        source = get_source(block)
        if source not in INCLUDED_SOURCES:
            continue

        cards.append({
            "id":                card_id,
            "name":              name.replace("_", " ").title(),
            "card_type":         "CONSERVATION",
            "base_cost":         0,
            "min_enclosure_size": None,
            "tags":              get_tags(block, tag_map, "tags"),
            "requirements":      [],
            "appeal_value":      0,
            "conservation_value": 0,
            "reputation_value":  0,
            "ability_text":      get_ability_text_effects(block, translations),
            "effect_code":       None,
            "image_url":         None,
            "source":            source,
            "card_number":       card_id,
        })

    return cards


def convert_endgames(translations: dict) -> list[dict]:
    content = (SOURCE_REPO / "src/data/EndGames.ts").read_text(encoding="utf-8")
    blocks  = extract_blocks(content)
    cards: list[dict] = []

    for block in blocks:
        card_id = get_str(block, "id")
        name    = get_str(block, "name")
        if not card_id or not name:
            continue
        source = get_source(block)
        if source not in INCLUDED_SOURCES:
            continue

        cards.append({
            "id":                card_id,
            "name":              name,   # EndGame cards already have proper names
            "card_type":         "FINAL_SCORING",
            "base_cost":         0,
            "min_enclosure_size": None,
            "tags":              [],
            "requirements":      [],
            "appeal_value":      0,
            "conservation_value": 0,
            "reputation_value":  0,
            "ability_text":      get_ability_text_effects(block, translations),
            "effect_code":       None,
            "image_url":         None,
            "source":            source,
            "card_number":       card_id,
        })

    return cards


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    log(f"Source repo : {SOURCE_REPO}")
    log(f"Output      : {OUTPUT_PATH}")

    if not SOURCE_REPO.exists():
        print(
            f"\nERROR: Next-Ark-Nova-Cards repo not found at {SOURCE_REPO}\n\n"
            "Clone it with:\n"
            "  git clone https://github.com/Ender-Wiggin2019/Next-Ark-Nova-Cards "
            f"  {SOURCE_REPO}\n",
            file=sys.stderr,
        )
        sys.exit(1)

    log("Loading English translations...")
    translations = load_translations()

    log("Building tag map from Tags.ts...")
    tag_map = build_tag_map()
    log(f"  {len(tag_map)} tag mappings")

    log("Building keyword map from KeyWords.ts...")
    kw_map = build_keyword_map()
    log(f"  {len(kw_map)} keyword mappings")

    all_cards: list[dict] = []

    log("Converting Animals.ts...")
    animals = convert_animals(tag_map, kw_map, translations)
    log(f"  → {len(animals)} animals")
    all_cards.extend(animals)

    log("Converting Sponsors.ts...")
    sponsors = convert_sponsors(tag_map, translations)
    log(f"  → {len(sponsors)} sponsors")
    all_cards.extend(sponsors)

    log("Converting Projects.ts...")
    projects = convert_projects(tag_map, translations)
    log(f"  → {len(projects)} conservation projects")
    all_cards.extend(projects)

    log("Converting EndGames.ts...")
    endgames = convert_endgames(translations)
    log(f"  → {len(endgames)} final-scoring cards")
    all_cards.extend(endgames)

    # Deduplicate (keep first occurrence)
    seen: set[str] = set()
    deduped = []
    for card in all_cards:
        if card["id"] not in seen:
            seen.add(card["id"])
            deduped.append(card)

    # Stable sort: type order, then numeric id
    type_order = ["ANIMAL", "SPONSOR", "CONSERVATION", "FINAL_SCORING"]
    def sort_key(c: dict):
        t = type_order.index(c["card_type"]) if c["card_type"] in type_order else 99
        try:
            n = int(c["id"])
        except ValueError:
            n = 99999
        return (t, n)

    deduped.sort(key=sort_key)

    # Write output
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(deduped, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # Summary
    total      = len(deduped)
    with_text  = sum(1 for c in deduped if c["ability_text"])
    with_image = sum(1 for c in deduped if c["image_url"])
    automated  = sum(1 for c in deduped if c["effect_code"])

    print()
    print("═" * 52)
    print(f"  Animals:          {len(animals)}")
    print(f"  Sponsors:         {len(sponsors)}")
    print(f"  Conservation:     {len(projects)}")
    print(f"  Final Scoring:    {len(endgames)}")
    print(f"  ─────────────────────────────────────────────")
    print(f"  Total cards:      {total}")
    print(f"  With ability text:{with_text}/{total}")
    print(f"  With image URL:   {with_image}/{total}")
    print(f"  effect_code set:  {automated}/{total} (add via DB, not this script)")
    print(f"  Output: {OUTPUT_PATH}")
    print("═" * 52)


if __name__ == "__main__":
    main()
