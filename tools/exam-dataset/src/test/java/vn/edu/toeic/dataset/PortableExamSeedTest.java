package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.toeic.dataset.Json.*;

/** MOCK serialization fixtures. Real HTTP/PG import is exercised by verify-portable-seed.ps1. */
class PortableExamSeedTest {
 @TempDir Path temp;
 JsonObject row(String table){var r=new JsonObject();for(String c:PortableExamSeed.COLUMNS.get(table))r.add(c,JsonNull.INSTANCE);r.addProperty("exam_id","mock-seed");return r;}
 JsonObject fixture(){
  var tables=new JsonObject();for(String t:PortableExamSeed.COLUMNS.keySet())tables.add(t,new JsonArray());
  var e=row("exams");e.addProperty("title","MOCK O'Brien; SELECT 'literal', \\path\nnext line");e.addProperty("content_version",2);e.addProperty("total_questions",200);tables.getAsJsonArray("exams").add(e);
  var s=row("exam_sources");for(String c:List.of("answer_key_status","option_content_status","group_mapping_status","media_validation_status"))s.addProperty(c,"VERIFIED");s.addProperty("source_sha256","a".repeat(64));s.addProperty("source_path","original-html-does-not-exist.html");tables.getAsJsonArray("exam_sources").add(s);
  for(int i=1;i<=200;i++){var q=row("exam_questions");q.addProperty("question_id","MOCK-"+i);q.addProperty("answer_key_status","VERIFIED");q.addProperty("source_sha256","a".repeat(64));tables.getAsJsonArray("exam_questions").add(q);}
  return obj("examId","mock-seed","validation","V2_HTTP_201_AND_SELECTABLE","backendCommit","MOCK","backendJarSha256","a".repeat(64),"schemaColumns",arr(obj("MOCK","schema")),"tables",tables);
 }
 @Test void onlySevenTablesAndNoAccountIdentityInSql(){var sql=PortableExamSeed.sql(fixture());assertTrue(sql.contains("current_setting('toeic.seed.proctor_id')::bigint"));assertFalse(sql.contains("INSERT INTO public.user_accounts"));assertFalse(sql.contains("login_sessions"));assertFalse(sql.contains("flyway_schema_history"));assertTrue(sql.contains("SELECT id FROM public.exam_sources WHERE exam_id='mock-seed' AND source_sha256="));assertFalse(sql.contains("ON CONFLICT"));}
 @Test void quoteLiteralPreservesUnicodeApostropheBackslashAndNewline(){assertEquals("'O''Brien; \\path\nTiếng Việt'",PortableExamSeed.literal(new JsonPrimitive("O'Brien; \\path\nTiếng Việt")));assertThrows(IllegalArgumentException.class,()->PortableExamSeed.literal(new JsonPrimitive(1.5)));assertTrue(PortableExamSeed.sql(fixture()).contains("MOCK O''Brien; SELECT ''literal''"));}
 @Test void refusesForeignExamAndUnexpectedTableOrColumn(){var d=fixture();d.getAsJsonObject("tables").getAsJsonArray("exam_questions").get(0).getAsJsonObject().addProperty("exam_id","foreign");assertThrows(IllegalArgumentException.class,()->PortableExamSeed.sql(d));var extra=fixture();extra.getAsJsonObject("tables").add("user_accounts",new JsonArray());assertThrows(IllegalArgumentException.class,()->PortableExamSeed.sql(extra));var column=fixture();column.getAsJsonObject("tables").getAsJsonArray("exams").get(0).getAsJsonObject().addProperty("created_by",123);assertThrows(IllegalArgumentException.class,()->PortableExamSeed.sql(column));}
 @Test void unverifiedContentAndMissingBackendReceiptCannotExport(){var receipt=fixture();receipt.addProperty("validation","UNVERIFIED");assertThrows(IllegalArgumentException.class,()->PortableExamSeed.sql(receipt));var d=fixture();d.getAsJsonObject("tables").getAsJsonArray("exam_sources").get(0).getAsJsonObject().addProperty("option_content_status","UNVERIFIED");var bad=d;assertThrows(IllegalArgumentException.class,()->PortableExamSeed.sql(bad));}
 Path createPack()throws Exception {
  var d=fixture();var m=row("exam_media_assets");m.addProperty("media_type","AUDIO");m.addProperty("media_id","mock-audio");m.addProperty("local_file","mock.wav");m.addProperty("storage_status","LOCAL_VERIFIED");
  Path audio=temp.resolve("input-audio/mock-seed/mock.wav");Files.createDirectories(audio.getParent());Files.writeString(audio,"MOCK archive bytes, not a decoded media fixture");m.addProperty("size_bytes",Files.size(audio));m.addProperty("sha256",hash(audio));d.getAsJsonObject("tables").getAsJsonArray("exam_media_assets").add(m);
  Path snapshot=temp.resolve("db-snapshot.json");write(snapshot,d);Path scripts=temp.resolve("scripts");Files.createDirectory(scripts);for(String name:List.of("restore-exam-seed.ps1","exam-seed-common.ps1"))Files.writeString(scripts.resolve(name),"# MOCK runtime for file-integrity test only\n");
  Path pack=temp.resolve("pack");new PortableExamSeed().create(snapshot,temp.resolve("input-audio"),temp.resolve("input-images"),scripts,pack);return pack;
 }
 @Test void selfContainedPackDoesNotReadOriginalHtml()throws Exception {Path pack=createPack();assertTrue(Files.exists(pack.resolve("seed.sql")));assertTrue(Files.exists(pack.resolve("restore-exam-seed.ps1")));assertFalse(Files.exists(temp.resolve("original-html-does-not-exist.html")));assertEquals(1,num(new PortableExamSeed().verify(pack),"mediaCount"));}
 @Test void refusesMediaTampering()throws Exception {Path pack=createPack();Files.writeString(pack.resolve("audio-resources/mock-seed/mock.wav"),"MOCK corruption");assertThrows(IOException.class,()->new PortableExamSeed().verify(pack));}
 @Test void refusesSqlNotDerivedFromDbDataEvenIfHashIsUpdated()throws Exception {Path pack=createPack();Files.writeString(pack.resolve("seed.sql"),"MOCK arbitrary SQL");var m=read(pack.resolve("seed-manifest.json"));for(var v:m.getAsJsonArray("files")){var e=v.getAsJsonObject();if("seed.sql".equals(str(e,"path"))){e.addProperty("sizeBytes",Files.size(pack.resolve("seed.sql")));e.addProperty("sha256",hash(pack.resolve("seed.sql")));}}write(pack.resolve("seed-manifest.json"),m);Files.writeString(pack.resolve("seed-manifest.sha256"),hash(pack.resolve("seed-manifest.json"))+"\n");assertThrows(IOException.class,()->new PortableExamSeed().verify(pack));}
 @Test void refusesMediaThatDisagreesWithValidatedDbEvenAfterRehash()throws Exception {
  Path pack=createPack();var d=read(pack.resolve("exam-data.json"));d.getAsJsonObject("tables").getAsJsonArray("exam_media_assets").get(0).getAsJsonObject().addProperty("sha256","b".repeat(64));write(pack.resolve("exam-data.json"),d);Files.writeString(pack.resolve("seed.sql"),PortableExamSeed.sql(d));
  var m=read(pack.resolve("seed-manifest.json"));for(var v:m.getAsJsonArray("files")){var e=v.getAsJsonObject();Path f=pack.resolve(str(e,"path"));e.addProperty("sizeBytes",Files.size(f));e.addProperty("sha256",hash(f));}write(pack.resolve("seed-manifest.json"),m);Files.writeString(pack.resolve("seed-manifest.sha256"),hash(pack.resolve("seed-manifest.json"))+"\n");
  assertThrows(IOException.class,()->new PortableExamSeed().verify(pack));
 }
 @Test void rejectsOverwriteWorktreeAndTraversal()throws Exception {Path p=temp.resolve("repo");Files.createDirectories(p.resolve(".git"));assertThrows(IOException.class,()->PortableExamSeed.externalNew(p.resolve("output")));assertThrows(IOException.class,()->PortableExamSeed.externalNew(p));for(String s:List.of("../escape","/abs","a\\b","a//b","a/../b","C:/path"))assertThrows(IOException.class,()->PortableExamSeed.safeResolve(temp,s));}
 @Test void rejectsExtraFilesAndCorruptManifest()throws Exception {Path pack=createPack();Files.writeString(pack.resolve("source.html"),"MOCK unwanted source HTML");assertThrows(IOException.class,()->new PortableExamSeed().verify(pack));Files.delete(pack.resolve("source.html"));Files.writeString(pack.resolve("seed-manifest.sha256"),"0".repeat(64));assertThrows(IOException.class,()->new PortableExamSeed().verify(pack));}
}
