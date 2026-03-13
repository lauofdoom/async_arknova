-- ─────────────────────────────────────────────────────────────────────────────
-- Ark Nova Async — Effect Code Expanded (V6)
-- Migration V6
--
-- Expands effect_code coverage beyond the 15 cards seeded in V5.
-- Skips V5 cards: 220, 206, 414, 406, 407, 433, 468, 484, 486, 488,
--                 493, 495, 498, 205, 223
--
-- All 238 cards in base_game.json have been reviewed. Categories:
--
--   1 — Simple unconditional ON_PLAY GAIN → UPDATE with effect_code
--   2 — Conditional ON_PLAY GAIN (MIN_ICON) → UPDATE with effect_code
--   3 — null abilityText → UPDATE with empty abilities array
--   4 — SKIP: complex effect, triggered/repeating income, draw cards,
--              board-state decisions, inter-player effects, scoring, etc.
--
-- CONSERVATION cards (101-132): all describe project requirements, not
--   ON_PLAY triggers — all SKIP.
-- FINAL_SCORING cards (001-011): all describe end-game scoring formulas —
--   all SKIP.
-- PROMO cards: reviewed individually below.
-- ─────────────────────────────────────────────────────────────────────────────


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 1 — Simple unconditional ON_PLAY GAIN (SPONSOR cards)
-- ══════════════════════════════════════════════════════════════════════════════

-- Card 209: Technology Institute (SPONSOR, SCIENCE tag)
-- abilityText: "Income: Gain 1 {XToken} -token."
-- Effect: ON_PLAY → GAIN X_TOKENS 1
UPDATE card_definitions
SET effect_code = '{"abilities":[{"trigger":"ON_PLAY","type":"GAIN","resource":"X_TOKENS","amount":1}]}'::jsonb
WHERE id = '209';


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 2 — Conditional ON_PLAY GAIN (MIN_ICON) — NONE FOUND
-- ══════════════════════════════════════════════════════════════════════════════
--
-- After reviewing all 238 cards, no additional cards were found with
-- abilityText in the form "If you have at least N [icon], gain X [resource]"
-- that maps cleanly to CONDITIONAL_GAIN + MIN_ICON.
--
-- Cards like "Gain {Appeal-1} for each predator icon" (402 Lion, 417 Wolf,
-- 423 New Zealand Sea Lion, 424 Australian Dingo) use a per-icon variable
-- multiplier — these are NOT currently supported by the engine (which only
-- supports a single fixed-amount gain when a threshold is met).
--
-- Cards like "Gain 1/2/3 {XToken} -tokens for 1/3/5 primate icons"
-- (459 Red-Shanked Douc, 464 Brown Spider Monkey) have three discrete tiers
-- and are likewise NOT currently supported.


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 3 — Null abilityText → empty abilities (no new cards beyond V5)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- All ANIMAL and SPONSOR cards with null abilityText were already covered in
-- V5. No additional null-abilityText ANIMAL/SPONSOR cards exist in the dataset.
-- (CONSERVATION and FINAL_SCORING cards with null abilityText — e.g. card 009
-- "Diverse Species Zoo" — are intentionally excluded because those card types
-- do not participate in ON_PLAY effect execution.)


-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 4 — SKIP catalogue (no UPDATE; comments only)
-- ══════════════════════════════════════════════════════════════════════════════
-- Every non-V5 card that is NOT automatable is documented here so future
-- migrations know why it was deferred.

-- ── ANIMAL — PREDATOR ──────────────────────────────────────────────────────

