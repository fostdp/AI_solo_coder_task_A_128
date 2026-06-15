package com.silkroad.dtu_receiver.validator;

import com.silkroad.entity.WeatherReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WeatherReportValidator {

    private static final double MIN_TEMP = -60.0;
    private static final double MAX_TEMP = 60.0;
    private static final double MAX_WIND = 200.0;
    private static final double MIN_HUMIDITY = 0.0;
    private static final double MAX_HUMIDITY = 100.0;
    private static final double MAX_PRECIPITATION = 100.0;
    private static final double MAX_VISIBILITY = 50.0;
    private static final double MAX_PRESSURE = 1100.0;
    private static final double MIN_PRESSURE = 800.0;

    public ValidationResult validate(WeatherReport report) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (report.getTemperatureC() != null) {
            if (report.getTemperatureC() < MIN_TEMP || report.getTemperatureC() > MAX_TEMP) {
                errors.add("温度超出合理范围: " + report.getTemperatureC() + "°C");
            }
        } else {
            warnings.add("温度数据缺失，将使用默认值");
        }

        if (report.getWindSpeedKmh() != null) {
            if (report.getWindSpeedKmh() < 0 || report.getWindSpeedKmh() > MAX_WIND) {
                errors.add("风速超出合理范围: " + report.getWindSpeedKmh() + "km/h");
            }
        }

        if (report.getHumidityPct() != null) {
            if (report.getHumidityPct() < MIN_HUMIDITY || report.getHumidityPct() > MAX_HUMIDITY) {
                errors.add("湿度超出合理范围: " + report.getHumidityPct() + "%");
            }
        }

        if (report.getPrecipitationMm() != null && report.getPrecipitationMm() < 0) {
            errors.add("降水量不能为负");
        }
        if (report.getPrecipitationMm() != null && report.getPrecipitationMm() > MAX_PRECIPITATION) {
            warnings.add("降水量异常偏高: " + report.getPrecipitationMm() + "mm");
        }

        if (report.getVisibilityKm() != null) {
            if (report.getVisibilityKm() < 0 || report.getVisibilityKm() > MAX_VISIBILITY) {
                errors.add("能见度超出合理范围: " + report.getVisibilityKm() + "km");
            }
        }

        if (report.getAirPressureHpa() != null) {
            if (report.getAirPressureHpa() < MIN_PRESSURE || report.getAirPressureHpa() > MAX_PRESSURE) {
                warnings.add("气压异常: " + report.getAirPressureHpa() + "hPa");
            }
        }

        if (report.getSandstormProbability() != null) {
            if (report.getSandstormProbability() < 0 || report.getSandstormProbability() > 1.0) {
                errors.add("沙尘暴概率超出[0,1]范围: " + report.getSandstormProbability());
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    public WeatherReport applyDefaults(WeatherReport report) {
        if (report.getTemperatureC() == null) report.setTemperatureC(20.0);
        if (report.getWindSpeedKmh() == null) report.setWindSpeedKmh(15.0);
        if (report.getHumidityPct() == null) report.setHumidityPct(35.0);
        if (report.getPrecipitationMm() == null) report.setPrecipitationMm(0.0);
        if (report.getVisibilityKm() == null) report.setVisibilityKm(10.0);
        if (report.getAirPressureHpa() == null) report.setAirPressureHpa(1013.0);
        if (report.getWindDirection() == null) report.setWindDirection(180);
        return report;
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
}
