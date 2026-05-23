package solution;

import java.util.*;

/**
 * Find shortest paths using Dijkstra's algorithm
 *
 * @author wick0167
 */

public class PathFinderDijkstra implements PathFinder {

    private HashMap<String, GraphNode> graph;

    public PathFinderDijkstra(Graph graph) {
        this.graph = graph.getGraph();
    }

    /**
     * compute shortest path from source node to each node in the graph
     * this has taken from Lab03
     *
     * @param source
     * @return
     */
    public Map<String, Object> computeShortestPath(String source) {
        Map<String, Double> distance = new HashMap<>(); // store distance of the current shortest path from source to each node
        Map<String, String> predecessor = new HashMap<>(); // store the node that appears just before each node in the shortest path from source to each node
        Set<String> known = new HashSet<>(); // array indicating whether the shortest path from source to each node

        // initial values for distance and predecessor maps by node id as the key
        for (String nodeId : graph.keySet()) {
            distance.put(nodeId, Double.MAX_VALUE);
            predecessor.put(nodeId, null);
        }

        distance.put(source, 0.0);

        // Use PriorityQueue to get the least distance node
        PriorityQueue<Object[]> pq = new PriorityQueue<>(
                Comparator.comparingDouble(a -> (Double) a[0])
        );
        pq.add(new Object[]{0.0, source});

        // this loop builds:
        // shortest distance from source to each node
        // predecessor for each node based on the shortest path
        while (!pq.isEmpty()) {
            Object[] entry = pq.poll();
            String node = (String) entry[1];

            if (known.contains(node)) continue;
            known.add(node);
            GraphNode nodeObj = graph.get(node);

            if (nodeObj == null) continue;
            for (GraphEdge edge : nodeObj.neighbours) {
                String neighbour = edge.to;
                if (known.contains(neighbour)) continue;
                double newDist = distance.get(node) + edge.weight;
                if (newDist < distance.getOrDefault(neighbour, Double.MAX_VALUE)) {
                    distance.put(neighbour, newDist);
                    predecessor.put(neighbour, node);
                    pq.add(new Object[]{newDist, neighbour});
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("distance", distance);
        result.put("predecessor", predecessor);
        return result;
    }

    /**
     * construct the shortest path from source node to target node
     * this has taken from Lab03
     *
     * @param predecessor
     * @param source
     * @param target
     * @return
     */
    public List<String> constructPath(Map<String, String> predecessor, String source, String target) {
        LinkedList<String> path = new LinkedList<>();
        String current = target;

        // target is unreachable if it has no predecessor and it is not the source
        if (predecessor.get(target) == null && !target.equals(source)) {
            return Collections.emptyList();
        }

        while (current != null) {
            path.addFirst(current);
            current = predecessor.get(current);
        }
        return path;
    }

    /**
     * get shortest path between to location. result with a list of nodes for navigation
     *
     * @param source
     * @param target
     * @return
     */
    public List<String> shortestPath(String source, String target) {
        Map<String, Object> result = computeShortestPath(source);
        Map<String, String> predecessor = (Map<String, String>) result.get("predecessor");
        return constructPath(predecessor, source, target);
    }

    /**
     * get shortest distance between two locations
     *
     * @param source
     * @param target
     * @return
     */
    public double shortestDistance(String source, String target) {
        Map<String, Object> result = computeShortestPath(source);
        Map<String, Double> distance = (Map<String, Double>) result.get("distance");
        return distance.getOrDefault(target, Double.MAX_VALUE);
    }
}
