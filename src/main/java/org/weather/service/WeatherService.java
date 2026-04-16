package org.weather.service;

import org.weather.api.GeocodingClient;
import org.weather.api.GeocodingClient.CityNotFoundException;
import org.weather.api.GeocodingClient.ApiException;
import org.weather.api.GeocodingClient.NetworkException;
import org.weather.api.OpenMeteoClient;
import org.weather.cache.WeatherCache;
import org.weather.model.Location;
import org.weather.model.WeatherData;

import java.time.Duration;
import java.util.Optional;

/**
 * 📁 service/WeatherService.java
 *
 * Función principal de la aplicación.
 *
 * ─── FLUJO CON CACHÉ ────────────────────────────────────────────────────────
 *
 *   getWeatherByCity("Bogotá")
 *          │
 *          ▼
 *   ¿Está en caché y es fresco?
 *      │              │
 *     SÍ             NO
 *      │              │
 *      │              ▼
 *      │       GeocodingClient.getLocation()   → coordenadas
 *      │              │
 *      │              ▼
 *      │       OpenMeteoClient.getWeather()    → datos del clima
 *      │              │
 *      │              ▼
 *      │       cache.put()                     → guardar para próxima vez
 *      │              │
 *      └──────────────┘
 *                     │
 *                     ▼
 *             WeatherResult (éxito o error)
 */
public class WeatherService {

    private final GeocodingClient geocodingClient;
    private final OpenMeteoClient weatherClient;
    private final WeatherCache    cache;

    // ── Constructores ────────────────────────────────────────────────────────

    /** Constructor para uso normal — caché de 1 hora predeterminado. */
    public WeatherService() {
        this.geocodingClient = new GeocodingClient();
        this.weatherClient   = new OpenMeteoClient();
        this.cache           = new WeatherCache();
    }

    /**
     * Constructor con TTL personalizado.
     * Útil si quieres un caché de 30 minutos o 2 horas.
     *
     * <pre>
     *   // Caché de 30 minutos
     *   WeatherService service = new WeatherService(Duration.ofMinutes(30));
     * </pre>
     *
     * @param cacheTtl Duración del caché. Ejemplo: Duration.ofMinutes(30)
     */
    public WeatherService(Duration cacheTtl) {
        this.geocodingClient = new GeocodingClient();
        this.weatherClient   = new OpenMeteoClient();
        this.cache           = new WeatherCache(cacheTtl);
    }

    /** Constructor para tests — permite inyectar mocks y caché personalizado. */
    WeatherService(GeocodingClient geocodingClient,
                   OpenMeteoClient weatherClient,
                   WeatherCache cache) {
        this.geocodingClient = geocodingClient;
        this.weatherClient   = weatherClient;
        this.cache           = cache;
    }

    /** Constructor para tests sin caché (compatibilidad con tests existentes). */
    public WeatherService(GeocodingClient geocodingClient, OpenMeteoClient weatherClient) {
        this(geocodingClient, weatherClient, new WeatherCache());
    }

    // ── Método principal ─────────────────────────────────────────────────────

