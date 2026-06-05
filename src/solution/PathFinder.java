package solution;

import java.util.List;

/**
 * @author wick0167
 *
 * Interface for shortest path finding algorithms
 */

public interface PathFinder {
    List<String> shortestPath(String source, String target);

    double shortestDistance(String source, String target);
}
