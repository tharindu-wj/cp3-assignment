package solution;

import java.util.*;

/**
 * Find shortest paths using Floyd-Warshall algorithm
 *
 * @author wick0167
 */

public class PathFinderFloydWarshall implements PathFinder {

    // support for only small maps since the memry contraints
    // this should be around 2000 to handle the 2GB memory heap
    // however, this set to very large number to simulate heap space exceed scenarios
    private static final int MAX_FW_NODES = 1000000;
    private static final int NONE = -1;

    private final Graph graph;

    // propeties to use convert integer matrix
    private String[] indexToNode;
    private Map<String, Integer> nodeToIndex;

    private double[][] distanceMatrix;
    private int[][] viaMatrix;

    private int builtVersion = Integer.MIN_VALUE;

    public PathFinderFloydWarshall(Graph graph) {
        int numberOfNodes = graph.getGraph().size();
        if (numberOfNodes > MAX_FW_NODES) {
            throw new Error("This Floyd-Warshall implimentation only supports maps up to " + MAX_FW_NODES);
        }
        this.graph = graph;
    }



    /**
     * get shortest path between to location. result with a list of nodes for navigation
     *
     * @param source
     * @param target
     * @return
     */
    public synchronized List<String> shortestPath(String source, String target) {
        ensureBuilt();
        if (!nodeToIndex.containsKey(source) || !nodeToIndex.containsKey(target)) {
            return Collections.emptyList();
        }
        if (source.equals(target)) {
            List<String> singleNodePath = new ArrayList<>();
            singleNodePath.add(source);
            return singleNodePath;
        }
        int sourceIndex = nodeToIndex.get(source);
        int targetIndex = nodeToIndex.get(target);
        if (distanceMatrix[sourceIndex][targetIndex] == Double.POSITIVE_INFINITY) {
            return Collections.emptyList();
        }
        List<String> path = new ArrayList<>();
        path.add(source);
        appendPath(sourceIndex, targetIndex, path);
        return path;
    }

    /**
     * get shortest distance between two locations
     *
     * @param source
     * @param target
     * @return
     */
    public synchronized double shortestDistance(String source, String target) {
        ensureBuilt();
        if (!nodeToIndex.containsKey(source) || !nodeToIndex.containsKey(target)) {
            return Double.MAX_VALUE;
        }
        double distance = distanceMatrix[nodeToIndex.get(source)][nodeToIndex.get(target)];
        return distance == Double.POSITIVE_INFINITY ? Double.MAX_VALUE : distance;
    }

    /**
     * check if the graph ahs changed
     */
    private void ensureBuilt() {
        if (distanceMatrix != null && graph.getVersion() == builtVersion) {
            return;
        }
        build();
        builtVersion = graph.getVersion();
    }

    /**
     *
     */
    private void build() {
        Map<String, GraphNode> nodes = graph.getGraph();
        int numberOfNodes = nodes.size();

        indexToNode = new String[numberOfNodes];
        nodeToIndex = new HashMap<>();
        int nextIndex = 0;
        for (String nodeId : nodes.keySet()) {
            indexToNode[nextIndex] = nodeId;
            nodeToIndex.put(nodeId, nextIndex);
            nextIndex++;
        }

        distanceMatrix = new double[numberOfNodes][numberOfNodes];
        viaMatrix = new int[numberOfNodes][numberOfNodes];
        for (int i = 0; i < numberOfNodes; i++) {
            for (int j = 0; j < numberOfNodes; j++) {
                distanceMatrix[i][j] = (i == j) ? 0.0 : Double.POSITIVE_INFINITY;
                viaMatrix[i][j] = NONE;
            }
        }
        for (GraphNode node : nodes.values()) {
            int sourceIndex = nodeToIndex.get(node.id);
            for (GraphEdge edge : node.neighbours) {
                Integer targetIndex = nodeToIndex.get(edge.to);

                // skip when node has removed
                if (targetIndex == null) {
                    continue;
                }

                // keep the smallest parallel edge
                if (edge.weight < distanceMatrix[sourceIndex][targetIndex]) {
                    distanceMatrix[sourceIndex][targetIndex] = edge.weight;
                }
            }
        }

        //
        for (int intermediateNode = 0; intermediateNode < numberOfNodes; intermediateNode++) {
            for (int sourceIndex = 0; sourceIndex < numberOfNodes; sourceIndex++) {
                if (distanceMatrix[sourceIndex][intermediateNode] == Double.POSITIVE_INFINITY) {
                    continue;
                }
                for (int targetIndex = 0; targetIndex < numberOfNodes; targetIndex++) {
                    double distanceThroughIntermediate =
                            distanceMatrix[sourceIndex][intermediateNode] + distanceMatrix[intermediateNode][targetIndex];
                    if (distanceThroughIntermediate < distanceMatrix[sourceIndex][targetIndex]) {
                        distanceMatrix[sourceIndex][targetIndex] = distanceThroughIntermediate;
                        viaMatrix[sourceIndex][targetIndex] = intermediateNode;
                    }
                }
            }
        }
    }

    /**
     *
     * @param sourceIndex
     * @param targetIndex
     * @param path
     */
    private void appendPath(int sourceIndex, int targetIndex, List<String> path) {
        int intermediateNode = viaMatrix[sourceIndex][targetIndex];
        if (intermediateNode == NONE) {
            path.add(indexToNode[targetIndex]);
        } else {
            appendPath(sourceIndex, intermediateNode, path);
            appendPath(intermediateNode, targetIndex, path);
        }
    }
}
