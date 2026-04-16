package api;



import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.weather.api.WeatherCodeMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📁 src/test/java/com/weather/api/WeatherCodeMapperTest.java
 *
 * Pruebas unitarias para WeatherCodeMapper.
 * Verifica que cada código WMO devuelva la descripción correcta
 * y que los códigos desconocidos tengan un fallback.
 *
 * No necesita mocks: es lógica pura sin dependencias externas.
 */
@DisplayName("WeatherCodeMapper — códigos WMO a descripciones")
class WeatherCodeMapperTest {

    // ── Códigos principales ──────────────────────────────────────────────────

    @ParameterizedTest(name = "código {0} → \"{1}\"")
    @DisplayName("Códigos conocidos devuelven descripción correcta")
    @CsvSource({
            "0,  Cielo despejado",
            "1,  Principalmente despejado",
            "2,  Parcialmente nublado",
            "3,  Nublado",
            "45, Niebla",
            "48, Niebla con escarcha",
            "51, Llovizna ligera",
            "53, Llovizna moderada",
            "55, Llovizna intensa",
            "61, Lluvia ligera",
            "63, Lluvia moderada",
            "65, Lluvia intensa",
            "71, Nevada ligera",
            "73, Nevada moderada",
            "75, Nevada intensa",
            "77, Granizo",
            "80, Chubascos ligeros",
            "81, Chubascos moderados",
            "82, Chubascos violentos",
            "95, Tormenta eléctrica",
            "96, Tormenta con granizo",
            "99, Tormenta con granizo"
    })
    void codigosConocidos_devuelvenDescripcionCorrecta(int code, String expectedDesc) {
        String result = WeatherCodeMapper.toDescription(code);
        assertEquals(expectedDesc.trim(), result,
                "El código " + code + " debe devolver '" + expectedDesc.trim() + "'");
    }

    // ── Códigos desconocidos ─────────────────────────────────────────────────

    @Test
    @DisplayName("Código desconocido devuelve mensaje con el número de código")
    void codigoDesconocido_devuelveFallback() {
        String result = WeatherCodeMapper.toDescription(999);

        assertAll(
                () -> assertNotNull(result, "El resultado no debe ser null"),
                () -> assertFalse(result.isBlank(), "El resultado no debe estar vacío"),
                () -> assertTrue(result.contains("999"),
                        "El fallback debe mencionar el código desconocido")
        );
    }

    @ParameterizedTest(name = "código desconocido {0} → contiene el número")
    @DisplayName("Varios códigos desconocidos incluyen el número en la respuesta")
    @CsvSource({ "100", "200", "-1", "50" })
    void codigosDesconocidos_incluyenNumero(int code) {
        String result = WeatherCodeMapper.toDescription(code);
        assertTrue(result.contains(String.valueOf(code)),
                "El fallback para código " + code + " debe mencionar el código");
    }

    // ── Grupos de códigos ────────────────────────────────────────────────────

    @Test
    @DisplayName("Todos los códigos de tormenta contienen 'Tormenta'")
    void codigosTormenta_contienenPalabraTormenta() {
        assertAll(
                () -> assertTrue(WeatherCodeMapper.toDescription(95).contains("Tormenta")),
                () -> assertTrue(WeatherCodeMapper.toDescription(96).contains("Tormenta")),
                () -> assertTrue(WeatherCodeMapper.toDescription(99).contains("Tormenta"))
        );
    }

    @Test
    @DisplayName("Todos los códigos de lluvia contienen 'Lluvia' o 'Llovizna'")
    void codigosLluvia_contienenPalabraLluvia() {
        int[] codigosLluvia = {51, 53, 55, 61, 63, 65};
        for (int code : codigosLluvia) {
            String desc = WeatherCodeMapper.toDescription(code);
            assertTrue(desc.contains("Lluvia") || desc.contains("Llovizna"),
                    "Código " + code + " debe contener 'Lluvia' o 'Llovizna', pero fue: " + desc);
        }
    }

    @Test
    @DisplayName("Todos los códigos de nieve contienen 'Nieve' o 'Nevada'")
    void codigosNieve_contienenPalabraNieve() {
        int[] codigosNieve = {71, 73, 75, 85, 86};
        for (int code : codigosNieve) {
            String desc = WeatherCodeMapper.toDescription(code);
            assertTrue(desc.contains("nieve") || desc.contains("Nevada") || desc.contains("Chubascos"),
                    "Código " + code + " debe estar relacionado con nieve, pero fue: " + desc);
        }
    }

    // ── Propiedades de las descripciones ─────────────────────────────────────

    @Test
    @DisplayName("Ninguna descripción devuelve null")
    void ninguna_descripcion_es_null() {
        int[] todosLosCodigos = {0, 1, 2, 3, 45, 48, 51, 53, 55, 61, 63, 65,
                71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99};
        for (int code : todosLosCodigos) {
            assertNotNull(WeatherCodeMapper.toDescription(code),
                    "El código " + code + " no debe devolver null");
        }
    }

    @Test
    @DisplayName("Ninguna descripción conocida está vacía")
    void ninguna_descripcion_conocida_esta_vacia() {
        int[] todosLosCodigos = {0, 1, 2, 3, 45, 48, 51, 53, 55, 61, 63, 65,
                71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99};
        for (int code : todosLosCodigos) {
            assertFalse(WeatherCodeMapper.toDescription(code).isBlank(),
                    "El código " + code + " no debe devolver cadena vacía");
        }
    }
}
