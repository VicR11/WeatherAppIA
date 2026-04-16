package org.weather.api;

import org.weather.api.WeatherCodeMapper;
import org.weather.model.Location;
import org.weather.model.WeatherData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle.block;

/**
 * 📁 api/OpenMeteoClient.java
 *
 * Responsabilidad ÚNICA: obtener los datos meteorológicos actuales
 * desde la API de Open-Meteo usando coordenadas geográficas.
 *
 * API usada: https://api.open-meteo.com/v1/forecast
 * Documentación: https://open-meteo.com/en/docs
 */
public class OpenMeteoClient {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(OpenMeteoClient.class.getName());
    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;

    public OpenMeteoClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    // ✅ Constructor nuevo (solo para tests)
    public OpenMeteoClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Obtiene el clima actual para una ubicación dada.
     *
     * @param location Objeto con nombre, latitud y longitud
     * @return WeatherData con temperatura y descripción del clima
     * @throws ApiException     Si la API responde con error HTTP
     * @throws NetworkException Si hay problemas de conectividad
     */
    private static final String WEATHER_FIELDS = "temperature_2m,weathercode";
    public WeatherData getWeather(Location location)
            throws ApiException, NetworkException {

        // ✅ Mejora — los parámetros de la API como constante con nombre descriptivo


        String url = String.format(
                java.util.Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&current=%s&timezone=auto",
                BASE_URL,
                location.latitude(),
                location.longitude(),
                WEATHER_FIELDS          // ← ahora es fácil agregar más campos aquí
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            throw new NetworkException("No se pudo conectar a Open-Meteo. " +
                    "Verifica tu conexión a internet.", e);
        } catch (java.io.IOException e) {
            throw new NetworkException("Error de red al contactar Open-Meteo: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // ← buena práctica: restaurar el flag de interrupción
            throw new NetworkException("La petición fue interrumpida.", e);
        }


// ✅ Mejora — mensajes distintos según el rango de error
        int status = response.statusCode();
        if (status == 400) {
            throw new ApiException("Parámetros inválidos en la petición (HTTP 400). " +
                    "Verifica las coordenadas enviadas.");
        } else if (status == 429) {
            throw new ApiException("Demasiadas peticiones a la API (HTTP 429). " +
                    "Espera unos segundos e intenta de nuevo.");
        } else if (status >= 500) {
            throw new ApiException("El servidor de Open-Meteo no está disponible (HTTP " + status + "). " +
                    "Intenta de nuevo más tarde.");
        } else if (status != 200) {
            throw new ApiException("Respuesta inesperada de Open-Meteo (HTTP " + status + ").");
        }

        return parseWeatherData(response.body(), location);
    }

    /**
     * Parsea el JSON de respuesta de Open-Meteo.
     *
     * Respuesta esperada de la API:
     * {
     *   "current": {
     *     "temperature_2m": 22.5,
     *     "weathercode": 2
     *   }
     * }
     */
    private WeatherData parseWeatherData(String json, Location location)
            throws ApiException {
        try {
            /*double temperature = extractDouble(json, "\"temperature_2m\":");
            int weatherCode;
            if (json.contains("\"weather_code\":")) {
                weatherCode = (int) extractDouble(json, "\"weather_code\":");
            } else {
                weatherCode = (int) extractDouble(json, "\"weathercode\":");
            }
            String description = WeatherCodeMapper.toDescription(weatherCode);

            return new WeatherData(
                    location.name(),
                    temperature,
                    description,
                    location.latitude(),
                    location.longitude()
            );*/
            // Paso 1: aislar el bloque "current": { ... }
            String currentBlock = extractBlock(json, "\"current\":");

            // Paso 2: leer temperatura y código de clima SOLO dentro de ese bloque
            double temperature = extractDouble(currentBlock, "\"temperature_2m\":");

            // Soportamos ambas variantes del nombre del campo
            int weatherCode;
            /*if (currentBlock.contains("\"weather_code\":")) {
                weatherCode = (int) extractDouble(currentBlock, "\"weather_code\":");
            } else {
                weatherCode = (int) extractDouble(currentBlock, "\"weathercode\":");
            }*/
            weatherCode = extractWeatherCode(currentBlock);


            String description = WeatherCodeMapper.toDescription(weatherCode);

            return new WeatherData(
                    location.name(),
                    temperature,
                    description,
                    location.latitude(),
                    location.longitude()
            );
        } catch (Exception e) {
            LOGGER.warning("No se pudo parsear la respuesta de Open-Meteo: " + e.getMessage());
            LOGGER.fine("JSON recibido:\n" + json);
            throw new ApiException("No se pudo procesar la respuesta del clima: " + e.getMessage());
        }
    }
    /**
     * Extrae el contenido del primer objeto JSON que sigue a una clave dada.
     *
     * Ejemplo: extractBlock(json, "\"current\":") sobre
     *   { "current_units":{...}, "current":{"temperature_2m":18.1} }
     * devuelve: {"temperature_2m":18.1}
     *
     * Funciona contando llaves de apertura y cierre para encontrar
     * el final correcto del objeto, sin importar cuántos campos tenga.
     */
    private String extractBlock(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) throw new IllegalArgumentException("Bloque no encontrado: " + key);

        // Avanzar hasta la llave de apertura '{'
        int start = json.indexOf('{', keyIdx + key.length());
        if (start == -1) throw new IllegalArgumentException("Objeto JSON no encontrado tras: " + key);

        // Recorrer el JSON contando llaves para encontrar el cierre correcto
        int depth = 0;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if      (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { end++; break; }
            }
            end++;
        }
        return json.substring(start, end);
    }
    // ---- Helper de parseo manual -------------------------------------------

    private double extractDouble(String json, String key) {
        int idx = json.indexOf(key);
        if (idx == -1) throw new IllegalArgumentException("Clave no encontrada: " + key);
        int start = idx + key.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return Double.parseDouble(json.substring(start, end));
    }

    // ---- Excepciones propias del cliente -----------------------------------

    public static class ApiException extends Exception {
        public ApiException(String message) { super(message); }
    }

    public static class NetworkException extends Exception {
        public NetworkException(String message, Throwable cause) { super(message, cause); }
    }

    // -----------------------------------------------------------------------
    // ✅ Mejora — un solo método que prueba en orden y evita la doble búsqueda
    private int extractWeatherCode(String block) {
        // Intentar primero el nombre moderno, luego el antiguo
        for (String key : new String[]{"\"weather_code\":", "\"weathercode\":"}) {
            if (block.contains(key)) {
                return (int) extractDouble(block, key);
            }
        }
        throw new IllegalArgumentException("No se encontró el código de clima en la respuesta.");
    }
}