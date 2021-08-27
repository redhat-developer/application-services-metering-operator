package com.redhat;

public class MeterStatus {

    private String currentlyWatching;
    private String watchedPodCount;

    public MeterStatus() {
    }

    public MeterStatus(String currentlyWatching, String watchedPodCount) {
        this.currentlyWatching = currentlyWatching;
        this.watchedPodCount = watchedPodCount;
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
}
