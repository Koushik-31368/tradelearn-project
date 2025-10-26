package com.tradelearn.server.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class StockDataService {

    private final RestTemplate restTemplate = new RestTemplate();
    // Your new API Key
    private final String apiKey = "F9HE0WX2CTKBJOGU";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<StockDataPoint> getDailyDataParsed(String symbol) {
        String apiUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY"
                + "&symbol=" + symbol
                + "&outputsize=full" // Request full history
                + "&apikey=" + apiKey;

        try {
            String jsonResponse = restTemplate.getForObject(apiUrl, String.class);
            if (jsonResponse == null) {
                System.err.println("Error: Received null response from Alpha Vantage API.");
                return null;
            }

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // Check for API error messages
            if (rootNode.has("Error Message")) {
                System.err.println("Alpha Vantage API Error: " + rootNode.path("Error Message").asText());
                return null;
            }
            if (rootNode.has("Note")) {
                System.err.println("Alpha Vantage API Note (likely rate limit): " + rootNode.path("Note").asText());
                return null;
            }
            if (rootNode.has("Information")) {
                System.err.println("Alpha Vantage API Info (check symbol?): " + rootNode.path("Information").asText());
                return null;
            }


            JsonNode timeSeriesNode = rootNode.path("Time Series (Daily)");
            if (timeSeriesNode.isMissingNode() || !timeSeriesNode.isObject()) {
                System.err.println("Error: 'Time Series (Daily)' data not found or not an object in API response.");
                System.err.println("Full Response: " + jsonResponse);
                return null;
            }

            List<StockDataPoint> dataPoints = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = timeSeriesNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String date = entry.getKey();
                JsonNode dayData = entry.getValue();

                if (!dayData.has("1. open") || !dayData.has("4. close")) {
                    System.err.println("Warning: Skipping date " + date + " due to missing data fields.");
                    continue;
                }

                StockDataPoint point = new StockDataPoint(
                        date,
                        dayData.path("1. open").asDouble(),
                        dayData.path("2. high").asDouble(),
                        dayData.path("3. low").asDouble(),
                        dayData.path("4. close").asDouble()
                );
                dataPoints.add(point);
            }

            if (dataPoints.isEmpty()) {
                System.err.println("Error: No valid data points extracted from API response.");
                return null;
            }

            Collections.reverse(dataPoints);

            int maxSize = 100;
            if (dataPoints.size() > maxSize) {
                return dataPoints.subList(dataPoints.size() - maxSize, dataPoints.size());
            }

            return dataPoints;

        } catch (IOException e) {
            System.err.println("Error parsing JSON response from Alpha Vantage: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("An unexpected error occurred in getDailyDataParsed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // This method is used by the StockSearch component
    public String getDailyData(String symbol) {
        String apiUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY"
                + "&symbol=" + symbol
                + "&apikey=" + apiKey;
        try {
            return restTemplate.getForObject(apiUrl, String.class);
        } catch (Exception e) {
            System.err.println("Error fetching raw data: " + e.getMessage());
            return "{\"Error\": \"Could not fetch data\"}";
        }
    }
}
