package solution;

import java.util.List;

public class RescueCoordinator {
    private final MapState mapState;
    private final PathPlanner pathPlanner;
    private final SimulatorGateway simulatorGateway;
    private final SimulatorParameters parameters;

    public RescueCoordinator(MapState mapState, PathPlanner pathPlanner, SimulatorGateway simulatorGateway, SimulatorParameters parameters) {
        this.mapState = mapState;
        this.pathPlanner = pathPlanner;
        this.simulatorGateway = simulatorGateway;
        this.parameters = parameters;
    }

    // actions for RESCUE request
    public void onRescueRequest(String location) {
        if (!mapState.isCollapsed(location)) {
            mapState.addPendingRescue(location);
        }
        processRescueRequest();
    }

    // actions for ROAD BLOCKED
    public void onRoadBlocked(String from, String to) {
        mapState.blockRoad(from, to);
        for (VehicleState vehicle : mapState.getVehicleFleet()) {
            if (pathPlanner.isPathExistEdge(vehicle.plannedPath, from, to)) {
                requestHaltForReroute(vehicle);
            }
        }
    }

    // actions for LOCATION COLLAPSED
    public void onLocationCollapsed(String location) {
        mapState.collapseLocation(location);
        mapState.removePendingRescuesByLocation(location);

        for (VehicleState vehicle : mapState.getVehicleFleet()) {
            if (!vehicle.isAlive || vehicle.isIdle) continue;

            if (location.equals(vehicle.rescueLocation) && !vehicle.isTransporting) {
                vehicle.rescueLocation = null;
                requestHaltForReroute(vehicle);
            } else if (pathPlanner.isPathExistNode(vehicle.plannedPath, location)) {
                requestHaltForReroute(vehicle);
            }
        }
    }

    // actions for INVALID PATH
    public void onWaypointInvalid(int vehicleNo, String from, String to) {
        VehicleState vehicle = mapState.getVehicleFromFleet(vehicleNo);

        vehicle.currentLocation = from;
        vehicle.isIdle = true;
        vehicle.isAwaitingHalt = false;

        mapState.blockRoad(from, to);
        reRoute(vehicle);
    }

    // actions for INVALID PATH
    public void onPathInvalid(int vehicleNo, String reason) {
        VehicleState vehicle = mapState.getVehicleFromFleet(vehicleNo);

        if ("DESTROYED".equals(reason)) {
            markVehicleDead(vehicle);
        } else {
            System.err.println("PATH_INVALID for vehicle " + vehicleNo + ": " + reason);
        }
    }

    // actions for VEHICLE ARRIVED
    public void onVehicleArrived(int vehicleNo, String location) {
        mapState.getVehicleFromFleet(vehicleNo).currentLocation = location;
    }

    // actions for VEHICLE HALTED
    public void onVehicleHalted(int vehicleNo, String location) {
        VehicleState vehicle = mapState.getVehicleFromFleet(vehicleNo);

        vehicle.isIdle = true;
        vehicle.currentLocation = location;

        if (vehicle.isAwaitingHalt) {
            vehicle.isAwaitingHalt = false;
            reRoute(vehicle);
            return;
        }

        if (location.equals(parameters.getBaseLocation())) {
            vehicle.rescueLocation = null;
        }
    }

    // actions for VEHICLE RETURNED
    public void onVehicleReturned(int vehicleNo) {
        VehicleState vehicle = mapState.getVehicleFromFleet(vehicleNo);

        vehicle.isIdle = true;
        vehicle.currentLocation = parameters.getBaseLocation();
        vehicle.rescueLocation = null;
        vehicle.isTransporting = false;
        vehicle.isAwaitingHalt = false;

        processRescueRequest();
    }

    // actions for PEOPLE TRANSFERRED
    public void onPeopleTransferred(int vehicleNo) {
        mapState.getVehicleFromFleet(vehicleNo).isTransporting = true;
    }

    // actions for VEHICLE DESTROYED
    public void onVehicleDestroyed(int vehicleNo) {
        markVehicleDead(mapState.getVehicleFromFleet(vehicleNo));
    }

