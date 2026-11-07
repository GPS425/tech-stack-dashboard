package com.capstone.jobtrend.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class PrimaryJobTest {

    // 사람인 직무 코드
    private static final int BACKEND = 84, FRONT = 92, FULLSTACK = 2232, WEB = 87, APP = 86, DATA_ENG = 83,
            DATA_SCI = 2248, GAME = 80, QA = 99, SQA = 2229, AI = 181, DEVOPS = 146, INFRA = 127, CLOUD = 136,
            SECURITY = 90, EMBEDDED = 128, SE = 100;

    private static int pick(String title, List<Integer> tags, Integer... linked) {
        return PrimaryJob.pick(title, tags, List.of(linked)).code();
    }

    private static PrimaryJob.Reason reason(String title, List<Integer> tags, Integer... linked) {
        return PrimaryJob.pick(title, tags, List.of(linked)).reason();
    }

    @Test
    void 연결이_없으면_고르지_않고_하나면_그것() {
        assertNull(PrimaryJob.pick("백엔드 개발자", List.of(BACKEND), List.of()));
        // 연결이 하나면 제목이 달라도 그대로(연결에 없는 직무는 붙이지 않는다)
        assertEquals(DATA_SCI, pick("서버/네트워크 관제", List.of(SE, DATA_SCI, AI), DATA_SCI));
        assertEquals(PrimaryJob.Reason.ONLY, reason("서버/네트워크 관제", List.of(), DATA_SCI));
    }

    @Test
    void 관제_공고는_연결된_인프라나_정보보안이_대표() {
        // 직무 순서로는 데이터 사이언티스트가 앞이지만 제목이 관제
        assertEquals(INFRA, pick("서버/네트워크 관제 요원 모집", List.of(DATA_SCI, AI, INFRA), DATA_SCI, AI, INFRA));
        assertEquals(SECURITY, pick("보안관제 요원 (주간)", List.of(DATA_SCI, SECURITY), DATA_SCI, SECURITY, INFRA));
        assertEquals(SECURITY, pick("네트워크 보안 엔지니어", List.of(INFRA), INFRA, SECURITY));
    }

    @Test
    void 제목에_여러_직무가_있으면_좁은_직무가_먼저() {
        assertEquals(BACKEND, pick("AI 백엔드 개발자", List.of(AI, BACKEND), BACKEND, AI));
        assertEquals(BACKEND, pick("AX전략운영,시스템 아키텍처 분석,AI백엔드개발", List.of(), BACKEND, AI));
        assertEquals(GAME, pick("게임 서버 개발자 (C++)", List.of(BACKEND, GAME), BACKEND, GAME));
        assertEquals(DEVOPS, pick("DevOps/인프라 엔지니어 (AWS)", List.of(INFRA, CLOUD, DEVOPS), INFRA, CLOUD, DEVOPS));
        assertEquals(CLOUD, pick("클라우드 인프라 엔지니어", List.of(INFRA, CLOUD), INFRA, CLOUD));
        assertEquals(EMBEDDED, pick("임베디드 리눅스 시스템 엔지니어", List.of(INFRA, EMBEDDED), INFRA, EMBEDDED));
        assertEquals(FRONT, pick("웹 프론트엔드 개발자", List.of(WEB, FRONT), FRONT, WEB));
        assertEquals(DATA_ENG, pick("데이터 엔지니어 (AI 플랫폼)", List.of(AI, DATA_ENG), DATA_ENG, AI));
        assertEquals(AI, pick("AI 엔지니어 (LLM)", List.of(DATA_SCI, AI), DATA_SCI, AI));
        assertEquals(APP, pick("Android/iOS 앱 개발자", List.of(WEB, APP), APP, WEB));
    }

    @Test
    void 영문_낱말은_다른_낱말_속에서_잡지_않는다() {
        // 'sqa' 안의 qa, 'research' 안의 search, 'said' 안의 ai 는 아니다
        assertEquals(SQA, pick("SQA 엔지니어", List.of(QA, SQA), QA, SQA));
        assertEquals(PrimaryJob.Reason.TAG, reason("Research Engineer", List.of(BACKEND), 103, BACKEND));
        assertEquals(PrimaryJob.Reason.TAG, reason("Detail said", List.of(BACKEND), AI, BACKEND));
        assertEquals(FULLSTACK, pick("Full-Stack Developer", List.of(), BACKEND, FRONT, FULLSTACK));
    }

    @Test
    void 제목이_안_맞으면_태그_순서_그다음은_직무_순서() {
        // 제목에 직무 낱말이 없다: 회사가 태그에 먼저 적은 직무
        assertEquals(DATA_ENG, pick("[유명기업] 신입/경력 공개채용", List.of(167, DATA_ENG, BACKEND), BACKEND, DATA_ENG));
        assertEquals(PrimaryJob.Reason.TAG, reason("공개채용", List.of(DATA_ENG, BACKEND), BACKEND, DATA_ENG));
        // 상세를 아직 못 받음(태그 없음)·제목 없음: 지금 화면과 같은 직무 순서
        assertEquals(BACKEND, pick(null, List.of(), BACKEND, DATA_ENG));
        assertEquals(PrimaryJob.Reason.ORDER, reason("공개채용", List.of(), BACKEND, DATA_ENG));
        // 제목 낱말이 맞는 직무가 연결에 없으면 그 낱말은 안 본다
        assertEquals(DATA_ENG, pick("백엔드 개발자", List.of(DATA_ENG, WEB), WEB, DATA_ENG));
    }

    @Test
    void 대표는_늘_연결된_직무_중_하나() {
        List<Integer> linked = List.of(BACKEND, FRONT, DATA_SCI);
        for (String title : List.of("서버/네트워크 관제", "AI 엔지니어", "보안 담당", "공개채용", "")) {
            assertTrue(linked.contains(PrimaryJob.pick(title, List.of(SE, AI, SECURITY), linked).code()), title);
        }
    }

    private static List<Integer> configuredJobs() throws IOException {
        Properties p = new Properties();
        try (InputStream in = PrimaryJobTest.class.getResourceAsStream("/application.properties")) {
            p.load(in);
        }
        return Arrays.stream(p.getProperty("crawler.jobs").split(",")).map(s -> Integer.valueOf(s.trim())).toList();
    }

    @Test
    void 설정의_직무는_모두_제목_규칙이_있고_코드표에_있다() throws IOException {
        Set<Integer> ruled = PrimaryJob.TITLE_RULES.stream().map(PrimaryJob.Rule::code).collect(Collectors.toSet());
        Map<Integer, String> codes = SeedLoader.readCodeNames();
        for (int code : configuredJobs()) {
            assertTrue(ruled.contains(code), "제목 규칙이 없는 직무 코드 " + code);
            assertTrue(codes.containsKey(code), "코드표에 없는 직무 코드 " + code);
        }
        assertEquals(PrimaryJob.TITLE_RULES.size(), ruled.size(), "같은 코드의 규칙이 둘");
    }

    @Test
    void 기존_직무_17개의_순서는_그대로_두고_새_직무는_뒤에_붙인다() throws IOException {
        // sort_order 는 이 순서로 매겨진다. 앞을 바꾸면 화면의 직무 순서가 바뀐다(job_id 는 사람인 코드로 찾아서 안 바뀜)
        List<Integer> jobs = configuredJobs();
        assertEquals(List.of(84, 92, 2232, 87, 86, 101, 83, 82, 2248, 80, 91, 95, 99, 2229, 103, 2246, 2249),
                jobs.subList(0, 17));
        assertEquals(List.of(181, 146, 127, 136, 90, 128), jobs.subList(17, jobs.size()));
    }
}
