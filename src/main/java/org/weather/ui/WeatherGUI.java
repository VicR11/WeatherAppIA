package org.weather.ui;

import org.weather.service.WeatherService;

import javax.swing.*;
import java.awt.*;

public class WeatherGUI extends JFrame {

    private final WeatherService weatherService = new WeatherService();

    private final JTextField cityField = new JTextField(20);
    private final JButton searchButton = new JButton("Consultar");
    private final JButton refreshButton = new JButton("Actualizar");
    private final JTextArea resultArea = new JTextArea();

    public WeatherGUI() {
        configureWindow();
        buildLayout();
        registerEvents();
    }

    private void configureWindow() {
        setTitle("Weather App");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        JPanel topPanel = new JPanel(new FlowLayout());

        topPanel.add(new JLabel("Ciudad:"));
        topPanel.add(cityField);
        topPanel.add(searchButton);
        topPanel.add(refreshButton);

        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(resultArea);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void registerEvents() {
        searchButton.addActionListener(e -> consultarClima());
        refreshButton.addActionListener(e -> actualizarClima());
    }

    private void consultarClima() {
        String city = cityField.getText().trim();

        if (city.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Debes ingresar una ciudad.",
                    "Campo vacío",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        var result = weatherService.getWeatherByCity(city);
        resultArea.setText(WeatherFormatter.format(result));
    }

    private void actualizarClima() {
        String city = cityField.getText().trim();

        if (city.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Debes ingresar una ciudad.",
                    "Campo vacío",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        var result = weatherService.refreshWeather(city);
        resultArea.setText(WeatherFormatter.format(result));
    }
}