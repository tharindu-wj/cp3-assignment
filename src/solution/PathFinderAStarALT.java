package solution;

import java.util.*;

/**
 * Find shortest paths using A* with the ALT (A* + Landmarks + Triangle Inequality) algorithm
 * Followed below implimentation and applied some logics into this class:
 * https://github.com/jgrapht/jgrapht/blob/master/jgrapht-core/src/main/java/org/jgrapht/alg/shortestpath/ALTAdmissibleHeuristic.java
 *
 * @author wick0167
 */

public class PathFinderAStarALT implements PathFinder {

    // used to check non negative weight checks
    private static final double TOLERANCE = 1e-9; // 0.000000001

    // forward adjacency
    private final Map<String, GraphNode> graph;

    // incoming edges used to store distance to a landmark
    private final Map<String, List<GraphEdge>> reverseAdjacency;

    private final boolean directed;

    private final Map<String, Map<String, Double>> fromLandmark = new HashMap<>();
    private final Map<String, Map<String, Double>> toLandmark;


    public PathFinderAStarALT(Graph graph, int numLandmarks, boolean directed) {
        this.graph = graph.getGraph();
        this.directed = directed;
        this.toLandmark = directed ? new HashMap<>() : this.fromLandmark;
        this.reverseAdjacency = directed ? buildReverseAdjacency(this.graph) : null;
        selectLandmarksAndPrecompute(Math.max(1, numLandmarks));
    }

    // used to store nodes with the priority
    private static final class FrontierNode {
        final String node;
        final double priority;

        FrontierNode(String node, double priority) {
            this.node = node;
            this.priority = priority;
        }
    }


    /**
     * get shortest path between to location. result with a list of nodes for navigation
     *
     * @param source
     * @param target
     * @return
     */
    public List<String> shortestPath(String source, String target) {
        if (source.equals(target)) {
            List<String> singleNodePath = new ArrayList<>();
            singleNodePath.add(source);
            return singleNodePath;
        }
        if (!graph.containsKey(source) || !graph.containsKey(target)) {
            return Collections.emptyList();
        }
        Map<String, String> predecessor = new HashMap<>();
        double shortest = aStar(source, target, predecessor);
        if (shortest == Double.MAX_VALUE) {
            return Collections.emptyList();
        }
        return reconstructPath(predecessor, source, target);
    }

    /**
     * get shortest distance between two locations
     *
     * @param source
     * @param target
     * @return
     */
    public double shortestDistance(String source, String target) {
        if (source.equals(target)) {
            return 0.0;
        }
        if (!graph.containsKey(source) || !graph.containsKey(target)) {
            return Double.MAX_VALUE;
        }
        return aStar(source, target, null);
    }

    /**
     * A* search implementation methods
     */

    /**
     * A* search method
     *
     * @param source
     * @param target
     * @param predecessor
     * @return
     */
    private double aStar(String source, String target, Map<String, String> predecessor) {
        // best known distance from source
        Map<String, Double> distanceFromStart = new HashMap<>();

        // nodes with final distance
        Set<String> settledNodes = new HashSet<>();
        PriorityQueue<FrontierNode> frontier = new PriorityQueue<>(Comparator.comparingDouble(frontierNode -> frontierNode.priority));

        distanceFromStart.put(source, 0.0);
        frontier.add(new FrontierNode(source, getCostEstimate(source, target)));

        while (!frontier.isEmpty()) {
            String currentNode = frontier.poll().node;

            // skip outdated queue entries
            if (settledNodes.contains(currentNode)) {
                continue;
            }
            settledNodes.add(currentNode);

            // distance can be finalised when the target is reached
            if (currentNode.equals(target)) {
                return distanceFromStart.get(currentNode);
            }

            double currentDistance = distanceFromStart.get(currentNode);
            GraphNode currentGraphNode = graph.get(currentNode);

            // skip removed nodes
            if (currentGraphNode == null) {
                continue;
            }

            // improve the distance to each neighbour
            for (GraphEdge edge : currentGraphNode.neighbours) {
                String neighbourNode = edge.to;
                if (settledNodes.contains(neighbourNode)) {
                    continue;
                }
                double tentativeDistance = currentDistance + edge.weight;
                if (tentativeDistance < distanceFromStart.getOrDefault(neighbourNode, Double.POSITIVE_INFINITY)) {
                    distanceFromStart.put(neighbourNode, tentativeDistance);
                    if (predecessor != null) {
                        predecessor.put(neighbourNode, currentNode);
                    }

                    double priority = tentativeDistance + getCostEstimate(neighbourNode, target);
                    frontier.add(new FrontierNode(neighbourNode, priority));
                }
            }
        }
        return Double.MAX_VALUE;
    }

