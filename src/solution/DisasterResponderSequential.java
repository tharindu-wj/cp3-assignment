package solution;

import org.jdom2.JDOMException;
import sim.Message;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author wick0167
 *
 */

public class DisasterResponderSequential extends DisasterResponder {
    private volatile RescueCoordinator rescueCoordinator;

    // this queue holds all the messages received from the simulator
    // workerLoop() method reads them according to the retrieval order
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    //
    private Thread worker;

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

        // start the single worker thread processes messages in arrival order
        worker = new Thread(this::workerConsumer, "DisasterResponderSequential-Worker");
        worker.start();
    }

    /**
     * Once a Message is received, this method is called with the newly received Message as its parameter
     *
     * @param s: Message text
     */
    @Override
    protected void handle(Message s) {
        // queue the received message
        // workerLoop method reads based on the retrival order
        workerProducer(s.text);
    }

    /**
     * works as the producer within this disaster responder
     * @param text
     */
    private void workerProducer(String text){
        messageQueue.offer(text);
    }

    /**
     * method for worker running until the shut down the simulator
     * take messages from the queue and then route to the relavant action
     * this would offload all complex calculations from the main program
     *
     * works as the consumer within this disaster responder
     */
    private void workerConsumer() {
        while (!Thread.currentThread().isInterrupted()) {
            String text;
            try {
                text = messageQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            route(text);
        }
    }

    /**
     * override base shutdown to interrupt ongoing threads
     */
    @Override
    public void shutdown() {
        // stop the worker thread when simulator shutdown
        if (worker != null) {
            worker.interrupt();
        }

        // call base class actions
        super.shutdown();
    }

    /**
     * handle messages based on the type and other data
     * run in the worker thread
     * @param text
     */
    protected void route(String text) {
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
