# Arknova-Async: Project Status & Roadmap to Full Game

> Last updated: 2026-03-13

## Context
Async Ark Nova board game Discord bot (Spring Boot 4.0.3 + JDA + PostgreSQL). Players take turns via slash commands in a Discord thread. The goal is a fully playable async game with all rules enforced and a clear game end/scoring flow.

---

## What's Done ✅

### Infrastructure
- Spring Boot 4.0.3 setup, Flyway migrations (V1 schema + V2 pending_discard + V3 break_track), Docker Compose dev/prod
- JPA models: Game, PlayerState, PlayerCard, CardDefinition, SharedBoardState, ActionLog, StateSnapshot
- 238 cards loaded (129 animals, 66 sponsors, 32 conservation, 11 final_scoring)
- PostgreSQL full-text search index on cards; icon count tracking
- DiscordLogger posting events to dedicated log channel

### Game Lifecycle
- `/arknova create` → `/arknova join` → `/arknova start` (dealing 4-card starting hands)
- `/arknova endturn` — advance to next player, increment turn on wrap
- `/arknova endgame` — end game, posts full VP breakdown per player
- Win condition detection (appeal + conservation ≥ 113 → FINAL_SCORING → ENDED)

### All 5 Action Cards (core mechanics complete)
| Card | Status | Notes |
|------|--------|-------|
| CARDS | ✅ | Draw/break/snap; pending-discard 2-phase flow; SNAP = manual |
| BUILD | ✅ | Single/multi-build, terrain costs, action card upgrades |
| ANIMALS | ✅ | Placement validation, cost, appeal/conservation, icon tracking |
| ASSOCIATION | ✅ | Worker tracking, partner zoos (strength req=3), universities (strength req=4), conservation projects, donations |
| SPONSOR | ✅ | Play/break, level caps, reputation-gated display access |

### Slash Commands
`create`, `join`, `start`, `endturn`, `endgame`, `animals`, `build`, `cards`, `association`, `sponsor`, `discard`, `status`, `hand`, `display`, `board`, `ping`, `adjust`, `draw`, `projects`, `score`

### Scoring & Tracks
- `ScoringTables.java` — partner zoo VP (3/slot), university VP (4/slot), X token formula (xTokens × breakTrack)
- `ScoreCommand` — `/arknova score track:[appeal|conservation|reputation|break|xtokens] value:[25|+5|-3]`
- `EndGameCommand` — full VP breakdown: appeal + conservation + partner zoo bonuses + university bonuses + X token scoring + FINAL_SCORING card list for manual resolution
- `breakTrack` field on PlayerState (V3 migration)

### Recently Fixed
- `DeckService.getHand/getDisplay` — `@Transactional(readOnly=true)` + lazy init fix
- `AssociationActionHandler` — removed money cost from PARTNER_ZOO and UNIVERSITY
- Starting money: seat 0=25, seat 1=26, seat 2=27, seat 3=28 (correctly implemented)

---

## What's Missing for Full Game 🔴

### Priority 1 — Required for Complete Playable Game

#### 1. ~~Final Scoring VP Calculation~~ ✅ DONE
`EndGameCommand` now shows full breakdown: appeal + conservation + partner zoo bonuses + university bonuses + X token scoring + FINAL_SCORING cards listed for manual resolution.

#### 2. ~~Break Track~~ ✅ DONE
`breakTrack` field added to `PlayerState`; V3 migration applied; accessible via `/arknova score track:break`.

#### 3. Break Pile / SNAP Action
Currently flagged `requiresManualResolution`. Phase 2 implementation:
- SharedBoardState needs a `breakPile` (JSON list of card IDs per player)
- SNAP = take 1 card from your break pile into hand
- Needs `/arknova snap card_id:<id>` or integrate into CARDS action multi-step flow
- CARDS break sub-action should increment `breakTrack` automatically

#### 4. X Tokens (field exists, never set automatically)
`PlayerState.xTokens` is accessible via `/arknova score track:xtokens` but no action handler increments it automatically. Need to wire X token gain to specific card effects / break actions.

### Priority 2 — Rules Completeness

#### 5. Map Selection at Game Start
`playerState.mapId` is hardcoded to "MAP_1". Need:
- Map definitions (which enclosures exist at start, size/position, terrain)
- Map selection option in `/arknova start` or `/arknova create`
- Different starting layouts per map affect BUILD action