-- Card 401: Cheetah — SKIP: draws cards from deck
-- Card 402: Lion — SKIP: "Gain {Appeal-1} for each predator icon" — per-icon variable gain
-- Card 403: Leopard — SKIP: reveal/choose from deck
-- Card 404: Caracal — SKIP: reveal/choose from deck
-- Card 405: Fennec Fox — SKIP: places action card on slot (board-state action)
-- Card 408: Sloth Bear — SKIP: places Association Action card on slot
-- Card 409: Sun Bear — SKIP: take the Association action (extra action)
-- Card 410: Yellow-Throated Marten — SKIP: reveal/choose from deck
-- Card 411: Grizzly Bear — SKIP: per-bear x-token gain (max 3) + hire worker — complex multi-effect
-- Card 412: Jaguar — SKIP: reveal/choose from deck
-- Card 413: Cougar — SKIP: advance break token 3 spaces + Gain {Money-3} — break token not automatable
-- Card 415: Raccoon — SKIP: places Association Action card on slot
-- Card 416: Eurasian Brown Bear — SKIP: place multiplier token + hire worker
-- Card 417: Wolf — SKIP: "Gain {Appeal-1} for each predator icon" — per-icon variable gain
-- Card 418: Eurasian Lynx — SKIP: "Gain {Appeal-1} for each Europe icon in all zoos (max. 8)" — per-icon variable gain across all zoos
-- Card 419: European Badger — SKIP: places Animal Action card on slot
-- Card 420: Stoat — SKIP: reveal/choose from deck
-- Card 421: New Zealand Fur Seal — SKIP: hire association worker
-- Card 422: Australian Sea Lion — SKIP: sell cards from hand
-- Card 423: New Zealand Sea Lion — SKIP: "Gain {Appeal-1} for each predator icon" — per-icon variable gain
-- Card 424: Australian Dingo — SKIP: "Gain {Appeal-1} for each predator icon" — per-icon variable gain
-- Card 425: Tasmanian Devil — SKIP: place card(s) under this card to gain appeal — hand-management decision

-- ── ANIMAL — HERBIVORE ─────────────────────────────────────────────────────

-- Card 426: African Bush Elephant — SKIP: draw/keep Final Scoring cards
-- Card 427: White Rhinoceros — SKIP: add unused conservation project to hand
-- Card 428: Giraffe — SKIP: place Sponsors Action card on slot
-- Card 429: Grevy — SKIP: place Sponsors Action card on slot + enclosure-sharing rule
-- Card 430: Pygmy Hippopotamus — SKIP: take the Sponsors action (extra action)
-- Card 431: Asian Elephant — SKIP: draw/keep Final Scoring cards
-- Card 432: Indian Rhinoceros — SKIP: add unused conservation project to hand
-- Card 434: Red Panda — SKIP: place multiplier token on Sponsors Action card
-- Card 435: Malayan Tapir — SKIP: up to 3x discard/draw card choices
-- Card 436: American Bison — SKIP: "Gain {Appeal-1} for each Americas icon in all zoos (max. 8)" — per-icon variable gain
-- Card 437: Muskox — SKIP: add all sponsor cards from display to hand
-- Card 438: Reindeer — SKIP: enclosure-sharing rule
-- Card 439: Lama — SKIP: enclosure-sharing rule
-- Card 440: Mountain Tapir — SKIP: up to 2x discard/draw card choices
-- Card 441: European Bison — SKIP: add all sponsor cards from display to hand
-- Card 442: Moose — SKIP: place multiplier token + enclosure-sharing rule
-- Card 443: Red Deer — SKIP: enclosure-sharing rule
-- Card 444: Alpine Ibex — SKIP: advance break token 2 + Gain {Money-2} — break token not automatable
-- Card 445: Crested Porcupine — SKIP: up to 2x discard/draw card choices
-- Card 446: Dugong — SKIP: up to 4x discard/draw card choices
-- Card 447: Red Kangaroo — SKIP: place cards under this card + enclosure-sharing rule
-- Card 448: Koala — SKIP: place cards under this card for appeal
-- Card 449: Platypus — SKIP: each player ahead gains venom tokens (inter-player effect)
-- Card 450: Common Wombat — SKIP: place card under this card for appeal

-- ── ANIMAL — PRIMATE ──────────────────────────────────────────────────────