    /**
     * recompute path for a vehicle when stopped at the current location
     * @param vehicle
     */
    private void reRoute(VehicleState vehicle) {
        if (!vehicle.isAlive) return;

        // continue again if the path is still avaialble
        if (vehicle.rescueLocation != null && !vehicle.isTransporting && !mapState.isCollapsed(vehicle.rescueLocation)) {
            List<String> roundTrip = pathPlanner.buildRoundTripWaypoints(vehicle.currentLocation, vehicle.rescueLocation);
            if (roundTrip != null) {
                dispatch(vehicle.id, roundTrip);
                return;
            }
            // cannot reach the pickup now
            // put to pending rescues
            requeueRescue(vehicle.rescueLocation);
            vehicle.rescueLocation = null;
        }

        // back to the base when path is not available
        List<String> toBase = pathPlanner.pathToBase(vehicle.currentLocation);
        if (toBase != null && toBase.size() >= 2) {
            dispatch(vehicle.id, toBase);
        } else if (vehicle.currentLocation.equals(parameters.getBaseLocation())) {
            vehicle.isIdle = true;
        }

        processRescueRequest();
    }

    /**
     *
     * @param vehicle
     */
    private void markVehicleDead(VehicleState vehicle) {
        vehicle.isAlive = false;
        vehicle.isIdle = false;
        if (vehicle.rescueLocation != null && !vehicle.isTransporting) {
            // the people are still waiting there
            requeueRescue(vehicle.rescueLocation);
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
        if (location == null || mapState.isCollapsed(location)) return;
        if (!mapState.hasPendingRescue(location)) {
            mapState.addPendingRescue(location);
        }
    }

    /**
     * Get next pending rescue request and send a vehicle to there
     */
    private void processRescueRequest() {
        for (String rescueLocation : mapState.allRescuesList()) {

            // find closet idle vehicle to the rescue location
            int bestVehicle = -1;
            double bestDistance = Double.MAX_VALUE;
            for (VehicleState vehicle : mapState.getVehicleFleet()) {
                if (!vehicle.isAlive || !vehicle.isIdle || vehicle.currentLocation == null) continue;

                double distanceToRescueLocation = pathPlanner.distance(vehicle.currentLocation, rescueLocation);
                if (distanceToRescueLocation < bestDistance) {
                    bestDistance = distanceToRescueLocation;
                    bestVehicle = vehicle.id;
                }
            }

            // send vehicle only when vehicle can reach th location before the deadline
            if (bestVehicle >= 0 && bestDistance < Double.MAX_VALUE && isPickupWithinDeadline(bestDistance)) {
                VehicleState vehicle = mapState.getVehicleFromFleet(bestVehicle);

                List<String> roundTrip = pathPlanner.buildRoundTripWaypoints(vehicle.currentLocation, rescueLocation);
                if (roundTrip != null) {
                    dispatch(bestVehicle, roundTrip);
                    vehicle.rescueLocation = rescueLocation;
                    mapState.removePendingRescuesByLocation(rescueLocation);
                }
            }
        }
    }

    /**
     * send outbound message to the simulator to dispatch a vehicle
     *
     * @param vehicleNo
     * @param waypoints
     */
    private void dispatch(int vehicleNo, List<String> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            return;
        }
        VehicleState vehicle = mapState.getVehicleFromFleet(vehicleNo);
        vehicle.isIdle = false;
        vehicle.isAwaitingHalt = false;
        vehicle.plannedPath = waypoints;
        simulatorGateway.sendPath(vehicleNo, waypoints);
    }

    /**
     *
     * @param vehicle
     */
    private void requestHaltForReroute(VehicleState vehicle) {
        if (!vehicle.isAlive || vehicle.isIdle || vehicle.isAwaitingHalt) return;
        vehicle.isAwaitingHalt = true;
        simulatorGateway.sendHalt(vehicle.id);
    }

    /**
     * Calculate if the rescue can be done before the deadline
     *
     * @param oneWayDistance
     * @return
     */
    private boolean isPickupWithinDeadline(double oneWayDistance) {
        if (parameters.getRescueDurationTicks() <= 0) return true;
        double travelTicks = oneWayDistance / parameters.getVehicleSpeed();
        return travelTicks <= parameters.getRescueDurationTicks();
    }
}
