package solution;

import java.util.ArrayList;
import java.util.List;

public class PathPlanner {
    private final PathFinderDijkstra pathFinder;
    private final String base;

    public PathPlanner(PathFinderDijkstra pathFinder, String base) {
        this.pathFinder = pathFinder;
        this.base = base;
    }

    public double distance(String from, String to) {
        return pathFinder.shortestDistance(from, to);
    }

    public List<String> pathToBase(String from) {
        return pathFinder.shortestPath(from, base);
    }

    /**
     * Build waypoints  sourceLocation current location to destinationLocation location and then base location
     * @param sourceLocation
     * @param destinationLocation
     * @return
     */
    public List<String> buildRoundTripWaypoints(String sourceLocation, String destinationLocation) {
        List<String> outbound = pathFinder.shortestPath(sourceLocation, destinationLocation);
        if (outbound == null || outbound.isEmpty()) {
            return null;
        }

        List<String> back = pathFinder.shortestPath(destinationLocation, base);
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
     * Check if the edge exist in the given path
     *
     * @param path
     * @param from
     * @param to
     * @return
     */
    public boolean isPathExistEdge(List<String> path, String from, String to) {
        if (path == null) return false;

        for (int i = 0; i + 1 < path.size(); i++) {
            if (path.get(i).equals(from) && path.get(i + 1).equals(to)) return true;
        }
        return false;
    }

    /**
     * Check if the node exist in the given path
     *
     * @param path
     * @param node
     * @return
     */
    public boolean isPathExistNode(List<String> path, String node) {
        if (path == null) return false;

        for (int i = 0; i < path.size(); i++) {
            if (path.get(i).equals(node)) return true;
        }
        return false;
    }
}
