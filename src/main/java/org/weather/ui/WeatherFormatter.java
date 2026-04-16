package org.weather.ui;


import org.weather.model.WeatherData;
import org.weather.service.WeatherService;

public class WeatherFormatter {

    public static String format(WeatherService.WeatherResult result) {

        if (!result.isSuccess()) {
            return "ERROR:\n\n" + result.getErrorMessage();
        }

        WeatherData data = result.getData();

        return String.format("""
                Ciudad: %s
                
                Temperatura: %.1f °C
                Descripción: %s
                
                Coordenadas:
                Latitud: %.4f
                Longitud: %.4f
                
                Fuente: %s
                """,
                data.cityName(),
                data.temperature(),
                data.description(),
                data.latitude(),
                data.longitude(),
                result.isFromCache() ? "Caché" : "API"
        );
    }
}