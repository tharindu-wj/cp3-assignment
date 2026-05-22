package solution;

import org.jdom2.JDOMException;
import sim.Message;
import util.ConfigurationInfo;

import java.io.IOException;
import java.util.*;

/**
 *
 * @author wick0167
 */

public class MyDisasterResponder extends DisasterResponder {
    private Graph graph;
    private String origin;

    private PathFinderDijkstra pathFinder;

    private int numOfVehicles = ConfigurationInfo.NUMBER_OF_VEHICLES;

    // state records for vehicles
    private VehicleState[] vehicleFleet;

    // pending rescue requests that have not dispatched yet
    private List<String> pendingRescues = new ArrayList<>();

    // location are collapsedLocations
    private Set<String> collapsedLocations = new HashSet<>();

    // for calcualate rescue feasibility
    private long rescueDurationTicks;
    private double vehicleSpeed;

    @Override
    protected void setup() {
        // build graph from the map data
        String mapFile = ConfigurationInfo.getMapFile(configFile);
        origin = ConfigurationInfo.getOrigin(configFile);

        try {
            graph = GraphBuilder.buildFromGraphML(mapFile);
        } catch (JDOMException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // System.out.println("Graph: " + graph.getGraph());

        // initialise vehicles at the base
        vehicleFleet = new VehicleState[numOfVehicles];

        for (int i = 0; i < numOfVehicles; i++) {
            vehicleFleet[i] = new VehicleState(i, origin);
        }

        // System.out.println("Idle vehicles: " + vehicleIdle);

        // initialize our PathFinder
        pathFinder = new PathFinderDijkstra(graph);

        Properties cfg = ConfigurationInfo.loadConfig(configFile);
        rescueDurationTicks = parseLong(cfg.getProperty("RESCUE_DURATION", "0"), 0) * 1000L;
        vehicleSpeed = parseDouble(cfg.getProperty("VEHICLE_SPEED", "0.2"), 0.2);

        // List<String> path = pathFinder.shortestPath("1", "8");
        // System.out.println("shortest path: " + path);
    }

    /**
     * Once a Message is received, this method is called with the newly received Message as its parameter
     *
     * @param s: Message text
     */
    @Override
    protected void handle(Message s) {
        String text = s.text;

        // Handle each command

        // RESCUE|LOCATION|location|PEOPLE|noOfPeople
        if (text.startsWith("RESCUE|")) {
            handleRescue(text);
        }
        // ROAD|FROM|location|TO|location|STATUS|status
        else if (text.startsWith("ROAD|")) {
            handleRoadStatus(text);
        }
        // LOCATION|location|COLLAPSED
        else if (text.startsWith("LOCATION|")) {
            handleLocationCollapsed(text);
        }
        // WAYPOINT_INVALID|VEHICLE|vehicleNo|FROM|location|TO|location|ROAD|reason
        else if (text.startsWith("WAYPOINT_INVALID|")) {
            handleInvalidWaypoint(text);
        }
        // PATH_INVALID|VEHICLE|vehicleNo|reason
        else if (text.startsWith("PATH_INVALID|")) {
            handleInvalidPath(text);
        }
        // PEOPLE_TRANSFERRED|LOCATION|location|VEHICLE|vehicleNo|PEOPLE|noOfPeople
        else if (text.startsWith("PEOPLE_TRANSFERRED|")) {
            handlePeopleTransferred(text);
        }
        // VEHICLE|vehicleNo|HALTED|LOCATION|locationNo
        else if (text.startsWith("VEHICLE|") && text.contains("|HALTED|")) {
            handleVehicleHalted(text);
        }
        // VEHICLE|vehicleNo|RETURNED|RESCUED|noOfPeople
        else if (text.startsWith("VEHICLE|") && text.contains("|RETURNED|")) {
            handleVehicleReturned(text);
        }
        // VEHICLE|vehicleNo|ARRIVED|LOCATION|locationNo
        else if (text.startsWith("VEHICLE|") && text.contains("|ARRIVED|")) {
            handleVehicleArrived(text);
        }
        // VEHICLE|vehicleNo|DESTROYED|LOCATION|locationNo|PEOPLE|noOfPeople
        else if (text.startsWith("VEHICLE|") && text.contains("|DESTROYED|")) {
            handleVehicleDestroyed(text);
        }
    }

    /**
     * Handle RESCUE REQUEST
     * Event ask for help in rescuing people trapped at a given location on the map
     *
     * @param text: RESCUE|LOCATION|location|PEOPLE|noOfPeople
     */
    private void handleRescue(String text) {
        // System.out.println("handleRescue: " + text);

        String[] parts = text.split("\\|");
        String location = parts[2];

        // add to rescue list
        pendingRescues.add(location);

        // dispatch a rescue request
        // this will dispatch the next available rescue request. Not always the on that added to the queue above.
        processRescueRequest();
    }

    /**
     * Handle ROAD STATUS
     *
     * @param text: ROAD|FROM|location|TO|location|STATUS|status
     */
    private void handleRoadStatus(String text) {
        String[] parts = text.split("\\|");
        String from = parts[2];
        String to = parts[4];
        String status = parts[6];

        // remove edge from the graph
        if ("BLOCKED".equals(status)) {
            graph.removeEdge(from, to);

            // halt and reroute vehicles that still pending to use this edge
            for (VehicleState vehicle : vehicleFleet) {
                if (isPathExistEdge(vehicle, from, to)) {
                    requestHaltForReroute(vehicle);
                }
            }
        }
    }

    /**
     * Handle LOCATION COLLAPSED
     *
     * @param text: LOCATION|location|COLLAPSED
     */
    private void handleLocationCollapsed(String text) {
        String[] parts = text.split("\\|");
        String location = parts[1];

        // remove node for the location
        graph.removeNode(location);

        // add collapsedLocations location to the list
        collapsedLocations.add(location);

        // remove pending rescues at the collapsedLocations location
        pendingRescues.removeIf(loc -> loc.equals(location));

        // halt and reroute vehicles dertination to or going through this node
        for (VehicleState vehicle : vehicleFleet) {
            if (!vehicle.isAlive || vehicle.isIdle) continue;

            if (location.equals(vehicle.rescueLocation) && !vehicle.isTransporting) {
                vehicle.rescueLocation = null;
                requestHaltForReroute(vehicle);
            } else if (isPathExistNode(vehicle, location)) {
                requestHaltForReroute(vehicle);
            }
        }
    }

    /**
     * Handle INVALID PATH
     *
     * @param text: PATH_INVALID|VEHICLE|vehicleNo|reason
     */
    private void handleInvalidPath(String text) {
        String[] parts = text.split("\\|");
        int vehicleNo = Integer.parseInt(parts[2]);
        String reason = parts[3];

        VehicleState vehicle = vehicleFleet[vehicleNo];

        if ("DESTROYED".equals(reason)) {
            // reason = DESTROYED
            markVehicleDead(vehicle);
        }else {
            // reason = STILL_MOVING
            // reason = INVALID_NUMBER
            // reason = INVALID_STARTING_POINT
            System.err.println("PATH_INVALID for vehicle " + vehicleNo + ": " + reason);
        }
    }

    /**
     * Handle INVALID WAYPOINT
     *
     * @param text: WAYPOINT_INVALID|VEHICLE|vehicleNo|FROM|location|TO|location|ROAD|reason
     */
    private void handleInvalidWaypoint(String text) {
        // reason = BLOCKED
        // reason = NON_EXISTENT

        String[] parts = text.split("\\|");
        int vehicleNo = Integer.parseInt(parts[2]);
        String from = parts[4];
        String to = parts[6];

        VehicleState vehicle = vehicleFleet[vehicleNo];

        vehicle.currentLocation = from;
        vehicle.isIdle = true;
        vehicle.isAwaitingHalt = false;

        graph.removeEdge(from, to);
        reRoute(vehicle);
    }

    /**
     * Handle VEHICLE ARRIVED
     * This message confirms that a vehicle has arrived at a particular location on the map
     *
     * @param text: VEHICLE|vehicleNo|ARRIVED|LOCATION|locationNo
     */
    private void handleVehicleArrived(String text) {
        String[] parts = text.split("\\|");

        int vehicleNo = Integer.parseInt(parts[1]);
        String location = parts[4];

        VehicleState vehicle = vehicleFleet[vehicleNo];
        vehicle.currentLocation = location;
    }

    /**
     * Handle VEHICLE HALTED
     * This message is sent when a vehicle halts at a particular location
     *
     * @param text: VEHICLE|vehicleNo|HALTED|LOCATION|locationNo
     */
    private void handleVehicleHalted(String text) {
        String[] parts = text.split("\\|");
        int vehicleNo = Integer.parseInt(parts[1]);
        String location = parts[4];

        VehicleState vehicle = vehicleFleet[vehicleNo];

        // put vehicle to idle state
        vehicle.isIdle = true;
        // update current location
        vehicle.currentLocation = location;

        if (vehicle.isAwaitingHalt) {
            vehicle.isAwaitingHalt = false;
            reRoute(vehicle);
            return;
        }

        if (location.equals(origin)){
            vehicle.rescueLocation = null;
        }
    }

    /**
     * Handle: VEHICLE RETURNED
     * The vehicle has returned to base (source), and has successfully rescued noOfPeople people
     *
     * @param text: VEHICLE|vehicleNo|RETURNED|RESCUED|noOfPeople
     */
    private void handleVehicleReturned(String text) {
        String[] parts = text.split("\\|");
        int vehicleNo = Integer.parseInt(parts[1]);

        VehicleState vehicle = vehicleFleet[vehicleNo];

        vehicle.isIdle = true;
        vehicle.currentLocation = origin;
        vehicle.rescueLocation = null;
        vehicle.isTransporting = false;
        vehicle.isAwaitingHalt = false;

        processRescueRequest();
    }

    /**
     * Handle PEOPLE TRANSFERRED
     * A vehicle has arrived at a rescue location and has picked up the evacuees there
     *
     * @param text: PEOPLE_TRANSFERRED|LOCATION|location|VEHICLE|vehicleNo|PEOPLE|noOfPeople
     */
    private void handlePeopleTransferred(String text) {
        String[] parts = text.split("\\|");
        int vehicleNo = Integer.parseInt(parts[4]);

        // people onboarded this vehicle
        vehicleFleet[vehicleNo].isTransporting = true;
    }

    /**
     * Handle VEHICLE DESTROYED
     *
     * @param text: VEHICLE|vehicleNo|DESTROYED|LOCATION|locationNo|PEOPLE|noOfPeople
     */
    private void handleVehicleDestroyed(String text) {
        // reason = BLOCKED
        // reason = NON_EXISTENT
        String[] parts = text.split("\\|");
        int vehicleNo = Integer.parseInt(parts[1]);
        markVehicleDead(vehicleFleet[vehicleNo]);
    }

    /**
     * recompute path for a vehicle when stopped at the current location
     * @param vehicle
     */
    private void reRoute(VehicleState vehicle) {
        if (!vehicle.isAlive) return;

        // continue again if the path is still avaialble
        if (vehicle.rescueLocation != null && !vehicle.isTransporting && !collapsedLocations.contains(vehicle.rescueLocation)) {
            List<String> roundTrip = buildRoundTripWaypoints(vehicle.currentLocation, vehicle.rescueLocation);

            if (roundTrip != null) {
                outboundDispatchVehicle(vehicle.id, roundTrip);
                return;
            }
            // cannot reach the pickup now
            // put to pending rescues
            requeueRescue(vehicle.rescueLocation);
            vehicle.rescueLocation = null;
        }

        // back to the base when path is not available
        List<String> toBase = pathFinder.shortestPath(vehicle.currentLocation, origin);
        if (toBase != null && toBase.size() >= 2) {
            outboundDispatchVehicle(vehicle.id, toBase);
        } else if (vehicle.currentLocation.equals(origin)) {
            vehicle.isIdle = true;
        }

        processRescueRequest();
    }

    /**
     *
     * @param vehicle
     */
    private void markVehicleDead(VehicleState vehicle){
        vehicle.isAlive = false;
        vehicle.isIdle = false;
        if (vehicle.rescueLocation != null && !vehicle.isTransporting) {
            requeueRescue(vehicle.rescueLocation); // the people are still waiting there
        }
        vehicle.rescueLocation = null;
        vehicle.isTransporting = false;
        processRescueRequest();
    }

    /**
     *
     * @param location
     */
    private void requeueRescue(String location) {
        if (location == null || collapsedLocations.contains(location)) return;
        if (!pendingRescues.contains(location)) {
            pendingRescues.add(location);
        }
    }

    /**
     * Get next pending rescue request and send a vehicle to there
     */
    private void processRescueRequest() {

        Iterator<String> it = pendingRescues.iterator();

        while (it.hasNext()) {
            // get next available pending rescue request location
            String rescueLocation = it.next();

            // find closet idle vehicle to the rescue location
            int bestVehicle = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < numOfVehicles; i++) {
                // best vehicle cannot reach before the deadline
                if (!isPickupWithinDeadline(bestDistance)) {
                    continue;
                }

                VehicleState vehicle = vehicleFleet[i];
                if (!vehicle.isAlive || !vehicle.isIdle || vehicle.currentLocation == null) continue;

                // find the shortest distance form current vehicle location to rescue location
                double distanceToRescueLocation = pathFinder.shortestDistance(vehicle.currentLocation, rescueLocation);

                // check if curretn vehicle is closer to rescue location than previouse best option
                if (distanceToRescueLocation < bestDistance) {
                    // update current vehicle if it is close to the rescue location
                    bestDistance = distanceToRescueLocation;
                    bestVehicle = i;
                }
            }

            // if a vehicle available for the current rescue request
            if (bestVehicle >= 0 && bestDistance < Double.MAX_VALUE) {
                VehicleState vehicle = vehicleFleet[bestVehicle];

                List<String> roundTrip = buildRoundTripWaypoints(vehicle.currentLocation, rescueLocation);

                if (roundTrip != null) {
                    // send the vehicle
                    outboundDispatchVehicle(bestVehicle, roundTrip);
                    // assign vehicle
                    vehicle.rescueLocation = rescueLocation;
                    // remove request from the list
                    it.remove();
                }
            }
        }
    }

