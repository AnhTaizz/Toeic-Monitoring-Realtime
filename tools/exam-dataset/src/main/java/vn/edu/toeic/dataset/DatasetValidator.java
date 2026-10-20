package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static vn.edu.toeic.dataset.Json.*;

/** Kết quả cấu trúc tách khỏi điều kiện xác minh nội dung; không tự nâng trạng thái. */
public final class DatasetValidator {
    public JsonObject validate(JsonObject d) {
        var findings = new JsonArray();
        var questions = d.getAsJsonArray("questions");
        var groups = d.getAsJsonArray("groups");
        var media = d.getAsJsonArray("media");
        var source = d.getAsJsonObject("source");
        check(findings, str(source, "sha256").equals(str(source, "expectedSha256")), "SOURCE_CHECKSUM", "/source", "Checksum HTML phải khớp manifest.");
        Map<String, JsonObject> gm = unique(groups, "groupId", findings, "/groups");
        Map<String, JsonObject> mm = unique(media, "mediaId", findings, "/media");
        unique(questions, "questionId", findings, "/questions");
        int[] counts = new int[7];
        Set<Integer> numbers = new HashSet<>();
        Map<String, List<Integer>> members = new HashMap<>();
        Set<String> usedMedia = new HashSet<>(), blockIds = new HashSet<>(), audioOwners = new HashSet<>();
        check(findings, questions.size() == 200, "QUESTION_COUNT", "/questions", "Cần đúng 200 câu.");
        int options = 0, missingOptionText = 0;
        for (int i = 0; i < questions.size(); i++) {
            JsonObject q = questions.get(i).getAsJsonObject();
            String path = "/questions/" + i;
            int number = num(q, "sourceQuestionNumber"), part = num(q, "part");
            if (part >= 1 && part <= 7) counts[part - 1]++;
            check(findings, numbers.add(number), "DUPLICATE_QUESTION", path, "Số câu bị trùng.");
            check(findings, number == i + 1 && num(q, "orderIndex") == i + 1 && part == HtmlExamParser.expectedPart(i + 1)
                    && HtmlExamParser.section(part).equals(str(q, "section")), "PART_ORDER", path, "Sai thứ tự/số câu/Part/section.");
            JsonArray os = q.getAsJsonArray("options"); options += os.size();
            check(findings, os.size() == (part == 2 ? 3 : 4), "OPTION_COUNT", path, "Thiếu/thừa option slots.");
            Set<String> labels = new HashSet<>();
            for (int j = 0; j < os.size(); j++) {
                var o = os.get(j).getAsJsonObject(); labels.add(str(o, "optionId"));
                check(findings, ("" + (char) ('A' + j)).equals(str(o, "optionId")) && num(o, "orderIndex") == j + 1,
                        "OPTION_ORDER", path, "Sai nhãn/thứ tự options.");
                if (part <= 2) check(findings, str(o, "text") == null, "TRANSCRIPT_LEAK", path, "Part1/2 chỉ dùng nhãn, không transcript.");
                else if (str(o, "text") == null || str(o, "text").isBlank()) missingOptionText++;
            }
            if (part <= 2) check(findings, (number + ".").equals(str(q, "prompt")), "TRANSCRIPT_LEAK", path, "Prompt Part1/2 phải là số câu nguồn.");
            check(findings, labels.contains(str(q, "correctOption")), "ANSWER_OUTSIDE_OPTIONS", path, "Đáp án nguồn thiếu hoặc ngoài options.");
            if (!"VERIFIED".equals(str(q, "answerKeyStatus"))) finding(findings, "BLOCKED", "ANSWER_UNVERIFIED", path, "Cần đối chiếu đáp án với nguồn gốc.");
            if (part > 2 && !"VERIFIED".equals(str(q, "optionContentStatus"))) finding(findings, "BLOCKED", "OPTIONS_UNVERIFIED", path,
                    "Bổ sung đủ options gốc; reviewOptionCandidates từ explanation không được dùng làm options.");
            String gid = str(q, "groupId");
            if (part == 5) check(findings, gid == null, "GROUP_MAPPING", path, "Part5 phải độc lập.");
            else {
                var g = gm.get(gid);
                check(findings, g != null && num(g, "part") == part && str(q, "section").equals(str(g, "section")), "GROUP_MAPPING", path, "Thiếu group hoặc sai Part/section.");
                members.computeIfAbsent(gid, ignored -> new ArrayList<>()).add(number);
            }
            inspectBlocks(q.getAsJsonArray("blocks"), path, mm, usedMedia, blockIds, findings, false);
            if (part <= 2) for (var b : q.getAsJsonArray("blocks")) if ("TEXT".equals(str(b.getAsJsonObject(), "type")))
                check(findings, (number + ".").equals(str(b.getAsJsonObject(), "text")), "TRANSCRIPT_LEAK", path, "Part1/2 TEXT không được chứa transcript.");
            check(findings, q.getAsJsonArray("blocks").asList().stream().anyMatch(b -> "TEXT".equals(str(b.getAsJsonObject(), "type"))), "PROMPT_MISSING", path, "Cần prompt TEXT nguồn.");
            if (part == 1) check(findings, q.getAsJsonArray("blocks").asList().stream().filter(b -> "IMAGE".equals(str(b.getAsJsonObject(), "type"))).count() == 1,
                    "PART1_IMAGE", path, "Mỗi câu Part1 cần một ảnh.");
        }
        for (int n = 1; n <= 200; n++) if (!numbers.contains(n)) finding(findings, "ERROR", "MISSING_QUESTION", "/questions", "Thiếu câu " + n);
        int previous = 0;
        for (int i = 0; i < groups.size(); i++) {
            var g = groups.get(i).getAsJsonObject(); String path = "/groups/" + i, id = str(g, "groupId");
            var ns = members.getOrDefault(id, List.of()); int part = num(g, "part");
            int expected = switch (part) { case 1, 2 -> 1; case 3, 4 -> 3; case 6 -> 4; default -> ns.size(); };
            boolean valid = !ns.isEmpty() && ns.size() == expected && ns.size() <= 5;
            for (int j = 1; j < ns.size(); j++) valid &= ns.get(j) == ns.get(j - 1) + 1;
            var declared = g.getAsJsonArray("questionNumbers");
            valid &= ns.size() == declared.size();
            for (int j = 0; j < Math.min(ns.size(), declared.size()); j++) valid &= ns.get(j) == declared.get(j).getAsInt();
            check(findings, valid, "GROUP_MEMBERS", path, "Group phải liên tiếp, đúng số lượng, khớp DOM membership.");
            check(findings, !ns.isEmpty() && num(g, "orderIndex") == ns.getFirst() && num(g, "orderIndex") > previous, "GROUP_ORDER", path, "Sai thứ tự group.");
            previous = num(g, "orderIndex");
            if (!"VERIFIED".equals(str(g, "mappingStatus"))) finding(findings, "BLOCKED", "GROUP_UNVERIFIED", path, "Cần review mapping group từ DOM với đề gốc.");
            JsonArray blocks = g.getAsJsonArray("passageBlocks");
            if (part <= 4) {
                inspectBlocks(blocks, path, mm, usedMedia, blockIds, findings, true);
                check(findings, g.getAsJsonArray("documents").isEmpty(), "DOCUMENT_OWNER", path, "Listening không có documents.");
                List<JsonObject> audios = blocks.asList().stream().map(JsonElement::getAsJsonObject).filter(b -> "AUDIO".equals(str(b, "type"))).toList();
                check(findings, audios.size() == 1, "SHARED_AUDIO", path, "Listening group cần đúng một AUDIO dùng chung.");
                for (var b : audios) {
                    String mid = str(b, "mediaId");
                    check(findings, audioOwners.add(mid), "SHARED_AUDIO", path, "Audio không được dùng bởi nhiều group.");
                    var m = mm.get(mid);
                    check(findings, m != null && str(m, "localFile") != null && str(m, "localFile").startsWith("%03d-".formatted(num(g, "orderIndex"))),
                            "AUDIO_FILENAME", path, "Tên audio phải bắt đầu ordinal của group.");
                }
            } else {
                var documents = g.getAsJsonArray("documents");
                if (documents.isEmpty()) inspectBlocks(blocks, path, mm, usedMedia, blockIds, findings, false);
                else {
                    check(findings, documents.size() <= 3 && (part != 6 || documents.size() == 1), "DOCUMENT_COUNT", path, "Part6 cần một document, Part7 một đến ba.");
                    Set<String> documentIds = new HashSet<>(); int blockOrder = 1;
                    for (int j = 0; j < documents.size(); j++) {
                        var doc = documents.get(j).getAsJsonObject();
                        check(findings, documentIds.add(str(doc, "documentId")) && num(doc, "orderIndex") == j + 1, "DOCUMENT_ORDER", path, "Document ID/order trùng hoặc sai.");
                        var db = doc.getAsJsonArray("blocks");
                        inspectBlocksAt(db, path, mm, usedMedia, blockIds, findings, false, blockOrder); blockOrder += db.size();
                        check(findings, db.asList().stream().anyMatch(b -> "TEXT".equals(str(b.getAsJsonObject(), "type"))), "DOCUMENT_TEXT", path, "Document cần TEXT gốc, không bịa mô tả để thay OCR.");
                    }
                }
                // Ảnh passage không chứng minh boundary tài liệu. Không tự suy một ảnh = một document.
                if (!"VERIFIED".equals(str(g, "documentBoundaryStatus")) || g.getAsJsonArray("documents").isEmpty())
                    finding(findings, "BLOCKED", "DOCUMENT_BOUNDARIES", path,
                            "Cần xác nhận documentId/order/title và TEXT gốc: Part6 một document, Part7 một đến ba; ảnh hiện có chỉ là passageBlocks.");
            }
        }
        Set<String> filenames = new HashSet<>();
        for (int i = 0; i < media.size(); i++) {
            var m = media.get(i).getAsJsonObject(); String path = "/media/" + i;
            String filename = str(m, "localFile"), url = str(m, "sourceUrl");
            check(findings, safeFile(filename) && filenames.add(Objects.toString(filename, "").toLowerCase(Locale.ROOT)), "MEDIA_FILENAME", path, "Tên media không an toàn/trùng.");
            check(findings, url != null && url.matches("https?://[^/]+/.+"), "MEDIA_SOURCE", path, "Thiếu URL nguồn media hợp lệ.");
            check(findings, usedMedia.contains(str(m, "mediaId")), "ORPHAN_MEDIA", path, "Media không được tham chiếu.");
            if (!"LOCAL_VERIFIED".equals(str(m, "storageStatus")) || !"VERIFIED".equals(str(m, "verificationStatus")))
                finding(findings, "BLOCKED", "MEDIA_UNVERIFIED", path, "Cần file local được phép dùng, hash/size/MIME và kiểm decode/playback thực tế.");
        }
        if (missingOptionText > 0) finding(findings, "BLOCKED", "MISSING_OPTION_TEXT", "/questions", "Thiếu " + missingOptionText + " text options gốc của Part3–7.");
        JsonObject permission = source.getAsJsonObject("permission");
        if (!Set.of("USER_CONFIRMED", "DOCUMENTED").contains(Objects.toString(str(permission, "status"), "")))
            finding(findings, "BLOCKED", "PERMISSION", "/source/permission", "Cần quyền sử dụng phù hợp.");
        if (!"VERIFIED".equals(str(source, "contentReviewStatus"))) finding(findings, "BLOCKED", "SOURCE_REVIEW", "/source", "Cần bằng chứng review keys/options/groups/media; xác nhận quyền HTML không thay quyền tải media.");
        long errors = findings.asList().stream().filter(v -> "ERROR".equals(str(v.getAsJsonObject(), "severity"))).count();
        var codeCounts = new TreeMap<String, Integer>();
        for (var f : findings) codeCounts.merge(str(f.getAsJsonObject(), "code"), 1, Integer::sum);
        return obj("status", findings.isEmpty() ? "READY_FOR_IMPORT_VALIDATION" : "BLOCKED", "importable", findings.isEmpty(),
                "summary", obj("questions", questions.size(), "partCounts", counts, "optionSlots", options, "missingOriginalOptionTexts", missingOptionText,
                        "groups", groups.size(), "audio", media.asList().stream().filter(m -> "AUDIO".equals(str(m.getAsJsonObject(), "mediaType"))).count(),
                        "images", media.asList().stream().filter(m -> "IMAGE".equals(str(m.getAsJsonObject(), "mediaType"))).count(), "structuralErrors", errors),
                "findingCounts", codeCounts, "findings", findings);
    }
    public JsonObject validate(JsonObject d, Path root) throws IOException {
        JsonObject report = validate(d); JsonObject source = d.getAsJsonObject("source");
        Path input = root.resolve(str(source, "path"));
        if (!Files.isRegularFile(input) || !hash(input).equals(str(source, "sha256"))
                || !hash(root.resolve("data/seedingTest/manifest.json")).equals(str(source, "manifestSha256"))) {
            finding(report.getAsJsonArray("findings"), "ERROR", "SOURCE_BYTES_CHANGED", "/source", "Bytes nguồn hoặc manifest đã thay đổi sau parse.");
            report.addProperty("status", "BLOCKED"); report.addProperty("importable", false);
        }
        return report;
    }
    private Map<String, JsonObject> unique(JsonArray list, String key, JsonArray findings, String path) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (var v : list) { var o = v.getAsJsonObject(); check(findings, result.put(str(o, key), o) == null, "DUPLICATE_ID", path, "ID bị trùng: " + str(o, key)); }
        return result;
    }
    private void inspectBlocks(JsonArray blocks, String path, Map<String, JsonObject> media, Set<String> used, Set<String> ids, JsonArray findings, boolean audioAllowed) {
        inspectBlocksAt(blocks, path, media, used, ids, findings, audioAllowed, 1);
    }
    private void inspectBlocksAt(JsonArray blocks, String path, Map<String, JsonObject> media, Set<String> used, Set<String> ids, JsonArray findings, boolean audioAllowed, int startOrder) {
        for (int i = 0; i < blocks.size(); i++) {
            var b = blocks.get(i).getAsJsonObject(); String type = str(b, "type");
            check(findings, ids.add(str(b, "blockId")) && num(b, "orderIndex") == i + startOrder, "BLOCK_ORDER", path, "Block ID/order không hợp lệ.");
            if ("TEXT".equals(type)) check(findings, str(b, "text") != null && !str(b, "text").isBlank() && str(b, "mediaId") == null && str(b, "mediaType") == null,
                    "BLOCK_TEXT", path, "TEXT thiếu nội dung hoặc có media.");
            else {
                String mid = str(b, "mediaId"); var m = media.get(mid); used.add(mid);
                check(findings, Set.of("AUDIO", "IMAGE").contains(type) && m != null && type.equals(str(m, "mediaType")) && type.equals(str(b, "mediaType")) && str(b, "text") == null,
                        "BLOCK_MEDIA", path, "Sai reference/discriminator media.");
                check(findings, audioAllowed || !"AUDIO".equals(type), "AUDIO_OWNER", path, "Audio chỉ thuộc group Listening.");
            }
        }
    }
    private static void check(JsonArray fs, boolean ok, String code, String path, String message) { if (!ok) finding(fs, "ERROR", code, path, message); }
    private static void finding(JsonArray fs, String severity, String code, String path, String message) { fs.add(obj("severity", severity, "code", code, "path", path, "message", message)); }
}
