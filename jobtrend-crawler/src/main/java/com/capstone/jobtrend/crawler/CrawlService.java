package com.capstone.jobtrend.crawler;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.jobtrend.crawler.DetailPageParser.ParseFailedException;
import com.capstone.jobtrend.crawler.DetailPageParser.PostingDetail;
import com.capstone.jobtrend.crawler.DetailPageParser.Tag;
import com.capstone.jobtrend.crawler.ListPageParser.ListItem;
import com.capstone.jobtrend.crawler.ListPageParser.ListPage;
import com.capstone.jobtrend.crawler.SaraminClient.BlockedException;

/**
 * 하루 한 번의 수집: ① 목록 → ② 마감 처리 → ③ 상세 → ④ 기술 추출 → ⑤ 집계(StatService).
 * 사람인에 요청하는 곳은 ①과 ③뿐이고 SaraminClient 를 지난다. 공고 하나가 끝날 때마다 바로 저장한다.
 * 쿼리는 설계 문서(캡스톤_DB_설계.html)의 단계별 쿼리를 그대로 옮겼다.
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PAGE_SIZE = 100;
    private static final int MAX_LIST_FAILS_IN_ROW = 3;
    /** 쪽을 넘기는 사이 순서가 바뀌면 경계의 공고를 1~2건 못 본다. 이만큼은 끝까지 받은 것으로 본다 */
    static final int LIST_TOLERANCE = 2;
    private static final int AIX_CODE = 193;
    private static final Set<Integer> UNIX_CODES = Set.of(305, 246, 288, 207, 303);   // Unix·Linux·Solaris·CentOS·Ubuntu

    /** 이 회차를 여기서 멈춘다(차단·연속 실패). crawl_run 은 FAILED 로 남는다. */
    static class StopRun extends RuntimeException {
        StopRun(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** crawl_run 에 남기는 건수. */
    static class Counts {
        int list;
        int newPostings;
        int closed;
        int detailOk;
        int fail;
        int skillPostings;
        int rawMissing;
        int excluded;
    }

    private final NamedParameterJdbcTemplate db;
    private final TransactionTemplate tx;
    private final CrawlerProperties props;
    private final SeedLoader seed;
    private final StatService stats;
    private final SaraminClient client = new SaraminClient();
    private final ListPageParser listParser = new ListPageParser();
    private final DetailPageParser detailParser = new DetailPageParser();
    private final RawStore rawStore;

    public CrawlService(NamedParameterJdbcTemplate db, TransactionTemplate tx, CrawlerProperties props,
                        SeedLoader seed, StatService stats) {
        this.db = db;
        this.tx = tx;
        this.props = props;
        this.seed = seed;
        this.stats = stats;
        this.rawStore = new RawStore(Path.of(props.rawDir()));
    }

    static LocalDateTime now() {
        return LocalDateTime.now(SEOUL);
    }

    /** 전체 수집이 넘지 않는 시간. 이보다 오래된 RUNNING 만 죽은 앱이 남긴 것으로 본다. */
    static final int STALE_RUN_HOURS = 6;

    /**
     * 앱이 뜰 때: 죽은 앱이 남긴 RUNNING 을 FAILED 로.
     * 시작한 지 6시간이 안 된 RUNNING 은 다른 곳(테스트 서버·PC)에서 지금 돌고 있는 회차일 수 있어서 건드리지 않는다.
     * 그러면 run() 이 RUNNING 을 보고 시작하지 않으므로 두 수집이 동시에 돌지 않는다.
     */
    public int failLeftoverRuns() {
        return db.update("""
                UPDATE crawl_run SET status = 'FAILED', finished_at = :now,
                       note = SUBSTR('앱이 다시 떠서 정리함 (끝나지 않은 회차) ' || note, 1, 500)
                 WHERE status = 'RUNNING' AND started_at < :now - NUMTODSINTERVAL(:hours, 'HOUR')
                """, new MapSqlParameterSource("now", now()).addValue("hours", STALE_RUN_HOURS));
    }

    /** 단계가 끝날 때마다 건수를 적어 둔다. 앱이 중간에 죽어도 어디까지 했는지 남는다. */
    private void saveCounts(long runId, Counts c, String note) {
        db.update("""
                UPDATE crawl_run SET list_cnt = :list, new_cnt = :detail, closed_cnt = :closed, fail_cnt = :fail, note = :note
                 WHERE run_id = :id
                """, new MapSqlParameterSource("list", c.list).addValue("detail", c.detailOk)
                .addValue("closed", c.closed).addValue("fail", c.fail)
                .addValue("note", cut(runNote(c, note), 500), Types.VARCHAR).addValue("id", runId));
    }

    /** new_cnt 는 설계대로 "상세를 받은 공고"다. 목록에서 새로 만든 공고 수는 메모에 남긴다. */
    private static String runNote(Counts c, String note) {
        String base = "새 공고 " + c.newPostings;
        return note == null ? base : base + " · " + note;
    }

    /** 한 회차. 이미 돌고 있으면 시작하지 않고 false. */
    public synchronized boolean run() {
        Integer running = db.queryForObject("SELECT COUNT(*) FROM crawl_run WHERE status = 'RUNNING'",
                new MapSqlParameterSource(), Integer.class);
        if (running != null && running > 0) {
            log.warn("수집이 이미 돌고 있어서 시작하지 않음");
            return false;
        }
        LocalDateTime runStart = now();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        db.update("INSERT INTO crawl_run (started_at, status) VALUES (:start, 'RUNNING')",
                new MapSqlParameterSource("start", runStart), key, new String[] {"run_id"});
        long runId = key.getKey().longValue();
        log.info("수집 시작 #{} (직무 {}, 목록 한도 {}쪽, 상세 한도 {}건)", runId, props.jobs(),
                props.maxListPages(), props.maxDetails());

        Counts c = new Counts();
        String status = "DONE";
        String note = null;
        boolean complete = false;
        Set<Integer> incompleteJobs = Set.of();
        try {
            seed.load(props.jobs());
            if (props.localOnly()) {
                note = "④·⑤만 다시 (요청 없음)";
            } else {
                incompleteJobs = collectLists(c);
                complete = incompleteJobs.isEmpty();
                db.update("UPDATE crawl_run SET list_complete = :v WHERE run_id = :id",
                        new MapSqlParameterSource("v", complete ? 1 : 0).addValue("id", runId));
                saveCounts(runId, c, "① 목록 끝");
                note = closePostings(runStart, incompleteJobs, c);
                saveCounts(runId, c, "② 마감 끝" + (note == null ? "" : " · " + note));
                fetchDetails(c, runId, note);
                saveCounts(runId, c, "③ 상세 끝" + (note == null ? "" : " · " + note));
            }
            extractSkills(c);
            stats.calculate(LocalDate.now(SEOUL));
        } catch (StopRun e) {
            status = "FAILED";
            note = e.getMessage();
            log.error("수집 중단: {}", e.getMessage(), e.getCause());
        } catch (RuntimeException e) {
            status = "FAILED";
            note = e.toString();
            log.error("수집 실패", e);
        } finally {
            saveCounts(runId, c, note);
            db.update("UPDATE crawl_run SET finished_at = :now, status = :status WHERE run_id = :id",
                    new MapSqlParameterSource("now", now()).addValue("status", status).addValue("id", runId));
            log.info("수집 끝 #{} {}: 목록 {}건(새 공고 {}, 끝까지 받음 {}), 마감 {}, 상세 성공 {} 실패 {}, 기술 추출 {}건(raw 없음 {})",
                    runId, status, c.list, c.newPostings, complete, c.closed, c.detailOk, c.fail,
                    c.skillPostings, c.rawMissing);
        }
        return "DONE".equals(status);
    }

    // ---------- ① 목록 ----------

    /** 끝까지 받지 못한 직무의 job_id. 비어 있으면 모든 직무를 끝까지 받은 것. */
    Set<Integer> collectLists(Counts c) {
        Map<Integer, Integer> jobIds = new HashMap<>();
        db.query("SELECT saramin_code, job_id FROM job", rs -> {
            jobIds.put(rs.getInt(1), rs.getInt(2));
        });
        Set<Long> seenAll = new HashSet<>();
        Set<Integer> incomplete = new HashSet<>();
        int listFailsInRow = 0;                                // 직무를 넘어가며 목록이 연달아 실패한 횟수
        for (int code : props.jobs()) {
            int jobId = jobIds.get(code);
            // 이 직무에 걸린 진행 중 공고 수(지난 회차까지). 이번에 절반도 못 보면 목록이 이상한 것으로 본다
            Integer before = db.queryForObject("""
                    SELECT COUNT(*) FROM posting_job pj JOIN posting p ON p.posting_id = pj.posting_id
                     WHERE pj.job_id = :jobId AND p.is_closed = 0
                    """, new MapSqlParameterSource("jobId", jobId), Integer.class);
            Set<Long> seen = new LinkedHashSet<>();
            Integer total = null;
            boolean ok = true;
            int fetched = 0;
            for (int page = 1; ; page++) {
                if (props.maxListPages() > 0 && page > props.maxListPages()) {
                    ok = false;                                // 시험 한도에서 끊음 = 덜 받음
                    break;
                }
                if (total != null && page > total / PAGE_SIZE + 3) {
                    ok = false;                                // 총 건수보다 쪽이 많다: 쪽 넘기기가 먹지 않는 것
                    log.warn("직무 {} 쪽이 끝나지 않음 (총 {}건, {}쪽)", code, total, page);
                    break;
                }
                ListPage lp;
                try {
                    lp = listParser.parse(client.listPage(code, page));
                } catch (BlockedException e) {
                    throw new StopRun("목록 요청이 막힘: " + e.getMessage(), e);
                } catch (IOException | ParseFailedException e) {
                    log.warn("직무 {} 목록 {}쪽 실패: {}", code, page, e.toString());
                    ok = false;
                    if (++listFailsInRow >= MAX_LIST_FAILS_IN_ROW)   // 직무마다 재시도하며 계속 두드리지 않게
                        throw new StopRun("목록이 " + listFailsInRow + "번 연달아 실패", e);
                    break;
                }
                listFailsInRow = 0;
                fetched++;
                if (total == null) total = lp.totalCount();
                LocalDateTime now = now();                     // 쪽마다: 자정을 넘겨도 D-N·내일마감이 맞게
                for (ListItem it : lp.items()) {
                    seen.add(it.postingId());
                    saveListItem(it, jobId, now, c);
                }
                if (lp.items().size() < PAGE_SIZE) break;
            }
            if (!listComplete(total, seen.size())) ok = false;
            // 0건 목록(점검·코드 폐지)이나 급감한 목록을 '끝까지 받음'으로 보면 그 직무 공고가 한꺼번에 마감된다
            if (total != null && total == 0) {
                ok = false;
                log.warn("직무 {} 목록이 0건: 덜 받은 것으로 봄", code);
            } else if (before != null && before >= 20 && seen.size() * 2 < before) {
                ok = false;
                log.warn("직무 {} 목록이 {}건 → {}건으로 절반 넘게 줄어 덜 받은 것으로 봄", code, before, seen.size());
            }
            log.info("① 직무 {}: {}쪽, {}건 / 총 {}건{}", code, fetched, seen.size(), total, ok ? "" : " (덜 받음)");
            if (!ok) incomplete.add(jobId);
            seenAll.addAll(seen);
        }
        c.list = seenAll.size();
        return incomplete;
    }

    /** 총 건수와 LIST_TOLERANCE 건 이하 차이면 끝까지 받은 것으로 본다. */
    static boolean listComplete(Integer total, int seen) {
        return total != null && seen >= total - LIST_TOLERANCE;
    }

    private void saveListItem(ListItem it, int jobId, LocalDateTime now, Counts c) {
        if (it.title().isBlank() || it.companyName().isBlank()) {   // 오라클은 '' 를 NULL 로 넣어 NOT NULL 에 걸린다
            log.warn("제목이나 회사명이 비어 건너뜀: {}", it.postingId());
            return;
        }
        ListTexts.Deadline dl = ListTexts.deadline(it.deadlineText(), now.toLocalDate());
        if (dl.unknown()) log.info("처음 보는 마감 표기 {}: {}", it.postingId(), it.deadlineText());
        ListTexts.Posted posted = ListTexts.posted(it.postedText(), now);
        LocalDateTime modifiedAt = posted != null && posted.modified() ? posted.at() : null;

        tx.executeWithoutResult(s -> {
            MapSqlParameterSource p = new MapSqlParameterSource("postingId", it.postingId())
                    .addValue("now", now)
                    .addValue("deadlineType", dl.type().name())
                    .addValue("modifiedAt", modifiedAt, Types.TIMESTAMP);
            // 이미 있는 공고: 목록이 정하는 칸만. 새 수정(이전 값이 없거나 1시간 이상 뒤)일 때만 수정 시각·실패 횟수를 바꾼다
            int updated = db.update("""
                    UPDATE posting
                       SET last_seen_at     = :now,
                           deadline_type    = :deadlineType,
                           detail_fail_cnt  = CASE WHEN CAST(:modifiedAt AS TIMESTAMP) IS NOT NULL
                                                    AND (list_modified_at IS NULL
                                                         OR CAST(:modifiedAt AS TIMESTAMP) >= list_modified_at + INTERVAL '1' HOUR)
                                                   THEN 0 ELSE detail_fail_cnt END,
                           list_modified_at = CASE WHEN CAST(:modifiedAt AS TIMESTAMP) IS NOT NULL
                                                    AND (list_modified_at IS NULL
                                                         OR CAST(:modifiedAt AS TIMESTAMP) >= list_modified_at + INTERVAL '1' HOUR)
                                                   THEN CAST(:modifiedAt AS TIMESTAMP) ELSE list_modified_at END
                     WHERE posting_id = :postingId
                    """, p);
            if (updated == 0) {
                p.addValue("companyId", companyId(it))
                        .addValue("title", cut(it.title(), 300))
                        .addValue("url", SaraminClient.BASE + "/zf_user/jobs/relay/view?rec_idx=" + it.postingId())
                        .addValue("headhunting", it.headhunting() ? 1 : 0);
                db.update("""
                        INSERT INTO posting (posting_id, company_id, title, url, deadline_type, is_headhunting,
                                             first_seen_at, last_seen_at, list_modified_at)
                        VALUES (:postingId, :companyId, :title, :url, :deadlineType, :headhunting,
                                :now, :now, :modifiedAt)
                        """, p);
                c.newPostings++;
            }
            // (공고, 직무) 쌍은 새 공고든 아니든 매번. 오라클에는 INSERT IGNORE 가 없어서 MERGE
            db.update("""
                    MERGE INTO posting_job t
                    USING (SELECT :postingId AS posting_id, :jobId AS job_id FROM dual) s
                       ON (t.posting_id = s.posting_id AND t.job_id = s.job_id)
                     WHEN NOT MATCHED THEN INSERT (posting_id, job_id) VALUES (s.posting_id, s.job_id)
                    """, new MapSqlParameterSource("postingId", it.postingId()).addValue("jobId", jobId));
        });
    }

    /** 회사 키는 csn. csn 이 없으면 csn 없는 같은 이름 회사를 쓴다. */
    private int companyId(ListItem it) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("csn", cut(it.companyCsn(), 64), Types.VARCHAR)
                .addValue("name", cut(it.companyName(), 100))
                .addValue("grp", cut(it.groupName(), 50), Types.VARCHAR)
                .addValue("size", cut(it.sizeLabel(), 20), Types.VARCHAR);
        if (it.companyCsn() != null) {
            db.update("""
                    MERGE INTO company t
                    USING (SELECT :csn AS saramin_csn, :name AS name, :grp AS group_name, :size AS size_label FROM dual) s
                       ON (t.saramin_csn = s.saramin_csn)
                     WHEN MATCHED THEN UPDATE SET t.name = s.name, t.group_name = s.group_name, t.size_label = s.size_label
                     WHEN NOT MATCHED THEN INSERT (saramin_csn, name, group_name, size_label)
                                           VALUES (s.saramin_csn, s.name, s.group_name, s.size_label)
                    """, p);
            return db.queryForObject("SELECT company_id FROM company WHERE saramin_csn = :csn", p, Integer.class);
        }
        List<Integer> found = db.queryForList(
                "SELECT company_id FROM company WHERE saramin_csn IS NULL AND name = :name ORDER BY company_id", p, Integer.class);
        if (!found.isEmpty()) return found.get(0);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        db.update("INSERT INTO company (name, group_name, size_label) VALUES (:name, :grp, :size)", p, key,
                new String[] {"company_id"});
        return key.getKey().intValue();
    }

    // ---------- ② 마감 처리 ----------

    /** crawl_run.note 에 남길 말이 있으면 돌려준다. */
    String closePostings(LocalDateTime runStart, Set<Integer> incompleteJobs, Counts c) {
        LocalDateTime now = now();
        MapSqlParameterSource p = new MapSqlParameterSource("runStart", runStart)
                .addValue("now", now)
                .addValue("today", now.toLocalDate())
                .addValue("keepDays", props.rawKeepDays())
                // 덜 받은 직무에 걸린 공고는 안 보여도 마감을 미룬다 (빈 IN () 은 오라클 문법 오류라 -1)
                .addValue("incomplete", incompleteJobs.isEmpty() ? List.of(-1) : List.copyOf(incompleteJobs));
        // 앞 공백 필수: 텍스트 블록은 줄 끝 공백을 지워서 "AND" 와 붙는다
        String held = " NOT EXISTS (SELECT 1 FROM posting_job h WHERE h.posting_id = %s.posting_id AND h.job_id IN (:incomplete))";
        boolean anyComplete = incompleteJobs.size() < props.jobs().size();
        String note = incompleteJobs.isEmpty() ? null : "덜 받은 직무 " + incompleteJobs.size() + "개는 마감을 미룸";
        // 마감일이 지났고 이번 목록에 없는 공고: 목록을 다 못 받은 날에도 닫는다
        c.closed += db.update("""
                UPDATE posting SET is_closed = 1, closed_at = :now
                 WHERE is_closed = 0 AND last_seen_at < :runStart AND deadline_on < :today
                """, p);
        if (anyComplete) {
            Map<String, Object> m = db.queryForMap("""
                    SELECT NVL(SUM(CASE WHEN last_seen_at < :runStart AND (deadline_on IS NULL OR deadline_on >= :today)
                                        THEN 1 ELSE 0 END), 0) AS missing,
                           COUNT(*) AS open_before
                      FROM posting p
                     WHERE is_closed = 0 AND first_seen_at < :runStart AND """ + held.formatted("p"), p);
            long missing = ((Number) m.get("MISSING")).longValue();
            long openBefore = ((Number) m.get("OPEN_BEFORE")).longValue();
            // 직무가 많으면 작은 직무 하나가 통째로 빠져도 전체 10%를 넘지 않는다 → 직무마다도 본다
            List<Map<String, Object>> perJob = db.queryForList("""
                    SELECT j.name,
                           NVL(SUM(CASE WHEN p.last_seen_at < :runStart AND (p.deadline_on IS NULL OR p.deadline_on >= :today)
                                        THEN 1 ELSE 0 END), 0) AS missing,
                           COUNT(*) AS open_before
                      FROM posting p JOIN posting_job pj ON pj.posting_id = p.posting_id JOIN job j ON j.job_id = pj.job_id
                     WHERE p.is_closed = 0 AND p.first_seen_at < :runStart AND j.job_id NOT IN (:incomplete)
                     GROUP BY j.name
                    """, p);
            String badJob = perJob.stream()
                    .filter(r -> ((Number) r.get("OPEN_BEFORE")).longValue() >= 20
                            && ((Number) r.get("MISSING")).longValue() * 10 > ((Number) r.get("OPEN_BEFORE")).longValue())
                    .map(r -> r.get("NAME") + " " + r.get("MISSING") + "/" + r.get("OPEN_BEFORE"))
                    .findFirst().orElse(null);
            if (openBefore > 0 && missing * 10 > openBefore) {
                note = join(note, "안 보인 공고 " + missing + "/" + openBefore + "건이 10%를 넘어 마감 처리를 건너뜀");
                log.warn("② {}", note);
            } else if (badJob != null) {
                note = join(note, "직무 " + badJob + "건이 10%를 넘어 마감 처리를 건너뜀");
                log.warn("② {}", note);
            } else {
                c.closed += db.update("""
                        UPDATE posting p SET is_closed = 1, closed_at = :now
                         WHERE is_closed = 0 AND last_seen_at < :runStart AND """ + held.formatted("p"), p);
            }
        }
        // 다시 보인 공고는 되살린다. raw 파일을 지운 뒤라면 detail_at 도 비워 ③에서 바로 다시 받는다
        int revived = db.update("""
                UPDATE posting
                   SET is_closed       = 0,
                       closed_at       = NULL,
                       detail_at       = CASE WHEN closed_at < :now - NUMTODSINTERVAL(:keepDays, 'DAY') THEN NULL ELSE detail_at END,
                       detail_fail_cnt = CASE WHEN closed_at < :now - NUMTODSINTERVAL(:keepDays, 'DAY') THEN 0 ELSE detail_fail_cnt END
                 WHERE is_closed = 1 AND last_seen_at >= :runStart
                """, p);
        List<Long> expired = db.queryForList("""
                SELECT posting_id FROM posting
                 WHERE is_closed = 1 AND closed_at < :now - NUMTODSINTERVAL(:keepDays, 'DAY')
                """, p, Long.class);
        int deleted = 0;
        for (long id : expired) {
            try {
                if (rawStore.delete(id)) deleted++;
            } catch (IOException e) {
                log.warn("raw 파일 삭제 실패 {}: {}", id, e.toString());
            }
        }
        log.info("② 마감 {}건, 되살림 {}건, raw 삭제 {}건{}", c.closed, revived, deleted,
                incompleteJobs.isEmpty() ? "" : anyComplete ? " (덜 받은 직무 " + incompleteJobs.size() + "개는 마감일 지난 것만 닫음)"
                        : " (목록을 덜 받아 마감일 지난 것만 닫음)");
        return note;
    }

    private static String join(String a, String b) {
        return a == null ? b : a + " · " + b;
    }

    // ---------- ③ 상세 ----------

    void fetchDetails(Counts c, long runId, String note) {
        List<Long> targets = db.queryForList("""
                SELECT posting_id FROM posting
                 WHERE is_closed = 0 AND detail_fail_cnt < 3
                   AND (detail_at IS NULL OR list_modified_at > detail_at)
                 ORDER BY posting_id DESC
                """, new MapSqlParameterSource(), Long.class);
        int total = targets.size();
        if (props.maxDetails() > 0 && targets.size() > props.maxDetails()) targets = targets.subList(0, props.maxDetails());
        log.info("③ 상세 대상 {}건 중 {}건", total, targets.size());
        int consecutive = 0;
        for (long id : targets) {
            try {
                PostingDetail d = detailParser.parse(client.detail(id));
                rawStore.save(id, new RawStore.Raw(d.tags(), d.required(), d.preferred()));
                saveDetail(id, d);
                c.detailOk++;
                consecutive = 0;
            } catch (BlockedException e) {
                throw new StopRun("상세 요청이 막힘: " + e.getMessage(), e);
            } catch (IOException | ParseFailedException e) {
                c.fail++;
                db.update("UPDATE posting SET detail_fail_cnt = detail_fail_cnt + 1 WHERE posting_id = :id",
                        new MapSqlParameterSource("id", id));
                log.warn("상세 실패 {}: {}", id, e.toString());
                if (++consecutive >= props.maxConsecutiveFailures())
                    throw new StopRun("상세가 " + consecutive + "번 연달아 실패", e);
            }
            if (c.detailOk > 0 && c.detailOk % 100 == 0) {
                log.info("③ {}건 받음", c.detailOk);
                saveCounts(runId, c, "③ 상세 받는 중" + (note == null ? "" : " · " + note));
            }
        }
    }

    private void saveDetail(long id, PostingDetail d) {
        ListTexts.Career career = ListTexts.career(d.summaryValue("경력"));
        db.update("""
                UPDATE posting
                   SET career_type = :careerType, exp_min = :expMin, exp_max = :expMax,
                       education = :education, employment_type = :employmentType, region = :region,
                       posted_on = :postedOn, deadline_on = :deadlineOn,
                       detail_at = :now, detail_fail_cnt = 0
                 WHERE posting_id = :id
                """, new MapSqlParameterSource("id", id)
                .addValue("careerType", career.type() == null ? null : career.type().name(), Types.VARCHAR)
                .addValue("expMin", career.expMin(), Types.INTEGER)
                .addValue("expMax", career.expMax(), Types.INTEGER)
                .addValue("education", cut(d.summaryValue("학력"), 30), Types.VARCHAR)
                .addValue("employmentType", cut(d.summaryValue("근무형태"), 50), Types.VARCHAR)
                .addValue("region", cut(d.summaryValue("근무지역"), 100), Types.VARCHAR)
                .addValue("postedOn", d.startAt() == null ? null : d.startAt().toLocalDate(), Types.DATE)
                .addValue("deadlineOn", d.endAt() == null ? null : d.endAt().toLocalDate(), Types.DATE)
                .addValue("now", now()));
        if (career.type() == null && d.summaryValue("경력") != null)
            log.info("처음 보는 경력 표기 {}: {}", id, d.summaryValue("경력"));
        if (d.startAt() == null && d.endAt() == null)
            log.info("접수 기간(.info_period)이 없음 {}: 마감일을 모름", id);
    }

    // ---------- ④ 기술 추출 ----------

    void extractSkills(Counts c) {
        List<SkillMatcher.Alias> aliases = new ArrayList<>();
        Map<String, Integer> byName = new HashMap<>();          // 태그 이름 → 기술 (글에서 안 찾는 별칭도)
        db.query("SELECT alias, skill_id, text_match FROM skill_alias", rs -> {
            SkillMatcher.Alias a = SkillMatcher.Alias.of(rs.getString(1), rs.getInt(2), rs.getInt(3) == 1);
            aliases.add(a);
            byName.put(a.alias(), a.skillId());
        });
        Map<Integer, Integer> byCode = new HashMap<>();
        db.query("SELECT saramin_code, skill_id FROM skill WHERE saramin_code IS NOT NULL",
                rs -> {
                    byCode.put(rs.getInt(1), rs.getInt(2));
                });
        SkillMatcher matcher = new SkillMatcher(aliases);
        Set<Integer> itCodes = SeedLoader.readCodeNames().keySet();   // 사람인 IT 코드표 260개
        Map<String, Integer> unmatchedTags = new HashMap<>();

        Map<Long, String> targets = new java.util.LinkedHashMap<>();
        db.query("SELECT posting_id, title FROM posting WHERE is_closed = 0 AND detail_at IS NOT NULL", rs -> {
            targets.put(rs.getLong(1), rs.getString(2));
        });
        // 원본 폴더가 안 보이면 모든 공고가 '원본 없음'이 되어 detail_at 이 지워지고 다음 ③이 전부 다시 받는다 → 멈춘다
        if (!targets.isEmpty() && !rawStore.looksMounted())
            throw new StopRun("원본(raw) 폴더가 비었거나 없음: " + props.rawDir(), null);
        for (Map.Entry<Long, String> target : targets.entrySet()) {
            long id = target.getKey();
            Optional<RawStore.Raw> raw;
            try {
                raw = rawStore.read(id);
            } catch (IOException e) {
                log.warn("raw 파일이 깨짐 {}: {}", id, e.toString());
                raw = Optional.empty();
            }
            if (raw.isEmpty()) {                               // 기술은 지우지 않고 다음 ③이 다시 받게
                db.update("UPDATE posting SET detail_at = NULL, detail_fail_cnt = 0 WHERE posting_id = :id",
                        new MapSqlParameterSource("id", id));
                c.rawMissing++;
                continue;
            }
            Set<Integer> tag = new LinkedHashSet<>();
            Set<Integer> codes = new HashSet<>();
            raw.get().tags().forEach(t -> codes.add(t.code()));
            for (Tag t : raw.get().tags()) {
                // AIX(193) 태그는 대부분 'AI(인공지능)'과 같이 붙은 오탐이다(9/29 검수: 33건 중 27건). 유닉스 계열 태그가 같이 있을 때만 인정
                if (t.code() == AIX_CODE && codes.stream().noneMatch(UNIX_CODES::contains)) continue;
                Integer skill = byCode.get(t.code());
                if (skill == null) skill = byName.get(SkillMatcher.normalize(t.name()).trim());
                if (skill != null) tag.add(skill);
                else unmatchedTags.merge(t.name(), 1, Integer::sum);
            }
            Set<Integer> required = matcher.match(raw.get().required());
            Set<Integer> preferred = matcher.match(raw.get().preferred());
            boolean excluded = excluded(target.getValue(), raw.get().tags(), itCodes, required, preferred);
            List<MapSqlParameterSource> rows = new ArrayList<>();
            tag.forEach(s -> rows.add(skillRow(id, s, "TAG")));
            required.forEach(s -> rows.add(skillRow(id, s, "REQUIRED")));
            preferred.forEach(s -> rows.add(skillRow(id, s, "PREFERRED")));
            tx.executeWithoutResult(s -> {
                db.update("DELETE FROM posting_skill WHERE posting_id = :id", new MapSqlParameterSource("id", id));
                if (!rows.isEmpty())
                    db.batchUpdate("INSERT INTO posting_skill (posting_id, skill_id, source) VALUES (:id, :skill, :source)",
                            rows.toArray(MapSqlParameterSource[]::new));
                // 별칭 표를 고치면 판정도 바뀌므로 ④마다 다시 쓴다
                db.update("UPDATE posting SET is_excluded = :ex WHERE posting_id = :id",
                        new MapSqlParameterSource("ex", excluded ? 1 : 0).addValue("id", id));
            });
            c.skillPostings++;
            if (excluded) c.excluded++;
        }
        log.info("④ 기술 추출 {}건(통계 제외 {}건), raw 없어 다시 받을 공고 {}건", c.skillPostings, c.excluded, c.rawMissing);
        if (!unmatchedTags.isEmpty()) {
            String top = unmatchedTags.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(40).map(e -> e.getKey() + "(" + e.getValue() + ")").reduce((a, b) -> a + ", " + b).orElse("");
            log.info("④ 기술로 못 맞춘 태그 {}종 (많은 순): {}", unmatchedTags.size(), top);
        }
    }

    /** 반도체 설계 공고의 태그. 사람인이 '프론트엔드 설계(RTL)'를 프론트엔드 직무로도 올린다 */
    static final Set<String> CHIP_DESIGN_TAGS = Set.of("rtl", "asic", "fpga", "verilog");

    /** 소프트웨어 직무 제목. 정규화(소문자)한 제목에 쓴다 */
    static final Pattern DEV_TITLE = Pattern.compile(String.join("|",
            "(?<![a-z])s/?w(?![a-z])", "소프트웨어", "software", "펌웨어", "firmware", "(?<![a-z])bsp(?![a-z])",
            "개발자", "developer", "프로그래머", "programmer", "백엔드", "backend", "풀스택", "full\\s*stack", "퍼블리셔",
            "데이터\\s*(분석|엔지니어)", "데이터베이스", "(?<![a-z])dba(?![a-z])",
            "(앱|웹|ai|java|자바|python|파이썬)\\s*개발"));

    /**
     * 위 말이 있어도 하드웨어·반도체·비개발 직무인 제목 ('아날로그 회로 개발자', 'H/W·S/W 엔지니어 (회로설계)', '사업개발자').
     * '설계'·'반도체'만으로는 막지 않는다: '펌웨어 설계', '소프트웨어 설계/개발자', '반도체 팹리스 Embedded Software' 는 살려야 한다
     */
    static final Pattern NOT_DEV_TITLE = Pattern.compile(String.join("|",
            "회로", "하드웨어", "(?<![a-z])h/?w(?![a-z])", "(?<![a-z])(rtl|asic|fpga|verilog)(?![a-z])",
            "칩\\s*설계", "디지털\\s*설계", "아날로그", "analog", "기구",
            "사업\\s*개발", "(?<![a-z])bd(?![a-z])", "business\\s*develop", "콘텐츠\\s*개발", "신약"));

    /**
     * 통계에서 뺄 공고(9/29 동희님 결정). 공고 목록에는 남고 ⑤ 집계에서만 빠진다.
     * - 비개발: IT 코드표 밖 태그가 절반을 넘고(동률은 안 뺌), 자격요건·우대 글에서 잡힌 기술이 2개 미만
     *   (태그가 적거나 글이 충실한 진짜 개발 공고를 살리려고 글 조건을 붙였다)
     * - 반도체 설계: RTL·ASIC·FPGA·Verilog 태그가 2개 이상
     * 단, 제목이 소프트웨어 직무면 빼지 않는다(9/29 3차: 554건 중 약 24건이 반도체 회사 임베디드 SW·펌웨어,
     * 태그가 비IT 위주인 SW 개발 공고였다). '프론트엔드'는 반도체 '프론트엔드 설계'와 겹쳐서 넣지 않았다
     */
    static boolean excluded(String title, List<Tag> tags, Set<Integer> itCodes, Set<Integer> required, Set<Integer> preferred) {
        if (title != null) {
            String t = SkillMatcher.normalize(title);
            if (DEV_TITLE.matcher(t).find() && !NOT_DEV_TITLE.matcher(t).find()) return false;
        }
        long nonIt = tags.stream().filter(t -> !itCodes.contains(t.code())).count();
        Set<Integer> text = new HashSet<>(required);
        text.addAll(preferred);
        boolean nonDev = !tags.isEmpty() && nonIt * 2 > tags.size() && text.size() < 2;
        long chip = tags.stream().filter(t -> CHIP_DESIGN_TAGS.contains(SkillMatcher.normalize(t.name()).trim())).count();
        return nonDev || chip >= 2;
    }

    private static MapSqlParameterSource skillRow(long id, int skill, String source) {
        return new MapSqlParameterSource("id", id).addValue("skill", skill).addValue("source", source);
    }

    static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
