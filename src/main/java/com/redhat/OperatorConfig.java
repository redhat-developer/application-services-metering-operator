package com.redhat;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "operator")
public interface OperatorConfig {
    List<String> productNameMapping();

    List<String> allowedProductNames();

    String scrapeInterval();

    MeterConfig meter();

    PodConfig pod();

    interface MeterConfig {
        String cpu();

        List<String> labels();
    }

    interface PodConfig {
        String identifier();

        Optional<String> labelPrefix();

        Optional<Boolean> removeLabelPrefix();

        String componentTypeLabel();

        String componentTypeInfrastructure();
    }
}
