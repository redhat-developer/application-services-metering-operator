package com.redhat;

import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.hamcrest.text.IsEmptyString;
import org.jboss.logmanager.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InMemoryLogHandler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@WithKubernetesTestServer
@QuarkusTest
public class MeterControllerTest {
    private static final Logger rootLogger = LogManager.getLogManager().getLogger("com.redhat");
    private static final InMemoryLogHandler inMemoryLogHandler = new InMemoryLogHandler(r -> r.getLoggerName().contains(MeterController.class.getName()));

    static {
        rootLogger.addHandler(inMemoryLogHandler);
    }

    @KubernetesTestServer
    KubernetesServer mockServer;

    @Inject
    MeterController meterController;

    @Inject
    MeterRegistry meterRegistry;

    @BeforeEach
    void setup() {
        // Ensure PodWatcher not running
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(false);
        meter.setSpec(spec);

        meterController.createOrUpdateResource(meter, null);

        // Clear the logs
        inMemoryLogHandler.close();

        // Clear the registry
        meterRegistry.clear();
    }

    private List<String> getLogMessages() {
        return inMemoryLogHandler.getRecords().stream().map(r -> r.getMessage()).collect(Collectors.toList());
    }

    @Test
    void testReconcileMarkedForDeletion() {
        Meter meter = new Meter();
        ObjectMeta metadata = meter.getMetadata();
        metadata.setDeletionTimestamp(Instant.now().toString());
        meter.setMetadata(metadata);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertFalse(response.isUpdateStatusSubResource());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Meter CustomResource marked for deletion, no reconciliation performed."), getLogMessages());
    }

    @Test
    void testSpecWithNoMetersAndEnabledFailsValidation() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertFalse(response.isUpdateStatusSubResource());
        assertNull(meterController.getWatcher());

        assertEquals(2, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(
            List.of("Invalid Meter CustomResource, coreMeterName is not set.",
            "Invalid Meter CustomResource, podLabelIdentifier not set."),
            getLogMessages());
    }

