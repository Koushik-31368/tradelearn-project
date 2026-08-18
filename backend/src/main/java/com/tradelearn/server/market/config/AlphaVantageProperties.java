package com.tradelearn.server.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds all {@code alpha-vantage.*} properties from
 * {@code application.properties} / environment variables.
 *
 * <p>Required env vars when {@code alpha-vantage.enabled=true}:
 * <ul>
 *   <li>{@code MARKET_DATA_API_KEY} — your Alpha Vantage API key</li>
 * </ul>
 *
 * <p>Optional:
 * <ul>
 *   <li>{@code ALPHA_VANTAGE_ENABLED} — set {@code true} to activate (default: {@code false})</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "alpha-vantage")
public class AlphaVantageProperties {

    /** Whether the Alpha Vantage provider is enabled. */
    private boolean enabled = false;

    /**
     * The API key, supplied via the {@code MARKET_DATA_API_KEY} environment variable.
     * Never commit a real key; leave blank in committed property files.
     */
    private String apiKey = "";

    /** Base URL for Alpha Vantage REST API. */
    private String baseUrl = "https://www.alphavantage.co/query";

    public boolean isEnabled()           { return enabled; }
    public void setEnabled(boolean v)    { this.enabled = v; }

    public String getApiKey()            { return apiKey; }
    public void setApiKey(String v)      { this.apiKey = v; }

    public String getBaseUrl()           { return baseUrl; }
    public void setBaseUrl(String v)     { this.baseUrl = v; }
}
