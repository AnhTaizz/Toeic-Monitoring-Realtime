package vn.edu.toeic.client.monitoring;

import java.util.List;

@FunctionalInterface
public interface ProcessSnapshotSource {
    List<ProcessReading> scan();
}
