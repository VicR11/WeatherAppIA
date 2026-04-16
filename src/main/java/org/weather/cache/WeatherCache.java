package org.weather.cache;

import org.weather.model.WeatherData;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 📁 cache/WeatherCache.java
 *
 * Caché en memoria para datos meteorológicos.
 *
 * ─── ¿POR QUÉ ES NECESARIA? ────────────────────────────────────────────────
 * Sin caché, cada consulta de la misma ciudad hace DOS llamadas HTTP:
 *   1. Geocoding API  → ciudad a coordenadas
 *   2. Open-Meteo     → coordenadas a clima
 *
 * El clima de una ciudad no cambia significativamente en minutos.
 * Con caché de 1 hora, si el usuario consulta "Bogotá" 10 veces
 * seguidas, solo la primera llama a la API. Las otras 9 son instantáneas.
 *
 * ─── DECISIONES DE DISEÑO ───────────────────────────────────────────────────
 *
 * ConcurrentHashMap en vez de HashMap:
 *   En una aplicación real pueden llegar varias consultas al mismo tiempo
 *   (hilos distintos). ConcurrentHashMap garantiza que eso no corrompa
 *   los datos. HashMap normal no es thread-safe.
 *
 * Clave normalizada (minúsculas, sin espacios extra):
 *   "Bogotá", "bogotá" y "BOGOTÁ" deben ser la misma entrada de caché.
 *
 * TTL configurable:
 *   Se puede crear con duración distinta a 1 hora para pruebas o
 *   escenarios donde el clima cambia rápido (zonas tropicales, etc).
 *
 * Expiración lazy (por demanda):
 *   Las entradas viejas no se eliminan en background. Se detectan y
 *   descartan cuando se consultan. Simple y sin hilos adicionales.
 *   El método cleanExpired() existe si quieres limpiar manualmente.
 */
public class WeatherCache {

    // ── Entrada interna del caché ────────────────────────────────────────────

    /**
     * Representa un dato almacenado en caché junto con su timestamp.
     * Es privado — solo WeatherCache conoce esta estructura.
     */
    private static class CacheEntry {
        final WeatherData data;      // El dato del clima almacenado
        final Instant     storedAt;  // Cuándo se guardó

        CacheEntry(WeatherData data) {
            this.data     = data;
            this.storedAt = Instant.now();
        }

        /**
         * Verifica si esta entrada todavía es válida.
         *
         * @param ttl Duración máxima de vida de la entrada
         * @return true si el dato aún está dentro del tiempo permitido
         */
        boolean isValid(Duration ttl) {
            return Duration.between(storedAt, Instant.now()).compareTo(ttl) < 0;
        }

        /**
         * Cuánto tiempo lleva almacenado este dato.
         */
        Duration age() {
            return Duration.between(storedAt, Instant.now());
        }
    }

    // ── Estado del caché ─────────────────────────────────────────────────────

    /** Duración predeterminada: 1 hora */
    public static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final Map<String, CacheEntry> store;   // Almacén principal
    private final Duration                ttl;     // Tiempo de vida configurado

    // ── Constructores ────────────────────────────────────────────────────────

    /**
     * Crea un caché con tiempo de vida de 1 hora (valor predeterminado).
     */
    public WeatherCache() {
        this(DEFAULT_TTL);
    }

