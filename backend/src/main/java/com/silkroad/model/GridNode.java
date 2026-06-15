package com.silkroad.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GridNode {
    private double lng;
    private double lat;
    private int gridX;
    private int gridY;
    private double gCost;
    private double hCost;
    private double fCost;
    private GridNode parent;
    private double elevationM;
    private String terrainType;
    private double passability;
    private double waterAccessibility;

    public GridNode(double lng, double lat, int gridX, int gridY) {
        this.lng = lng;
        this.lat = lat;
        this.gridX = gridX;
        this.gridY = gridY;
        this.passability = 1.0;
        this.waterAccessibility = 0.0;
    }

    public void calculateFCost() {
        this.fCost = this.gCost + this.hCost;
    }
}
