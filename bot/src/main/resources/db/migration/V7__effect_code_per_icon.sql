-- ─────────────────────────────────────────────────────────────────────────────
-- Ark Nova Async — Effect Code Per-Icon (V7)
-- Migration V7
--
-- Populates effect_code for all ANIMAL cards whose abilityText is purely of the
-- form "Gain X [resource] for each [icon] icon [in your zoo / in all zoos]
-- (max. N)".  These map cleanly to the GAIN_PER_ICON type introduced in V7.
--
-- Skips V5 cards: 220, 206, 414, 406, 407, 433, 468, 484, 486, 488,
--                 493, 495, 498, 205, 223
-- Skips V6 card:  209
--
-- Icon keys are the tag key strings used in the player's icon map and in the
-- card's tags array (e.g. "PREDATOR", "EUROPE", "PET").
--
-- max: 0 means no cap (omit from JSON is equivalent; 0 is stored explicitly
--      here only where the abilityText states no cap, i.e. predator cards).
--      Cards with "max. 8" in their abilityText get "max":8.
--
-- Multi-effect cards (SKIP):
--   522 Donkey     — "Gain {XToken} -token. / Gain {Appeal-3} for each PET icon"
--   524 Mangalica  — discard/draw choice + per-PET appeal gain
--   528 Bennett    — place card under this card + per-PET appeal gain
--   341 Capybara   — per-PET appeal + conditional adjacency appeal gain
-- ─────────────────────────────────────────────────────────────────────────────


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 1 — PREDATOR icon → APPEAL (no cap)
-- ══════════════════════════════════════════════════════════════════════════════

-- Card 402: Lion (ANIMAL — PREDATOR, AFRICA)
-- abilityText: "Gain {Appeal-1} for each predator icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PREDATOR APPEAL 1 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PREDATOR","resource":"APPEAL","amount":1,"max":0}]}'::jsonb
WHERE id = '402';

-- Card 417: Wolf (ANIMAL — PREDATOR, EUROPE)
-- abilityText: "Gain {Appeal-1} for each predator icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PREDATOR APPEAL 1 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PREDATOR","resource":"APPEAL","amount":1,"max":0}]}'::jsonb
WHERE id = '417';

-- Card 423: New Zealand Sea Lion (ANIMAL — PREDATOR, AUSTRALIA)
-- abilityText: "Gain {Appeal-1} for each predator icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PREDATOR APPEAL 1 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PREDATOR","resource":"APPEAL","amount":1,"max":0}]}'::jsonb
WHERE id = '423';

-- Card 424: Australian Dingo (ANIMAL — PREDATOR, AUSTRALIA)
-- abilityText: "Gain {Appeal-1} for each predator icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PREDATOR APPEAL 1 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PREDATOR","resource":"APPEAL","amount":1,"max":0}]}'::jsonb
WHERE id = '424';


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 2 — Continent icon → APPEAL (max 8, counted across all zoos)
-- ══════════════════════════════════════════════════════════════════════════════

-- Card 418: Eurasian Lynx (ANIMAL — PREDATOR, EUROPE)
-- abilityText: "Gain {Appeal-1} for each Europe icon in all zoos (max. 8)."
-- Effect: ON_PLAY → GAIN_PER_ICON EUROPE APPEAL 1 max:8
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"EUROPE","resource":"APPEAL","amount":1,"max":8}]}'::jsonb
WHERE id = '418';

-- Card 436: American Bison (ANIMAL — HERBIVORE, AMERICAS)
-- abilityText: "Gain {Appeal-1} for each Americas icon in all zoos (max. 8)."
-- Effect: ON_PLAY → GAIN_PER_ICON AMERICAS APPEAL 1 max:8
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"AMERICAS","resource":"APPEAL","amount":1,"max":8}]}'::jsonb
WHERE id = '436';

-- Card 452: Senegal Bushbaby (ANIMAL — PRIMATE, AFRICA)
-- abilityText: "Gain {Appeal-1} for each ContinentTag.Africa icon in all zoos (max. 8)."
-- Effect: ON_PLAY → GAIN_PER_ICON AFRICA APPEAL 1 max:8
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"AFRICA","resource":"APPEAL","amount":1,"max":8}]}'::jsonb
WHERE id = '452';

