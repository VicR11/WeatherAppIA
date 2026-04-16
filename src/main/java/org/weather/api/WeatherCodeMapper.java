package org.weather.api;


/**
 * 📁 api/WeatherCodeMapper.java
 *
 * Convierte los códigos WMO (World Meteorological Organization)
 * devueltos por Open-Meteo en descripciones legibles para el usuario.
 *
 * Referencia completa de códigos:
 * https://open-meteo.com/en/docs#weathervariables
 */
public class WeatherCodeMapper {

    // Constructor privado — esta clase solo tiene métodos estáticos
    private WeatherCodeMapper() {}

    /**
     * Devuelve una descripción en español para un código WMO.
     *
     * @param code Código meteorológico WMO (0–99)
     * @return Descripción legible del clima
     */
    public static String toDescription(int code) {
        return switch (code) {
            case 0          -> "Cielo despejado";
            case 1          -> "Principalmente despejado";
            case 2          -> "Parcialmente nublado";
            case 3          -> "Nublado";
            case 45         -> "Niebla";
            case 48         -> "Niebla con escarcha";
            case 51         -> "Llovizna ligera";
            case 53         -> "Llovizna moderada";
            case 55         -> "Llovizna intensa";
            case 61         -> "Lluvia ligera";
            case 63         -> "Lluvia moderada";
            case 65         -> "Lluvia intensa";
            case 71         -> "Nevada ligera";
            case 73         -> "Nevada moderada";
            case 75         -> "Nevada intensa";
            case 77         -> "Granizo";
            case 80         -> "Chubascos ligeros";
            case 81         -> "Chubascos moderados";
            case 82         -> "Chubascos violentos";
            case 85, 86     -> "Chubascos de nieve";
            case 95         -> "Tormenta eléctrica";
            case 96, 99     -> "Tormenta con granizo";
            default         -> "Condición desconocida (código " + code + ")";
        };
    }
}