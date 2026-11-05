package me.opd;

import me.opd.DataReading.*;
import me.opd.GA.GAConfig;
import me.opd.JewelHandeling.*;
import me.opd.Optimization.*;
import me.opd.PetHandeling.*;
import me.opd.Storage.*;
import me.opd.Utils.CopyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoPetSearchTest {
    @TempDir Path temp;
    Catalog catalog() {
        var fixture=new PetIntegrationTest();
        Gear gear=OptimizerTest.gear("body",GearSlot.PET);
        gear.name="Planning template"; gear.pet=fixture.pet(); gear.sockets.add(new Socket(JewelType.PET));
        Catalog catalog=OptimizerTest.catalog(gear);
        catalog.petTalents.addAll(gear.pet.traits);
        catalog.petTalents.add(fixture.mighty().petTalents.getFirst());
        catalog.petTalents.add(fixed("accuracy",StatType.ACCURACY,School.STORM,5));
        catalog.petTalents.add(fixed("pierce",StatType.PIERCE,School.DEATH,6));
        catalog.petTalents.add(fixed("health",StatType.HEALTH,School.UNIVERSAL,200));
        catalog.petTalents.add(fixed("unused",StatType.MANA,School.UNIVERSAL,1));
        catalog.jewels.add(fixture.mighty());
        Jewel health=OptimizerTest.jewel("health-star",JewelType.PET);
        health.statBonus.put(StatType.HEALTH,School.UNIVERSAL,50); catalog.jewels.add(health);
        return catalog;
    }
    PetTrait fixed(String name,StatType stat,School school,double value) {
        PetTrait talent=new PetTrait(); talent.id="talent:"+name; talent.name=name;
        talent.statBonus.put(stat,school,value); return talent;
    }
    OptimizationRequest request(Catalog catalog) {
        var request=OptimizerTest.request(GearSlot.PET);
        request.petSearch.mode=PetSearchConfig.Mode.AUTO;
        request.petSearch.template=new GearSelector("Planning template");
        request.petSearch.talentPool=catalog.petTalents.stream().map(t -> t.name).toList();
        request.targets.add(OptimizerTest.target(StatType.ACCURACY,School.STORM,StatTarget.Mode.MINIMUM,5,true));
        request.targets.add(OptimizerTest.target(StatType.PIERCE,School.DEATH,StatTarget.Mode.MINIMUM,5,true));
        request.targets.add(OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MAXIMIZE,0,false));
        request.targets.add(OptimizerTest.target(StatType.HEALTH,School.UNIVERSAL,StatTarget.Mode.MAXIMIZE,0,false));
        request.targets.getLast().scale=1000;
        return request;
    }
    Set<String> ids(Gear gear) { return new HashSet<>(gear.pet.traits.stream().map(t -> t.id).toList()); }

    @Test void discoversNewCombinationsWithSeparateMightyJewelAndPostScaling() {
        Catalog catalog=catalog(); var request=request(catalog);
        request.wizard.school=School.STORM; request.wizard.level=180; request.wizard.scaledLevel=170;
        String before=JsonIO.encode(catalog);
        var ga=new Optimizer().optimize(catalog,request);
        request.search.method=GAConfig.Method.EXACT;
        var exact=new Optimizer().optimize(catalog,request);
        var best=ga.solutions().getFirst();
        assertTrue(best.fitness().feasible());
        assertEquals(exact.solutions().getFirst().fitness(),best.fitness());
        assertEquals(Set.of("dealer","giver","talent:accuracy","talent:pierce","talent:health"),ids(best.equipment().gearSet.get(GearSlot.PET)));
        assertEquals("mighty-jewel",best.equipment().jewels.get(GearSlot.PET).getFirst().id);
        assertEquals(17.625,best.rawCharacterStats().effective(StatType.DAMAGE,School.DEATH),1e-12);
        assertEquals(17.625/371*368,best.targetStats().effective(StatType.DAMAGE,School.DEATH),1e-12);
        assertEquals(200,best.rawCharacterStats().effective(StatType.HEALTH,School.UNIVERSAL));
        assertTrue(best.equipment().gearSet.get(GearSlot.PET).pet.hypothetical);
        assertEquals(before,JsonIO.encode(catalog));
    }

    @Test void twentyTalentPoolPreservesLocksAndCompatibilityAcrossMutationsAndCrossovers() {
        Catalog catalog=catalog();
        for (int i=catalog.petTalents.size();i<20;i++) catalog.petTalents.add(fixed("extra-"+i,StatType.HEALTH,School.UNIVERSAL,i));
        var request=request(catalog); request.petSearch.lockedTalents=List.of(" dealer ","MIGHTY");
        SearchSpace space=new SearchSpace(catalog,request);
        Random random=new Random(97);
        Individual first=space.random(random),second=space.random(random);
        Set<String> signatures=new HashSet<>();
        for (int i=0;i<300;i++) {
            first=GeneticUtils.mutate(GeneticUtils.crossover(first,second,space,random),space,random);
            second=space.random(random);
            for (Individual build : List.of(first,second)) {
                space.validate(build); Gear pet=build.gearSet.get(GearSlot.PET);
                assertEquals(5,ids(pet).size()); assertTrue(ids(pet).containsAll(List.of("dealer","mighty")));
                for (Jewel jewel : build.jewels.get(GearSlot.PET)) if (jewel!=null)
                    assertTrue(Collections.disjoint(ids(pet),jewel.talentIds()));
                signatures.add(build.signature());
            }
        }
        assertTrue(signatures.size()>30);
        Individual copy=CopyUtils.deepCopy(first); space.validate(copy);
        copy.gearSet.get(GearSlot.PET).pet.traits.removeFirst();
        assertThrows(IllegalArgumentException.class,() -> space.validate(copy));
        space.validate(first);
    }

    @Test void lockedStarReservesItsTalentsAndManifestedLocksCannotBeSatisfiedByTheStar() {
        Catalog catalog=catalog(); var request=request(catalog);
        request.lockedJewels.put("PET:0","mighty-jewel");
        request.petSearch.lockedTalents=List.of("dealer");
        SearchSpace space=new SearchSpace(catalog,request);
        for (int seed=0;seed<50;seed++) {
            Individual build=space.random(new Random(seed)); space.validate(build);
            assertFalse(ids(build.gearSet.get(GearSlot.PET)).contains("mighty"));
            assertTrue(ids(build.gearSet.get(GearSlot.PET)).contains("dealer"));
            assertEquals("mighty-jewel",build.jewels.get(GearSlot.PET).getFirst().id);
        }
        request.petSearch.lockedTalents=List.of("Mighty");
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request)).getMessage().contains("locked PET jewel"));
    }

    @Test void allFiveLockedStillAllowJewelSearchAndExactLimitCountsTalents() {
        Catalog catalog=catalog(); var request=request(catalog);
        request.petSearch.lockedTalents=List.of("dealer","giver","accuracy","pierce","health");
        SearchSpace space=new SearchSpace(catalog,request);
        assertEquals(1,space.enumerationChoices(GearSlot.PET).size());
        Gear pet=space.random(new Random(7)).gearSet.get(GearSlot.PET);
        assertSame(pet,space.autoPets.mutate(pet,new Random(7)));
        var best=new Optimizer().optimize(catalog,request).solutions().getFirst();
        assertEquals("mighty-jewel",best.equipment().jewels.get(GearSlot.PET).getFirst().id);
        request.petSearch.lockedTalents=List.of(); request.search.method=GAConfig.Method.EXACT; request.search.exactLimit=2;
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new Optimizer().optimize(catalog,request)).getMessage().contains("exactLimit"));
    }

    @Test void predefinedModeKeepsExistingSearchAndCanFilterPetsByLockedTalentNames() throws Exception {
        Catalog example=JsonIO.readCatalog(Path.of("examples/catalog.json"));
        var ordinary=JsonIO.readRequest(Path.of("examples/offschool-request.json"));
        var expected=new Optimizer().optimize(example,ordinary);
        ordinary.petSearch.template=new GearSelector("Unused template"); ordinary.petSearch.talentPool=List.of("Unused pool");
        assertEquals(JsonIO.encode(expected),JsonIO.encode(new Optimizer().optimize(example,ordinary)));
        Catalog catalog=catalog(); Gear other=JsonIO.MAPPER.convertValue(catalog.gear.getFirst(),Gear.class);
        other.id="other"; other.name="Pet with Mighty"; other.pet.traits.add(catalog.petTalents.get(2)); catalog.gear.add(other);
        var request=request(catalog); request.petSearch.mode=PetSearchConfig.Mode.PREDEFINED;
        request.petSearch.lockedTalents=List.of("mighty");
        SearchSpace space=new SearchSpace(catalog,request);
        assertNull(space.autoPets); assertEquals(List.of("other"),space.gear.get(GearSlot.PET).stream().map(g -> g.id).toList());
        request.search.method=GAConfig.Method.EXACT;
        assertEquals("other",new Optimizer().optimize(catalog,request).solutions().getFirst().equipment().gearSet.get(GearSlot.PET).id);
    }

    @Test void reportsInvalidPoolNamesLocksTemplateAndOwnershipClearly() {
        Catalog catalog=catalog(); var original=request(catalog);
        for (String invalid : List.of("unknown","dealer")) {
            var request=JsonIO.decode(JsonIO.encode(original),OptimizationRequest.class);
            request.petSearch.talentPool=new ArrayList<>(request.petSearch.talentPool); request.petSearch.talentPool.add(invalid);
            assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
        }
        var request=request(catalog); request.petSearch.lockedTalents=List.of("dealer"," dealer ");
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
        request.petSearch.lockedTalents=List.of("outside");
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
        request.petSearch.lockedTalents=List.of(); request.wizard.ownedOnly=true;
        assertTrue(assertThrows(IllegalArgumentException.class,request::validate).getMessage().contains("ownedOnly"));
        request.wizard.ownedOnly=false; request.petSearch.template=new GearSelector("Missing template");
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
        request.petSearch.template=new GearSelector("Planning template"); catalog.gear.getFirst().pet.age=5;
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
        catalog.gear.getFirst().pet.age=7; catalog.gear.getFirst().pet.fullyTrained=false;
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
    }

    @Test void standaloneTalentsAndNamedLocksSurviveJsonSqliteProfilesAndCli() throws Exception {
        Catalog catalog=catalog(); var request=request(catalog); request.petSearch.lockedTalents=List.of("dealer");
        Path json=temp.resolve("catalog.json"),profile=temp.resolve("request.json"),output=temp.resolve("result.json");
        JsonIO.write(json,catalog); JsonIO.write(profile,request);
        try (var db=new SqliteItemRepository(temp.resolve("items.sqlite"))) {
            db.importCatalog(catalog); db.importCatalog(catalog); db.saveProfile("auto",request);
            assertEquals(JsonIO.encode(catalog.petTalents.stream().sorted(Comparator.comparing(t -> t.id)).toList()),JsonIO.encode(db.snapshot().petTalents));
            var loaded=db.loadProfile("auto"); assertEquals(List.of("dealer"),loaded.petSearch.lockedTalents);
            assertEquals(PetSearchConfig.Mode.AUTO,loaded.petSearch.mode);
            assertTrue(new Optimizer().optimize(db.snapshot(),loaded).solutions().getFirst().fitness().feasible());
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        int code=Main.run(new String[]{"optimize","--catalog",json.toString(),"--request",profile.toString(),"--output",output.toString(),"--quiet"},
            new PrintStream(out),new PrintStream(err));
        assertEquals(0,code,err.toString());
        assertTrue(out.toString().contains("talent: dealer"));
        assertTrue(out.toString().contains("AUTO pet search"));
        assertTrue(Files.size(output)>0);
    }

    @Test void canonicalCombinationsHaveDistinctCacheKeysAndRepeatableResults() {
        Catalog catalog=catalog(); var request=request(catalog);
        SearchSpace space=new SearchSpace(catalog,request); FitnessEvaluator evaluator=new FitnessEvaluator(space);
        Set<String> signatures=new HashSet<>();
        for (Gear pet : space.enumerationChoices(GearSlot.PET)) {
            Individual build=new Individual(); build.gearSet.put(GearSlot.PET,pet);
            build.jewels.put(GearSlot.PET,new ArrayList<>(Collections.singletonList(null)));
            assertTrue(signatures.add(build.signature())); evaluator.evaluate(build);
        }
        assertEquals(21,evaluator.evaluated().size());
        assertEquals(JsonIO.encode(new Optimizer().optimize(catalog,request)),JsonIO.encode(new Optimizer().optimize(catalog,request)));
    }

    @Test void rejectsAmbiguousNamesAndAliasesButAllowsExplicitTalentIds() {
        Catalog catalog=catalog();
        catalog.petTalents.getFirst().name="Shared talent";
        PetTrait ambiguous=fixed("Shared talent",StatType.HEALTH,School.UNIVERSAL,2); catalog.petTalents.add(ambiguous);
        var request=request(catalog);
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request)).getMessage().contains("ambiguous"));
        request.petSearch.talentPool=catalog.petTalents.stream().map(t -> t.id).toList();
        request.petSearch.lockedTalents=List.of("Shared talent");
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
        request.petSearch.lockedTalents=List.of(ambiguous.id);
        SearchSpace space=new SearchSpace(catalog,request);
        assertTrue(ids(space.random(new Random(7)).gearSet.get(GearSlot.PET)).contains(ambiguous.id));
        request.petSearch.talentPool=new ArrayList<>(request.petSearch.talentPool);
        request.petSearch.talentPool.add("Mighty"); // Already selected by its ID.
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request)).getMessage().contains("duplicate"));
    }

    @Test void failsEarlyForMissingDefinitionsOversizedPoolsAndImpossibleJewelReservations() {
        Catalog catalog=catalog(); var request=request(catalog);
        request.petSearch.talentPool=List.of("dealer","giver","Mighty","accuracy","pierce");
        request.lockedJewels.put("PET:0","mighty-jewel");
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request)).getMessage().contains("Fewer than five"));
        request.lockedJewels.clear(); catalog.petTalents.clear();
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request)).getMessage().contains("catalog.petTalents"));
        request.petSearch.talentPool=Collections.nCopies(21,"dealer");
        assertThrows(IllegalArgumentException.class,request::validate);
        request.petSearch.talentPool=List.of("dealer","giver","Mighty","accuracy","pierce");
        request.requiredSlots=EnumSet.of(GearSlot.HAT);
        assertThrows(IllegalArgumentException.class,request::validate);
    }
}
