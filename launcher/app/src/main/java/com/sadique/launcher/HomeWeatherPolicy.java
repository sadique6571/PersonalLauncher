package com.sadique.launcher;

final class HomeWeatherPolicy {
    private HomeWeatherPolicy() { }
    static boolean canAttempt(boolean network,boolean internet,boolean validated) {
        return network && (internet || validated);
    }
    static boolean fresh(String value,String storedKey,String requestedKey,long fetchedAt,long now,long ttl) {
        return value!=null && !value.trim().isEmpty() && requestedKey.equals(storedKey)
                && fetchedAt>0 && now>=fetchedAt && now-fetchedAt<ttl;
    }
}