    /**
     * Obtiene los datos meteorológicos actuales para una ciudad.
     *
     * En la primera consulta llama a las APIs externas y guarda
     * el resultado en caché. Las consultas siguientes para la misma
     * ciudad dentro de la hora siguiente son instantáneas.
     *
     * @param cityName Nombre de la ciudad en cualquier idioma
     * @return WeatherResult — siempre devuelve algo, nunca lanza excepción
     */
    public WeatherResult getWeatherByCity(String cityName) {

        // ── Paso 1: Validar entrada ──────────────────────────────────────────
        if (cityName == null || cityName.isBlank()) {
            return WeatherResult.failure(
                    "El nombre de la ciudad no puede estar vacío. " +
                            "Ejemplo: \"Bogotá\", \"Madrid\", \"Tokyo\"");
        }

        // ── Paso 2: Revisar caché ────────────────────────────────────────────
        // Si ya consultamos esta ciudad hace menos de 1 hora, devolver
        // el resultado guardado sin llamar a ninguna API.
        Optional<WeatherData> cached = cache.get(cityName);
        if (cached.isPresent()) {
            return WeatherResult.successFromCache(cached.get());
        }

        // ── Paso 3: Obtener coordenadas (API externa) ────────────────────────
        Location location;
        try {
            location = geocodingClient.getLocation(cityName);
        } catch (CityNotFoundException e) {
            return WeatherResult.failure(e.getMessage());
        } catch (ApiException e) {
            return WeatherResult.failure(
                    "Error al consultar la API de geocodificación: " + e.getMessage());
        } catch (NetworkException e) {
            return WeatherResult.failure(
                    "Problema de red al buscar la ciudad. " +
                            "Verifica tu conexión a internet. Detalle: " + e.getMessage());
        }

        // ── Paso 4: Obtener clima (API externa) ──────────────────────────────
        WeatherData weatherData;
        try {
            weatherData = weatherClient.getWeather(location);
        } catch (OpenMeteoClient.ApiException e) {
            return WeatherResult.failure(
                    "Error al consultar Open-Meteo: " + e.getMessage());
        } catch (OpenMeteoClient.NetworkException e) {
            return WeatherResult.failure(
                    "Problema de red al obtener el clima. " +
                            "Verifica tu conexión a internet. Detalle: " + e.getMessage());
        }

        // ── Paso 5: Guardar en caché y devolver ─────────────────────────────
        // La próxima vez que se consulte esta ciudad, se usará este dato
        // guardado en lugar de llamar a la API de nuevo.
        cache.put(cityName, weatherData);
        return WeatherResult.success(weatherData);
    }

    /**
     * Fuerza la actualización del clima de una ciudad ignorando el caché.
     * Útil cuando el usuario quiere datos frescos explícitamente.
     *
     * @param cityName Nombre de la ciudad
     * @return WeatherResult con datos actualizados directamente de la API
     */
    public WeatherResult refreshWeather(String cityName) {
        cache.invalidate(cityName);
        return getWeatherByCity(cityName);
    }

    /**
     * Indica si una ciudad tiene datos válidos en caché en este momento.
     *
     * @param cityName Nombre de la ciudad
     * @return true si hay datos en caché que aún no expiraron
     */
    public boolean isCached(String cityName) {
        return cache.contains(cityName);
    }

    // ── Clase resultado ──────────────────────────────────────────────────────

    /**
     * Contenedor de resultado que siempre es seguro de usar.
     * Ahora también indica si el dato vino del caché o de la API.
     */
    public static class WeatherResult {

        private final WeatherData data;
        private final String      errorMessage;
        private final boolean     success;
        private final boolean     fromCache;    // ← NUEVO: origen del dato

        private WeatherResult(WeatherData data, String errorMessage,
                              boolean success, boolean fromCache) {
            this.data         = data;
            this.errorMessage = errorMessage;
            this.success      = success;
            this.fromCache    = fromCache;
        }

        /** Resultado exitoso con datos frescos de la API. */
        public static WeatherResult success(WeatherData data) {
            return new WeatherResult(data, null, true, false);
        }

        /** Resultado exitoso con datos del caché. */
        public static WeatherResult successFromCache(WeatherData data) {
            return new WeatherResult(data, null, true, true);
        }

        /** Resultado fallido con mensaje descriptivo. */
        public static WeatherResult failure(String errorMessage) {
            return new WeatherResult(null, errorMessage, false, false);
        }

        public boolean   isSuccess()       { return success; }
        public WeatherData getData()       { return data; }
        public String    getErrorMessage() { return errorMessage; }

        /**
         * Indica si el dato vino del caché (true) o de la API (false).
         * Útil para mostrar al usuario cuán reciente es la información.
         */
        public boolean   isFromCache()     { return fromCache; }

