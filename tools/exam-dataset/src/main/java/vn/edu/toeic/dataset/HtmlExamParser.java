package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import static vn.edu.toeic.dataset.Json.*;

/** Chỉ đọc DOM offline. Nội dung explanation được lưu riêng cho người review. */
public final class HtmlExamParser {
    static final int[] ENDS = {6, 31, 70, 100, 130, 146, 200};
    private final JsonArray media = new JsonArray();
    private final Map<String, String> mediaIds = new LinkedHashMap<>();
    private final Map<String, String> evidence = new LinkedHashMap<>();
    private int audioCount, imageCount;

    static int expectedPart(int n) {
        for (int p = 0; p < ENDS.length; p++) if (n > 0 && n <= ENDS[p]) return p + 1;
        return 0;
    }
    static String section(int part) { return part <= 4 ? "LISTENING" : "READING"; }
    static String clean(String text) {
        return text.lines().map(s -> s.replaceAll("[\\t \\u00a0]+", " ").strip())
                .filter(s -> !s.isEmpty()).reduce((a, b) -> a + "\n" + b).orElse("");
    }
    static String lines(Element e) {
        if (e == null) return "";
        Element copy = e.clone();
        copy.select("br").after("\n");
        copy.select("p,div,li,tr").after("\n");
        return clean(copy.wholeText());
    }
    static Element nearestGroup(Element article) {
        for (Element p = article.parent(); p != null; p = p.parent())
            if (p.hasAttr("data-zen-history-listening-group") || p.hasAttr("data-zen-history-reading-group")) return p;
        return null;
    }
    public JsonObject parse(Path root, String sourcePath) throws IOException {
        // Một parser instance dành cho một đề, tránh chia sẻ cache giữa các đề.
        if (!evidence.isEmpty()) throw new IllegalStateException("Use a fresh parser for each exam");
        Path file = root.resolve(sourcePath), manifestFile = root.resolve("data/seedingTest/manifest.json");
        JsonObject manifest = read(manifestFile), entry = null;
        for (var v : manifest.getAsJsonArray("files")) if (sourcePath.equals(str(v.getAsJsonObject(), "path"))) {
            if (entry != null) throw new IllegalArgumentException("Duplicate manifest entry");
            entry = v.getAsJsonObject();
        }
        if (entry == null) throw new IllegalArgumentException("Source absent from manifest: " + sourcePath);
        String hash = hash(file);
        Document dom = Jsoup.parse(Files.readString(file));
        String stem = file.getFileName().toString().replaceFirst("\\.html$", "");
        var permission = manifest.getAsJsonObject("distributionPermission");
        var dataset = obj("normalizedVersion", 1, "exam", obj("examId", stem.replace('_', '-'),
                "title", "ETS 2026 Test " + stem.replaceFirst(".*test", ""), "description", "Bản nháp từ HTML review; cần review nội dung.", "profile", "TOEIC_200"),
                "source", obj("path", sourcePath, "sha256", hash, "expectedSha256", str(entry, "sha256"),
                        "manifestSha256", hash(manifestFile), "sourceName", str(manifest, "source"),
                        "copyrightNotice", str(manifest, "sourceCopyright"), "permission", permission.deepCopy(),
                        "checksumMatches", hash.equals(str(entry, "sha256"))));
        evidence.put("/source/sha256", "SHA-256 đo trực tiếp từ bytes HTML; đối chiếu manifest.");
        evidence.put("/source/manifestSha256", "SHA-256 đo trực tiếp từ bytes manifest.");
        var groups = new JsonArray();
        var groupIds = new IdentityHashMap<Element, String>();
        for (Element group : dom.select("section[data-zen-history-listening-group],section[data-zen-history-reading-group]")) {
            var members = group.select("article[data-question-number]");
            if (members.isEmpty()) continue;
            int first = Integer.parseInt(members.first().attr("data-question-number"));
            int part = Integer.parseInt(members.first().attr("data-part-name").replaceFirst(".*?([1-7]).*", "$1"));
            String id = "g-" + group.attr("data-question-id");
            groupIds.put(group, id);
            JsonArray numbers = new JsonArray();
            members.forEach(a -> numbers.add(Integer.parseInt(a.attr("data-question-number"))));
            Element context = group.selectFirst(".zen-history-listening-context,.zen-history-reading-context");
            JsonArray blocks = blocks(context == null ? null : context.selectFirst(".zen-history-question-passage"), id, first);
            if (context != null) for (Element audio : context.select("audio")) addMedia(blocks, audio, id, first);
            groups.add(obj("groupId", id, "section", section(part), "part", part, "orderIndex", first,
                    "title", context == null ? null : context.select(".zen-history-question-title").text(),
                    "questionNumbers", numbers, "sourceLocator", "#" + group.id(), "mappingStatus", "NEEDS_REVIEW",
                    "passageBlocks", blocks, "documents", new JsonArray(), "documentBoundaryStatus", "NEEDS_REVIEW"));
        }
        var questions = new JsonArray();
        for (Element article : dom.select("article[data-zen-history-answer]")) {
            int n = Integer.parseInt(article.attr("data-question-number"));
            int part = Integer.parseInt(article.attr("data-part-name").replaceFirst(".*?([1-7]).*", "$1"));
            String id = "q%03d".formatted(n);
            Element parent = nearestGroup(article);
            String groupId = parent == null ? null : groupIds.get(parent);
            if (part <= 2) {
                groupId = "g%03d".formatted(n);
                var audio = new JsonArray();
                for (Element a : article.select(".zen-history-audio audio")) addMedia(audio, a, groupId, n);
                groups.add(obj("groupId", groupId, "section", section(part), "part", part, "orderIndex", n,
                        "title", article.select(".zen-history-question-title").text(), "questionNumbers", arr(n),
                        "sourceLocator", "#" + article.id(), "mappingStatus", "NEEDS_REVIEW", "passageBlocks", audio,
                        "documents", new JsonArray(), "documentBoundaryStatus", "EXTRACTED"));
            }
            Element promptNode = article.selectFirst(".zen-history-sub-question");
            String prompt = lines(promptNode);
            // Part1/2 chỉ lấy số câu từ DOM. Không copy transcript hoặc text explanation.
            String displayPrompt = part <= 2 ? n + "." : prompt;
            JsonArray content = new JsonArray();
            content.add(textBlock(id + "-prompt", 1, displayPrompt));
            Element questionContent = article.selectFirst(".zen-history-question-content");
            if (questionContent != null) {
                JsonArray passage = blocks(questionContent.selectFirst(".zen-history-question-passage"), id, n);
                for (var b : passage) { b.getAsJsonObject().addProperty("orderIndex", content.size() + 1); content.add(b); }
            }
            String answer = null, answerText = null;
            for (Element p : article.select(".zen-history-answer-details p")) {
                Element label = p.selectFirst("strong");
                if (label != null && label.text().strip().equals("Đáp án đúng:")) {
                    Element copy = p.clone(); copy.select("strong").remove(); answerText = clean(copy.text());
                    var match = Pattern.compile("^([A-D])\\.").matcher(answerText);
                    if (match.find()) answer = match.group(1);
                }
            }
            var options = new JsonArray();
            for (int i = 0; i < (part == 2 ? 3 : 4); i++) options.add(obj("optionId", "" + (char) ('A' + i), "orderIndex", i + 1, "text", null));
            var candidates = new JsonArray();
            String explanation = lines(article.selectFirst(".zen-history-explanation"));
            var pattern = Pattern.compile("(?m)^\\s*(?:\\(([A-D])\\)|([A-D])\\.)\\s*(.+)$");
            var match = pattern.matcher(explanation);
            while (match.find()) candidates.add(obj("label", match.group(1) == null ? match.group(2) : match.group(1),
                    "text", match.group(3), "origin", "REVIEW_EXPLANATION", "status", "REJECTED"));
            // Một số Part5 chỉ có danh sách gạch đầu dòng: không suy thứ tự A-D.
            if (candidates.isEmpty()) for (String line : explanation.split("\n"))
                if (line.matches("^[–-]\\s+.+")) candidates.add(obj("label", null, "text", line,
                        "origin", "UNLABELLED_EXPLANATION", "status", "REJECTED"));
            questions.add(obj("questionId", id, "sourceQuestionNumber", n, "orderIndex", n, "part", part,
                    "section", article.attr("data-part-type").toUpperCase(Locale.ROOT), "groupId", groupId,
                    "sourceLocator", "#" + article.id(), "sourceTitle", article.select(".zen-history-question-title").text(),
                    "prompt", displayPrompt, "blocks", content, "options", options,
                    "correctOption", answer, "sourceAnswerText", answerText, "answerKeyStatus", "EXTRACTED",
                    "optionContentStatus", part <= 2 ? "EXTRACTED" : "NEEDS_REVIEW", "reviewOptionCandidates", candidates));
        }
        List<JsonElement> sorted = new ArrayList<>(); groups.forEach(sorted::add);
        sorted.sort(Comparator.comparingInt(v -> num(v.getAsJsonObject(), "orderIndex")));
        groups = new JsonArray(); sorted.forEach(groups::add);
        dataset.add("groups", groups); dataset.add("questions", questions); dataset.add("media", media);
        JsonObject reviews = new JsonObject(); reviewFields(dataset, "", reviews);
        dataset.add("fieldReviews", reviews);
        return dataset;
    }
    private void reviewFields(JsonElement node, String path, JsonObject reviews) {
        if (node.isJsonObject()) for (var e : node.getAsJsonObject().entrySet()) reviewFields(e.getValue(), path + "/" + e.getKey(), reviews);
        else if (node.isJsonArray() && !node.getAsJsonArray().isEmpty()) {
            for (int i = 0; i < node.getAsJsonArray().size(); i++) reviewFields(node.getAsJsonArray().get(i), path + "/" + i, reviews);
        } else {
            String status = node.isJsonNull() || node.isJsonArray() ? "NEEDS_REVIEW" : "EXTRACTED";
            if (path.contains("reviewOptionCandidates")) status = "REJECTED";
            if (path.endsWith("/mappingStatus") || path.endsWith("/documents") || path.contains("/permission/") || path.endsWith("/optionContentStatus")) status = "NEEDS_REVIEW";
            if (evidence.containsKey(path)) status = "VERIFIED";
            reviews.add(path, obj("status", status, "evidence", evidence.getOrDefault(path,
                    "Trích từ DOM/manifest; selector nguồn nằm trong sourceLocator của bản ghi. Chưa đối chiếu nội dung gốc.")));
        }
    }
    static JsonObject textBlock(String id, int order, String text) {
        return obj("blockId", id, "type", "TEXT", "orderIndex", order, "text", text, "mediaId", null, "mediaType", null);
    }
    private JsonArray blocks(Element passage, String owner, int ordinal) {
        var result = new JsonArray();
        if (passage != null) walk(passage, new StringBuilder(), result, owner, ordinal);
        return result;
    }
    private void walk(Node node, StringBuilder buffer, JsonArray blocks, String owner, int ordinal) {
        if (node instanceof TextNode text) buffer.append(text.getWholeText());
        else if (node instanceof Element e && (e.tagName().equals("img") || e.tagName().equals("audio"))) {
            flush(buffer, blocks, owner); addMedia(blocks, e, owner, ordinal);
        } else {
            for (Node child : node.childNodes()) walk(child, buffer, blocks, owner, ordinal);
            if (node instanceof Element e && Set.of("br", "p", "div", "tr", "li").contains(e.tagName())) buffer.append('\n');
        }
        if (node.parent() == null || node instanceof Element e && e.hasClass("zen-history-question-passage")) flush(buffer, blocks, owner);
    }
    private void flush(StringBuilder buffer, JsonArray blocks, String owner) {
        String value = clean(buffer.toString()); buffer.setLength(0);
        if (!value.isBlank()) blocks.add(textBlock(owner + "-b" + (blocks.size() + 1), blocks.size() + 1, value));
    }
    private void addMedia(JsonArray blocks, Element element, String owner, int ordinal) {
        String type = element.tagName().equals("audio") ? "AUDIO" : "IMAGE";
        String url = element.attr("src");
        if (url.isBlank() && element.selectFirst("source[src]") != null) url = element.selectFirst("source[src]").attr("src");
        String key = type + ":" + url, id = mediaIds.get(key);
        if (id == null) {
            id = type.equals("AUDIO") ? "audio-%03d".formatted(++audioCount) : "image-%03d".formatted(++imageCount);
            mediaIds.put(key, id);
            String extension = url.replaceFirst("[?#].*$", "").replaceFirst(".*\\.", "").toLowerCase(Locale.ROOT);
            if (!Set.of("mp3", "m4a", "wav", "png", "jpg", "jpeg").contains(extension)) extension = "unknown";
            String filename = (type.equals("AUDIO") ? "%03d-".formatted(ordinal) : "") + id + "." + extension;
            media.add(obj("mediaId", id, "mediaType", type, "sourceUrl", url, "localFile", filename,
                    "storageStatus", "REMOTE_REFERENCE", "mimeType", null, "sizeBytes", null, "sha256", null,
                    "widthPx", null, "heightPx", null, "durationMs", null, "verificationStatus", "NEEDS_REVIEW",
                    "altText", null, "sourceDeclaredWidth", element.hasAttr("width") ? element.attr("width") : null,
                    "sourceDeclaredHeight", element.hasAttr("height") ? element.attr("height") : null));
        }
        blocks.add(obj("blockId", owner + "-b" + (blocks.size() + 1), "type", type,
                "orderIndex", blocks.size() + 1, "text", null, "mediaId", id, "mediaType", type));
    }
}
