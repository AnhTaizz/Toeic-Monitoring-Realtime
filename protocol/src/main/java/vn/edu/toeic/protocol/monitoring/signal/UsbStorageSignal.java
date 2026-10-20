package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Count of classified mass-storage only; optional random run-local reference, never a hardware identifier. */
public record UsbStorageSignal(String eventId, String observerSessionId, long observationSequence,
                               Instant observedAt, EventOrigin origin, Kind kind, Quality quality,
                               int storageCount, String deviceRef) implements Signal {
    public enum Kind { USB_STORAGE_PRESENT_AT_START, USB_STORAGE_CONNECTED, USB_STORAGE_REMOVED }
    public enum Quality { COMPLETE, PARTIAL }

    public UsbStorageSignal {
        Signal.validateCommon(eventId, observerSessionId, observationSequence, observedAt, origin);
        if (kind == null || quality == null || storageCount < 0 || storageCount > 64
                || kind != Kind.USB_STORAGE_REMOVED && storageCount == 0)
            throw new IllegalArgumentException();
        if (deviceRef != null) {
            SignalJson.id(deviceRef);
            if (deviceRef.length() > 64) throw new IllegalArgumentException();
        } else if (quality == Quality.COMPLETE) throw new IllegalArgumentException();
    }
    public String eventType() { return kind.name(); }
    public String source() { return "WINDOWS_USB_STORAGE"; }
    public String metadataQuality() { return quality.name(); }
    public JsonObject details() {
        JsonObject d = new JsonObject();
        d.addProperty("deviceClass", "USB_MASS_STORAGE");
        d.addProperty("storageCount", storageCount);
        if (deviceRef != null) d.addProperty("deviceRef", deviceRef);
        return d;
    }
}
