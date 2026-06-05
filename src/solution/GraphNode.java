package solution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author wick0167
 */

public class GraphNode {
    String id;
    List<GraphEdge> neighbours = new CopyOnWriteArrayList<>();

    public GraphNode(String id) {
        this.id = id;
    }
}
