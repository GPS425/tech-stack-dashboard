package com.capstone.jobtrend.crawler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.capstone.jobtrend.crawler.DetailPageParser.Tag;

class ExcludedTest {

    private static final Set<Integer> IT = Set.of(84);
    private static final List<Tag> CHIP = List.of(new Tag(1, "RTL"), new Tag(2, "Verilog"), new Tag(84, "백엔드/서버개발"));

    private static boolean chip(String title) {
        return CrawlService.excluded(title, CHIP, IT, Set.of(), Set.of());
    }

    @Test
    void 반도체_설계는_뺀다() {
        assertTrue(chip("유명 대기업 반도체 DDI Digital Design"));
        assertTrue(chip("유명 반도체 SoC PI/Front-End Engineer"));
        assertTrue(chip("유명 반도체 팹리스 Embedded SRAM/ROM 개발"));
        assertTrue(chip("반도체 프론트엔드 설계(RTL)"));
    }

    @Test
    void 제목이_소프트웨어_직무면_안_뺀다() {
        assertFalse(chip("유명 반도체 팹리스 Embedded S/W Engineer (S)"));
        assertFalse(chip("유명 반도체 팹리스 Embedded Software Engineer"));
        assertFalse(chip("유명 반도체 임베디드 펌웨어 개발"));
        assertFalse(chip("유명 반도체 팹리스 BSP Driver Engineer (S)"));
        assertFalse(chip("중견기업 광학장비 SW개발(Java,C++) 대리"));
        assertFalse(chip("[정규직/코스닥상장사/성과급] 로봇 SW 개발자 경력직"));
        assertFalse(chip("로컬 AI 개발 엔지니어 신입/경력 모집"));
        assertFalse(chip("AX전략운영,시스템 아키텍처 분석,AI백엔드개발"));
        assertFalse(chip("[캐시워크] 데이터분석 담당 채용전환형 인턴"));
    }

    @Test
    void 개발자라도_하드웨어_비개발_직무면_그대로_뺀다() {   // 9/29 3차 검수
        assertTrue(chip("아날로그 회로 개발자"));
        assertTrue(chip("RTL 설계 개발자"));
        assertTrue(chip("FPGA 개발자"));
        assertTrue(chip("하드웨어 개발자"));
        assertTrue(chip("기구 개발자"));
        assertTrue(chip("사업개발자(BD)"));
        assertTrue(chip("Business Developer"));
        assertTrue(chip("교육 콘텐츠 개발자"));
        assertTrue(chip("교육콘텐츠개발 담당"));
        assertTrue(chip("신약 개발자"));
        assertFalse(chip("[신규 PC/콘솔] 콘텐츠 프로그래머"));
        assertTrue(chip("H/W·S/W 엔지니어 (회로설계)"));
        assertTrue(chip("회로설계/펌웨어개발/해외영업/AI솔루션영업 경력"));
    }

    @Test
    void 설계라는_말이_있어도_SW_직무면_안_뺀다() {   // 9/29 3차 두 번째 검수
        assertFalse(chip("펌웨어 설계 엔지니어"));
        assertFalse(chip("임베디드 SW 설계 및 개발"));
        assertFalse(chip("소프트웨어 설계/개발자"));
        assertFalse(chip("백엔드 개발자 (DB 설계)"));
        assertFalse(chip("BI(Business Intelligence) 개발자"));
        assertFalse(chip("유명 반도체 팹리스 Embedded Software Engineer"));
        assertTrue(chip("칩 설계 개발자"));
        assertTrue(chip("디지털 설계 개발자"));
    }

    @Test
    void 비개발_태그_위주_공고() {
        List<Tag> sales = List.of(new Tag(1, "영업"), new Tag(2, "기술영업"), new Tag(84, "백엔드/서버개발"));
        assertTrue(CrawlService.excluded("전력기기 국내영업", sales, IT, Set.of(), Set.of()));
        assertFalse(CrawlService.excluded("전력기기 국내영업", sales, IT, Set.of(10, 11), Set.of()));   // 글에서 기술 2개
    }
}
