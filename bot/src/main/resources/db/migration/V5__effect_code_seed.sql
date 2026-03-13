-- ─────────────────────────────────────────────────────────────────────────────
-- Ark Nova Async — Effect Code Seed (15 cards)
-- Migration V5
--
-- Populates effect_code for cards whose abilityText unambiguously maps to a
-- single automated ON_PLAY effect supported by EffectExecutor.
--
-- Cards are chosen from three groups:
--
--   Group A (2): Sponsors with a single unconditional resource GAIN.
--                abilityText is completely resolvable by the engine.
--
--   Group B (1): Animal with a plain X-token gain.
--                abilityText: "Gain {} {XToken} -token."
--
--   Group C (12): Cards with null abilityText — no on-play effect whatsoever.
--                 Setting effect_code = '{"abilities":[]}' marks isAutomated()=true
--                 with zero effects to execute, which is semantically correct.
-- ─────────────────────────────────────────────────────────────────────────────


-- ── Group A: Single unconditional resource GAIN ───────────────────────────────

-- Card 220: Federal Grants (SPONSOR, level 3)
-- abilityText: "**Income:** Gain {Money-3} ."
-- Effect: ON_PLAY → GAIN MONEY 3
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN","resource":"MONEY","amount":3}]}'::jsonb
WHERE id = '220';

-- Card 206: Medical Breakthrough (SPONSOR, level 2)
-- abilityText: "Income: Gain {ConservationPoint-1} ."
-- Effect: ON_PLAY → GAIN CONSERVATION 1
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN","resource":"CONSERVATION","amount":1}]}'::jsonb
WHERE id = '206';


-- ── Group B: Plain X-token gain ───────────────────────────────────────────────

-- Card 414: South American Coati (ANIMAL, cost 8)
-- abilityText: "Gain {} {XToken} -token."
-- Effect: ON_PLAY → GAIN X_TOKENS 1
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN","resource":"X_TOKENS","amount":1}]}'::jsonb
WHERE id = '414';


-- ── Group C: Null abilityText — no on-play effects ────────────────────────────
-- These cards have no ability text at all. Marking them automated with an empty
-- abilities list is correct: isAutomated()=true, requiresManualResolution()=false,
-- and EffectExecutor.execute() returns an empty delta map.

-- Card 406: Siberian Tiger (ANIMAL, cost 30)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '406';

-- Card 407: Sumatran Tiger (ANIMAL, cost 23)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '407';

-- Card 433: Giant Panda (ANIMAL, cost 28)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '433';

-- Card 468: Cotton-Top Tamarin (ANIMAL, cost 14)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '468';

-- Card 484: European Pond Turtle (ANIMAL, cost 6)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '484';

-- Card 486: Common Wall Lizard (ANIMAL, cost 4)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '486';

-- Card 488: Slow Worm (ANIMAL, cost 4)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '488';

-- Card 493: Thorny Devil (ANIMAL, cost 9)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '493';

-- Card 495: Secretary Bird (ANIMAL, cost 11)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '495';

-- Card 498: Shoebill (ANIMAL, cost 15)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '498';

-- Card 205: Gorilla Field Research (SPONSOR, level 4)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '205';

-- Card 223: Science Institute (SPONSOR, level 3)
UPDATE card_definitions
SET effect_code = '{"abilities":[]}'::jsonb
WHERE id = '223';
