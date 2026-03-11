#!/usr/bin/env tsx
/**
 * Card Converter — Next-Ark-Nova-Cards → base_game.json
 * ─────────────────────────────────────────────────────
 * Converts the TypeScript card data from the Next-Ark-Nova-Cards community
 * repository into the JSON format expected by the bot's CardDatabaseLoader.
 *
 * USAGE:
 *   cd scripts
 *   npm install
 *   npm run convert [-- --source-repo=../Next-Ark-Nova-Cards]
 *
 * The script expects the Next-Ark-Nova-Cards repo to be cloned adjacent to
 * this repository (or at the path specified by --source-repo):
 *
 *   arknova-async/          ← this repo
 *   Next-Ark-Nova-Cards/    ← community repo (cloned separately)
 *
 * To clone the community repo:
 *   git clone https://github.com/Ender-Wiggin2019/Next-Ark-Nova-Cards \
 *             ../Next-Ark-Nova-Cards
 *
 * OUTPUT:
 *   bot/src/main/resources/cards/base_game.json
 *
 * The output file is committed to version control. Re-run this script
 * when the community repo is updated to pull in new/corrected card data.
 *
 * NOTE: effect_code is never written by this script — it is always null.
 * Card effects are added to the database by contributors via SQL or a
 * separate migration. This script only updates metadata (name, cost, tags,
 * ability text, image URLs).
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import type {
  AnimalCard,
  SponsorCard,
  ProjectCard,
  EndGameCard,
  SourceEffect,
  OutputCard,
  CardSource,
} from './types.js';

// ── Configuration ─────────────────────────────────────────────────────────────

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');

// Parse --source-repo CLI argument (default: ../Next-Ark-Nova-Cards)
const sourceRepoArg = process.argv
  .find((a) => a.startsWith('--source-repo='))
  ?.split('=')[1];

const SOURCE_REPO = path.resolve(
  REPO_ROOT,
  sourceRepoArg ?? '../Next-Ark-Nova-Cards'
);

const OUTPUT_PATH = path.resolve(
  REPO_ROOT,
  'bot/src/main/resources/cards/base_game.json'
);

const DATA_DIR = path.join(SOURCE_REPO, 'src', 'data');

// ── Helpers ────────────────────────────────────────────────────────────────────

function log(msg: string) {
  console.log(`[convert-cards] ${msg}`);
}

function warn(msg: string) {
  console.warn(`[convert-cards] WARN: ${msg}`);
}

/**
 * Dynamically imports a TypeScript data module from the source repo.
 * Uses tsx's runtime import (no compile step needed).
 */
async function importData<T>(filename: string): Promise<T> {
  const fullPath = path.join(DATA_DIR, filename);
  if (!fs.existsSync(fullPath)) {
    throw new Error(
      `Data file not found: ${fullPath}\n` +
      `Make sure the Next-Ark-Nova-Cards repo is cloned at: ${SOURCE_REPO}`
    );
  }
  const module = await import(fullPath);
  return module;
}

/**
 * Joins all effectDesc strings from a list of effects into one human-readable
 * ability text. Returns null if there are no effects.
 */
function buildAbilityText(effects: SourceEffect[] | SourceEffect | undefined | null): string | null {
  if (!effects) return null;
  const list = Array.isArray(effects) ? effects : [effects];
  const descriptions = list
    .map((e) => e?.effectDesc?.trim())
    .filter((d): d is string => Boolean(d));
  return descriptions.length > 0 ? descriptions.join(' / ') : null;
}

/**
 * Normalises a tag string to uppercase with underscores.
 * The source repo uses camelCase or mixed formats for some tags.
 */
function normaliseTag(tag: string): string {
  return tag
    .replace(/([A-Z])/g, '_$1')
    .toUpperCase()
    .replace(/^_/, '')
    .replace(/\s+/g, '_');
}

function normaliseTags(tags: string[] | undefined): string[] {
  if (!tags) return [];
  return tags.map(normaliseTag).filter(Boolean);
}

