package com.aisuite.config;

import com.aisuite.service.GroqService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentConfig {

    @Value("${app.groq.api-key}")
    private String groqApiKey;

    // Primary bean — base instance, not used directly by controllers
    @Bean
    @Primary
    public GroqService groqService() {
        return new GroqService(groqApiKey);
    }

    // Named bean for weather agent
    @Bean("weatherGroq")
    public GroqService weatherGroqService() {
        return new GroqService(groqApiKey, GroqService.WEATHER_PROMPT);
    }

    // Named bean for currency agent
    @Bean("currencyGroq")
    public GroqService currencyGroqService() {
        return new GroqService(groqApiKey, GroqService.CURRENCY_PROMPT);
    }
}
