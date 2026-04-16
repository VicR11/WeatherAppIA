package org.weather.model;


/**
 * 📁 model/WeatherData.java
 *
 * Representa el objeto JSON de respuesta final.
 * Al ser un record de Java 17, genera automáticamente
 * constructor, getters, equals, hashCode y toString.
 */
public record WeatherData(
        String cityName,         // Nombre de la ciudad consultada
        double temperature,      // Temperatura en grados Celsius
        String description,      // Descripción legible del clima (ej: "Parcialmente nublado")
        double latitude,         // Latitud usada en la consulta
        double longitude         // Longitud usada en la consulta
) {

    /**
     * Convierte el objeto a una representación JSON sencilla.
     * En un proyecto real usarías Jackson o Gson para esto.
     */
    public String toJson() {
        return String.format("""
                {
                  "cityName": "%s",
                  "temperature": %.1f,
                  "description": "%s",
                  "latitude": %.4f,
                  "longitude": %.4f
                }""",
                cityName, temperature, description, latitude, longitude);
    }


}
