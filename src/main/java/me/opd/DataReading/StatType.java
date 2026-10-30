package me.opd.DataReading;
/** Percentage values use percentage points; rating units never imply percentages. */
public enum StatType {
    DAMAGE(true), RESISTANCE(true), ACCURACY(true), PIERCE(true), CRITICAL_RATING(true),
    BLOCK_RATING(true), PIP_CONVERSION_RATING(true), FLAT_DAMAGE(true), FLAT_RESISTANCE(true),
    HEALTH(false), MANA(false), POWER_PIP_CHANCE(false), SHADOW_PIP_RATING(false),
    SHADOW_PIP_CHANCE(false), ARCHMASTERY_RATING(false), INCOMING_HEALING(false),
    OUTGOING_HEALING(false), ENERGY(false), STUN_RESISTANCE(false);
    public final boolean schoolSpecific;
    StatType(boolean schoolSpecific) { this.schoolSpecific = schoolSpecific; }
}
