package com.redhat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.monitoring.v1.PrometheusRule;
import io.fabric8.openshift.api.model.monitoring.v1.PrometheusRuleBuilder;
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
    private static final String PROMETHEUS_RULES_NAME = "application-services-operator-metrics-prometheus-rules";

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
        if (serviceMonitor().get() != null) {
            System.out.println("SKIPPED INSTALL");
            // Don't repeat the install
            return;
        }

        try {
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
            role = client.rbac().roles().createOrReplace(role);

            // TODO remove
            System.out.println("ROLE RESOURCE VERSION: " + role.getMetadata().getResourceVersion());
            if (role.getMetadata().getResourceVersion() == null) {
                // No exception, but Role not created properly
                return;
            }
        } catch (Exception e) {
            LOG.error("Failed to create Role", e);
            //TODO Uncomment when permissions are fixed
//            return;
        }

        try {
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
            roleBinding = client.rbac().roleBindings().createOrReplace(roleBinding);

            // TODO remove
            System.out.println("ROLEBINDING RESOURCE VERSION: " + roleBinding.getMetadata().getResourceVersion());

            if (roleBinding.getMetadata().getResourceVersion() == null) {
                // No exception, but RoleBinding not created properly
                return;
            }
        } catch (Exception e) {
            LOG.error("Failed to create RoleBinding", e);
            //TODO Uncomment when permissions are fixed
//            return;
        }

        try {
            // Create Prometheus Rule
            PrometheusRule promRule = new PrometheusRuleBuilder()
                    .withNewMetadata()
                        .withName(PROMETHEUS_RULES_NAME)
                        .withNamespace(OPENSHIFT_MONITORING_NAMESPACE)
                    .endMetadata()
                    .withNewSpec()
                        .addNewGroup()
                            .withName("application-services.rules")
                            .addNewRule()
                                .withNewExpr("sum by (prod_name) (appsvcs_cpu_usage_cores)")
                                .withRecord("appsvcs:cores_by_product:sum")
                            .endRule()
                        .endGroup()
                    .endSpec()
                    .build();
            promRule = client.monitoring().prometheusRules().inNamespace(OPENSHIFT_MONITORING_NAMESPACE).createOrReplace(promRule);

            // TODO remove
            System.out.println("PROMETHEUSRULE RESOURCE VERSION: " + promRule.getMetadata().getResourceVersion());
            if (promRule.getMetadata().getResourceVersion() == null) {
                // No exception, but PrometheusRule not created properly
                return;
            }
        } catch (Exception e) {
            LOG.error("Failed to create PrometheusRule", e);
            //TODO Uncomment when permissions are fixed
            // return;
        }

        try {
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
            monitor = client.monitoring().serviceMonitors().inNamespace(OPENSHIFT_MONITORING_NAMESPACE).createOrReplace(monitor);
            // TODO remove
            System.out.println("SERVICEMONITOR RESOURCE VERSION: " + monitor.getMetadata().getResourceVersion());

            if (!monitor.getMetadata().getResourceVersion().isEmpty()) {
                LOG.info("ServiceMonitor " + SERVICE_MONITOR_NAME + " installed.");
            }
        } catch (Exception e) {
            LOG.error("Failed to create ServiceMonitor", e);
        }
    }

    // These are safe to execute even if all of them are not there
    void deleteServiceMonitor() {
        // Delete ServiceMonitor
        Resource<ServiceMonitor> serviceMonitorResource = serviceMonitor();
        if (serviceMonitorResource.get() != null) {
            serviceMonitorResource.delete();
            LOG.info("ServiceMonitor " + SERVICE_MONITOR_NAME + " un-installed.");
        }

        // Delete PrometheusRule
        Resource<PrometheusRule> prometheusRuleResource = client.monitoring().prometheusRules().withName(PROMETHEUS_RULES_NAME);
        if (prometheusRuleResource.get() != null) {
            prometheusRuleResource.delete();
        }

        // Delete RoleBinding
        Resource<RoleBinding> roleBindingResource = client.rbac().roleBindings().withName(ROLE_BINDING_NAME);
        if (roleBindingResource.get() != null) {
            roleBindingResource.delete();
        }

        // Delete Role
        Resource<Role> roleResource = client.rbac().roles().withName(ROLE_NAME);
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