-- Card 451: Proboscis Monkey — SKIP: add conservation project to hand
-- Card 452: Senegal Bushbaby — SKIP: "Gain {Appeal-1} for each Africa icon in all zoos (max. 8)" — per-icon variable gain
-- Card 453: Collared Mangabey — SKIP: take the Card action (extra action)
-- Card 454: Ring-Tailed Lemur — SKIP: sell cards from hand
-- Card 455: Mantled Guereza — SKIP: place any action card on slot
-- Card 456: Barbary Macaque — SKIP: draw from or take money from leading player (inter-player effect)
-- Card 457: Mandrill — SKIP: place multiplier token on Card Action card
-- Card 458: Japanese Macaque — SKIP: draw from or take money from two leading players (inter-player effect)
-- Card 459: Red-Shanked Douc — SKIP: tiered 1/2/3 x-token gain for 1/3/5 primate icons — multi-tier conditional
-- Card 460: Dusky-Leaf Monkey — SKIP: place any action card on slot
-- Card 461: Horsfield — SKIP: advance break token 4 + Gain {Money-4} — break token not automatable
-- Card 462: Northern Plains Gray Langur — SKIP: advance break token 3 + Gain {Money-3} — break token not automatable
-- Card 463: Panamanian White-Faced Capuchin — SKIP: draw from or take money from leading player (inter-player)
-- Card 464: Brown Spider Monkey — SKIP: tiered 1/2/3 x-token gain for 1/3/5 primate icons — multi-tier conditional
-- Card 465: Golden Lion Tamarin — SKIP: place any action card on slot
-- Card 466: Bolivian Red Howler — SKIP: place Card Action card on slot
-- Card 467: Ecuadorian Squirrel Monkey — SKIP: place any action card on slot

-- ── ANIMAL — REPTILE ──────────────────────────────────────────────────────

-- Card 469: Nile Crocodile — SKIP: 2x gain any card from display
-- Card 470: Western Green Mamba — SKIP: each player ahead gains venom tokens (inter-player)
-- Card 471: African Spurred Tortoise — SKIP: sell cards from hand
-- Card 472: Rock Monitor — SKIP: sell cards from hand
-- Card 473: Common Agama — SKIP: sell cards from hand
-- Card 474: Indian Rock Python — SKIP: each player ahead gains constriction tokens (inter-player)
-- Card 475: King Cobra — SKIP: take action from another player's action card (inter-player)
-- Card 476: Komodo Dragon — SKIP: "Gain {Appeal-1} for each Asia icon in all zoos (max. 8)" — per-icon variable gain
-- Card 477: Veiled Chameleon — SKIP: gain any card from display
-- Card 478: Chinese Water Dragon — SKIP: sell cards from hand
-- Card 479: American Alligator — SKIP: gain any card from display
-- Card 480: Broad-Snouted Caiman — SKIP: gain any card from display
-- Card 481: Galapagos Giant Tortoise — SKIP: sell up to 4 cards from hand
-- Card 482: Anaconda — SKIP: each player ahead gains constriction tokens (inter-player)
-- Card 483: Boa Constrictor — SKIP: each player ahead gains constriction tokens (inter-player)
-- Card 485: Common European Adder — SKIP: take action from another player's action card (inter-player)
-- Card 487: European Grass Snake — SKIP: place any action card on slot
-- Card 489: Saltwater Crocodile — SKIP: 2x gain any card from display
-- Card 490: Gould — SKIP: shuffle discard and draw from it
-- Card 491: Frilled Lizard — SKIP: draw card(s) from deck
-- Card 492: Inland Taipan — SKIP: each player ahead gains venom tokens (inter-player)

-- ── ANIMAL — BIRD ─────────────────────────────────────────────────────────

-- Card 494: African Ostrich — SKIP: draw 2 cards from deck
-- Card 496: Marabou — SKIP: shuffle discard and draw from it
-- Card 497: Lesser Flamingo — SKIP: place free kiosk or pavilion (building placement decision)
-- Card 499: Cinereous Vulture — SKIP: shuffle discard and draw from it
-- Card 500: Long-Billed Vulture — SKIP: shuffle discard and draw from it
-- Card 501: Indian Peafowl — SKIP: up to 2x place free kiosk or pavilion
-- Card 502: Great Hornbill — SKIP: place Building Action card on slot
-- Card 503: Snowy Owl — SKIP: draw 4 cards, keep 2
-- Card 504: Andean Condor — SKIP: shuffle discard and draw from it
-- Card 505: Bald Eagle — SKIP: take 1 other action (extra action)
-- Card 506: King Vulture — SKIP: shuffle discard and draw from it
-- Card 507: Greater Rhea — SKIP: draw 1 card from deck
-- Card 508: Scarlet Macaw — SKIP: up to 3x place free kiosk or pavilion
-- Card 509: Golden Eagle — SKIP: take 1 other action (extra action)
-- Card 510: White Stork — SKIP: place multiplier token on Building Action card
-- Card 511: Greater Flamingo — SKIP: place free kiosk or pavilion
-- Card 512: Eurasian Eagle-Owl — SKIP: draw 4 cards, keep 2
-- Card 513: Barn Owl — SKIP: draw 4 cards, keep 2
-- Card 514: Emu — SKIP: place Large Bird Aviary for free (building placement)
-- Card 515: Australian Pelican — SKIP: take the Building action (extra action)
-- Card 516: Northern Cassowary — SKIP: place multiplier token on Building Action card
-- Card 517: Laughing Kookaburra — SKIP: "Gain {Appeal-1} for each Australia icon in all zoos (max. 8)" — per-icon variable gain
-- Card 518: Lesser Bird-Of-Paradise — SKIP: place free kiosk or pavilion

