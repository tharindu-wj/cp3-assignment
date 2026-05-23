package solution;

import sim.Message;

/**
 *
 * @author wick0167
 *
 */

public class MyDisasterResponderWithoutExecutors extends MyDisasterResponderAbstract {
    @Override
    protected void handle(Message s) {
        worker(s.text);
    }
}