    /**
     * Build waypoints  from current location to rescue location and then base location
     * @param sourceLocation
     * @param destinationLocation
     * @return
     */
    private List<String> buildRoundTripWaypoints(String sourceLocation, String destinationLocation){
        List<String> outbound = pathFinder.shortestPath(sourceLocation, destinationLocation);

        if (outbound == null || outbound.isEmpty()) {
            return null;
        }

        List<String> back = pathFinder.shortestPath(destinationLocation, origin);
        if (back == null || back.isEmpty()) {
            return null;
        }

        List<String> fullPath = new ArrayList<>(outbound);

        for (int i = 1; i < back.size(); i++) {
            fullPath.add(back.get(i));
        }
        return fullPath;
    }

    /**
     * This method send outbound message to the simulator to dispatch a vehicle
     * PATH REQUEST: PATH|VEHICLE|vehicleNo|WAYPOINTS|wayPoints
     *
     * @param vehicleNo
     * @param waypoints
     */
    private void outboundDispatchVehicle(int vehicleNo, List<String> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            return;
        }

        // send the vehicle command
        String command = "PATH|VEHICLE|" + vehicleNo + "|WAYPOINTS|" + String.join(",", waypoints);

        VehicleState vehicle = vehicleFleet[vehicleNo];
        vehicle.isIdle = false;
        vehicle.isAwaitingHalt = false;
        vehicle.plannedPath = waypoints;

