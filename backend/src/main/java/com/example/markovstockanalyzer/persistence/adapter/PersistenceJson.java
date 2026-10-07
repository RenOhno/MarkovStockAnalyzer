package com.example.markovstockanalyzer.persistence.adapter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** JSON strings remain raw JSON in the Hibernate JSON columns established in STEP 8A. */
@Component
@Profile("mysql")
public class PersistenceJson {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    public String write(Object value) { return mapper.writeValueAsString(value); }
    public <T> T read(String value, Class<T> type) { return mapper.readValue(value, type); }
    public <T> T read(String value, TypeReference<T> type) { return mapper.readValue(value, type); }
    // Local canonical representation: alphabetical property/map keys, UTF-8 JSON.
    public String sha256(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
