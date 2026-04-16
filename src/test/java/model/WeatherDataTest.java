package model;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.weather.model.WeatherData;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📁 src/test/java/com/weather/model/WeatherDataTest.java
 *
 * Pruebas unitarias para el record WeatherData.
 * Verifica que el objeto se cree correctamente y que toJson()
 * produzca un JSON válido con todos los campos esperados.
 */
@DisplayName("WeatherData — modelo de datos del clima")
class WeatherDataTest {

    // ── Datos de prueba reutilizables ────────────────────────────────────────
    private static final String CITY      = "Medellín";
    private static final double TEMP      = 22.5;
    private static final String DESC      = "Parcialmente nublado";
    private static final double LAT       = 6.2518;
    private static final double LON       = -75.5636;

    // ── Tests del constructor / record ───────────────────────────────────────

    @Test
    @DisplayName("Crea el objeto con todos los campos correctos")
    void constructor_camposCorrectos() {
        WeatherData data = new WeatherData(CITY, TEMP, DESC, LAT, LON);

        assertEquals(CITY, data.cityName(),    "cityName debe coincidir");
        assertEquals(TEMP, data.temperature(), "temperature debe coincidir");
        assertEquals(DESC, data.description(), "description debe coincidir");
        assertEquals(LAT,  data.latitude(),    "latitude debe coincidir");
        assertEquals(LON,  data.longitude(),   "longitude debe coincidir");
    }

    @Test
    @DisplayName("Temperatura negativa se almacena correctamente")
    void constructor_temperaturaNegatva() {
        WeatherData data = new WeatherData("Oslo", -15.3, "Nevada intensa", 59.9, 10.7);
        assertEquals(-15.3, data.temperature());
    }

    @Test
    @DisplayName("Temperatura cero se almacena correctamente")
    void constructor_temperaturaCero() {
        WeatherData data = new WeatherData("Bogotá", 0.0, "Nublado", 4.6, -74.0);
        assertEquals(0.0, data.temperature());
    }

    // ── Tests de toJson() ────────────────────────────────────────────────────

    @Test
    @DisplayName("toJson() contiene todos los campos requeridos")
    void toJson_contieneTodasLasClaves() {
        WeatherData data = new WeatherData(CITY, TEMP, DESC, LAT, LON);
        String json = data.toJson();

        assertAll("El JSON debe contener todas las claves",
                () -> assertTrue(json.contains("\"cityName\""),    "Falta cityName"),
                () -> assertTrue(json.contains("\"temperature\""), "Falta temperature"),
                () -> assertTrue(json.contains("\"description\""), "Falta description"),
                () -> assertTrue(json.contains("\"latitude\""),    "Falta latitude"),
                () -> assertTrue(json.contains("\"longitude\""),   "Falta longitude")
        );
    }

    @Test
    @DisplayName("toJson() incluye los valores correctos")
    void toJson_contieneValoresCorrectos() {
        WeatherData data = new WeatherData(CITY, TEMP, DESC, LAT, LON);
        String json = data.toJson();

        assertAll("El JSON debe contener los valores correctos",
                () -> assertTrue(json.contains("\"Medellín\""),          "Falta nombre ciudad"),
                () -> assertTrue(json.contains("22.5"),                   "Falta temperatura"),
                () -> assertTrue(json.contains("\"Parcialmente nublado\""), "Falta descripción")
        );
    }

    @Test
    @DisplayName("toJson() produce JSON con llaves de apertura y cierre")
    void toJson_esJsonValido() {
        WeatherData data = new WeatherData(CITY, TEMP, DESC, LAT, LON);
        String json = data.toJson().trim();

        assertTrue(json.startsWith("{"), "JSON debe comenzar con '{'");
        assertTrue(json.endsWith("}"),   "JSON debe terminar con '}'");
    }

    @ParameterizedTest(name = "ciudad={0}, temp={1}")
    @DisplayName("toJson() formatea temperatura con 1 decimal")
    @CsvSource({
            "Paris,    18.0,  Despejado,        48.85, 2.35",
            "Tokyo,    30.7,  Nublado,          35.68, 139.69",
            "Helsinki, -5.0,  Nevada ligera,    60.16, 24.93"
    })
    void toJson_temperaturaConUnDecimal(String city, double temp, String desc, double lat, double lon) {
        WeatherData data = new WeatherData(city, temp, desc, lat, lon);
        String json = data.toJson();

        // La temperatura debe aparecer con exactamente 1 decimal (formato %.1f)
        String expectedTemp = String.format("%.1f", temp);
        assertTrue(json.contains(expectedTemp),
                "La temperatura " + expectedTemp + " debe estar en el JSON");
    }

    // ── Tests de igualdad (comportamiento del record) ────────────────────────

    @Test
    @DisplayName("Dos WeatherData con los mismos valores son iguales")
    void equals_mismosDatos_sonIguales() {
        WeatherData a = new WeatherData(CITY, TEMP, DESC, LAT, LON);
        WeatherData b = new WeatherData(CITY, TEMP, DESC, LAT, LON);
        assertEquals(a, b, "Records con mismos valores deben ser iguales");
    }

    @Test
    @DisplayName("Dos WeatherData con distinta temperatura no son iguales")
    void equals_distintosDatos_noSonIguales() {
        WeatherData a = new WeatherData(CITY, 22.5, DESC, LAT, LON);
        WeatherData b = new WeatherData(CITY, 30.0, DESC, LAT, LON);
        assertNotEquals(a, b);
    }
}