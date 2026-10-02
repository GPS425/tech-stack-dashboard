package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.capstone.jobtrend.crawler.SkillMatcher.Alias;

class SkillMatcherTest {

    private static final int JAVA = 1, JAVASCRIPT = 2, MYSQL = 3, SQL = 4, VUE = 5, CPP = 6, PYTHON = 7,
            SPRING = 8, SPRING_BOOT = 9, ES = 10;

    private final SkillMatcher matcher = new SkillMatcher(List.of(
            Alias.of("java", JAVA, true), Alias.of("자바", JAVA, true),
            Alias.of("javascript", JAVASCRIPT, true), Alias.of("자바스크립트", JAVASCRIPT, true),
            Alias.of("mysql", MYSQL, true), Alias.of("sql", SQL, true),
            Alias.of("vue.js", VUE, true), Alias.of("뷰", VUE, false),
            Alias.of("c++", CPP, true), Alias.of("python", PYTHON, true),
            Alias.of("spring", SPRING, true), Alias.of("spring boot", SPRING_BOOT, true),
            Alias.of("es", ES, false)));

    @Test
    void 버전이_붙은_표기와_한글_조사() {
        assertThat(matcher.match("Java8, Python3, C++17 경험")).containsExactlyInAnyOrder(JAVA, PYTHON, CPP);
        assertThat(matcher.match("자바를 쓰고")).containsExactly(JAVA);
    }

    @Test
    void 앞부분이_같은_표기는_긴_것부터() {
        assertThat(matcher.match("자바스크립트 개발")).containsExactly(JAVASCRIPT);
        assertThat(matcher.match("JavaScript와 TypeScript")).containsExactly(JAVASCRIPT);
        assertThat(matcher.match("Spring Boot 3.x")).containsExactly(SPRING_BOOT);
        assertThat(matcher.match("MySQL 튜닝")).containsExactly(MYSQL);
        assertThat(matcher.match("MySQL·NoSQL, SQL 튜닝")).containsExactlyInAnyOrder(MYSQL, SQL);
    }

    @Test
    void 태그에서만_쓰는_짧은_별칭은_글에서_안_잡는다() {
        assertThat(matcher.match("코드 리뷰 문화")).isEmpty();
        assertThat(matcher.match("ES6 문법")).isEmpty();
        assertThat(matcher.match("Vue.js 3")).containsExactly(VUE);
    }

    @Test
    void 한두_글자_기술은_나열될_때만() {   // 9/29 검수: Go·C 를 글에서 놓치던 것
        SkillMatcher m = new SkillMatcher(List.of(
                Alias.of("java", JAVA, true), Alias.of("kotlin", 20, true),
                Alias.of("go", 21, false), Alias.of("golang", 21, true),
                Alias.of("c", 22, false), Alias.of("c언어", 22, true), Alias.of("c++", CPP, true)));
        assertThat(m.match("Java, Go, Kotlin 중 하나")).containsExactlyInAnyOrder(JAVA, 21, 20);
        assertThat(m.match("c, java, javascript")).contains(22, JAVA);
        assertThat(m.match("go 백엔드 개발 경험 필수")).isEmpty();
        assertThat(m.match("Golang 3년")).containsExactly(21);
        assertThat(m.match("C/C++ 가능자")).containsExactlyInAnyOrder(22, CPP);
        assertThat(m.match("C++ 개발")).containsExactly(CPP);
        assertThat(m.match("C레벨 보고 경험")).isEmpty();
    }

    private static final int GO = 21, C = 22, R = 23, SAP = 24, KOTLIN = 20, LINUX = 25;

    private final SkillMatcher listed = new SkillMatcher(List.of(
            Alias.of("java", JAVA, true), Alias.of("kotlin", KOTLIN, true), Alias.of("python", PYTHON, true),
            Alias.of("sap", SAP, true), Alias.of("linux", LINUX, true), Alias.of("c++", CPP, true),
            Alias.of("go", GO, false), Alias.of("golang", GO, true), Alias.of("c", C, false), Alias.of("r", R, false)));

