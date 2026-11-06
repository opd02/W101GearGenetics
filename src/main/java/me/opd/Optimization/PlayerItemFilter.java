package me.opd.Optimization;

import me.opd.DataReading.Gear;
import me.opd.JewelHandeling.Jewel;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative archive screening, separate from completeness and effect mapping.
 * Passing this filter does not verify retail availability. Source records stay immutable.
 */
public final class PlayerItemFilter {
    private PlayerItemFilter() {}
    private static final Pattern CAMEL_BOUNDARY=Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Pattern INTERNAL_MARKER=Pattern.compile(
        "(?:^|[^a-z0-9])(?:qa|test|debug|dev|developer|godmode|npc|polymorph|transform|transformation|stats?|mdeck|donotuse|do\\s+not\\s+use|monster\\s*items)(?:$|[^a-z0-9])",
        Pattern.CASE_INSENSITIVE);
    private static final Set<String> BLOCKED_METADATA=Set.of("qa-or-test","npc","npc-only","developer-only","test-only","non-player");

    public static String rejectionReason(Gear gear) {
        return rejectionReason(gear.name,gear.source,gear.category,gear.extensions);
    }
    public static String rejectionReason(Jewel jewel) {
        return rejectionReason(jewel.name,jewel.source,jewel.category,jewel.extensions);
    }
    private static String rejectionReason(String name,String source,String category,Map<String,Object> extensions) {
        // Manual/synthetic data and resolved hypothetical pet profiles have separate contracts.
        if (!"wizard101-wad".equals(source)) return null;
        if (BLOCKED_METADATA.contains(category.toLowerCase(Locale.ROOT))
                || BLOCKED_METADATA.contains(String.valueOf(extensions.get("availability")).toLowerCase(Locale.ROOT)))
            return "non-player-metadata";
        String identity=name+" "+extensions.getOrDefault("member","")+" "+extensions.getOrDefault("rawObjectName","");
        String words=CAMEL_BOUNDARY.matcher(identity).replaceAll(" ");
        if (INTERNAL_MARKER.matcher(words).find() || INTERNAL_MARKER.matcher(identity).find()) return "internal-item-marker";
        Object rawIssues=extensions.get("issues");
        Collection<?> issues=rawIssues instanceof Collection<?> values ? values : List.of();
        Object displayKey=extensions.get("displayNameKey");
        if (!(displayKey instanceof String key) || key.isBlank() || issues.contains("unresolved-display-name"))
            return "unresolved-display-name";
        for (Object value : issues) {
            String issue=String.valueOf(value);
            if (issue.equals("invalid-level-requirement") || issue.equals("conflicting-school-requirements")
                    || issue.startsWith("unsupported-requirement") || issue.startsWith("level-school-semantics-unverified:"))
                return "unresolved-eligibility";
        }
        return null;
    }
}
