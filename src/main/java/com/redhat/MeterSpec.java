package com.redhat;

import java.util.HashSet;
import java.util.Set;

public class MeterSpec {

    //TODO Can this be defaulted to a value that's overridable by CR?
    private String meterNamePrefix;
    private String podLabelIdentifier;
    private Set<String> meterLabels = new HashSet<>();
    private Boolean meterCollectionEnabled = true;
    private Set<String> watchNamespaces = new HashSet<>();
    private String scrapeInterval;

    public String getMeterNamePrefix() {
        return meterNamePrefix;
    }

    public void setMeterNamePrefix(String meterNamePrefix) {
        this.meterNamePrefix = meterNamePrefix;
    }

    public String getPodLabelIdentifier() {
        return podLabelIdentifier;
    }

    public void setPodLabelIdentifier(String podLabelIdentifier) {
        this.podLabelIdentifier = podLabelIdentifier;
    }

    public Set<String> getMeterLabels() {
        return meterLabels;
    }

    public void setMeterLabels(Set<String> meterLabels) {
        this.meterLabels = meterLabels;
    }

    public Boolean getMeterCollectionEnabled() {
        return meterCollectionEnabled;
    }

    public void setMeterCollectionEnabled(Boolean meterCollectionEnabled) {
        this.meterCollectionEnabled = meterCollectionEnabled;
    }

    public Set<String> getWatchNamespaces() {
        return watchNamespaces;
    }

    public void setWatchNamespaces(Set<String> watchNamespaces) {
        this.watchNamespaces = watchNamespaces;
    }

    public String getScrapeInterval() {
        return scrapeInterval;
    }

    public void setScrapeInterval(String scrapeInterval) {
        this.scrapeInterval = scrapeInterval;
    }
}
