package solution;

import java.util.*;

/**
 * class to track live status changes of the overall situation
 * this holds live road graph, vehicle fleet, pending resuces
 */

public class DynamicStateManager {
    private final Graph graph;
    private final List<VehicleState> fleet;
    private final List<VehicleState> fleetView;
    private final List<String> pendingRescues = new ArrayList<>();
    private final Set<String> collapsedLocations = new HashSet<>();

    public DynamicStateManager(Graph graph, SimulatorStaticParameters parameters) {
        this.graph = graph;

        List<VehicleState> vehicles = new ArrayList<>();
        for (int i = 0; i < parameters.getNumberOfVehicles(); i++) {
            // park all the vehicle at the base when the setup
            vehicles.add(new VehicleState(i, parameters.getBaseLocation()));
        }
        this.fleet = vehicles;
        this.fleetView = Collections.unmodifiableList(vehicles);
    }

    // update road damages by removing the relavat edge
    public synchronized void blockRoad(String from, String to) {
        graph.removeEdge(from, to);
    }

    // update collapsed locations by removing a node
    public synchronized void collapseLocation(String location) {
        graph.removeNode(location);
        collapsedLocations.add(location);
    }

    // check if the location is already collapsed
    public synchronized boolean isCollapsed(String location) {
        return collapsedLocations.contains(location);
    }

    // get a vehicle from the fleet
    public synchronized VehicleState getVehicleFromFleet(int id) {
        return fleet.get(id);
    }

    // list all vehicles in the fleet
    public synchronized List<VehicleState> getVehicleFleet() {
        return fleetView;
    }

    // add new rescue
    public synchronized void addPendingRescue(String location) {
        pendingRescues.add(location);
    }

    public synchronized void removePendingRescuesByLocation(String location) {
        pendingRescues.remove(location);
    }

    public synchronized boolean hasPendingRescue(String location) {
        return pendingRescues.contains(location);
    }

    public synchronized List<String> allRescuesList() {
        return new ArrayList<>(pendingRescues);
    }
}
