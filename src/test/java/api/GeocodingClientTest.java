package api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weather.api.GeocodingClient;
import org.weather.model.Location;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;


/**
 * 📁 src/test/java/com/weather/api/GeocodingClientTest.java
 *
 * Pruebas unitarias para GeocodingClient.
 *
 * ESTRATEGIA DE MOCK:
 * GeocodingClient hace llamadas HTTP reales, lo que haría las pruebas
 * lentas e inestables (dependen de internet). Usamos Mockito para
 * simular las respuestas HTTP sin hacer peticiones reales.
 *
 * Para poder inyectar el HttpClient mockeado, necesitamos un constructor
 * adicional en GeocodingClient que lo acepte (ver nota al final del archivo).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeocodingClient — conversión ciudad → coordenadas")
class GeocodingClientTest {

    // Mock del HttpClient — simula llamadas HTTP sin internet
    @Mock
    private HttpClient mockHttpClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private HttpResponse mockHttpResponse;

    private GeocodingClient client;

    // Respuesta JSON real de la Geocoding API de Open-Meteo para "Medellín"
    private static final String JSON_MEDELLIN = """
            {
              "results": [
                {
                  "id": 3674962,
                  "name": "Medellín",
                  "latitude": 6.25184,
                  "longitude": -75.56359,
                  "country": "Colombia"
                }
              ]
            }
            """;

    // Respuesta JSON cuando la ciudad no existe
    private static final String JSON_SIN_RESULTADOS = """
            {
              "generationtime_ms": 0.52
            }
            """;

    // Respuesta con results:null
    private static final String JSON_RESULTS_NULL = """
            {
              "results": null
            }
            """;

    @BeforeEach
    void setUp() {
        // Usamos el constructor que acepta HttpClient para poder inyectar el mock
        client = new GeocodingClient(mockHttpClient);
    }

    // ── Tests: entrada inválida (sin llamada HTTP) ───────────────────────────

    @ParameterizedTest(name = "entrada: \"{0}\"")
    @DisplayName("Lanza CityNotFoundException para nombre vacío o null")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void entradaVaciaONull_lanzaCityNotFoundException(String cityName) {
        assertThrows(
                GeocodingClient.CityNotFoundException.class,
                () -> client.getLocation(cityName),
                "Debe lanzar CityNotFoundException para entrada: '" + cityName + "'"
        );
    }

    // ── Tests: respuesta exitosa ─────────────────────────────────────────────

    @Test
    @DisplayName("Ciudad válida devuelve Location con coordenadas correctas")
    @SuppressWarnings("unchecked")
    void ciudadValida_devuelveLocationCorrecta() throws Exception {
        // Arrange — simular respuesta HTTP 200 con JSON de Medellín
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_MEDELLIN);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        // Act
        Location result = client.getLocation("Medellín");

        // Assert
        assertAll("La Location debe tener los datos de Medellín",
                () -> assertNotNull(result, "El resultado no debe ser null"),
                () -> assertEquals("Medellín", result.name(), "El nombre debe coincidir"),
                () -> assertEquals(6.25184,   result.latitude(),  0.0001, "Latitud incorrecta"),
                () -> assertEquals(-75.56359, result.longitude(), 0.0001, "Longitud incorrecta")
        );
    }

    @Test
    @DisplayName("Nombre de ciudad con espacios funciona correctamente")
    @SuppressWarnings("unchecked")
    void ciudadConEspacios_funciona() throws Exception {
        String jsonBuenosAires = """
                {"results":[{"name":"Buenos Aires","latitude":-34.6037,"longitude":-58.3816}]}
                """;
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(jsonBuenosAires);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        Location result = client.getLocation("Buenos Aires");

        assertEquals("Buenos Aires", result.name());
    }

    // ── Tests: ciudad no encontrada ──────────────────────────────────────────

    @Test
    @DisplayName("Ciudad inexistente lanza CityNotFoundException")
    @SuppressWarnings("unchecked")
    void ciudadInexistente_lanzaCityNotFoundException() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_SIN_RESULTADOS);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        GeocodingClient.CityNotFoundException ex = assertThrows(
                GeocodingClient.CityNotFoundException.class,
                () -> client.getLocation("xyzciudadinexistente123")
        );

        assertTrue(ex.getMessage().contains("xyzciudadinexistente123"),
                "El mensaje de error debe mencionar la ciudad buscada");
    }

    @Test
    @DisplayName("Respuesta con results:null lanza CityNotFoundException")
    @SuppressWarnings("unchecked")
    void resultsNull_lanzaCityNotFoundException() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(JSON_RESULTS_NULL);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        assertThrows(
                GeocodingClient.CityNotFoundException.class,
                () -> client.getLocation("CiudadFantasma")
        );
    }

    // ── Tests: errores HTTP ──────────────────────────────────────────────────

    @Test
    @DisplayName("HTTP 500 lanza ApiException")
    @SuppressWarnings("unchecked")
    void http500_lanzaApiException() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(500);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        GeocodingClient.ApiException ex = assertThrows(
                GeocodingClient.ApiException.class,
                () -> client.getLocation("Madrid")
        );

        assertTrue(ex.getMessage().contains("500"),
                "El mensaje debe mencionar el código HTTP 500");
    }

    @Test
    @DisplayName("HTTP 429 (rate limit) lanza ApiException")
    @SuppressWarnings("unchecked")
    void http429_lanzaApiException() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(429);
        when(mockHttpClient.send(any(), any())).thenReturn(mockHttpResponse);

        assertThrows(GeocodingClient.ApiException.class,
                () -> client.getLocation("Tokyo"));
    }

    // ── Tests: error de red ──────────────────────────────────────────────────

    @Test
    @DisplayName("Sin conexión a internet lanza NetworkException")
    void sinConexion_lanzaNetworkException() throws Exception {
        when(mockHttpClient.send(any(), any()))
                .thenThrow(new java.net.ConnectException("Connection refused"));

        assertThrows(
                GeocodingClient.NetworkException.class,
                () -> client.getLocation("Bogotá")
        );
    }

    @Test
    @DisplayName("Host desconocido lanza NetworkException")
    void hostDesconocido_lanzaNetworkException() throws Exception {
        when(mockHttpClient.send(any(), any()))
                .thenThrow(new java.net.UnknownHostException("geocoding-api.open-meteo.com"));

        assertThrows(
                GeocodingClient.NetworkException.class,
                () -> client.getLocation("Lima")
        );
    }

    // ── Tests: mensajes de error descriptivos ────────────────────────────────

    @Test
    @DisplayName("NetworkException contiene mensaje descriptivo")
    void networkException_mensajeDescriptivo() throws Exception {
        when(mockHttpClient.send(any(), any()))
                .thenThrow(new java.net.ConnectException("timeout"));

        GeocodingClient.NetworkException ex = assertThrows(
                GeocodingClient.NetworkException.class,
                () -> client.getLocation("Santiago")
        );

        assertFalse(ex.getMessage().isBlank(),
                "El mensaje de NetworkException no debe estar vacío");
    }
}

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * NOTA PARA EL DESARROLLADOR:
 *
 * Para que estas pruebas funcionen, GeocodingClient necesita un segundo
 * constructor que acepte un HttpClient externo (inyección de dependencias):
 *
 *   // Constructor para pruebas (inyección de dependencias)
 *   GeocodingClient(HttpClient httpClient) {
 *       this.httpClient = httpClient;
 *   }
 *
 * Este patrón se llama "Inyección de Dependencias" y es una buena práctica:
 * en producción el constructor sin parámetros crea su propio HttpClient,
 * pero en tests puedes pasar un mock para controlar el comportamiento.
 * ─────────────────────────────────────────────────────────────────────────────
 */
