package solution;

import java.util.HashMap;

public class Graph {

    private HashMap<String, Node> graph = new HashMap<>();

    public HashMap<String, Node> getGraph() {
        return graph;
    }

    public void addNode(String id) {
        graph.put(id, new Node(id));
    }

    public void removeNode(String id) {
        graph.remove(id);
        for (Node node : graph.values()) {
            node.neighbours.removeIf(e -> e.to.equals(id));
        }
    }

    public void addEdge(String source, String target, double weight) {
        graph.get(source).neighbours.add(new Edge(target, weight));
    }

    public void removeEdge(String source, String target) {
        Node node = graph.get(source);
        if (node != null) {
            node.neighbours.removeIf(e -> e.to.equals(target));
        }
    }
}
