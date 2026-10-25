package vn.edu.toeic.protocol.monitoring;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Finite, versioned executable catalog shared by collection, wire validation and presentation. */
public final class ProcessCatalog {
    public static final String LEGACY_POLICY="process-policy-v1", EXPANDED_POLICY="process-policy-v2";
    public enum Group { BROWSER, COMMUNICATION, REMOTE, DOCUMENT, TERMINAL, SYSTEM }
    public record Entry(Group group,String application) { public boolean review(){return group!=Group.SYSTEM;} }
    private static final Set<String> LEGACY=Set.of("chrome.exe","msedge.exe","firefox.exe","zalo.exe","teams.exe","discord.exe","anydesk.exe","teamviewer.exe");
    private static final Map<String,Entry> ENTRIES=catalog();
    private ProcessCatalog() { }
    private static Map<String,Entry> catalog(){
        var m=new LinkedHashMap<String,Entry>();
        add(m,Group.BROWSER,"chrome.exe","Google Chrome");add(m,Group.BROWSER,"msedge.exe","Microsoft Edge");add(m,Group.BROWSER,"firefox.exe","Mozilla Firefox");
        add(m,Group.COMMUNICATION,"zalo.exe","Zalo");add(m,Group.COMMUNICATION,"discord.exe","Discord");
        add(m,Group.COMMUNICATION,"teams.exe","Microsoft Teams");add(m,Group.COMMUNICATION,"ms-teams.exe","Microsoft Teams");
        add(m,Group.REMOTE,"anydesk.exe","AnyDesk");add(m,Group.REMOTE,"teamviewer.exe","TeamViewer");
        add(m,Group.DOCUMENT,"winword.exe","Microsoft Word");add(m,Group.DOCUMENT,"excel.exe","Microsoft Excel");add(m,Group.DOCUMENT,"notepad.exe","Notepad");
        add(m,Group.DOCUMENT,"acrord32.exe","Adobe Reader");add(m,Group.DOCUMENT,"acrobat.exe","Adobe Acrobat");
        add(m,Group.DOCUMENT,"foxitpdfreader.exe","Foxit PDF Reader");add(m,Group.DOCUMENT,"sumatrapdf.exe","SumatraPDF");
        add(m,Group.TERMINAL,"cmd.exe","Command Prompt");add(m,Group.TERMINAL,"powershell.exe","Windows PowerShell");add(m,Group.TERMINAL,"pwsh.exe","PowerShell");
        for(String name:new String[]{"svchost.exe","runtimebroker.exe","dwm.exe","explorer.exe","csrss.exe","lsass.exe","services.exe","wininit.exe","winlogon.exe","smss.exe",
                "dllhost.exe","conhost.exe","taskhostw.exe","sihost.exe","searchhost.exe","searchindexer.exe","shellexperiencehost.exe","startmenuexperiencehost.exe","applicationframehost.exe","msedgewebview2.exe"})add(m,Group.SYSTEM,name,name);
        return Map.copyOf(m);
    }
    private static void add(Map<String,Entry> map,Group group,String name,String application){map.put(name,new Entry(group,application));}
    public static Entry entry(String name){return name==null?null:ENTRIES.get(name.toLowerCase(Locale.ROOT));}
    public static boolean supported(String policy){return LEGACY_POLICY.equals(policy)||EXPANDED_POLICY.equals(policy);}
    public static Set<String> executables(String policy){
        if(LEGACY_POLICY.equals(policy))return LEGACY;
        if(EXPANDED_POLICY.equals(policy))return ENTRIES.keySet();
        throw new IllegalArgumentException("Unsupported process policy");
    }
    public static boolean includes(String policy,String name){return supported(policy)&&name!=null&&executables(policy).contains(name.toLowerCase(Locale.ROOT));}
    public static boolean review(String name){var e=entry(name);return e==null||e.review();}
    public static int limit(String policy){if(!supported(policy))throw new IllegalArgumentException("Unsupported process policy");return LEGACY_POLICY.equals(policy)?128:256;}
}
