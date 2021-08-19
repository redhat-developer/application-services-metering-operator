package com.redhat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;

class PodWatcher implements Watcher<Pod> {

    private final MeterRegistry meterRegistry;
    private MeterSpec spec;
    private Map<Tags, PodGroup> metrics = new ConcurrentHashMap<>();

    private final CpuMeasurer cpuMeasurer;
    private final MemoryMeasurer memoryMeasurer;

    private Boolean cpuMeterActive;
    private Boolean memoryMeterActive;

    public PodWatcher(KubernetesClient client, MeterRegistry meterRegistry, MeterSpec spec) {
        this.meterRegistry = meterRegistry;
        this.spec = spec;
 
        cpuMeasurer = new CpuMeasurer(client);
        memoryMeasurer = new MemoryMeasurer(client);

        cpuMeterActive = !spec.getCpuMeterName().isBlank();
        memoryMeterActive = !spec.getMemoryMeterName().isBlank();
    }

    @Override
    public void eventReceived(Action action, Pod resource) {
        if (!(spec.getWatchNamespaces().isEmpty() || spec.getWatchNamespaces().contains(resource.getMetadata().getNamespace()))) {
            // If we're not watching all namespaces or the event is from a namespace we're not watching, do nothing
            return;
        }

        if (resource.getMetadata().getLabels().containsKey(spec.getPodLabelIdentifier())) {
            // Get/Create metric                
            Tags tags = generateTags(resource.getMetadata().getLabels());
            PodGroup pods = metrics.get(tags);

            switch (action) {
                case ADDED:
                    if (pods == null) {
                        pods = new PodGroup();
                        pods.addPod(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
                        metrics.put(tags, pods);

                        // Create Gauges
                        if (cpuMeterActive) {
                            pods.setCpuGauge(
                                Gauge.builder(spec.getCpuMeterName(), pods, cpuMeasurer)
                                    .tags(tags)
                                    .register(meterRegistry));
                        }
                        if (memoryMeterActive) {
                            pods.setMemoryGauge(
                                Gauge.builder(spec.getMemoryMeterName(), pods, memoryMeasurer)
                                    .tags(tags)
                                    .baseUnit(BaseUnits.BYTES)
                                    .register(meterRegistry));
                        }
                    } else {
                        pods.addPod(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
                    }
                    break;
                case DELETED:
                    if (pods != null) {
                        pods.removePod(resource.getMetadata().getName());

                        if (pods.list().size() == 0) {
                            // Remove the pod and clear Gauge meter
                            metrics.remove(tags).removeGauges(meterRegistry);
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
    public void onClose(WatcherException cause) {
        for (PodGroup podGroup : metrics.values()) {
            meterRegistry.remove(podGroup.cpuGauge);
            meterRegistry.remove(podGroup.memoryGauge);
        }

        metrics.clear();
    }

    void updateSpec(MeterSpec newSpec) {
        if (newSpec.equals(spec)) {
            // Specs are identical, no updates needed
            return;
        }

        // Update the watcher based on changes in the spec
        // Note: Does not support:
        //  - Changes in cpuMeterName or memoryMeterName values, only whether the name switches between present and not, or vice versa
        //  - Update existing meters if podLabelIdentifier changes
        //  - Update existing meters if meterLabels changes
        //  - Update existing meters if watchNamespaces changes
        // All these scenarios can be handled by setting meterCollectionEnabled to false, which removes the watcher, and then to true with
        //  the adjusted settings for watching

        // Check meter names
        final Boolean cpuMeterActive = !newSpec.getCpuMeterName().isBlank();
        final Boolean memoryMeterActive = !newSpec.getMemoryMeterName().isBlank();

        if (this.cpuMeterActive != cpuMeterActive || this.memoryMeterActive != memoryMeterActive) {
            // Handle change
            for (Entry<Tags, PodGroup> entry : metrics.entrySet()) {
                if (this.cpuMeterActive && !cpuMeterActive) {
                    // CPU Meter no longer tracked
                    entry.getValue().removeCpuGauge(meterRegistry);
                } else {
                    // CPU Meter now being tracked
                    entry.getValue().setCpuGauge(
                        Gauge.builder(spec.getCpuMeterName(), entry.getValue(), cpuMeasurer)
                            .tags(entry.getKey())
                            .register(meterRegistry));
                }

                if (this.memoryMeterActive && !memoryMeterActive) {
                    // Memory Meter no longer tracked
                    entry.getValue().removeMemoryGauge(meterRegistry);
                } else {
                    // Memory Meter now being tracked
                    entry.getValue().setMemoryGauge(
                        Gauge.builder(spec.getMemoryMeterName(), entry.getValue(), memoryMeasurer)
                            .tags(entry.getKey())
                            .baseUnit(BaseUnits.BYTES)
                            .register(meterRegistry));
                }
            }
        }

        // Update the MeterSpec instance
        spec = newSpec;
        this.cpuMeterActive = cpuMeterActive;
        this.memoryMeterActive = memoryMeterActive;
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
            if (spec.getMeterLabels().contains(labelName)) {
                metricTags = metricTags.and(labelName, entry.getValue());
            }
        }

        return metricTags;
    }

    private String stripLabelPrefix(String labelName) {
        if (labelName.startsWith("com.redhat.") && spec.getRemoveRedHatMeterLabelPrefix()) {
            return labelName.substring(10);
        }

        return labelName;
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

    static class MemoryMeasurer implements ToDoubleFunction<PodGroup> {
        private final KubernetesClient client;

        public MemoryMeasurer(KubernetesClient client) {
            this.client = client;
        }

        @Override
        public double applyAsDouble(PodGroup value) {
            BigDecimal memoryCount = new BigDecimal(0);

            for (Entry<String, String> entry : value.list().entrySet()) {
                for (ContainerMetrics metrics : client.top().pods().metrics(entry.getValue(), entry.getKey()).getContainers()) {
                    memoryCount = memoryCount.add(Quantity.getAmountInBytes(metrics.getUsage().get("memory")));
                }
            }

            return memoryCount.doubleValue();
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
