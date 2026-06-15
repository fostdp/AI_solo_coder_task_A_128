package com.silkroad.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RiskAssessmentEvent extends ApplicationEvent {

    private final Long stationId;
    private final Double sandstormProbability;
    private final Double windSpeedKmh;
    private final Double temperatureC;
    private final String riskLevel;

    public RiskAssessmentEvent(Object source, Long stationId,
                                Double sandstormProbability, Double windSpeedKmh,
                                Double temperatureC, String riskLevel) {
        super(source);
        this.stationId = stationId;
        this.sandstormProbability = sandstormProbability;
        this.windSpeedKmh = windSpeedKmh;
        this.temperatureC = temperatureC;
        this.riskLevel = riskLevel;
    }
}
