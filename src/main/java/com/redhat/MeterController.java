package com.redhat;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.api.Context;
import io.micrometer.core.instrument.MeterRegistry;

@Controller
public class MeterController implements ResourceController<Meter> {
    private static final Logger LOG = Logger.getLogger(MeterController.class);

    private final MeterRegistry meterRegistry;
    private final KubernetesClient client;
    private final OperatorConfig config;

    private PodWatcher podWatcher;

    public MeterController(KubernetesClient client, MeterRegistry meterRegistry, OperatorConfig config) {
        this.client = client;
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public DeleteControl deleteResource(Meter resource, Context<Meter> context) {
        LOG.info("Meter CustomResource deleted.");
        if (podWatcher != null) {
            podWatcher.onClose();
        }

        return DeleteControl.DEFAULT_DELETE;
    }

    @Override
    public UpdateControl<Meter> createOrUpdateResource(Meter resource, Context<Meter> context) {
        if (resource.isMarkedForDeletion()) {
            LOG.info("Meter CustomResource marked for deletion, no reconciliation performed.");
            return UpdateControl.noUpdate();
        }

        final MeterSpec spec = resource.getSpec();

        // Validate CR content. Do nothing if invalid
        if (invalid(spec)) {
            return UpdateControl.noUpdate();
        }

        if (spec.getMeterCollectionEnabled()) {
            // Meter Collection enabled

            // Handle PodWatcher
            if (podWatcher != null) {
                // Update existing watcher
                LOG.info("Updating Meter spec in PodWatcher.");
                podWatcher.updateSpec(spec);
            } else {
                // Set up new watcher
                podWatcher = new PodWatcher(client, meterRegistry, spec, config);
                client.pods().inAnyNamespace().watch(podWatcher);
            }
        } else {
            // Meter collection disabled
            LOG.info("Meter collection disabled.");

            // Handle PodWatcher
            if (podWatcher != null) {
                // Meter collection is now disabled, close the watcher
                podWatcher.onClose();
                podWatcher = null;
                LOG.info("Stopped watching for events. No further metrics captured.");
            }
        }

        resource.setStatus(constructStatus());
        return UpdateControl.updateStatusSubResource(resource);
    }

    // Testing purposes only
    PodWatcher getWatcher() {
        return podWatcher;
    }

    private MeterStatus constructStatus() {
        final String currentlyWatching = podWatcher != null ? "TRUE" : "FALSE";
        final String watchedPodCount = podWatcher != null ? podWatcher.watchedPods() : "UNKNOWN";
        return new MeterStatus(currentlyWatching, watchedPodCount);
    }

    private boolean invalid(MeterSpec spec) {
        boolean invalid = false;

        if (spec.getMeterCollectionEnabled()) {
            if (spec.getCpuMeterName() == null || spec.getCpuMeterName().isBlank()) {
                invalid = true;
                LOG.warn("Invalid Meter CustomResource, coreMeterName is not set.");
            }
    
            if (spec.getPodLabelIdentifier() == null || spec.getPodLabelIdentifier().isBlank()) {
                invalid = true;
                LOG.warn("Invalid Meter CustomResource, podLabelIdentifier not set.");
            }
        }

        return invalid;
    }
}
