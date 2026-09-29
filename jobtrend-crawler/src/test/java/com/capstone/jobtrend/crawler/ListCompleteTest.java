package com.capstone.jobtrend.crawler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListCompleteTest {

    @Test
    void 두_건까지는_끝까지_받은_것으로_본다() {
        assertTrue(CrawlService.listComplete(811, 811));
        assertTrue(CrawlService.listComplete(811, 810));   // 2차 데이터엔지니어
        assertTrue(CrawlService.listComplete(811, 809));
        assertFalse(CrawlService.listComplete(811, 808));
    }

    @Test
    void 총_건수를_모르면_덜_받음() {
        assertFalse(CrawlService.listComplete(null, 500));
    }
}
