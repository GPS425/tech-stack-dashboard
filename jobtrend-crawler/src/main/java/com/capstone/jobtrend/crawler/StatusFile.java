package com.capstone.jobtrend.crawler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 대시보드용 상태 파일(status.json). 수집 단계마다 DB 의 숫자를 읽어 덮어쓴다(읽기만).
 * 비밀번호·공고 원문·회사 이름은 넣지 않는다. 숫자와 기술 이름만.
 * 위치: crawler.status-file, 비어 있으면 raw 폴더 옆(도커에서는 /data/status.json).
 * 쓰기에 실패해도 수집은 실패로 치지 않는다.
 */
@Component
public class StatusFile {

    private static final Logger log = LoggerFactory.getLogger(StatusFile.class);
    static final int SCHEMA = 1;
    private static final int HISTORY_DAYS = 14;
    private static final int BACKEND_CODE = 84;
    private static final LocalTime DAILY_AT = LocalTime.of(4, 0);

    // 메모(note)에서 늘 붙는 부분. 이걸 지우고 남는 조각이 경고다
    private static final Pattern NEW = Pattern.compile("새 공고 (\\d+)");
    private static final Pattern CLOSED = Pattern.compile("마감 (\\d+)(?: \\(마감일 지남 (\\d+) · 목록에서 사라짐 (\\d+)\\))?");
    private static final Pattern STAGE = Pattern.compile("([①②③④⑤]) ([^·]+?)(?= · |$)");
    private static final Pattern PROGRESS = Pattern.compile("(\\d+)/(\\d+)");
    private static final Pattern ROUTINE = Pattern.compile(
            "새 공고 \\d+|마감 \\d+(?: \\(마감일 지남 \\d+ · 목록에서 사라짐 \\d+\\))?(?: \\(이유별 기록 전\\))?"
            + "|제목·회사명 없어 건너뜀 \\d+|처음 보는 마감 표기 \\d+|[①②③④⑤] [^·]+?(?= · |$)");

    private final NamedParameterJdbcTemplate db;
    private final CrawlerProperties props;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public StatusFile(NamedParameterJdbcTemplate db, CrawlerProperties props) {
        this.db = db;
        this.props = props;
    }

    /** 수집 중 단계마다 부른다. 예외를 밖으로 내보내지 않는다. */
    public void refresh() {
        try {
            write(defaultPath());
        } catch (Exception e) {
            log.warn("상태 파일을 쓰지 못함: {}", e.toString());
        }
    }

    Path defaultPath() {
        if (!props.statusFile().isBlank()) return Path.of(props.statusFile());
        Path raw = Path.of(props.rawDir()).toAbsolutePath();
        return (raw.getParent() == null ? raw : raw.getParent()).resolve("status.json");
    }

