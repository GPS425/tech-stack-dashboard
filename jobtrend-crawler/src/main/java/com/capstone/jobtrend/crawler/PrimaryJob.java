package com.capstone.jobtrend.crawler;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 공고마다 대표 직무 하나를 고른다(11/7). 요청·DB 없이 제목·상세 태그·직무 연결만 본다.
 * 직무 연결(posting_job)은 사람인 직무 목록에 나온 대로이고, 그 목록은 회사가 공고에 고른 직무 태그 그대로다.
 * 회사가 직무를 넓게 고르면(관제 공고에 '데이터 사이언티스트') 카드 맨 앞 직무가 엉뚱해져서, 연결은 그대로 두고 대표만 고른다.
 *
 * 순서
 * 1. 연결이 하나면 그것
 * 2. 제목: 연결된 직무 중 제목 낱말이 맞는 것. 여럿이면 TITLE_RULES 에서 위에 있는 것
 *    ('AI 백엔드 개발자' 는 백엔드, '네트워크 보안 관제' 는 정보보안, '게임 서버 개발' 은 게임)
 * 3. 태그: 상세 태그 순서(회사가 고른 순서)에서 처음 나오는 연결된 직무
 * 4. 그래도 없으면(상세를 아직 못 받음) 직무 순서(sort_order)에서 첫 번째 = 지금 화면과 같음
 * 대표는 늘 연결된 직무 중 하나다. 연결에 없는 직무를 제목만 보고 붙이지 않는다.
 */
final class PrimaryJob {

    private PrimaryJob() {}

    enum Reason { ONLY, TITLE, TAG, ORDER }

    record Pick(int code, Reason reason) {}

    record Rule(int code, Pattern title) {}

    private static Rule rule(int code, String... words) {
        return new Rule(code, Pattern.compile(String.join("|", words)));
    }

    /** 사람인 직무 코드 → 제목 낱말. 정규화(소문자·NFKC)한 제목에 쓴다. 위에 있을수록 먼저(좁은 직무가 위) */
    static final List<Rule> TITLE_RULES = List.of(
            rule(90, "보안", "security", "모의\\s*해킹", "취약점", "침해"),                       // 정보보안
            rule(146, "devops", "데브옵스", "(?<![a-z])sre(?![a-z])", "ci\\s*/\\s*cd"),            // DevOps
            rule(128, "임베디드", "embedded", "펌웨어", "firmware", "(?<![a-z])bsp(?![a-z])"),      // 임베디드
            rule(2248, "데이터\\s*사이언", "data\\s*scien"),                                      // 데이터 사이언티스트
            rule(83, "데이터\\s*엔지니어", "data\\s*engineer"),                                   // 데이터엔지니어
            rule(82, "데이터\\s*분석", "data\\s*analy"),                                          // 데이터분석가
            rule(2246, "(?<![a-z])bi(?![a-z])"),                                                  // BI 엔지니어
            rule(95, "(?<![a-z])dba(?![a-z])", "데이터베이스\\s*관리"),                           // DBA
            rule(2229, "(?<![a-z])sqa(?![a-z])"),                                                 // SQA
            rule(99, "(?<![a-z])qa(?![a-z])", "테스터", "tester", "테스트\\s*엔지니어"),          // QA/테스터
            rule(91, "퍼블리셔", "퍼블리싱", "publish"),                                          // 퍼블리셔
            rule(80, "게임", "(?<![a-z])game"),                                                   // 게임개발
            rule(103, "검색", "(?<![a-z])search"),                                                // 검색엔진
            rule(2232, "풀\\s*스택", "full\\s*-?\\s*stack"),                                      // 풀스택
            rule(92, "프론트", "front\\s*-?\\s*end"),                                             // 프론트엔드
            rule(86, "앱\\s*(개발|프로그래)", "android", "안드로이드", "(?<![a-z])ios(?![a-z])",
                    "모바일", "mobile", "flutter", "플러터", "react\\s*native"),                  // 앱개발
            rule(84, "백엔드", "back\\s*-?\\s*end", "서버\\s*(개발|프로그래)", "server\\s*(develop|program)"),  // 백엔드/서버개발
            rule(136, "클라우드", "cloud"),                                                       // 클라우드
            rule(127, "인프라", "infra", "관제", "네트워크", "network", "시스템\\s*(엔지니어|운영|관리)",
                    "system\\s*engineer", "서버\\s*(운영|관리|엔지니어)", "(?<![a-z])se(?![a-z])"),  // 인프라
            rule(181, "(?<![a-z])ai(?![a-z])", "인공\\s*지능", "머신\\s*러닝", "딥\\s*러닝", "machine\\s*learning",
                    "deep\\s*learning", "(?<![a-z])(ml|llm|nlp)(?![a-z])"),                       // AI(인공지능)
            rule(87, "웹\\s*(개발|프로그래)", "web\\s*(develop|program)"),                        // 웹개발
            rule(101, "(?<![a-z])si(?![a-z])"),                                                   // SI개발
            rule(2249, "클라이언트", "client"));                                                  // 클라이언트

    /**
     * @param title  공고 제목(없으면 null)
     * @param tags   상세 태그의 사람인 코드, 상세에 나온 순서. 상세를 못 받았으면 빈 목록
     * @param linked 이 공고에 연결된 직무의 사람인 코드, 직무 순서(sort_order)대로
     * @return 연결이 없으면 null
     */
    static Pick pick(String title, List<Integer> tags, List<Integer> linked) {
        if (linked.isEmpty()) return null;
        if (linked.size() == 1) return new Pick(linked.get(0), Reason.ONLY);
        if (title != null) {
            String t = SkillMatcher.normalize(title);
            for (Rule r : TITLE_RULES) {
                if (linked.contains(r.code()) && r.title().matcher(t).find()) return new Pick(r.code(), Reason.TITLE);
            }
        }
        for (int code : tags) {
            if (linked.contains(code)) return new Pick(code, Reason.TAG);
        }
        return new Pick(linked.get(0), Reason.ORDER);
    }
}
