package vn.edu.toeic.client.exam.rich;

import java.util.*;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;
import vn.edu.toeic.protocol.exam.rich.*;

/**
 * Đề rich đã kiểm tra, gom theo Part và "đơn vị hiển thị" để renderer vẽ.
 *
 * <p>Part 1, 2, 5: mỗi câu là một đơn vị. Part 3, 4, 6, 7: mỗi group là một đơn vị, các câu trong
 * group dùng chung audio hoặc tài liệu. Thứ tự lấy từ orderIndex do server cấp, không sắp theo ID.
 * Dữ liệu sai cấu trúc bị từ chối ở {@link #from} trước khi mở màn thi (fail-closed).
 */
public final class RichExamModel {
    /** number là số câu TOEIC (orderIndex do server cấp, 1..200). */
    public record Question(int number, CandidateQuestion dto, CandidateGroup group, String audioFile) {
        public String id() { return dto.questionId(); }
        public int part() { return dto.part(); }
    }
    /** group null nghĩa là câu đứng riêng (Part 5) hoặc group chỉ chứa audio (Part 1/2). */
    public record Unit(int part, CandidateGroup group, List<Question> questions) {
        public Unit { questions = List.copyOf(questions); }
        public int firstNumber() { return questions.getFirst().number(); }
        public int lastNumber() { return questions.getLast().number(); }
    }
    public record PartInfo(int part, Section section, List<Unit> units) {
        public PartInfo { units = List.copyOf(units); }
        public int questionCount() { return units.stream().mapToInt(u -> u.questions().size()).sum(); }
    }

    private final RichCandidateExam exam;
    private final List<PartInfo> parts;
    private final List<Question> questions;
    private final Map<String, Question> byId;
    private final Map<String, CandidateMedia> media;

    private RichExamModel(RichCandidateExam exam, List<PartInfo> parts, List<Question> questions, Map<String, CandidateMedia> media) {
        this.exam = exam; this.parts = List.copyOf(parts); this.questions = List.copyOf(questions); this.media = Map.copyOf(media);
        Map<String, Question> index = new LinkedHashMap<>();
        for (Question q : questions) index.put(q.id(), q);
        this.byId = Collections.unmodifiableMap(index);
    }

