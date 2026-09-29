package com.capstone.jobtrend.crawler;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.properties 의 crawler.* 값.
 * 시험할 때는 max-list-pages·max-details 로 요청 수를 줄인다(0 = 제한 없음). 목록을 한도에서 끊으면 덜 받은 것으로 본다.
 */
@ConfigurationProperties("crawler")
public record CrawlerProperties(
        @DefaultValue("false") boolean enabled,             // 매일 04:00 수집(이 서버에서만 켠다)
        @DefaultValue("false") boolean runOnce,             // 뜨자마자 한 번 돌리고 끝낸다(수동 실행·시험)
        @DefaultValue("false") boolean localOnly,           // 기준 데이터·④·⑤만. 사람인에 요청하지 않는다(별칭 표를 고친 뒤)
        @DefaultValue("") String reportFile,                // 비어 있지 않으면 수집 대신 보고서 숫자를 이 JSON 파일로 쓰고 끝낸다
        @DefaultValue({"84", "92", "2232"}) List<Integer> jobs,
        @DefaultValue("raw") String rawDir,
        @DefaultValue("0") int maxListPages,                 // 직무마다 받을 목록 쪽 수
        @DefaultValue("0") int maxDetails,                   // 한 번에 받을 상세 수
        @DefaultValue("7") int rawKeepDays,                  // 마감 뒤 raw 파일 보관 기간
        @DefaultValue("10") int maxConsecutiveFailures       // 상세가 연달아 이만큼 실패하면 그날 수집을 멈춘다
) {}