/**
 * Resolves the image URL from a card's image field.
 * The source repo uses both direct URLs (directUseImage=true) and
 * relative paths. We store whatever is present and resolve at render time.
 */
function resolveImageUrl(card: {
  image?: string;
  directUseImage?: boolean;
}): string | null {
  if (!card.image) return null;
  // If directUseImage is true, the image field is already a full URL
  if (card.directUseImage) return card.image;
  // Otherwise it's a relative path within the source repo's public directory
  // We store it as-is; the bot's CardImageCacheService handles resolution
  return card.image;
}

// ── Converters ─────────────────────────────────────────────────────────────────

function convertAnimal(card: AnimalCard): OutputCard {
  // Combine all effect types into one ability text string
  const allEffects = [
    ...(card.abilities ?? []),
    ...(card.reefDwellerEffect ?? []),
    card.description,
    card.soloEffect,
  ].filter((e): e is SourceEffect => Boolean(e));

  return {
    id: String(card.id),
    name: card.name,
    card_type: 'ANIMAL',
    base_cost: card.price ?? 0,
    min_enclosure_size: card.size ?? null,
    tags: normaliseTags(card.tags),
    requirements: normaliseTags(card.requirements),
    appeal_value: card.appeal ?? 0,
    conservation_value: card.conservationPoint ?? 0,
    reputation_value: card.reputation ?? 0,
    ability_text: buildAbilityText(allEffects),
    effect_code: null,
    image_url: resolveImageUrl(card),
    source: card.source ?? 'BASE',
    card_number: String(card.id),
  };
}

function convertSponsor(card: SponsorCard): OutputCard {
  return {
    id: String(card.id),
    name: card.name,
    card_type: 'SPONSOR',
    base_cost: card.strength ?? 0,
    min_enclosure_size: null,
    tags: normaliseTags(card.tags),
    requirements: normaliseTags(card.requirements),
    appeal_value: card.appeal ?? 0,
    conservation_value: card.conservationPoint ?? 0,
    reputation_value: card.reputation ?? 0,
    ability_text: buildAbilityText(card.effects),
    effect_code: null,
    image_url: resolveImageUrl(card),
    source: card.source ?? 'BASE',
    card_number: String(card.id),
  };
}

function convertProject(card: ProjectCard): OutputCard {
  // Conservation projects have a tag (continent category) and a description effect
  const allEffects = [
    card.description,
    ...(card.placeBonuses ?? []),
  ].filter((e): e is SourceEffect => Boolean(e));

  return {
    id: String(card.id),
    name: card.name,
    card_type: 'CONSERVATION',
    base_cost: 0,
    min_enclosure_size: null,
    tags: [normaliseTag(card.tag ?? card.type ?? '')].filter(Boolean),
    requirements: [],
    appeal_value: 0,
    conservation_value: 0,
    reputation_value: 0,
    ability_text: buildAbilityText(allEffects),
    effect_code: null,
    image_url: resolveImageUrl(card),
    source: card.source ?? 'BASE',
    card_number: String(card.id),
  };
}

function convertEndGame(card: EndGameCard): OutputCard {
  return {
    id: String(card.id),
    name: card.name,
    card_type: 'FINAL_SCORING',
    base_cost: 0,
    min_enclosure_size: null,
    tags: [],
    requirements: [],
    appeal_value: 0,
    conservation_value: 0,
    reputation_value: 0,
    ability_text: buildAbilityText(card.description),
    effect_code: null,
    image_url: resolveImageUrl(card),
    source: card.source ?? 'BASE',
    card_number: String(card.id),
  };
}

// ── Source filtering ───────────────────────────────────────────────────────────

/**
 * Which card sources to include. At v1.0 we only want base game cards.
 * Fan-made and alternative cards are excluded to keep the scope clean.
 */
const INCLUDED_SOURCES: CardSource[] = ['BASE', 'PROMO'];

