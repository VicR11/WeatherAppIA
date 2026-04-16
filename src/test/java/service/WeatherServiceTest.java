package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weather.api.GeocodingClient;
import org.weather.model.Location;
import org.weather.model.WeatherData;
import org.weather.service.WeatherService;
import org.mockito.Mock;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 📁 src/test/java/com/weather/service/WeatherServiceTest.java
 *
 * Pruebas unitarias para WeatherService — la función principal del proyecto.
 *
 * ESTRATEGIA:
 * WeatherService depende de GeocodingClient y OpenMeteoClient.
 * Mockeamos ambos para aislar la lógica del service y probar
 * solo su comportamiento (orquestación + manejo de errores).
 *
 * Usamos @Nested para agrupar pruebas por escenario.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeatherService — función principal getWeatherByCity()")
class WeatherServiceTest {

    @Mock private GeocodingClient mockGeoClient;
    @Mock private org.weather.api.OpenMeteoClient mockWeatherClient;

    private WeatherService service;

    // ── Datos de prueba compartidos ──────────────────────────────────────────
    private static final Location LOCATION_MEDELLIN =
            new Location("Medellín", 6.25184, -75.56359);

    private static final WeatherData WEATHER_MEDELLIN =
            new WeatherData("Medellín", 22.5, "Parcialmente nublado", 6.25184, -75.56359);

