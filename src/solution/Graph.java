package solution;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author wick0167
 */

public class Graph {

    private final Map<String, GraphNode> graph = new ConcurrentHashMap<>();

    private final AtomicInteger version = new AtomicInteger(0);

    public Map<String, GraphNode> getGraph() {
        return graph;
    }

    public void addNode(String id) {
        graph.put(id, new GraphNode(id));
        version.incrementAndGet();
    }

    public void removeNode(String id) {
        graph.remove(id);
        for (GraphNode node : graph.values()) {
            node.neighbours.removeIf(e -> e.to.equals(id));
        }
        version.incrementAndGet();
    }

    public void addEdge(String source, String target, double weight) {
        graph.get(source).neighbours.add(new GraphEdge(target, weight));
    }

    public void removeEdge(String source, String target) {
        GraphNode node = graph.get(source);
        if (node != null) {
            node.neighbours.removeIf(e -> e.to.equals(target));
        }
        version.incrementAndGet();
    }

    public int getVersion() {
        return version.get();
    }
}
