package com.redhat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

@ApplicationScoped
public class Metering {

    @Inject
    KubernetesClient client;

    @Inject
    MeterRegistry meterRegistry;

    //TODO Move to config
    private final String metricName = "openshift-subscription.app.services";

    private final Set<String> requiredLabels = new HashSet<>();

    private Map<Tags, PodGroup> metrics = new ConcurrentHashMap<>();

    private CpuMeasurer measurer;

    private Watch podWatcher;

    public void setup() {
        //TODO Move to config
        // Create label set
        requiredLabels.add("com.redhat.product-name");
        requiredLabels.add("com.redhat.product-version");
        requiredLabels.add("com.redhat.component-name");
        requiredLabels.add("com.redhat.component-type");
        requiredLabels.add("com.redhat.component-version");

        measurer = new CpuMeasurer(client);

        // Setup watcher
        podWatcher = client.pods().inAnyNamespace().watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod resource) {
                if (resource.getMetadata().getLabels().containsKey("com.redhat.product-name")) {
                    // Get/Create metric                
                    Tags tags = generateTags(resource.getMetadata().getLabels());
                    PodGroup pods = metrics.get(tags);

                    switch (action) {
                        case ADDED:
                            if (pods == null) {
                                pods = new PodGroup();
                                pods.addPod(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
                                metrics.put(tags, pods);

                                // Create Gauge
                                // meterRegistry.gauge(metricName, tags, pods, measurer);
                                pods.setGauge(Gauge.builder(metricName, pods, measurer).tags(tags).register(meterRegistry));
                            } else {
                                pods.addPod(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
                            }
                            break;
                        case DELETED:
                            if (pods != null) {
                                pods.removePod(resource.getMetadata().getName());

                                if (pods.list().size() == 0) {
                                    // Remove the pod and clear Gauge meter
                                    metrics.remove(tags).removeGauge(meterRegistry);
                                    //TODO This might need to have a delay by a few scrapes at 0 before removal?
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }
            }

            @Override
            public void onClose(WatcherException cause) {
            }
        });
    }

    public void shutdown() {
        podWatcher.close();
    }

    private Tags generateTags(Map<String, String> labels) {
        Tags metricTags = Tags.empty();

        for (Entry<String, String> entry : labels.entrySet()) {
            if (requiredLabels.contains(entry.getKey())) {
                metricTags = metricTags.and(entry.getKey(), entry.getValue());
            }
        }

        return metricTags;
    }

    static class CpuMeasurer implements ToDoubleFunction<PodGroup> {
        private final KubernetesClient client;

        public CpuMeasurer(KubernetesClient client) {
            this.client = client;
        }

        @Override
        public double applyAsDouble(PodGroup value) {
            BigDecimal cpuCount = new BigDecimal(0);

            for (Entry<String, String> entry : value.list().entrySet()) {
                for (ContainerMetrics metrics : client.top().pods().metrics(entry.getValue(), entry.getKey()).getContainers()) {
                    cpuCount = cpuCount.add(Quantity.getAmountInBytes(metrics.getUsage().get("cpu")));
                }
            }

            return cpuCount.doubleValue();
        }

    }

    static class PodGroup {
        private final Map<String, String> pods = new HashMap<>();
        private Gauge gauge;

        public void removePod(String podName) {
            pods.remove(podName);
        }

        public void addPod(String podName, String namespace) {
            pods.put(podName, namespace);
        }

        public void setGauge(Gauge gauge) {
            this.gauge = gauge;
        }

        public void removeGauge(MeterRegistry registry) {
            registry.remove(gauge);
            gauge = null;
        }

        public Map<String, String> list() {
            return Collections.unmodifiableMap(pods);
        }
    }
}
