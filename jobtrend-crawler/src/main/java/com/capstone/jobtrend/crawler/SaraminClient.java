package com.capstone.jobtrend.crawler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;

/**
 * 사람인에 보내는 요청은 모두 이 클래스를 지난다.
 * 요청 사이 1~2초, 429·5xx·시간 초과·연결 끊김은 5초 → 15초 → 45초 뒤 다시 보낸다(Retry-After 가 더 길면 그만큼).
 * 403·다른 주소로 돌려보냄은 막힌 것으로 보고 BlockedException 을 던진다. 부르는 쪽은 그 자리에서 수집을 멈춘다.
 */
public class SaraminClient {

    static {   // jsoup 기본값(JDK HttpClient)은 본문이 멈추면 시간제한이 걸리지 않는다
        System.setProperty("jsoup.useHttpClient", "false");
    }

    public static final String BASE = "https://www.saramin.co.kr";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    private static final long[] RETRY_WAIT_MS = {5_000, 15_000, 45_000};
    private static final int MAX_BODY_BYTES = 20 * 1024 * 1024;   // 목록 한 쪽이 2.7MB, 공고는 2.2MB 뒤에 나온다

    /** 403 이나 로그인·차단 화면으로 돌려보낸 경우. 다시 보내지 않고 수집을 멈춘다. */
    public static class BlockedException extends IOException {
        public BlockedException(String message) {
            super(message);
        }
    }

    private final long minGapMs;
    private final long maxGapMs;
    // 쿠키를 이어 쓰고 기본 헤더를 한곳에 둔다. 요청은 send 가 한 번에 하나씩만 보낸다
    private final Connection session = Jsoup.newSession()
            .userAgent(USER_AGENT)
            .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
            .timeout(10_000)                                   // 헤더 대기 5초, 본문 끝까지 10초
            .maxBodySize(MAX_BODY_BYTES)                       // 기본 2MB 는 넘는 부분을 오류 없이 자른다
            .followRedirects(false)                            // 돌려보내면 로그인·차단 화면이므로 따라가지 않는다
            .ignoreHttpErrors(true);                           // 상태 코드는 send 가 직접 본다

    public SaraminClient() {
        this(1_000, 2_000);
    }

    public SaraminClient(long minGapMs, long maxGapMs) {
        this.minGapMs = minGapMs;
        this.maxGapMs = maxGapMs;
    }

    /** 목록 한 쪽. */
    public Document listPage(int jobCode, int page) throws IOException {
        // sort=RD 는 9/23 수집 때 붙인 값 그대로(설계 문서). 뜻은 확인 안 함
        return get("/zf_user/jobs/list/job-category?cat_kewd=" + jobCode + "&page=" + page + "&page_count=100&sort=RD");
    }

    /** 공고 하나의 요약·자격요건·우대사항·태그·접수 기간. 화면이 부르는 view-ajax 를 그대로 부른다. */
    public Document detail(long postingId) throws IOException {
        return send(session.newRequest(BASE + "/zf_user/jobs/relay/view-ajax")
                .method(Connection.Method.POST)
                .header("X-Requested-With", "XMLHttpRequest")
                .referrer(BASE + "/zf_user/jobs/relay/view?rec_idx=" + postingId)
                .data(Map.of("rec_idx", String.valueOf(postingId), "rec_seq", "0", "view_type", "list")));
    }

    public Document get(String path) throws IOException {
        return send(session.newRequest(BASE + path).method(Connection.Method.GET));
    }

    // synchronized: 여러 스레드에서 불러도 요청 간격이 지켜지게
    private synchronized Document send(Connection conn) throws IOException {
        sleep(minGapMs + ThreadLocalRandom.current().nextLong(maxGapMs - minGapMs + 1));
        for (int attempt = 0; ; attempt++) {
            long wait;
            try {
                Connection.Response res = conn.execute();
                int status = res.statusCode();
                if (status == 200) {
                    if (res.bodyAsBytes().length >= MAX_BODY_BYTES)
                        throw new IOException("본문이 " + MAX_BODY_BYTES + "바이트 제한에 걸려 잘림");
                    return res.parse();
                }
                if (status == 403 || (status >= 300 && status < 400))
                    throw new BlockedException("HTTP " + status + (res.hasHeader("Location") ? " → " + res.header("Location") : ""));
                HttpStatusException e = new HttpStatusException("HTTP " + status, status, res.url().toString());
                // 재시도를 다 써도 429 면 사람인이 속도를 줄이라는 것이다. 다음 공고로 넘어가지 않고 회차를 멈춘다
                if (status == 429 && attempt == RETRY_WAIT_MS.length)
                    throw new BlockedException("HTTP 429 (요청이 너무 많음) — 재시도 " + RETRY_WAIT_MS.length + "번 뒤에도 계속");
                if (!retryable(e) || attempt == RETRY_WAIT_MS.length) throw e;
                wait = Math.max(RETRY_WAIT_MS[attempt], retryAfterMs(res.header("Retry-After")));
            } catch (BlockedException | HttpStatusException e) {
                throw e;                                       // 위에서 이미 다시 보낼지 정했다
            } catch (IOException e) {                          // 시간 초과·연결 끊김
                if (!retryable(e) || attempt == RETRY_WAIT_MS.length) throw e;
                wait = RETRY_WAIT_MS[attempt];
            }
            sleep(wait);
        }
    }

    static boolean retryable(IOException e) {
        if (e instanceof BlockedException) return false;
        if (e instanceof HttpStatusException h)              // 429·5xx만 다시, 다른 4xx는 바로 실패
            return h.getStatusCode() == 429 || h.getStatusCode() >= 500;
        return !(e instanceof UnsupportedMimeTypeException); // HTML이 아닌 응답은 다시 받아도 같다
    }

    /** Retry-After 초 단위만 본다. 날짜 형식이거나 없으면 0. 너무 길면 10분으로 자른다. */
    static long retryAfterMs(String value) {
        if (value == null || !value.trim().matches("\\d{1,6}")) return 0;
        return Math.min(Long.parseLong(value.trim()) * 1000, 600_000);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청 대기 중 중단됨", e);
        }
    }
}
