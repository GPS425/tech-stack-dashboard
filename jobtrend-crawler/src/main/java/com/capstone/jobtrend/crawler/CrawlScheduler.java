package com.capstone.jobtrend.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * crawler.enabled=true  : 매일 04:00(한국 시간) 한 번. 수집은 서버 한 곳에서만 켠다.
 * crawler.run-once=true : 뜨자마자 한 번 돌리고 앱을 끝낸다(수동 실행·시험). 성공이면 종료 코드 0.
 * crawler.local-only=true 만 주면 사람인에 요청하지 않고 ④·⑤만 한 번 돌리고 끝낸다.
 * 수집을 켜면 뜰 때와 회차마다 죽은 앱이 남긴 RUNNING 을 FAILED 로 바꾼다(CrawlService.failLeftoverRuns).
 */
@Component
public class CrawlScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final CrawlService crawl;
    private final ReportService report;
    private final CrawlerProperties props;
    private final ConfigurableApplicationContext context;

    public CrawlScheduler(CrawlService crawl, ReportService report, CrawlerProperties props,
                          ConfigurableApplicationContext context) {
        this.crawl = crawl;
        this.report = report;
        this.props = props;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!props.reportFile().isBlank()) {                 // 보고서 숫자만 뽑고 끝낸다(수집 안 함)
            report.export(java.nio.file.Path.of(props.reportFile()));
            System.exit(SpringApplication.exit(context, () -> 0));
        }
        // local-only 만 주면 ④·⑤를 한 번 돌리고 끝낸다(README 의 CRAWLER_LOCAL_ONLY=true). enabled 와 같이 주면 매일 ④·⑤만
        boolean once = props.runOnce() || (props.localOnly() && !props.enabled());
        if (!props.enabled() && !once) {
            log.info("수집이 꺼져 있음 (crawler.enabled=false, crawler.run-once=false)");
            return;
        }
        int left = crawl.failLeftoverRuns();
        if (left > 0) log.warn("끝나지 않은 수집 {}건을 FAILED 로 정리함", left);
        if (once) {
            boolean ok = crawl.run();
            System.exit(SpringApplication.exit(context, () -> ok ? 0 : 1));
        }
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void daily() {
        if (props.enabled()) crawl.run();
    }
}
