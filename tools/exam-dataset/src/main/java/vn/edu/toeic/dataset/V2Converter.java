package vn.edu.toeic.dataset;

import com.google.gson.*;
import static vn.edu.toeic.dataset.Json.*;

/** Projection đúng các field DTO EX03; trạng thái review nằm trong sidecar, không nhét vào request. */
public final class V2Converter {
    public JsonObject draft(JsonObject d) {
        var source = d.getAsJsonObject("source"); var permission = source.getAsJsonObject("permission");
        JsonObject s = obj("sourceRef", "html-source", "sourceName", str(source, "sourceName"), "sourcePath", str(source, "path"),
                "sourceUrl", null, "copyrightNotice", str(source, "copyrightNotice"), "permissionStatus", str(permission, "status"),
                "permissionConfirmedOn", str(permission, "date"), "permissionNote", str(permission, "statement"),
                "licenseDocument", "NOT PROVIDED".equals(str(permission, "licenseDocument")) ? null : str(permission, "licenseDocument"),
                "sourceSha256", str(source, "sha256"), "manifestSha256", str(source, "manifestSha256"),
                "answerKeyStatus", "UNVERIFIED", "groupMappingStatus", "UNVERIFIED", "optionContentStatus", "UNVERIFIED", "mediaValidationStatus", "UNVERIFIED");
        if (source.has("mediaPermission") && source.get("mediaPermission").isJsonObject()) {
            var permissionMedia = source.getAsJsonObject("mediaPermission");
            s.addProperty("permissionNote", str(permission, "statement") + " Media: " + str(permissionMedia, "statement") + "; confirmedOn=" + str(permissionMedia, "confirmedOn"));
        }
        if (!d.getAsJsonArray("media").isEmpty() && d.getAsJsonArray("media").asList().stream().allMatch(v -> {
            var m = v.getAsJsonObject(); return "LOCAL_VERIFIED".equals(str(m, "storageStatus")) && "VERIFIED".equals(str(m, "verificationStatus"));
        })) s.addProperty("mediaValidationStatus", "VERIFIED");
        var groups = new JsonArray();
        for (var value : d.getAsJsonArray("groups")) {
            var g = value.getAsJsonObject(); boolean listening = num(g, "part") <= 4;
            groups.add(obj("groupId", str(g, "groupId"), "section", str(g, "section"), "part", num(g, "part"), "title", str(g, "title"),
                    "orderIndex", num(g, "orderIndex"), "mappingStatus", "UNVERIFIED", "documents", g.getAsJsonArray("documents").deepCopy(),
                    "blocks", listening ? g.getAsJsonArray("passageBlocks").deepCopy() : new JsonArray()));
        }
        var questions = new JsonArray();
        for (var value : d.getAsJsonArray("questions")) {
            var q = value.getAsJsonObject(); var options = q.getAsJsonArray("options").deepCopy();
            // Luôn loại transcript khỏi options Part1/2, kể cả khi normalized bị sửa tay.
            if (num(q, "part") <= 2) for (var option : options) option.getAsJsonObject().add("text", JsonNull.INSTANCE);
            questions.add(obj("questionId", str(q, "questionId"), "section", str(q, "section"), "part", num(q, "part"),
                    "orderIndex", num(q, "orderIndex"), "groupId", str(q, "groupId"), "sourceRef", "html-source",
                    "sourceQuestionNumber", num(q, "sourceQuestionNumber"), "answerKeyStatus", "UNVERIFIED",
                    "blocks", q.getAsJsonArray("blocks").deepCopy(), "options", options, "scoring", obj("correctOption", str(q, "correctOption"))));
        }
        var media = new JsonArray();
        for (var value : d.getAsJsonArray("media")) {
            var m = value.getAsJsonObject(); JsonObject item = obj("sourceRef", "html-source");
            for (String field : new String[]{"mediaId", "mediaType", "sourceUrl", "storageStatus", "localFile", "mimeType", "sha256", "widthPx", "heightPx", "durationMs", "altText"})
                item.add(field, m.get(field).deepCopy());
            item.addProperty("sizeBytes", m.get("sizeBytes").isJsonNull() ? 0 : m.get("sizeBytes").getAsLong());
            media.add(item);
        }
        return obj("schemaVersion", 2, "exam", d.getAsJsonObject("exam").deepCopy(), "sources", arr(s), "groups", groups, "media", media, "questions", questions);
    }
}
