package vn.edu.toeic.client.dashboard;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.DashboardModel.Snapshot;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;

/** One state worker, one existing B2 socket, bounded push inbox and five HTTP requests at most.
 * Snapshot delivery is immutable; UI decides how to marshal it onto JavaFX.
 */
public final class DashboardController implements AutoCloseable {
    private record Push(long epoch, MessageEnvelope<JsonObject> message) { }
    private final DashboardApi api;
    private final MonitoringTransport transport;
    private final Consumer<Set<String>> applyScope;
    private final Consumer<Snapshot> observer;
    private final Runnable sessionExpired;
    private final DashboardModel model;
    private final ExecutorService worker;
    private final ArrayBlockingQueue<Push> inbox;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean(), pumpQueued = new AtomicBoolean(), overflow = new AtomicBoolean();
    private final AtomicLong epoch = new AtomicLong();
    private final AutoCloseable messages, states;
    private volatile Snapshot latest;
    private long refreshGeneration, selectionGeneration, historyGeneration;
    private long eventGeneration;
    private long stateGeneration;
    private CompletableFuture<?> eventRequest, historyRequest,stateRequest;
    private boolean syncing, historyDirty, resyncRequested;
    private int reloadBudget=2;
    public DashboardController(Role role, DashboardApi api, MonitoringTransport transport, Consumer<Set<String>> applyScope,
            Set<String> initialScope, Consumer<Snapshot> observer, Runnable sessionExpired) {
        this(role,api,transport,applyScope,initialScope,observer,sessionExpired,
                Integer.getInteger("toeic.dashboard.pushBuffer",256),Integer.getInteger("toeic.dashboard.maxRows",5000),
                Executors.newSingleThreadExecutor(task -> { Thread thread=new Thread(task,"toeic-dashboard-worker"); thread.setDaemon(true); return thread; }));
    }
    DashboardController(Role role, DashboardApi api, MonitoringTransport transport, Consumer<Set<String>> applyScope,
            Set<String> initialScope, Consumer<Snapshot> observer, Runnable sessionExpired, int buffer, int rows, ExecutorService worker) {
        if (role!=Role.PROCTOR) { worker.shutdownNow(); api.close(); throw new IllegalArgumentException("Dashboard chỉ dành cho giám thị"); }
        if (buffer<1 || buffer>100000 || rows<1 || rows>100000 || initialScope.size()>rows) {
            worker.shutdownNow(); api.close(); throw new IllegalArgumentException("Giới hạn dashboard không hợp lệ");
        }
        this.api=api; this.transport=transport; this.applyScope=applyScope; this.observer=observer; this.sessionExpired=sessionExpired;
        this.worker=worker; inbox=new ArrayBlockingQueue<>(buffer); model=new DashboardModel(initialScope,buffer,rows); latest=model.snapshot();
        messages=transport.onMessage(this::receive);
        states=transport.onConnectionState(this::stateChanged);
        stateChanged(transport.connectionState());
    }
    public Snapshot snapshot() { return latest; }
    private void dispatch(Runnable action) {
        if (closed.get()) return;
        try { worker.execute(() -> { if (!closed.get() && !model.snapshot().loginRequired()) action.run(); }); }
        catch (RejectedExecutionException ignored) { }
    }
    private void publish() {
        if (closed.get()) return;
        latest=model.snapshot();
        try { observer.accept(latest); } catch (RuntimeException ignored) { }
    }
    private void stateChanged(ConnectionState state) {
        long current=epoch.incrementAndGet();
        dispatch(() -> {
            if (current!=epoch.get()) return;
            refreshGeneration++; selectionGeneration++; cancelRequests(); inbox.clear(); overflow.set(false);
            syncing=false; model.connection(state);
            if (state==ConnectionState.CONNECTED) { reloadBudget=2; refreshNow(); }
            publish();
        });
    }
    public void refresh() { dispatch(() -> { reloadBudget=2; refreshNow(); publish(); }); }
    private void refreshNow() {
        if (transport.connectionState()!=ConnectionState.CONNECTED) { model.rosterFailed("Dashboard chưa kết nối. Dữ liệu đang hiển thị là dữ liệu cũ."); return; }
        long generation=++refreshGeneration, current=epoch.get(); selectionGeneration++; historyDirty=false;
        cancelRequests(); syncing=true; resyncRequested=false; model.beginRoster();
        if (model.selected()!=null) { model.beginEvents(); model.beginHistory(); }
        request(api.scope(), (scope,failure) -> {
            if (!current(current,generation)) return;
            if (failure!=null) { syncing=false; fail("roster",null,failure); return; }
            if (scope.role()!=Role.PROCTOR) { expire(); return; }
            model.scope(scope.attemptScope());
            try { applyScope.accept(scope.attemptScope()); }
            catch (RuntimeException ignored) { syncing=false; model.rosterFailed("Chưa cập nhật được quyền kết nối. Hãy làm mới khi đã kết nối."); publish(); return; }
            request(api.roster(),(roster,rosterFailure) -> {
                if (!current(current,generation)) return;
                syncing=false;
                if (rosterFailure!=null) { fail("roster",null,rosterFailure); return; }
                try { model.roster(roster.attempts()); }
                catch (RuntimeException ignored) { model.rosterFailed("Dữ liệu danh sách mâu thuẫn. Hãy làm mới để đối chiếu HTTP."); publish(); return; }
                if (model.selected()!=null) loadDetails();
                publish(); if (resyncRequested) resync();
            });
            publish();
        });
    }
    private boolean current(long connection,long generation) { return connection==epoch.get() && generation==refreshGeneration && transport.connectionState()==ConnectionState.CONNECTED; }
    public void select(String attempt) {
        dispatch(() -> {
            if (attempt!=null && !model.allowed(attempt)) return;
            if (Objects.equals(attempt,model.selected())) return;
            selectionGeneration++; historyGeneration++; historyDirty=false;
            cancelDetails();
            model.select(attempt);
            if (attempt!=null && transport.connectionState()==ConnectionState.CONNECTED) loadDetails();
            publish();
        });
    }
    private void loadDetails() {
        String attempt=model.selected(); if (attempt==null) return;
        long selection=selectionGeneration, refresh=refreshGeneration, current=epoch.get(), request=++eventGeneration;
        if (eventRequest!=null) eventRequest.cancel(true);
        model.beginEvents();
        CompletableFuture<List<Event>> future=api.events(attempt); eventRequest=future;
        request(future,(events,failure) -> {
            if (!selected(attempt,current,refresh,selection) || request!=eventGeneration) return;
            if (failure!=null) { fail("events",attempt,failure); return; }
            try { model.events(events); }
            catch (RuntimeException ignored) { model.eventsFailed("Cảnh báo mâu thuẫn. Hãy làm mới để đối chiếu HTTP."); }
            publish();
        });
        loadHistory();
        loadState();
    }
    private void loadState() {
        String attempt=model.selected(); if(attempt==null) return;
        long selection=selectionGeneration,refresh=refreshGeneration,current=epoch.get(),request=++stateGeneration;
        if(stateRequest!=null) stateRequest.cancel(true);
        model.beginState();
        CompletableFuture<MonitoringStateView> future=api.state(attempt); stateRequest=future;
        request(future,(state,failure) -> {
            if(!selected(attempt,current,refresh,selection) || request!=stateGeneration) return;
            if(failure!=null) { fail("state",attempt,failure); return; }
            try { model.state(state,true); }
            catch(RuntimeException invalid) { model.stateFailed("Trạng thái process mâu thuẫn. Hãy làm mới."); }
            publish();
        });
    }
    private boolean selected(String attempt,long connection,long refresh,long selection) {
        return current(connection,refresh) && selection==selectionGeneration && attempt.equals(model.selected()) && model.allowed(attempt);
    }
    private void loadHistory() {
        String attempt=model.selected(); if (attempt==null) return;
        long selection=selectionGeneration, refresh=refreshGeneration, current=epoch.get(), request=++historyGeneration;
        if (historyRequest!=null) historyRequest.cancel(true);
        historyDirty=false; model.beginHistory();
        CompletableFuture<List<Interruption>> future=api.interruptions(attempt); historyRequest=future;
        request(future,(history,failure) -> {
            if (!selected(attempt,current,refresh,selection) || request!=historyGeneration) return;
            if (failure!=null) { fail("history",attempt,failure); return; }
            try { model.history(history); }
            catch (RuntimeException ignored) { model.historyFailed("Lịch sử mâu thuẫn. Hãy làm mới để đối chiếu HTTP."); }
            if (historyDirty) loadHistory();
            publish();
        });
    }
    private <T> void request(CompletableFuture<T> future, BiConsumer<T,Throwable> result) {
        pending.add(future);
        if (closed.get()) { pending.remove(future); future.cancel(true); return; }
        future.whenComplete((value,failure) -> { pending.remove(future); dispatch(() -> result.accept(value,failure)); });
    }
    private void receive(MessageEnvelope<JsonObject> message) {
        if (closed.get()) return;
        if (message.type().equals("ERROR")) {
            String code=message.payload().get("code").getAsString();
            if (code.equals("UNAUTHORIZED")) dispatch(this::expire);
            return;
        }
        if (!message.type().equals("MONITOR_PRESENCE") && !message.type().equals("MONITOR_WARNING") && !message.type().equals("MONITOR_STATE")) return;
        if (!inbox.offer(new Push(epoch.get(),message))) overflow.set(true);
        queuePump();
    }
    private void queuePump() { if (pumpQueued.compareAndSet(false,true)) dispatch(this::pump); }
    private void pump() {
        boolean reload=overflow.getAndSet(false);
        if (reload) model.overflow();
        for (int count=0;count<inbox.remainingCapacity()+inbox.size();count++) {
            Push push=inbox.poll(); if (push==null) break;
            if (push.epoch()!=epoch.get() || transport.connectionState()!=ConnectionState.CONNECTED) continue;
            try {
                if (push.message().type().equals("MONITOR_STATE")) {
                    MonitoringStateView state=MonitoringStateView.parse(push.message().payload());
                    if(!state.attemptId().equals(push.message().attemptId())) throw new IllegalArgumentException();
                    model.state(state,false);
                } else if (push.message().type().equals("MONITOR_PRESENCE")) {
                    MonitoringJson.push(push.message());
                    if (model.presence(MonitoringJson.presence(push.message().payload()))) {
                        if (model.snapshot().historyLoading()) historyDirty=true; else loadHistory();
                    }
                } else { MonitoringJson.push(push.message()); model.warning(MonitoringJson.event(push.message().payload())); }
            } catch (RuntimeException ignored) {
                if (push.message().type().equals("MONITOR_STATE")) model.stateFailed("Cập nhật process mâu thuẫn. Cần đối chiếu HTTP.");
                else if (push.message().type().equals("MONITOR_PRESENCE")) model.rosterFailed("Cập nhật trạng thái mâu thuẫn. Cần đối chiếu HTTP.");
                else model.eventsFailed("Cập nhật cảnh báo mâu thuẫn. Cần đối chiếu HTTP.");
                reload=true;
            }
        }
        pumpQueued.set(false);
        if (!inbox.isEmpty() || overflow.get()) queuePump();
        if (reload) { resyncRequested=true; resync(); }
        publish();
    }
    private void resync() {
        if (syncing || !resyncRequested) return;
        if (reloadBudget>0) { reloadBudget--; refreshNow(); }
        // Otherwise keep stale/error visible. Explicit refresh grants a fresh finite budget.
    }
    private void fail(String part,String attempt,Throwable failure) {
        Throwable cause=failure; while (cause instanceof CompletionException && cause.getCause()!=null) cause=cause.getCause();
        if (cause instanceof LoginFailedException denied && denied.statusCode()==401) { expire(); return; }
        if (cause instanceof LoginFailedException denied && denied.statusCode()==403) {
            if (attempt==null) { expire(); return; }
            model.remove(attempt); selectionGeneration++; historyGeneration++; resyncRequested=true;
            model.rosterFailed("Lượt đang xem không còn quyền. Đang kiểm tra lại danh sách.");
            publish(); resync(); return;
        }
        String text="Không tải được dữ liệu hợp lệ. Dữ liệu đang hiển thị là dữ liệu cũ; hãy làm mới.";
        if (part.equals("roster")) model.rosterFailed(text);
        else if (part.equals("events")) model.eventsFailed(text);
        else if (part.equals("state")) model.stateFailed(text);
        else model.historyFailed(text);
        publish();
    }
    private void expire() {
        cancelRequests(); inbox.clear(); model.expire(); publish();
        unsubscribe(); api.close(); worker.shutdown();
        try { sessionExpired.run(); } catch (RuntimeException ignored) { }
    }
    private void cancelDetails() {
        eventGeneration++; historyGeneration++; stateGeneration++;
        if (eventRequest!=null) eventRequest.cancel(true);
        if (historyRequest!=null) historyRequest.cancel(true);
        if (stateRequest!=null) stateRequest.cancel(true);
        eventRequest=historyRequest=stateRequest=null;
    }
    private void cancelRequests() { cancelDetails(); for (CompletableFuture<?> request : Set.copyOf(pending)) request.cancel(true); pending.clear(); }
    private void unsubscribe() { try { messages.close(); } catch (Exception ignored) { } try { states.close(); } catch (Exception ignored) { } }
    @Override public void close() {
        if (!closed.compareAndSet(false,true)) return;
        epoch.incrementAndGet(); unsubscribe(); cancelRequests(); inbox.clear(); api.close(); worker.shutdownNow();
        latest=new DashboardModel(Set.of(),1,1).snapshot();
    }
}
