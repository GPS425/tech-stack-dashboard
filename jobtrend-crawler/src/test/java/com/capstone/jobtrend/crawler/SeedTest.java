package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.capstone.jobtrend.crawler.DetailPageParser.PostingDetail;
import com.capstone.jobtrend.crawler.SeedLoader.AliasRow;
import com.capstone.jobtrend.crawler.SeedLoader.DbSkill;
import com.capstone.jobtrend.crawler.SeedLoader.SkillPlan;
import com.capstone.jobtrend.crawler.SeedLoader.SkillRow;

/** resources/seed 의 CSV 가 DB 제약(UNIQUE·CHECK·길이)에 맞는지, 실제 공고에서 기대한 기술이 나오는지. */
class SeedTest {

    private static final Set<String> CATEGORIES =
            Set.of("LANGUAGE", "FRAMEWORK", "DATABASE", "CLOUD_INFRA", "DEVOPS_TOOL", "MOBILE", "AI_DATA", "ETC");

    private final List<SkillRow> skills = SeedLoader.readSkills();
    private final List<AliasRow> aliases = SeedLoader.aliases(skills, SeedLoader.readAliases());
    private final SkillMatcher m = matcher();

    private SkillMatcher matcher() {
        Map<String, Integer> ids = new HashMap<>();
        for (int i = 0; i < skills.size(); i++) ids.put(skills.get(i).name(), i);
        return new SkillMatcher(aliases.stream()
                .map(a -> SkillMatcher.Alias.of(a.alias(), ids.get(a.skill()), a.textMatch())).toList());
    }

    /** 글에서 찾은 기술 이름 */
    private Set<String> find(String text) {
        return m.match(text).stream().map(i -> skills.get(i).name()).collect(Collectors.toSet());
    }

    @Test
    void 기술_표가_제약에_맞는다() {
        assertThat(skills).extracting(SkillRow::name).doesNotHaveDuplicates().allMatch(n -> n.length() <= 50);
        assertThat(skills.stream().map(SkillRow::code).filter(c -> c != null).toList()).doesNotHaveDuplicates();
        assertThat(skills).allMatch(s -> CATEGORIES.contains(s.category()));
        // 기술 코드는 모두 코드표에 있다
        assertThat(SeedLoader.readCodeNames().keySet()).containsAll(
                skills.stream().map(SkillRow::code).filter(c -> c != null).toList());
        // 운영체제는 한 분류에, AI 코딩 도구는 개발 도구로
        Map<String, String> category = skills.stream().collect(Collectors.toMap(SkillRow::name, SkillRow::category));
        for (String os : List.of("Windows", "Linux", "Unix", "macOS", "Ubuntu", "CentOS", "Solaris", "AIX"))
            assertThat(category.get(os)).as(os).isEqualTo("CLOUD_INFRA");
        assertThat(category.get("AI 코딩 도구")).isEqualTo("DEVOPS_TOOL");
    }

    @Test
    void 별칭은_소문자이고_짧은_표기는_글에서_안_찾는다() {
        // 고르기 전의 CSV 그대로: 같은 별칭을 두 번 적은 줄이 없다(뒤의 줄이 말없이 이기지 않게)
        assertThat(SeedLoader.readAliases()).extracting(r -> SkillMatcher.normalize(r.alias()).trim()).doesNotHaveDuplicates();
        assertThat(aliases).extracting(AliasRow::alias).doesNotHaveDuplicates()
                .allMatch(a -> a.equals(a.toLowerCase()) && a.length() <= 60 && !a.isBlank());
        Map<String, Boolean> text = aliases.stream().collect(Collectors.toMap(AliasRow::alias, AliasRow::textMatch));
        for (String a : List.of("go", "c", "r", "es", "뷰", "깃", "펄", "노드", "넥스트", "다트", "node", "cursor"))
            assertThat(text.get(a)).as(a).isFalse();
        for (String a : List.of("java", "node.js", "nodejs", "node js", "rails", "go lang"))
            assertThat(text.get(a)).as(a).isTrue();
    }