    @Test
    void 찾아서_지운_자리는_나열_기호가_아니다() {   // 예전엔 쉼표로 지워서 "SAP go" 의 go 가 나열처럼 보였다
        assertThat(listed.match("Java go 백엔드")).containsExactly(JAVA);
        assertThat(listed.match("SAP Go-Live")).containsExactly(SAP);
        assertThat(listed.match("GO/NO-GO")).isEmpty();
        assertThat(listed.match("Go(Golang), Kotlin")).containsExactlyInAnyOrder(GO, KOTLIN);
        assertThat(listed.match("Kotlin/Go")).containsExactlyInAnyOrder(GO, KOTLIN);
    }

    @Test
    void 한_글자_코드와_괄호_표기는_나열이_아니다() {
        for (String t : List.of("(R&D)", "R&D 경험", "Python, R&D 경험", "Microsoft(R) Office", "R/R 정의",
                "C/S 환경", "C/S, Web", "Copyright (C) 회사", "A/B/C 테스트"))
            assertThat(listed.match(t)).as(t).doesNotContain(R, C);
        assertThat(listed.match("Python, R")).containsExactlyInAnyOrder(PYTHON, R);
        assertThat(listed.match("(C, Java)")).containsExactlyInAnyOrder(C, JAVA);
    }

    @Test
    void C_는_및_Embedded_프로그래밍_으로도() {
        assertThat(listed.match("C 및 C++ 개발")).containsExactlyInAnyOrder(C, CPP);
        assertThat(listed.match("Embedded C 개발")).containsExactly(C);
        assertThat(listed.match("임베디드 C 개발")).containsExactly(C);
        assertThat(listed.match("Embedded C++ 개발")).containsExactly(CPP);
        assertThat(listed.match("Linux C 프로그래밍")).containsExactlyInAnyOrder(LINUX, C);
        assertThat(listed.match("C 레벨 미팅")).isEmpty();
    }

    @Test
    void 한_글자_코드_뒤의_레벨_급_등급은_기술이_아니다() {   // 줄 앞 글머리 ㆍ•・ 도 · 로 바뀌어 나열 기호가 된다
        for (String t : List.of("ㆍC레벨 임원 대상 보고 경험", "• C레벨 커뮤니케이션 경험", "・C레벨 보고", "· C레벨 보고 경험",
                "•C급 인력 관리", "• C 레벨 보고", "A, B, C 등급 고객 관리 경험", "A/B/C 등급 분류 체계 이해", "• R 및 D 업무"))
            assertThat(listed.match(t)).as(t).doesNotContain(C, R);
        // 줄 앞 글머리 뒤의 진짜 기술은 잡는다
        assertThat(listed.match("ㆍGo 기반 서버 개발 경험")).containsExactly(GO);
        assertThat(listed.match("• Go 백엔드 개발 경험")).containsExactly(GO);
        assertThat(listed.match("ㆍR 활용 통계 분석")).containsExactly(R);
        assertThat(listed.match("Python, R 및 SQL")).containsExactlyInAnyOrder(PYTHON, R);
        // 알고 놓치는 것: 공백으로만 나열한 "Python Go Java" 의 Go
        assertThat(listed.match("Python Go Java 중 하나")).containsExactlyInAnyOrder(PYTHON, JAVA);
    }

    private static final int NODE = 26, CURSOR = 27, RUST = 28, TS = 29, SQL_ = 4;

    /** 10/1 재검토 2차: 줄 앞 글머리·나열 규칙을 고친 것 */
    private final SkillMatcher list2 = new SkillMatcher(List.of(
            Alias.of("java", JAVA, true), Alias.of("kotlin", KOTLIN, true), Alias.of("python", PYTHON, true),
            Alias.of("sap", SAP, true), Alias.of("c++", CPP, true), Alias.of("sql", SQL_, true), Alias.of("rust", RUST, true),
            Alias.of("typescript", TS, true), Alias.of("react", 39, true), Alias.of("claude code", CURSOR, true),
            Alias.of("go", GO, false), Alias.of("golang", GO, true), Alias.of("c", C, false), Alias.of("r", R, false),
            Alias.of("node", NODE, false), Alias.of("cursor", CURSOR, false)));