    public void write(Path file) throws IOException {
        String text = json.writeValueAsString(build(CrawlService.now()));
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    Map<String, Object> build(LocalDateTime now) {
        MapSqlParameterSource none = new MapSqlParameterSource();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", SCHEMA);
        out.put("generated_at", iso(now));
        out.put("next_run_at", iso(nextRun(now)));

        // ④·⑤만 다시 돌린 회차(목록 0건으로 DONE)는 뺀다. 실패·도는 중인 회차는 0건이어도 넣는다
        List<Map<String, Object>> runs = db.queryForList("""
                SELECT run_id, started_at, finished_at, heartbeat_at, status, list_complete, list_cnt, new_cnt, closed_cnt, fail_cnt, note
                  FROM crawl_run
                 WHERE NOT (status = 'DONE' AND list_cnt = 0)
                 ORDER BY run_id DESC FETCH FIRST 60 ROWS ONLY
                """, none);
        List<Map<String, Object>> parsed = runs.stream().map(StatusFile::run).toList();
        out.put("last", parsed.isEmpty() ? null : parsed.get(0));
        out.put("streak_ok_days", streak(parsed));
        out.put("crawl_days", db.queryForObject(
                "SELECT COUNT(DISTINCT TRUNC(started_at)) FROM crawl_run WHERE list_cnt > 0", none, Integer.class));
        out.put("history", history(parsed));

        out.put("totals", lower(db.queryForMap("""
                SELECT (SELECT COUNT(*) FROM posting WHERE is_closed = 0) AS open_postings,
                       (SELECT COUNT(*) FROM posting) AS postings,
                       (SELECT COUNT(*) FROM company) AS companies,
                       (SELECT COUNT(*) FROM posting WHERE is_closed = 0 AND is_excluded = 1) AS excluded,
                       (SELECT COUNT(*) FROM posting p WHERE p.is_closed = 0 AND p.detail_at IS NOT NULL AND p.is_excluded = 0
                           AND EXISTS (SELECT 1 FROM posting_skill ps JOIN skill sk ON sk.skill_id = ps.skill_id AND sk.is_active = 1
                                        WHERE ps.posting_id = p.posting_id)) AS stat_base
                  FROM dual
                """, none)));
        out.put("backend_top", backendTop());
        return out;
    }

    private Map<String, Object> backendTop() {
        MapSqlParameterSource p = new MapSqlParameterSource("code", BACKEND_CODE);
        List<Map<String, Object>> rows = db.queryForList("""
                WITH d AS (SELECT DISTINCT calc_date FROM job_skill_stat ORDER BY calc_date DESC FETCH FIRST 2 ROWS ONLY),
                     j AS (SELECT job_id, name FROM job WHERE saramin_code = :code)
                SELECT s.calc_date, j.name AS job, s.rank_no, k.name AS skill, s.ratio, s.posting_cnt, s.job_total,
                       (SELECT o.rank_no FROM job_skill_stat o
                         WHERE o.job_id = s.job_id AND o.skill_id = s.skill_id
                           AND o.calc_date = (SELECT MIN(calc_date) FROM d)
                           AND (SELECT COUNT(*) FROM d) = 2) AS prev_rank
                  FROM job_skill_stat s JOIN j ON j.job_id = s.job_id JOIN skill k ON k.skill_id = s.skill_id
                 WHERE s.calc_date = (SELECT MAX(calc_date) FROM d) AND s.rank_no <= 10
                 ORDER BY s.rank_no
                """, p);
        if (rows.isEmpty()) return null;
        Map<String, Object> top = new LinkedHashMap<>();
        Object d = value(rows.get(0).get("CALC_DATE"));
        top.put("calc_date", d == null ? null : d.toString().substring(0, 10));
        top.put("job", rows.get(0).get("JOB"));
        top.put("job_total", rows.get(0).get("JOB_TOTAL"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("rank", r.get("RANK_NO"));
            i.put("skill", r.get("SKILL"));
            i.put("ratio", r.get("RATIO"));
            i.put("posting_cnt", r.get("POSTING_CNT"));
            i.put("prev_rank", r.get("PREV_RANK"));
            items.add(i);
        }
        top.put("items", items);
        return top;
    }

    /** crawl_run 한 줄 → 대시보드가 바로 쓰는 모양 */
    static Map<String, Object> run(Map<String, Object> r) {
        String status = String.valueOf(r.get("STATUS"));
        String note = r.get("NOTE") == null ? "" : r.get("NOTE").toString();
        int fail = num(r.get("FAIL_CNT"));
        boolean complete = num(r.get("LIST_COMPLETE")) == 1;
        LocalDateTime start = time(r.get("STARTED_AT"));
        LocalDateTime end = time(r.get("FINISHED_AT"));
        List<String> warnings = warnings(status, note);
        if (fail > 0) warnings.add("상세 실패 " + fail + "건");
        if ("DONE".equals(status) && !complete) warnings.add("목록을 끝까지 받지 못함");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_id", num(r.get("RUN_ID")));
        m.put("date", start == null ? null : start.toLocalDate().toString());
        m.put("status", status);
        m.put("result", result(status, warnings));
        m.put("started_at", start == null ? null : iso(start));
        m.put("finished_at", end == null ? null : iso(end));
        m.put("heartbeat_at", value(r.get("HEARTBEAT_AT")));
        m.put("minutes", start == null || end == null ? null : java.time.Duration.between(start, end).toMinutes());
        String stage = stage(status, note);
        m.put("stage", stage);
        m.put("stage_step", stageStep(stage));
        m.put("detail_done", stage == null ? null : find(PROGRESS, stage, 1));
        m.put("detail_target", stage == null ? null : find(PROGRESS, stage, 2));
        m.put("list_complete", complete);
        m.put("list", num(r.get("LIST_CNT")));
        m.put("new_postings", find(NEW, note, 1));
        m.put("detail_ok", num(r.get("NEW_CNT")));
        m.put("detail_fail", fail);
        m.put("closed", num(r.get("CLOSED_CNT")));
        m.put("closed_deadline", find(CLOSED, note, 2));
        m.put("closed_unseen", find(CLOSED, note, 3));
        m.put("warnings", warnings);
        return m;
    }

    /** 늘 붙는 숫자·단계 표시를 지우고 남은 조각. 중단된 회차는 원인 첫 줄 */
    static List<String> warnings(String status, String note) {
        List<String> w = new ArrayList<>();
        if (note == null || note.isBlank()) return w;
        String rest = ROUTINE.matcher(note).replaceAll("");
        if ("FAILED".equals(status)) {                       // 중단 원인(예외)은 한 덩어리로
            String cause = rest.replaceAll("^[\\s·]+", "").strip();
            if (!cause.isEmpty()) w.add(CrawlService.shortNote(cause));
            return w;
        }
        for (String part : rest.split(" · ")) {
            String t = part.strip();
            if (!t.isEmpty()) w.add(t);
        }
        return w;
    }

    static String result(String status, List<String> warnings) {
        if ("RUNNING".equals(status)) return "RUNNING";
        if (!"DONE".equals(status)) return "FAIL";
        return warnings.isEmpty() ? "OK" : "WARN";
    }

    /** 도는 중이면 메모의 마지막 단계 표시(① 목록 끝 → 다음은 ②), 중단이면 멈춘 단계(없으면 null), 정상 끝은 null */
    static String stage(String status, String note) {
        boolean running = "RUNNING".equals(status);
        if (!running && !"FAILED".equals(status)) return null;
        String last = null;
        Matcher m = STAGE.matcher(note == null ? "" : note);
        while (m.find()) last = m.group(1) + " " + m.group(2).strip();
        return last == null && running ? "① 목록 받는 중" : last;
    }

    /** 지금 하고 있는 단계 1~5. "① 목록 끝" 이면 ②를 하는 중 */
    static Integer stageStep(String stage) {
        if (stage == null || stage.isEmpty()) return null;
        int n = "①②③④⑤".indexOf(stage.charAt(0)) + 1;
        if (n == 0) return null;
        return stage.endsWith("끝") ? Math.min(n + 1, 5) : n;
    }

    /** 가장 최근 날부터 하루 한 회차씩 보고, OK 가 이어진 날 수. 도는 중인 회차는 건너뛴다 */
    static int streak(List<Map<String, Object>> runs) {
        int n = 0;
        Set<Object> days = new LinkedHashSet<>();
        for (Map<String, Object> r : runs) {
            if ("RUNNING".equals(r.get("result"))) continue;
            if (!days.add(r.get("date"))) continue;
            if (!"OK".equals(r.get("result"))) break;
            n++;
        }
        return n;
    }

    /** 최근 14일, 날마다 그날 마지막 회차 하나. 오래된 날부터 */
    static List<Map<String, Object>> history(List<Map<String, Object>> runs) {
        Map<Object, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (Map<String, Object> r : runs) {
            if ("RUNNING".equals(r.get("result"))) continue;
            byDay.putIfAbsent(r.get("date"), r);
            if (byDay.size() == HISTORY_DAYS) break;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : byDay.values()) {
            Map<String, Object> h = new LinkedHashMap<>();
            for (String k : List.of("date", "run_id", "result", "minutes", "list", "new_postings", "detail_ok", "detail_fail",
                    "closed", "closed_deadline", "closed_unseen")) {
                h.put(k, r.get(k));
            }
            h.put("warning_count", ((List<?>) r.get("warnings")).size());
            out.add(0, h);
        }
        return out;
    }

    static LocalDateTime nextRun(LocalDateTime now) {
        LocalDateTime today = LocalDate.from(now).atTime(DAILY_AT);
        return now.isBefore(today) ? today : today.plusDays(1);
    }

    /** 초까지 늘 같은 모양(2026-10-21T04:00:00). toString 은 0초를 생략한다 */
    static String iso(LocalDateTime t) {
        return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(t.withNano(0));
    }

    private static Integer find(Pattern p, String s, int group) {
        Matcher m = p.matcher(s == null ? "" : s);
        return m.find() && m.group(group) != null ? Integer.valueOf(m.group(group)) : null;
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static LocalDateTime time(Object o) {
        return o instanceof Timestamp t ? t.toLocalDateTime() : o instanceof LocalDateTime l ? l : null;
    }

    private static Object value(Object o) {
        if (o instanceof Timestamp t) return iso(t.toLocalDateTime());
        if (o instanceof java.sql.Date d) return d.toLocalDate().toString();
        return o;
    }

    private static Map<String, Object> lower(Map<String, Object> m) {
        Map<String, Object> r = new LinkedHashMap<>();
        m.forEach((k, v) -> r.put(k.toLowerCase(), value(v)));
        return r;
    }
}
