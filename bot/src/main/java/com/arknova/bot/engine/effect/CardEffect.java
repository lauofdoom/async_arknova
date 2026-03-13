package com.arknova.bot.engine.effect;

/**
 * A single machine-executable effect parsed from a card's {@code effect_code} JSON.
 *
 * <p>Example JSON entry inside the {@code "abilities"} array:
 *
 * <pre>
 * { "trigger": "ON_PLAY", "type": "GAIN", "resource": "MONEY", "amount": 3 }
 * { "trigger": "ON_PLAY", "type": "CONDITIONAL_GAIN",
 *   "condition": { "type": "MIN_ICON", "icon": "PREDATOR", "count": 2 },
 *   "resource": "APPEAL", "amount": 1 }
 * </pre>
 *
 * <p>Fields:
 *
 * <ul>
 *   <li>{@code trigger} — when the effect fires; currently only {@code "ON_PLAY"} is executed.
 *   <li>{@code type} — {@code "GAIN"} or {@code "CONDITIONAL_GAIN"}.
 *   <li>{@code resource} — one of {@code MONEY}, {@code APPEAL}, {@code CONSERVATION},
 *       {@code REPUTATION}, {@code X_TOKENS}.
 *   <li>{@code amount} — how many units to add.
 *   <li>{@code condition} — only present for {@code "CONDITIONAL_GAIN"}; may be {@code null}.
 * </ul>
 */
public record CardEffect(
    String trigger, String type, String resource, int amount, CardEffectCondition condition) {}
