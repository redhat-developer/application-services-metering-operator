package com.redhat;

import java.util.HashSet;
import java.util.Set;

public class MeterSpec {

    private Boolean includeInfrastructure = true;
    private Boolean meterCollectionEnabled = true;
    private Set<String> watchNamespaces = new HashSet<>();

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
        result = prime * result + ((meterCollectionEnabled == null) ? 0 : meterCollectionEnabled.hashCode());
        result = prime * result + ((includeInfrastructure == null) ? 0 : includeInfrastructure.hashCode());
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

        if (meterCollectionEnabled == null) {
            if (other.meterCollectionEnabled != null)
                return false;
        } else if (!meterCollectionEnabled.equals(other.meterCollectionEnabled))
            return false;

        if (includeInfrastructure == null) {
            if (other.includeInfrastructure != null)
                return false;
        } else if (!includeInfrastructure.equals(other.includeInfrastructure))
            return false;

        if (watchNamespaces == null) {
            if (other.watchNamespaces != null)
                return false;
        } else if (!watchNamespaces.equals(other.watchNamespaces))
            return false;

            return true;
    }

}
