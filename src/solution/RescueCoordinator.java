package solution;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */

public class RescueCoordinator {
    private final DynamicStateManager dynamicStateManager;
    private final PathPlanner pathPlanner;
    private final SimulatorGateway simulatorGateway;
    private final SimulatorStaticParameters parameters;

    // cordinate dispatches from parallel threads
    private final ReentrantLock dispatchLock = new ReentrantLock();

    public RescueCoordinator(DynamicStateManager dynamicStateManager, PathPlanner pathPlanner, SimulatorGateway simulatorGateway, SimulatorStaticParameters parameters) {
        this.dynamicStateManager = dynamicStateManager;
        this.pathPlanner = pathPlanner;
        this.simulatorGateway = simulatorGateway;
        this.parameters = parameters;
    }

    // actions for RESCUE request
    public void onRescueRequest(String location) {
        if (!dynamicStateManager.isCollapsed(location)) {
            dynamicStateManager.addPendingRescue(location);
        }
        processRescueRequest();
    }

    // actions for ROAD BLOCKED
    public void onRoadBlocked(String from, String to) {
        dynamicStateManager.blockRoad(from, to);
        for (VehicleState vehicle : dynamicStateManager.getVehicleFleet()) {
            synchronized (vehicle) {
                if (pathPlanner.isPathExistEdge(vehicle.plannedPath, from, to)) {
                    requestHaltForReroute(vehicle);
                }
            }
        }
    }

    // actions for LOCATION COLLAPSED
    public void onLocationCollapsed(String location) {
        dynamicStateManager.collapseLocation(location);
        dynamicStateManager.removePendingRescuesByLocation(location);

        for (VehicleState vehicle : dynamicStateManager.getVehicleFleet()) {
            synchronized (vehicle) {
                if (!vehicle.isAlive || vehicle.isIdle) continue;

                if (location.equals(vehicle.rescueLocation) && !vehicle.isTransporting) {
                    vehicle.rescueLocation = null;
                    requestHaltForReroute(vehicle);
                } else if (pathPlanner.isPathExistNode(vehicle.plannedPath, location)) {
                    requestHaltForReroute(vehicle);
                }
            }
        }
    }

    // actions for INVALID PATH
    public void onWaypointInvalid(int vehicleNo, String from, String to) {
        VehicleState vehicle = dynamicStateManager.getVehicleFromFleet(vehicleNo);

        synchronized (vehicle) {
            vehicle.currentLocation = from;
            vehicle.isIdle = true;
            vehicle.isAwaitingHalt = false;
        }

        dynamicStateManager.blockRoad(from, to);
        reRoute(vehicle);
    }

    // actions for INVALID PATH
    public void onPathInvalid(int vehicleNo, String reason) {
        VehicleState vehicle = dynamicStateManager.getVehicleFromFleet(vehicleNo);

        if ("DESTROYED".equals(reason)) {
            markVehicleDead(vehicle);
        } else {
            System.err.println("PATH_INVALID for vehicle " + vehicleNo + ": " + reason);
        }
    }

    // actions for VEHICLE ARRIVED
    public void onVehicleArrived(int vehicleNo, String location) {
        VehicleState vehicle = dynamicStateManager.getVehicleFromFleet(vehicleNo);
        synchronized (vehicle) {
            vehicle.currentLocation = location;
        }    }

    // actions for VEHICLE HALTED
    public void onVehicleHalted(int vehicleNo, String location) {
        VehicleState vehicle = dynamicStateManager.getVehicleFromFleet(vehicleNo);

        boolean needsReroute = false;

        synchronized (vehicle) {
            vehicle.currentLocation = location;

            if (vehicle.isAwaitingHalt) {
                vehicle.isAwaitingHalt = false;
                needsReroute = true;
            } else {
                vehicle.isIdle = true;
                if (location.equals(parameters.getBaseLocation())) {
                    vehicle.rescueLocation = null;
                }
            }
        }

        if (needsReroute) {
            reRoute(vehicle);
        }
    }

    // actions for VEHICLE RETURNED
    public void onVehicleReturned(int vehicleNo) {
        VehicleState vehicle = dynamicStateManager.getVehicleFromFleet(vehicleNo);

        synchronized (vehicle) {
            vehicle.isIdle = true;
            vehicle.currentLocation = parameters.getBaseLocation();
            vehicle.rescueLocation = null;
            vehicle.isTransporting = false;
            vehicle.isAwaitingHalt = false;
        }

        processRescueRequest();
    }