    @Test
    void 별칭_표가_잘못되면_멈춘다() {
        List<SkillRow> s = List.of(new SkillRow(1, "Go", "LANGUAGE"), new SkillRow(2, "Java", "LANGUAGE"));
        // 같은 별칭이 다른 기술로 두 번
        assertThatThrownBy(() -> SeedLoader.aliases(s, List.of(new AliasRow("고", "Go", true), new AliasRow("고", "Java", true))))
                .hasMessageContaining("두 번");
        // 다른 기술의 이름과 같은 별칭
        assertThatThrownBy(() -> SeedLoader.aliases(s, List.of(new AliasRow("Go", "Java", false))))
                .hasMessageContaining("이름과 같은 별칭");
        // 자기 이름은 덮어도 된다(go 를 0 으로)
        assertThat(SeedLoader.aliases(s, List.of(new AliasRow("go", "Go", false))))
                .contains(new AliasRow("go", "Go", false));
        // text_match 는 0·1 만
        assertThatThrownBy(() -> SeedLoader.aliasRow(new String[] {"golang", "Go", "true"})).hasMessageContaining("text_match");
        assertThatThrownBy(() -> SeedLoader.aliasRow(new String[] {"golang", "Go", "2"})).hasMessageContaining("text_match");
        assertThat(SeedLoader.aliasRow(new String[] {"golang", "Go", " 1 "}).textMatch()).isTrue();
    }

    @Test
    void 기술_코드를_바꾸거나_지워도_새로_넣지_않는다() {   // 넣으면 이름 UNIQUE(ORA-00001)로 수집이 멈추던 것
        List<DbSkill> db = List.of(new DbSkill(10, 235, "Java"), new DbSkill(11, null, "FastAPI"), new DbSkill(12, 223, "Go"));
        // 코드 바꾸기·지우기·붙이기: 이름으로 찾아 고친다. 바뀌는 코드는 먼저 비운다
        SkillPlan p = SeedLoader.plan(List.of(new SkillRow(999, "Java", "LANGUAGE"), new SkillRow(500, "FastAPI", "FRAMEWORK"),
                new SkillRow(null, "Go", "LANGUAGE")), db);
        assertThat(p.insert()).isEmpty();
        assertThat(p.update()).containsOnlyKeys(10, 11, 12);
        assertThat(p.update().get(10).code()).isEqualTo(999);
        assertThat(p.update().get(12).code()).isNull();
        assertThat(p.clearCode()).containsExactlyInAnyOrder(10, 12);
        assertThat(p.deactivate()).isEmpty();
    }

    @Test
    void 코드가_다른_이름으로_옮겨_가면_먼저_비운다() {
        List<DbSkill> db = List.of(new DbSkill(10, 235, "Java"), new DbSkill(11, 236, "JavaScript"),
                new DbSkill(12, 300, "Old"), new DbSkill(13, null, "New"));
        // 두 기술이 코드를 서로 바꿈, CSV 에서 뺀 Old 의 코드 300 을 DB 에 이미 있는 New 가 가져감
        SkillPlan p = SeedLoader.plan(List.of(new SkillRow(236, "Java", "LANGUAGE"), new SkillRow(235, "JavaScript", "LANGUAGE"),
                new SkillRow(300, "New", "ETC")), db);
        assertThat(p.clearCode()).containsExactlyInAnyOrder(10, 11, 12);
        assertThat(p.update()).containsOnlyKeys(10, 11, 13);
        assertThat(p.update().get(13).code()).isEqualTo(300);
        assertThat(p.insert()).isEmpty();
        assertThat(p.deactivate()).containsExactly(12);
        // 코드를 쥔 행의 이름이 CSV 에 남아 있으면 그 행을 뺏지 않고 새로 넣는다
        p = SeedLoader.plan(List.of(new SkillRow(null, "Old", "ETC"), new SkillRow(300, "Brand New", "ETC")), db.subList(2, 3));
        assertThat(p.update().get(12).name()).isEqualTo("Old");
        assertThat(p.clearCode()).containsExactly(12);
        assertThat(p.insert()).extracting(SkillRow::name).containsExactly("Brand New");
    }

