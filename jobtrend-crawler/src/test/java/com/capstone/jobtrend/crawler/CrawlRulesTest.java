package com.capstone.jobtrend.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

import org.jsoup.HttpStatusException;
import org.jsoup.UnsupportedMimeTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CrawlService 의 DB 없이 볼 수 있는 규칙. */
class CrawlRulesTest {

    @Test
    void 공고_탓인_실패만_실패_횟수에_넣는다() {
        assertTrue(CrawlService.postingsFault(new DetailPageParser.ParseFailedException("형식 다름")));
        assertTrue(CrawlService.postingsFault(new HttpStatusException("HTTP 404", 404, "u")));
        assertTrue(CrawlService.postingsFault(new UnsupportedMimeTypeException("json", "application/json", "u")));
        assertTrue(CrawlService.postingsFault(new SaraminClient.TooLargeException("큼")));
        assertFalse(CrawlService.postingsFault(new HttpStatusException("HTTP 429", 429, "u")));
        assertFalse(CrawlService.postingsFault(new HttpStatusException("HTTP 503", 503, "u")));
        assertFalse(CrawlService.postingsFault(new SocketTimeoutException("Read timed out")));
        assertFalse(CrawlService.postingsFault(new IOException("No space left on device")));   // raw 저장 실패
    }

    @Test
    void 수정_시각_허용_오차는_표기_단위와_1시간_중_큰_것() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 4, 5);
        assertEquals(60, CrawlService.modifiedToleranceMinutes(ListTexts.posted("10분 전 수정", now)));
        assertEquals(60, CrawlService.modifiedToleranceMinutes(ListTexts.posted("3시간 전 수정", now)));
        assertEquals(24 * 60, CrawlService.modifiedToleranceMinutes(ListTexts.posted("1일 전 수정", now)));
        assertEquals(24 * 60, CrawlService.modifiedToleranceMinutes(ListTexts.posted("어제 수정", now)));
        assertEquals(60, CrawlService.modifiedToleranceMinutes(null));
    }

    @Test
    void 회차_메모에_건너뛴_항목과_처음_보는_마감_표기를_남긴다() {
        CrawlService.Counts c = new CrawlService.Counts();
        c.newPostings = 3;
        assertEquals("새 공고 3", CrawlService.runNote(c, null));
        c.skipped = 2;
        c.deadlineUnknown = 5;
        assertEquals("새 공고 3 · 제목·회사명 없어 건너뜀 2 · 처음 보는 마감 표기 5 · ② 마감 끝",
                CrawlService.runNote(c, "② 마감 끝"));
    }

    @Test
    void 원본_폴더의_오래된_임시_파일만_지운다(@TempDir Path dir) throws IOException {
        RawStore store = new RawStore(dir);
        store.save(1L, new RawStore.Raw(List.of(), List.of("Java"), List.of()));
        Path oldTmp = Files.writeString(dir.resolve("2-123.tmp"), "x");
        Files.setLastModifiedTime(oldTmp, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofDays(2).toMillis()));
        Path newTmp = Files.writeString(dir.resolve("3-456.tmp"), "x");

        assertEquals(1, store.sweepTemp(Duration.ofDays(1)));
        assertFalse(Files.exists(oldTmp));
        assertTrue(Files.exists(newTmp));                       // 지금 쓰는 중일 수 있는 것은 남긴다
        assertTrue(store.exists(1L));                           // 원본은 건드리지 않는다
        assertFalse(store.exists(2L));
    }
}
