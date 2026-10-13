package vn.edu.toeic.server.audio;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** No command/notification escapes a rolled-back transaction. Delivery failures never undo a committed start. */
@Component
public class ListeningDelivery {
    private final ListeningService listening;
    private final RealtimeSessionRegistry sockets;
    public ListeningDelivery(ListeningService listening, RealtimeSessionRegistry sockets) { this.listening = listening; this.sockets = sockets; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void started(AudioService.StartAuthorized event) {
        try {
            for (var command : listening.committedCommands(event.sessionId(),event.startId())) sockets.startListening(command);
        } catch (RuntimeException ignored) { /* Explicit proctor retry can deliver the same committed ID. */ }
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(ListeningService.StatusChanged event) {
        try { sockets.listeningStatus(listening.committedStatus(event.attemptId()),UUID.randomUUID().toString()); }
        catch (RuntimeException ignored) { /* Status is recoverable over HTTP. */ }
    }
}
