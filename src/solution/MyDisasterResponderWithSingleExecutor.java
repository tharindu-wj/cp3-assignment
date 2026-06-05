package solution;

import org.jdom2.JDOMException;
import sim.Message;

import java.io.IOException;

/**
 *
 * @author wick0167
 *
 * Responder calss offloades complex computations from communication thread to a different single thread
 */

public class MyDisasterResponderWithSingleExecutor extends MyDisasterResponderAbstract {
    @Override
    protected void handle(Message s) {
        executor.submit(() -> worker(s.text));
    }
}