    @Test
    void 줄_앞_글머리_뒤는_기술_문맥일_때만() {
        for (String t : List.of("ㆍGo 기반 서버 개발 경험", "• Go 백엔드 개발 경험", "・Go 서버", "• Go 로 서버 개발"))
            assertThat(list2.match(t)).as(t).containsExactly(GO);
        for (String t : List.of("ㆍR 활용 통계 분석", "• R Shiny 대시보드", "• R(ggplot2) 시각화", "ㆍR 을 이용한 분석"))
            assertThat(list2.match(t)).as(t).containsExactly(R);
        for (String t : List.of("• Node 런타임 이해", "• Node 기반 백엔드", "• Node (Express) API", "• Node 18 이상 사용 경험"))
            assertThat(list2.match(t)).as(t).containsExactly(NODE);
        assertThat(list2.match("ㆍCursor 를 활용한 생산성 향상")).containsExactly(CURSOR);
        assertThat(list2.match("• C(Embedded) 펌웨어 개발")).containsExactly(C);
        assertThat(list2.match("• Java • Go • Kotlin 개발")).containsExactlyInAnyOrder(JAVA, GO, KOTLIN);   // 글 가운데의 • 는 나열
        // 기술 문맥이 아니면 안 잡는다
        for (String t : List.of("• Go Live 지원", "• Go Live 일정 관리 및 cut-over", "ㆍGo To Market 전략 수립", "• Go to market 실행",
                "• C 사 프로젝트 수행 경험", "ㆍR 값 산출 및 검증", "• Node 증설 및 교체 작업", "• Cursor 기반 페이지네이션 API 설계",
                "ㆍCursor 기반 무한 스크롤 구현", "• C레벨 보고", "• R 및 D 업무"))
            assertThat(list2.match(t)).as(t).isEmpty();
        // 글머리가 아닌 - 로 시작하면 예전처럼 나열일 때만
        assertThat(list2.match("- Go 서버 개발 경험")).isEmpty();
    }

    @Test
    void 줄바꿈은_나열_기호가_아니다() {
        assertThat(list2.match(List.of("- 슬로건 Ready to Go", "• 협업 경험"))).isEmpty();
        assertThat(list2.match(List.of("- 고객 등급 관리(A~C", "• 데이터 분석 경험"))).isEmpty();
        assertThat(list2.match(List.of("- 사용 기술: Java, Kotlin,", "Go 기반 서버"))).containsExactlyInAnyOrder(JAVA, KOTLIN);
        assertThat(list2.match(List.of("- Java, Go", "• 협업 경험"))).containsExactlyInAnyOrder(JAVA, GO);
    }

    @Test
    void 다른_한_글자_코드와의_슬래시는_나열() {
        assertThat(list2.match("C/Go 개발 경험")).containsExactlyInAnyOrder(C, GO);
        assertThat(list2.match("Go/C 기반 시스템 개발")).containsExactlyInAnyOrder(C, GO);
        assertThat(list2.match("C/R 활용 경험")).containsExactlyInAnyOrder(C, R);
        assertThat(list2.match("C/R/Python 활용")).containsExactlyInAnyOrder(C, R, PYTHON);
        for (String t : List.of("R/R 정의", "A/B/C 테스트", "C/S 환경", "A/S 및 C/S 대응", "등급(A/B/C) 관리", "H/W, S/W, C/S"))
            assertThat(list2.match(t)).as(t).isEmpty();
    }

    @Test
    void 한_글자_코드의_이웃이_모두_한_글자면_기술이_아니다() {
        for (String t : List.of("Plan A, B, C 수립", "A, B, C 중 택1", "A, B, C 형 설계", "Aㆍ BㆍC 단계"))
            assertThat(list2.match(t)).as(t).isEmpty();
        assertThat(list2.match("Java, C, R 가능")).containsExactlyInAnyOrder(JAVA, C, R);
        assertThat(list2.match("C, R 활용")).containsExactlyInAnyOrder(C, R);
        for (String t : List.of("C 학점 이상", "C팀 협업", "C군 관리", "R 값 계산", "C형 구조"))
            assertThat(list2.match("Java, " + t)).as(t).containsExactly(JAVA);
        assertThat(list2.match("Java, C 사용 경험")).containsExactlyInAnyOrder(JAVA, C);   // 사용 은 C 사 가 아니다
    }

    @Test
    void 영문_바로_뒤_괄호_하나만_등록상표_저작권() {
        assertThat(list2.match("통계 언어(R) 활용 능력")).containsExactly(R);
        assertThat(list2.match("데이터 분석 언어 (R) 사용 능숙자")).containsExactly(R);
        for (String t : List.of("Microsoft(R) Office", "Microsoft (R) Office", "Copyright (C) 회사", "Copyright(C) 2024", "Intel(R) Xeon"))
            assertThat(list2.match(t)).as(t).isEmpty();
    }

