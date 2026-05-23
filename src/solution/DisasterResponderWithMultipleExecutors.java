package solution;

import org.jdom2.JDOMException;
import sim.Message;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author wick0167
 *
 */

public class DisasterResponderWithMultipleExecutors extends DisasterResponder {
    private volatile RescueCoordinator rescueCoordinator;

    private final int threadExecutorCount = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 4));

    private ExecutorService[] threadExecutors;

    @Override
    protected void setup() {
        SimulatorStaticParameters parameters = SimulatorStaticParameters.fromConfig(configFile);

        Graph graph;

        try {
            graph = GraphBuilder.buildFromGraphML(parameters.getMapFile());
        } catch (JDOMException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // System.out.println("Graph: " + graph.getGraph());

        //
        DynamicStateManager dynamicStateManager = new DynamicStateManager(graph, parameters);

        //
        PathFinder pathFinder = new PathFinderDijkstra(graph);
        PathPlanner pathPlanner = new PathPlanner(pathFinder, parameters.getBaseLocation());

        // intilaise outbound message gateway
        SimulatorGateway simulatorGateway = new SimulatorGateway(outMessageQueue);

        //
        rescueCoordinator = new RescueCoordinator(dynamicStateManager, pathPlanner, simulatorGateway, parameters);

        //
        threadExecutors = newThreadExecutors(threadExecutorCount);
    }

    /**
     * Once a Message is received, this method is called with the newly received Message as its parameter
     *
     * @param s: Message text
     */
    @Override
    protected void handle(Message s) {
        String text = s.text;

        int vehicleNo = extractVehicleId(text);

        if (vehicleNo >= 0) {
            threadExecutors[vehicleNo % threadExecutorCount].submit(() -> worker(text));
        } else {
            executor.submit(() -> worker(text));
        }
    }

    /**
     * Extract vehicle id from a given message text
     * @param text
     * @return
     */
    private int extractVehicleId(String text) {
        try {
            // VEHICLE|vehicleNo|
            if (text.startsWith("VEHICLE|")) {
                return Integer.parseInt(text.split("\\|")[1]);
            }
            // WAYPOINT_INVALID|VEHICLE|vehicleNo|
            if (text.startsWith("WAYPOINT_INVALID|") || text.startsWith("PATH_INVALID|")) {
                return Integer.parseInt(text.split("\\|")[2]);
            }
            // PEOPLE_TRANSFERRED|LOCATION|location|VEHICLE|vehicleNo|PEOPLE|noOfPeople
            if (text.startsWith("PEOPLE_TRANSFERRED|")) {
                return Integer.parseInt(text.split("\\|")[4]);
            }
        } catch (Exception e) {
        }
        return -1;
    }

    /**
     *
     */
    @Override
    public void shutdown() {
        for (ExecutorService laneExecutor : threadExecutors) {
            if (laneExecutor != null) {
                laneExecutor.shutdown();
            }
        }

        // base class actions
        super.shutdown();
    }

    /**
     *
     * @param n
     * @return
     */
    private static ExecutorService[] newThreadExecutors(int n) {
        ExecutorService[] arr = new ExecutorService[n];
        for (int i = 0; i < n; i++) {
            arr[i] = Executors.newSingleThreadExecutor();
        }
        return arr;
    }

    /**
     * handle messages based on the type and other data
     * run in the worker thread
     * @param text
     */
    protected void worker(String text) {
        // Handle each command

        try {
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
        } catch (Exception e) {
            System.err.println("Error handling message :"+ text +": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle RESCUE REQUEST
     * Event ask for help in rescuing people trapped at a given location on the map
     *
     * @param text: RESCUE|LOCATION|location|PEOPLE|noOfPeople
     */
    private void handleRescue(String text) {
        String[] parts = text.split("\\|");
        rescueCoordinator.onRescueRequest(parts[2]);
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
            rescueCoordinator.onRoadBlocked(from, to);
        }
    }

    /**
     * Handle LOCATION COLLAPSED
     *
     * @param text: LOCATION|location|COLLAPSED
     */
    private void handleLocationCollapsed(String text) {
        String[] parts = text.split("\\|");
        rescueCoordinator.onLocationCollapsed(parts[1]);
    }

    /**
     * Handle INVALID PATH
     *
     * @param text: PATH_INVALID|VEHICLE|vehicleNo|reason
     */
    private void handleInvalidPath(String text) {
        String[] parts = text.split("\\|");
        rescueCoordinator.onPathInvalid(Integer.parseInt(parts[2]), parts[3]);
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
        rescueCoordinator.onWaypointInvalid(Integer.parseInt(parts[2]), parts[4], parts[6]);

    }

    /**
     * Handle VEHICLE ARRIVED
     * This message confirms that a vehicle has arrived at a particular location on the map
     *
     * @param text: VEHICLE|vehicleNo|ARRIVED|LOCATION|locationNo
     */
    private void handleVehicleArrived(String text) {
        String[] parts = text.split("\\|");
        rescueCoordinator.onVehicleArrived(Integer.parseInt(parts[1]), parts[4]);

    }

    /**
     * Handle VEHICLE HALTED
     * This message is sent when a vehicle halts at a particular location
     *
     * @param text: VEHICLE|vehicleNo|HALTED|LOCATION|locationNo
     */
    private void handleVehicleHalted(String text) {
        String[] parts = text.split("\\|");
        rescueCoordinator.onVehicleHalted(Integer.parseInt(parts[1]), parts[4]);
    }

    /**
     * Handle: VEHICLE RETURNED
     * The vehicle has returned to base (source), and has successfully rescued noOfPeople people
     *
     * @param text: VEHICLE|vehicleNo|RETURNED|RESCUED|noOfPeople
     */
    private void handleVehicleReturned(String text) {
        String[] parts = text.split("\\|");
        rescueCoordinator.onVehicleReturned(Integer.parseInt(parts[1]));
    }

    /**
     * Handle PEOPLE TRANSFERRED
     * A vehicle has arrived at a rescue location and has picked up the evacuees there
     *
     * @param text: PEOPLE_TRANSFERRED|LOCATION|location|VEHICLE|vehicleNo|PEOPLE|noOfPeople
     */
    private void handlePeopleTransferred(String text) {
        String[] parts = text.split("\\|");
        rescueCoordinator.onPeopleTransferred(Integer.parseInt(parts[4]));
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
        rescueCoordinator.onVehicleDestroyed(Integer.parseInt(parts[1]));
    }
}
