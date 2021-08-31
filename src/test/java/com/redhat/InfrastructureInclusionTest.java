package com.redhat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.redhat.OperatorConfig.PodConfig;

import org.junit.jupiter.api.Test;

public class InfrastructureInclusionTest {
    private static PodConfig podConfig = TestUtil.podConfig(null, null, null, "rht.component-type", "infrastructure");
    private static OperatorConfig config = TestUtil.operatorConfig(null, TestUtil.emptyMeterConfig(), podConfig);

    private static Map<String, String> podLabels =
        Map.of("rht.component-type", "infrastructure",
                "rht.product-name", "Red_Hat_Integration");

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