    @Test
    void 더하기_콜론_와과도_나열() {
        assertThat(list2.match("TypeScript + Node 개발 경험")).containsExactlyInAnyOrder(TS, NODE);
        assertThat(list2.match("Go + Kotlin")).containsExactlyInAnyOrder(GO, KOTLIN);
        assertThat(list2.match("C++ Go 개발")).containsExactly(CPP);           // C++ 의 + 는 나열이 아니다
        assertThat(list2.match("프로그래밍 언어: C")).containsExactly(C);
        assertThat(list2.match("• 언어: Go")).containsExactly(GO);
        assertThat(list2.match("기술 스택: Go, Kotlin")).containsExactlyInAnyOrder(GO, KOTLIN);
        assertThat(list2.match("등급: C")).isEmpty();                         // 언어·스택·기술 뒤의 : 만
        assertThat(list2.match("Go와 Rust 경험")).containsExactlyInAnyOrder(GO, RUST);
        assertThat(list2.match("C와 C++ 개발 경험")).containsExactlyInAnyOrder(C, CPP);
        assertThat(list2.match("R과 Python 사용 가능자")).containsExactlyInAnyOrder(R, PYTHON);
    }

    @Test
    void go_와_cursor_의_다른_뜻() {
        for (String t : List.of("SAP 구축 및 Go Live 지원", "분석/설계/개발/Go Live 단계", "Go/No Go 판단", "GO / NO GO 의사결정",
                "GO/NO-GO", "Go/Stop 판단", "Go, Stop 판단"))
            assertThat(list2.match(t)).as(t).doesNotContain(GO);
        for (String t : List.of("커서 기반 페이지네이션(Cursor, Offset) 설계", "Offset/Cursor 기반 페이징", "No-Offset(Cursor) 페이징",
                "Explicit Cursor, Ref Cursor 활용", "커서(Cursor) 기반 대량 처리", "MongoDB Cursor, Aggregation", "DB Cursor, Trigger",
                "Implicit Cursor, Function", "SQL Cursor, Python", "cursor based pagination, React", "Java, Cursor 기반 무한 스크롤"))
            assertThat(list2.match(t)).as(t).doesNotContain(CURSOR);
        assertThat(list2.match("Copilot, Cursor 활용")).containsExactly(CURSOR);
        assertThat(list2.match("Cursor/Claude Code 활용")).containsExactly(CURSOR);
        assertThat(list2.match("벡터 DB, Cursor 활용")).containsExactly(CURSOR);   // DB 뒤 쉼표는 나열
    }

    @Test
    void 숫자로_끝나는_별칭() {
        SkillMatcher m = new SkillMatcher(List.of(Alias.of("s3", 40, true), Alias.of("java", JAVA, true)));
        assertThat(m.match("MCU(NXP S32K) 펌웨어")).isEmpty();
        assertThat(m.match("AWS S3, EC2 운영")).containsExactly(40);
        assertThat(m.match("S3버킷")).containsExactly(40);
        assertThat(m.match("Java8, Java17")).containsExactly(JAVA);
    }

    @Test
    void SAS_는_공고_전체로도_가른다() {
        int sas = 50;
        SkillMatcher m = new SkillMatcher(List.of(Alias.of("sas", sas, true), Alias.of("python", PYTHON, true)));
        // 줄 단위 EXCEPT 가 못 막는 줄("SAS 링크 디버깅")도 같은 공고의 다른 줄(SSD 펌웨어)로 가른다
        List<String> storage = List.of("ㆍSSD 펌웨어 검증", "- SAS 링크 디버깅 경험", "- FW 이슈 재현 및 분석", "- Python 자동화");
        Set<Integer> found = m.match(List.of("- SAS 링크 디버깅 경험", "- Python 자동화"));
        assertThat(found).containsExactlyInAnyOrder(sas, PYTHON);
        Set<Integer> dropped = m.dropByContext(found, storage);
        assertThat(dropped).containsExactly(PYTHON);
        assertThat(found).containsExactlyInAnyOrder(sas, PYTHON);                      // 받은 것은 그대로, 사본을 돌려준다
        // 통계 낱말이 있으면 그대로
        for (String stat : List.of("SAS, SPSS 활용", "통계 분석 경험", "SAS/STAT 활용", "SAS Viya", "데이터 분석 경험", "Base SAS 자격", "SAS, R 활용"))
            assertThat(m.dropByContext(Set.of(sas), List.of("SSD 펌웨어 검증", stat))).as(stat).containsExactly(sas);
        // 스토리지 낱말이 없으면 그대로("분석" 만으로는 통계로 안 본다: 스토리지 공고도 "이슈 분석" 을 쓴다)
        assertThat(m.dropByContext(Set.of(sas), List.of("SAS 프로그래밍 경험"))).containsExactly(sas);
        assertThat(m.dropByContext(Set.of(sas), List.of("SAS-4 인터페이스 분석", "NVMe 검증"))).isEmpty();
        // SAS 가 없거나 별칭 표에 sas 가 없으면 그대로
        assertThat(m.dropByContext(Set.of(PYTHON), storage)).containsExactly(PYTHON);
        assertThat(list2.dropByContext(Set.of(sas, PYTHON), storage)).containsExactlyInAnyOrder(sas, PYTHON);
    }

