package com.astro.model;

public class ExposureResult {
    public enum Status { UNDER_EXPOSED, OPTIMAL, OVER_EXPOSED }

    public final Status status;
    public final double swampFactor;
    public final double exactTargetTime; // El tiempo ideal calculado
    public final String message;
    public final double readNoise;
    public final double skyElectrons;

    public ExposureResult(Status status, double swampFactor, double exactTargetTime, String message, double rn, double skyElec) {
        this.status = status;
        this.swampFactor = swampFactor;
        this.exactTargetTime = exactTargetTime;
        this.message = message;
        this.readNoise = rn;
        this.skyElectrons = skyElec;
    }
}
