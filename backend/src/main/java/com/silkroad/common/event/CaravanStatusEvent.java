package com.silkroad.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CaravanStatusEvent extends ApplicationEvent {

    private final Long caravanId;
    private final String status;
    private final Double waterSupplyLiters;
    private final Double lng;
    private final Double lat;

    public CaravanStatusEvent(Object source, Long caravanId, String status,
                               Double waterSupplyLiters, Double lng, Double lat) {
        super(source);
        this.caravanId = caravanId;
        this.status = status;
        this.waterSupplyLiters = waterSupplyLiters;
        this.lng = lng;
        this.lat = lat;
    }
}
