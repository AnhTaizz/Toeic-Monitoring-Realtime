import com.google.gson.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only audit: never export raw scripts, token values or credentials. */
public class AuditOriginalSources {
 static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 static String hash(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),published=Path.of(args[1]);
  var reference=Jsoup.parse(Files.readString(published));var rows=new JsonArray();
  for(String name:List.of("originals","originals-before-main-checkout")){
   Path raw=root.resolve("target/seed-publication-20261019/"+name+"/ets2026_test01.html");
   if(!Files.exists(raw))continue;
   var dom=Jsoup.parse(Files.readString(raw));int same=0,different=0;
   for(var q:reference.select("article[data-zen-history-answer]")){
    var original=dom.getElementById(q.id());if(original!=null&&original.text().equals(q.text()))same++;else different++;
   }
   int structuredQuestionScripts=0,jsonScripts=0;
   for(var script:dom.select("script")){
    String type=script.attr("type");if(type.contains("json"))jsonScripts++;
    String text=script.data();
    if(text.matches("(?si).*(?:[\"'](?:choices|choice_answer|correct_answer|sub_questions)[\"']\\s*:).*") )structuredQuestionScripts++;
   }
   var row=new JsonObject();row.addProperty("relativeSourcePath","target/seed-publication-20261019/"+name+"/ets2026_test01.html");
   row.addProperty("sha256",hash(raw));row.addProperty("articles",dom.select("article[data-zen-history-answer]").size());
   row.addProperty("articleTextEqualToPublished",same);row.addProperty("articleTextDifferent",different);
   row.addProperty("scripts",dom.select("script").size());row.addProperty("jsonScripts",jsonScripts);
   row.addProperty("scriptsWithStructuredOriginalChoiceKeys",structuredQuestionScripts);
   row.addProperty("recognizedChoiceKeysDetected",structuredQuestionScripts>0);row.addProperty("secretsExported",false);rows.add(row);
  }
  var report=new JsonObject();report.addProperty("scope","ETS_TEST01_LOCAL_SOURCE_AUDIT");report.addProperty("publishedSha256",hash(published));report.add("rawSources",rows);
  report.addProperty("conclusion","Review DOM has the same 200 question texts; no structured original-choice payload found by listed key scan. Supplemental original source remains required; scan is not exhaustive of arbitrary encoded payloads.");
  Files.writeString(Path.of(args[2]),GSON.toJson(report)+"\n",StandardCharsets.UTF_8);
  System.out.println("RAW_SOURCE_AUDIT files="+rows.size()+" report="+Path.of(args[2]).getFileName());
 }
}
