/**
 * TypeScript types mirroring the data shapes in Next-Ark-Nova-Cards.
 * Source: https://github.com/Ender-Wiggin2019/Next-Ark-Nova-Cards
 *
 * These types are used by the converter to parse the community repo's
 * data files. The property names match those in the source repo.
 */

// ── Source repo types ─────────────────────────────────────────────────────────

export type CardSource =
  | 'BASE'
  | 'MARINE_WORLD'
  | 'PROMO'
  | 'FAN_MADE'
  | 'ALTERNATIVE'
  | 'BEGINNER';

export type EffectType =
  | 'PASSIVE'
  | 'IMMEDIATE'
  | 'INCOME'
  | 'ENDGAME'
  | 'CONSERVATION';

/**
 * An ability/effect as represented in Next-Ark-Nova-Cards.
 * Note: effectDesc is a plain human-readable string, NOT a machine-executable DSL.
 */
export interface SourceEffect {
  effectType: EffectType;
  effectDesc: string;
  display?: boolean;
  fontSize?: string;
  start?: number;
  end?: number;
}

/** Tags that can appear on animal cards */
export type Tag =
  | 'AFRICA'
  | 'ASIA'
  | 'EUROPE'
  | 'AMERICAS'
  | 'AUSTRALIA'
  | 'BIRD'
  | 'PREDATOR'
  | 'REPTILE'
  | 'HERBIVORE'
  | 'PRIMATE'
  | 'PET'
  | 'BEAR'
  | 'SEA_ANIMAL'
  | 'PREHISTORIC'
  | 'SCIENCE'
  | 'WATER'
  | 'ROCK'
  | string; // allow unknown tags from future expansions

export type Requirement = 'ROCK' | 'WATER' | 'ELECTRICAL';

export interface AnimalCard {
  id: string;
  name: string;
  latinName?: string;
  endangeredCategory?: string;
  image?: string;
  directUseImage?: boolean;

  // Enclosure requirements
  size: number;           // minimum enclosure size
  rock?: number;
  water?: number;
  electrical?: number;
  requirements?: Requirement[];

  price: number;
  tags: Tag[];
  canBeInStandardEnclosure?: boolean;
  specialEnclosures?: string[];

  // Abilities
  abilities?: SourceEffect[];
  description?: SourceEffect;
  reefDwellerEffect?: SourceEffect[];
  soloEffect?: SourceEffect;
  wave?: boolean;

  // Scoring
  reputation?: number;
  appeal?: number;
  conservationPoint?: number;

  source: CardSource;
}

export interface SponsorCard {
  id: string;
  name: string;
  image?: string;
  directUseImage?: boolean;

  strength: number;       // card "size" / base cost
  rock?: number;
  water?: number;
  requirements?: Tag[];
  tags: Tag[];

  effects?: SourceEffect[];
  reputation?: number;
  appeal?: number;
  conservationPoint?: number;

  source: CardSource;
  type?: string;
}

export type ProjectCategory =
  | 'AFRICA'
  | 'ASIA'
  | 'EUROPE'
  | 'AMERICAS'
  | 'AUSTRALIA'
  | 'OCEAN'
  | string;

export interface ProjectSlot {
  tag?: Tag;
  count?: number;
  description?: string;
}

export interface ProjectCard {
  id: string;
  name: string;
  type: ProjectCategory;
  image?: string;
  directUseImage?: boolean;
  tag: Tag;
  slots: ProjectSlot[];
  placeBonuses?: SourceEffect[];
  description: SourceEffect;
  source: CardSource;
}

export interface EndGameCard {
  id: string;
  name: string;
  image?: string;
  directUseImage?: boolean;
  description: SourceEffect;
  source: CardSource;
}

// ── Our output schema ──────────────────────────────────────────────────────────

/**
 * The card definition JSON schema written to base_game.json.
 * This is what CardDatabaseLoader.java reads at startup.
 */
export interface OutputCard {
  /** Card ID — matches official card number (e.g. "401" for Lion) */
  id: string;
  name: string;
  /** ANIMAL | SPONSOR | CONSERVATION | FINAL_SCORING */
  card_type: 'ANIMAL' | 'SPONSOR' | 'CONSERVATION' | 'FINAL_SCORING';
  base_cost: number;
  /** Minimum enclosure size (animals only; null for sponsors/projects) */
  min_enclosure_size: number | null;
  tags: string[];
  requirements: string[];
  appeal_value: number;
  conservation_value: number;
  reputation_value: number;
  /**
   * Human-readable ability text joined from all effectDesc strings.
   * This is displayed to players when card is played or viewed.
   * Machine-executable effect_code is added separately in the DB.
   */
  ability_text: string | null;
  /** Always null in the JSON — effect_code is added to the DB by contributors */
  effect_code: null;
  image_url: string | null;
  source: CardSource;
  card_number: string;
}