-- Card 476: Komodo Dragon (ANIMAL — REPTILE, ASIA)
-- abilityText: "Gain {Appeal-1} for each ContinentTag.Asia icon in all zoos (max. 8)."
-- Effect: ON_PLAY → GAIN_PER_ICON ASIA APPEAL 1 max:8
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"ASIA","resource":"APPEAL","amount":1,"max":8}]}'::jsonb
WHERE id = '476';

-- Card 517: Laughing Kookaburra (ANIMAL — BIRD, AUSTRALIA)
-- abilityText: "Gain {Appeal-1} for each ContinentTag.Australia icon in all zoos (max. 8)."
-- Effect: ON_PLAY → GAIN_PER_ICON AUSTRALIA APPEAL 1 max:8
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"AUSTRALIA","resource":"APPEAL","amount":1,"max":8}]}'::jsonb
WHERE id = '517';


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 3 — PET icon → APPEAL (simple per-PET cards only)
-- ══════════════════════════════════════════════════════════════════════════════
-- Icon key: "PET" (the tag stored in the player's icon map for Petting Zoo Animal icons)

-- Card 519: (Domestic) Goat (ANIMAL — PET)
-- abilityText: "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PET APPEAL 3 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PET","resource":"APPEAL","amount":3,"max":0}]}'::jsonb
WHERE id = '519';

-- Card 520: Sheep (ANIMAL — PET)
-- abilityText: "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PET APPEAL 3 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PET","resource":"APPEAL","amount":3,"max":0}]}'::jsonb
WHERE id = '520';

-- Card 521: Horse (ANIMAL — PET)
-- abilityText: "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PET APPEAL 3 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PET","resource":"APPEAL","amount":3,"max":0}]}'::jsonb
WHERE id = '521';

-- Card 523: Domestic Rabbit (ANIMAL — PET)
-- abilityText: "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PET APPEAL 3 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PET","resource":"APPEAL","amount":3,"max":0}]}'::jsonb
WHERE id = '523';

-- Card 525: Guinea Pig (ANIMAL — PET)
-- abilityText: "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PET APPEAL 3 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PET","resource":"APPEAL","amount":3,"max":0}]}'::jsonb
WHERE id = '525';

-- Card 526: Alpaca (ANIMAL — PET)
-- abilityText: "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PET APPEAL 3 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PET","resource":"APPEAL","amount":3,"max":0}]}'::jsonb
WHERE id = '526';

-- Card 527: Coconut Lorikeet (ANIMAL — PET)
-- abilityText: "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
-- Effect: ON_PLAY → GAIN_PER_ICON PET APPEAL 3 (no cap)
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN_PER_ICON","icon":"PET","resource":"APPEAL","amount":3,"max":0}]}'::jsonb
WHERE id = '527';


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 4 — SKIP catalogue (multi-effect PET cards)
-- ══════════════════════════════════════════════════════════════════════════════

-- Card 522: Donkey (ANIMAL — PET) — SKIP: multi-effect
--   "Gain {} {XToken} -token. / Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
--   First ability is GAIN X_TOKENS 1; second is GAIN_PER_ICON PET APPEAL 3.
--   Multi-ability effect_code is not yet supported by EffectExecutor for mixed types.

-- Card 524: Mangalica (ANIMAL — PET) — SKIP: multi-effect
--   "Choose up to 1x: Discard 1 card from the display and replenish OR discard 1 card from
--    your hand and draw 1 other from the deck. / Gain {Appeal-3} for each Petting Zoo Animal
--    icon in your zoo."
--   First ability requires a player decision (discard/draw); not automatable.

-- Card 528: Bennett (ANIMAL — PET) — SKIP: multi-effect
--   "You may place 1 card(s) from your hand under this card to gain {Appeal-2} .
--    / Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo."
--   First ability requires a hand-management decision; not automatable.

-- Card 341: Capybara (PROMO — PET) — SKIP: multi-effect
--   "Gain {Appeal-3} for each Petting Zoo Animal icon in your zoo.
--    / Gain {Appeal-2} if the petting zoo is adjacent to a water space."
--   Second ability requires board-topology evaluation; not automatable.
