package solution;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author wick0167
 */

public class GraphNode {
    String id;
    List<GraphEdge> neighbours = new ArrayList<>();

    public GraphNode(String id) {
        this.id = id;
    }
}
