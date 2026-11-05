package me.opd.Optimization;

import me.opd.DataReading.Catalog;
import me.opd.PetHandeling.PetTrait;
import java.util.*;
import java.util.stream.Collectors;

/** Friendly exact talent names, with IDs available to disambiguate catalog definitions. */
public final class PetTalentResolver {
    private PetTalentResolver() {}
    public static List<PetTrait> definitions(Catalog catalog,boolean standaloneOnly) {
        Map<String,PetTrait> definitions=new TreeMap<>();
        catalog.petTalents.forEach(t -> definitions.put(t.id,t));
        if (!standaloneOnly) {
            catalog.gear.stream().filter(g -> g.pet!=null).forEach(g -> g.pet.traits.forEach(t -> definitions.putIfAbsent(t.id,t)));
            catalog.jewels.forEach(j -> j.petTalents.forEach(t -> definitions.putIfAbsent(t.id,t)));
        }
        return List.copyOf(definitions.values());
    }
    public static List<PetTrait> resolve(List<String> selections,List<PetTrait> definitions,String field) {
        List<PetTrait> resolved=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for (String selection : selections) {
            String name=GearSelector.normalize(selection);
            List<PetTrait> matches=definitions.stream().filter(t -> t.id.equals(selection)
                || GearSelector.normalize(t.name).equals(name)).toList();
            if (matches.isEmpty()) throw new IllegalArgumentException(field+": unknown pet talent '"+selection
                +"'. Use a supported exact talent name or ID; AUTO requires catalog.petTalents (rebuild the pet catalog).");
            if (matches.size()>1) throw new IllegalArgumentException(field+": ambiguous pet talent '"+selection+"'; use an ID: "
                +matches.stream().map(t -> t.name+" ["+t.id+"]").collect(Collectors.joining(", ")));
            PetTrait talent=matches.getFirst();
            if (!seen.add(talent.id)) throw new IllegalArgumentException(field+": duplicate pet talent '"+selection+"'");
            resolved.add(talent);
        }
        return List.copyOf(resolved);
    }
}
