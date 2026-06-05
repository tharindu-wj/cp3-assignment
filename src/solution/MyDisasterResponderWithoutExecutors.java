package solution;

import sim.Message;

/**
 *
 * @author wick0167
 *
 * Responder class that runs all complex calculation withing the communication thread
 */

public class MyDisasterResponderWithoutExecutors extends MyDisasterResponderAbstract {
    @Override
    protected void handle(Message s) {
        worker(s.text);
    }
}
