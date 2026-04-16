package api;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weather.model.Location;
import org.weather.model.WeatherData;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 📁 src/test/java/com/weather/api/OpenMeteoClientTest.java
 *
 * Pruebas unitarias para OpenMeteoClient.
 * Simula las respuestas HTTP de Open-Meteo para verificar que
 * los datos del clima se parsean correctamente.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenMeteoClient — obtención de datos meteorológicos")
class OpenMeteoClientTest {

    @Mock private HttpClient mockHttpClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private HttpResponse mockHttpResponse;

    private org.weather.api.OpenMeteoClient client;

    // Location de prueba (Medellín)
    private static final Location MEDELLIN = new Location("Medellín", 6.25184, -75.56359);

    // Respuesta real de Open-Meteo (simplificada)
    private static final String JSON_CLIMA_DESPEJADO = """
            {
              "latitude": 6.25,
              "longitude": -75.5625,
              "timezone": "America/Bogota",
              "current": {
                "time": "2024-01-15T14:00",
                "temperature_2m": 22.5,
                "weathercode": 0
              }
            }
            """;

    private static final String JSON_CLIMA_TORMENTA = """
            {
              "current": {
                "temperature_2m": 18.0,
                "weathercode": 95
              }
            }
            """;

    private static final String JSON_CLIMA_NEGATIVO = """
            {
              "current": {
                "temperature_2m": -12.3,
                "weathercode": 73
              }
            }
            """;

    @BeforeEach
    void setUp() {
        client = new org.weather.api.OpenMeteoClient(mockHttpClient);

    }

    // ── Tests: respuesta exitosa ─────────────────────────────────────────────

    @Test
    @DisplayName("Respuesta exitosa devuelve WeatherData con todos los campos")
    @SuppressWarnings("unchecked")
    void respuestaExitosa_devuelveWeatherDataCompleto() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_CLIMA_DESPEJADO);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        WeatherData result = client.getWeather(MEDELLIN);

        assertAll("WeatherData debe tener todos los campos correctos",
                () -> assertNotNull(result),
                () -> assertEquals("Medellín", result.cityName()),
                () -> assertEquals(22.5, result.temperature(), 0.01),
                () -> assertNotNull(result.description()),
                () -> assertFalse(result.description().isBlank()),
                () -> assertEquals(6.25184,   result.latitude(),  0.0001),
                () -> assertEquals(-75.56359, result.longitude(), 0.0001)
        );
    }

    @Test
    @DisplayName("Código 0 produce descripción de cielo despejado")
    @SuppressWarnings("unchecked")
    void codigo0_produceCieloDespejado() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_CLIMA_DESPEJADO);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        WeatherData result = client.getWeather(MEDELLIN);

        assertEquals("Cielo despejado", result.description());
    }

    @Test
    @DisplayName("Código 95 (tormenta) produce descripción correcta")
    @SuppressWarnings("unchecked")
    void codigoTormenta_producDescripcionCorrecta() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_CLIMA_TORMENTA);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        Location oslo = new Location("Oslo", 59.91, 10.75);
        WeatherData result = client.getWeather(oslo);

        assertTrue(result.description().contains("Tormenta"),
                "La descripción para código 95 debe contener 'Tormenta'");
    }

    @Test
    @DisplayName("Temperatura negativa se parsea correctamente")
    @SuppressWarnings("unchecked")
    void temperaturaNegativa_parseoCorrector() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_CLIMA_NEGATIVO);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        Location reykjavik = new Location("Reykjavik", 64.13, -21.89);
        WeatherData result = client.getWeather(reykjavik);

        assertEquals(-12.3, result.temperature(), 0.01,
                "La temperatura negativa debe parsearse correctamente");
    }

    // ── Tests: errores HTTP ──────────────────────────────────────────────────

    @Test
    @DisplayName("HTTP 500 lanza ApiException con código en el mensaje")
    @SuppressWarnings("unchecked")
    void http500_lanzaApiExceptionConCodigo() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(500);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        org.weather.api.OpenMeteoClient.ApiException ex = assertThrows(
                org.weather.api.OpenMeteoClient.ApiException.class,
                () -> client.getWeather(MEDELLIN)
        );

        assertTrue(ex.getMessage().contains("500"),
                "El mensaje debe mencionar el código HTTP 500");
    }

    @Test
    @DisplayName("HTTP 503 (servicio no disponible) lanza ApiException")
    @SuppressWarnings("unchecked")
    void http503_lanzaApiException() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(503);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        assertThrows(org.weather.api.OpenMeteoClient.ApiException.class,
                () -> client.getWeather(MEDELLIN));
    }

    // ── Tests: errores de red ────────────────────────────────────────────────

    @Test
    @DisplayName("ConnectException lanza NetworkException")
    void connectException_lanzaNetworkException() throws Exception {
        when(mockHttpClient.send(any(), any()))
                .thenThrow(new java.net.ConnectException("timeout"));

        assertThrows(org.weather.api.OpenMeteoClient.NetworkException.class,
                () -> client.getWeather(MEDELLIN));
    }

    @Test
    @DisplayName("InterruptedException lanza NetworkException")
    void interruptedException_lanzaNetworkException() throws Exception {
        when(mockHttpClient.send(any(), any()))
                .thenThrow(new InterruptedException("interrumpido"));

        assertThrows(org.weather.api.OpenMeteoClient.NetworkException.class,
                () -> client.getWeather(MEDELLIN));
    }

    // ── Tests: integridad del objeto resultado ───────────────────────────────

    @Test
    @DisplayName("El nombre de ciudad en WeatherData viene de Location, no de la API")
    @SuppressWarnings("unchecked")
    void nombreCiudad_vieneDeLocation() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_CLIMA_DESPEJADO);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        Location location = new Location("Mi Ciudad Personalizada", 6.25, -75.56);
        WeatherData result = client.getWeather(location);

        assertEquals("Mi Ciudad Personalizada", result.cityName(),
                "El cityName debe tomarse del objeto Location, no del JSON de la API");
    }
}

/*
 * NOTA: Igual que GeocodingClient, OpenMeteoClient necesita un segundo
 * constructor para pruebas:
 *
 *   OpenMeteoClient(HttpClient httpClient) {
 *       this.httpClient = httpClient;
 *   }
 */