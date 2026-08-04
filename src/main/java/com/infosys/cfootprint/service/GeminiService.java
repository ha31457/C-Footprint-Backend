package com.infosys.cfootprint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.model.ActivityLog;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeminiService {

    @Value("${app.gemini.keys}")
    private List<String> apiKeys;

    @Value("${app.gemini.models}")
    private List<String> models;

    private final Map<String, List<String>> keyModelsMap = new HashMap<>();
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        java.io.File file = new java.io.File("Models.txt");
        if (file.exists()) {
            try {
                List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
                String currentKey = null;
                List<String> currentModels = new ArrayList<>();
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.endsWith("Models:")) {
                        if (currentKey != null) {
                            keyModelsMap.put(currentKey, new ArrayList<>(currentModels));
                        }
                        currentKey = line.replace("Models:", "").trim();
                        currentModels.clear();
                    } else if (currentKey != null) {
                        currentModels.add(line);
                    }
                }
                if (currentKey != null) {
                    keyModelsMap.put(currentKey, new ArrayList<>(currentModels));
                }
            } catch (Exception e) {
                System.err.println("Failed to read Models.txt: " + e.getMessage());
            }
        }
    }

    public String executeWithFallback(String prompt) {
        List<String> activeKeys = apiKeys.stream()
                .filter(k -> k != null && !k.trim().isEmpty())
                .collect(Collectors.toList());

        List<String> activeModels = models.stream()
                .filter(m -> m != null && !m.trim().isEmpty())
                .collect(Collectors.toList());

        if (activeKeys.isEmpty()) {
            throw new IllegalStateException("No active Gemini API keys configured.");
        }

        Exception lastException = null;

        for (int i = 0; i < activeKeys.size(); i++) {
            String key = activeKeys.get(i);
            String keyIdentifier = "GEMINI_API_KEY_" + (i + 1);
            List<String> keyModels = keyModelsMap.getOrDefault(keyIdentifier, activeModels);

            if (keyModels == null || keyModels.isEmpty()) {
                keyModels = activeModels;
            }

            for (String modelName : keyModels) {
                try {
                    String modelPath = modelName;
                    if (modelPath.startsWith("models/")) {
                        modelPath = modelPath.substring(7);
                    }
                    String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelPath + ":generateContent?key=" + key;

                    Map<String, Object> textPart = Map.of("text", prompt);
                    Map<String, Object> partsObj = Map.of("parts", List.of(textPart));
                    Map<String, Object> requestBody = Map.of("contents", List.of(partsObj));

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                        return response.getBody();
                    }
                } catch (Exception e) {
                    System.err.println("Gemini execution failed for key prefix: " 
                            + key.substring(0, Math.min(key.length(), 6)) 
                            + "... and model: " + modelName + ". Error: " + e.getMessage());
                    lastException = e;
                }
            }
        }

        throw new RuntimeException("All Gemini API keys and models pool have been exhausted. Last error: " 
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    public List<String> generateRecommendations(List<ActivityLog> logs) {
        if (logs == null || logs.isEmpty()) {
            throw new IllegalArgumentException("No activity logs available for recommendation.");
        }

        Map<String, Long> activityCounts = logs.stream()
                .collect(Collectors.groupingBy(ActivityLog::getActivityType, Collectors.counting()));

        List<String> topActivityTypes = activityCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> activitySummaries = new ArrayList<>();
        for (String actType : topActivityTypes) {
            final String targetType = actType;
            double totalQty = logs.stream()
                    .filter(l -> l.getActivityType().equals(targetType))
                    .mapToDouble(ActivityLog::getQuantity)
                    .sum();
            double totalCo2 = logs.stream()
                    .filter(l -> l.getActivityType().equals(targetType))
                    .mapToDouble(ActivityLog::getCo2Emission)
                    .sum();
            String unit = logs.stream()
                    .filter(l -> l.getActivityType().equals(targetType))
                    .map(ActivityLog::getUnit)
                    .findFirst()
                    .orElse("");

            activitySummaries.add(actType + ": " + (Math.round(totalQty * 100.0) / 100.0) + " " + unit + " logged, generating " + (Math.round(totalCo2 * 100.0) / 100.0) + " kg CO2");
        }

        String prompt = "You are an expert personal carbon footprint sustainability coach. " +
                "The user has logged the following top activities: [" + String.join("; ", activitySummaries) + "]. " +
                "Provide exactly 3 concise, highly personalized recommendations (1-2 sentences each). " +
                "CRITICAL INSTRUCTION: Each recommendation MUST explicitly cite the user's exact logged numbers (for example: 'You have logged 150.0 km of driving, which generated 27.0 kg of CO2...') so the user immediately recognizes their own logged data in your advice. " +
                "Format your response strictly as a JSON array of 3 string items, e.g. [\"rec 1\", \"rec 2\", \"rec 3\"]. Do not wrap in markdown fences if possible.";

        String rawResponse = executeWithFallback(prompt);
        return parseGeminiResponse(rawResponse);
    }

    private List<String> parseGeminiResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new RuntimeException("Gemini returned empty text.");
            }

            String text = textNode.asText().trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.startsWith("```")) {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            List<String> list = new ArrayList<>();
            if (text.startsWith("[")) {
                JsonNode arrayNode = objectMapper.readTree(text);
                if (arrayNode.isArray()) {
                    for (JsonNode item : arrayNode) {
                        list.add(item.asText());
                    }
                }
            } else {
                String[] lines = text.split("\n");
                for (String line : lines) {
                    String clean = line.replaceAll("^[0-9]+\\.\\s*", "").replaceAll("^-\\s*", "").trim();
                    if (!clean.isEmpty()) {
                        list.add(clean);
                    }
                }
            }

            if (list.size() >= 3) {
                return list.subList(0, 3);
            }
            if (!list.isEmpty()) {
                return list;
            }
            throw new RuntimeException("Could not parse 3 recommendations from Gemini response.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini API response: " + e.getMessage(), e);
        }
    }

    public String generateContent(String prompt) {
        String raw = executeWithFallback(prompt);
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            return textNode.asText().trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini content: " + e.getMessage(), e);
        }
    }
}
