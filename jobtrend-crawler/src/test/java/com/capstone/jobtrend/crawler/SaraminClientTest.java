package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.HttpStatusException;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.capstone.jobtrend.crawler.SaraminClient.BlockedException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/** 로컬 서버로 상태 코드·본문 끊김마다 다시 보내는지, 멈추는지. 사람인에는 요청하지 않는다. */
class SaraminClientTest {

    private static final long[] WAITS = {10, 20, 40};   // 재시도 3번
    private static final String HTML = "<html><body><p id=x>안녕</p></body></html>";

    private final AtomicInteger hits = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);   // 본문을 멈춘 처리기를 끝낼 때
    private HttpServer server;
    private ExecutorService pool;

    @AfterEach
    void stop() {
        release.countDown();
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    // 시험용 시간제한. 느린 CI 에서도 정상 응답이 시간 초과로 재시도되지 않게 넉넉히(운영 값 10초와는 따로)
    private static final int TIMEOUT_MS = 5_000;
    private static final long HOLD_SECONDS = 60;          // 본문을 멈춘 처리기가 기다리는 최대 시간(끝나면 release 로 푼다)

    private SaraminClient serve(HttpHandler h) throws IOException {
        return serve(h, TIMEOUT_MS);
    }

    private SaraminClient serve(HttpHandler h, int timeoutMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newCachedThreadPool();                     // 멈춘 처리기가 있어도 다음 요청을 받게
        server.setExecutor(pool);
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            try {
                h.handle(ex);
            } finally {
                ex.close();
            }
        });
        server.start();
        return new SaraminClient("http://127.0.0.1:" + server.getAddress().getPort(), 0, 0, WAITS, timeoutMs);
    }

    private static void reply(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        if (type != null) ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) ex.getResponseBody().write(b);
    }

    private static void html(HttpExchange ex) throws IOException {
        reply(ex, 200, "text/html; charset=utf-8", HTML);
    }

    @Test
    void 정상_HTML을_읽는다() throws IOException {
        SaraminClient c = serve(SaraminClientTest::html);
        Document d = c.get("/a");
        assertThat(d.selectFirst("#x").text()).isEqualTo("안녕");
        assertThat(hits).hasValue(1);
    }

    @Test
    void 상세는_view_ajax_에_POST_로_보낸다() throws IOException {
        StringBuilder seen = new StringBuilder();
        SaraminClient c = serve(ex -> {
            seen.append(ex.getRequestMethod()).append(' ').append(ex.getRequestURI().getPath()).append(' ')
                    .append(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).contains("rec_idx=123"));
            html(ex);
        });
        c.detail(123);
        assertThat(seen).hasToString("POST /zf_user/jobs/relay/view-ajax true");
    }

    @Test
    void 막힘_403은_다시_보내지_않는다() throws IOException {
        SaraminClient c = serve(ex -> reply(ex, 403, "text/html", "forbidden"));
        assertThatThrownBy(() -> c.get("/a")).isInstanceOf(BlockedException.class).hasMessageContaining("403");
        assertThat(hits).hasValue(1);
    }

    @Test
    void 막힘_JSON_403도_막힘으로_본다() throws IOException {
        SaraminClient c = serve(ex -> reply(ex, 403, "application/json", "{\"error\":\"blocked\"}"));
        assertThatThrownBy(() -> c.get("/a")).isInstanceOf(BlockedException.class);
        assertThat(hits).hasValue(1);
    }

    @Test
    void 돌려보냄_302는_막힘이다() throws IOException {
        SaraminClient c = serve(ex -> {
            ex.getResponseHeaders().set("Location", "/login");
            reply(ex, 302, null, "");
        });
        assertThatThrownBy(() -> c.get("/a")).isInstanceOf(BlockedException.class).hasMessageContaining("/login");
        assertThat(hits).hasValue(1);
    }

    @Test
    void 요청과다_429_뒤_200이면_받는다() throws IOException {
        SaraminClient c = serve(ex -> {
            if (hits.get() == 1) {
                ex.getResponseHeaders().set("Retry-After", "0");
                reply(ex, 429, "application/json", "{}");
            } else html(ex);
        });
        assertThat(c.get("/a").selectFirst("#x").text()).isEqualTo("안녕");
        assertThat(hits).hasValue(2);
    }

    @Test
    void 요청과다_429가_계속되면_막힘() throws IOException {
        SaraminClient c = serve(ex -> reply(ex, 429, "text/html", "slow down"));
        assertThatThrownBy(() -> c.get("/a")).isInstanceOf(BlockedException.class).hasMessageContaining("429");
        assertThat(hits).hasValue(1 + WAITS.length);
    }

    @Test
    void 서버오류_5xx_뒤_200이면_받는다() throws IOException {
        SaraminClient c = serve(ex -> {
            if (hits.get() <= 2) reply(ex, hits.get() == 1 ? 500 : 503, "text/html", "oops");
            else html(ex);
        });
        assertThat(c.get("/a").selectFirst("#x").text()).isEqualTo("안녕");
        assertThat(hits).hasValue(3);
    }

    @Test
    void 서버오류가_계속되면_HttpStatusException() throws IOException {
        SaraminClient c = serve(ex -> reply(ex, 502, "text/html", "bad gateway"));
        assertThatThrownBy(() -> c.get("/a")).isExactlyInstanceOf(HttpStatusException.class);
        assertThat(hits).hasValue(1 + WAITS.length);
    }

    @Test
    void 없음_404는_다시_보내지_않는다() throws IOException {
        SaraminClient c = serve(ex -> reply(ex, 404, "text/html", "not found"));
        assertThatThrownBy(() -> c.get("/a")).isExactlyInstanceOf(HttpStatusException.class)
                .satisfies(e -> assertThat(((HttpStatusException) e).getStatusCode()).isEqualTo(404));
        assertThat(hits).hasValue(1);
    }

    @Test
    void 본문이_멈추면_다시_보내고_끝내_IOException() throws IOException {
        SaraminClient c = serve(ex -> {
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, 100_000);                   // 헤더만 보내고
            OutputStream out = ex.getResponseBody();
            out.write("<html><body>".getBytes(StandardCharsets.UTF_8));
            out.flush();
            try {
                release.await(HOLD_SECONDS, TimeUnit.SECONDS);      // 본문은 멈춘다
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 1_000);                                                  // 4번 × 1초. 처리기는 그보다 오래 멈춰 있다
        Throwable t = catchThrowable(() -> c.get("/a"));
        assertThat(t).isInstanceOf(IOException.class).isNotInstanceOf(BlockedException.class);
        assertThat(t).isNotInstanceOf(UncheckedIOException.class);
        assertThat(hits).hasValue(1 + WAITS.length);
    }

    @Test
    void 본문이_한번_멈춰도_다시_받으면_된다() throws IOException {
        SaraminClient c = serve(ex -> {
            if (hits.get() > 1) {
                html(ex);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "text/html");
            ex.sendResponseHeaders(200, 100_000);
            ex.getResponseBody().flush();
            try {
                release.await(HOLD_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 2_000);                                                  // 느린 CI 에서도 두 번째 요청이 시간 안에 끝나게
        assertThat(c.get("/a").selectFirst("#x").text()).isEqualTo("안녕");
        assertThat(hits.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void 본문을_읽다_중단되면_잘린_본문을_넘기지_않고_다시_보내지도_않는다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        SaraminClient c = serve(ex -> {
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, 1_000_000);
            OutputStream out = ex.getResponseBody();
            byte[] chunk = "<p>조금씩</p>".repeat(50).getBytes(StandardCharsets.UTF_8);
            try {
                out.write("<html><body>".getBytes(StandardCharsets.UTF_8));
                // 본문을 천천히 흘린다(최대 약 20초, 800KB < 길이 1,000,000). 끊기 전에 다 보내 버리면 시험이 무의미해진다
                for (int i = 0; i < 1_000 && release.getCount() > 0; i++) {
                    out.write(chunk);
                    out.flush();
                    started.countDown();
                    Thread.sleep(20);
                }
            } catch (IOException | InterruptedException ignored) {        // 받는 쪽이 끊음
            }
        }, 30_000);
        Thread caller = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            try {
                started.await(HOLD_SECONDS, TimeUnit.SECONDS);
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            caller.interrupt();
        });
        interrupter.start();
        Throwable t;
        try {
            t = catchThrowable(() -> c.get("/a"));
        } finally {
            interrupter.join();
            Thread.interrupted();                                       // 다음 시험에 남기지 않는다
        }
        assertThat(t).isExactlyInstanceOf(InterruptedIOException.class);
        assertThat(hits).hasValue(1);
        assertThat(SaraminClient.retryable(new InterruptedIOException())).isFalse();
        assertThat(SaraminClient.retryable(new SocketTimeoutException())).isTrue();
    }

    @Test
    void HTML이_아닌_200은_다시_보내지_않고_실패() throws IOException {
        SaraminClient c = serve(ex -> reply(ex, 200, "application/json", "{\"a\":1}"));
        assertThatThrownBy(() -> c.get("/a")).isInstanceOf(UnsupportedMimeTypeException.class);
        assertThat(hits).hasValue(1);
    }

    @Test
    void XML_200은_받는다() throws IOException {
        SaraminClient c = serve(ex -> reply(ex, 200, "application/xhtml+xml", "<html><p id=\"x\">a</p></html>"));
        assertThat(c.get("/a").getElementById("x").text()).isEqualTo("a");
    }

    @Test
    void 제한_20MB에_걸린_본문은_다시_보내지_않는다() throws IOException {
        byte[] big = new byte[21 * 1024 * 1024];
        Arrays.fill(big, (byte) 'a');
        SaraminClient c = serve(ex -> {
            ex.getResponseHeaders().set("Content-Type", "text/html");
            ex.sendResponseHeaders(200, big.length);
            try {
                ex.getResponseBody().write(big);
            } catch (IOException ignored) {                        // 받는 쪽이 20MB 에서 끊는다
            }
        }, 60_000);                                                 // 21MB 를 느린 CI 에서 받아도 시간 초과(재시도)가 아니라 크기 제한에 걸리게
        assertThatThrownBy(() -> c.get("/a")).isInstanceOf(SaraminClient.TooLargeException.class);
        assertThat(hits).hasValue(1);
    }

    @Test
    void 다시_보낼지_판단() {
        assertThat(SaraminClient.retryable(new SocketTimeoutException())).isTrue();
        assertThat(SaraminClient.retryable(new HttpStatusException("x", 429, "u"))).isTrue();
        assertThat(SaraminClient.retryable(new HttpStatusException("x", 503, "u"))).isTrue();
        assertThat(SaraminClient.retryable(new HttpStatusException("x", 404, "u"))).isFalse();
        assertThat(SaraminClient.retryable(new BlockedException("x"))).isFalse();
        assertThat(SaraminClient.retryable(new SaraminClient.TooLargeException("x"))).isFalse();
        assertThat(SaraminClient.retryable(new UnsupportedMimeTypeException("x", "application/json", "u"))).isFalse();
    }

    @Test
    void Retry_After_는_초_단위만_본다() {
        assertThat(SaraminClient.retryAfterMs(null)).isZero();
        assertThat(SaraminClient.retryAfterMs("")).isZero();
        assertThat(SaraminClient.retryAfterMs("120")).isEqualTo(120_000);
        assertThat(SaraminClient.retryAfterMs(" 3 ")).isEqualTo(3_000);
        assertThat(SaraminClient.retryAfterMs("999999")).isEqualTo(600_000);    // 10분으로 자른다
        assertThat(SaraminClient.retryAfterMs("1234567")).isZero();             // 7자리 이상은 무시
        assertThat(SaraminClient.retryAfterMs("-1")).isZero();
        assertThat(SaraminClient.retryAfterMs("1.5")).isZero();
        assertThat(SaraminClient.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT")).isZero();
    }
}
