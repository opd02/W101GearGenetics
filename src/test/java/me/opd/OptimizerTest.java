package me.opd;
import me.opd.DataReading.*;
import me.opd.GA.*;
import me.opd.JewelHandeling.*;
import me.opd.Optimization.*;
import me.opd.PetHandeling.*;
import me.opd.Storage.*;
import me.opd.Utils.CopyUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OptimizerTest {
    @TempDir Path temp;
    static Gear gear(String id,GearSlot slot) {
        Gear g=new Gear(); g.id=id; g.name=id; g.slot=slot; g.level=1; g.complete=true; g.source="test"; return g;
    }
    static Jewel jewel(String id,JewelType type) {
        Jewel j=new Jewel(); j.id=id; j.name=id; j.type=type; j.level=1; j.complete=true; j.source="test"; return j;
    }
    static StatTarget target(StatType stat,School school,StatTarget.Mode mode,double value,boolean mandatory) {
        StatTarget t=new StatTarget(); t.stat=stat; t.school=school; t.mode=mode; t.value=value; t.mandatory=mandatory; t.scale=Math.max(1,value); return t;
    }
    static OptimizationRequest request(GearSlot... slots) {
        OptimizationRequest r=new OptimizationRequest(); r.wizard.school=School.DEATH; r.wizard.level=170;
        r.wizard.baseOverrides=new StatBlock(); r.requiredSlots=EnumSet.copyOf(List.of(slots));
        r.search.populationSize=40; r.search.elites=2; r.search.generations=30; r.search.seed=71;
        return r;
    }
    static Catalog catalog(Gear... gear) { Catalog c=new Catalog(); c.gear.addAll(List.of(gear)); return c; }
    @Test void universalAndAllSevenSchoolsRemainSeparate() {
        StatBlock stats=new StatBlock().put(StatType.DAMAGE,School.UNIVERSAL,40)
            .put(StatType.DAMAGE,School.STORM,150).put(StatType.DAMAGE,School.FIRE,20);
        for (School s : School.values()) if (s!=School.UNIVERSAL)
            assertEquals(s==School.STORM?190:s==School.FIRE?60:40,stats.effective(StatType.DAMAGE,s));
        assertEquals(40,stats.raw(StatType.DAMAGE,School.UNIVERSAL));
        for (StatType type : StatType.values()) if (type.schoolSpecific) {
            StatBlock block=new StatBlock().put(type,School.UNIVERSAL,3);
            for (School s : School.values()) if (s!=School.UNIVERSAL) {
                block.put(type,s,7); assertEquals(10,block.effective(type,s));
            }
        }
    }
    @Test void hardRequirementsDominateUnlimitedSoftHealth() {
        var r=request(GearSlot.HAT);
        r.targets.add(target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MINIMUM,150,true));
        r.targets.add(target(StatType.HEALTH,School.UNIVERSAL,StatTarget.Mode.MAXIMIZE,0,false));
        StatBlock feasible=new StatBlock().put(StatType.DAMAGE,School.STORM,150);
        StatBlock infeasible=new StatBlock().put(StatType.HEALTH,School.UNIVERSAL,1e12).put(StatType.DAMAGE,School.STORM,149);
        assertTrue(Fitness.evaluate(feasible,r.targets).compareTo(Fitness.evaluate(infeasible,r.targets))>0);
    }
    @Test void deficitsPreferredPenaltiesMaximumAndIgnoreAreNumeric() {
        var min=target(StatType.DAMAGE,School.FIRE,StatTarget.Mode.MINIMUM,100,true);
        assertEquals(0.5,Fitness.evaluate(new StatBlock().put(StatType.DAMAGE,School.FIRE,50),List.of(min)).hardDeficit());
        assertTrue(Fitness.evaluate(new StatBlock().put(StatType.DAMAGE,School.FIRE,90),List.of(min))
            .compareTo(Fitness.evaluate(new StatBlock(),List.of(min)))>0);
        var preferred=target(StatType.ACCURACY,School.FIRE,StatTarget.Mode.PREFERRED,30,false);
        preferred.abovePenalty=0; preferred.belowPenalty=2;
        assertEquals(0,Fitness.evaluate(new StatBlock().put(StatType.ACCURACY,School.FIRE,40),List.of(preferred)).objective());
        assertEquals(-1,Fitness.evaluate(new StatBlock().put(StatType.ACCURACY,School.FIRE,15),List.of(preferred)).objective());
        var max=target(StatType.POWER_PIP_CHANCE,School.UNIVERSAL,StatTarget.Mode.MAXIMUM,100,true);
        assertFalse(Fitness.evaluate(new StatBlock().put(StatType.POWER_PIP_CHANCE,School.UNIVERSAL,101),List.of(max)).feasible());
        max.mandatory=false; max.mode=StatTarget.Mode.IGNORE;
        assertEquals(0,Fitness.evaluate(new StatBlock().put(StatType.POWER_PIP_CHANCE,School.UNIVERSAL,101),List.of(max)).objective());
    }
    @Test void eligibilityOwnershipExclusionsAndLocksSurviveGenerations() {
        Gear locked=gear("locked",GearSlot.HAT), excluded=gear("excluded",GearSlot.HAT);
        Gear wrongSchool=gear("wrong",GearSlot.ROBE); wrongSchool.schools.add(School.FIRE);
        Gear high=gear("high",GearSlot.ROBE); high.level=180;
        Gear robe=gear("owned",GearSlot.ROBE), other=gear("unowned",GearSlot.ROBE);
        Jewel pin=jewel("locked-pin",JewelType.POWER_PIN);
        pin.bonusSchools.add(School.STORM); pin.gearSlots.add(GearSlot.HAT);
        locked.sockets.add(new Socket(JewelType.POWER_PIN));
        Catalog c=catalog(locked,excluded,wrongSchool,high,robe,other); c.jewels.add(pin);
        var r=request(GearSlot.HAT,GearSlot.ROBE); r.search.generations=150;
        r.lockedItems.put(GearSlot.HAT,locked.id); r.lockedJewels.put("HAT:0",pin.id);
        r.excludedIds.add(excluded.id); r.wizard.ownedOnly=true;
        r.wizard.ownedItemIds.addAll(List.of(locked.id,excluded.id,robe.id,pin.id,wrongSchool.id,high.id));
        SearchSpace space=new SearchSpace(c,r);
        assertEquals(List.of(robe),space.gear.get(GearSlot.ROBE));
        FitnessEvaluator eval=new FitnessEvaluator(space);
        Random random=new Random(9);
        Individual a=space.random(random),b=space.random(random);
        for (int i=0;i<200;i++) {
            a=GeneticUtils.mutate(GeneticUtils.crossover(a,b,space,random),space,random);
            space.validate(a); eval.evaluate(a);
            assertEquals(locked.id,a.gearSet.get(GearSlot.HAT).id);
            assertEquals(pin.id,a.jewels.get(GearSlot.HAT).getFirst().id);
        }
        var result=new Optimizer().optimize(c,r);
        for (var solution : result.solutions()) {
            assertEquals(locked.id,solution.equipment().gearSet.get(GearSlot.HAT).id);
            assertEquals(robe.id,solution.equipment().gearSet.get(GearSlot.ROBE).id);
        }
    }
    @Test void lockedExcludedOrIneligibleGearGivesActionableError() {
        Gear g=gear("locked",GearSlot.HAT); Catalog c=catalog(g); var r=request(GearSlot.HAT);
        r.lockedItems.put(GearSlot.HAT,g.id); r.excludedIds.add(g.id);
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r)).getMessage().contains("excluded"));
        r.excludedIds.clear(); g.level=171;
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r)).getMessage().contains("HAT"));
        g.level=1; g.schools.add(School.ICE);
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
    }
    @Test void missingSlotsAndEmptyPoolsFailButSingleItemPoolsWork() {
        Catalog c=catalog(gear("only",GearSlot.HAT)); var r=request(GearSlot.HAT);
        assertEquals(1,new Optimizer().optimize(c,r).uniqueEvaluations());
        r.requiredSlots.add(GearSlot.ROBE);
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new Optimizer().optimize(c,r)).getMessage().contains("ROBE"));
        assertThrows(IllegalArgumentException.class,() -> new Optimizer().optimize(new Catalog(),request(GearSlot.HAT)));
    }
    @Test void incompatibleJewelsPinsAndSocketPositionsAreRejected() {
        Gear g=gear("hat",GearSlot.HAT); g.sockets.add(new Socket(JewelType.TEAR)); g.sockets.add(new Socket(JewelType.SWORD_PIN));
        Jewel circle=jewel("circle",JewelType.CIRCLE),pin=jewel("pin",JewelType.SWORD_PIN);
        pin.gearSlots.add(GearSlot.ROBE); pin.bonusSchools.add(School.STORM);
        Catalog c=catalog(g); c.jewels.addAll(List.of(circle,pin)); var r=request(GearSlot.HAT);
        r.lockedJewels.put("HAT:1",pin.id);
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
        pin.gearSlots=EnumSet.of(GearSlot.HAT); g.sockets.get(1).allowedSchools.add(School.ICE);
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
        g.sockets.get(1).allowedSchools=EnumSet.of(School.STORM);
        SearchSpace space=new SearchSpace(c,r); Individual build=space.random(new Random(1));
        assertNull(build.jewels.get(GearSlot.HAT).get(0)); assertEquals(pin,build.jewels.get(GearSlot.HAT).get(1));
        build.jewels.get(GearSlot.HAT).set(0,circle); assertThrows(IllegalArgumentException.class,() -> space.validate(build));
        r.lockedJewels.clear(); r.lockedJewels.put("HAT:99",pin.id); assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
    }
    @Test void ownedJewelsCannotBeDuplicatedAndLocksReserveCopies() {
        Gear g=gear("hat",GearSlot.HAT); g.sockets.add(new Socket(JewelType.CIRCLE)); g.sockets.add(new Socket(JewelType.CIRCLE));
        Jewel j=jewel("j",JewelType.CIRCLE); j.statBonus.put(StatType.DAMAGE,School.STORM,10);
        Catalog c=catalog(g); c.jewels.add(j); var r=request(GearSlot.HAT); r.wizard.ownedOnly=true;
        r.wizard.ownedItemIds.addAll(List.of(g.id,j.id)); r.lockedJewels.put("HAT:1",j.id);
        r.search.method=GAConfig.Method.EXACT;
        var result=new Optimizer().optimize(c,r);
        assertEquals(10,result.solutions().getFirst().rawEquipmentStats().effective(StatType.DAMAGE,School.STORM));
        r.lockedJewels.put("HAT:0",j.id); assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
        r.wizard.ownedQuantities.put(j.id,2); assertDoesNotThrow(() -> new Optimizer().optimize(c,r));
    }
    @Test void genericSetRulesAndPetResolvedTalentsAggregateOnce() {
        Gear a=gear("a",GearSlot.HAT),b=gear("b",GearSlot.PET); a.setIds.add("duo"); b.setIds.add("duo");
        b.pet=new Pet(); PetTrait t=new PetTrait(); t.id="talent"; t.name="Resolved"; t.statBonus.put(StatType.DAMAGE,School.FIRE,7); b.pet.traits.add(t);
        SetBonus bonus=new SetBonus(); bonus.setId="duo"; bonus.pieces=2; bonus.stats.put(StatType.DAMAGE,School.UNIVERSAL,8);
        Catalog c=catalog(a,b); c.setBonuses.add(bonus); var r=request(GearSlot.HAT,GearSlot.PET);
        var result=new Optimizer().optimize(c,r).solutions().getFirst();
        assertEquals(15,result.rawCharacterStats().effective(StatType.DAMAGE,School.FIRE));
        assertEquals(8,result.rawCharacterStats().effective(StatType.DAMAGE,School.STORM));
        assertEquals(List.of("duo:2"),result.appliedSets());
        b.pet.traits.add(t); assertThrows(IllegalArgumentException.class,b.pet::validate);
        b.pet.traits.clear();
        for (int i=0;i<6;i++) { PetTrait trait=new PetTrait(); trait.id="t"+i; trait.name="t"+i; b.pet.traits.add(trait); }
        assertThrows(IllegalArgumentException.class,b.pet::validate);
    }
    @Test void hypotheticalPetsAreNotSelectableInOwnedOnlyMode() {
        Gear p=gear("pet",GearSlot.PET); p.pet=new Pet(); p.pet.hypothetical=true;
        var r=request(GearSlot.PET); r.wizard.ownedOnly=true; r.wizard.ownedItemIds.add(p.id);
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog(p),r));
    }
    @Test void invalidTargetsAndSettingsFailEarly() {
        var r=request(GearSlot.HAT);
        var t=target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MINIMUM,10,true); r.targets.add(t);
        t.scale=0; assertThrows(IllegalArgumentException.class,r::validate); t.scale=1;
        t.weight=Double.NaN; assertThrows(IllegalArgumentException.class,r::validate); t.weight=1;
        r.targets.add(target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MAXIMUM,5,true));
        assertThrows(IllegalArgumentException.class,r::validate); r.targets.clear();
        t.stat=StatType.HEALTH; t.school=School.STORM; r.targets.add(t); assertThrows(IllegalArgumentException.class,r::validate);
        r.targets.clear(); r.search.elites=r.search.populationSize; assertThrows(IllegalArgumentException.class,r::validate);
    }
    @Test void baseStatsNeedAnExactSourceOrExplicitOverridesAndRatingsStaySeparate() {
        Catalog c=catalog(gear("hat",GearSlot.HAT)); var r=request(GearSlot.HAT); r.wizard.baseOverrides=null;
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
        c.baseStats.put("DEATH:170",new StatBlock().put(StatType.HEALTH,School.UNIVERSAL,5000).put(StatType.SHADOW_PIP_RATING,School.UNIVERSAL,85));
        r.wizard.baseOverrides=new StatBlock().put(StatType.HEALTH,School.UNIVERSAL,6000);
        var stats=new SearchSpace(c,r).base;
        assertEquals(6000,stats.effective(StatType.HEALTH,School.UNIVERSAL)); assertEquals(85,stats.effective(StatType.SHADOW_PIP_RATING,School.UNIVERSAL));
        assertEquals(0,stats.effective(StatType.SHADOW_PIP_CHANCE,School.UNIVERSAL));
    }
    @Test void seedReproducibilityGaAndTinyExactAgree() {
        Catalog c=new Catalog();
        for (int i=0;i<3;i++) {
            Gear h=gear("h"+i,GearSlot.HAT),b=gear("b"+i,GearSlot.ROBE);
            h.baseStats.put(StatType.DAMAGE,School.STORM,i*50); b.baseStats.put(StatType.DAMAGE,School.UNIVERSAL,i*25);
            c.gear.addAll(List.of(h,b));
        }
        var r=request(GearSlot.HAT,GearSlot.ROBE);
        r.targets.add(target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MINIMUM,150,true));
        r.targets.add(target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MAXIMIZE,0,false));
        Optimizer optimizer=new Optimizer(); var first=optimizer.optimize(c,r); var second=optimizer.optimize(c,r);
        assertEquals(JsonIO.encode(first),JsonIO.encode(second));
        r.search.method=GAConfig.Method.EXACT; var exact=optimizer.optimize(c,r);
        assertTrue(exact.exhaustive()); assertEquals(9,exact.uniqueEvaluations());
        assertEquals(exact.solutions().getFirst().fitness(),first.solutions().getFirst().fitness());
        assertTrue(first.solutions().getFirst().fitness().feasible());
        for (int i=1;i<first.history().size();i++) {
            var before=first.history().get(i-1); var after=first.history().get(i);
            assertTrue(after.hardDeficit()<=before.hardDeficit());
        }
        r.search.exactLimit=2; assertThrows(IllegalArgumentException.class,() -> optimizer.optimize(c,r));
    }
    @Test void legacyFilesQuotedCsvBooleansAndDamageBounder() throws Exception {
        List<Gear> gear=GearLoader.loadGearFromCSV("GearDB.csv",School.FIRE);
        assertFalse(gear.isEmpty()); assertFalse(gear.getFirst().complete);
        assertFalse(GearLoader.loadJewelsFromCSV("JewelDB.csv",School.FIRE).isEmpty());
        assertEquals(11,GearLoader.loadTraitsFromCSV("PetTraitsDB.csv",School.FIRE).size());
        Path file=temp.resolve("quoted.csv");
        String header=Files.readAllLines(Path.of("GearDB.csv")).getFirst();
        Files.writeString(file,header+"\n\"Hat, Fancy\",HAT,10,0,0,0,0,0,0,5,0,0,true,true,null,null,null,null\n");
        Gear quoted=GearLoader.loadGearFromCSV(file.toString(),School.STORM).getFirst();
        assertEquals("Hat, Fancy",quoted.name); assertEquals(2,quoted.itemCards.size());
        assertArrayEquals(new double[]{5,5},StatBounder.findDamageBounds(List.of(quoted),School.STORM));
        Files.writeString(file,Files.readString(file).replace(",10,",",oops,"));
        assertTrue(assertThrows(IOException.class,() -> GearLoader.loadGearFromCSV(file.toString(),School.STORM)).getMessage().contains("row 1"));
    }
    @Test void deepCopyAndGenomeCopyDoNotShareSocketLists() {
        Gear g=gear("g",GearSlot.HAT); g.sockets.add(new Socket(JewelType.CIRCLE)); Jewel j=jewel("j",JewelType.CIRCLE);
        Individual a=new Individual(); a.gearSet.put(g.slot,g); a.jewels.put(g.slot,new ArrayList<>(List.of(j)));
        Individual b=a.copyGenome(); b.jewels.get(g.slot).clear(); assertEquals(1,a.jewels.get(g.slot).size());
        Individual deep=CopyUtils.deepCopy(a); deep.gearSet.get(g.slot).baseStats.put(StatType.HEALTH,School.UNIVERSAL,10);
        deep.jewels.get(g.slot).getFirst().name="changed";
        assertEquals(0,g.baseStats.effective(StatType.HEALTH,School.UNIVERSAL)); assertEquals("j",j.name);
    }
    @Test void completeOffSchoolExampleAndCliRoundTrip() throws Exception {
        Catalog c=JsonIO.readCatalog(Path.of("examples/catalog.json"));
        var r=JsonIO.readRequest(Path.of("examples/offschool-request.json"));
        var result=new Optimizer().optimize(c,r); var best=result.solutions().getFirst();
        assertTrue(best.fitness().feasible());
        assertEquals(150,best.rawCharacterStats().effective(StatType.DAMAGE,School.STORM));
        assertEquals(60,best.rawCharacterStats().effective(StatType.DAMAGE,School.FIRE));
        assertEquals(40,best.rawCharacterStats().effective(StatType.RESISTANCE,School.UNIVERSAL));
        assertEquals(100,best.rawCharacterStats().effective(StatType.POWER_PIP_CHANCE,School.UNIVERSAL));
        assertEquals(10,best.equipment().gearSet.size()); assertTrue(result.solutions().size()>1);
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        Path resultFile=temp.resolve("results.json"),copy=temp.resolve("profile.json");
        assertEquals(0,Main.run(new String[]{"optimize","--request","examples/offschool-request.json","--catalog","examples/catalog.json",
            "--output",resultFile.toString(),"--save-request",copy.toString(),"--quiet"},new PrintStream(stdout),new PrintStream(stderr)),stderr.toString());
        for (School school : School.values()) assertTrue(stdout.toString().contains(school+":"));
        assertTrue(Files.exists(resultFile)); assertEquals(School.DEATH,JsonIO.readRequest(copy).wizard.school);
        assertEquals(2,Main.run(new String[]{"optimize","--typo","x"},new PrintStream(stdout),new PrintStream(stderr)));
    }

    @Test void petJewelCannotDuplicateAManifestedTalent() {
        Gear p=gear("pet",GearSlot.PET); p.pet=new Pet(); p.sockets.add(new Socket(JewelType.PET));
        PetTrait trait=new PetTrait(); trait.id="proof"; trait.name="Resolved Proof"; p.pet.traits.add(trait);
        Jewel j=jewel("jewel",JewelType.PET); j.grantedTalentIds.add("proof");
        Catalog c=catalog(p); c.jewels.add(j); var r=request(GearSlot.PET); r.lockedJewels.put("PET:0",j.id);
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(c,r));
        j.grantedTalentIds.clear(); j.grantedTalentIds.add("defy");
        assertDoesNotThrow(() -> new SearchSpace(c,r));
    }
    @Test void sourcesCategoriesAndIncompleteRecordsAreFilteredBeforeSearch() {
        Gear allowed=gear("allowed",GearSlot.HAT),source=gear("source",GearSlot.HAT),category=gear("category",GearSlot.HAT),partial=gear("partial",GearSlot.HAT);
        source.source="excluded-source"; category.category="excluded-category"; partial.complete=false;
        var r=request(GearSlot.HAT); r.excludedSources.add(source.source); r.excludedCategories.add(category.category);
        assertEquals(List.of(allowed),new SearchSpace(catalog(allowed,source,category,partial),r).gear.get(GearSlot.HAT));
    }
    @Test void infeasibleResultsExplainUnmetWeightedRequirements() {
        Gear low=gear("low",GearSlot.HAT),closer=gear("closer",GearSlot.HAT);
        low.baseStats.put(StatType.DAMAGE,School.STORM,10); closer.baseStats.put(StatType.DAMAGE,School.STORM,50);
        var r=request(GearSlot.HAT); r.search.method=GAConfig.Method.EXACT;
        var t=target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MINIMUM,100,true); t.weight=2; r.targets.add(t);
        var result=new Optimizer().optimize(catalog(low,closer),r); var best=result.solutions().getFirst();
        assertFalse(best.fitness().feasible()); assertEquals("closer",best.equipment().gearSet.get(GearSlot.HAT).id);
        assertEquals(1,best.fitness().hardDeficit()); assertEquals(1,best.fitness().components().getFirst().hardContribution());
        assertFalse(best.fitness().components().getFirst().achieved()); assertTrue(result.warnings().stream().anyMatch(w -> w.contains("No feasible")));
    }
}
