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

    private CpuMeasurer cpuMeasurer;
    private MemoryMeasurer memoryMeasurer;

    public PodWatcher(KubernetesClient client, MeterRegistry meterRegistry, MeterSpec spec) {
        this.meterRegistry = meterRegistry;
        this.spec = spec;
 
        cpuMeasurer = new CpuMeasurer(client);
        memoryMeasurer = new MemoryMeasurer(client);
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

                        // Create Gauge
                        pods.setCpuGauge(Gauge.builder(spec.getCoreMeterName(), pods, cpuMeasurer).tags(tags).register(meterRegistry));
                        pods.setMemoryGauge(Gauge.builder(spec.getMemoryMeterName(), pods, memoryMeasurer).tags(tags).baseUnit(BaseUnits.BYTES).register(meterRegistry));
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
        //TODO: Update spec
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
            if (spec.getMeterLabels().contains(entry.getKey())) {
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
            registry.remove(cpuGauge);
            registry.remove(memoryGauge);
            cpuGauge = null;
            memoryGauge = null;
        }

        public Map<String, String> list() {
            return Collections.unmodifiableMap(pods);
        }
    }

}