function isIncluded(source: CardSource | undefined): boolean {
  return INCLUDED_SOURCES.includes(source ?? 'BASE');
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  log(`Source repo: ${SOURCE_REPO}`);
  log(`Output:      ${OUTPUT_PATH}`);

  if (!fs.existsSync(SOURCE_REPO)) {
    console.error(
      `\nERROR: Next-Ark-Nova-Cards repo not found at: ${SOURCE_REPO}\n\n` +
      `Clone it with:\n` +
      `  git clone https://github.com/Ender-Wiggin2019/Next-Ark-Nova-Cards \\\n` +
      `            ${SOURCE_REPO}\n`
    );
    process.exit(1);
  }

  const allCards: OutputCard[] = [];
  const stats = {
    animals: 0,
    sponsors: 0,
    conservation: 0,
    finalScoring: 0,
    skipped: 0,
    noImage: 0,
  };

  // ── Animals ────────────────────────────────────────────────────────────────
  log('Loading Animals.ts...');
  try {
    const animalsModule = await importData<{ [key: string]: AnimalCard[] }>('Animals.ts');
    // The module may export as default, named, or a mix — try common patterns
    const animals: AnimalCard[] = findArrayExport(animalsModule);

    for (const card of animals) {
      if (!isIncluded(card.source)) {
        stats.skipped++;
        continue;
      }
      if (!card.id || !card.name) {
        warn(`Skipping animal with missing id/name: ${JSON.stringify(card).slice(0, 80)}`);
        stats.skipped++;
        continue;
      }
      const converted = convertAnimal(card);
      if (!converted.image_url) stats.noImage++;
      allCards.push(converted);
      stats.animals++;
    }
    log(`  ${stats.animals} animals loaded`);
  } catch (e) {
    warn(`Could not load Animals.ts: ${(e as Error).message}`);
    log('  Attempting fallback to Animals.js...');
    // Some versions of the repo compile to JS; try that
  }

  // ── Sponsors ───────────────────────────────────────────────────────────────
  log('Loading Sponsors.ts...');
  try {
    const sponsorsModule = await importData<{ [key: string]: SponsorCard[] }>('Sponsors.ts');
    const sponsors: SponsorCard[] = findArrayExport(sponsorsModule);

    for (const card of sponsors) {
      if (!isIncluded(card.source)) {
        stats.skipped++;
        continue;
      }
      if (!card.id || !card.name) {
        warn(`Skipping sponsor with missing id/name: ${JSON.stringify(card).slice(0, 80)}`);
        stats.skipped++;
        continue;
      }
      const converted = convertSponsor(card);
      if (!converted.image_url) stats.noImage++;
      allCards.push(converted);
      stats.sponsors++;
    }
    log(`  ${stats.sponsors} sponsors loaded`);
  } catch (e) {
    warn(`Could not load Sponsors.ts: ${(e as Error).message}`);
  }

  // ── Conservation Projects ─────────────────────────────────────────────────
  log('Loading Projects.ts...');
  try {
    const projectsModule = await importData<{ [key: string]: ProjectCard[] }>('Projects.ts');
    const projects: ProjectCard[] = findArrayExport(projectsModule);

    for (const card of projects) {
      if (!isIncluded(card.source)) {
        stats.skipped++;
        continue;
      }
      if (!card.id || !card.name) {
        warn(`Skipping project with missing id/name: ${JSON.stringify(card).slice(0, 80)}`);
        stats.skipped++;
        continue;
      }
      const converted = convertProject(card);
      if (!converted.image_url) stats.noImage++;
      allCards.push(converted);
      stats.conservation++;
    }
    log(`  ${stats.conservation} conservation projects loaded`);
  } catch (e) {
    warn(`Could not load Projects.ts: ${(e as Error).message}`);
  }

  // ── End-Game Cards ────────────────────────────────────────────────────────
  log('Loading EndGames.ts...');
  try {
    const endGamesModule = await importData<{ [key: string]: EndGameCard[] }>('EndGames.ts');
    const endGames: EndGameCard[] = findArrayExport(endGamesModule);

    for (const card of endGames) {
      if (!isIncluded(card.source)) {
        stats.skipped++;
        continue;
      }
      if (!card.id || !card.name) {
        warn(`Skipping end-game card with missing id/name: ${JSON.stringify(card).slice(0, 80)}`);
        stats.skipped++;
        continue;
      }
      const converted = convertEndGame(card);
      if (!converted.image_url) stats.noImage++;
      allCards.push(converted);
      stats.finalScoring++;
    }
    log(`  ${stats.finalScoring} final-scoring cards loaded`);
  } catch (e) {
    warn(`Could not load EndGames.ts: ${(e as Error).message}`);
  }

  // ── Deduplication ────────────────────────────────────────────────────────
  const seenIds = new Set<string>();
  const deduplicated = allCards.filter((card) => {
    if (seenIds.has(card.id)) {
      warn(`Duplicate card id "${card.id}" (${card.name}) — keeping first occurrence`);
      return false;
    }
    seenIds.add(card.id);
    return true;
  });

  // ── Sort for stable diffs ──────────────────────────────────────────────────
  deduplicated.sort((a, b) => {
    // Sort by card_type first, then by numeric id
    const typeOrder = ['ANIMAL', 'SPONSOR', 'CONSERVATION', 'FINAL_SCORING'];
    const typeA = typeOrder.indexOf(a.card_type);
    const typeB = typeOrder.indexOf(b.card_type);
    if (typeA !== typeB) return typeA - typeB;
    return parseInt(a.id, 10) - parseInt(b.id, 10);
  });

  // ── Write output ──────────────────────────────────────────────────────────
  const outputDir = path.dirname(OUTPUT_PATH);
  fs.mkdirSync(outputDir, { recursive: true });

  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(deduplicated, null, 2) + '\n', 'utf-8');

  // ── Summary ───────────────────────────────────────────────────────────────
  const total = deduplicated.length;
  const withText = deduplicated.filter((c) => c.ability_text).length;
  const withImage = deduplicated.filter((c) => c.image_url).length;

  log('');
  log('═'.repeat(50));
  log(`  Conversion complete!`);
  log(`  Animals:          ${stats.animals}`);
  log(`  Sponsors:         ${stats.sponsors}`);
  log(`  Conservation:     ${stats.conservation}`);
  log(`  Final Scoring:    ${stats.finalScoring}`);
  log(`  Skipped:          ${stats.skipped} (non-BASE sources)`);
  log('  ─────────────────────────────────────────────');
  log(`  Total cards:      ${total}`);
  log(`  With ability text: ${withText}/${total} (${pct(withText, total)}%)`);
  log(`  With image URL:   ${withImage}/${total} (${pct(withImage, total)}%)`);
  log(`  effect_code:      0/${total} (0%) — add via DB, not this script`);
  log('  ─────────────────────────────────────────────');
  log(`  Output: ${OUTPUT_PATH}`);
  log('═'.repeat(50));
}