    // actions for PEOPLE TRANSFERRED
    public void onPeopleTransferred(int vehicleNo) {
        VehicleState vehicle = dynamicStateManager.getVehicleFromFleet(vehicleNo);

        synchronized (vehicle) {
            vehicle.isTransporting = true;
        }
    }

    // actions for VEHICLE DESTROYED
    public void onVehicleDestroyed(int vehicleNo) {
        markVehicleDead(dynamicStateManager.getVehicleFromFleet(vehicleNo));
    }

    /**
     * recompute path for a vehicle when stopped at the current location
     * @param vehicle
     */
    private void reRoute(VehicleState vehicle) {
        synchronized (vehicle) {
            if (!vehicle.isAlive) return;

            // continue again if the path is still avaialble
            if (vehicle.rescueLocation != null && !vehicle.isTransporting && !dynamicStateManager.isCollapsed(vehicle.rescueLocation)) {
                List<String> roundTrip = pathPlanner.buildRoundTripWaypoints(vehicle.currentLocation, vehicle.rescueLocation);
                if (roundTrip != null) {
                    dispatch(vehicle, roundTrip);
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
                dispatch(vehicle, toBase);
            } else if (vehicle.currentLocation.equals(parameters.getBaseLocation())) {
                vehicle.isIdle = true;
            }
        }

        processRescueRequest();
    }

    /**
     *
     * @param vehicle
     */
    private void markVehicleDead(VehicleState vehicle) {
        String rescueToRequeue = null;

        synchronized (vehicle) {
            vehicle.isAlive = false;
            vehicle.isIdle = false;
            if (vehicle.rescueLocation != null && !vehicle.isTransporting) {
                // the people are still waiting there
                rescueToRequeue = vehicle.rescueLocation;
            }
            vehicle.rescueLocation = null;
            vehicle.isTransporting = false;
        }

        if (rescueToRequeue != null) {
            requeueRescue(rescueToRequeue);
        }

        processRescueRequest();
    }

    /**
     *
     * @param location
     */
    private void requeueRescue(String location) {
        if (location == null || dynamicStateManager.isCollapsed(location)) return;
        if (!dynamicStateManager.hasPendingRescue(location)) {
            dynamicStateManager.addPendingRescue(location);
        }
    }

    /**
     * Get next pending rescue request and send a vehicle to there
     */
    private void processRescueRequest() {
        dispatchLock.lock();

        try {
            for (String rescueLocation : dynamicStateManager.allRescuesList()) {

                // find closet idle vehicle to the rescue location
                int bestVehicle = -1;
                double bestDistance = Double.MAX_VALUE;
                for (VehicleState vehicle : dynamicStateManager.getVehicleFleet()) {
                    String location;
                    synchronized (vehicle) {
                        if (!vehicle.isAlive || !vehicle.isIdle || vehicle.currentLocation == null) continue;
                        location = vehicle.currentLocation;
                    }

                    double distanceToRescueLocation = pathPlanner.distance(location, rescueLocation);
                    if (distanceToRescueLocation < bestDistance) {
                        bestDistance = distanceToRescueLocation;
                        bestVehicle = vehicle.id;
                    }
                }

                // send vehicle only when vehicle can reach th location before the deadline
                if (bestVehicle >= 0 && bestDistance < Double.MAX_VALUE && isPickupWithinDeadline(bestDistance)) {
                    VehicleState vehicle = dynamicStateManager.getVehicleFromFleet(bestVehicle);
                    synchronized (vehicle) {
                        if (!vehicle.isAlive || !vehicle.isIdle) continue;
                        List<String> roundTrip = pathPlanner.buildRoundTripWaypoints(vehicle.currentLocation, rescueLocation);

                        if (roundTrip != null) {
                            dispatch(vehicle, roundTrip);
                            vehicle.rescueLocation = rescueLocation;
                            dynamicStateManager.removePendingRescuesByLocation(rescueLocation);
                        }
                    }
                }
            }
        } finally {
            dispatchLock.unlock();
        }
    }

    /**
     * send outbound message to the simulator to dispatch a vehicle
     *
     * @param vehicle
     * @param waypoints
     */
    private void dispatch(VehicleState vehicle, List<String> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            return;
        }

        vehicle.isIdle = false;
        vehicle.isAwaitingHalt = false;
        vehicle.plannedPath = waypoints;
        simulatorGateway.sendPath(vehicle.id, waypoints);
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