    public static RichExamModel from(RichCandidateExam exam) {
        if (exam == null || exam.schemaVersion() != 2 || !"PROCTORED_V2".equals(exam.contractVersion()))
            throw invalid("Đề không phải ca PROCTORED_V2.");
        if (exam.startedAt() == null || exam.deadlineAt() == null || blank(exam.visualManifestId()))
            throw invalid("Đề chưa có mốc START do server chốt.");
        if (exam.questions() == null || exam.questions().isEmpty()) throw invalid("Đề không có câu hỏi.");

        Map<String, CandidateMedia> media = new HashMap<>();
        for (CandidateMedia m : exam.media() == null ? List.<CandidateMedia>of() : exam.media()) {
            if (m == null || blank(m.mediaId()) || m.mediaType() == null || media.put(m.mediaId(), m) != null)
                throw invalid("Media thiếu mã hoặc trùng mã.");
            if (m.mediaType() == MediaType.IMAGE && (blank(m.imagePath()) || m.widthPx() == null || m.heightPx() == null
                    || m.widthPx() < 1 || m.heightPx() < 1 || blank(m.sha256())))
                throw invalid("Ảnh " + m.mediaId() + " thiếu đường tải hoặc kích thước.");
            if (m.mediaType() == MediaType.AUDIO && blank(m.audioFileName()))
                throw invalid("Audio " + m.mediaId() + " thiếu tên file.");
        }
        Map<String, CandidateGroup> groups = new HashMap<>();
        for (CandidateGroup g : exam.groups() == null ? List.<CandidateGroup>of() : exam.groups()) {
            if (g == null || blank(g.groupId()) || groups.put(g.groupId(), g) != null) throw invalid("Group thiếu mã hoặc trùng mã.");
            checkSection(g.section(), g.part(), "Group " + g.groupId());
            blocks(g.blocks(), media, "Group " + g.groupId());
            Set<Integer> docOrders = new HashSet<>();
            for (DocumentContent d : g.documents() == null ? List.<DocumentContent>of() : g.documents()) {
                if (d == null || blank(d.documentId()) || !docOrders.add(d.orderIndex()))
                    throw invalid("Tài liệu của group " + g.groupId() + " thiếu mã hoặc trùng thứ tự.");
                blocks(d.blocks(), media, "Tài liệu " + d.documentId());
            }
        }

        Set<String> ids = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        List<Question> all = new ArrayList<>();
        for (CandidateQuestion q : exam.questions()) {
            if (q == null || blank(q.questionId()) || !ids.add(q.questionId())) throw invalid("Câu hỏi thiếu mã hoặc trùng mã.");
            if (q.orderIndex() < 1 || !numbers.add(q.orderIndex())) throw invalid("Câu " + q.questionId() + " trùng hoặc sai số thứ tự.");
            checkSection(q.section(), q.part(), "Câu " + q.questionId());
            blocks(q.blocks(), media, "Câu " + q.questionId());
            if (q.options() == null || q.options().size() < 2) throw invalid("Câu " + q.questionId() + " có ít hơn hai lựa chọn.");
            Set<String> options = new HashSet<>();
            for (OptionContent o : q.options())
                if (o == null || blank(o.optionId()) || !options.add(o.optionId())) throw invalid("Câu " + q.questionId() + " có lựa chọn trùng mã.");
            CandidateGroup group = null;
            if (!blank(q.groupId())) {
                group = groups.get(q.groupId());
                if (group == null || group.part() != q.part()) throw invalid("Câu " + q.questionId() + " trỏ tới group không hợp lệ.");
            }
            String audio = null;
            if (q.section() == Section.LISTENING) {
                List<String> found = new ArrayList<>();
                for (ContentBlock b : concat(group == null ? null : group.blocks(), q.blocks()))
                    if (b.type() == BlockType.AUDIO) found.add(media.get(b.mediaId()).audioFileName());
                if (found.size() != 1) throw invalid("Câu Listening " + q.questionId() + " phải có đúng một audio.");
                audio = found.getFirst();
            }
            all.add(new Question(q.orderIndex(), q, group, audio));
        }
        all.sort(Comparator.comparingInt(Question::number));

        // Duyệt câu theo số thứ tự. Mỗi đơn vị là một danh sách câu; câu cùng group (Part 3/4/6/7)
        // được thêm vào danh sách đã mở khi gặp câu đầu tiên của group đó.
        Map<Integer, List<List<Question>>> unitsByPart = new TreeMap<>();
        Map<String, List<Question>> openGroups = new HashMap<>();
        for (Question q : all) {
            boolean shared = q.part() == 3 || q.part() == 4 || q.part() == 6 || q.part() == 7;
            List<Question> unit = shared && q.group() != null ? openGroups.get(q.group().groupId()) : null;
            if (unit == null) {
                unit = new ArrayList<>();
                unitsByPart.computeIfAbsent(q.part(), k -> new ArrayList<>()).add(unit);
                if (shared && q.group() != null) openGroups.put(q.group().groupId(), unit);
            }
            unit.add(q);
        }
        List<PartInfo> parts = new ArrayList<>();
        for (var entry : unitsByPart.entrySet()) {
            List<Unit> units = new ArrayList<>();
            for (List<Question> members : entry.getValue()) units.add(new Unit(entry.getKey(), members.getFirst().group(), members));
            parts.add(new PartInfo(entry.getKey(), entry.getKey() <= 4 ? Section.LISTENING : Section.READING, units));
        }
        return new RichExamModel(exam, parts, all, media);
    }

    public RichCandidateExam exam() { return exam; }
    public List<PartInfo> parts() { return parts; }
    public List<Question> questions() { return questions; }
    public Question question(String id) { return byId.get(id); }
    public CandidateMedia media(String mediaId) { return media.get(mediaId); }
    public int totalQuestions() { return questions.size(); }

    public PartInfo part(int part) {
        for (PartInfo p : parts) if (p.part() == part) return p;
        return null;
    }

    /** Đơn vị chứa câu hỏi; dùng cho điều hướng "Chọn câu". */
    public Unit unitOf(String questionId) {
        for (PartInfo p : parts) for (Unit u : p.units()) for (Question q : u.questions()) if (q.id().equals(questionId)) return u;
        return null;
    }

