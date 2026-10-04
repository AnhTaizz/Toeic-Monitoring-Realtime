package vn.edu.toeic.client.monitoring;

public record ObservedProcess(ProcessIdentity identity, String executableName, MetadataQuality metadataQuality) { }
