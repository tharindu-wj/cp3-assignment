package solution;

import java.util.List;

/**
 * @author wick0167
 */

public class VehicleState {
    final int id;
    boolean isIdle = true;
    boolean isAlive = true;
    boolean isTransporting = false;
    boolean isAwaitingHalt = false;

    String currentLocation;
    String rescueLocation;

    List<String> plannedPath;

    VehicleState(int id, String currentLocation){
        this.id = id;
        this.currentLocation = currentLocation;
    }
}
