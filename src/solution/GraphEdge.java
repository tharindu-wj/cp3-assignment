package solution;

/**
 *
 * @author wick0167
 */

public class GraphEdge {
    String to; // destination node id
    double weight; // distance

    public GraphEdge(String to, double weight) {
        this.to = to;
        this.weight = weight;
    }
}

