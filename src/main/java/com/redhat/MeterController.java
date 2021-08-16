package com.redhat;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
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
    private final String serviceMonitorName;
    private final String operatorServiceName;
    private final String operatorServiceLabel;
    private final Integer httpPort;
    private final static String httpPortName = "http";

    private PodWatcher podWatcher;

    public MeterController(OpenShiftClient client, MeterRegistry meterRegistry,
            @ConfigProperty(name = "service-monitor.name") String serviceMonitorName,
            @ConfigProperty(name = "operator-service.name") String operatorServiceName,
            @ConfigProperty(name = "operator-service.label-value") String operatorServiceLabel,
            @ConfigProperty(name = "quarkus.http.port") Integer httpPort) {
        this.client = client;
        this.meterRegistry = meterRegistry;
        this.serviceMonitorName = serviceMonitorName;
        this.operatorServiceName = operatorServiceName;
        this.operatorServiceLabel = operatorServiceLabel;
        this.httpPort = httpPort;
    }

    @Override
    public DeleteControl deleteResource(Meter resource, Context<Meter> context) {
        if (podWatcher != null) {
            podWatcher.onClose();
        }

        // Delete ServiceMonitor
        serviceMonitor().delete();

        // Delete Service
        client.services().withName(operatorServiceName).delete();

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
                podWatcher.updateSpec(spec);
            } else {
                // Set up new watcher
                podWatcher = new PodWatcher(client, meterRegistry, spec);
                client.pods().inAnyNamespace().watch(podWatcher);
            }

            // Handle Service for operator
            ServiceResource<Service> serviceResource = client.services().withName(operatorServiceName);
            if (serviceResource.get() == null) {
                // Create Service for operator
                Service newOperatorService = new ServiceBuilder()
                        .withNewMetadata().withName(operatorServiceName).withLabels(Map.of("type", operatorServiceLabel)).endMetadata()
                        .withNewSpec()
                            .addNewPort()
                                .withName(httpPortName)
                                .withNewTargetPort(httpPort)
                                .withPort(httpPort)
                                .withProtocol("TCP")
                            .endPort()
                        .endSpec()
                        .build();
                client.services().inNamespace(client.getNamespace()).createOrReplace(newOperatorService);
            }

            // Handle ServiceMonitor for operator
            Resource<ServiceMonitor> serviceMonitorResource = serviceMonitor();
            if (serviceMonitorResource.get() == null) {
                // Create ServiceMonitor
                ServiceMonitor newServiceMonitor = new ServiceMonitorBuilder()
                        .withNewMetadata().withName(serviceMonitorName).withNamespace("openshift-monitoring").endMetadata()
                        .withNewSpec()
                            .withNewNamespaceSelector().withMatchNames(client.getNamespace()).endNamespaceSelector()
                            .withNewSelector().withMatchLabels(Map.of("type", operatorServiceLabel)).endSelector()
                            .addNewEndpoint()
                                .withPort(httpPortName)
                                .withPath("/q/metrics")
                                .withScheme("http")
                                .withInterval(spec.getScrapeInterval())
                            .endEndpoint()
                        .endSpec()
                        .build();
                client.monitoring().serviceMonitors().inNamespace("openshift-monitoring").createOrReplace(newServiceMonitor);
            }
        } else {
            // Meter collection disabled

            // Handle PodWatcher
            if (podWatcher != null) {
                // Meter collection is now disabled, close the watcher
                podWatcher.onClose();
                podWatcher = null;
                LOG.info("Stopped watching for events. No further metrics captured.");
            }

            // Handle ServiceMonitor for operator
            Resource<ServiceMonitor> serviceMonitorResource = serviceMonitor();
            if (serviceMonitorResource.get() != null) {
                final boolean success = serviceMonitorResource.delete();
                if (success) {
                    LOG.info("Successfully removed ServiceMonitor");
                } else {
                    LOG.warn("Possible problem removing ServiceMonitor");
                }
            }
            
            // Handle Service for operator
            ServiceResource<Service> serviceResource = client.services().withName(operatorServiceName);
            if (serviceResource.get() != null) {
                final boolean success = serviceResource.delete();
                if (success) {
                    LOG.info("Successfully removed Service");
                } else {
                    LOG.warn("Possible problem removing Service");
                }
            }
        }

        resource.setStatus(constructStatus());
        return UpdateControl.updateStatusSubResource(resource);
    }

    private MeterStatus constructStatus() {
        final String currentlyWatching = podWatcher != null ? "TRUE" : "FALSE";
        final String watchedPodCount = podWatcher != null ? podWatcher.watchedPods() : "UNKNOWN";
        final Resource<ServiceMonitor> serviceMonitor = serviceMonitor();
        final String serviceMonitorInstalled = serviceMonitor != null ? (serviceMonitor.get() != null ? "TRUE" : "FALSE") : "UNKNOWN";
        return new MeterStatus(currentlyWatching, watchedPodCount, serviceMonitorInstalled);
    }

    private Resource<ServiceMonitor> serviceMonitor() {
        return client.monitoring().serviceMonitors().inNamespace("openshift-monitoring").withName(serviceMonitorName);
    }

    private boolean invalid(MeterSpec spec) {
        boolean invalid = false;

        if (spec.getMeterCollectionEnabled()) {
            if (spec.getCoreMeterName().isEmpty() && spec.getMemoryMeterName().isEmpty()) {
                invalid = true;
                LOG.warn("Invalid Meter CustomResource, at least one of coreMeterName and memoryMeterName is not set.");
            }
    
            if (spec.getPodLabelIdentifier().isEmpty()) {
                invalid = true;
                LOG.warn("Invalid Meter CustomResource, podLabelIdentifier not set.");
            }
    
            if (spec.getScrapeInterval().isEmpty()) {
                invalid = true;
                LOG.warn("Invalid Meter CustomResource, scrapeInterval not set.");
            }
        }

        return invalid;
    }
}