    @Test
    void 이름을_바꾸면_코드로_찾고_CSV_에서_뺀_기술은_끈다() {
        List<DbSkill> db = List.of(new DbSkill(10, 258, "NodeJS"), new DbSkill(11, null, "Zustand"), new DbSkill(12, 159, "Nginx"));
        SkillPlan p = SeedLoader.plan(List.of(new SkillRow(258, "Node.js", "FRAMEWORK"), new SkillRow(null, "Nginx", "CLOUD_INFRA")), db);
        assertThat(p.update().get(10).name()).isEqualTo("Node.js");      // 같은 행 이름만 바꿈
        assertThat(p.update().get(12).code()).isNull();
        assertThat(p.insert()).isEmpty();
        assertThat(p.deactivate()).containsExactly(11);                // 지우지 않고 끈다
        assertThat(p.clearCode()).containsExactly(12);
        // 다시 넣으면 이름으로 찾아 켠다
        assertThat(SeedLoader.plan(List.of(new SkillRow(null, "Zustand", "FRAMEWORK")), db).update()).containsOnlyKeys(11);
    }

    @Test
    void 글에서_기술을_찾는다() {   // 저장한 HTML 이 없어도 돈다
        assertThat(find("Java, Spring Boot, JPA 기반 서버 개발 · MySQL, Redis · AWS(EC2, S3), Docker, k8s"))
                .containsExactlyInAnyOrder("Java", "Spring Boot", "JPA", "MySQL", "Redis", "AWS", "Docker", "Kubernetes");
        assertThat(find("Apache Kafka, React Native 앱, 스프링 부트")).containsExactlyInAnyOrder("Kafka", "React Native", "Spring Boot");
        // 9/29 검수: Claude Code 는 코딩 도구, 그냥 Claude·OpenAI 는 LLM, 자격증 MCP 는 제외
        assertThat(find("Claude Code, Cursor 등 AI 코딩 도구 활용 경험")).containsExactly("AI 코딩 도구");
        assertThat(find("OpenAI, Claude 등 LLM API 사용 경험, RAG 구축")).containsExactlyInAnyOrder("LLM", "RAG");
        assertThat(find("자격증: MCP (Microsoft Certified Professional)")).isEmpty();
        assertThat(find("MCP 서버 개발 경험")).containsExactly("MCP");
    }

    @Test
    void 한두_글자_기술은_나열될_때만() {
        // R: 연구개발·등록상표·역할 표기는 아니다
        for (String t : List.of("(R&D)", "R&D 경험", "Python, R&D 경험", "Microsoft(R) Office", "R/R 정의"))
            assertThat(find(t)).as(t).doesNotContain("R");
        assertThat(find("Python, R 활용 통계 분석")).containsExactlyInAnyOrder("Python", "R");
        // C: 클라이언트/서버, 저작권, A/B/C
        for (String t : List.of("C/S 환경", "C/S, Web", "Copyright (C) 회사", "A/B/C 테스트"))
            assertThat(find(t)).as(t).doesNotContain("C");
        assertThat(find("개발 언어(C, C++)")).containsExactlyInAnyOrder("C", "C++");
        assertThat(find("C 및 C++ 개발")).containsExactlyInAnyOrder("C", "C++");
        assertThat(find("Embedded C 개발")).containsExactly("C");
        assertThat(find("Embedded C++ 개발")).containsExactly("C++");
        assertThat(find("Linux C 프로그래밍")).containsExactlyInAnyOrder("Linux", "C");
        // Go: SAP 의 Go-Live, GO/NO-GO, 지운 별칭(SAP·Java) 바로 뒤의 go
        assertThat(find("SAP Go-Live 경험")).containsExactly("SAP");
        assertThat(find("GO/NO-GO 판단")).isEmpty();
        assertThat(find("Java go 백엔드")).containsExactly("Java");
        assertThat(find("Java, Go, Kotlin 중 하나")).containsExactlyInAnyOrder("Java", "Go", "Kotlin");
        // 가운뎃점 여러 가지
        for (String t : List.of("JavaㆍGoㆍKotlin", "Java・Go・Kotlin", "Java•Go•Kotlin", "Java∙Go∙Kotlin"))
            assertThat(find(t)).as(t).containsExactlyInAnyOrder("Java", "Go", "Kotlin");
    }