    /**
     * Crea un caché con tiempo de vida personalizado.
     * Útil para pruebas o ajustes de rendimiento.
     *
     * <pre>
     *   // Para pruebas: caché que expira en 5 segundos
     *   WeatherCache cache = new WeatherCache(Duration.ofSeconds(5));
     *
     *   // Para producción: caché de 2 horas
     *   WeatherCache cache = new WeatherCache(Duration.ofHours(2));
     * </pre>
     *
     * @param ttl Duración máxima de vida de cada entrada. No puede ser null
     *            ni negativa.
     */
    public WeatherCache(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException(
                    "El TTL debe ser una duración positiva. Ejemplo: Duration.ofHours(1)");
        }
        this.ttl   = ttl;
        this.store = new ConcurrentHashMap<>();
    }

    // ── API pública ──────────────────────────────────────────────────────────

    /**
     * Busca datos del clima en caché para una ciudad dada.
     *
     * Devuelve los datos solo si existen Y todavía son válidos (dentro del TTL).
     * Si la entrada existe pero expiró, la elimina automáticamente y devuelve empty.
     *
     * <pre>
     *   Optional<WeatherData> cached = cache.get("Bogotá");
     *   if (cached.isPresent()) {
     *       return cached.get();   // datos frescos del caché
     *   } else {
     *       // llamar a la API...
     *   }
     * </pre>
     *
     * @param cityName Nombre de la ciudad (no sensible a mayúsculas/minúsculas)
     * @return Optional con los datos si están en caché y son válidos,
     *         Optional.empty() si no existen o expiraron
     */
    public Optional<WeatherData> get(String cityName) {
        String key   = normalizeKey(cityName);
        CacheEntry entry = store.get(key);

        if (entry == null) {
            return Optional.empty();          // nunca se consultó esta ciudad
        }

        if (!entry.isValid(ttl)) {
            store.remove(key);               // expiró → limpiar y reportar miss
            return Optional.empty();
        }

        return Optional.of(entry.data);      // hit: dato fresco encontrado
    }

    /**
     * Almacena datos del clima en caché para una ciudad.
     *
     * Si ya existía una entrada para esa ciudad, la sobreescribe con
     * los datos nuevos y reinicia el contador de tiempo.
     *
     * @param cityName    Nombre de la ciudad (no sensible a mayúsculas)
     * @param weatherData Datos del clima a guardar. No puede ser null.
     * @throws IllegalArgumentException si weatherData es null
     */
    public void put(String cityName, WeatherData weatherData) {
        if (weatherData == null) {
            throw new IllegalArgumentException(
                    "No se pueden almacenar datos null en el caché.");
        }
        String key = normalizeKey(cityName);
        store.put(key, new CacheEntry(weatherData));
    }

    /**
     * Verifica si una ciudad tiene datos válidos en caché (sin recuperarlos).
     *
     * @param cityName Nombre de la ciudad
     * @return true si hay datos frescos disponibles para esa ciudad
     */
    public boolean contains(String cityName) {
        return get(cityName).isPresent();
    }

    /**
     * Elimina los datos de una ciudad específica del caché.
     * Útil para forzar una actualización.
     *
     * @param cityName Nombre de la ciudad a invalidar
     */
    public void invalidate(String cityName) {
        store.remove(normalizeKey(cityName));
    }

    /**
     * Vacía completamente el caché.
     * Útil en pruebas o cuando se necesita forzar actualización global.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Elimina todas las entradas que ya expiraron.
     *
     * No es necesario llamar a este método para que el caché funcione —
     * las entradas expiradas se detectan automáticamente en {@link #get}.
     * Úsalo si quieres liberar memoria explícitamente en aplicaciones
     * que manejan muchas ciudades.
     *
     * @return Cantidad de entradas eliminadas
     */
    public int cleanExpired() {
        int[] removed = {0};
        store.entrySet().removeIf(entry -> {
            if (!entry.getValue().isValid(ttl)) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }

    /**
     * Información de estado del caché (para monitoreo o depuración).
     *
     * @return Número total de entradas almacenadas (válidas + expiradas)
     */
    public int size() {
        return store.size();
    }

    /**
     * Cuánto tiempo lleva almacenado el dato de una ciudad.
     *
     * @param cityName Nombre de la ciudad
     * @return Optional con la edad del dato, o empty si no está en caché
     */
    public Optional<Duration> ageOf(String cityName) {
        CacheEntry entry = store.get(normalizeKey(cityName));
        if (entry == null) return Optional.empty();
        return Optional.of(entry.age());
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    /**
     * Normaliza el nombre de ciudad para usarlo como clave.
     *
     * Garantiza que "Bogotá", "bogotá", "BOGOTÁ" y "  Bogotá  "
     * sean tratadas como la misma entrada de caché.
     *
     * @param cityName Nombre de ciudad en cualquier formato
     * @return Clave normalizada en minúsculas sin espacios extra
     */
    private String normalizeKey(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre de ciudad no puede ser null o vacío.");
        }
        return cityName.trim().toLowerCase();
    }
}