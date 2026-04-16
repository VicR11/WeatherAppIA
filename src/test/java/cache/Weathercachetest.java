package cache;


import org.weather.cache.WeatherCache;
import org.weather.model.WeatherData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📁 src/test/java/com/weather/cache/WeatherCacheTest.java
 *
 * Pruebas unitarias para WeatherCache.
 *
 * Para probar la expiración sin esperar 1 hora real, creamos
 * el caché con un TTL muy corto (1 milisegundo) y luego dormimos
 * el hilo brevemente para que expire.
 */
@DisplayName("WeatherCache — almacenamiento temporal de datos del clima")
class WeatherCacheTest {

    private WeatherCache cache;

    private static final WeatherData BOGOTA =
            new WeatherData("Bogotá", 18.5, "Parcialmente nublado", 4.6097, -74.0817);

    private static final WeatherData MEDELLIN =
            new WeatherData("Medellín", 22.5, "Cielo despejado", 6.2518, -75.5636);

    @BeforeEach
    void setUp() {
        cache = new WeatherCache();   // TTL de 1 hora (predeterminado)
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Constructor y configuración
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Constructor y configuración")
    class Construccion {

        @Test
        @DisplayName("Caché nuevo empieza vacío")
        void cachNuevo_estaVacio() {
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("TTL negativo lanza IllegalArgumentException")
        void ttlNegativo_lanzaExcepcion() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WeatherCache(Duration.ofSeconds(-1)));
        }

        @Test
        @DisplayName("TTL cero lanza IllegalArgumentException")
        void ttlCero_lanzaExcepcion() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WeatherCache(Duration.ZERO));
        }

        @Test
        @DisplayName("TTL null lanza IllegalArgumentException")
        void ttlNull_lanzaExcepcion() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WeatherCache(null));
        }

        @Test
        @DisplayName("TTL personalizado se acepta correctamente")
        void ttlPersonalizado_seAceptaCorrectamente() {
            assertDoesNotThrow(() -> new WeatherCache(Duration.ofMinutes(30)));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  put() y get() — operaciones básicas
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Almacenamiento y recuperación")
    class AlmacenamientoYRecuperacion {

        @Test
        @DisplayName("Dato guardado se puede recuperar")
        void datoGuardado_seRecupera() {
            cache.put("Bogotá", BOGOTA);
            Optional<WeatherData> result = cache.get("Bogotá");

            assertTrue(result.isPresent(), "Debe encontrarse el dato guardado");
            assertEquals(BOGOTA, result.get());
        }

        @Test
        @DisplayName("Ciudad no guardada devuelve Optional.empty()")
        void ciudadNoGuardada_devuelveEmpty() {
            Optional<WeatherData> result = cache.get("Tokyo");
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Guardar datos null lanza IllegalArgumentException")
        void datosNull_lanzaExcepcion() {
            assertThrows(IllegalArgumentException.class,
                    () -> cache.put("Bogotá", null));
        }

        @Test
        @DisplayName("Sobreescribir una entrada actualiza el dato")
        void sobreescribir_actualizaElDato() {
            cache.put("Bogotá", BOGOTA);
            WeatherData actualizado = new WeatherData("Bogotá", 20.0, "Nublado", 4.6097, -74.0817);
            cache.put("Bogotá", actualizado);

            Optional<WeatherData> result = cache.get("Bogotá");
            assertTrue(result.isPresent());
            assertEquals(20.0, result.get().temperature(), 0.01,
                    "Debe devolver el dato más reciente");
        }

        @Test
        @DisplayName("Múltiples ciudades se almacenan de forma independiente")
        void multiplesCiudades_sonIndependientes() {
            cache.put("Bogotá",   BOGOTA);
            cache.put("Medellín", MEDELLIN);

            assertEquals(BOGOTA,   cache.get("Bogotá").orElseThrow());
            assertEquals(MEDELLIN, cache.get("Medellín").orElseThrow());
            assertEquals(2, cache.size());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Normalización de claves
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Normalización del nombre de ciudad")
    class Normalizacion {

        @Test
        @DisplayName("Mayúsculas y minúsculas son la misma entrada")
        void mayusculasYMinusculas_mismaEntrada() {
            cache.put("bogotá", BOGOTA);

            assertAll(
                    () -> assertTrue(cache.get("bogotá").isPresent(),  "bogotá debe encontrarse"),
                    () -> assertTrue(cache.get("Bogotá").isPresent(),  "Bogotá debe encontrarse"),
                    () -> assertTrue(cache.get("BOGOTÁ").isPresent(),  "BOGOTÁ debe encontrarse"),
                    () -> assertTrue(cache.get("BOGotá").isPresent(),  "BOGotá debe encontrarse")
            );
        }

        @Test
        @DisplayName("Espacios al inicio y al final se ignoran")
        void espaciosExternos_seIgnoran() {
            cache.put("  Madrid  ", BOGOTA);

            assertTrue(cache.get("Madrid").isPresent(),
                    "Buscar sin espacios debe encontrar el dato guardado con espacios");
        }

        @Test
        @DisplayName("Ciudad con nombre null lanza IllegalArgumentException en get()")
        void nombreNull_lanzaExcepcionEnGet() {
            assertThrows(IllegalArgumentException.class, () -> cache.get(null));
        }

        @Test
        @DisplayName("Ciudad con nombre null lanza IllegalArgumentException en put()")
        void nombreNull_lanzaExcepcionEnPut() {
            assertThrows(IllegalArgumentException.class, () -> cache.put(null, BOGOTA));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Expiración (TTL)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Expiración de datos (TTL)")
    class Expiracion {

        @Test
        @DisplayName("Dato dentro del TTL es válido")
        void datoFresco_esValido() {
            // TTL de 1 hora — el dato guardado ahora mismo debe ser válido
            cache.put("Bogotá", BOGOTA);
            assertTrue(cache.get("Bogotá").isPresent(),
                    "Dato recién guardado debe estar disponible");
        }

        @Test
        @DisplayName("Dato expirado devuelve Optional.empty()")
        void datoExpirado_devuelveEmpty() throws InterruptedException {
            // Crear caché con TTL de 1 milisegundo para simular expiración
            WeatherCache cacheRapido = new WeatherCache(Duration.ofMillis(1));
            cacheRapido.put("Bogotá", BOGOTA);

            Thread.sleep(10);  // Esperar 10ms — el dato ya expiró

            assertFalse(cacheRapido.get("Bogotá").isPresent(),
                    "El dato expirado no debe devolverse");
        }

        @Test
        @DisplayName("Dato expirado se elimina automáticamente del caché")
        void datoExpirado_seEliminaAutomaticamente() throws InterruptedException {
            WeatherCache cacheRapido = new WeatherCache(Duration.ofMillis(1));
            cacheRapido.put("Bogotá", BOGOTA);

            assertEquals(1, cacheRapido.size(), "Debe haber 1 entrada antes de expirar");

            Thread.sleep(10);
            cacheRapido.get("Bogotá");   // este get() dispara la limpieza automática

            assertEquals(0, cacheRapido.size(),
                    "La entrada expirada debe haberse eliminado al consultarla");
        }

        @Test
        @DisplayName("ageOf() devuelve la edad del dato almacenado")
        void ageOf_devuelveLaEdadDelDato() throws InterruptedException {
            cache.put("Bogotá", BOGOTA);
            Thread.sleep(50);

            Optional<Duration> age = cache.ageOf("Bogotá");
            assertTrue(age.isPresent());
            assertTrue(age.get().toMillis() >= 50,
                    "La edad debe ser al menos 50ms");
        }

        @Test
        @DisplayName("ageOf() devuelve empty para ciudad no almacenada")
        void ageOf_devuelveEmptyParaCiudadNoAlmacenada() {
            assertFalse(cache.ageOf("CiudadFantasma").isPresent());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  contains(), invalidate(), clear(), cleanExpired()
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Operaciones de gestión del caché")
    class Gestion {

        @Test
        @DisplayName("contains() devuelve true para ciudad almacenada y válida")
        void contains_trueParaCiudadAlmacenada() {
            cache.put("Bogotá", BOGOTA);
            assertTrue(cache.contains("Bogotá"));
        }

        @Test
        @DisplayName("contains() devuelve false para ciudad no almacenada")
        void contains_falseParaCiudadNoAlmacenada() {
            assertFalse(cache.contains("Lima"));
        }

        @Test
        @DisplayName("invalidate() elimina una ciudad específica")
        void invalidate_eliminaCiudadEspecifica() {
            cache.put("Bogotá",   BOGOTA);
            cache.put("Medellín", MEDELLIN);

            cache.invalidate("Bogotá");

            assertAll(
                    () -> assertFalse(cache.contains("Bogotá"),   "Bogotá debe eliminarse"),
                    () -> assertTrue(cache.contains("Medellín"),   "Medellín debe mantenerse"),
                    () -> assertEquals(1, cache.size())
            );
        }

        @Test
        @DisplayName("clear() vacía todo el caché")
        void clear_vaciaTodoElCache() {
            cache.put("Bogotá",   BOGOTA);
            cache.put("Medellín", MEDELLIN);
            cache.clear();

            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("cleanExpired() elimina solo las entradas viejas")
        void cleanExpired_eliminaSoloLasEntradaViejas() throws InterruptedException {
            WeatherCache cacheRapido = new WeatherCache(Duration.ofMillis(50));

            cacheRapido.put("Bogotá",   BOGOTA);    // va a expirar
            Thread.sleep(60);
            cacheRapido.put("Medellín", MEDELLIN);  // fresco, no debe eliminarse

            int eliminadas = cacheRapido.cleanExpired();

            assertAll(
                    () -> assertEquals(1, eliminadas,                          "Debe eliminar 1 entrada"),
                    () -> assertFalse(cacheRapido.contains("Bogotá"),          "Bogotá debe eliminarse"),
                    () -> assertTrue(cacheRapido.get("Medellín").isPresent(),  "Medellín debe mantenerse")
            );
        }
    }
}
