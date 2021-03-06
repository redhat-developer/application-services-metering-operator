package com.redhat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redhat.OperatorConfig.PodConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PodInclusionTest {
    private static PodConfig podConfig = TestUtil.podConfig("rht.prod_name", null, null, "rht.component_type", "infrastructure");
    private static OperatorConfig config = TestUtil.operatorConfig(null, List.of("Red_Hat_Integration"), TestUtil.emptyMeterConfig(), podConfig);

    private static Map<String, String> podLabels =
        Map.of("rht.component_type", "infrastructure",
                "rht.prod_name", "Red_Hat_Integration");

    private static PodWatcher podWatcher;

    @BeforeAll
    static void createPodWatcher() {
        podWatcher = new PodWatcher(null, null, null, config);
    }

    @Test
    void testInfrastructureIncluded() {
        MeterSpec spec = new MeterSpec();
        spec.setIncludeInfrastructure(true);

        assertTrue(podWatcher.includePod(config, podLabels, spec));
    }

    @Test
    void testInfrastructureExcluded() {
        MeterSpec spec = new MeterSpec();
        spec.setIncludeInfrastructure(false);

        assertFalse(podWatcher.includePod(config, podLabels, spec));
    }

    @Test
    void testNamespaceWatchedWithEmptyWatches() {
        assertTrue(podWatcher.shouldWatch(Collections.emptySet(), "test"));
    }

    @Test
    void testNamespaceWatchedWithEmptyWatch() {
        assertTrue(podWatcher.shouldWatch(Set.of(""), "test"));
    }

    @Test
    void testNamespaceWatchedWithDefinedWatches() {
        assertTrue(podWatcher.shouldWatch(Set.of("another", "test"), "test"));
    }

    @Test
    void testNamespaceNotWatched() {
        assertFalse(podWatcher.shouldWatch(Set.of("another", "test"), "bad"));
    }
}
