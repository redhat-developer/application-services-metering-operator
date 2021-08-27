package com.redhat;

import java.util.HashSet;
import java.util.Set;

public class MeterSpec {

    private String cpuMeterName;
    private String podLabelIdentifier;
    private Set<String> meterLabels = new HashSet<>();
    private Boolean removeRedHatMeterLabelPrefix = true;
    private Boolean includeInfrastructure = false;
    private Boolean meterCollectionEnabled = true;
    private Set<String> watchNamespaces = new HashSet<>();

    public String getCpuMeterName() {
        return cpuMeterName;
    }

    public void setCpuMeterName(String cpuMeterName) {
        this.cpuMeterName = cpuMeterName;
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

    public Boolean getRemoveRedHatMeterLabelPrefix() {
        return removeRedHatMeterLabelPrefix;
    }

    public void setRemoveRedHatMeterLabelPrefix(Boolean removeRedHatMeterLabelPrefix) {
        this.removeRedHatMeterLabelPrefix = removeRedHatMeterLabelPrefix;
    }

    public Boolean getIncludeInfrastructure() {
        return includeInfrastructure;
    }

    public void setIncludeInfrastructure(Boolean includeInfrastructure) {
        this.includeInfrastructure = includeInfrastructure;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((cpuMeterName == null) ? 0 : cpuMeterName.hashCode());
        result = prime * result + ((meterCollectionEnabled == null) ? 0 : meterCollectionEnabled.hashCode());
        result = prime * result + ((meterLabels == null) ? 0 : meterLabels.hashCode());
        result = prime * result + ((removeRedHatMeterLabelPrefix == null) ? 0 : removeRedHatMeterLabelPrefix.hashCode());
        result = prime * result + ((includeInfrastructure == null) ? 0 : includeInfrastructure.hashCode());
        result = prime * result + ((podLabelIdentifier == null) ? 0 : podLabelIdentifier.hashCode());
        result = prime * result + ((watchNamespaces == null) ? 0 : watchNamespaces.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

            MeterSpec other = (MeterSpec) obj;
        if (cpuMeterName == null) {
            if (other.cpuMeterName != null)
                return false;
        } else if (!cpuMeterName.equals(other.cpuMeterName))
            return false;

        if (meterCollectionEnabled == null) {
            if (other.meterCollectionEnabled != null)
                return false;
        } else if (!meterCollectionEnabled.equals(other.meterCollectionEnabled))
            return false;

        if (meterLabels == null) {
            if (other.meterLabels != null)
                return false;
        } else if (!meterLabels.equals(other.meterLabels))
            return false;

        if (removeRedHatMeterLabelPrefix == null) {
            if (other.removeRedHatMeterLabelPrefix != null)
                return false;
        } else if (!removeRedHatMeterLabelPrefix.equals(other.removeRedHatMeterLabelPrefix))
            return false;

        if (includeInfrastructure == null) {
            if (other.includeInfrastructure != null)
                return false;
        } else if (!includeInfrastructure.equals(other.includeInfrastructure))
            return false;
    
        if (podLabelIdentifier == null) {
            if (other.podLabelIdentifier != null)
                return false;
        } else if (!podLabelIdentifier.equals(other.podLabelIdentifier))
            return false;

        if (watchNamespaces == null) {
            if (other.watchNamespaces != null)
                return false;
        } else if (!watchNamespaces.equals(other.watchNamespaces))
            return false;

            return true;
    }

}
