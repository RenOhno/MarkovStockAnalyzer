package com.example.markovstockanalyzer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StockAndConditionQueryTests {
    @Autowired
    private MockMvc mockMvc;

    private static final String VALID = """
            {
              "name":"query test",
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
    void listsStocksWithSummaryFields() throws Exception {
        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].ticker").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].exchange").exists())
                .andExpect(jsonPath("$[0].currency").exists())
                .andExpect(jsonPath("$[0].timeZone").exists());
    }

    @Test
    void savesThenFindsConditionByIdAndListsConditions() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/conditions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isCreated())
                .andReturn();

        String location = created.getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Integer.parseInt(id)))
                .andExpect(jsonPath("$.stockId").value("9001"));
        mockMvc.perform(get("/api/conditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void missingConditionReturns404() throws Exception {
        mockMvc.perform(get("/api/conditions/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONDITION_NOT_FOUND"));
    }

    @Test
    void unknownStockReturns404StockNotFound() throws Exception {
        String invalid = VALID.replace("\"stockId\":\"9001\"", "\"stockId\":\"missing\"");

        mockMvc.perform(post("/api/conditions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }
}
