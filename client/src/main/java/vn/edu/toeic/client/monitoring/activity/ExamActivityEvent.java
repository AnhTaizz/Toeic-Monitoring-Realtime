package vn.edu.toeic.client.monitoring.activity;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/** Privacy-minimal in-process marker. It deliberately contains no question, option or text data. */
public final class ExamActivityEvent extends Event {
    public enum Kind { NAVIGATION, ANSWER_ACTION }
    public static final EventType<ExamActivityEvent> ANY = new EventType<>(Event.ANY, "TOEIC_EXAM_ACTIVITY");
    private final Kind kind;

    public ExamActivityEvent(Kind kind) {
        super(ANY);
        if (kind == null) throw new IllegalArgumentException();
        this.kind = kind;
    }

    public Kind kind() { return kind; }
    public static void fire(EventTarget target, Kind kind) {
        if (target == null) throw new IllegalArgumentException();
        Event.fireEvent(target, new ExamActivityEvent(kind));
    }
}