    /**
     * Dijkrsta's algorithm method
     *
     * @param source
     * @param followRoadDirection
     * @return
     */
    private Map<String, Double> dijkstra(String source, boolean followRoadDirection) {
        Map<String, Double> distanceFromSource = new HashMap<>();
        for (String nodeId : graph.keySet()) {
            distanceFromSource.put(nodeId, Double.POSITIVE_INFINITY);
        }
        Set<String> settledNodes = new HashSet<>();
        PriorityQueue<FrontierNode> frontier = new PriorityQueue<>(Comparator.comparingDouble(frontierNode -> frontierNode.priority));

        distanceFromSource.put(source, 0.0);
        frontier.add(new FrontierNode(source, 0.0));

        while (!frontier.isEmpty()) {
            String currentNode = frontier.poll().node;
            if (settledNodes.contains(currentNode)) {
                continue;
            }
            settledNodes.add(currentNode);

            double currentDistance = distanceFromSource.get(currentNode);
            List<GraphEdge> edges = followRoadDirection ? neighboursOf(currentNode) : reverseAdjacency.get(currentNode);
            if (edges == null) {
                continue;
            }

            for (GraphEdge edge : edges) {
                String neighbourNode = edge.to;
                double newDistance = currentDistance + edge.weight;
                if (newDistance < distanceFromSource.getOrDefault(neighbourNode, Double.POSITIVE_INFINITY)) {
                    distanceFromSource.put(neighbourNode, newDistance);
                    frontier.add(new FrontierNode(neighbourNode, newDistance));
                }
            }
        }
        return distanceFromSource;
    }

    /**
     * traverse through links backward to constructur the path
     *
     * @param predecessor
     * @param source
     * @param target
     * @return
     */
    private List<String> reconstructPath(Map<String, String> predecessor, String source, String target) {
        LinkedList<String> path = new LinkedList<>();
        String currentNode = target;
        while (currentNode != null) {
            path.addFirst(currentNode);
            if (currentNode.equals(source)) {
                break;
            }
            currentNode = predecessor.get(currentNode);
        }
        if (path.isEmpty() || !path.getFirst().equals(source)) {
            return Collections.emptyList();
        }
        return path;
    }

    /**
     * ALT landmark heuristic methods
     * Referred from https://github.com/jgrapht/jgrapht/blob/master/jgrapht-core/src/main/java/org/jgrapht/alg/shortestpath/ALTAdmissibleHeuristic.java
     */

    /**
     *  An admissible heuristic estimate from a source vertex to a target vertex.
     *  The estimate is always non-negative and never overestimates the true distance.
     *
     * @param source
     * @param target
     * @return
     */
    private double getCostEstimate(String source, String target) {
        double bestEstimate = 0.0;

        // special case, source equals goal
        if (source.equals(target)) {
            return bestEstimate;
        }
        //  Special case, source is landmark
        //  Exact distance is already stored
        if (fromLandmark.containsKey(source)) {
            return fromLandmark.get(source).get(target);
        }
        //  Special case, target is landmark
        //  exact distance is already stored
        if (toLandmark.containsKey(target)) {
            return toLandmark.get(target).get(source);
        }
        //  Compute from landmarks
        for (String landmark : fromLandmark.keySet()) {
            double landmarkEstimate;
            Map<String, Double> distanceFromLandmark = fromLandmark.get(landmark);
            if (directed) {
                Map<String, Double> distanceToLandmark = toLandmark.get(landmark);
                landmarkEstimate = Math.max(
                        distanceToLandmark.get(source) - distanceToLandmark.get(target),
                        distanceFromLandmark.get(target) - distanceFromLandmark.get(source));
            } else {
                landmarkEstimate = Math.abs(distanceFromLandmark.get(source) - distanceFromLandmark.get(target));
            }

            // max over all landmark
            if (Double.isFinite(landmarkEstimate)) {
                bestEstimate = Math.max(bestEstimate, landmarkEstimate);
            }
        }
        return bestEstimate;
    }

