package com.redhat;

import java.util.List;
import java.util.Optional;

import com.redhat.OperatorConfig.MeterConfig;
import com.redhat.OperatorConfig.PodConfig;

public class TestUtil {
    private TestUtil() {
    }

    public static MeterConfig meterConfig(final String cpuName, final List<String> labels) {
        return new OperatorConfig.MeterConfig() {

            @Override
            public String cpu() {
                return cpuName;
            }

            @Override
            public List<String> labels() {
                return labels;
            }

        };
    }

    public static MeterConfig emptyMeterConfig() {
        return new OperatorConfig.MeterConfig() {

            @Override
            public String cpu() {
                return null;
            }

            @Override
            public List<String> labels() {
                return null;
            }

        };
    }

    public static PodConfig podConfig(final String identifier, final String labelPrefix,
            final Boolean removeLabelPrefix, final String componentTypeLabel,
            final String componentTypeInfrastructure) {
        return new PodConfig() {

            @Override
            public String identifier() {
                return identifier;
            }

            @Override
            public Optional<String> labelPrefix() {
                return Optional.of(labelPrefix);
            }

            @Override
            public Optional<Boolean> removeLabelPrefix() {
                return Optional.of(removeLabelPrefix);
            }

            @Override
            public String componentTypeLabel() {
                return componentTypeLabel;
            }

            @Override
            public String componentTypeInfrastructure() {
                return componentTypeInfrastructure;
            }

        };
    }

    public static PodConfig emptyPodConfig() {
        return new PodConfig() {

            @Override
            public String identifier() {
                return null;
            }

            @Override
            public Optional<String> labelPrefix() {
                return null;
            }

            @Override
            public Optional<Boolean> removeLabelPrefix() {
                return null;
            }

            @Override
            public String componentTypeLabel() {
                return null;
            }

            @Override
            public String componentTypeInfrastructure() {
                return null;
            }

        };
    }

    public static OperatorConfig operatorConfig(final List<String> productNameMapping,
            final List<String> allowedProductNames,
            final MeterConfig meterConfig, final PodConfig podConfig) {
        return new OperatorConfig() {

            @Override
            public List<String> productNameMapping() {
                return productNameMapping;
            }

            @Override
            public String scrapeInterval() {
                return "10s";
            }

            @Override
            public MeterConfig meter() {
                return meterConfig;
            }

            @Override
            public PodConfig pod() {
                return podConfig;
            }

            @Override
            public List<String> allowedProductNames() {
                return allowedProductNames;
            }

        };
    }

    public static OperatorConfig emptyOperatorConfig() {
        return new OperatorConfig() {

            @Override
            public List<String> productNameMapping() {
                return null;
            }

            @Override
            public String scrapeInterval() {
                return null;
            }

            @Override
            public MeterConfig meter() {
                return emptyMeterConfig();
            }

            @Override
            public PodConfig pod() {
                return emptyPodConfig();
            }

            @Override
            public List<String> allowedProductNames() {
                return null;
            }

        };
    }
}
