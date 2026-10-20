package vn.edu.toeic.server.exam.rich;

import java.io.*;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import vn.edu.toeic.protocol.exam.rich.*;

/** Graph allowlist do server tính; không chứa prompt, option, alt text hay provenance. */
@Component
public final class PartOneVisualManifest {
    private final JdbcClient jdbc;
    public PartOneVisualManifest(JdbcClient jdbc) { this.jdbc = jdbc; }

    public VisualManifest build(RichImageSessionAuthority.Context context) {
        var slots = jdbc.sql("""
                SELECT q.order_index, m.media_id, m.local_file, m.mime_type, m.size_bytes,
                       m.sha256, m.width_px, m.height_px
                FROM exam_questions q JOIN exam_content_blocks b
                  ON b.exam_id=q.exam_id AND b.question_id=q.question_id AND b.block_type='IMAGE'
                JOIN exam_media_assets m ON m.exam_id=b.exam_id AND m.media_id=b.media_id
                  AND m.media_type='IMAGE' AND m.storage_status='LOCAL_VERIFIED'
                WHERE q.exam_id=:exam AND q.section='LISTENING' AND q.part=1
                ORDER BY q.order_index, b.order_index
                """).param("exam", context.examId()).query((rs, row) -> {
                    var media = new MediaImport(rs.getString("media_id"), MediaType.IMAGE, null, null,
                            StorageStatus.LOCAL_VERIFIED, rs.getString("local_file"), rs.getString("mime_type"),
                            rs.getLong("size_bytes"), rs.getString("sha256"), (Integer)rs.getObject("width_px"),
                            (Integer)rs.getObject("height_px"), null, null);
                    RichMediaValidator.validateImageMetadata(media);
                    int position = rs.getInt("order_index");
                    return new VisualImageSlot("p1-" + position, position, media.mediaId(), null,
                            media.sha256(), media.sizeBytes(), media.mimeType(), media.widthPx(), media.heightPx());
                }).list();
        return create(context, slots);
    }

    static VisualManifest create(RichImageSessionAuthority.Context c, List<VisualImageSlot> input) {
        if (c.writerEpoch() < 1 || c.preparationEpoch() == null || c.preparationEpoch().isBlank()
                || input.size() != 6) throw RichImageService.invalidState();
        List<VisualImageSlot> slots = input.stream().sorted(Comparator.comparingInt(VisualImageSlot::position)).toList();
        for (int i=0;i<6;i++) if (slots.get(i).position()!=i+1 || !slots.get(i).slotId().equals("p1-"+(i+1)))
            throw RichImageService.invalidState();
        String id = hash(c, slots);
        var linked = slots.stream().map(s -> new VisualImageSlot(s.slotId(),s.position(),s.mediaId(),
                "/api/v2/attempts/"+c.attemptId()+"/images/"+s.mediaId()+"?visualManifestId="+id,
                s.sha256(),s.sizeBytes(),s.mimeType(),s.widthPx(),s.heightPx())).toList();
        return new VisualManifest(2,id,c.attemptId(),c.sessionId(),c.examId(),2,c.writerEpoch(),c.preparationEpoch(),linked);
    }

    private static String hash(RichImageSessionAuthority.Context c,List<VisualImageSlot> slots) {
        try {
            var bytes = new ByteArrayOutputStream();
            try(var out = new DataOutputStream(bytes)) {
                out.writeUTF("EX02-P1-V1");out.writeUTF(c.examId());out.writeUTF(c.sessionId());out.writeUTF(c.attemptId());
                out.writeInt(2);out.writeLong(c.writerEpoch());out.writeUTF(c.preparationEpoch());out.writeInt(slots.size());
                for(var s:slots) {
                    out.writeUTF(s.slotId());out.writeInt(s.position());out.writeUTF(s.mediaId());out.writeLong(s.sizeBytes());
                    out.writeUTF(s.sha256());out.writeUTF(s.mimeType());out.writeInt(s.widthPx());out.writeInt(s.heightPx());
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
