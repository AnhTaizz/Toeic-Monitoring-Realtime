package vn.edu.toeic.client.dashboard;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Roster;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;

public interface DashboardApi extends AutoCloseable {
    CompletableFuture<ScopeView> scope();
    CompletableFuture<Roster> roster();
    CompletableFuture<List<Event>> events(String attempt);
    CompletableFuture<List<Interruption>> interruptions(String attempt);
    default CompletableFuture<MonitoringStateView> state(String attempt) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Full state API chưa được cung cấp"));
    }
    @Override void close();
}
