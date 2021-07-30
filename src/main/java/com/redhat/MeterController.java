package com.redhat;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

@Controller
public class MeterController implements ResourceController<Meter> {

    private final KubernetesClient client;

    public MeterController(KubernetesClient client) {
        this.client = client;
    }

    // TODO Fill in the rest of the controller

    @Override
    public void init(EventSourceManager eventSourceManager) {
        // TODO: fill in init
    }

    @Override
    public UpdateControl<Meter> createOrUpdateResource(
        Meter resource, Context<Meter> context) {
        // TODO: fill in logic

        return UpdateControl.noUpdate();
    }
}

