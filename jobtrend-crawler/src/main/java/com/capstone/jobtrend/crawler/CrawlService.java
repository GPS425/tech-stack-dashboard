package com.capstone.jobtrend.crawler;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Types;
import java.time.Duration;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jsoup.HttpStatusException;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    /** 총 건수를 못 읽은 직무의 쪽 상한. 가장 큰 직무(백엔드 약 1,800건)도 20쪽 안이다 */
    private static final int MAX_LIST_PAGES = 100;
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
        int skipped;            // 제목·회사명이 비어 새로 넣지 못한 목록 항목
        int deadlineUnknown;    // 처음 보는 마감 표기(마감 종류를 바꾸지 않음)
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
    private final NamedParameterJdbcTemplate beatDb;           // 살아 있음 표시 전용: 끊긴 연결에서 무한히 기다리지 않게 시간 제한
    private volatile Beat beat = Beat.NONE;                    // 지금 도는 회차의 살아 있음 표시

    public CrawlService(NamedParameterJdbcTemplate db, TransactionTemplate tx, CrawlerProperties props,
                        SeedLoader seed, StatService stats) {
        this.db = db;
        this.tx = tx;
        this.props = props;
        this.seed = seed;
        this.stats = stats;
        this.rawStore = new RawStore(Path.of(props.rawDir()));
        JdbcTemplate jt = new JdbcTemplate(db.getJdbcTemplate().getDataSource());
        jt.setQueryTimeout(30);
        this.beatDb = new NamedParameterJdbcTemplate(jt);
    }

    static LocalDateTime now() {
        return LocalDateTime.now(SEOUL);
    }

    /**
     * 살아 있음 표시(heartbeat_at)가 이만큼 끊긴 RUNNING 은 죽은 앱이 남긴 것으로 본다.
     * 도는 회차는 따로 도는 스레드가 HEARTBEAT_MINUTES 마다 표시한다. 요청이 재시도로 30분 걸려도 표시는 끊기지 않는다
     */
    static final int STALE_RUN_MINUTES = 60;
    static final int HEARTBEAT_MINUTES = 5;

    /**
     * 죽은 앱이 남긴 RUNNING 을 FAILED 로. 앱이 뜰 때와 회차를 시작할 때마다 부른다
     * (상시 실행 중에 컨테이너가 재시작되면, 뜰 때는 아직 끊긴 지 얼마 안 돼 남고 다음 날 04:00 에 정리된다).
     * 살아 있음 표시가 최근인 RUNNING 은 다른 곳(테스트 서버·PC)에서 지금 돌고 있는 회차라서 건드리지 않는다.
     */
    public int failLeftoverRuns() {
        return db.update("""
                UPDATE crawl_run SET status = 'FAILED', finished_at = :now,
                       note = SUBSTR('끝나지 않은 회차를 정리함 ' || note, 1, 500)
                 WHERE status = 'RUNNING'
                   AND NVL(heartbeat_at, started_at) < :now - NUMTODSINTERVAL(:minutes, 'MINUTE')
                """, new MapSqlParameterSource("now", now()).addValue("minutes", STALE_RUN_MINUTES));
    }

    /** 살아 있음 표시를 이만큼 못 적으면 멈춘다. 다른 곳이 이 회차를 죽은 것으로 보기(STALE_RUN_MINUTES) 전에 */
    static final int BEAT_LOST_MINUTES = 30;

    /**
     * 한 회차의 살아 있음 표시. 따로 도는 스레드가 HEARTBEAT_MINUTES 마다 적는다.
     * 0행이면 다른 곳(failLeftoverRuns)이 이 회차를 정리한 것이고, 오래 못 적었으면(DB 끊김·스레드가 죽음) 곧 정리될 것이라
     * 둘 다 checkAlive() 가 멈추게 한다(같이 돌면 사람인에 두 회차가 동시에 요청한다). 회차마다 새로 만든다.
     */
    static final class Beat {
        static final Beat NONE = new Beat(null);
        final ScheduledExecutorService ex;
        volatile boolean taken;
        volatile long lastOkMillis = System.currentTimeMillis();

        Beat(ScheduledExecutorService ex) {
            this.ex = ex;
        }

        void stop() {
            if (ex == null) return;
            ex.shutdown();
            try {
                if (!ex.awaitTermination(10, TimeUnit.SECONDS)) ex.shutdownNow();
            } catch (InterruptedException e) {
                ex.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private Beat startHeartbeat(long runId) {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crawl-heartbeat");
            t.setDaemon(true);
            return t;
        });
        Beat b = new Beat(ex);
        ex.scheduleWithFixedDelay(() -> {
            try {
                int n = beatDb.update("UPDATE crawl_run SET heartbeat_at = :now WHERE run_id = :id AND status = 'RUNNING'",
                        new MapSqlParameterSource("now", now()).addValue("id", runId));
                if (n == 0) b.taken = true;
                else b.lastOkMillis = System.currentTimeMillis();
            } catch (Throwable e) {                            // Error 로 죽으면 다음 표시가 조용히 멈추므로 다 잡는다
                log.warn("살아 있음 표시 실패: {}", e.toString());
            }
        }, HEARTBEAT_MINUTES, HEARTBEAT_MINUTES, TimeUnit.MINUTES);
        return b;
    }

    /** 다른 곳이 이 회차를 정리했거나 살아 있음 표시를 오래 못 적었으면 멈춘다. 쪽·공고마다 부른다. */
    private void checkAlive() {
        Beat b = beat;
        if (b.taken) throw new StopRun("다른 곳이 이 회차를 끝난 것으로 정리함 (살아 있음 표시가 끊겼던 것으로 보임)", null);
        if (b.ex != null && System.currentTimeMillis() - b.lastOkMillis > TimeUnit.MINUTES.toMillis(BEAT_LOST_MINUTES))
            throw new StopRun("살아 있음 표시를 " + BEAT_LOST_MINUTES + "분 넘게 못 적음 (DB 연결 확인)", null);
    }

    /** 단계가 끝날 때마다 건수를 적어 둔다. 앱이 중간에 죽어도 어디까지 했는지 남는다. */
    private void saveCounts(long runId, Counts c, String note) {
        db.update("""
                UPDATE crawl_run SET list_cnt = :list, new_cnt = :detail, closed_cnt = :closed, fail_cnt = :fail, note = :note,
                       heartbeat_at = :now
                 WHERE run_id = :id
                """, new MapSqlParameterSource("list", c.list).addValue("detail", c.detailOk)
                .addValue("closed", c.closed).addValue("fail", c.fail).addValue("now", now())
                .addValue("note", cut(runNote(c, note), 500), Types.VARCHAR).addValue("id", runId));
    }

    /** new_cnt 는 설계대로 "상세를 받은 공고"다. 목록에서 새로 만든 공고 수는 메모에 남긴다. */
    static String runNote(Counts c, String note) {
        String base = "새 공고 " + c.newPostings;
        if (c.skipped > 0) base += " · 제목·회사명 없어 건너뜀 " + c.skipped;
        if (c.deadlineUnknown > 0) base += " · 처음 보는 마감 표기 " + c.deadlineUnknown;
        return note == null ? base : base + " · " + note;
    }

    /** 한 회차. 이미 돌고 있으면 시작하지 않고 false. */
    public synchronized boolean run() {
        int left = failLeftoverRuns();
        if (left > 0) log.warn("끝나지 않은 수집 {}건을 FAILED 로 정리함", left);
        Integer running = db.queryForObject("SELECT COUNT(*) FROM crawl_run WHERE status = 'RUNNING'",
                new MapSqlParameterSource(), Integer.class);
        if (running != null && running > 0) {
            log.warn("수집이 이미 돌고 있어서 시작하지 않음");
            return false;
        }
        LocalDateTime runStart = now();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            // 위 COUNT 와 이 INSERT 사이에 다른 곳이 먼저 시작하면 RUNNING 유니크 인덱스(uk_run_running)가 막는다
            db.update("INSERT INTO crawl_run (started_at, heartbeat_at, status) VALUES (:start, :start, 'RUNNING')",
                    new MapSqlParameterSource("start", runStart), key, new String[] {"run_id"});
        } catch (DuplicateKeyException e) {
            log.warn("수집이 이미 돌고 있어서 시작하지 않음 (동시에 시작)");
            return false;
        }
        long runId = key.getKey().longValue();
        Beat beat = startHeartbeat(runId);
        this.beat = beat;
        log.info("수집 시작 #{} (직무 {}, 목록 한도 {}쪽, 상세 한도 {}건)", runId, props.jobs(),
                props.maxListPages(), props.maxDetails());

        Counts c = new Counts();
        boolean recorded = false;
        String status = "FAILED";                              // Error(OOM 등)로 빠져나가도 DONE 으로 남지 않게
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
            status = "DONE";
        } catch (StopRun e) {
            note = e.getMessage();
            log.error("수집 중단: {}", e.getMessage(), e.getCause());
        } catch (RuntimeException e) {
            note = e.toString();
            log.error("수집 실패", e);
        } finally {
            // 앱 종료(interrupt)로 빠져나왔으면 표시를 잠시 지운다. 남아 있으면 아래 DB 쓰기가 실패할 수 있다
            boolean interrupted = Thread.interrupted();
            beat.stop();
            this.beat = Beat.NONE;
            // 원문 지우기는 약관 문제라 중간에 멈춘 회차에서도 한다.
            // ④·⑤만 도는 회차(PC 에서 서버 DB 로 돌리기도 함)와 다른 곳이 정리한 회차(그쪽이 지금 돌 수 있음)는 건너뛴다
            if (!props.localOnly() && !beat.taken) {
                try {
                    deleteExpiredRaw();
                } catch (RuntimeException e) {
                    log.warn("raw 삭제 실패: {}", e.toString());
                }
            }
            try {
                // 건수와 끝 상태를 한 번에. 다른 곳이 이미 FAILED 로 정리한 회차는 덮지 않는다
                recorded = db.update("""
                        UPDATE crawl_run SET list_cnt = :list, new_cnt = :detail, closed_cnt = :closed, fail_cnt = :fail,
                               note = :note, finished_at = :now, status = :status
                         WHERE run_id = :id AND status = 'RUNNING'
                        """, new MapSqlParameterSource("list", c.list).addValue("detail", c.detailOk)
                        .addValue("closed", c.closed).addValue("fail", c.fail).addValue("now", now())
                        .addValue("note", cut(runNote(c, note), 500), Types.VARCHAR)
                        .addValue("status", status).addValue("id", runId)) == 1;
                if (!recorded) log.warn("회차 #{} 는 다른 곳이 이미 정리해서 끝 상태를 적지 않음", runId);
            } catch (RuntimeException e) {
                log.error("회차 #{} 끝 상태를 적지 못함 ({}분 뒤 다음 회차가 정리함): {}", runId, STALE_RUN_MINUTES, e.toString());
            }
            if (interrupted) Thread.currentThread().interrupt();
            log.info("수집 끝 #{} {}: 목록 {}건(새 공고 {}, 끝까지 받음 {}), 마감 {}, 상세 성공 {} 실패 {}, 기술 추출 {}건(raw 없음 {})",
                    runId, status, c.list, c.newPostings, complete, c.closed, c.detailOk, c.fail,
                    c.skillPostings, c.rawMissing);
        }
        return "DONE".equals(status) && recorded;
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
                // 총 건수보다 쪽이 많다(총 건수를 못 읽었으면 MAX_LIST_PAGES): 쪽 넘기기가 먹지 않는 것
                if (page > (total != null ? total / PAGE_SIZE + 3 : MAX_LIST_PAGES)) {
                    ok = false;
                    log.warn("직무 {} 쪽이 끝나지 않음 (총 {}건, {}쪽)", code, total, page);
                    break;
                }
                ListPage lp;
                try {
                    lp = listParser.parse(client.listPage(code, page));
                } catch (BlockedException e) {
                    throw new StopRun("목록 요청이 막힘: " + e.getMessage(), e);
                } catch (ParseFailedException e) {
                    // 총 건수가 100의 배수면 마지막 쪽 뒤에 빈 쪽을 한 번 더 받는다. 목록 칸이 없어도 끝난 것으로 본다.
                    // (총 건수로 미리 멈추지 않는 것은 쪽을 넘기는 사이 뒤로 밀린 공고를 받기 위해서다)
                    if (total != null && (page - 1) * PAGE_SIZE >= total) {
                        listFailsInRow = 0;
                        break;
                    }
                    log.warn("직무 {} 목록 {}쪽 실패: {}", code, page, e.toString());
                    ok = false;
                    if (++listFailsInRow >= MAX_LIST_FAILS_IN_ROW)
                        throw new StopRun("목록이 " + listFailsInRow + "번 연달아 실패", e);
                    break;
                } catch (IOException e) {
                    if (Thread.currentThread().isInterrupted()) throw new StopRun("앱이 끝나며 중단됨", e);
                    log.warn("직무 {} 목록 {}쪽 실패: {}", code, page, e.toString());
                    ok = false;
                    if (++listFailsInRow >= MAX_LIST_FAILS_IN_ROW)   // 직무마다 재시도하며 계속 두드리지 않게
                        throw new StopRun("목록이 " + listFailsInRow + "번 연달아 실패", e);
                    break;
                }
                listFailsInRow = 0;
                fetched++;
                checkAlive();
                if (total == null) total = lp.totalCount();
                LocalDateTime now = now();                     // 쪽마다: 자정을 넘겨도 D-N·내일마감이 맞게
                for (ListItem it : lp.items()) {
                    // 건너뛴 항목(새 공고인데 제목·회사명 없음)도 목록에는 있었으므로 본 것으로 센다.
                    // 이미 있는 공고는 saveListItem 이 last_seen_at 을 갱신하므로 잘못 마감되지 않는다
                    seen.add(it.postingId());
                    saveListItem(it, jobId, now, c);
                }
                // 거르기 전 칸 수로 본다: 광고 칸 하나를 걸렀다고 꽉 찬 쪽을 마지막 쪽으로 보지 않게
                if (lp.rawItemCount() < PAGE_SIZE) break;
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

    /** 새 수정으로 볼 최소 차이. 목록 표기가 버림이라 같은 수정도 회차마다 조금씩 다르게 계산된다 */
    static final Duration MIN_MODIFIED_GAP = Duration.ofHours(1);

    /** 이전 수정 시각과 이만큼 넘게 차이 나야 새 수정. "23시간 전"(어제) → "1일 전"(오늘)처럼 단위가 바뀌어도 같은 수정으로 본다 */
    static long modifiedToleranceMinutes(ListTexts.Posted posted) {
        Duration unit = posted == null || posted.precision() == null ? Duration.ZERO : posted.precision();
        return (unit.compareTo(MIN_MODIFIED_GAP) > 0 ? unit : MIN_MODIFIED_GAP).toMinutes();
    }

    private void saveListItem(ListItem it, int jobId, LocalDateTime now, Counts c) {
        // 오라클은 '' 를 NULL 로 넣어 NOT NULL 에 걸린다. 새 공고만 못 넣고, 이미 있는 공고는 본 것으로 갱신한다
        boolean blank = it.title().isBlank() || it.companyName().isBlank();
        ListTexts.Deadline dl = ListTexts.deadline(it.deadlineText(), now.toLocalDate());
        if (dl.unknown()) {
            c.deadlineUnknown++;
            log.info("처음 보는 마감 표기 {}: {}", it.postingId(), it.deadlineText());
        }
        ListTexts.Posted posted = ListTexts.posted(it.postedText(), now);
        LocalDateTime modifiedAt = posted != null && posted.modified() ? posted.at() : null;

        tx.executeWithoutResult(s -> {
            MapSqlParameterSource p = new MapSqlParameterSource("postingId", it.postingId())
                    .addValue("now", now)
                    .addValue("deadlineType", dl.type().name())
                    .addValue("unknown", dl.unknown() ? 1 : 0)
                    .addValue("listDeadline", dl.date(), Types.DATE)
                    .addValue("modifiedAt", modifiedAt, Types.TIMESTAMP)
                    .addValue("tolMinutes", modifiedToleranceMinutes(posted));
            // 이미 있는 공고: 목록이 정하는 칸만.
            // - 마감 표기를 못 읽으면(선택자가 바뀜 등) 마감 종류를 DATE 로 덮지 않고 그대로 둔다
            // - 목록의 마감일은 이미 있는 값보다 늦을 때만 쓴다(연장). 상세를 못 받은 공고도 '마감일 지남'으로 닫히게.
            //   날짜 마감이던 공고가 채용시·상시로 바뀌면 옛 마감일을 지운다(안 지우면 그 날짜에 잘못 닫힘)
            // - 새 수정(이전 값이 없거나 표기 단위·1시간 중 큰 것보다 뒤)일 때만 수정 시각·실패 횟수를 바꾼다
            int updated = db.update("""
                    UPDATE posting
                       SET last_seen_at     = :now,
                           deadline_type    = CASE WHEN :unknown = 1 THEN deadline_type ELSE :deadlineType END,
                           deadline_on      = CASE WHEN :unknown = 0 AND :deadlineType <> 'DATE' AND deadline_type = 'DATE'
                                                   THEN NULL
                                                   WHEN CAST(:listDeadline AS DATE) IS NOT NULL
                                                    AND (deadline_on IS NULL OR deadline_on < CAST(:listDeadline AS DATE))
                                                   THEN CAST(:listDeadline AS DATE) ELSE deadline_on END,
                           detail_fail_cnt  = CASE WHEN CAST(:modifiedAt AS TIMESTAMP) IS NOT NULL
                                                    AND (list_modified_at IS NULL
                                                         OR CAST(:modifiedAt AS TIMESTAMP) > list_modified_at + NUMTODSINTERVAL(:tolMinutes, 'MINUTE'))
                                                   THEN 0 ELSE detail_fail_cnt END,
                           list_modified_at = CASE WHEN CAST(:modifiedAt AS TIMESTAMP) IS NOT NULL
                                                    AND (list_modified_at IS NULL
                                                         OR CAST(:modifiedAt AS TIMESTAMP) > list_modified_at + NUMTODSINTERVAL(:tolMinutes, 'MINUTE'))
                                                   THEN CAST(:modifiedAt AS TIMESTAMP) ELSE list_modified_at END
                     WHERE posting_id = :postingId
                    """, p);
            if (updated == 0) {
                if (blank) {
                    c.skipped++;
                    log.warn("제목이나 회사명이 비어 새 공고를 넣지 못함: {}", it.postingId());
                    return;
                }
                p.addValue("companyId", companyId(it))
                        .addValue("title", cut(it.title(), 300))
                        .addValue("url", SaraminClient.BASE + "/zf_user/jobs/relay/view?rec_idx=" + it.postingId())
                        .addValue("headhunting", it.headhunting() ? 1 : 0);
                db.update("""
                        INSERT INTO posting (posting_id, company_id, title, url, deadline_type, deadline_on, is_headhunting,
                                             first_seen_at, last_seen_at, list_modified_at)
                        VALUES (:postingId, :companyId, :title, :url, :deadlineType, :listDeadline, :headhunting,
                                :now, :now, :modifiedAt)
                        """, p);
                c.newPostings++;
            }
            // (공고, 직무) 쌍은 새 공고든 아니든 매번. 오라클에는 INSERT IGNORE 가 없어서 MERGE.
            // last_seen_at 은 ②가 이 직무 목록에서 빠진 공고의 연결을 지울 때 쓴다
            db.update("""
                    MERGE INTO posting_job t
                    USING (SELECT :postingId AS posting_id, :jobId AS job_id FROM dual) s
                       ON (t.posting_id = s.posting_id AND t.job_id = s.job_id)
                     WHEN MATCHED THEN UPDATE SET t.last_seen_at = :now
                     WHEN NOT MATCHED THEN INSERT (posting_id, job_id, last_seen_at) VALUES (s.posting_id, s.job_id, :now)
                    """, new MapSqlParameterSource("postingId", it.postingId()).addValue("jobId", jobId).addValue("now", now));
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

    /** 지금 설정(crawler.jobs)에 있는 직무의 job_id. */
    private List<Integer> activeJobIds() {
        return db.queryForList("SELECT job_id FROM job WHERE saramin_code IN (:codes)",
                new MapSqlParameterSource("codes", props.jobs()), Integer.class);
    }

    /** crawl_run.note 에 남길 말이 있으면 돌려준다. */
    String closePostings(LocalDateTime runStart, Set<Integer> incompleteJobs, Counts c) {
        LocalDateTime now = now();
        List<Integer> active = activeJobIds();
        List<Integer> complete = active.stream().filter(j -> !incompleteJobs.contains(j)).toList();
        MapSqlParameterSource p = new MapSqlParameterSource("runStart", runStart)
                .addValue("now", now)
                .addValue("today", now.toLocalDate())
                // 빈 IN () 은 오라클 문법 오류라 -1
                .addValue("active", active.isEmpty() ? List.of(-1) : active)
                .addValue("complete", complete.isEmpty() ? List.of(-1) : complete)
                // 덜 받은 직무에 걸린 공고는 안 보여도 마감을 미룬다
                .addValue("incomplete", incompleteJobs.isEmpty() ? List.of(-1) : List.copyOf(incompleteJobs));
        // 앞 공백 필수: 텍스트 블록은 줄 끝 공백을 지워서 "AND" 와 붙는다
        String held = " NOT EXISTS (SELECT 1 FROM posting_job h WHERE h.posting_id = %s.posting_id AND h.job_id IN (:incomplete))";
        boolean anyComplete = !complete.isEmpty();
        String note = incompleteJobs.isEmpty() ? null : "덜 받은 직무 " + incompleteJobs.size() + "개는 마감을 미룸";
        // 다시 보인 공고는 되살린다(아래 연결 정리가 진행 중 공고만 보므로 먼저). raw 파일을 지운 뒤라면 detail_at 도 비워 ③에서 바로 다시 받는다
        int revived = db.update("""
                UPDATE posting
                   SET is_closed       = 0,
                       closed_at       = NULL,
                       detail_at       = CASE WHEN raw_deleted_at IS NOT NULL THEN NULL ELSE detail_at END,
                       detail_fail_cnt = CASE WHEN raw_deleted_at IS NOT NULL THEN 0 ELSE detail_fail_cnt END,
                       raw_deleted_at  = NULL
                 WHERE is_closed = 1 AND last_seen_at >= :runStart
                """, p);
        // 마감일이 지났고 이번 목록에 없는 공고: 목록을 다 못 받은 날에도 닫는다
        c.closed += db.update("""
                UPDATE posting SET is_closed = 1, closed_at = :now
                 WHERE is_closed = 0 AND last_seen_at < :runStart AND deadline_on < :today
                """, p);
        if (anyComplete) {
            // 10% 검사는 끝까지 받은 '설정에 있는' 직무의 공고로만 한다.
            // 설정에서 뺀 직무의 공고는 매일 100% 안 보이므로, 세면 마감 처리가 영영 꺼진다(그 공고는 아래 UPDATE 로 닫힘)
            Map<String, Object> m = db.queryForMap("""
                    SELECT NVL(SUM(CASE WHEN last_seen_at < :runStart AND (deadline_on IS NULL OR deadline_on >= :today)
                                        THEN 1 ELSE 0 END), 0) AS missing,
                           COUNT(*) AS open_before
                      FROM posting p
                     WHERE is_closed = 0 AND first_seen_at < :runStart
                       AND EXISTS (SELECT 1 FROM posting_job a WHERE a.posting_id = p.posting_id AND a.job_id IN (:complete))
                       AND""" + held.formatted("p"), p);
            long missing = ((Number) m.get("MISSING")).longValue();
            long openBefore = ((Number) m.get("OPEN_BEFORE")).longValue();
            // 직무가 많으면 작은 직무 하나가 통째로 빠져도 전체 10%를 넘지 않는다 → 직무마다도 본다
            List<Map<String, Object>> perJob = db.queryForList("""
                    SELECT j.name,
                           NVL(SUM(CASE WHEN p.last_seen_at < :runStart AND (p.deadline_on IS NULL OR p.deadline_on >= :today)
                                        THEN 1 ELSE 0 END), 0) AS missing,
                           COUNT(*) AS open_before
                      FROM posting p JOIN posting_job pj ON pj.posting_id = p.posting_id JOIN job j ON j.job_id = pj.job_id
                     WHERE p.is_closed = 0 AND p.first_seen_at < :runStart AND j.job_id IN (:complete)
                       AND""" + held.formatted("p") + """

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
                         WHERE is_closed = 0 AND last_seen_at < :runStart AND""" + held.formatted("p"), p);
                note = unlinkMovedPostings(p, note);
            }
        }
        // 설정에서 뺀 직무와의 연결: 설정에 있는 직무에도 걸린 진행 중 공고만 끊는다(통계·k 에서 빠지게)
        int removedLinks = db.update("""
                DELETE FROM posting_job pj
                 WHERE pj.job_id NOT IN (:active)
                   AND EXISTS (SELECT 1 FROM posting p WHERE p.posting_id = pj.posting_id AND p.is_closed = 0)
                   AND EXISTS (SELECT 1 FROM posting_job k WHERE k.posting_id = pj.posting_id AND k.job_id IN (:active))
                """, p);
        if (removedLinks > 0) log.info("② 설정에서 뺀 직무와의 연결 {}건을 끊음", removedLinks);
        log.info("② 마감 {}건, 되살림 {}건{}", c.closed, revived,
                incompleteJobs.isEmpty() ? "" : anyComplete ? " (덜 받은 직무 " + incompleteJobs.size() + "개는 마감일 지난 것만 닫음)"
                        : " (목록을 덜 받아 마감일 지난 것만 닫음)");
        return note;
    }

    /**
     * 끝까지 받은 직무의 목록에서 빠졌지만 다른 직무 목록에는 아직 있는 공고: 그 직무와의 연결을 끊는다.
     * 안 끊으면 회사가 직무를 고친 공고가 마감될 때까지 옛 직무에 남아 job_total·k·'이 직무만'이 틀어진다.
     * 마감된 공고의 연결은 기록으로 남긴다. 경계에서 놓친 1~2건(LIST_TOLERANCE)은 다음 회차 MERGE 가 다시 잇는다.
     * 한 직무에서 끊을 연결이 10%를 넘으면(사람인 직무 분류·정렬이 바뀜 등) 그 직무는 건너뛴다.
     */
    private String unlinkMovedPostings(MapSqlParameterSource p, String note) {
        String stale = "NVL(pj.last_seen_at, TIMESTAMP '2000-01-01 00:00:00') < :runStart AND p.last_seen_at >= :runStart";
        List<Map<String, Object>> perJob = db.queryForList("""
                SELECT j.job_id, j.name,
                       NVL(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS stale,
                       COUNT(*) AS links
                  FROM posting_job pj JOIN posting p ON p.posting_id = pj.posting_id JOIN job j ON j.job_id = pj.job_id
                 WHERE pj.job_id IN (:complete) AND p.is_closed = 0
                 GROUP BY j.job_id, j.name
                """.formatted(stale), p);
        List<Integer> safe = new ArrayList<>();
        for (Map<String, Object> r : perJob) {
            long n = ((Number) r.get("STALE")).longValue();
            long links = ((Number) r.get("LINKS")).longValue();
            if (n == 0) continue;
            if (links >= 20 && n * 10 > links) {
                note = join(note, "직무 " + r.get("NAME") + " 연결 " + n + "/" + links + "건이 10%를 넘어 연결 정리를 건너뜀");
                log.warn("② {}", note);
            } else {
                safe.add(((Number) r.get("JOB_ID")).intValue());
            }
        }
        if (safe.isEmpty()) return note;
        int unlinked = db.update("""
                DELETE FROM posting_job pj
                 WHERE pj.job_id IN (:safe)
                   AND NVL(pj.last_seen_at, TIMESTAMP '2000-01-01 00:00:00') < :runStart
                   AND EXISTS (SELECT 1 FROM posting p
                                WHERE p.posting_id = pj.posting_id AND p.is_closed = 0 AND p.last_seen_at >= :runStart)
                """, new MapSqlParameterSource(p.getValues()).addValue("safe", safe));
        log.info("② 직무 목록에서 빠진 공고-직무 연결 {}건을 끊음", unlinked);
        return note;
    }

    /**
     * 마감 뒤 raw-keep-days 가 지난 공고의 원문을 지운다. 지운 공고는 raw_deleted_at 을 적어 다음부터 다시 보지 않는다.
     * 원본 폴더가 안 보이면(볼륨이 안 붙음·다른 PC) 지운 표시만 남고 실제 파일은 영영 안 지워지므로 아무것도 하지 않는다.
     */
    void deleteExpiredRaw() {
        if (!rawStore.looksMounted()) {
            log.warn("원본(raw) 폴더가 비었거나 없어 raw 삭제를 건너뜀: {}", props.rawDir());
            return;
        }
        LocalDateTime now = now();
        MapSqlParameterSource p = new MapSqlParameterSource("now", now).addValue("keepDays", props.rawKeepDays());
        Map<Long, Boolean> expired = new HashMap<>();          // 공고 → 파일이 없어도 끝난 것으로 볼 만큼 오래됨
        db.query("""
                SELECT posting_id,
                       CASE WHEN closed_at < :now - NUMTODSINTERVAL(:keepDays + :graceDays, 'DAY') THEN 1 ELSE 0 END AS old_enough
                  FROM posting
                 WHERE is_closed = 1 AND raw_deleted_at IS NULL AND closed_at < :now - NUMTODSINTERVAL(:keepDays, 'DAY')
                """, p.addValue("graceDays", RAW_ABSENT_GRACE_DAYS), rs -> {
            expired.put(rs.getLong(1), rs.getInt(2) == 1);
        });
        List<MapSqlParameterSource> done = new ArrayList<>();
        int deleted = 0;
        for (Map.Entry<Long, Boolean> e : expired.entrySet()) {
            long id = e.getKey();
            try {
                // 지웠을 때만 끝난 것으로 적는다. 파일이 없으면 이 폴더가 다른 곳(PC)의 것일 수 있어서
                // RAW_ABSENT_GRACE_DAYS 동안은 회차마다 다시 보고, 그 뒤에야 원래 없던 것으로 본다
                if (rawStore.delete(id)) {
                    deleted++;
                    done.add(new MapSqlParameterSource("id", id).addValue("now", now));
                } else if (e.getValue()) {
                    done.add(new MapSqlParameterSource("id", id).addValue("now", now));
                }
            } catch (IOException ex) {
                log.warn("raw 파일 삭제 실패 {}: {}", id, ex.toString());             // 다음 회차에 다시
            }
        }
        if (!done.isEmpty())
            db.batchUpdate("UPDATE posting SET raw_deleted_at = :now WHERE posting_id = :id AND is_closed = 1 AND raw_deleted_at IS NULL",
                    done.toArray(MapSqlParameterSource[]::new));
        int tmp = rawStore.sweepTemp(java.time.Duration.ofDays(1));   // 쓰다가 앱이 죽어 남은 임시 파일
        if (!expired.isEmpty() || tmp > 0) log.info("raw 삭제 {}건 (대상 {}건), 임시 파일 {}건", deleted, expired.size(), tmp);
    }

    /** 마감 뒤 raw-keep-days 가 지나고도 이만큼은 파일이 없어도 회차마다 다시 확인한다 */
    static final int RAW_ABSENT_GRACE_DAYS = 30;

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
        int probes = 0;
        // 공고의 실패 횟수(3번이면 쉼)에는 그 공고 탓인 실패(형식 다름·4xx 등)를 바로 넣는다.
        // 시간 초과·5xx·raw 저장 실패처럼 지나가는 실패는 사이트가 멀쩡했다는 것이 확인될 때(뒤 공고를 받음, 목록 확인)만 넣는다.
        // 장애 동안 맨 앞(최신) 공고들이 다 쉬게 되지 않고, 늘 5xx 인 공고는 결국 쉬어서 매일 같은 자리에서 ③이 멈추지 않는다
        List<Long> streak = new ArrayList<>();
        for (long id : targets) {
            checkAlive();
            try {
                PostingDetail d = parseDetail(client.detail(id));
                rawStore.save(id, new RawStore.Raw(d.tags(), d.required(), d.preferred()));
                saveDetail(id, d);
                c.detailOk++;
                consecutive = 0;
                addDetailFailures(streak);                     // 바로 뒤 공고는 받았다 = 앞의 실패는 그 공고 탓
                streak.clear();
            } catch (BlockedException e) {
                throw new StopRun("상세 요청이 막힘: " + e.getMessage(), e);
            } catch (ParseFailedException | IOException e) {
                if (Thread.currentThread().isInterrupted())   // 앱 종료: 공고 탓이 아니다
                    throw new StopRun("앱이 끝나며 중단됨", e);
                c.fail++;
                if (postingsFault(e)) addDetailFailures(List.of(id));
                else streak.add(id);
                log.warn("상세 실패 {}: {}", id, e.toString());
                if (++consecutive >= props.maxConsecutiveFailures()) {
                    // 사이트가 죽었는지 이 공고들만 이상한지 목록 1쪽으로 확인한다(요청 1번, 회차에 2번까지)
                    if (probes < MAX_DETAIL_PROBES && siteIsUp()) {
                        probes++;
                        log.warn("③ 상세가 {}번 연달아 실패했지만 목록은 받아짐: 그 공고들의 실패 횟수를 올리고 계속", consecutive);
                        addDetailFailures(streak);
                        streak.clear();
                        consecutive = 0;
                    } else {
                        throw new StopRun("상세가 " + consecutive + "번 연달아 실패", e);
                    }
                }
            }
            if (c.detailOk > 0 && c.detailOk % 100 == 0) {
                log.info("③ {}건 받음", c.detailOk);
                saveCounts(runId, c, "③ 상세 받는 중" + (note == null ? "" : " · " + note));
            }
        }
    }

    static final int MAX_DETAIL_PROBES = 2;

    private void addDetailFailures(List<Long> ids) {
        if (ids.isEmpty()) return;
        db.batchUpdate("UPDATE posting SET detail_fail_cnt = detail_fail_cnt + 1 WHERE posting_id = :id",
                ids.stream().map(id -> new MapSqlParameterSource("id", id)).toArray(MapSqlParameterSource[]::new));
    }

    /** 상세가 연달아 실패할 때 사람인이 살아 있는지 목록 1쪽으로 본다. 막혔으면 바로 멈춘다. */
    private boolean siteIsUp() {
        try {
            listParser.parse(client.listPage(props.jobs().get(0), 1));
            return true;
        } catch (BlockedException e) {
            throw new StopRun("목록 확인 요청이 막힘: " + e.getMessage(), e);
        } catch (IOException | ParseFailedException e) {
            return false;
        }
    }

    /** 파서 안의 예상 못 한 RuntimeException 도 그 공고 하나의 실패로 본다(회차 전체가 FAILED 가 되지 않게). */
    private PostingDetail parseDetail(Document doc) {
        try {
            return detailParser.parse(doc);
        } catch (ParseFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ParseFailedException("상세 파싱 중 오류: " + e);
        }
    }

    /** 다시 받아도 같을 실패: 화면 형식이 다름, 4xx(429 제외), HTML 이 아닌 응답, 너무 큰 본문. */
    static boolean postingsFault(Exception e) {
        if (e instanceof ParseFailedException) return true;
        if (e instanceof HttpStatusException h) return h.getStatusCode() >= 400 && h.getStatusCode() < 500 && h.getStatusCode() != 429;
        return e instanceof UnsupportedMimeTypeException || e instanceof SaraminClient.TooLargeException;
    }

    private void saveDetail(long id, PostingDetail d) {
        ListTexts.Career career = ListTexts.career(d.summaryValue("경력"));
        db.update("""
                UPDATE posting
                   SET career_type = :careerType, exp_min = :expMin, exp_max = :expMax,
                       education = :education, employment_type = :employmentType, region = :region,
                       posted_on = :postedOn, deadline_on = NVL(CAST(:deadlineOn AS DATE), deadline_on),
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
        db.query("SELECT saramin_code, skill_id FROM skill WHERE saramin_code IS NOT NULL AND is_active = 1",   // CSV 에서 뺀 기술은 태그로도 안 셈
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
        // 원본 폴더가 안 보이거나 일부만 있으면(다른 PC 의 폴더 등) 많은 공고가 '원본 없음'이 되어 detail_at 이 지워지고
        // 다음 ③이 수천 건을 사람인에 다시 요청한다 → DB 를 바꾸기 전에 멈춘다
        if (!targets.isEmpty() && !rawStore.looksMounted())
            throw new StopRun("원본(raw) 폴더가 비었거나 없음: " + props.rawDir(), null);
        long missing = targets.keySet().stream().filter(id -> !rawStore.exists(id)).count();
        if (missing >= 20 && missing * 10 > targets.size())
            throw new StopRun("원본(raw)이 없는 공고가 " + missing + "/" + targets.size() + "건이라 멈춤 (raw 폴더 위치 확인): "
                    + props.rawDir(), null);
        int done = 0;
        for (Map.Entry<Long, String> target : targets.entrySet()) {
            if (++done % 200 == 0) checkAlive();
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
            // SSD·스토리지 검증 공고의 SAS(인터페이스)는 통계 SAS 가 아니다: 공고 전체 글을 보고 뺀다
            List<String> lines = new ArrayList<>(raw.get().required());
            lines.addAll(raw.get().preferred());
            Set<Integer> required = matcher.dropByContext(matcher.match(raw.get().required()), lines);
            Set<Integer> preferred = matcher.dropByContext(matcher.match(raw.get().preferred()), lines);
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
            "백엔드", "backend", "풀스택", "full\\s*stack", "퍼블리셔",
            "데이터\\s*(분석|엔지니어)", "데이터베이스", "(?<![a-z])dba(?![a-z])",
            "(앱|웹|ai|java|자바|python|파이썬)\\s*개발"));

    /**
     * 넓은 말만 있는 제목('배터리 공정 개발자', '식품 개발자'). 이것만으로는 바로 살리지 않고,
     * 자격요건·우대 글에서 기술이 하나라도 잡혔을 때만 비개발 판정에서 살린다(반도체 설계 판정에서는 지금처럼 살림)
     */
    static final Pattern BROAD_DEV_TITLE = Pattern.compile("개발자|developer|프로그래머|programmer");

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
     * 태그가 비IT 위주인 SW 개발 공고였다). '프론트엔드'는 반도체 '프론트엔드 설계'와 겹쳐서 넣지 않았다.
     * '개발자'·'developer' 처럼 넓은 말만 있는 제목은 글에서 기술이 하나라도 잡혀야 비개발 판정에서 살린다(10/1 검수)
     */
    static boolean excluded(String title, List<Tag> tags, Set<Integer> itCodes, Set<Integer> required, Set<Integer> preferred) {
        boolean broadTitle = false;
        if (title != null) {
            String t = SkillMatcher.normalize(title);
            if (!NOT_DEV_TITLE.matcher(t).find()) {
                if (DEV_TITLE.matcher(t).find()) return false;
                broadTitle = BROAD_DEV_TITLE.matcher(t).find();
            }
        }
        long nonIt = tags.stream().filter(t -> !itCodes.contains(t.code())).count();
        Set<Integer> text = new HashSet<>(required);
        text.addAll(preferred);
        boolean nonDev = !tags.isEmpty() && nonIt * 2 > tags.size() && text.size() < 2;
        if (broadTitle) return nonDev && text.isEmpty();
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
