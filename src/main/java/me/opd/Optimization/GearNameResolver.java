package me.opd.Optimization;

import me.opd.DataReading.*;
import me.opd.Storage.JsonIO;
import java.util.*;
import java.util.stream.Collectors;

/** Compile friendly names to the existing ID constraints without changing caller preferences. */
public final class GearNameResolver {
    private GearNameResolver() {}
    public record Resolved(OptimizationRequest request,List<String> notes) {}
    public static Resolved resolve(Catalog catalog,OptimizationRequest original) {
        if (original.lockedGear.isEmpty() && original.excludedGear.isEmpty()) return new Resolved(original,List.of());
        OptimizationRequest request=JsonIO.decode(JsonIO.encode(original),OptimizationRequest.class);
        List<String> notes=new ArrayList<>();
        for (GearSelector selector : request.excludedGear) {
            List<Gear> matches=catalog.gear.stream().filter(selector::matches).toList();
            if (matches.isEmpty()) throw noMatch("Excluded gear",selector,catalog,null);
            matches.forEach(g -> request.excludedIds.add(g.id));
            notes.add("Excluded gear name '"+selector.name+"': "+matches.size()+" matching catalog record(s)");
        }
        for (var entry : request.lockedGear.entrySet()) {
            GearSlot slot=entry.getKey(); GearSelector selector=entry.getValue();
            List<Gear> named=catalog.gear.stream().filter(g -> g.slot==slot && selector.matches(g))
                .sorted(Comparator.comparingInt((Gear g) -> g.level).thenComparing(g -> g.id)).toList();
            if (named.isEmpty()) throw noMatch("Locked "+slot,selector,catalog,slot);
            String existing=request.lockedItems.get(slot);
            if (existing!=null && named.stream().noneMatch(g -> existing.equals(g.id)))
                throw new IllegalArgumentException("Conflicting name and ID locks for "+slot+": "+selector.name+" / "+existing);
            List<Gear> eligible=named.stream().filter(g -> (existing==null || existing.equals(g.id))
                && g.eligible(request.wizard.school,request.wizard.level)
                && (g.complete || request.allowIncompleteItems)
                && !request.excludedIds.contains(g.id) && !request.excludedSources.contains(g.source) && !request.excludedCategories.contains(g.category)
                && (!request.wizard.ownedOnly || request.wizard.quantity(g.id)>0 && (g.pet==null || !g.pet.hypothetical))).toList();
            if (eligible.isEmpty()) throw new IllegalArgumentException("Locked "+slot+" '"+selector.name
                +"' has no eligible match. Check character level, school, exclusions, ownership and completeness. Catalog matches: "+describe(named));
            if (eligible.size()>1) throw new IllegalArgumentException("Ambiguous locked "+slot+" name '"+selector.name
                +"': "+eligible.size()+" eligible records. Specify an item level using {\"name\":\"...\",\"level\":180}. "
                +"If records still differ at that level, use the existing lockedItems ID field. Matches: "+describe(eligible));
            Gear chosen=eligible.getFirst(); request.lockedItems.put(slot,chosen.id);
            notes.add("Locked "+slot+" by name: "+chosen.name+" (level "+chosen.level+") ["+chosen.id+"]");
        }
        request.validate();
        return new Resolved(request,List.copyOf(notes));
    }
    private static IllegalArgumentException noMatch(String label,GearSelector selector,Catalog catalog,GearSlot slot) {
        String query=GearSelector.normalize(selector.name);
        String suggestions=catalog.gear.stream().filter(g -> slot==null || g.slot==slot)
            .filter(g -> GearSelector.normalize(g.name).contains(query) || query.contains(GearSelector.normalize(g.name)))
            .map(g -> g.name+" ("+g.slot+", level "+g.level+")").distinct().sorted().limit(8).collect(Collectors.joining("; "));
        return new IllegalArgumentException(label+" name '"+selector.name+"' was not found in the supplied catalog. "
            +"Use an exact item name; check any level/slot qualifier and catalog filters. "
            +(suggestions.isEmpty()?"Use the find command to search names.":"Similar names: "+suggestions));
    }
    private static String describe(List<Gear> gear) {
        return gear.stream().limit(12).map(g -> g.name+" (level "+g.level+", schools="+g.schools+", source="+g.source+") ["+g.id+"]")
            .collect(Collectors.joining("; "))+(gear.size()>12?"; ...":"");
    }
}