    /**
     *  Compute all distances to and from a landmar
     *
     * @param landmark
     */
    private void precomputeToFromLandmark(String landmark) {
        GraphNode landmarkNode = graph.get(landmark);
        if (landmarkNode != null) {
            for (GraphEdge edge : landmarkNode.neighbours) {
                if (edge.weight < -TOLERANCE) {
                    throw new IllegalArgumentException("Graph edge weights cannot be negative");
                }
            }
        }

        // distances from the landmark
        fromLandmark.put(landmark, dijkstra(landmark, true));

        // distances to the landmark
        if (directed) {
            toLandmark.put(landmark, dijkstra(landmark, false));
        }
    }

    /**
     *
     * @param numberOfLandmarks
     */
    private void selectLandmarksAndPrecompute(int numberOfLandmarks) {
        if (graph.isEmpty()) {
            return;
        }
        List<String> allNodeIds = new ArrayList<>(graph.keySet());
        int landmarkTarget = Math.min(numberOfLandmarks, allNodeIds.size());

        Map<String, Double> distanceFromStart = dijkstra(allNodeIds.get(0), true);
        precomputeToFromLandmark(farthestFrom(distanceFromStart, allNodeIds));

        while (fromLandmark.size() < landmarkTarget) {
            String bestCandidate = null;
            double largestMinimumDistance = -1.0;
            for (String candidate : allNodeIds) {
                double minDistanceToChosenLandmarks = Double.POSITIVE_INFINITY;
                for (Map<String, Double> landmarkDistanceMap : fromLandmark.values()) {
                    minDistanceToChosenLandmarks = Math.min(minDistanceToChosenLandmarks, landmarkDistanceMap.getOrDefault(candidate, Double.POSITIVE_INFINITY));
                }
                if (!Double.isFinite(minDistanceToChosenLandmarks)) {
                    continue;
                }
                if (minDistanceToChosenLandmarks > largestMinimumDistance) {
                    largestMinimumDistance = minDistanceToChosenLandmarks;
                    bestCandidate = candidate;
                }
            }
            if (bestCandidate == null) {
                break;
            }
            precomputeToFromLandmark(bestCandidate);
        }
    }

    /**
     *
     * @param distanceMap
     * @param allNodeIds
     * @return
     */
    private static String farthestFrom(Map<String, Double> distanceMap, List<String> allNodeIds) {
        String farthestNode = allNodeIds.get(0);
        double farthestDistance = -1.0;
        for (String nodeId : allNodeIds) {
            double distance = distanceMap.getOrDefault(nodeId, Double.POSITIVE_INFINITY);
            if (Double.isFinite(distance) && distance > farthestDistance) {
                farthestDistance = distance;
                farthestNode = nodeId;
            }
        }
        return farthestNode;
    }

    /**
     *
     * @param nodeId
     * @return
     */
    private List<GraphEdge> neighboursOf(String nodeId) {
        GraphNode node = graph.get(nodeId);
        return node == null ? null : node.neighbours;
    }

    private static Map<String, List<GraphEdge>> buildReverseAdjacency(Map<String, GraphNode> graph) {
        Map<String, List<GraphEdge>> reversedAdjacency = new HashMap<>();
        for (String nodeId : graph.keySet()) {
            reversedAdjacency.put(nodeId, new ArrayList<>());
        }
        for (GraphNode node : graph.values()) {
            for (GraphEdge edge : node.neighbours) {
                reversedAdjacency.computeIfAbsent(edge.to, key -> new ArrayList<>()).add(new GraphEdge(node.id, edge.weight));
            }
        }
        return reversedAdjacency;
    }
}
