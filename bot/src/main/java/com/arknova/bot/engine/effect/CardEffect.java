package com.arknova.bot.engine.effect;

/**
 * A single machine-executable effect parsed from a card's {@code effect_code} JSON.
 *
 * <p>Example JSON entries inside the {@code "abilities"} array:
 *
 * <pre>
 * { "trigger": "ON_PLAY", "type": "GAIN", "resource": "MONEY", "amount": 3 }
 * { "trigger": "ON_PLAY", "type": "CONDITIONAL_GAIN",
 *   "condition": { "type": "MIN_ICON", "icon": "PREDATOR", "count": 2 },
 *   "resource": "APPEAL", "amount": 1 }
 * { "trigger": "ON_PLAY", "type": "GAIN_PER_ICON",
 *   "icon": "PREDATOR", "resource": "APPEAL", "amount": 1, "max": 8 }
 * </pre>
 *
 * <p>Fields:
 *
 * <ul>
 *   <li>{@code trigger} — when the effect fires; currently only {@code "ON_PLAY"} is executed.
 *   <li>{@code type} — {@code "GAIN"}, {@code "CONDITIONAL_GAIN"}, or {@code "GAIN_PER_ICON"}.
 *   <li>{@code resource} — one of {@code MONEY}, {@code APPEAL}, {@code CONSERVATION},
 *       {@code REPUTATION}, {@code X_TOKENS}.
 *   <li>{@code amount} — units per icon (for {@code GAIN_PER_ICON}) or flat amount (for others).
 *   <li>{@code icon} — icon type key for {@code GAIN_PER_ICON} (e.g. {@code "PREDATOR"}); null
 *       for other types.
 *   <li>{@code max} — maximum total gain for {@code GAIN_PER_ICON}; 0 means no cap.
 *   <li>{@code condition} — only present for {@code "CONDITIONAL_GAIN"}; may be {@code null}.
 * </ul>
 */
public record CardEffect(
    String trigger,
    String type,
    String resource,
    int amount,
    String icon,
    int max,
    CardEffectCondition condition) {}
