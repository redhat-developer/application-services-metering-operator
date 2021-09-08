package com.redhat;

public class MeterStatus {

    private String currentlyWatching;
    private String watchedPodCount;
    private String serviceMonitorInstalled;

    public MeterStatus() {
    }

    public MeterStatus(String currentlyWatching, String watchedPodCount, String serviceMonitorInstalled) {
        this.currentlyWatching = currentlyWatching;
        this.watchedPodCount = watchedPodCount;
        this.serviceMonitorInstalled = serviceMonitorInstalled;
    }

    public String getCurrentlyWatching() {
        return currentlyWatching;
    }

    public void setCurrentlyWatching(String currentlyWatching) {
        this.currentlyWatching = currentlyWatching;
    }

    public String getWatchedPods() {
        return watchedPodCount;
    }

    public void setWatchedPods(String meterCount) {
        this.watchedPodCount = meterCount;
    }

    public String getServiceMonitorInstalled() {
        return serviceMonitorInstalled;
    }

    public void setServiceMonitorInstalled(String serviceMonitorInstalled) {
        this.serviceMonitorInstalled = serviceMonitorInstalled;
    }
}
