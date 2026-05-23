package solution;

import java.util.HashMap;

/**
 *
 * @author wick0167
 */

public class Graph {

    private HashMap<String, GraphNode> graph = new HashMap<>();

    public HashMap<String, GraphNode> getGraph() {
        return graph;
    }

    public void addNode(String id) {
        graph.put(id, new GraphNode(id));
    }

    public void removeNode(String id) {
        graph.remove(id);
        for (GraphNode node : graph.values()) {
            node.neighbours.removeIf(e -> e.to.equals(id));
        }
    }

    public void addEdge(String source, String target, double weight) {
        graph.get(source).neighbours.add(new GraphEdge(target, weight));
    }

    public void removeEdge(String source, String target) {
        GraphNode node = graph.get(source);
        if (node != null) {
            node.neighbours.removeIf(e -> e.to.equals(target));
        }
    }
}