    @Test
    void 다른_뜻과_겹치는_표기는_안_잡는다() {
        // DB 커서
        assertThat(find("Oracle PL/SQL (Procedure, Function, Cursor) 작성")).containsExactlyInAnyOrder("Oracle", "PL/SQL");
        assertThat(find("Stored Procedure, Cursor, Trigger 이해")).isEmpty();
        assertThat(find("Cursor AI, Cursor IDE, Cursor 에디터 활용")).containsExactly("AI 코딩 도구");
        // 아파치 재단 프로젝트
        for (String t : List.of("Apache Flink", "Apache Iceberg", "Apache Hive, Apache NiFi", "아파치 카프카"))
            assertThat(find(t)).as(t).doesNotContain("Apache");
        assertThat(find("Apache, Nginx 운영")).containsExactlyInAnyOrder("Apache", "Nginx");
        assertThat(find("Apache HTTP Server, 아파치 웹서버 설정")).containsExactly("Apache");
        assertThat(find("Apache 2.4 설정")).containsExactly("Apache");
        // 쿠버네티스 노드
        for (String t : List.of("worker node", "Kubernetes 클러스터 node 관리", "edge node"))
            assertThat(find(t)).as(t).doesNotContain("Node.js");
        assertThat(find("Node.js, NodeJS, Node JS")).containsExactly("Node.js");
        // 9/30 검수 오탐
        assertThat(find("Cisco IOS 장비")).doesNotContain("iOS");
        assertThat(find("SAN/NAS/SAS 스토리지")).doesNotContain("SAS");
        assertThat(find("SAS, SPSS, R 등 통계 도구 활용 경험")).contains("SAS");   // 통계 SAS 는 그대로
        assertThat(find("SAS 기반 통계 분석")).contains("SAS");
        assertThat(find("SWIFT 전문 처리")).doesNotContain("Swift");
        assertThat(find("Unity Catalog")).doesNotContain("Unity");
        assertThat(find("스칼라 연산")).doesNotContain("Scala");
        assertThat(find("LLM guard rails")).containsExactly("LLM");
        assertThat(find("AIX(AI Transformation)")).doesNotContain("AIX");
        // 그 기술일 때는 잡는다
        assertThat(find("iOS 앱 개발")).containsExactly("iOS");
        assertThat(find("SAS 통계 분석")).containsExactly("SAS");
        assertThat(find("Swift, Kotlin 앱 개발")).containsExactlyInAnyOrder("Swift", "Kotlin");
        assertThat(find("Unity 엔진, 유니티")).containsExactly("Unity");
        assertThat(find("스칼라 개발")).containsExactly("Scala");
        assertThat(find("Ruby on Rails")).containsExactly("Ruby on Rails");
        assertThat(find("Ruby, Rails")).containsExactlyInAnyOrder("Ruby", "Ruby on Rails");
        assertThat(find("AIX 서버 운영")).containsExactly("AIX");
    }

