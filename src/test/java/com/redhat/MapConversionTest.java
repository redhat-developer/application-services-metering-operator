package com.redhat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class MapConversionTest {

    @Test
    void testListToMapConversion() {
        List<String> keyValuesList = List.of("key1=value1", "key2=value2", "key3=value3");
        Map<String, String> results = PodWatcher.convertListToMap(keyValuesList);

        assertNotNull(results);
        assertEquals(3, results.size());
        assertArrayEquals(new String[]{"key1", "key2", "key3"}, results.keySet().toArray());
        assertArrayEquals(new String[]{"value1", "value2", "value3"}, results.values().toArray());
    }    
}
