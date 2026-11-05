package me.opd;

import me.opd.DataReading.*;
import me.opd.GA.GAConfig;
import me.opd.JewelHandeling.*;
import me.opd.Optimization.*;
import me.opd.Storage.JsonIO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SocketSearchTest {
    @Test void maximizesHealthWithoutAThresholdAndFillsTearAndSquareWithoutSacrificingRequirements() {
        Gear ring=OptimizerTest.gear("ring",GearSlot.RING);
        ring.baseStats.put(StatType.DAMAGE,School.STORM,100).put(StatType.HEALTH,School.UNIVERSAL,1000);
        for (JewelType type : List.of(JewelType.TEAR,JewelType.SQUARE,JewelType.CIRCLE)) ring.sockets.add(new Socket(type));
        Gear infeasible=OptimizerTest.gear("huge-health-without-damage",GearSlot.RING);
        infeasible.baseStats.put(StatType.HEALTH,School.UNIVERSAL,1e9);
        Catalog catalog=OptimizerTest.catalog(ring,infeasible);
        Jewel tear=OptimizerTest.jewel("health-tear",JewelType.TEAR);
        tear.statBonus.put(StatType.HEALTH,School.UNIVERSAL,155);
        Jewel square=OptimizerTest.jewel("health-square",JewelType.SQUARE);
        square.statBonus.put(StatType.HEALTH,School.UNIVERSAL,525);
        Jewel weakerSquare=OptimizerTest.jewel("weaker-health-square",JewelType.SQUARE);
        weakerSquare.statBonus.put(StatType.HEALTH,School.UNIVERSAL,55);
        Jewel pierce=OptimizerTest.jewel("required-pierce",JewelType.CIRCLE);
        pierce.statBonus.put(StatType.PIERCE,School.DEATH,6);
        Jewel conflictingHealth=OptimizerTest.jewel("health-instead-of-pierce",JewelType.CIRCLE);
        conflictingHealth.statBonus.put(StatType.HEALTH,School.UNIVERSAL,1e6);
        catalog.jewels.addAll(List.of(tear,square,weakerSquare,pierce,conflictingHealth));
        var request=OptimizerTest.request(GearSlot.RING); request.wizard.school=School.STORM;
        request.targets.add(OptimizerTest.target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MINIMUM,100,true));
        request.targets.add(OptimizerTest.target(StatType.PIERCE,School.DEATH,StatTarget.Mode.MINIMUM,6,true));
        // MAXIMIZE needs no threshold value, including when loaded from JSON.
        request.targets.add(JsonIO.decode("""
            {"stat":"HEALTH","school":"UNIVERSAL","mode":"MAXIMIZE","mandatory":false,"scale":1000}
            """,StatTarget.class));
        Optimizer optimizer=new Optimizer(); var ga=optimizer.optimize(catalog,request);
        request.search.method=GAConfig.Method.EXACT;
        var exact=optimizer.optimize(catalog,request);
        var best=ga.solutions().getFirst();
        assertTrue(best.fitness().feasible());
        assertEquals(1680,best.rawCharacterStats().effective(StatType.HEALTH,School.UNIVERSAL));
        assertEquals(List.of("health-tear","health-square","required-pierce"),
            best.equipment().jewels.get(GearSlot.RING).stream().map(j -> j.id).toList());
        assertEquals(exact.solutions().getFirst().fitness(),best.fitness());
        assertEquals(1.68,best.fitness().objective(),1e-9);
    }

    @Test void discoversPinBearingArmorWithSecondarySchoolDamageAndPierceInBothDirections() {
        for (School primary : List.of(School.STORM,School.DEATH)) {
            School secondary=primary==School.STORM ? School.DEATH : School.STORM;
            Gear armor=OptimizerTest.gear("pin-armor",GearSlot.HAT);
            armor.schools.add(primary);
            armor.baseStats.put(StatType.DAMAGE,primary,30);
            armor.sockets.add(new Socket(JewelType.SWORD_PIN));
            armor.sockets.add(new Socket(JewelType.SWORD_PIN));
            Gear universal=OptimizerTest.gear("universal-armor",GearSlot.HAT);
            universal.baseStats.put(StatType.DAMAGE,School.UNIVERSAL,40);
            Catalog catalog=OptimizerTest.catalog(armor,universal);
            Jewel pin=OptimizerTest.jewel("secondary-pin",JewelType.SWORD_PIN);
            pin.schools.add(primary); pin.gearSlots.add(GearSlot.HAT);
            pin.statBonus.put(StatType.DAMAGE,secondary,46).put(StatType.PIERCE,secondary,4);
            catalog.jewels.add(pin);
            for (int i=0;i<60;i++) {
                Jewel irrelevant=OptimizerTest.jewel("irrelevant-"+i,JewelType.SWORD_PIN);
                irrelevant.statBonus.put(StatType.RESISTANCE,School.UNIVERSAL,i);
                catalog.jewels.add(irrelevant);
            }
            var request=OptimizerTest.request(GearSlot.HAT); request.wizard.school=primary;
            request.search.populationSize=12; request.search.generations=0;
            request.targets.add(OptimizerTest.target(StatType.DAMAGE,primary,StatTarget.Mode.MINIMUM,30,true));
            request.targets.add(OptimizerTest.target(StatType.DAMAGE,secondary,StatTarget.Mode.MINIMUM,92,true));
            request.targets.add(OptimizerTest.target(StatType.PIERCE,secondary,StatTarget.Mode.MINIMUM,8,true));
            var ga=new Optimizer().optimize(catalog,request).solutions().getFirst();
            request.search.method=GAConfig.Method.EXACT;
            var exact=new Optimizer().optimize(catalog,request).solutions().getFirst();
            assertTrue(exact.fitness().feasible());
            assertTrue(ga.fitness().feasible(),primary+" must evaluate the armor with its useful secondary pins");
            assertEquals("pin-armor",ga.equipment().gearSet.get(GearSlot.HAT).id);
            assertEquals(92,ga.rawCharacterStats().effective(StatType.DAMAGE,secondary));
            assertEquals(8,ga.rawCharacterStats().effective(StatType.PIERCE,secondary));
            assertEquals(exact.fitness(),ga.fitness());
            request.search.method=GAConfig.Method.GA;
            SearchSpace space=new SearchSpace(catalog,request);
            Individual unpinned=new Individual(); unpinned.gearSet.put(GearSlot.HAT,universal);
            unpinned.jewels.put(GearSlot.HAT,new ArrayList<>());
            Random random=new Random(17); boolean changedHolder=false;
            for (int i=0;i<30;i++) {
                Individual child=GeneticUtils.mutate(unpinned,space,random);
                if (child.gearSet.get(GearSlot.HAT)==armor) {
                    changedHolder=true; space.validate(child);
                    assertTrue(Fitness.evaluate(child.evaluateStats(),request.targets).feasible(),
                        "A newly selected holder must be evaluated with its useful pins");
                }
            }
            assertTrue(changedHolder);
        }
    }

    @Test void socketRefinementRetainsLocksOwnedCopiesAndMandatoryMaximums() {
        Gear armor=OptimizerTest.gear("armor",GearSlot.HAT);
        for (int i=0;i<3;i++) armor.sockets.add(new Socket(JewelType.SWORD_PIN));
        Jewel locked=OptimizerTest.jewel("locked",JewelType.SWORD_PIN);
        locked.statBonus.put(StatType.DAMAGE,School.STORM,5);
        Jewel secondary=OptimizerTest.jewel("secondary",JewelType.SWORD_PIN);
        secondary.schools.add(School.STORM);
        secondary.statBonus.put(StatType.DAMAGE,School.DEATH,46);
        Jewel excessive=OptimizerTest.jewel("excessive",JewelType.SWORD_PIN);
        excessive.statBonus.put(StatType.DAMAGE,School.DEATH,100).put(StatType.DAMAGE,School.STORM,100);
        Catalog catalog=OptimizerTest.catalog(armor); catalog.jewels.addAll(List.of(locked,secondary,excessive));
        var request=OptimizerTest.request(GearSlot.HAT); request.wizard.school=School.STORM;
        request.wizard.ownedOnly=true; request.wizard.ownedItemIds.addAll(List.of("armor","locked","secondary","excessive"));
        request.lockedJewels.put("HAT:0","locked"); request.search.generations=0;
        request.targets.add(OptimizerTest.target(StatType.DAMAGE,School.STORM,StatTarget.Mode.MAXIMUM,5,true));
        request.targets.add(OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MINIMUM,46,true));
        var best=new Optimizer().optimize(catalog,request).solutions().getFirst();
        assertTrue(best.fitness().feasible());
        var jewels=best.equipment().jewels.get(GearSlot.HAT);
        assertEquals("locked",jewels.getFirst().id);
        assertEquals(1,jewels.stream().filter(j -> j!=null && j.id.equals("secondary")).count());
        assertEquals(1,jewels.stream().filter(Objects::isNull).count());
        assertEquals(5,best.rawCharacterStats().effective(StatType.DAMAGE,School.STORM));
    }
}