        /**
         * Devuelve siempre un JSON válido, sea éxito o error.
         * Incluye el campo "fromCache" para transparencia.
         */
        public String toJson() {
            if (success) {
                return data.toJson();
            } else {
                return String.format("""
                        {
                          "error": true,
                          "message": "%s"
                        }""", errorMessage.replace("\"", "'"));
            }
        }
    }
}

/*package org.weather.service;

import org.weather.api.GeocodingClient;
import org.weather.api.OpenMeteoClient;
import org.weather.cache.WeatherCache;
import org.weather.model.Location;
import org.weather.model.WeatherData;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * 📁 service/WeatherService.java
 *
 * ─────────────────────────────────────────────────────────────
 *  FUNCIÓN PRINCIPAL PARA DESARROLLADORES PRINCIPIANTES
 * ─────────────────────────────────────────────────────────────
 *
 * Recibe el nombre de una ciudad y devuelve un objeto WeatherData
 * (equivalente a un JSON) con:
 *   - cityName    → nombre de la ciudad
 *   - temperature → temperatura en °C
 *   - description → descripción del clima en español
 *   - latitude    → latitud usada
 *   - longitude   → longitud usada
 *
 * FLUJO INTERNO:
 *   1. Valida la entrada
 *   2. Llama a GeocodingClient → obtiene coordenadas
 *   3. Llama a OpenMeteoClient → obtiene clima con esas coordenadas
 *   4. Devuelve un WeatherData con todo combinado
 *
 * ERRORES MANEJADOS:
 *   - Ciudad inválida o no encontrada  → WeatherResult.failure(mensaje)
 *   - Error de la API (código HTTP)    → WeatherResult.failure(mensaje)
 *   - Problema de red / sin internet   → WeatherResult.failure(mensaje)

public class WeatherService {

    private final GeocodingClient geocodingClient;
    private final OpenMeteoClient weatherClient;
    private final WeatherCache cache;

    public WeatherService() {
        HttpClient sharedClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.geocodingClient = new GeocodingClient(sharedClient);
        this.weatherClient   = new OpenMeteoClient(sharedClient);
        this.cache           = new WeatherCache();
    }

    /**
     * Constructor con TTL personalizado.
     * Útil si quieres un caché de 30 minutos o 2 horas.
     *
     * <pre>
     *   // Caché de 30 minutos
     *   WeatherService service = new WeatherService(Duration.ofMinutes(30));
     * </pre>
     *
     * @param cacheTtl Duración del caché. Ejemplo: Duration.ofMinutes(30)

    public WeatherService(Duration cacheTtl) {
        this.geocodingClient = new GeocodingClient();
        this.weatherClient   = new OpenMeteoClient();
        this.cache           = new WeatherCache(cacheTtl);
    }



    // Para tests — recibe los mocks desde afuera
    public WeatherService(GeocodingClient geocodingClient, OpenMeteoClient weatherClient) {
        //this.geocodingClient = geocodingClient;
        //this.weatherClient   = weatherClient;
        this(geocodingClient, weatherClient, new WeatherCache());

    }



    // ─────────────────────────────────────────────────────────────
    //  MÉTODO PRINCIPAL — usa este desde tu código
    // ─────────────────────────────────────────────────────────────

    /**
     * Obtiene los datos meteorológicos actuales para una ciudad.
     *
     * Ejemplo de uso:
     * <pre>
     *   WeatherService service = new WeatherService();
     *   WeatherResult result = service.getWeatherByCity("Medellín");
     *
     *   if (result.isSuccess()) {
     *       System.out.println(result.getData().toJson());
     *   } else {
     *       System.out.println("Error: " + result.getErrorMessage());
     *   }
     * </pre>
     *
     * @param cityName Nombre de la ciudad en cualquier idioma
     * @return WeatherResult — siempre devuelve algo, nunca lanza excepción

    public WeatherResult getWeatherByCity(String cityName) {

        // ── Paso 1: Validar entrada ──────────────────────────────
        if (cityName == null || cityName.isBlank()) {
            return WeatherResult.failure(
                    "El nombre de la ciudad no puede estar vacío. " +
                            "Ejemplo: \"Bogotá\", \"Madrid\", \"Tokyo\"");
        }

        // ── Paso 2: Revisar caché ────────────────────────────────────────────
        // Si ya consultamos esta ciudad hace menos de 1 hora, devolver
        // el resultado guardado sin llamar a ninguna API.
        Optional<WeatherData> cached = cache.get(cityName);
        if (cached.isPresent()) {
            return WeatherResult.successFromCache(cached.get());
        }


        // ── Paso 2: Obtener coordenadas ──────────────────────────
        Location location;
        try {
            // ✅ Mejora — validación defensiva antes de usarlo
            location = geocodingClient.getLocation(cityName);

            // Defensa contra implementaciones incorrectas del cliente
            if (location == null) {
                return WeatherResult.failure(
                        "Error interno: la búsqueda de ciudad no devolvió resultado.");
            }
        } catch (GeocodingClient.CityNotFoundException e) {
            return WeatherResult.failure(e.getMessage());
        } catch (GeocodingClient.ApiException e) {
            return WeatherResult.failure(
                    "Error al consultar la API de geocodificación: " + e.getMessage());
        } catch (GeocodingClient.NetworkException e) {
            throw new RuntimeException(e);
        }


        // ── Paso 3: Obtener datos del clima ──────────────────────
        WeatherData weatherData;
        try {
            weatherData = weatherClient.getWeather(location);
        } catch (OpenMeteoClient.ApiException e) {
            return WeatherResult.failure(
                    "Error al consultar Open-Meteo: " + e.getMessage());
        } catch (OpenMeteoClient.NetworkException e) {
            return WeatherResult.failure(
                    "Problema de red al obtener el clima. " +
                            "Verifica tu conexión a internet. Detalle: " + e.getMessage());
        }

        // ── Paso 4: Devolver resultado exitoso ───────────────────
        // ── Paso 5: Guardar en caché y devolver ─────────────────────────────
        // La próxima vez que se consulte esta ciudad, se usará este dato
        // guardado en lugar de llamar a la API de nuevo.
        cache.put(cityName, weatherData);
        return WeatherResult.success(weatherData);
    }

    // ─────────────────────────────────────────────────────────────
    //  CLASE RESULTADO — envuelve éxito o error
    // ─────────────────────────────────────────────────────────────

    /**
     * Contenedor de resultado que siempre es seguro de usar.
     * Evita que el código que llama tenga que manejar excepciones.

    public static class WeatherResult {

        private final WeatherData data;          // null si hubo error
        private final String      errorMessage;  // null si fue exitoso
        private final boolean     success;

        private WeatherResult(WeatherData data, String errorMessage, boolean success) {
            this.data         = data;
            this.errorMessage = errorMessage;
            this.success      = success;
        }

        /** Crea un resultado exitoso con datos del clima.
        public static WeatherResult success(WeatherData data) {
            return new WeatherResult(data, null, true);
        }

        /** Crea un resultado de error con mensaje descriptivo.
        public static WeatherResult failure(String errorMessage) {
            return new WeatherResult(null, errorMessage, false);
        }

        /** @return true si la petición fue exitosa
        public boolean isSuccess() { return success; }

        /** @return Los datos del clima (solo si isSuccess() == true)
        public WeatherData getData() { return data; }

        /** @return Mensaje de error (solo si isSuccess() == false)
        public String getErrorMessage() { return errorMessage; }


        /**
         * Indica si el dato vino del caché (true) o de la API (false).
         * Útil para mostrar al usuario cuán reciente es la información.

        public boolean   isFromCache()     { return fromCache; }

        /**
         * Devuelve siempre un JSON válido, sea éxito o error.
         * Útil para APIs REST o para depuración.

        public String toJson() {
            if (success) {
                return data.toJson();
            } else {
                return String.format("""
                        {
                          "error": true,
                          "message": "%s"
                        }""", errorMessage.replace("\"", "'"));
            }
        }
    }
}*/