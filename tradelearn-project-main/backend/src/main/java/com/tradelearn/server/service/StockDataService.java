package com.tradelearn.server.service;

import org.springframework.beans.factory.annotation.Value;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class StockDataService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${alphavantage.key:}")
    private String apiKey;

    public List<StockDataPoint> getDailyDataParsed(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("AlphaVantage API key is not configured (alphavantage.key).");
            return null;
        }

        String apiUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY"
                + "&symbol=" + symbol
                + "&outputsize=full"
                + "&apikey=" + apiKey;

        try {
            String jsonResponse = restTemplate.getForObject(apiUrl, String.class);
            if (jsonResponse == null) {
                logger.error("Received null response from Alpha Vantage for symbol: {}", symbol);
                return null;
            }

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (rootNode.has("Error Message")) {
                logger.error("Alpha Vantage API Error: {}", rootNode.path("Error Message").asText());
                return null;
            }
            if (rootNode.has("Note")) {
                logger.warn("Alpha Vantage API Note (rate limit likely): {}", rootNode.path("Note").asText());
                return null;
            }
            if (rootNode.has("Information")) {
                logger.warn("Alpha Vantage API Info: {}", rootNode.path("Information").asText());
                return null;
            }

            JsonNode timeSeriesNode = rootNode.path("Time Series (Daily)");
            if (timeSeriesNode.isMissingNode() || !timeSeriesNode.isObject()) {
                logger.error("'Time Series (Daily)' data not found for symbol {}. Full response: {}", symbol, jsonResponse);
                return null;
            }

            List<StockDataPoint> dataPoints = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = timeSeriesNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String date = entry.getKey();
                JsonNode dayData = entry.getValue();

                if (!dayData.has("1. open") || !dayData.has("4. close")) {
                    logger.warn("Skipping date {} for symbol {} due to missing fields.", date, symbol);
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
                logger.error("No valid data extracted for symbol {}.", symbol);
                return null;
            }

            Collections.reverse(dataPoints);

            int maxSize = 100;
            if (dataPoints.size() > maxSize) {
                return dataPoints.subList(dataPoints.size() - maxSize, dataPoints.size());
            }

            return dataPoints;

        } catch (IOException e) {
            logger.error("Error parsing JSON response from Alpha Vantage: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error in getDailyDataParsed: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getDailyData(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("AlphaVantage API key is not configured (alphavantage.key).");
            return "{\"Error\": \"API key not configured\"}";
        }
        String apiUrl = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY"
                + "&symbol=" + symbol
                + "&apikey=" + apiKey;
        try {
            return restTemplate.getForObject(apiUrl, String.class);
        } catch (Exception e) {
            logger.error("Error fetching raw data for {}: {}", symbol, e.getMessage(), e);
            return "{\"Error\": \"Could not fetch data\"}";
        }
    }
}
