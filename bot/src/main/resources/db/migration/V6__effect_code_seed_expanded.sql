-- ─────────────────────────────────────────────────────────────────────────────
-- Ark Nova Async — Effect Code Seed Expanded
-- Migration V6
--
-- Adds effect_code for three new groups of cards now supported by EffectExecutor:
--
--   Group A (1):  Cards with null abilityText — empty abilities (no on-play effect).
--   Group B (4):  Animals that advance the break track AND gain money on placement.
--                 Requires BREAK_TRACK resource support added in this release.
--   Group C (4):  Animals with "Gain {Appeal-1} for each predator icon in your zoo."
--                 Uses new GAIN_PER_ICON effect type.
--   Group D (7):  Petting zoo animals with "Gain {Appeal-3} for each Petting Zoo
--                 Animal (PET) icon in your zoo." Uses GAIN_PER_ICON with icon=PET.
--   Group E (1):  Donkey — X_TOKENS gain + PET per-icon appeal (two effects).
-- ─────────────────────────────────────────────────────────────────────────────


-- ── Group A: Null abilityText — no on-play effect ─────────────────────────────

-- Card 009: Diverse Species Zoo (FINAL_SCORING)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '009';


-- ── Group B: Advance break track + gain money ─────────────────────────────────
-- abilityText pattern: "Advance the break token N spaces. Gain {Money-N}."
-- Two ON_PLAY effects: GAIN BREAK_TRACK N, GAIN MONEY N.

-- Card 413: Cougar (ANIMAL, cost 14) — break +3, money +3
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN","resource":"BREAK_TRACK","amount":3},
  {"trigger":"ON_PLAY","type":"GAIN","resource":"MONEY","amount":3}
]}'::jsonb
WHERE id = '413';

-- Card 444: Alpine Ibex (ANIMAL, cost 10) — break +2, money +2
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN","resource":"BREAK_TRACK","amount":2},
  {"trigger":"ON_PLAY","type":"GAIN","resource":"MONEY","amount":2}
]}'::jsonb
WHERE id = '444';

-- Card 461: Horsfield's Tortoise (ANIMAL, cost 4) — break +4, money +4
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN","resource":"BREAK_TRACK","amount":4},
  {"trigger":"ON_PLAY","type":"GAIN","resource":"MONEY","amount":4}
]}'::jsonb
WHERE id = '461';

-- Card 462: Northern Plains Gray Langur (ANIMAL, cost 8) — break +3, money +3
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN","resource":"BREAK_TRACK","amount":3},
  {"trigger":"ON_PLAY","type":"GAIN","resource":"MONEY","amount":3}
]}'::jsonb
WHERE id = '462';


-- ── Group C: Gain {Appeal-1} for each predator icon in your zoo ───────────────
-- GAIN_PER_ICON: amount=1 per PREDATOR icon, no cap.

-- Card 402: Lion (ANIMAL, cost 18)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":1,
   "condition":{"type":"ICON","icon":"PREDATOR","count":0,"max":0}}
]}'::jsonb
WHERE id = '402';

-- Card 417: Wolf (ANIMAL, cost 9)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":1,
   "condition":{"type":"ICON","icon":"PREDATOR","count":0,"max":0}}
]}'::jsonb
WHERE id = '417';

-- Card 423: New Zealand Sea Lion (ANIMAL, cost 10)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":1,
   "condition":{"type":"ICON","icon":"PREDATOR","count":0,"max":0}}
]}'::jsonb
WHERE id = '423';

-- Card 424: Australian Dingo (ANIMAL, cost 7)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":1,
   "condition":{"type":"ICON","icon":"PREDATOR","count":0,"max":0}}
]}'::jsonb
WHERE id = '424';


-- ── Group D: Gain {Appeal-3} for each Petting Zoo Animal (PET) icon in zoo ────
-- GAIN_PER_ICON: amount=3 per PET icon, no cap.

-- Card 519: (Domestic) Goat (ANIMAL, cost 4)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '519';

-- Card 520: Sheep (ANIMAL, cost 5)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '520';

-- Card 521: Horse (ANIMAL, cost 7)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '521';

-- Card 523: Domestic Rabbit (ANIMAL, cost 4)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '523';

-- Card 525: Guinea Pig (ANIMAL, cost 3)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '525';

-- Card 526: Alpaca (ANIMAL, cost 6)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '526';

-- Card 527: Coconut Lorikeet (ANIMAL, cost 8)
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '527';


-- ── Group E: Multi-effect cards ───────────────────────────────────────────────

-- Card 522: Donkey (ANIMAL, cost 5)
-- abilityText: "Gain {} {XToken} -token. / Gain {Appeal-3} for each Petting Zoo Animal icon."
-- Two effects: X_TOKENS +1 then GAIN_PER_ICON APPEAL 3 per PET.
UPDATE card_definitions
SET effect_code = '{"abilities":[
  {"trigger":"ON_PLAY","type":"GAIN","resource":"X_TOKENS","amount":1},
  {"trigger":"ON_PLAY","type":"GAIN_PER_ICON","resource":"APPEAL","amount":3,
   "condition":{"type":"ICON","icon":"PET","count":0,"max":0}}
]}'::jsonb
WHERE id = '522';
