package com.redhat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class InfrastructureInclusionTest {
    private static OperatorConfig config = new OperatorConfig(){
        @Override
        public List<String> productNameMapping() {
            return null;
        }

        @Override
        public String labelPrefix() {
            return null;
        }

        @Override
        public ComponentType componentType() {
            return new OperatorConfig.ComponentType(){
                @Override
                public String labelKey() {
                    return "com.redhat.component-type";
                }

                @Override
                public String infrastructureValue() {
                    return "infrastructure";
                }
                
            };
        }
    };

    private static Map<String, String> podLabels =
        Map.of("com.redhat.component-type", "infrastructure",
                "com.redhat.product-name", "Red_Hat_Integration");

    @Test
    void testInfrastructureIncluded() {
        MeterSpec spec = new MeterSpec();
        spec.setIncludeInfrastructure(true);

        assertTrue(PodWatcher.includePod(config, podLabels, spec));
    }

    @Test
    void testInfrastructureExcluded() {
        MeterSpec spec = new MeterSpec();
        spec.setIncludeInfrastructure(false);

        assertFalse(PodWatcher.includePod(config, podLabels, spec));
    }
}
