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
 * Responder class with multiple threads that execute concurrently
 * Each vehicle has their own thread
 */

public class MyDisasterResponderWithMultipleExecutors extends MyDisasterResponderAbstract {
    private volatile RescueCoordinator rescueCoordinator;

//    private final int threadExecutorCount = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 4));
    private final int threadExecutorCount = 20; // used fixed count to get same result across different computers

    private final ExecutorService[] threadExecutors = newThreadExecutors(threadExecutorCount);


    /**
     * Once a Message is received, this method is called with the newly received Message as its parameter
     *
     * @param s: Message text
     */
    @Override
    protected void handle(Message s) {
        String text = s.text;

        int vehicleNo = extractVehicleId(text);

        // use multiple threads to handle each vehicle related events concurrently
        // this ensures, no concurrent events processed for single vehicle
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
}