    /** Đơn vị đầu tiên dùng file audio này; Listening tự chuyển màn theo đoạn đang phát. */
    public Unit unitForAudio(String audioFile) {
        for (PartInfo p : parts) for (Unit u : p.units()) if (Objects.equals(u.questions().getFirst().audioFile(), audioFile)) return u;
        return null;
    }

    /** Mã câu → mã lựa chọn hợp lệ, cho ExamSession từ chối ID ngoài đề. */
    public Map<String, Set<String>> allowedOptions() {
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        for (Question q : questions) {
            Set<String> ids = new LinkedHashSet<>();
            for (OptionContent o : q.dto().options()) ids.add(o.optionId());
            allowed.put(q.id(), Set.copyOf(ids));
        }
        return Map.copyOf(allowed);
    }

    public Set<String> listeningQuestionIds() {
        Set<String> ids = new HashSet<>();
        for (Question q : questions) if (q.dto().section() == Section.LISTENING) ids.add(q.id());
        return Set.copyOf(ids);
    }

    /**
     * Bridge contract §7.1: chuyển phần Listening sang CandidateExamDto mà ListeningPlayback đang dùng,
     * để vẫn chỉ có một player và một journal. Prompt lấy từ TEXT blocks; option không có text dùng nhãn.
     */
    public CandidateExamDto toListeningExam() {
        List<CandidateQuestionDto> listening = new ArrayList<>();
        for (Question q : questions) {
            if (q.dto().section() != Section.LISTENING) continue;
            List<CandidateOptionDto> options = new ArrayList<>();
            for (OptionContent o : q.dto().options())
                options.add(new CandidateOptionDto(o.optionId(), blank(o.text()) ? o.optionId() : o.text(), o.orderIndex()));
            String prompt = text(q.dto().blocks());
            listening.add(new CandidateQuestionDto(q.id(), "LISTENING", q.part(), q.dto().groupId(), null, q.audioFile(),
                    prompt.isBlank() ? "Câu " + q.number() : prompt, q.number(), options));
        }
        if (listening.isEmpty()) throw invalid("Đề không có câu Listening.");
        return new CandidateExamDto(exam.examId(), exam.title(), exam.description(), questions.size(), listening);
    }

    /** Nối các TEXT block theo thứ tự server cấp, mỗi block một dòng. */
    public static String text(List<ContentBlock> blocks) {
        StringBuilder out = new StringBuilder();
        for (ContentBlock b : ordered(blocks)) {
            if (b.type() != BlockType.TEXT || blank(b.text())) continue;
            if (!out.isEmpty()) out.append('\n');
            out.append(b.text());
        }
        return out.toString();
    }

    public static List<ContentBlock> ordered(List<ContentBlock> blocks) {
        if (blocks == null) return List.of();
        return blocks.stream().sorted(Comparator.comparingInt(ContentBlock::orderIndex)).toList();
    }

    public static List<DocumentContent> documents(CandidateGroup group) {
        if (group == null || group.documents() == null) return List.of();
        return group.documents().stream().sorted(Comparator.comparingInt(DocumentContent::orderIndex)).toList();
    }

    private static void blocks(List<ContentBlock> blocks, Map<String, CandidateMedia> media, String owner) {
        if (blocks == null) return;
        Set<Integer> orders = new HashSet<>();
        for (ContentBlock b : blocks) {
            if (b == null || b.type() == null || !orders.add(b.orderIndex())) throw invalid(owner + " có block thiếu loại hoặc trùng thứ tự.");
            if (b.type() == BlockType.TEXT) continue;
            CandidateMedia m = b.mediaId() == null ? null : media.get(b.mediaId());
            MediaType expected = b.type() == BlockType.IMAGE ? MediaType.IMAGE : MediaType.AUDIO;
            if (m == null || m.mediaType() != expected) throw invalid(owner + " tham chiếu media không có trong đề.");
        }
    }

    private static void checkSection(Section section, int part, String owner) {
        if (section == null || part < 1 || part > 7 || (section == Section.LISTENING) != (part <= 4))
            throw invalid(owner + " sai Section/Part.");
    }

    private static List<ContentBlock> concat(List<ContentBlock> a, List<ContentBlock> b) {
        List<ContentBlock> all = new ArrayList<>();
        if (a != null) all.addAll(a);
        if (b != null) all.addAll(b);
        return all;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