    @Test
    void 빠졌던_표기() {
        assertThat(find("VC++")).containsExactly("C++");
        assertThat(find("MS VC++ 개발")).containsExactly("C++");
        assertThat(find("Postgre")).containsExactly("PostgreSQL");
        assertThat(find("Postgre SQL")).containsExactly("PostgreSQL");
        assertThat(find("SQLServer")).containsExactly("MSSQL");
        for (String t : List.of("노드.js", "노드 js")) assertThat(find(t)).as(t).containsExactly("Node.js");
        assertThat(find("뷰.js")).containsExactly("Vue.js");
        for (String t : List.of("넥스트js", "넥스트.js")) assertThat(find(t)).as(t).containsExactly("Next.js");
        assertThat(find("엘라스틱 서치")).containsExactly("Elasticsearch");
        assertThat(find("구글 클라우드")).containsExactly("GCP");
        assertThat(find("구글 클라우드 플랫폼")).containsExactly("GCP");
        assertThat(find("유닉스")).containsExactly("Unix");
        assertThat(find("깃헙")).containsExactly("Git");
        assertThat(find("와이어샤크")).containsExactly("Wireshark");
        assertThat(find("OpenJDK")).containsExactly("Java");
        assertThat(find("ADO.NET")).containsExactly(".NET");
        assertThat(find("JavaFX")).containsExactly("Java");
    }

    /** 10/1 재검토: 공고 문장 547줄을 예전·지금 매처로 돌려 본 결과. 되찾은 것과 계속 막아야 하는 것 */
    @Test
    void 재검토_되찾은_표기() {
        Map<String, String> want = new java.util.LinkedHashMap<>();
        want.put("React, Node 기반 풀스택 개발 경험 우대", "Node.js");
        want.put("React/Node/Postgres 스택 경험", "Node.js");
        want.put("Node, Express 경험", "Node.js");
        want.put("Rails 경험이 있으신 분", "Ruby on Rails");
        want.put("Rails 7 경험", "Ruby on Rails");
        want.put("Ruby/Rails 경험", "Ruby on Rails");
        want.put("Swift 전문가 우대", "Swift");
        want.put("Swift 전문 지식 보유", "Swift");
        want.put("ChatGPT, Cursor 등 생성형 AI 도구를 업무에 활용하시는 분", "AI 코딩 도구");
        want.put("Claude, Cursor 를 활용한 생산성 향상 경험", "AI 코딩 도구");
        want.put("Cursor, Windsurf 등 AI IDE 사용", "AI 코딩 도구");
        want.put("Apache or Nginx 리버스 프록시 구성 경험", "Apache");
        want.put("Apache and Tomcat 설정", "Apache");
        want.put("Apache mod_jk 연동 경험", "Apache");
        want.put("Apache SSL 인증서 설정 경험", "Apache");
        want.put("Apache WAS 연동 경험", "Apache");
        want.put("Apache Nginx 운영", "Apache");
        want.put("Go lang 개발", "Go");
        want.put("ㆍGo 기반 서버 개발 경험", "Go");
        want.put("• Go 백엔드 개발 경험", "Go");
        want.put("ㆍR 활용 통계 분석", "R");
        want.put("Java 또는 Go로 대규모 분산 시스템 개발 경험", "Go");
        want.put("R 또는 Python을 이용한 통계 분석 경험", "R");
        want.put("C 및 C++ 개발 경험 3년 이상", "C");
        want.put("임베디드 C 프로그래밍 가능자", "C");
        want.put("JavaㆍGoㆍKotlin 중 하나 이상", "Go");
        want.put("Postgre DB 운영 경험", "PostgreSQL");
        want.put("MFC, VC++ 기반 윈도우 애플리케이션 개발 경험", "C++");
        want.forEach((t, skill) -> assertThat(find(t)).as(t).contains(skill));
        // 알고 놓치는 것(테스트로 남겨 둠): 글머리 없이 나열도 아닌 Node·Cursor, 은행과 못 가르는 "Swift 전문 개발자",
        // 공백으로만 나열한 "Python Go Java"
        assertThat(find("Node 기반 SSR 서버 운영 경험")).doesNotContain("Node.js");
        assertThat(find("Cursor 사용 경험")).doesNotContain("AI 코딩 도구");
        assertThat(find("Swift 전문 개발자로 앱스토어 출시 경험")).doesNotContain("Swift");
        assertThat(find("Python Go Java 중 하나 이상 능숙")).doesNotContain("Go");
    }