    @BeforeEach
    void setUp() {
        // Inyectamos los mocks por constructor
        service = new WeatherService(mockGeoClient, mockWeatherClient);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ESCENARIO 1: Entrada inválida
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cuando la entrada es inválida")
    class EntradaInvalida {

        @ParameterizedTest(name = "entrada: \"{0}\"")
        @DisplayName("Devuelve failure para nombre vacío, null o solo espacios")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", "  \t  "})
        void entradaVaciaONull_devuelveFailure(String cityName) {
            WeatherService.WeatherResult result = service.getWeatherByCity(cityName);

            assertAll(
                    () -> assertFalse(result.isSuccess(), "Debe ser failure"),
                    () -> assertNull(result.getData(),    "getData() debe ser null en failure"),
                    () -> assertNotNull(result.getErrorMessage(), "Debe tener mensaje de error"),
                    () -> assertFalse(result.getErrorMessage().isBlank(), "Mensaje no debe estar vacío")
            );
        }

        @Test
        @DisplayName("El mensaje de error para entrada vacía es descriptivo")
        void entradaVacia_mensajeDescriptivo() {
            WeatherService.WeatherResult result = service.getWeatherByCity("");

            String msg = result.getErrorMessage();
            // El mensaje debe orientar al usuario, no ser técnico
            assertFalse(msg.isBlank());
            assertTrue(msg.length() > 10, "El mensaje debe tener más de 10 caracteres");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ESCENARIO 2: Flujo exitoso completo
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cuando todo funciona correctamente")
    class FlujoExitoso {

        @BeforeEach
        void configurarMocksExitosos() throws Exception {
            when(mockGeoClient.getLocation(anyString())).thenReturn(LOCATION_MEDELLIN);
            when(mockWeatherClient.getWeather(any(Location.class))).thenReturn(WEATHER_MEDELLIN);
        }

        @Test
        @DisplayName("Devuelve success con WeatherData completo")
        void ciudadValida_devuelveSuccess() {
            WeatherService.WeatherResult result = service.getWeatherByCity("Medellín");

            assertTrue(result.isSuccess(), "El resultado debe ser exitoso");
        }

        @Test
        @DisplayName("WeatherData tiene el nombre de ciudad correcto")
        void ciudadValida_dataTieneNombreCiudad() {
            WeatherService.WeatherResult result = service.getWeatherByCity("Medellín");

            assertEquals("Medellín", result.getData().cityName());
        }

        @Test
        @DisplayName("WeatherData tiene la temperatura correcta")
        void ciudadValida_dataTieneTemperatura() {
            WeatherService.WeatherResult result = service.getWeatherByCity("Medellín");

            assertEquals(22.5, result.getData().temperature(), 0.01);
        }

        @Test
        @DisplayName("WeatherData tiene descripción no vacía")
        void ciudadValida_dataTieneDescripcion() {
            WeatherService.WeatherResult result = service.getWeatherByCity("Medellín");

            assertFalse(result.getData().description().isBlank());
        }

        @Test
        @DisplayName("getErrorMessage() devuelve null en éxito")
        void exito_errorMessageEsNull() {
            WeatherService.WeatherResult result = service.getWeatherByCity("Medellín");

            assertNull(result.getErrorMessage(),
                    "No debe haber mensaje de error en un resultado exitoso");
        }

        @Test
        @DisplayName("toJson() produce JSON válido en éxito")
        void exito_toJsonEsJsonValido() {
            WeatherService.WeatherResult result = service.getWeatherByCity("Medellín");
            String json = result.toJson().trim();

            assertAll("JSON de éxito debe ser estructuralmente válido",
                    () -> assertTrue(json.startsWith("{"),          "Debe comenzar con '{'"),
                    () -> assertTrue(json.endsWith("}"),            "Debe terminar con '}'"),
                    () -> assertTrue(json.contains("cityName"),     "Debe contener cityName"),
                    () -> assertTrue(json.contains("temperature"),  "Debe contener temperature"),
                    () -> assertFalse(json.contains("\"error\""),   "No debe contener campo 'error'")
            );
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ESCENARIO 3: Ciudad no encontrada
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cuando la ciudad no existe")
    class CiudadNoEncontrada {

        @Test
        @DisplayName("CityNotFoundException del geocoder → devuelve failure")
        void cityNotFound_devuelveFailure() throws Exception {
            when(mockGeoClient.getLocation(anyString()))
                    .thenThrow(new GeocodingClient.CityNotFoundException("Ciudad no encontrada"));

            WeatherService.WeatherResult result = service.getWeatherByCity("xyzabc123");

            assertAll(
                    () -> assertFalse(result.isSuccess()),
                    () -> assertNotNull(result.getErrorMessage()),
                    () -> assertNull(result.getData())
            );
        }

        @Test
        @DisplayName("El mensaje de error menciona el problema de forma clara")
        void cityNotFound_mensajeClaroParaUsuario() throws Exception {
            when(mockGeoClient.getLocation(anyString()))
                    .thenThrow(new GeocodingClient.CityNotFoundException("Ciudad no encontrada: xyzabc"));

            WeatherService.WeatherResult result = service.getWeatherByCity("xyzabc");

            assertFalse(result.getErrorMessage().isBlank());
        }

        @Test
        @DisplayName("toJson() en failure contiene campo 'error: true'")
        void cityNotFound_toJsonContieneError() throws Exception {
            when(mockGeoClient.getLocation(anyString()))
                    .thenThrow(new GeocodingClient.CityNotFoundException("No encontrada"));

            WeatherService.WeatherResult result = service.getWeatherByCity("fantasmaville");
            String json = result.toJson();

            assertAll(
                    () -> assertTrue(json.contains("\"error\""),  "JSON debe tener campo 'error'"),
                    () -> assertTrue(json.contains("true"),        "El error debe ser true"),
                    () -> assertTrue(json.contains("\"message\""), "JSON debe tener campo 'message'")
            );
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ESCENARIO 4: Error de API en geocodificación
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cuando la API de geocodificación falla")
    class ErrorApiGeocodificacion {

        @Test
        @DisplayName("ApiException del geocoder → devuelve failure")
        void apiException_geocoder_devuelveFailure() throws Exception {
            when(mockGeoClient.getLocation(anyString()))
                    .thenThrow(new GeocodingClient.ApiException("HTTP 500"));

            WeatherService.WeatherResult result = service.getWeatherByCity("Madrid");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("NetworkException del geocoder → devuelve failure con aviso de red")
        void networkException_geocoder_devuelveFailureConAvisoRed() throws Exception {
            when(mockGeoClient.getLocation(anyString()))
                    .thenThrow(new GeocodingClient.NetworkException(
                            "Sin conexión", new Exception()));

            WeatherService.WeatherResult result = service.getWeatherByCity("Lima");

            assertAll(
                    () -> assertFalse(result.isSuccess()),
                    () -> assertTrue(
                            result.getErrorMessage().toLowerCase().contains("red") ||
                                    result.getErrorMessage().toLowerCase().contains("conexión") ||
                                    result.getErrorMessage().toLowerCase().contains("internet"),
                            "El mensaje debe orientar al usuario sobre el problema de red"
                    )
            );
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ESCENARIO 5: Error de API en Open-Meteo (geocoder OK, clima falla)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cuando Open-Meteo falla (geocodificación fue exitosa)")
    class ErrorApiClima {

        @BeforeEach
        void geocoderFunciona() throws Exception {
            // La geocodificación siempre funciona en este bloque
            when(mockGeoClient.getLocation(anyString())).thenReturn(LOCATION_MEDELLIN);
        }

        @Test
        @DisplayName("ApiException de Open-Meteo → devuelve failure")
        void apiException_clima_devuelveFailure() throws Exception {
            when(mockWeatherClient.getWeather(any(Location.class)))
                    .thenThrow(new org.weather.api.OpenMeteoClient.ApiException("HTTP 503"));

            WeatherService.WeatherResult result = service.getWeatherByCity("Medellín");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("NetworkException de Open-Meteo → devuelve failure")
        void networkException_clima_devuelveFailure() throws Exception {
            when(mockWeatherClient.getWeather(any(Location.class)))
                    .thenThrow(new org.weather.api.OpenMeteoClient.NetworkException(
                            "Timeout", new Exception()));

            WeatherService.WeatherResult result = service.getWeatherByCity("Bogotá");

            assertAll(
                    () -> assertFalse(result.isSuccess()),
                    () -> assertNull(result.getData())
            );
        }

        @Test
        @DisplayName("Fallo en clima no devuelve datos parciales")
        void falloEnClima_noDatosparciales() throws Exception {
            when(mockWeatherClient.getWeather(any(Location.class)))
                    .thenThrow(new org.weather.api.OpenMeteoClient.ApiException("falla"));

            WeatherService.WeatherResult result = service.getWeatherByCity("Cali");

            // En un fallo, getData() NUNCA debe devolver datos parciales
            assertNull(result.getData(),
                    "En un fallo, getData() debe ser null — nunca datos incompletos");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ESCENARIO 6: Propiedades del WeatherResult
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Propiedades de WeatherResult")
    class PropiedadesWeatherResult {

        @Test
        @DisplayName("WeatherResult.success() crea resultado exitoso")
        void success_factory_creaResultadoExitoso() {
            WeatherService.WeatherResult result =
                    WeatherService.WeatherResult.success(WEATHER_MEDELLIN);

            assertAll(
                    () -> assertTrue(result.isSuccess()),
                    () -> assertNotNull(result.getData()),
                    () -> assertNull(result.getErrorMessage())
            );
        }

        @Test
        @DisplayName("WeatherResult.failure() crea resultado fallido")
        void failure_factory_creaResultadoFallido() {
            WeatherService.WeatherResult result =
                    WeatherService.WeatherResult.failure("Error de prueba");

            assertAll(
                    () -> assertFalse(result.isSuccess()),
                    () -> assertNull(result.getData()),
                    () -> assertEquals("Error de prueba", result.getErrorMessage())
            );
        }

        @Test
        @DisplayName("getWeatherByCity() nunca devuelve null")
        void getWeatherByCity_nuncaDevuelveNull() {
            // Incluso con input inválido, el método nunca debe retornar null
            WeatherService.WeatherResult result = service.getWeatherByCity(null);
            assertNotNull(result, "El método nunca debe devolver null");
        }
    }
}

/*
 * NOTA: WeatherService necesita un segundo constructor para pruebas:
 *
 *   // Constructor para pruebas (inyección de dependencias)
 *   WeatherService(GeocodingClient geocodingClient, OpenMeteoClient weatherClient) {
 *       this.geocodingClient = geocodingClient;
 *       this.weatherClient   = weatherClient;
 *   }
 */
