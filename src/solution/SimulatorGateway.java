package solution;

import sim.Message;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Handle outbound requests to the simulator
 */

public class SimulatorGateway {
    private final BlockingQueue<Message> outMessageQueue;

    public SimulatorGateway(BlockingQueue<Message> outMessageQueue) {
        this.outMessageQueue = outMessageQueue;
    }

    /**
     * This method send outbound message to the simulator to dispatch a vehicle
     * PATH REQUEST: PATH|VEHICLE|vehicleNo|WAYPOINTS|wayPoints
     * @param vehicleNo
     * @param waypoints
     */
    public void sendPath(int vehicleNo, List<String> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            return;
        }
        send("PATH|VEHICLE|" + vehicleNo + "|WAYPOINTS|" + String.join(",", waypoints));
    }

    /**
     * This method send outbound message to the simulator to halt a vehicle
     * VEHICLE HALT: HALT|VEHICLE|vehicleNo
     *
     * @param vehicleNo
     */
    public void sendHalt(int vehicleNo) {
        send("HALT|VEHICLE|" + vehicleNo);
    }

    /**
     * Send command to outbound queue
     *
     * @param command
     */
    private void send(String command) {
        try {
            outMessageQueue.put(new Message(command));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