    @Test
    void 가운뎃점은_모두_나열_기호() {
        for (String t : List.of("JavaㆍGoㆍKotlin", "Java・Go・Kotlin", "Java･Go･Kotlin", "Java•Go•Kotlin", "Java·Go·Kotlin"))
            assertThat(listed.match(t)).as(t).containsExactlyInAnyOrder(JAVA, GO, KOTLIN);
        assertThat(SkillMatcher.normalize("AㆍB・C•D")).isEqualTo("a·b·c·d");
    }

    @Test
    void 앞뒤_예외_규칙() {
        SkillMatcher m = new SkillMatcher(List.of(Alias.of("apache", 30, true), Alias.of("ios", 31, true),
                Alias.of("unity", 32, true), Alias.of("rails", 33, true), Alias.of("ruby", 34, true), Alias.of("aix", 35, true),
                Alias.of("swift", 36, true), Alias.of("node", 37, false), Alias.of("cursor", 38, false), Alias.of("react", 39, true)));
        assertThat(m.match("Apache Flink, Apache Hive")).isEmpty();
        assertThat(m.match("Apache, Nginx · Apache HTTP Server")).containsExactly(30);
        assertThat(m.match("Cisco IOS 장비, IOS-XE")).isEmpty();
        assertThat(m.match("Unity Catalog")).isEmpty();
        for (String t : List.of("Apache or Nginx", "Apache and Tomcat", "Apache mod_jk 연동", "Apache SSL 설정", "Apache WAS 연동",
                "Apache Nginx 운영", "Apache PHP", "Apache vhost 설정", "Apache reverse proxy", "Apache web server"))
            assertThat(m.match(t)).as(t).containsExactly(30);
        for (String t : List.of("Apache Struts", "Apache POI", "Apache Commons", "Apache ORC", "Apache Ozone"))
            assertThat(m.match(t)).as(t).isEmpty();
        assertThat(m.match("LLM guard rails, 평가")).isEmpty();          // 나열 모양이어도 guard 뒤는 아니다
        assertThat(m.match("guard-rails 설계, guardrails")).isEmpty();
        assertThat(m.match("Ruby, Rails")).containsExactlyInAnyOrder(33, 34);
        assertThat(m.match("Rails 7 경험")).containsExactly(33);
        // Swift: 은행 SWIFT 망·전문은 아니고, Swift 전문가·전문성·전문 지식은 iOS
        for (String t : List.of("SWIFT 전문(MT103)", "SWIFT 전문 송수신", "SWIFT 망 연동", "SWIFT gpi"))
            assertThat(m.match(t)).as(t).isEmpty();
        for (String t : List.of("Swift 전문가 우대", "Swift 전문성", "Swift 전문 지식 보유"))
            assertThat(m.match(t)).as(t).containsExactly(36);
        assertThat(m.match("Swift 전문 개발자")).isEmpty();               // 알고 놓치는 것: 은행 "SWIFT 전문 개발" 과 못 가름
        // node: 나열된 Node 는 Node.js, 쿠버네티스 노드는 아니다
        assertThat(m.match("React, Node 기반")).containsExactlyInAnyOrder(37, 39);
        assertThat(m.match("React/Node/Postgres")).containsExactlyInAnyOrder(37, 39);
        for (String t : List.of("worker node, master node 구성", "k8s node, pod 관리", "edge node / GPU node", "data node, compute node",
                "Kubernetes node, 쿠버네티스 node", "node pool, node group, node selector, node affinity 설정"))
            assertThat(m.match(t)).as(t).isEmpty();
        assertThat(m.match("Node 환경 이해")).isEmpty();                  // 알고 놓치는 것: 나열이 아닌 Node
        // cursor: 나열된 Cursor 는 AI 코딩 도구, DB 커서 이웃은 아니다
        assertThat(m.match("Claude, Cursor 사용")).containsExactly(38);
        assertThat(m.match("Cursor, Windsurf 등")).containsExactly(38);
        for (String t : List.of("(Procedure, Function, Cursor)", "(Procedure, Function, Package, Cursor, Trigger)",
                "Stored Procedure, Cursor, Trigger", "함수, Cursor 작성", "Cursor/Trigger"))
            assertThat(m.match(t)).as(t).isEmpty();
        assertThat(m.match("AIX(AI Transformation), AI 전환(AIX)")).isEmpty();
        assertThat(m.match("AIX, Linux 서버")).containsExactly(35);
        // 10/1 재검토 2차
        for (String t : List.of("시스코 장비 IOS 업그레이드", "Cisco 라우터 IOS 설정", "IOS XR 라우터"))
            assertThat(m.match(t)).as(t).doesNotContain(31);
        assertThat(m.match("iOS 앱 개발, Cisco 장비 운영")).containsExactly(31);
        for (String t : List.of("Apache License 2.0", "Apache 2.0 라이선스", "Apache-2.0 license", "Apache HttpClient", "Apache HTTP Client 사용"))
            assertThat(m.match(t)).as(t).doesNotContain(30);
        for (String t : List.of("Apache 2.4 운영", "Apache HTTP Server", "Apache httpd 설정"))
            assertThat(m.match(t)).as(t).containsExactly(30);
        for (String t : List.of("LLM safety rails", "hand rails", "side-rails"))
            assertThat(m.match(t)).as(t).doesNotContain(33);
        for (String t : List.of("SWIFT MT 메시지 처리", "SWIFT MT103", "SWIFT ISO 20022 전환"))
            assertThat(m.match(t)).as(t).doesNotContain(36);
        assertThat(m.match("Unity 카탈로그 관리")).isEmpty();
        // 쿠버네티스·하둡·블록체인 노드
        for (String t : List.of("Edge/Node 서버", "노드(Node), 파드(Pod)", "Pod, Node, Service", "Node, Pod 모니터링",
                "Kubernetes(Node/Pod)", "Name Node, Data Node", "Full Node, RPC Node", "Validator Node, React"))
            assertThat(m.match(t)).as(t).doesNotContain(37);
        assertThat(m.match("React, Node, Express")).containsExactlyInAnyOrder(37, 39);
    }