    /** 10/1 재검토 2차: 새로 쓴 공고 문장 311줄과 오탐 후보 112줄에서 찾은 것 */
    @Test
    void 재검토2_되찾은_표기() {
        Map<String, String> want = new java.util.LinkedHashMap<>();
        want.put("C/Go 개발 경험", "Go");
        want.put("Go/C 기반 시스템 개발", "C");
        want.put("C/R/Python 활용", "R");
        want.put("통계 언어(R) 활용 능력", "R");
        want.put("데이터 분석 언어 (R) 사용 능숙자", "R");
        want.put("TypeScript + Node 개발 경험", "Node.js");
        want.put("• 프로그래밍 언어: C", "C");
        want.put("• 언어: Go", "Go");
        want.put("Go와 Rust 경험", "Go");
        want.put("C와 C++ 개발 경험", "C");
        want.put("R과 Python 사용 가능자", "R");
        want.put("ㆍCursor 를 활용한 개발 생산성 향상 경험", "AI 코딩 도구");
        want.put("• Node 18 이상 사용 경험", "Node.js");
        want.put("• R Shiny 대시보드 개발 경험", "R");
        want.put("• C(Embedded) 펌웨어 개발", "C");
        want.put("SAS, SPSS, R 등 통계 패키지 활용 능력", "SAS");
        want.put("SAS 9.4 프로그래밍", "SAS");
        want.put("임상 통계 분석(SAS) 3년 이상", "SAS");
        want.put("SAS 3년 이상 경험", "SAS");
        want.put("Apache 서버 운영", "Apache");
        want.put("AWS S3, EC2 운영", "AWS");
        want.forEach((t, skill) -> assertThat(find(t)).as(t).contains(skill));
    }

    @Test
    void 재검토2_막는_오탐() {
        Map<String, String> not = new java.util.LinkedHashMap<>();
        for (String t : List.of("• Go Live 일정 관리 및 cut-over 계획 수립", "- SAP 구축 및 Go Live 지원", "ㆍGo To Market 전략 수립",
                "- Go/No Go 판단 회의", "- Go/Stop 판단 기준 수립")) not.put(t, "Go");
        for (String t : List.of("• Cursor 기반 페이지네이션 API 설계", "ㆍCursor 기반 무한 스크롤 구현", "• 커서(Cursor) 기반 대량 처리 최적화",
                "ㆍExplicit Cursor, Ref Cursor 활용 가능자", "- Offset/Cursor 기반 페이징 구현 경험", "- MongoDB Cursor, Aggregation 이해"))
            not.put(t, "AI 코딩 도구");
        for (String t : List.of("• C 사 프로젝트 수행 경험", "- Plan A, B, C 수립", "- A, B, C 형 설계", "Copyright (C) 2024"))
            not.put(t, "C");
        for (String t : List.of("ㆍR 값 산출 및 검증", "Microsoft(R) Office")) not.put(t, "R");
        for (String t : List.of("• Node 증설 및 교체 작업", "- Pod, Node, Service 리소스 이해", "- Hadoop Name Node, Data Node 운영",
                "- 블록체인 풀노드(Full Node) 운영 경험")) not.put(t, "Node.js");
        for (String t : List.of("ㆍSAS Expander 및 HBA 설정 경험자", "ㆍPCIe Gen5, SAS-4 인터페이스 분석 경험", "• SAS 12Gbps 링크 디버깅 경험",
                "• JBOD, RAID 컨트롤러(SAS 3108) 이해", "- SAS 드라이브 FW 이슈 재현 및 분석", "- 12G SAS HBA 경험",
                "- SAS (Serial Attached SCSI) 이해", "- HDD(SAS) 교체", "- SAS 및 SATA 호환성")) not.put(t, "SAS");
        not.put("• MCU(Infineon TC3xx, NXP S32K) 펌웨어 개발", "AWS");
        for (String t : List.of("- 시스코 장비 IOS 업그레이드 경험", "- Cisco 라우터 IOS 설정")) not.put(t, "iOS");
        for (String t : List.of("• 아파치 니파이 운영 경험", "- Apache 2.0 라이선스", "- Apache License 2.0 오픈소스 기여", "- Apache HttpClient 사용"))
            not.put(t, "Apache");
        not.put("LLM safety rails 설계", "Ruby on Rails");
        not.put("SWIFT MT 메시지 처리", "Swift");
        not.forEach((t, skill) -> assertThat(find(t)).as(t).doesNotContain(skill));
        // 줄이 바뀌면 나열이 아니다
        assertThat(m.match(List.of("- 슬로건 Ready to Go", "• 협업 경험"))).isEmpty();
    }

