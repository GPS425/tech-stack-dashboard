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

    /** 칩 태그 + 글에서 기술 1개(실제 SW 공고처럼). 글 기술이 2개 미만이라 태그만 보면 비개발로도 걸린다 → 제목 규칙만 남는다 */
    private static boolean chip(String title) {
        return CrawlService.excluded(title, CHIP, IT, Set.of(10), Set.of());
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

    @Test
    void 넓은_개발자_제목은_글에_기술이_없으면_뺀다() {   // 10/1 검수: '배터리 공정 개발자' 같은 비IT 공고
        List<Tag> nonIt = List.of(new Tag(1, "2차전지"), new Tag(2, "공정관리"), new Tag(3, "생산기술"));
        assertTrue(CrawlService.excluded("배터리 공정 개발자", nonIt, IT, Set.of(), Set.of()));
        assertTrue(CrawlService.excluded("식품 개발자", nonIt, IT, Set.of(), Set.of()));
        assertFalse(CrawlService.excluded("배터리 공정 개발자", nonIt, IT, Set.of(10), Set.of()));   // 글에서 기술 1개
        assertFalse(CrawlService.excluded("임베디드 소프트웨어 엔지니어", nonIt, IT, Set.of(), Set.of()));   // 강한 말은 그대로 살림
        assertTrue(CrawlService.excluded("RTL 설계 개발자", CHIP, IT, Set.of(10), Set.of()));   // 막는 말이 있으면 넓은 말도 안 살림
    }

    @Test
    void 막는_말은_제목으로_살리기만_막는다() {
        // '신약 개발자'라도 태그가 IT 위주면 빼지 않는다(제목은 살리기에만 쓰고, 빼는 판정은 태그·글로 한다)
        List<Tag> itTags = List.of(new Tag(84, "백엔드/서버개발"), new Tag(84, "API"));
        assertFalse(CrawlService.excluded("신약 개발자", itTags, IT, Set.of(), Set.of()));
        List<Tag> nonIt = List.of(new Tag(1, "임상"), new Tag(2, "제약"));
        assertTrue(CrawlService.excluded("신약 개발자", nonIt, IT, Set.of(10), Set.of()));
    }

    @Test
    void 넓은_제목은_글에_기술이_없으면_칩_태그여도_뺀다() {   // chip() 은 글 기술 1개를 주므로 여기서 따로 본다
        assertTrue(CrawlService.excluded("BI(Business Intelligence) 개발자", CHIP, IT, Set.of(), Set.of()));
        assertTrue(CrawlService.excluded("[신규 PC/콘솔] 콘텐츠 프로그래머", CHIP, IT, Set.of(), Set.of()));
        assertTrue(CrawlService.excluded("iOS 개발자", CHIP, IT, Set.of(), Set.of()));
        assertFalse(CrawlService.excluded("iOS 앱 개발", CHIP, IT, Set.of(), Set.of()));   // '앱 개발'은 강한 말
    }

    @Test
    void 반도체_설계_규칙만으로도_뺀다() {
        // IT 태그가 많고 글 기술도 2개 이상이라 비개발 판정은 안 걸린다 → 칩 태그 2개 규칙만 본다
        List<Tag> chipButIt = List.of(new Tag(1, "RTL"), new Tag(2, "Verilog"),
                new Tag(84, "백엔드/서버개발"), new Tag(84, "API"), new Tag(84, "Linux"));
        assertTrue(CrawlService.excluded("반도체 설계 엔지니어", chipButIt, IT, Set.of(10, 11), Set.of()));
        assertFalse(CrawlService.excluded("반도체 설계 엔지니어", chipButIt.subList(1, 5), IT, Set.of(10, 11), Set.of()));
        assertTrue(CrawlService.excluded("FPGA 개발자", chipButIt, IT, Set.of(10, 11), Set.of()));   // 막는 말(fpga)이 있어 제목으로 안 살림
    }
}