    @Test
    void 공백_여러_칸과_전각_글자() {
        assertThat(matcher.match("Spring  Boot 기반")).containsExactly(SPRING_BOOT);
        assertThat(matcher.match("Spring\tBoot")).containsExactly(SPRING_BOOT);
        assertThat(matcher.match("Ｊａｖａ 가능자")).containsExactly(JAVA);
        assertThat(new SkillMatcher(List.of(Alias.of(" ", JAVA, true))).match("아무 글")).isEmpty();
    }

    @Test
    void 붙은_우대_항목도_잡는다() {   // <li><span>보유역량</span>자바</li> → "보유역량: 자바"
        assertThat(matcher.match(DetailPageParser.lines(org.jsoup.Jsoup.parse(
                "<div><ul><li><span>보유역량</span>자바 개발 경험</li></ul></div>").selectFirst("div"))))
                .containsExactly(JAVA);
    }

    @Test
    void raw_파일_쓰고_읽기(@TempDir Path dir) throws Exception {
        RawStore store = new RawStore(dir);
        RawStore.Raw raw = new RawStore.Raw(List.of(new DetailPageParser.Tag(235, "Java")),
                List.of("Java 경험 3년"), List.of("Kotlin 우대"));
        store.save(1L, raw);
        assertThat(store.read(1L)).contains(raw);
        assertThat(store.delete(1L)).isTrue();
        assertThat(store.read(1L)).isEmpty();
        try (var files = java.nio.file.Files.list(dir)) {
            assertThat(files).noneMatch(f -> f.toString().endsWith(".tmp"));   // 임시 파일이 남지 않는다
        }
    }
}
