package vn.edu.toeic.client.proctor.redesign;

import java.util.Set;
import java.util.stream.Collectors;

/** Test-only access to the production room projection; callers run on the FX thread. */
public final class MonitoringFeedProbe {
    private MonitoringFeedProbe() { }
    public static String diagnostic(ProctorRedesignView view,String attempt) {
        if(view.room()==null)return "room absent";
        var item=view.room().feed().of(attempt);
        return "history="+historyTypes(view,attempt)+", alerts="+alertTypes(view,attempt)+", feed="
                +(item==null?"absent":"loaded="+item.loaded()+", loading="+item.loading()+", signals="+item.signals().size()+", error="+item.error()+", unsupported="+item.signalsUnavailable());
    }
    public static Set<String> historyTypes(ProctorRedesignView view,String attempt) {
        if(view.room()==null)return Set.of();
        return view.room().timeline().stream().filter(e->e.attemptId().equals(attempt))
                .map(e->e.alert().kind().name()).collect(Collectors.toSet());
    }
    public static Set<String> alertTypes(ProctorRedesignView view,String attempt) {
        if(view.room()==null)return Set.of();
        return view.room().alerts().stream().filter(e->e.attemptId().equals(attempt))
                .map(e->e.kind().name()).collect(Collectors.toSet());
    }
    public static boolean filterVisible(ProctorRedesignView view,String attempt,String kind) {
        view.show(ProctorRedesignView.Section.ALERTS);
        var pane=view.pane(AlertsPane.class);
        pane.kind(RoomModel.Kind.valueOf(kind));
        return pane.visibleAlerts().stream().anyMatch(a->a.attemptId().equals(attempt)&&a.kind().name().equals(kind));
    }
    public static Set<String> reviewProcessNames(ProctorRedesignView view,String attempt){
        if(view.room()==null)return Set.of();
        return view.room().alerts().stream().filter(a->a.attemptId().equals(attempt)&&a.origin() instanceof vn.edu.toeic.client.dashboard.MonitoringData.Event)
                .map(a->((vn.edu.toeic.client.dashboard.MonitoringData.Event)a.origin()).processName()).collect(Collectors.toSet());
    }
}
