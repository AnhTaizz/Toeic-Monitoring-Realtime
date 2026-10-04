package vn.edu.toeic.client.dashboard;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Roster;

public interface DashboardApi extends AutoCloseable {
    CompletableFuture<ScopeView> scope();
    CompletableFuture<Roster> roster();
    CompletableFuture<List<Event>> events(String attempt);
    CompletableFuture<List<Interruption>> interruptions(String attempt);
    @Override void close();
}