    @Test
    void 스토리지_공고의_SAS_는_공고_전체로_뺀다() {
        List<String> storage = List.of("ㆍSSD 펌웨어 검증 업무 경력 2년 이상", "ㆍSAS 링크 디버깅 경험", "ㆍ프로토콜 분석기 사용 경험");
        Set<Integer> found = m.match(storage);
        assertThat(found.stream().map(i -> skills.get(i).name())).contains("SAS");
        assertThat(m.dropByContext(found, storage).stream().map(i -> skills.get(i).name())).doesNotContain("SAS");
        List<String> stats = List.of("ㆍSAS, SPSS 활용 통계 분석", "ㆍSSD 대용량 데이터 처리");
        assertThat(m.dropByContext(m.match(stats), stats).stream().map(i -> skills.get(i).name())).contains("SAS");
    }

    @Test
    void 코드의_별칭_규칙이_별칭_표와_맞다() {
        Map<String, Boolean> text = aliases.stream().collect(Collectors.toMap(AliasRow::alias, AliasRow::textMatch));
        // LIST_ONLY 는 text_match=0 이어야 나열될 때만 찾는다(1 이면 모든 "c"·"go" 가 잡힌다). 별칭 표에서 지우면 기술 이름이 1 로 돌아간다
        for (String a : SkillMatcher.LIST_ONLY) assertThat(text.get(a)).as(a).isFalse();
        // 앞뒤 예외의 키는 별칭이어야 한다(별칭을 고치면 예외가 말없이 꺼진다)
        assertThat(text.keySet()).containsAll(SkillMatcher.EXCEPT.keySet());
    }

