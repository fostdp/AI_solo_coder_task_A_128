package com.silkroad.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WeatherReportReceivedEvent extends ApplicationEvent {

    private final Long stationId;
    private final Double windSpeedKmh;
    private final Double humidityPct;
    private final Double temperatureC;
    private final Double sandstormProbability;
    private final Double visibilityKm;

    public WeatherReportReceivedEvent(Object source, Long stationId,
                                       Double windSpeedKmh, Double humidityPct,
                                       Double temperatureC, Double sandstormProbability,
                                       Double visibilityKm) {
        super(source);
        this.stationId = stationId;
        this.windSpeedKmh = windSpeedKmh;
        this.humidityPct = humidityPct;
        this.temperatureC = temperatureC;
        this.sandstormProbability = sandstormProbability;
        this.visibilityKm = visibilityKm;
    }
}
