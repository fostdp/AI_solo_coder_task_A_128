package com.silkroad.model;

public enum Season {
    SPRING("SPRING"),
    SUMMER("SUMMER"),
    AUTUMN("AUTUMN"),
    WINTER("WINTER");

    private final String code;

    Season(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Season fromCode(String code) {
        if (code == null) return SPRING;
        for (Season s : values()) {
            if (s.code.equalsIgnoreCase(code)) {
                return s;
            }
        }
        return SPRING;
    }
}
