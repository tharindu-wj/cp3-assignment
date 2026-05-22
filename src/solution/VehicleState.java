package solution;

public class VehicleState {
    final int id;
    boolean isIdle = true;
    boolean isAlive = true;
    boolean isTransporting = false;
    String currentLocation;
    String rescueLocation;

    VehicleState(int id, String currentLocation){
        this.id = id;
        this.currentLocation = currentLocation;
    }
}
