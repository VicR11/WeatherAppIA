package org.weather.ui;


import org.weather.service.WeatherService;

import java.util.Scanner;

public class ConsoleUI {

    private final WeatherService weatherService;

    public ConsoleUI() {
        this.weatherService = new WeatherService();
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("==================================");
        System.out.println("      CONSULTA DE CLIMA");
        System.out.println("==================================");

        while (true) {
            System.out.print("Ingresa el nombre de la ciudad: ");
            String city = scanner.nextLine();

            if (city.equalsIgnoreCase("salir")) {
                System.out.println("Programa finalizado.");
                break;
            }

            var result = weatherService.getWeatherByCity(city);
            System.out.println(WeatherFormatter.format(result));
        }

        scanner.close();
    }
}
