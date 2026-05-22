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

    // currently assigned vehicles for rescue
    private Map<Integer, String> vehicleAssignment = new HashMap<>();

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
        // PEOPLE_TRANSFERRED|LOCATION|location|VEHICLE|vehicleNo|PEOPLE|noOfPeople
        else if (text.startsWith("PEOPLE_TRANSFERRED|")) {
            handlePeopleTransferred(text);
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

    }

    /**
     * Handle LOCATION COLLAPSED
     *
     * @param text: LOCATION|location|COLLAPSED
     */
    private void handleLocationCollapsed(String text) {

    }

    /**
     * Handle INVALID PATH
     *
     * @param text: PATH_INVALID|VEHICLE|vehicleNo|reason
     */
    private void handleInvalidPath(String text) {
        // reason = STILL_MOVING
        // reason = DESTROYED
        // reason = INVALID_NUMBER
        // reason = INVALID_STARTING_POINT
    }

    /**
     * Handle INVALID WAYPOINT
     *
     * @param text: WAYPOINT_INVALID|VEHICLE|vehicleNo|FROM|location|TO|location|ROAD|reason
     */
    private void handleInvalidWaypoint(String text) {
        // reason = BLOCKED
        // reason = NON_EXISTENT
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

        vehicleFleet[vehicleNo].currentLocation = location;
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

        // check the halted vehicle is already in a rescue
        if (location.equals(vehicle.rescueLocation)) {

            // vehicle arrived to assigned rescue location
            // send back to origin since already picked the people
            outboundDispatchVehicle(vehicleNo, location, origin);

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

        processRescueRequest();
    }

    /**
     * Handle PEOPLE TRANSFERRED
     * A vehicle has arrived at a rescue location and has picked up the evacuees there
     *
     * @param text: PEOPLE_TRANSFERRED|LOCATION|location|VEHICLE|vehicleNo|PEOPLE|noOfPeople
     */
    private void handlePeopleTransferred(String text) {
        // String[] parts = text.split("\\|");


        // TODO::actions need to take
    }

    /**
     * Handle VEHICLE DESTROYED
     *
     * @param text: VEHICLE|vehicleNo|DESTROYED|LOCATION|locationNo|PEOPLE|noOfPeople
     */
    private void handleVehicleDestroyed(String text) {
        // reason = BLOCKED
        // reason = NON_EXISTENT
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
                VehicleState vehicle = vehicleFleet[i];
                if (!vehicle.isIdle || vehicle.currentLocation == null) continue;

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

                // send the vehicle
                outboundDispatchVehicle(bestVehicle, vehicle.currentLocation, rescueLocation);
                // assigne vehicle
                vehicle.rescueLocation = rescueLocation;
                // remove request from the list
                it.remove();
            }
        }
    }

    /**
     * This method send outbound message to the simulator to dispatch a vehicle
     * PATH REQUEST: PATH|VEHICLE|vehicleNo|WAYPOINTS|wayPoints
     *
     * @param vehicleNo
     * @param from
     * @param to
     */
    private void outboundDispatchVehicle(int vehicleNo, String from, String to) {
        // get the shortest path
        List<String> path = pathFinder.shortestPath(from, to);

        // if no existing paths
        if (path == null || path.isEmpty()) {
            return;
        }

        // send the vehicle command
        String waypoints = String.join(",", path);
        String command = "PATH|VEHICLE|" + vehicleNo + "|WAYPOINTS|" + waypoints;


        vehicleFleet[vehicleNo].isIdle = false;
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

    }
}