#### 6. Reputation Track Impact
Reputation controls which display slots a player can access (slot ≤ reputation). Some sponsor cards should grant +1 rep automatically via `effectCode`. Currently all manual.

### Priority 3 — Progressive Card Effect Automation

#### 7. effectCode Execution Engine
Currently: 0 of 238 cards have `effect_code`. All card abilities are manual resolution.
Schema is designed (trigger/type/resource/amount/condition). Need:
- Effect executor in GameEngineImpl (parse effectCode JSON, apply GAIN/CONDITIONAL_GAIN/etc.)
- Start with high-frequency simple effects: `GAIN MONEY`, `GAIN APPEAL`, `GAIN CONSERVATION`, `GAIN REPUTATION`
- Then conditional effects: `MIN_ICON` conditions
- Populate `effect_code` for the most-played cards first

#### 8. Undo (Phase 3, low priority)
`state_snapshots` table exists (max 3 per game, gzip compressed). GameEngineImpl has `// TODO Phase 3: implement snapshot-based undo`. Not blocking gameplay.

---

### Priority 4 — Discord Channel Architecture

Currently the bot operates entirely within a single pre-existing channel/thread. No channels are created or managed automatically. The new design gives each game a **dedicated category** with structured channels.

#### 9. Automatic Game Channel Setup / Teardown

**On `/arknova start`** — bot creates a Discord category and channels:

```
📁 Ark Nova — Game #<short-id>    (Category, visible only to players + bot)
  📢 #board                        (public to all game players — shared board state PNG)
  📋 #game-log                     (existing thread OR new channel for action log)
  🔒 #<player1-name>-private       (only player1 + bot can see)
  🔒 #<player2-name>-private       (only player2 + bot can see)
  ...
```

**Private channel contents** (pinned/refreshed):
- Player's current hand (card list with names, IDs, types, costs)
- Current resources: money, workers, appeal, conservation, reputation, break, X tokens
- Board state image (ZooBoardRenderer PNG)
- Final scoring cards (when game enters FINAL_SCORING phase)
- Action history summary (last N actions by this player)

**On `/arknova endgame`** — bot:
1. Posts final score embed in each private channel + board channel
2. Removes permission overwrites (channels become visible to all or are archived)
3. Does NOT delete channels (preserves game history for review)

**Permission model:**
- Category: deny `@everyone` VIEW_CHANNEL
- `#board`: allow all player Discord IDs + bot
- `#<player>-private`: allow only that player's Discord ID + bot role
- JDA permission overwrites: `PermissionOverride` on each channel per member

**Data model changes needed:**
- `Game`: add `categoryId VARCHAR(20)`, `boardChannelId VARCHAR(20)`
- `PlayerState`: add `privateChannelId VARCHAR(20)`
- Flyway migration: `V4__game_channels.sql`

**New service: `DiscordChannelService.java`** (`discord/` package):
```java
// Creates full channel structure, stores IDs back in Game + PlayerState
void setupGameChannels(Game game, List<PlayerState> players, Guild guild);

// Removes permission overwrites (archive on game end)
void archiveGameChannels(Game game, List<PlayerState> players, Guild guild);

// Posts/refreshes private channel content for one player
void refreshPrivateChannel(Game game, PlayerState player);

// Posts board image to board channel
void postBoardUpdate(Game game, BufferedImage boardImage);
```

**JDA access pattern:**
```java
Guild guild = jda.getGuildById(game.getGuildId());
Category category = guild.createCategory("Ark Nova — Game #" + shortId).complete();
TextChannel boardChannel = guild.createTextChannel("board")
    .setParent(category)
    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
    .complete();
```

#### 10. `/arknova refresh` Command + Dual-Delivery

**`/arknova refresh`** — refreshes the calling player's private channel with current state:
1. Resources embed (money, workers, all tracks)
2. Hand embed (all cards currently held)
3. Board PNG (ZooBoardRenderer)
4. Final scoring cards (if in FINAL_SCORING phase)

