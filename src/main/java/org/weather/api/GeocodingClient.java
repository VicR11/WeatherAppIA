package org.weather.api;

import org.weather.model.Location;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 📁 api/GeocodingClient.java
 *
 * Responsabilidad ÚNICA: convertir un nombre de ciudad
 * en coordenadas geográficas usando la Geocoding API de Open-Meteo.
 *
 * API usada: https://geocoding-api.open-meteo.com/v1/search
 */
public class GeocodingClient {

    private static final String BASE_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;

    public GeocodingClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    public GeocodingClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }



    /**
     * Busca las coordenadas de una ciudad.
     *
     * @param cityName Nombre de la ciudad (ej: "Medellín", "Tokyo")
     * @return Location con nombre, latitud y longitud
     * @throws CityNotFoundException   Si la ciudad no existe en la API
     * @throws ApiException            Si la API responde con error HTTP
     * @throws NetworkException        Si hay problemas de conectividad
     */
    public Location getLocation(String cityName)
            throws CityNotFoundException, ApiException, NetworkException {

        // Validación de entrada
        if (cityName == null || cityName.isBlank()) {
            throw new CityNotFoundException("El nombre de la ciudad no puede estar vacío.");
        }

        String encodedCity = URLEncoder.encode(cityName.trim(), StandardCharsets.UTF_8);
        String url = BASE_URL + "?name=" + encodedCity + "&count=1&language=es&format=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();

        // Ejecutar petición
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            throw new NetworkException("No se pudo conectar a la API de geocodificación. " +
                    "Verifica tu conexión a internet.", e);
        } catch (Exception e) {
            throw new NetworkException("Error de red inesperado: " + e.getMessage(), e);
        }

        // Verificar código HTTP
        if (response.statusCode() != 200) {
            throw new ApiException("La API de geocodificación respondió con código HTTP " +
                    response.statusCode());
        }

        return parseLocation(response.body(), cityName);
    }

    /**
     * Parsea el JSON de respuesta de la Geocoding API.
     *
     * Respuesta esperada de la API:
     * {
     *   "results": [
     *     {
     *       "name": "Medellín",
     *       "latitude": 6.25184,
     *       "longitude": -75.56359
     *     }
     *   ]
     * }
     */
    private Location parseLocation(String json, String originalCityName)
            throws CityNotFoundException {

        // Si no hay resultados, la ciudad no existe
        if (!json.contains("\"results\"") || json.contains("\"results\":null")) {
            throw new CityNotFoundException(
                    "No se encontró la ciudad: \"" + originalCityName + "\". " +
                            "Verifica el nombre e intenta de nuevo.");
        }

        try {
            String name      = extractString(json, "\"name\":");
            double latitude  = extractDouble(json, "\"latitude\":");
            double longitude = extractDouble(json, "\"longitude\":");
            return new Location(name, latitude, longitude);
        } catch (Exception e) {
            throw new CityNotFoundException(
                    "No se pudo procesar la respuesta para: \"" + originalCityName + "\"");
        }
    }

    // ---- Helpers de parseo manual (sin dependencias externas) ---------------

    private String extractString(String json, String key) {
        int idx = json.indexOf(key);
        if (idx == -1) throw new IllegalArgumentException("Clave no encontrada: " + key);
        int start = json.indexOf('"', idx + key.length()) + 1;
        int end   = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private double extractDouble(String json, String key) {
        int idx = json.indexOf(key);
        if (idx == -1) throw new IllegalArgumentException("Clave no encontrada: " + key);
        int start = idx + key.length();
        // Saltar espacios
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return Double.parseDouble(json.substring(start, end));
    }

    // ---- Excepciones propias del cliente ------------------------------------

    public static class CityNotFoundException extends Exception {
        public CityNotFoundException(String message) { super(message); }
    }

    public static class ApiException extends Exception {
        public ApiException(String message) { super(message); }
    }

    public static class NetworkException extends Exception {
        public NetworkException(String message, Throwable cause) { super(message, cause); }
    }
}