-- ── ANIMAL — PET ──────────────────────────────────────────────────────────

-- Card 341: Capybara (PROMO) — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 519: (Domestic) Goat — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 520: Sheep — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 521: Horse — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 522: Donkey — SKIP: "Gain {} {XToken} -token. / Gain {Appeal-3} for each Petting Zoo Animal icon" — multi-effect, second part is per-icon variable
-- Card 523: Domestic Rabbit — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 524: Mangalica — SKIP: discard/draw choice + per-icon appeal gain — multi-effect
-- Card 525: Guinea Pig — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 526: Alpaca — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 527: Coconut Lorikeet — SKIP: "Gain {Appeal-3} for each Petting Zoo Animal icon" — per-icon variable gain
-- Card 528: Bennett — SKIP: place card under this card + per-icon appeal gain — multi-effect

-- ── SPONSOR — SCIENCE ─────────────────────────────────────────────────────

-- Card 201: Science Lab — SKIP: take 1 card from deck/reputation range + conditional conservation gain
-- Card 202: Spokesperson — SKIP: ongoing triggered ability (each time you play a research icon)
-- Card 203: Veterinarian — SKIP: passive rule modifier (conservation project cost reduction)
-- Card 204: Science Museum — SKIP: ongoing triggered ability (each time you play a research icon)
-- Card 207: Basic Research — SKIP: complex conditional conservation gain + gives other players money
-- Card 208: Science Library — SKIP: ongoing trigger + conditional conservation gain
-- Card 219: Diversity Researcher — SKIP: passive rule modifier (build over water/rock) + conditional money gain
-- Card 221: Archeologist — SKIP: ongoing triggered ability (border space placement bonuses)
-- Card 222: Release Of Patents — SKIP: per-icon conservation gain + gives other players money
-- Card 224: Migration Recording — SKIP: ongoing triggered ability (each release into wild conservation project)
-- Card 225: Quarantine Lab — SKIP: passive immunity + conditional conservation gain
-- Card 226: Foreign Institute — SKIP: "Gain {ConservationPoint-1} for 5 different continent icons" — conditional on icon diversity (not MIN_ICON of a single icon type)

-- ── SPONSOR — CONTINENT / CATEGORY ───────────────────────────────────────

-- Card 210: Expert On The Americas — SKIP: ongoing trigger (each time you play an Americas icon)
-- Card 211: Expert On Europe — SKIP: ongoing trigger (each time you play a Europe icon)
-- Card 212: Expert On Australia — SKIP: ongoing trigger (each time you play an Australia icon)
-- Card 213: Expert On Asia — SKIP: ongoing trigger (each time you play an Asia icon)
-- Card 214: Expert On Africa — SKIP: ongoing trigger (each time you play an Africa icon)

-- ── SPONSOR — CONSERVATION / PARTNER ─────────────────────────────────────

-- Card 215: Breeding Cooperation — SKIP: places player tokens + passive rule modifier for conservation support
-- Card 216: Talented Communicator — SKIP: hire association worker (requires worker placement)
-- Card 217: Engineer — SKIP: passive rule modifier (extra build of same kind)
-- Card 218: Breeding Program — SKIP: places player tokens + passive rule modifier for conservation support

-- ── SPONSOR — ANIMAL SIZE / WAZA ──────────────────────────────────────────

