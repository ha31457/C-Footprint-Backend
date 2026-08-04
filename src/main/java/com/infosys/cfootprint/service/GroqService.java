package com.infosys.cfootprint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    @Value("${app.groq.api-key:}")
    private String apiKey;

    @Value("${app.groq.model:llama-3.3-70b-versatile}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GroqService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    public boolean isContextualQuery(String userQuery) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            // Default to contextual (safe fallback) if no Groq API Key is configured
            return true;
        }

        String url = "https://api.groq.com/openai/v1/chat/completions";

        String systemPrompt = "You are a query classifier. Analyze the user's query and classify it.\n" +
                "If the query requires context about the user's specific data, progress, logged activities, current goals, or specific metrics on the platform, return 'CONTEXTUAL'.\n" +
                "If the query is a general sustainability question, platform tutorial, or non-user-specific query, return 'GENERAL'.\n" +
                "Respond with exactly one word: 'CONTEXTUAL' or 'GENERAL'.";

        Map<String, Object> systemMessage = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> userMessage = Map.of("role", "user", "content", userQuery);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(systemMessage, userMessage),
                "temperature", 0.0
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String result = root.path("choices").get(0).path("message").path("content").asText().trim();
                return result.equalsIgnoreCase("CONTEXTUAL");
            }
        } catch (Exception e) {
            System.err.println("Groq classification failed: " + e.getMessage());
        }

        // Fallback: assume contextual if API call fails
        return true;
    }
}