    @Test
    void testSpecWithCpuMeterAndEnabledFailsValidation() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        spec.setCpuMeterName("cpuMeterName1");
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertFalse(response.isUpdateStatusSubResource());
        assertNull(meterController.getWatcher());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Invalid Meter CustomResource, podLabelIdentifier not set."), getLogMessages());
    }

    @Test
    void testSpecWithNoMetersAndDisabled() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(false);
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());
        assertNull(meterController.getWatcher());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Meter collection disabled."), getLogMessages());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("FALSE", status.getCurrentlyWatching());
        assertEquals("UNKNOWN", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());
    }

    @Test
    void testSpecWithCpuMeterAndDisabled() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(false);
        spec.setCpuMeterName("cpuMeterName2");
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());
        assertNull(meterController.getWatcher());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Meter collection disabled."), getLogMessages());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("FALSE", status.getCurrentlyWatching());
        assertEquals("UNKNOWN", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());
    }

    @Test
    void testSpecWithMetersAndDisabled() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(false);
        spec.setCpuMeterName("cpuMeterName3");
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());
        assertNull(meterController.getWatcher());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Meter collection disabled."), getLogMessages());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("FALSE", status.getCurrentlyWatching());
        assertEquals("UNKNOWN", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());
    }

    @Test
    void testSpecWithCpuMeterAndEnabled() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        spec.setCpuMeterName("cpuMeterName4");
        spec.setPodLabelIdentifier("rht.prod_name");
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());
        assertNotNull(meterController.getWatcher());

        assertEquals(0, inMemoryLogHandler.getRecords().size());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());
   }

    @Test
    void testSpecWithCpuMeterAndEnabledThenDisabled() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        spec.setCpuMeterName("cpuMeterName5");
        spec.setPodLabelIdentifier("rht.prod_name");
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());
        assertNotNull(meterController.getWatcher());

        assertEquals(0, inMemoryLogHandler.getRecords().size());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());

        // Disable meter collection
        Meter updatedMeter = new Meter();
        MeterSpec updatedSpec = new MeterSpec();
        updatedSpec.setMeterCollectionEnabled(false);
        updatedSpec.setCpuMeterName("cpuMeterName5");
        updatedSpec.setPodLabelIdentifier("rht.prod_name");
        updatedMeter.setSpec(updatedSpec);

        response = meterController.createOrUpdateResource(updatedMeter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());
        assertNull(meterController.getWatcher());

        assertEquals(2, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Meter collection disabled.",
            "Stopped watching for events. No further metrics captured."),
            getLogMessages());

        status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("FALSE", status.getCurrentlyWatching());
        assertEquals("UNKNOWN", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());
    }

    @Test
    void testStatusWatchingPods() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        spec.setCpuMeterName("cpu_meter_name1");
        spec.setPodLabelIdentifier("rht.prod_name");
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(0, inMemoryLogHandler.getRecords().size());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());

        // Setup test pods
        final Pod pod1 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-1")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "fuse-online"))
                .endMetadata()
                .build();
        final Pod pod2 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-2")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "syndesis"))
                .endMetadata()
                .build();

        // Calling these adds them, but the delete below does not clear them for a subsequent test
        // mockServer.getClient().pods().create(pod1);
        // mockServer.getClient().pods().create(pod2);

        final PodWatcher watcher = meterController.getWatcher();
        assertNotNull(watcher);
        watcher.eventReceived(Action.ADDED, pod1);
        watcher.eventReceived(Action.ADDED, pod2);

        response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Updating Meter spec in PodWatcher."), getLogMessages());

        status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("2", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                // Prometheus body has ALL THE THINGS in no particular order

                .body(containsString("# HELP cpu_meter_name1"))
                .body(containsString("# TYPE cpu_meter_name1 gauge"))
                // Note, test for 0.0 as we're not able to mock the metrics from pods in a unit test
                .body(containsString("cpu_meter_name1 0.0"));


        // Cleanup
        mockServer.getClient().pods().delete();
    }

    @Test
    void testStatusWatchingPodsAndInfrastructureFlag() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        spec.setCpuMeterName("cpu_meter_name2");
        spec.setPodLabelIdentifier("rht.prod_name");
        spec.setIncludeInfrastructure(true);
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(0, inMemoryLogHandler.getRecords().size());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());

        // Setup test pods
        final Pod pod1 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-1")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "fuse-online", "rht.subcomp_t", "application"))
                .endMetadata()
                .build();
        final Pod pod2 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-2")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "syndesis", "rht.subcomp_t", "infrastructure"))
                .endMetadata()
                .build();

        // Calling these adds them, but the delete below does not clear them for a subsequent test
        // mockServer.getClient().pods().create(pod1);
        // mockServer.getClient().pods().create(pod2);

        final PodWatcher watcher = meterController.getWatcher();
        assertNotNull(watcher);
        watcher.eventReceived(Action.ADDED, pod1);
        watcher.eventReceived(Action.ADDED, pod2);

        response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Updating Meter spec in PodWatcher."), getLogMessages());

        status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("2", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                // Prometheus body has ALL THE THINGS in no particular order

                .body(containsString("# HELP cpu_meter_name2"))
                .body(containsString("# TYPE cpu_meter_name2 gauge"))
                // Note, test for 0.0 as we're not able to mock the metrics from pods in a unit test
                .body(containsString("cpu_meter_name2 0.0"));


        // Turn off infrastructure inclusion
        Meter updatedMeter = new Meter();
        MeterSpec updatedSpec = new MeterSpec();
        updatedSpec.setMeterCollectionEnabled(true);
        updatedSpec.setCpuMeterName("cpu_meter_name2");
        updatedSpec.setPodLabelIdentifier("rht.prod_name");
        updatedSpec.setIncludeInfrastructure(false);
        updatedMeter.setSpec(updatedSpec);
        response = meterController.createOrUpdateResource(updatedMeter, null);

        //TODO This is a hack to pretend the pods were re-added as the mock can't find them
        watcher.eventReceived(Action.ADDED, pod1);
        watcher.eventReceived(Action.ADDED, pod2);
        response = meterController.createOrUpdateResource(updatedMeter, null);
        // END HACK

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(3, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(
            List.of("Updating Meter spec in PodWatcher.",
            "Updating Meter spec in PodWatcher.",
            "Updating Meter spec in PodWatcher."),
            getLogMessages());

        status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("1", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                // Prometheus body has ALL THE THINGS in no particular order

                .body(containsString("# HELP cpu_meter_name2"))
                .body(containsString("# TYPE cpu_meter_name2 gauge"))
                // Note, test for 0.0 as we're not able to mock the metrics from pods in a unit test
                .body(containsString("cpu_meter_name2 0.0"));
        
        // Cleanup
        mockServer.getClient().pods().delete();
    }

    @Test
    void testNoPodsWithMismatchedIdentifier() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        spec.setCpuMeterName("cpu_meter_name1");
        spec.setPodLabelIdentifier("rht.prod_name_not");
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(0, inMemoryLogHandler.getRecords().size());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());

        // Setup test pods
        final Pod pod1 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-1")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "fuse-online"))
                .endMetadata()
                .build();
        final Pod pod2 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-2")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "syndesis"))
                .endMetadata()
                .build();

        // Calling these adds them, but the delete below does not clear them for a subsequent test
        // mockServer.getClient().pods().create(pod1);
        // mockServer.getClient().pods().create(pod2);

        final PodWatcher watcher = meterController.getWatcher();
        assertNotNull(watcher);
        watcher.eventReceived(Action.ADDED, pod1);
        watcher.eventReceived(Action.ADDED, pod2);

        response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Updating Meter spec in PodWatcher."), getLogMessages());

        status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());

        // Cleanup
        mockServer.getClient().pods().delete();
    }

    @Test
    void testSpecUpdateNamespace() {
        Meter meter = new Meter();
        MeterSpec spec = new MeterSpec();
        spec.setMeterCollectionEnabled(true);
        spec.setCpuMeterName("cpu_meter_name1");
        spec.setPodLabelIdentifier("rht.prod_name");
        spec.setWatchNamespaces(Set.of("badNamespace"));
        meter.setSpec(spec);

        UpdateControl<Meter> response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(0, inMemoryLogHandler.getRecords().size());

        MeterStatus status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());

        // Setup test pods
        final Pod pod1 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-1")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "fuse-online"))
                .endMetadata()
                .build();
        final Pod pod2 = new PodBuilder()
                .withNewMetadata()
                    .withName("my-pod-2")
                    .withNamespace("test")
                    .withLabels(Map.of("rht.prod_name", "syndesis"))
                .endMetadata()
                .build();

        // Calling these adds them, but the delete below does not clear them for a subsequent test
        // mockServer.getClient().pods().create(pod1);
        // mockServer.getClient().pods().create(pod2);

        PodWatcher watcher = meterController.getWatcher();
        assertNotNull(watcher);
        watcher.eventReceived(Action.ADDED, pod1);
        watcher.eventReceived(Action.ADDED, pod2);

        response = meterController.createOrUpdateResource(meter, null);

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(1, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(List.of("Updating Meter spec in PodWatcher."), getLogMessages());

        status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("0", status.getWatchedPods());
 
        when().get("/q/metrics").then().statusCode(200)
                .body(IsEmptyString.emptyOrNullString());

        // Update namespace watching
        Meter updatedMeter = new Meter();
        MeterSpec updatedSpec = new MeterSpec();
        updatedSpec.setMeterCollectionEnabled(true);
        updatedSpec.setCpuMeterName("cpu_meter_name1");
        updatedSpec.setPodLabelIdentifier("rht.prod_name");
        updatedSpec.setWatchNamespaces(Collections.emptySet());
        updatedMeter.setSpec(updatedSpec);
        response = meterController.createOrUpdateResource(updatedMeter, null);

        //TODO This is a hack to pretend the pods were re-added as the mock can't find them
        watcher.eventReceived(Action.ADDED, pod1);
        watcher.eventReceived(Action.ADDED, pod2);
        response = meterController.createOrUpdateResource(updatedMeter, null);
        // END HACK

        assertNotNull(response);
        assertNotNull(response.getCustomResource());
        assertFalse(response.isUpdateCustomResource());
        assertTrue(response.isUpdateStatusSubResource());

        assertEquals(3, inMemoryLogHandler.getRecords().size());
        assertLinesMatch(
            List.of("Updating Meter spec in PodWatcher.",
            "Updating Meter spec in PodWatcher.",
            "Updating Meter spec in PodWatcher."),
            getLogMessages());

        status = response.getCustomResource().getStatus();
        assertNotNull(status);
        assertEquals("TRUE", status.getCurrentlyWatching());
        assertEquals("2", status.getWatchedPods());

        when().get("/q/metrics").then().statusCode(200)
                // Prometheus body has ALL THE THINGS in no particular order

                .body(containsString("# HELP cpu_meter_name1"))
                .body(containsString("# TYPE cpu_meter_name1 gauge"))
                // Note, test for 0.0 as we're not able to mock the metrics from pods in a unit test
                .body(containsString("cpu_meter_name1 0.0"));


        // Cleanup
        mockServer.getClient().pods().delete();
    }
}
