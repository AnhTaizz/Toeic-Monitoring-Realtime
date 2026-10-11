package vn.edu.toeic.client.monitoring;

/** Local selection only. Network full parser still accepts production policy exclusively. */
public interface ProcessSelection {
    String version();
    boolean includes(ProcessReading reading);
}
