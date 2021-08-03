package com.redhat;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitor;
import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitorBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.api.Context;
import io.micrometer.core.instrument.MeterRegistry;

@Controller
public class MeterController implements ResourceController<Meter> {
    private static final Logger LOG = Logger.getLogger(MeterController.class);

    private final MeterRegistry meterRegistry;

    private final OpenShiftClient client;

    private PodWatcher podWatcher;

    public MeterController(OpenShiftClient client, MeterRegistry meterRegistry) {
        this.client = client;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public DeleteControl deleteResource(Meter resource, Context<Meter> context) {
        if (podWatcher != null) {
            podWatcher.onClose();
        }
        // TODO: Verify ServiceMonitor is removed with below, otherwise we need to
        // explicitly remove it here
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
            if (podWatcher != null) {
                // Update existing watcher
                podWatcher.updateSpec(spec);
            } else {
                // Set up new watcher
                podWatcher = new PodWatcher(client, meterRegistry, spec);
                client.pods().inAnyNamespace().watch(podWatcher);
            }
        } else {
            if (podWatcher != null) {
                podWatcher.onClose();
                podWatcher = null;
                LOG.info("Stopped watching for events. No further metrics captured.");
            }
        }

        // TODO: Install/Verify ServiceMonitor
        Resource<ServiceMonitor> serviceMonitorResource = serviceMonitor();
        if (!serviceMonitorResource.isReady()) {
            // Create ServiceMonitor
            ServiceMonitor newServiceMonitor = new ServiceMonitorBuilder()
                    .withNewMetadata().withName("application").endMetadata()
                    .withNewSpec()
                        .withNewNamespaceSelector().withAny(true).endNamespaceSelector()
                        .withNewSelector().addToMatchLabels("", "").endSelector()
                        .addNewEndpoint().withPort("arg0").withInterval(spec.getScrapeInterval()).endEndpoint()
                    .endSpec()
                    .build();
            client.monitoring().serviceMonitors().inNamespace("name").createOrReplace(newServiceMonitor);
        }

        resource.setStatus(constructStatus());
        return UpdateControl.updateStatusSubResource(resource);
    }

    private MeterStatus constructStatus() {
        final String currentlyWatching = podWatcher != null ? "TRUE" : "FALSE";
        final String watchedPodCount = podWatcher != null ? podWatcher.watchedPods() : "UNKNOWN";
        final Resource<ServiceMonitor> serviceMonitor = serviceMonitor();
        final String serviceMonitorInstalled = serviceMonitor != null ? (serviceMonitor.isReady() ? "TRUE" : "FALSE") : "UNKNOWN";
        return new MeterStatus(currentlyWatching, watchedPodCount, serviceMonitorInstalled);
    }

    private Resource<ServiceMonitor> serviceMonitor() {
        // TODO: Set a proper name!
        return client.monitoring().serviceMonitors().withName("name");
    }

    private boolean invalid(MeterSpec spec) {
        boolean invalid = false;

        if (spec.getMeterNamePrefix().isEmpty()) {
            invalid = true;
            LOG.warn("Invalid Meter CustomResource, meterNamePrefix not set.");
        }

        if (spec.getPodLabelIdentifier().isEmpty()) {
            invalid = true;
            LOG.warn("Invalid Meter CustomResource, podLabelIdentifier not set.");
        }

        if (spec.getScrapeInterval().isEmpty()) {
            invalid = true;
            LOG.warn("Invalid Meter CustomResource, scrapeInterval not set.");
        }

        if (spec.getScrapeInterval().endsWith("s")) {
            invalid = true;
            LOG.warn("Invalid Meter CustomResource, scrapeInterval invalid.");
        }
        return invalid;
    }
}
