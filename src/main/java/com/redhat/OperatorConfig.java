package com.redhat;

import java.util.List;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "operator")
public interface OperatorConfig {
    List<String> productNameMapping();

    String labelPrefix();

    ComponentType componentType();

    interface ComponentType {
        String labelKey();

        String infrastructureValue();
    }
}