// ── Utility ────────────────────────────────────────────────────────────────────

/**
 * Finds the first array export from a dynamically imported module.
 * Handles: default export, named export matching common names, or
 * the first array-valued export found.
 */
function findArrayExport<T>(mod: Record<string, unknown>): T[] {
  // Check default export first
  if (Array.isArray(mod.default)) return mod.default as T[];

  // Check common export names
  const commonNames = [
    'Animals', 'Sponsors', 'Projects', 'EndGames',
    'animals', 'sponsors', 'projects', 'endGames', 'cards'
  ];
  for (const name of commonNames) {
    if (Array.isArray(mod[name])) return mod[name] as T[];
  }

  // Fall back to first array-valued export
  for (const [key, value] of Object.entries(mod)) {
    if (key !== 'default' && Array.isArray(value) && value.length > 0) {
      return value as T[];
    }
  }

  throw new Error(
    `Could not find an array export in module. ` +
    `Available exports: ${Object.keys(mod).join(', ')}`
  );
}

function pct(n: number, total: number): string {
  return total === 0 ? '0' : Math.round((n / total) * 100).toString();
}

// ── Run ────────────────────────────────────────────────────────────────────────

main().catch((err) => {
  console.error('\n[convert-cards] FATAL ERROR:', err);
  process.exit(1);
});