-- Card 227: Waza Special Assignment — SKIP: mark card choice + reveal deck + ongoing triggers
-- Card 228: Waza Small Animal Program — SKIP: ongoing trigger with extra play + take from display
-- Card 229: Expert In Small Animals — SKIP: passive cost reduction
-- Card 230: Expert In Large Animals — SKIP: passive cost reduction
-- Card 263: Waza Large Animal Program — SKIP: passive condition ignore + free enclosure placement

-- ── SPONSOR — INCOME (tiered/conditional) ─────────────────────────────────

-- Card 231: Sponsorship: Primates — SKIP: "**Income:** Gain {Money-3}/{Money-6}/{Money-9} for 1/3/5 primate icons" — tiered conditional income, not a simple single-threshold MIN_ICON gain
-- Card 232: Sponsorship: Reptiles — SKIP: same pattern, tiered conditional
-- Card 233: Sponsorship: Vultures — SKIP: same pattern, tiered conditional
-- Card 234: Sponsorship: Lions — SKIP: same pattern, tiered conditional
-- Card 235: Sponsorship: Elephants — SKIP: same pattern, tiered conditional

-- ── SPONSOR — ONGOING per-icon triggers ───────────────────────────────────

-- Card 236: Primatologist — SKIP: ongoing trigger (each time primate icon played into any zoo)
-- Card 237: Herpetologist — SKIP: ongoing trigger (each time reptile icon played into any zoo)
-- Card 238: Ornithologist — SKIP: ongoing trigger (each time bird icon played into any zoo)
-- Card 239: Expert In Predators — SKIP: ongoing trigger (each time predator icon played into any zoo)
-- Card 240: Expert In Herbivores — SKIP: ongoing trigger (each time herbivore icon played into any zoo)

-- ── SPONSOR — HYDROLOGIST / GEOLOGIST / MAP-BASED ─────────────────────────

-- Card 241: Hydrologist — SKIP: per-space conditional (next to water) + all-connected conservation gain
-- Card 242: Geologist — SKIP: per-space conditional (next to rock) + all-connected conservation gain
-- Card 243: Meerkat Den — SKIP: ongoing trigger + placement requirement
-- Card 244: Penguin Pool — SKIP: ongoing trigger + placement requirement
-- Card 245: Aquarium — SKIP: ongoing trigger + placement requirement
-- Card 246: Cable Car — SKIP: ongoing trigger + placement requirement
-- Card 247: Baboon Rock — SKIP: ongoing trigger + placement requirement
-- Card 248: Rhesus Monkey Park — SKIP: ongoing trigger (each time primate icon played)
-- Card 249: Barred Owl Hut — SKIP: ongoing trigger (each time bird icon played) + draw/keep
-- Card 250: Sea Turtle Tank — SKIP: ongoing trigger (each time reptile icon played) + sell from hand
-- Card 251: Polar Bear Exhibit — SKIP: ongoing trigger (each bear icon in any zoo) — inter-zoo trigger
-- Card 252: Spotted Hyena Compound — SKIP: ongoing trigger (each time predator icon played) + reveal/keep
-- Card 253: Okapi Stable — SKIP: places tokens on card + ongoing trigger + play sponsor for X

-- ── SPONSOR — MAP PLACEMENT / SCORING ─────────────────────────────────────

-- Card 254: Zoo School — SKIP: placement requirement (2 border spaces) + take card from deck/range
-- Card 255: Adventure Playground — SKIP: placement requirement (next to rock space), appeal_value from card stat not ability
-- Card 256: Water Playground — SKIP: placement requirement (next to water space)
-- Card 257: Side Entrance — SKIP: complex placement + **Income:** per-building money + conditional appeal
-- Card 258: Native Seabirds — SKIP: "Gain {Appeal-1} for each connected water space" — zone-topology scoring
-- Card 259: Native Lizards — SKIP: "Gain {Appeal-1} for each connected rock space" — zone-topology scoring
-- Card 260: Native Farm Animals — SKIP: "Gain {Appeal-1} for each connected border space without buildings" — zone-topology scoring
-- Card 261: Guided School Tours — SKIP: "Gain {ConservationPoint-1} for 5 different animal category icons" — diverse-icon conditional
-- Card 262: Explorer — SKIP: ongoing trigger (each new icon type gained) + conditional money
-- Card 264: Free-Range New World Monkeys — SKIP: "Gain {Appeal-1} for each connected space with placement bonus" — zone-topology scoring