    @Test
    void 재검토_계속_막는_오탐() {
        Map<String, String> not = new java.util.LinkedHashMap<>();
        for (String t : List.of("연구개발(R&D) 과제 수행 경험", "R/R 정의 및 일정 관리 경험", "Microsoft(R) Office 활용 능력",
                "ㆍR&D 과제 수행", "• R 및 D 업무")) not.put(t, "R");
        for (String t : List.of("고객사 C/S 대응 경험", "C/S 및 Web 기반 시스템 개발 경험", "PowerBuilder 기반 C/S 프로그램 유지보수 경험",
                "Copyright (C) 관련 업무 경험", "A/B/C 등급 분류 체계 이해", "A, B, C 등급 고객 관리 경험", "ㆍ 고객 C/S 대응",
                "ㆍC레벨 임원 대상 보고 경험", "• C레벨 커뮤니케이션 경험", "・C레벨 보고", "· C레벨 보고 경험", "•C급 인력 관리",
                "C 레벨 대상 보고 경험")) not.put(t, "C");
        for (String t : List.of("GO/NO-GO 의사결정 지원 경험", "분석/설계/개발/테스트/Go-Live 전 단계 수행 경험", "SAP Go-Live 경험 우대",
                "SAP go-live 및 안정화 경험", "ㆍGo-Live 지원", "go 백엔드 경험")) not.put(t, "Go");
        for (String t : List.of("PL/SQL(Procedure, Function, Cursor) 작성 능력",
                "PL/SQL(Procedure, Function, Package, Cursor, Trigger) 작성 능력", "PL/SQL(Function, Cursor) 작성"))
            not.put(t, "AI 코딩 도구");
        for (String t : List.of("k8s node 장애 대응 경험", "worker node, master node 구성 및 관리 경험", "edge node 배포 경험",
                "GPU node 관리", "node pool, node group 설정")) not.put(t, "Node.js");
        for (String t : List.of("Apache Flink, Apache Iceberg 경험 우대", "Apache NiFi, Apache Airflow 등 워크플로우 도구 경험",
                "Apache JMeter 를 이용한 성능 테스트 경험", "Apache Hadoop, Apache Hive 운영",
                "Apache Kafka, Apache Flink, Apache Spark 기반 실시간 처리", "Apache Superset 대시보드 구축 경험",
                "Apache Druid 운영 경험", "Apache Struts 보안 패치", "Apache POI 엑셀 처리", "Apache Commons 라이브러리",
                "아파치 카프카, 스파크 운영 경험", "아파치 에어플로우 DAG 작성 경험")) not.put(t, "Apache");
        for (String t : List.of("은행 SWIFT 전문(MT103) 개발 경험", "은행 SWIFT 망 연동 경험", "SWIFT 전문 송수신 개발")) not.put(t, "Swift");
        for (String t : List.of("Cisco IOS, IOS-XE 설정 경험", "iOS-XR 라우터", "IOS XE 업그레이드")) not.put(t, "iOS");
        for (String t : List.of("SAN/NAS/SAS 스토리지 이해", "SAS HDD 교체 경험", "SAS 디스크 증설 경험", "SAN, SAS 스토리지",
                "SATA, SAS, NVMe 인터페이스 검증 경험", "NVMe, SAS 프로토콜 이해", "PCIe/SAS 컨트롤러 펌웨어 검증",
                "SAS 인터페이스 테스트 경험")) not.put(t, "SAS");
        for (String t : List.of("Databricks, Unity Catalog 운영 경험", "유니티 카탈로그 운영 경험")) not.put(t, "Unity");
        for (String t : List.of("스칼라 값 연산 등 선형대수 이해", "스칼라값 계산")) not.put(t, "Scala");
        for (String t : List.of("LLM guard rails, 평가 체계 구축 경험", "guard-rails 설계 경험", "guardrails 설계 경험")) not.put(t, "Ruby on Rails");
        for (String t : List.of("AIX(AI Transformation) 프로젝트 경험 우대", "AI 전환(AIX) 컨설팅 경험", "AIX (AI 기반 전환) 컨설팅"))
            not.put(t, "AIX");
        not.forEach((t, skill) -> assertThat(find(t)).as(t).doesNotContain(skill));
        // 같은 줄의 다른 기술은 그대로
        assertThat(find("SAP Go-Live 경험 우대")).containsExactly("SAP");
        assertThat(find("worker node, master node 구성 및 관리 경험")).isEmpty();
        assertThat(find("Apache, Nginx, Tomcat 설정 및 튜닝 경험")).containsExactlyInAnyOrder("Apache", "Nginx", "Tomcat");
    }

    @Test
    void 실제_공고에서_기술을_찾는다() throws Exception {   // 저장한 HTML 이 없으면 건너뜀
        PostingDetail d = new DetailPageParser().parse(SavedHtml.load("ajax-54749084.html"));
        Set<String> found = m.match(d.required()).stream().map(i -> skills.get(i).name()).collect(Collectors.toSet());
        assertThat(found).contains("Python");
    }
}
