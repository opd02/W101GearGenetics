package me.opd.JewelHandeling;

import me.opd.DataReading.JewelType;
import me.opd.DataReading.StatBlock;

public class Jewel {
    public String name;
    public JewelType type;
    public StatBlock statBonus;

    public Jewel(String name, JewelType type, StatBlock statBonus) {
        this.name = name;
        this.type = type;
        this.statBonus = statBonus;
    }
    public Jewel(){

    }
}
