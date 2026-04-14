package solution;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author wick0167
 */

public class Node {
    String id;
    List<Edge> neighbours = new ArrayList<>();

    public Node(String id) {
        this.id = id;
    }
}
