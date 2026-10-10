package vn.edu.toeic.client.monitoring;

/** Optional tap of the existing collector. Implementations must not block scanning with disk I/O. */
public interface ObservationTrace {
    ObservationTrace NONE = new ObservationTrace() { };
    default void started(String collector, String policy, long tick) { }
    default void observed(ProcessSnapshot snapshot) { }
    default void failed(String collector, String policy, long tick) { }
    default void stopped(String collector, long tick) { }
}
