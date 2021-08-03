package com.redhat;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Metering {

    private final Set<String> requiredLabels = new HashSet<>();

    public void setup() {
        //TODO Move to config
        // Create label set
        requiredLabels.add("com.redhat.product-name");
        requiredLabels.add("com.redhat.product-version");
        requiredLabels.add("com.redhat.component-name");
        requiredLabels.add("com.redhat.component-type");
        requiredLabels.add("com.redhat.component-version");
    }

    public void shutdown() {
    }
}
