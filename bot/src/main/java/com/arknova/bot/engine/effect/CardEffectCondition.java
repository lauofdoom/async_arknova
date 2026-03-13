package com.arknova.bot.engine.effect;

/**
 * Condition attached to a {@link CardEffect}. Currently supports {@code "MIN_ICON"} which
 * checks that the player has at least {@code count} icons of the named {@code icon} type.
 *
 * <p>Example JSON:
 *
 * <pre>
 * { "type": "MIN_ICON", "icon": "PREDATOR", "count": 2 }
 * </pre>
 */
public record CardEffectCondition(String type, String icon, int count) {}
