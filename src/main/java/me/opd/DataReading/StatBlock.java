package me.opd.DataReading;

public class StatBlock {
    public int health = 0;
    public int powerpip = 0;
    public int shadowpip = 0;
    public int pipconversion = 0;
    public int accuracy = 0;
    public int critical = 0;
    public int criticalblock = 0;
    public int resist = 0;
    public int damage = 0;
    public int pierce = 0;
    public boolean provideblade = false;
    public boolean providesharpen = false;

    public void add(StatBlock other) {
        this.health += other.health;
        this.powerpip += other.powerpip;
        this.shadowpip += other.shadowpip;
        this.accuracy += other.accuracy;
        this.pipconversion += other.pipconversion;
        this.critical += other.critical;
        this.criticalblock += other.criticalblock;
        this.resist += other.resist;
        this.provideblade |= other.provideblade;
        this.pierce += other.pierce;
        this.damage += other.damage;
        if((!this.providesharpen && other.providesharpen) || (this.providesharpen && !other.providesharpen)) {
            this.providesharpen = true;
        }
    }

    public StatBlock addDamage(int damage) {
        this.damage += damage;
        return this;
    }

    @Override
    public String toString() {
        return "StatBlock{" +
                "health=" + health +
                ", powerpip=" + powerpip +
                ", shadowpip=" + shadowpip +
                ", pipconversion=" + pipconversion +
                ", accuracy=" + accuracy +
                ", critical=" + critical +
                ", criticalblock=" + criticalblock +
                ", resist=" + resist +
                ", damage=" + damage +
                ", pierce=" + pierce +
                ", provideblade=" + provideblade +
                ", providesharpen=" + providesharpen +
                '}';
    }

    public StatBlock addBaseStats(){
        this.health += 4303;
        this.powerpip += 40;
        this.shadowpip += 85;
        this.pipconversion += 364;
        return this;
    }
}

