package org.weather;

import org.weather.ui.ConsoleUI;
import org.weather.model.WeatherData;
import org.weather.service.WeatherService;
import org.weather.ui.WeatherGUI;

import java.util.Scanner;

/**
 * 📁 Main.java  (raíz del paquete com.weather)
 *
 * Punto de entrada de la aplicación.
 * Solo se encarga de leer la entrada y mostrar el resultado.
 * Toda la lógica real está en WeatherService.
 */
public class Main {

    public static void main(String[] args) {
        //ConsoleUI ui = new ConsoleUI();
        //ui.start();
        javax.swing.SwingUtilities.invokeLater(() -> {
            new WeatherGUI().setVisible(true);
        });

        //Para mostrar por consola
        /*WeatherService service = new WeatherService();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Consulta de Clima ===");
        System.out.print("Ingresa el nombre de la ciudad: ");
        String cityName = scanner.nextLine();

        // ── Uso de la función principal ──────────────────────────
        WeatherService.WeatherResult result = service.getWeatherByCity(cityName);

        // ── Mostrar resultado en JSON ────────────────────────────
        System.out.println("\nRespuesta JSON:");
        System.out.println(result.toJson());

        // ── O acceder a los campos individualmente ───────────────
        if (result.isSuccess()) {
            WeatherData data = result.getData();
            System.out.println("\nDatos individuales:");
            System.out.println("  Ciudad      : " + data.cityName());
            System.out.println("  Temperatura : " + data.temperature() + " °C");
            System.out.println("  Descripción : " + data.description());
        }*/
    }
}