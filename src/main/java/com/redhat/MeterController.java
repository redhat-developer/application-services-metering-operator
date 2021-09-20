package com.redhat;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Role;
import io.fabric8.openshift.api.model.RoleBinding;
import io.fabric8.openshift.api.model.RoleBindingBuilder;
import io.fabric8.openshift.api.model.RoleBuilder;
import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitor;
import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitorBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.api.Context;
import io.micrometer.core.instrument.MeterRegistry;

@Controller
public class MeterController implements ResourceController<Meter> {
    private static final Logger LOG = Logger.getLogger(MeterController.class);

    private static final String ROLE_NAME = "application-services-operator-metrics-reader";
    private static final String ROLE_BINDING_NAME = "read-application-services-operator-metrics";
    private static final String SERVICE_MONITOR_NAME = "application-services-operator-metrics";
    private static final String OPENSHIFT_MONITORING_NAMESPACE = "openshift-monitoring";

    private final MeterRegistry meterRegistry;
    private final OpenShiftClient client;
    private final OperatorConfig config;
    private final String applicationName;

    private PodWatcher podWatcher;
    private Watch watchHandle;

    public MeterController(OpenShiftClient client, MeterRegistry meterRegistry, OperatorConfig config,
        @ConfigProperty(name = "quarkus.application.name") String applicationName) {
        this.client = client;
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.applicationName = applicationName;
    }

    @Override
    public DeleteControl deleteResource(Meter resource, Context<Meter> context) {
        LOG.info("Meter CustomResource deleted.");
        if (watchHandle != null) {
            watchHandle.close();

            if (podWatcher != null) {
                podWatcher = null;
            }
        }

        deleteServiceMonitor();

        return DeleteControl.DEFAULT_DELETE;
    }

    @Override
    public UpdateControl<Meter> createOrUpdateResource(Meter resource, Context<Meter> context) {
        if (resource.isMarkedForDeletion()) {
            LOG.info("Meter CustomResource marked for deletion, no reconciliation performed.");
            return UpdateControl.noUpdate();
        }

        final MeterSpec spec = resource.getSpec();

        if (spec.getMeterCollectionEnabled()) {
            // Meter Collection enabled
            LOG.info("Meter collection enabled.");
            createServiceMonitor();

            // Handle PodWatcher
            if (podWatcher != null) {
                // Update existing watcher
                LOG.info("Updating Meter spec in PodWatcher.");
                podWatcher.updateSpec(spec);
            } else {
                // Set up new watcher
                LOG.info("Creating a new PodWatcher.");
                podWatcher = new PodWatcher(client, meterRegistry, spec, config);
                watchHandle = client.pods().inAnyNamespace().watch(podWatcher);
            }
        } else {
            // Meter collection disabled
            LOG.info("Meter collection disabled.");
            deleteServiceMonitor();

            // Handle Watcher
            if (watchHandle != null) {
                watchHandle.close();
                LOG.info("Stopped watching for events. No further metrics captured.");

                if (podWatcher != null) {
                    podWatcher = null;
                }
            }
        }

        resource.setStatus(constructStatus());
        return UpdateControl.updateStatusSubResource(resource);
    }

    void createServiceMonitor() {
        // Create Role
        Role role = new RoleBuilder()
                .withNewMetadata()
                    .withName(ROLE_NAME)
                .endMetadata()
                .addNewRule()
                    .addNewApiGroup("")
                    .withResources("pods", "services", "endpoints")
                    .withVerbs("get", "list", "watch")
                .endRule()
                .build();
        client.roles().createOrReplace(role);

        // Create RoleBinding
        RoleBinding roleBinding = new RoleBindingBuilder()
                .withNewMetadata()
                    .withName(ROLE_BINDING_NAME)
                .endMetadata()
                .addNewSubject()
                    .withKind("ServiceAccount")
                    .withName("prometheus-k8s")
                    .withNamespace(OPENSHIFT_MONITORING_NAMESPACE)
                .endSubject()
                .withNewRoleRef()
                    .withKind("Role")
                    .withName(ROLE_NAME)
                .endRoleRef()
                .build();
        client.roleBindings().createOrReplace(roleBinding);

        // Create ServiceMonitor
        ServiceMonitor monitor = new ServiceMonitorBuilder()
                .withNewMetadata()
                    .withName(SERVICE_MONITOR_NAME)
                    .withNamespace(OPENSHIFT_MONITORING_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                    .addNewEndpoint()
                        .withScheme("http")
                        .withPort("http")
                        .withPath("/q/metrics")
                        .withInterval(config.scrapeInterval())
                    .endEndpoint()
                    .withNewNamespaceSelector()
                        .withMatchNames(client.getNamespace())
                    .endNamespaceSelector()
                    .withNewSelector()
                        .withMatchLabels(Map.of("app.kubernetes.io/name", applicationName))
                    .endSelector()
                .endSpec()
                .build();
        client.monitoring().serviceMonitors().inNamespace(OPENSHIFT_MONITORING_NAMESPACE).createOrReplace(monitor);

        LOG.info("ServiceMonitor " + SERVICE_MONITOR_NAME + " installed.");
    }

    void deleteServiceMonitor() {
        // Delete ServiceMonitor
        Resource<ServiceMonitor> serviceMonitorResource = serviceMonitor();
        if (serviceMonitorResource.get() != null) {
            serviceMonitorResource.delete();
            LOG.info("ServiceMonitor " + SERVICE_MONITOR_NAME + " un-installed.");
        }

        // Delete RoleBinding
        Resource<RoleBinding> roleBindingResource = client.roleBindings().withName(ROLE_BINDING_NAME);
        if (roleBindingResource.get() != null) {
            roleBindingResource.delete();
        }

        // Delete Role
        Resource<Role> roleResource = client.roles().withName(ROLE_NAME);
        if (roleResource.get() != null) {
            roleResource.delete();
        }
    }

    // Testing purposes only
    PodWatcher getWatcher() {
        return podWatcher;
    }

    private MeterStatus constructStatus() {
        final String currentlyWatching = podWatcher != null ? "TRUE" : "FALSE";
        final String watchedPodCount = podWatcher != null ? podWatcher.watchedPods() : "UNKNOWN";
        final Resource<ServiceMonitor> serviceMonitor = serviceMonitor();
        final String serviceMonitorInstalled = serviceMonitor != null ? (serviceMonitor.get() != null ? "TRUE" : "FALSE") : "UNKNOWN";
        return new MeterStatus(currentlyWatching, watchedPodCount, serviceMonitorInstalled);
    }

    private Resource<ServiceMonitor> serviceMonitor() {
        return client.monitoring().serviceMonitors().inNamespace(OPENSHIFT_MONITORING_NAMESPACE).withName(SERVICE_MONITOR_NAME);
    }
}