        try {
            outMessageQueue.put(new Message(command));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method send outbound message to the simulator to halt a vehicle
     * VEHICLE HALT: HALT|VEHICLE|vehicleNo
     *
     * @param vehicleNo
     */
    private void outboundHaltVehicle(int vehicleNo) {
        String command = "HALT|VEHICLE|" + vehicleNo;
        try {
            outMessageQueue.put(new Message(command));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param vehicle
     */
    private void requestHaltForReroute(VehicleState vehicle) {
        if (!vehicle.isAlive || vehicle.isIdle || vehicle.isAwaitingHalt) return;
        vehicle.isAwaitingHalt = true;
        outboundHaltVehicle(vehicle.id);
    }


    /**
     * Check if the edge exist in the given path
     *
     * @param vehicle
     * @param from
     * @param to
     * @return
     */
    private boolean isPathExistEdge(VehicleState vehicle, String from, String to) {
        List<String> path = vehicle.plannedPath;
        if (path == null) return false;
        for (int i = 0; i + 1 < path.size(); i++) {
            if (path.get(i).equals(from) && path.get(i + 1).equals(to)) return true;
        }
        return false;
    }

    /**
     * Check if the node exist in the given path
     *
     * @param vehicle
     * @param node
     * @return
     */
    private boolean isPathExistNode(VehicleState vehicle, String node) {
        List<String> path = vehicle.plannedPath;
        if (path == null) return false;
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i).equals(node)) return true;
        }
        return false;
    }

    /**
     * Calculate if the rescue can be done before the deadline
     *
     * @param oneWayDistance
     * @return
     */
    private boolean isPickupWithinDeadline(double oneWayDistance) {
        if (rescueDurationTicks <= 0) return true;
        double travelTicks = oneWayDistance / vehicleSpeed;
        return travelTicks <= rescueDurationTicks;
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
