package com.silkroad.model;

public enum TerrainType {
    PLAINS("PLAINS", 0.95, 1.0),
    HILLS("HILLS", 0.8, 1.3),
    MOUNTAINS("MOUNTAINS", 0.6, 1.8),
    HIGH_MOUNTAINS("HIGH_MOUNTAINS", 0.3, 2.5),
    PLATEAU("PLATEAU", 0.7, 1.5),
    VALLEY("VALLEY", 0.85, 1.1),
    DESERT("DESERT", 0.5, 1.6),
    SAND_DUNES("SAND_DUNES", 0.25, 3.0),
    DESERT_STEPPE("DESERT_STEPPE", 0.65, 1.4),
    OASIS("OASIS", 0.9, 1.0),
    FOOTHILLS("FOOTHILLS", 0.75, 1.2),
    SALINE("SALINE", 0.3, 2.0);

    private final String code;
    private final double passability;
    private final double resistanceFactor;

    TerrainType(String code, double passability, double resistanceFactor) {
        this.code = code;
        this.passability = passability;
        this.resistanceFactor = resistanceFactor;
    }

    public String getCode() {
        return code;
    }

    public double getPassability() {
        return passability;
    }

    public double getResistanceFactor() {
        return resistanceFactor;
    }

    public static TerrainType fromCode(String code) {
        for (TerrainType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return PLAINS;
    }
}
