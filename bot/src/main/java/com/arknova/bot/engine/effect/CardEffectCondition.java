package com.arknova.bot.engine.effect;

/**
 * Condition attached to a {@link CardEffect}.
 *
 * <p>Supported types:
 *
 * <ul>
 *   <li>{@code "MIN_ICON"} — passes when the player has at least {@code count} icons of type {@code
 *       icon}. Used with {@code "CONDITIONAL_GAIN"}.
 *   <li>{@code "ICON"} — specifies which icon to count for a {@code "GAIN_PER_ICON"} effect. {@code
 *       max} optionally caps the multiplier (0 = no cap).
 * </ul>
 *
 * <p>Example JSON:
 *
 * <pre>
 * { "type": "MIN_ICON", "icon": "PREDATOR", "count": 2 }
 * { "type": "ICON", "icon": "PREDATOR", "max": 0 }
 * { "type": "ICON", "icon": "PET", "max": 8 }
 * </pre>
 */
public record CardEffectCondition(String type, String icon, int count, int max) {}
