package org.weather.model;


/**
 * 📁 model/Location.java
 *
 * Representa las coordenadas obtenidas de la Geocoding API.
 * Es un objeto interno — el usuario final nunca lo ve directamente.
 */
public record Location(
        String name,       // Nombre normalizado devuelto por la API
        double latitude,
        double longitude
) {}