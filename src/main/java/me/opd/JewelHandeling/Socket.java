package me.opd.JewelHandeling;

import me.opd.DataReading.JewelType;

public class Socket {
    public JewelType type; // circle, tear, square, etc.

    public Socket(JewelType type) {
        this.type = type;
    }

    public JewelType getType() {
        return type;
    }

    public void setType(JewelType type) {
        this.type = type;
    }
}
