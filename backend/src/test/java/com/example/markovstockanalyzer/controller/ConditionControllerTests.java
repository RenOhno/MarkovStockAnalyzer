package com.example.markovstockanalyzer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConditionControllerTests {
    @Autowired
    private MockMvc mockMvc;

    private static final String VALID = """
            {
              "name":"3-state test",
              "stockId":"9001",
              "startDate":"2025-01-06",
              "endDate":"2025-02-19",
              "lowerThreshold":-0.005,
              "upperThreshold":0.005,
              "stateCount":3,
              "estimator":"MLE_STRICT",
              "windowMode":"FULL",
              "windowSize":null,
              "horizons":[1,3,5,10]
            }
            """;

    @Test
    void createsConditionWithLocationAndId() throws Exception {
        mockMvc.perform(post("/api/conditions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/conditions/\\d+")))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void rejectsFixedValueViolations() throws Exception {
        assert422("stateCount", "2");
        assert422("estimator", "OTHER");
        assert422("windowMode", "ROLLING");
        assert422("windowSize", "10");
        assert422("horizons", "[1, 5]");
    }

    @Test
    void rejectsInvalidRangeAndThresholds() throws Exception {
        assert422("startDate", "2026-01-01").andExpect(jsonPath("$.code").value("INVALID_CONDITION"));
        assert422("lowerThreshold", "-1.0");
        assert422("upperThreshold", "1.0");
        assert422("endDate", "2031-01-07");
    }

    private org.springframework.test.web.servlet.ResultActions assert422(
            String field,
            String value
    ) throws Exception {
        String body = switch (field) {
            case "name", "stockId", "estimator", "windowMode" ->
                VALID.replace("\"" + field + "\":\"" + (field.equals("estimator") ? "MLE_STRICT" : field.equals("windowMode") ? "FULL" : field.equals("name") ? "3-state test" : "9001") + "\"", "\"" + field + "\":\"" + value + "\"");
            case "startDate" -> VALID.replace("\"startDate\":\"2025-01-06\"", "\"startDate\":\"" + value + "\"");
            case "endDate" -> VALID.replace("\"endDate\":\"2025-02-19\"", "\"endDate\":\"" + value + "\"");
            case "lowerThreshold" -> VALID.replace("\"lowerThreshold\":-0.005", "\"lowerThreshold\":" + value);
            case "upperThreshold" -> VALID.replace("\"upperThreshold\":0.005", "\"upperThreshold\":" + value);
            case "stateCount" -> VALID.replace("\"stateCount\":3", "\"stateCount\":" + value);
            case "windowSize" -> VALID.replace("\"windowSize\":null", "\"windowSize\":" + value);
            case "horizons" -> VALID.replace("\"horizons\":[1,3,5,10]", "\"horizons\":" + value);
            default -> throw new IllegalArgumentException("Unsupported test field: " + field);
        };
        return mockMvc.perform(post("/api/conditions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
