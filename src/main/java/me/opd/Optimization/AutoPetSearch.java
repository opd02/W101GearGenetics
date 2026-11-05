package me.opd.Optimization;

import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import me.opd.PetHandeling.*;
import me.opd.Storage.JsonIO;
import java.util.*;

/** Per-run, immutable generated holders. Talent genes are encoded in each holder's stable ID. */
public final class AutoPetSearch {
    private record Generated(Gear gear,List<List<Jewel>> sockets,List<Set<String>> socketIds) {}
    private final Gear template;
    private final List<PetTrait> pool,locked,unlocked;
    private final Set<String> poolIds,lockedIds;
    private final List<List<Jewel>> baseSockets;
    private final Map<Jewel,Set<String>> jewelTalents=new IdentityHashMap<>();
    private final Map<String,Generated> generated=new HashMap<>();
    private List<Gear> all;

    public AutoPetSearch(Gear template,List<PetTrait> pool,List<PetTrait> locked,
                         List<List<Jewel>> baseSockets,OptimizationRequest request) {
        this.template=template; this.baseSockets=baseSockets;
        Set<String> reserved=new HashSet<>();
        for (int i=0;i<baseSockets.size();i++) {
            for (Jewel jewel : baseSockets.get(i)) if (jewel!=null) jewelTalents.computeIfAbsent(jewel,Jewel::talentIds);
            if (request.lockedJewels.containsKey("PET:"+i)) reserved.addAll(jewelTalents.get(baseSockets.get(i).getFirst()));
        }
        poolIds=new HashSet<>(); pool.forEach(t -> poolIds.add(t.id));
        lockedIds=new HashSet<>();
        for (PetTrait talent : locked) {
            if (!poolIds.contains(talent.id)) throw new IllegalArgumentException("Locked pet talent '"+talent.name+"' is outside talentPool");
            if (reserved.contains(talent.id)) throw new IllegalArgumentException("Locked pet talent '"+talent.name+"' duplicates a locked PET jewel talent");
            lockedIds.add(talent.id);
        }
        this.locked=locked.stream().sorted(Comparator.comparing(t -> t.id)).toList();
        this.pool=pool.stream().filter(t -> !reserved.contains(t.id)).sorted(Comparator.comparing(t -> t.id)).toList();
        unlocked=this.pool.stream().filter(t -> !lockedIds.contains(t.id)).toList();
        if (this.pool.size()<5) throw new IllegalArgumentException("Fewer than five pet talents remain after reserving locked PET jewel talents");
    }
    /** Ignore the template's preset talents, retaining its body, attributes, cards and socket rules. */
    public static Gear emptyTemplate(Gear source) {
        if (source.pet==null || source.pet.age<6 || !source.pet.fullyTrained)
            throw new IllegalArgumentException("AUTO pet template needs a fullyTrained Mega/Ultra pet with all five attributes");
        Gear copy=JsonIO.MAPPER.convertValue(source,Gear.class);
        copy.pet.traits=new ArrayList<>(); copy.pet.hypothetical=true;
        return copy;
    }
    public Gear random(Random random) {
        List<PetTrait> options=new ArrayList<>(unlocked); Collections.shuffle(options,random);
        List<PetTrait> talents=new ArrayList<>(locked); talents.addAll(options.subList(0,5-locked.size()));
        return holder(talents);
    }
    public Gear mutate(Gear prior,Random random) {
        List<PetTrait> replaceable=prior.pet.traits.stream().filter(t -> !lockedIds.contains(t.id)).toList();
        Set<String> present=ids(prior.pet.traits);
        List<PetTrait> options=unlocked.stream().filter(t -> !present.contains(t.id)).toList();
        if (replaceable.isEmpty() || options.isEmpty()) return prior;
        PetTrait removed=replaceable.get(random.nextInt(replaceable.size()));
        List<PetTrait> talents=new ArrayList<>(prior.pet.traits); talents.remove(removed);
        talents.add(options.get(random.nextInt(options.size())));
        return holder(talents);
    }
    public Gear crossover(Gear first,Gear second,Random random) {
        Map<String,PetTrait> union=new TreeMap<>();
        first.pet.traits.forEach(t -> { if (!lockedIds.contains(t.id)) union.put(t.id,t); });
        second.pet.traits.forEach(t -> { if (!lockedIds.contains(t.id)) union.put(t.id,t); });
        List<PetTrait> options=new ArrayList<>(union.values()); Collections.shuffle(options,random);
        List<PetTrait> talents=new ArrayList<>(locked); talents.addAll(options.subList(0,5-locked.size()));
        return holder(talents);
    }
    private Gear holder(List<PetTrait> talents) {
        List<PetTrait> sorted=talents.stream().sorted(Comparator.comparing(t -> t.id)).toList();
        Set<String> chosen=ids(sorted);
        if (sorted.size()!=5 || chosen.size()!=5 || !poolIds.containsAll(chosen) || !chosen.containsAll(lockedIds))
            throw new IllegalArgumentException("Invalid automatic pet talents");
        StringBuilder key=new StringBuilder("auto-pet:"); append(key,template.id); sorted.forEach(t -> append(key,t.id));
        return generated.computeIfAbsent(key.toString(),id -> generate(id,sorted,chosen)).gear();
    }
    private Generated generate(String id,List<PetTrait> talents,Set<String> chosen) {
        Gear gear=new Gear(); gear.id=id; gear.name="Automatic pet: "+template.name; gear.slot=GearSlot.PET;
        gear.level=template.level; gear.schools=template.schools; gear.baseStats=template.baseStats;
        gear.sockets=template.sockets; gear.setIds=template.setIds; gear.itemCards=template.itemCards;
        gear.category=template.category; gear.source=template.source; gear.gameVersion=template.gameVersion;
        gear.importedAt=template.importedAt; gear.complete=template.complete;
        gear.extensions=new TreeMap<>(template.extensions); gear.extensions.remove("profile");
        gear.extensions.put("autoPetTemplateId",template.id);
        Pet pet=new Pet(); pet.name=gear.name; pet.hypothetical=true; pet.age=template.pet.age;
        pet.attributes.putAll(template.pet.attributes); pet.maximumAttributes.putAll(template.pet.maximumAttributes);
        pet.fullyTrained=template.pet.fullyTrained; pet.rounding=template.pet.rounding; pet.traits=talents; gear.pet=pet;
        pet.getTotalStats(); // Validate the combined modifiers/effects before admitting a holder.
        List<List<Jewel>> sockets=new ArrayList<>(); List<Set<String>> socketIds=new ArrayList<>();
        for (List<Jewel> base : baseSockets) {
            List<Jewel> options=new ArrayList<>(); Set<String> permitted=new HashSet<>();
            for (Jewel jewel : base) if (jewel==null || Collections.disjoint(chosen,jewelTalents.get(jewel))
                    && PetCalculator.canApply(pet,jewel)) {
                options.add(jewel); permitted.add(jewel==null ? null : jewel.id);
            }
            if (options.isEmpty()) throw new IllegalArgumentException("Automatic pet has no compatible choice for a locked socket");
            sockets.add(Collections.unmodifiableList(options)); socketIds.add(Collections.unmodifiableSet(permitted));
        }
        return new Generated(gear,List.copyOf(sockets),List.copyOf(socketIds));
    }
    public boolean contains(Gear gear) {
        Generated known=generated.get(gear.id);
        return known!=null && (known.gear()==gear || JsonIO.encode(known.gear()).equals(JsonIO.encode(gear)));
    }
    public List<List<Jewel>> sockets(Gear gear) {
        Generated known=generated.get(gear.id); return known==null ? null : known.sockets();
    }
    public boolean accepts(Gear gear,int socket,Jewel jewel) {
        return (jewel==null || jewel.id!=null) && generated.get(gear.id).socketIds().get(socket).contains(jewel==null ? null : jewel.id);
    }
    /** Only exact enumeration asks for all combinations; the GA constructs holders on demand. */
    public List<Gear> all() {
        if (all==null) {
            List<Gear> result=new ArrayList<>(); enumerate(new ArrayList<>(locked),0,result); all=List.copyOf(result);
        }
        return all;
    }
    private void enumerate(List<PetTrait> talents,int start,List<Gear> result) {
        if (talents.size()==5) { result.add(holder(talents)); return; }
        for (int i=start;i<=unlocked.size()-(5-talents.size());i++) {
            talents.add(unlocked.get(i)); enumerate(talents,i+1,result); talents.removeLast();
        }
    }
    private static Set<String> ids(List<PetTrait> talents) {
        Set<String> ids=new HashSet<>(); talents.forEach(t -> ids.add(t.id)); return ids;
    }
    private static void append(StringBuilder key,String id) { key.append(id.length()).append(':').append(id); }
}
