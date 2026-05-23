package solution;

import util.ConfigurationInfo;

import java.util.Properties;

/**
 * Static parameters for the simulator
 */

public class SimulatorStaticParameters {
    private final String mapFile;
    private final String base;
    private final int numberOfVehicles;
    private final long rescueDurationTicks;
    private final double vehicleSpeed;

    private SimulatorStaticParameters(String mapFile, String base, int numberOfVehicles, long rescueDurationTicks, double vehicleSpeed) {
        this.mapFile = mapFile;
        this.base = base;
        this.numberOfVehicles = numberOfVehicles;
        this.rescueDurationTicks = rescueDurationTicks;
        this.vehicleSpeed = vehicleSpeed;
    }

    // build parameters from the config
    public static SimulatorStaticParameters fromConfig(String configFile) {
        String mapFile = ConfigurationInfo.getMapFile(configFile);
        String base = ConfigurationInfo.getOrigin(configFile);
        int numberOfVehicles = ConfigurationInfo.NUMBER_OF_VEHICLES;

        Properties cfg = ConfigurationInfo.loadConfig(configFile);
        long rescueDurationTicks = parseLong(cfg.getProperty("RESCUE_DURATION", "0"), 0) * 1000L;
        double vehicleSpeed = parseDouble(cfg.getProperty("VEHICLE_SPEED", "0.2"), 0.2);

        return new SimulatorStaticParameters(mapFile, base, numberOfVehicles, rescueDurationTicks, vehicleSpeed);
    }

    public String getMapFile() {
        return mapFile;
    }

    public String getBaseLocation() {
        return base;
    }

    public int getNumberOfVehicles() {
        return numberOfVehicles;
    }

    public long getRescueDurationTicks() {
        return rescueDurationTicks;
    }

    public double getVehicleSpeed() {
        return vehicleSpeed;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }
}