**Dual delivery pattern** — existing ephemeral commands (`/arknova hand`, `/arknova status`, `/arknova board`) keep their ephemeral reply in the game thread AND also post to the player's private channel:
- Ephemeral reply: immediate inline response (quick reference during a turn)
- Private channel post: persistent record (refer back between sessions, doesn't disappear)

**Files to create/modify:**
| File | Action |
|------|--------|
| `discord/DiscordChannelService.java` | NEW — channel lifecycle management |
| `discord/command/RefreshCommand.java` | NEW — `/arknova refresh` |
| `discord/command/StartCommand.java` | UPDATE — call `setupGameChannels()` after start |
| `discord/command/EndGameCommand.java` | UPDATE — call `archiveGameChannels()` on end |
| `model/Game.java` | ADD `categoryId`, `boardChannelId` fields |
| `model/PlayerState.java` | ADD `privateChannelId` field |
| `db/migration/V4__game_channels.sql` | NEW — add channel ID columns |

---

### Priority 5 — Visual Game State Rendering

#### 11. Track Score Overlay on Board Image
Currently `ZooBoardRenderer` shows track values as text in the info panel. Upgrade to render actual track progress bars or overlaid token positions. Can be done programmatically (no image assets needed) as a first step.

#### 12. Programmatic PNG Game State Board (Image Overlay System)

**Architecture** (follows TI4-async pattern, extends existing `ZooBoardRenderer`):

```
GameStateRenderer (new)
  ├── ImageCache (new)          — load/cache PNG assets from resources/images/
  ├── ZooBoardRenderer (exists) — renders zoo hex grid (900×800)
  └── TrackOverlayRenderer (new)— overlays token sprites on track backgrounds
```

**Full board composite layout** (single PNG, ~1400×1100 px):
```
┌─────────────────────────────────────────────┐
│  PLAYER BOARD (ZooBoardRenderer, 900×800)   │
│  [hex grid + enclosures + animals already   │
│   rendered; action cards strip already in   │
│   info panel]                               │
├─────────────────────────────────────────────┤
│  TRACK PANEL (below, 900×220)               │
│  [appeal_track.png + token overlay]         │
│  [conservation_track.png + token overlay]   │
│  [reputation_track.png + token overlay]     │
│  [break_track.png + token/X-token overlay]  │
├─────────────────────────────────────────────┤
│  SHARED BOARD PANEL (right side, 460×1020)  │
│  [display: 6 face-up card thumbnails]       │
│  [conservation projects list]               │
└─────────────────────────────────────────────┘
```

**Image Asset Directory:** `bot/src/main/resources/images/`
```
images/
  tracks/
    appeal_track.png          — full track background (0–113)
    conservation_track.png    — full track background (0–80)
    reputation_track.png      — track background (1–15)
    break_track.png           — track background (0–N)
  tokens/
    appeal_token.png          — player-colored disc
    conservation_token.png
    reputation_token.png
    break_token.png
    x_token.png
  cards/
    {CARD_ID}.png             — individual card art (e.g. ANIMAL_001.png)
    card_back.png             — face-down placeholder
  icons/
    {icon_name}.png           — animal icons (BIRD, REPTILE, etc.)
  boards/
    zoo_board_base.png        — optional: replace programmatic bg with real board art
```

**`ImageCache.java`** (new, `renderer/` package):
- Loads via `ImageIO.read(getClass().getResourceAsStream("/images/..."))`
- `ConcurrentHashMap` keyed by path string
- Returns placeholder blank image if asset missing (graceful degradation)

**Token position math:**
```
tokenX = trackMinX + (position / trackMax) * (trackMaxX - trackMinX)
```

**`TrackOverlayRenderer.java`** — composites token sprite onto track background at calculated position.

**`GameStateRenderer.java`** — assembles full composite: ZooBoardRenderer + tracks panel + shared board panel → single `BufferedImage` → PNG bytes.

**Discord posting:** Board image auto-posted to `#board` channel after each `endturn`. `BoardCommand` updated to post non-ephemeral to board channel.

**Graceful degradation:** Falls back to current programmatic rendering if image assets are absent.

---

## Immediate Next Steps

1. **Implement `DiscordChannelService`** — category + board + private channel creation on `/arknova start`
2. **Implement `/arknova refresh`** — posts hand + resources + board PNG to private channel
3. **Update `hand`, `status`, `board` commands** — dual delivery (ephemeral in thread + persistent in private channel)
4. **Wire `ZooBoardRenderer`** to auto-post to `#board` channel after `endturn`
5. **Break pile / SNAP action** — increment `breakTrack` in CARDS handler; add `/arknova snap`
6. **Image assets** — add track/token PNGs to `resources/images/` once art is sourced

---

## Verification Plan
After each change:
1. `docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build bot` on Unraid
2. Create test game: `/arknova create` → `/arknova join` (x2) → `/arknova start`
3. Verify hand dealt: `/arknova hand`
4. Exercise each fixed command in Discord
5. Check bot-log channel for event posts