-- ── SPONSOR — PROMO ───────────────────────────────────────────────────────

-- Card 281: Arcade (PROMO) — SKIP: "**Income:** Gain {Money-1} per {Appeal-10}" — income per-10-appeal increments, complex scoring metric; also has inter-zoo appeal comparison
-- Card 282: Promotion Team (PROMO) — SKIP: find a sponsor card + ongoing trigger (each time a person sponsor is played into any zoo)

-- ── CONSERVATION cards (101-132) ──────────────────────────────────────────
-- All conservation project cards describe requirements for supporting the
-- project (continent icons, animal category icons, releases, etc.). These are
-- not ON_PLAY game effects — they define project eligibility criteria.
-- All are SKIP.
--
-- Card 101: Species Diversity — SKIP: conservation project requirement card
-- Card 102: Habitat Diversity — SKIP: conservation project requirement card
-- Card 103: Africa — SKIP: conservation project requirement card
-- Card 104: Americas — SKIP: conservation project requirement card
-- Card 105: Australia — SKIP: conservation project requirement card
-- Card 106: Asia — SKIP: conservation project requirement card
-- Card 107: Europe — SKIP: conservation project requirement card
-- Card 108: Primates — SKIP: conservation project requirement card
-- Card 109: Reptiles — SKIP: conservation project requirement card
-- Card 110: Predators — SKIP: conservation project requirement card
-- Card 111: Herbivores — SKIP: conservation project requirement card
-- Card 112: Birds — SKIP: conservation project requirement card
-- Card 113: Bavarian Forest National Park — SKIP: release-into-wild project card
-- Card 114: Yosemite National Park — SKIP: release-into-wild project card
-- Card 115: Angthong National Park — SKIP: release-into-wild project card
-- Card 116: Serengeti National Park — SKIP: release-into-wild project card
-- Card 117: Blue Mountains National Park — SKIP: release-into-wild project card
-- Card 118: Savanna — SKIP: release-into-wild project card
-- Card 119: Low Mountain Range — SKIP: release-into-wild project card
-- Card 120: Bamboo Forest — SKIP: release-into-wild project card
-- Card 121: Sea Cave — SKIP: release-into-wild project card
-- Card 122: Jungle — SKIP: release-into-wild project card
-- Card 123: Bird Breeding Program — SKIP: conservation project requirement card
-- Card 124: Predator Breeding Program — SKIP: conservation project requirement card
-- Card 125: Reptile Breeding Program — SKIP: conservation project requirement card
-- Card 126: Herbivore Breeding Program — SKIP: conservation project requirement card
-- Card 127: Primate Breeding Program — SKIP: conservation project requirement card
-- Card 128: Aquatic — SKIP: conservation project requirement card
-- Card 129: Geological — SKIP: conservation project requirement card
-- Card 130: Small Animals — SKIP: conservation project requirement card
-- Card 131: Large Animals — SKIP: conservation project requirement card
-- Card 132: Research — SKIP: conservation project requirement card

-- ── FINAL_SCORING cards (001-011) ─────────────────────────────────────────
-- All final scoring cards describe end-game conservation point formulas.
-- These are not ON_PLAY effects.
-- All are SKIP.
--
-- Card 001: Large Animal Zoo — SKIP: end-game scoring card
-- Card 002: Small Animal Zoo — SKIP: end-game scoring card
-- Card 003: Research Zoo — SKIP: end-game scoring card
-- Card 004: Architectural Zoo — SKIP: end-game scoring card
-- Card 005: Conservation Zoo — SKIP: end-game scoring card
-- Card 006: Naturalists Zoo — SKIP: end-game scoring card
-- Card 007: Favorite Zoo — SKIP: end-game scoring card
-- Card 008: Sponsored Zoo — SKIP: end-game scoring card
-- Card 009: Diverse Species Zoo — SKIP: end-game scoring card (null abilityText)
-- Card 010: Climbing Park — SKIP: end-game scoring card
-- Card 011: Aquatic Park — SKIP: end-game scoring card
