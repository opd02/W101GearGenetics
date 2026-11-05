package me.opd;

import me.opd.DataReading.*;
import me.opd.GA.GAConfig;
import me.opd.JewelHandeling.*;
import me.opd.Optimization.*;
import me.opd.PetHandeling.*;
import me.opd.Storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PetIntegrationTest {
    @TempDir Path temp;
    Pet pet() {
        Pet p = new Pet(); p.name="Test pet"; p.age=7; p.fullyTrained=true;
        p.attributes.putAll(Map.of(PetAttribute.STRENGTH,255.0,PetAttribute.INTELLECT,250.0,
            PetAttribute.AGILITY,260.0,PetAttribute.WILL,260.0,PetAttribute.POWER,250.0));
        p.maximumAttributes.putAll(p.attributes);
        p.traits.add(damage("dealer",School.DEATH,0.015));
        p.traits.add(damage("giver",School.UNIVERSAL,0.01));
        return p;
    }
    PetTrait damage(String id,School school,double coefficient) {
        PetTrait t=new PetTrait(); t.id=id; t.name=id;
        PetStatEffect e=new PetStatEffect(); e.primaryStat1=PetAttribute.STRENGTH;
        e.primaryStat2=PetAttribute.WILL; e.secondaryStat=PetAttribute.POWER; e.secondaryModifier=0.5;
        e.coefficients.put(StatType.DAMAGE,school,coefficient); t.effects.add(e); return t;
    }
    Jewel mighty() {
        Jewel j=new Jewel(); j.id="mighty-jewel"; j.name="Mighty"; j.level=1; j.type=JewelType.PET;
        j.complete=true; j.minimumPetAge=7;
        PetTrait t=new PetTrait(); t.id="mighty"; t.name="Mighty"; t.attributeModifiers.put(PetAttribute.STRENGTH,65.0);
        j.petTalents.add(t); j.grantedTalentIds.add(t.id); return j;
    }
    Gear gear(String id,GearSlot slot) {
        Gear g=new Gear(); g.id=id; g.name=id; g.slot=slot; g.level=1; g.complete=true; return g;
    }
    @Test void jewelMightyRecalculatesEveryTalentWithoutMutatingThePet() {
        Pet p=pet(); Jewel j=mighty(); var original=new EnumMap<>(p.attributes);
        assertEquals(16,p.getTotalStats().effective(StatType.DAMAGE,School.DEATH),1e-9);
        var totals=PetCalculator.calculate(p,List.of(j));
        assertEquals(17.625,totals.effective(StatType.DAMAGE,School.DEATH),1e-9);
        assertEquals(7.05,totals.effective(StatType.DAMAGE,School.STORM),1e-9);
        assertEquals(original,p.attributes);
        p.rounding=Pet.Rounding.NEAREST_WHOLE;
        assertEquals(18,PetCalculator.calculate(p,List.of(j)).effective(StatType.DAMAGE,School.DEATH));
        p.traits.add(j.petTalents.getFirst());
        assertEquals(18,p.getTotalStats().effective(StatType.DAMAGE,School.DEATH));
        assertThrows(IllegalArgumentException.class,() -> PetCalculator.calculate(p,List.of(j)));
    }
    @Test void ageAndPremiumJewelTalentOverlapAreEnforcedBeforeSearch() {
        Gear g=gear("pet",GearSlot.PET); g.pet=pet(); Socket s=new Socket(JewelType.PET);
        Jewel j=mighty(); g.pet.age=6;
        assertFalse(s.accepts(j,g,School.DEATH,170));
        g.pet.age=7; assertTrue(s.accepts(j,g,School.DEATH,170));
        // A jewel granting two talents cannot duplicate either manifested talent.
        j.petTalents.add(damage("dealer",School.DEATH,0.015));
        assertFalse(s.accepts(j,g,School.DEATH,170));
        g.pet.age=3; j=mighty(); j.minimumPetAge=0;
        assertFalse(s.accepts(j,g,School.DEATH,170));
        g.pet.age=2; assertThrows(IllegalArgumentException.class,g.pet::validate);
    }
    @Test void invalidAttributesAndUnsupportedTrainingAssumptionsFailEarly() {
        Pet p=pet(); p.attributes.remove(PetAttribute.STRENGTH);
        assertThrows(IllegalArgumentException.class,p::getTotalStats);
        p=pet(); p.fullyTrained=false; final Pet untrained=p;
        assertThrows(IllegalArgumentException.class,() -> PetCalculator.calculate(untrained,List.of(mighty())));
        p=pet(); p.maximumAttributes.put(PetAttribute.STRENGTH,null);
        assertThrows(IllegalArgumentException.class,p::validate);
        p=pet(); p.attributes.put(PetAttribute.STRENGTH,256.0);
        assertThrows(IllegalArgumentException.class,p::validate);
    }
    @Test void gaAndExactFindDynamicPetSolutionWithPinLocksAndOwnedCopies() {
        Gear hat=gear("hat",GearSlot.HAT); hat.baseStats.put(StatType.DAMAGE,School.DEATH,10);
        hat.sockets.add(new Socket(JewelType.SWORD_PIN));
        Gear pet=gear("pet",GearSlot.PET); pet.pet=pet(); pet.sockets.add(new Socket(JewelType.PET));
        Jewel pin=new Jewel(); pin.id="storm-pin"; pin.name="Storm pin for Death"; pin.level=170;
        pin.type=JewelType.SWORD_PIN; pin.complete=true; pin.schools.add(School.DEATH);
        pin.gearSlots.add(GearSlot.HAT); pin.statBonus.put(StatType.DAMAGE,School.STORM,20);
        Catalog c=new Catalog(); c.gear.addAll(List.of(hat,pet)); c.jewels.addAll(List.of(pin,mighty()));
        OptimizationRequest r=new OptimizationRequest(); r.requiredSlots=EnumSet.of(GearSlot.HAT,GearSlot.PET);
        r.wizard.school=School.DEATH; r.wizard.level=170; r.wizard.baseOverrides=new StatBlock(); r.wizard.ownedOnly=true;
        r.wizard.ownedItemIds.addAll(List.of("hat","pet","storm-pin","mighty-jewel")); r.lockedJewels.put("HAT:0","storm-pin");
        StatTarget t=new StatTarget(); t.stat=StatType.DAMAGE; t.school=School.DEATH;
        t.mode=StatTarget.Mode.MINIMUM; t.value=27; t.scale=27; t.mandatory=true; r.targets.add(t);
        r.search.populationSize=12; r.search.generations=20; r.search.seed=3;
        var ga=new Optimizer().optimize(c,r).solutions().getFirst();
        r.search.method=GAConfig.Method.EXACT;
        var exact=new Optimizer().optimize(c,r).solutions().getFirst();
        assertTrue(ga.fitness().feasible()); assertTrue(exact.fitness().feasible());
        assertEquals(27.625,exact.rawCharacterStats().effective(StatType.DAMAGE,School.DEATH),1e-9);
        assertEquals(27.05,exact.rawCharacterStats().effective(StatType.DAMAGE,School.STORM),1e-9);
        assertEquals(exact.equipment().signature(),ga.equipment().signature());
        r.wizard.ownedQuantities.put("mighty-jewel",0);
        assertFalse(new Optimizer().optimize(c,r).solutions().getFirst().fitness().feasible());
    }
    @Test void dynamicPetCatalogSurvivesJsonSqliteAndIndependentCopy() throws Exception {
        Gear g=gear("pet",GearSlot.PET); g.pet=pet(); g.sockets.add(new Socket(JewelType.PET));
        Catalog c=new Catalog(); c.gear.add(g); c.jewels.add(mighty());
        Path json=temp.resolve("pets.json"); JsonIO.write(json,c);
        Catalog reloaded=JsonIO.readCatalog(json);
        try (var db=new SqliteItemRepository(temp.resolve("pets.sqlite"))) {
            db.importCatalog(reloaded); reloaded=db.snapshot();
        }
        Individual build=new Individual(); build.gearSet.put(GearSlot.PET,reloaded.gear.getFirst());
        build.jewels.put(GearSlot.PET,new ArrayList<>(List.of(reloaded.jewels.getFirst())));
        Individual copied=me.opd.Utils.CopyUtils.deepCopy(build);
        assertEquals(17.625,copied.evaluateStats().effective(StatType.DAMAGE,School.DEATH),1e-9);
        copied.gearSet.get(GearSlot.PET).pet.attributes.put(PetAttribute.STRENGTH,1.0);
        assertEquals(255,build.gearSet.get(GearSlot.PET).pet.attributes.get(PetAttribute.STRENGTH));
    }
    @Test void pinsRequireMatchingShapeLegalHolderAndExplicitWizardEligibility() {
        Jewel pin=new Jewel(); pin.id="pin"; pin.name="pin"; pin.level=170; pin.type=JewelType.SWORD_PIN;
        pin.schools.add(School.DEATH); pin.gearSlots.addAll(Set.of(GearSlot.HAT,GearSlot.ROBE,GearSlot.BOOTS));
        Socket socket=new Socket(JewelType.SWORD_PIN);
        assertTrue(socket.accepts(pin,gear("hat",GearSlot.HAT),School.DEATH,170));
        assertFalse(socket.accepts(pin,gear("hat",GearSlot.HAT),School.STORM,170));
        assertFalse(socket.accepts(pin,gear("hat",GearSlot.HAT),School.DEATH,169));
        assertFalse(socket.accepts(pin,gear("ring",GearSlot.RING),School.DEATH,170));
        assertFalse(new Socket(JewelType.SHIELD_PIN).accepts(pin,gear("hat",GearSlot.HAT),School.DEATH,170));
    }
    @Test void newGearSamplesSocketsWhileRepairPreservesExplicitEmptyChoicesAndOwnedLimits() {
        Gear hat=gear("hat",GearSlot.HAT);
        hat.sockets.add(new Socket(JewelType.SWORD_PIN)); hat.sockets.add(new Socket(JewelType.SWORD_PIN));
        Jewel pin=new Jewel(); pin.id="pin"; pin.name="pin"; pin.level=1; pin.complete=true; pin.type=JewelType.SWORD_PIN;
        Catalog c=new Catalog(); c.gear.add(hat); c.jewels.add(pin);
        OptimizationRequest r=new OptimizationRequest(); r.requiredSlots=EnumSet.of(GearSlot.HAT);
        r.wizard.school=School.DEATH; r.wizard.level=170; r.wizard.baseOverrides=new StatBlock(); r.wizard.ownedOnly=true;
        r.wizard.ownedItemIds.addAll(List.of("hat","pin"));
        SearchSpace space=new SearchSpace(c,r); Random random=new Random(7); int filled=0;
        for (int i=0;i<30;i++) {
            Individual build=space.random(random); space.validate(build);
            long copies=build.jewels.get(GearSlot.HAT).stream().filter(Objects::nonNull).count();
            assertTrue(copies<=1); filled+=copies;
        }
        assertTrue(filled>0,"New gear must explore filled pin sockets");
        Individual empty=new Individual(); empty.gearSet.put(GearSlot.HAT,hat);
        empty.jewels.put(GearSlot.HAT,new ArrayList<>(Arrays.asList(null,null)));
        space.repairJewels(empty,random);
        assertTrue(empty.jewels.get(GearSlot.HAT).stream().allMatch(Objects::isNull));
    }
}
