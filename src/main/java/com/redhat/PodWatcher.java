package com.redhat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

class PodWatcher implements Watcher<Pod> {
    private static final Logger LOG = Logger.getLogger(PodWatcher.class);

    private final KubernetesClient client;
    private final MeterRegistry meterRegistry;
    private final OperatorConfig config;
    private MeterSpec spec;
    private Map<Tags, PodGroup> metrics = new ConcurrentHashMap<>();
    private final Map<String, String> productNameMapping;

    private final CpuMeasurer cpuMeasurer;

    public PodWatcher(KubernetesClient client, MeterRegistry meterRegistry, MeterSpec spec, OperatorConfig config) {
        this.client = client;
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.spec = spec;
        this.productNameMapping = convertListToMap(config.productNameMapping());
 
        cpuMeasurer = new CpuMeasurer(client);
    }

    static Map<String, String> convertListToMap(List<String> productNameMapAsList) {
        if (productNameMapAsList == null) {
            return Collections.emptyMap();
        }

        return productNameMapAsList.stream()
            .filter(productNames -> !productNames.isEmpty())
            .map(keyValuePair -> keyValuePair.split("=", 2))
            .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
    }

    @Override
    public void eventReceived(Action action, Pod resource) {
        if (!shouldWatch(spec.getWatchNamespaces(), resource.getMetadata().getNamespace())) {
            // If we're not watching all namespaces or the event is from a namespace we're not watching, do nothing
            return;
        }

        if (resource.getMetadata().getLabels().containsKey(config.pod().identifier())) {
            // Get/Create metric                
            Tags tags = generateTags(resource.getMetadata().getLabels());
            PodGroup podGroup = metrics.get(tags);

            switch (action) {
                case ADDED:
                    if (includePod(config, resource.getMetadata().getLabels(), spec)) {
                        LOG.trace("Adding pod to metrics gathering: " + resource.getMetadata().getName() + " in " + resource.getMetadata().getNamespace());
                        if (podGroup == null) {
                            podGroup = new PodGroup();
                            podGroup.addPod(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
                            metrics.put(tags, podGroup);
    
                            // Create Gauge
                            podGroup.setCpuGauge(
                                Gauge.builder(config.meter().cpu(), podGroup, cpuMeasurer)
                                    .tags(tags)
                                    .register(meterRegistry));
                        } else {
                            podGroup.addPod(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
                        }
                    }
                    break;
                case DELETED:
                    if (podGroup != null) {
                        podGroup.removePod(resource.getMetadata().getName());

                        if (podGroup.list().size() == 0) {
                            // Remove the pod and clear cpu Gauge meter
                            metrics.remove(tags).removeCpuGauge(meterRegistry);
                            //TODO This might need to have a delay by a few scrapes at 0 before removal? Waiting on feedback from Todd
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onClose() {
        meterRegistry.clear();
        metrics.clear();
    }

    @Override
    public void onClose(WatcherException cause) {
        onClose();
    }

    Boolean shouldWatch(Set<String> watchingNamespaces, String namespace) {
        return watchingNamespaces.isEmpty() || watchingNamespaces.contains(namespace)
            || watchingNamespaces.contains("");
    }

    static Boolean isInfrastructure(OperatorConfig config, Map<String, String> podLabels) {
        if (podLabels.containsKey(config.pod().componentTypeLabel())) {
            if (podLabels.get(config.pod().componentTypeLabel()).equals(config.pod().componentTypeInfrastructure())) {
                return true;
            }
        }
        return false;
    }

    Boolean includePod(OperatorConfig config, Map<String, String> podLabels, MeterSpec spec) {
        if (!spec.getIncludeInfrastructure() && isInfrastructure(config, podLabels)) {
            return false;
        }

        final String productName = mapProductNames(podLabels.get(config.pod().identifier()));
        if (!config.allowedProductNames().contains(productName)) {
            return false;
        }

        return true;
    }

    void updateSpec(MeterSpec newSpec) {
        if (newSpec.equals(spec)) {
            // Specs are identical, no updates needed
            return;
        }

        // Recreate metrics
        meterRegistry.clear();
        metrics.clear();

        PodList pods = client.pods().inAnyNamespace().list();
        for (Pod pod : pods.getItems()) {
            if (!shouldWatch(newSpec.getWatchNamespaces(), pod.getMetadata().getNamespace())) {
                // If we're not watching all namespaces or the event is from a namespace we're not watching, do nothing
                continue;
            }
    
            if (pod.getMetadata().getLabels().containsKey(config.pod().identifier())) {
                // Get/Create metric                
                Tags tags = generateTags(pod.getMetadata().getLabels());
                PodGroup podGroup = metrics.get(tags);

                if (includePod(config, pod.getMetadata().getLabels(), newSpec)) {
                    LOG.trace("Adding pod to metrics gathering: " + pod.getMetadata().getName() + " in " + pod.getMetadata().getNamespace());
                    if (podGroup == null) {
                        podGroup = new PodGroup();
                        podGroup.addPod(pod.getMetadata().getName(), pod.getMetadata().getNamespace());
                        metrics.put(tags, podGroup);

                        // Create Gauge
                        podGroup.setCpuGauge(
                            Gauge.builder(config.meter().cpu(), podGroup, cpuMeasurer)
                                .tags(tags)
                                .register(meterRegistry));
                    } else {
                        podGroup.addPod(pod.getMetadata().getName(), pod.getMetadata().getNamespace());
                    }
                }
            }
        }

        // Update the MeterSpec instance
        spec = newSpec;
    }

    String watchedPods() {
        int count = 0;
        for (PodGroup podGroup : metrics.values()) {
            count += podGroup.pods.size();
        }
        return Integer.toString(count);
    }
    
    private Tags generateTags(Map<String, String> labels) {
        Tags metricTags = Tags.empty();

        for (Entry<String, String> entry : labels.entrySet()) {
            final String labelName = stripLabelPrefix(entry.getKey());
            if (config.meter().labels().contains(labelName)) {
                metricTags = metricTags.and(labelName, mapProductNames(entry.getValue()));
            }
        }

        return metricTags;
    }

    private String stripLabelPrefix(String labelName) {
        if (config.pod().removeLabelPrefix().isPresent() && config.pod().labelPrefix().isPresent()) {
            if (config.pod().removeLabelPrefix().get() && labelName.startsWith(config.pod().labelPrefix().get())) {
                return labelName.substring(config.pod().labelPrefix().get().length());
            }
        }

        return labelName;
    }

    // Should be a "hack" that can be removed when we're communicating with our own Tenant for metrics collection
    private String mapProductNames(String labelValue) {
        if (productNameMapping.containsKey(labelValue)) {
            return productNameMapping.get(labelValue);
        }

        return labelValue;
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
                if (client.pods().inNamespace(entry.getValue()).withName(entry.getKey()).isReady()) {
                    try {
                        for (ContainerMetrics metrics : client.top().pods().metrics(entry.getValue(), entry.getKey()).getContainers()) {
                            Quantity qty = metrics.getUsage().get("cpu");
                            if (qty != null) {
                                cpuCount = cpuCount.add(Quantity.getAmountInBytes(qty));
                            }
                        }
                    } catch (KubernetesClientException kce) {
                        // Ignore, as it likely means a pod is "ready", but no metrics available yet
                        // Log a debug message in case it's an error of a different kind
                        LOG.debug(kce);
                    }
                } else {
                    // Ignore, as the pod is not "ready"
                }
            }

            return cpuCount.doubleValue();
        }

    }

    static class PodGroup {
        // Key - Pod name, Value - Pod namespace
        private final Map<String, String> pods = new HashMap<>();
        private Gauge cpuGauge;
        private Gauge memoryGauge;

        public void removePod(String podName) {
            pods.remove(podName);
        }

        public void addPod(String podName, String namespace) {
            pods.put(podName, namespace);
        }

        public void setCpuGauge(Gauge gauge) {
            cpuGauge = gauge;
        }

        public void setMemoryGauge(Gauge gauge) {
            memoryGauge = gauge;
        }

        public void removeGauges(MeterRegistry registry) {
            removeCpuGauge(registry);
            removeMemoryGauge(registry);
        }

        public void removeCpuGauge(MeterRegistry registry) {
            registry.remove(cpuGauge);
            cpuGauge = null;
        }

        public void removeMemoryGauge(MeterRegistry registry) {
            registry.remove(memoryGauge);
            memoryGauge = null;
        }

        public Map<String, String> list() {
            return Collections.unmodifiableMap(pods);
        }
    }

}
