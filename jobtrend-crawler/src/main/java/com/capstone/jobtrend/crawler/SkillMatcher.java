package com.capstone.jobtrend.crawler;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 자격요건·우대사항 글에서 기술을 찾는다.
 * Java 19부터 정규식 \b 는 영문·숫자만 글자로 봐서 한글 별칭과 "C++"를 놓치므로 앞뒤를 직접 막는다.
 * 태그는 사람인 코드로 바로 맞추므로 이 클래스를 거치지 않는다.
 */
public class SkillMatcher {

    public record Alias(String alias, int skillId, boolean textMatch, Pattern pattern) {
        public static Alias of(String alias, int skillId, boolean textMatch) {
            String a = normalize(alias).trim();
            return new Alias(a, skillId, textMatch, compile(a));
        }
    }

    /**
     * 글에서는 안 찾는(text_match=0) 표기 가운데, 다른 기술과 나열됐을 때만 인정하는 것. CSV 에서 반드시 0 이어야 한다(SeedTest 가 본다).
     * "Java, Go, Kotlin", "C, Java", "C 및 C++" 는 잡고 "go 백엔드"·"A등급" 같은 일반 글은 안 잡는다(9/29 검수: Go 26건 중 20건을 놓침).
     * node 는 "React, Node"·"React/Node" 처럼 나열될 때만. "worker node, master node"·"k8s node" 는 EXCEPT 가 막는다.
     * cursor 는 "Claude, Cursor" 처럼 나열될 때만. DB 커서 "(Procedure, Function, Cursor)" 는 EXCEPT 가 막는다.
     */
    static final Set<String> LIST_ONLY = Set.of("go", "c", "r", "node", "cursor");

    /** "c/c++" 는 C 와 C++ 두 기술이다. 긴 표기부터 차지하는 규칙 때문에 C++ 만 남던 것을 둘로 풀어 둔다. */
    private static final Pattern C_AND_CPP = Pattern.compile("(?<![a-z0-9])c\\s*/\\s*c\\+\\+");

    private final List<Alias> longestFirst;
    private final List<Alias> listOnly;
    /** 별칭 "sas" 의 기술(dropByContext 용). 별칭 표에 없으면 null */
    private final Integer sasId;

    /** ④를 돌릴 때마다 별칭 표를 DB에서 다시 읽어 새로 만든다. */
    public SkillMatcher(List<Alias> aliases) {
        this.longestFirst = aliases.stream()
                .filter(Alias::textMatch)                      // 뷰·깃·펄·노드·넥스트·다트·es 는 태그에서만, go·c·r·node·cursor 는 나열될 때만
                .filter(a -> !a.alias().isBlank())             // 공백 별칭은 모든 공고에 걸린다
                .sorted(Comparator.comparingInt((Alias a) -> a.alias().length()).reversed())
                .toList();                                     // "자바스크립트"가 "자바"보다, "spring boot"가 "spring"보다 먼저
        this.listOnly = aliases.stream()
                .filter(a -> !a.textMatch() && LIST_ONLY.contains(a.alias()))
                .map(a -> new Alias(a.alias(), a.skillId(), false, compileListed(a.alias())))
                .toList();
        this.sasId = aliases.stream().filter(a -> a.alias().equals("sas")).map(Alias::skillId).findFirst().orElse(null);
    }

    /**
     * 앞에 오면 나열인 자리: , / ( 와 글 가운데의 ·, "A + B" 의 +, "및" "또는" "embedded"(Embedded C), "언어:"·"스택:".
     * 줄 앞 글머리(• ㆍ ・ 가 · 로 바뀐 것)는 여기 들지 않는다(BULLET). 줄바꿈은 나열 기호가 아니다([ ]? 만 허용).
     */
    private static final String LIST_BEFORE = "(?<=(?:[,/(]|[^\\n ·][ ]?·|[a-z0-9가-힣)][ ]?\\+|및|또는|embedded|임베디드"
            + "|(?:언어|language|languages|스택|stack|skills?|기술)[ ]?:)[ ]?)";
    /** 뒤에 오면 나열인 자리: , / · ) + "및" "또는" "프로그래밍"(Linux C 프로그래밍), 조사 "와·과"(Go와 Rust, C와 C++) */
    private static final String LIST_AFTER = "(?=[ ]?(?:[,/·)+]|및|또는|프로그래밍)|(?:와|과)[ ])";
    /** 줄 앞 글머리 바로 뒤 */
    private static final String BULLET_BEFORE = "(?<=(?:^|\\n)[ ]?·[ ]?)";
    /**
     * 줄 앞 글머리 뒤의 별칭은 나열 기호나 기술 문맥 낱말·버전이 뒤따를 때만: "• Go 백엔드 개발"·"ㆍR 활용"·"• Node (Express)"·"• Node 18 이상" 은 잡고
     * "• Go Live"·"ㆍGo To Market"·"• C 사 프로젝트"·"ㆍR 값 산출"·"• Node 증설" 은 안 잡는다.
     */
    private static final String BULLET_AFTER = "(?=[ ]?(?:\\(|개발|언어|서버|기반|백엔드|프로그래밍|활용|사용|경험|능숙|가능|스크립트|런타임|환경|코드|모듈"
            + "|애플리케이션|앱|패키지|펌웨어|드라이버|shiny|(?:를|을|로|으로)(?![가-힣])"
            + "|\\d{1,2}(?:\\.[\\dx]+)*[ ]?(?:이상|버전|version|\\+|[,/·)]|기반|환경|사용|경험|개발)))";   // "• Node 18 이상"

    /**
     * 나열로 보는 자리(LIST_BEFORE·LIST_AFTER, 줄 앞 글머리는 BULLET_AFTER 까지).
     * 별칭에 붙은 글자가 영문·숫자·+·#·&·- 면 다른 낱말(golang, c++, c#, R&D, Go-Live, NO-GO).
     * 한 글자 코드와 슬래시로 붙은 것(C/S, R/R, A/B/C)은 기술이 아니다. 단 다른 LIST_ONLY 한 글자 코드와는 된다(C/R, C/Go).
     * 한 글자 코드는 더: 이웃이 모두 한 글자인 나열(Plan A, B, C), 영문 바로 뒤 괄호 하나(Microsoft(R)·Copyright (C), 등록상표·저작권),
     * 뒤에 레벨·급·등급·사·값·형·팀·군·학점(C레벨, C 사, R 값)이 오면 기술이 아니다. "통계 언어(R)" 는 R.
     */
    static Pattern compileListed(String alias) {
        String a = Pattern.quote(alias);
        Except e = EXCEPT.getOrDefault(alias, NONE);
        String partners = LIST_ONLY.stream().filter(x -> x.length() == 1 && !x.equals(alias)).sorted().collect(Collectors.joining());
        String other = partners.isEmpty() ? "[a-z]" : "[a-z&&[^" + partners + "]]";   // 나열 상대로 안 되는 한 글자
        String lone = "(?<![a-z0-9])" + other;                                          // 앞뒤가 영문·숫자가 아닌 한 글자
        String endLone = "(?![a-z0-9+#])";
        boolean single = alias.length() == 1;
        String start = "(?<![a-z0-9가-힣&-])(?<!" + lone + "/)"
                + (single ? "(?<!" + lone + "[ ]?[,·][ ]?)" : "") + e.before();
        String end = "(?![a-z0-9+#&-])(?!/" + other + endLone + ")"
                + (single ? "(?![ ]?[,·][ ]?" + other + endLone + ")"
                        + "(?!(?<=[a-z0-9][ ]?\\(" + a + ")\\))"
                        + "(?![ ]?(?:레벨|급|등급|값|팀|군|학점|사(?!용)|형(?!식)))" : "")
                + e.after();
        return Pattern.compile(start + "(?:" + LIST_BEFORE + a + "|" + a + LIST_AFTER + "|" + BULLET_BEFORE + a + BULLET_AFTER + ")" + end);
    }

    /**
     * 앞(before)·뒤(after)에 이 글이 붙으면 그 기술이 아닌 별칭. 앞은 뒤를 보는 정규식이라 길이가 정해져야 한다.
     * 자격증 "MCP (Microsoft Certified Professional)", "Apache Flink"·"Apache License", "Cisco IOS"·"시스코 장비 IOS",
     * "SATA, SAS, NVMe"·"SAS Expander", 은행 "SWIFT 전문·MT", 쿠버네티스 "worker node", DB 커서 "Function, Cursor"·"커서(Cursor)" 등.
     * 늘어나면 별칭 표에 칸으로 옮길 것. 키는 별칭 표에 있어야 한다(SeedTest 가 본다).
     */
    record Except(String before, String after) {
        static Except of(String before, String after) {
            return new Except(before == null ? "" : "(?<!" + before + ")", after == null ? "" : "(?!" + after + ")");
        }
    }

    private static final Except NONE = new Except("", "");

    static final Map<String, Except> EXCEPT = Map.ofEntries(
            Map.entry("mcp", Except.of(null, "\\s*\\(?\\s*microsoft")),
            // "Apache Flink·Hive·NiFi" 같은 아파치 재단 프로젝트. 웹 서버는 "Apache, Nginx"·"Apache HTTP"·"Apache 설정" 으로 쓴다.
            // 뒤의 영문이 웹 서버 쪽 낱말(WAS·SSL·mod_jk·or·and·Nginx·PHP·vhost·reverse proxy)이면 웹 서버. HttpClient·라이선스는 아니다
            Map.entry("apache", Except.of(null,
                    "\\s+(?!http(?![ ]?(?:client|components|core))|web|server|was|ssl|mod_|or(?![a-z])|and(?![a-z])|nginx|php|vhost"
                    + "|reverse|proxy)[a-z]|[\\s\\d.v-]*(?:라이선스|라이센스|license|licence)")),
            Map.entry("아파치", Except.of(null, "\\s*(?:카프카|스파크|하둡|톰캣|하이브|플링크|에어플로우|나이파이|니파이|주키퍼|카산드라"
                    + "|아이스버그|제플린|솔라|루씬|드루이드|슈퍼셋|펄사|스톰|카멜|빔)")),
            // 시스코 장비 OS: "Cisco IOS", "시스코 장비 IOS", "Cisco 라우터 IOS 설정"(앞 두 낱말까지), "IOS-XE"·"IOS XR"
            Map.entry("ios", Except.of("(?:cisco|시스코)(?:[ ]?|[ ][^ \\n]{1,12}[ ]|[ ][^ \\n]{1,12}[ ][^ \\n]{1,12}[ ])",
                    "[\\s-]?x[er](?![a-z])")),
            // SSD·스토리지 검증 공고의 인터페이스("SATA, SAS, NVMe", "PCIe/SAS", "HDD(SAS)", "12G SAS")와 부품("SAS Expander·HBA·드라이브",
            // "SAS-4", "SAS 3108", "SAS 12Gbps", "SAS (Serial Attached SCSI)")은 통계 SAS 가 아니다. 공고 전체로는 dropByContext
            Map.entry("sas", Except.of("(?:(?:san|nas|sata|scsi|ssd|hdd|nvme|pcie|ufs|emmc)\\s?[/,·(]\\s?|\\d{1,3}\\s?g(?:bps)?\\s?)",
                    "\\s*(?:[/,·]\\s*(?:san|nas|sata|scsi|ssd|hdd|nvme|pcie|ufs|emmc)(?![a-z])|및\\s?sata|스토리지|storage|디스크|disk|hdd|ssd"
                    + "|케이블|cable|컨트롤러|controller|인터페이스|interface|프로토콜|protocol|expander|hba|드라이브|drive"
                    + "|\\(?\\s?serial|\\d{1,3}\\s?g(?:bps)?(?![a-z])|\\d{3,4}(?![\\d.])|-?[34](?![\\d.a-z가-힣]))")),
            // 은행 SWIFT 망·전문(MT103·MT 메시지·gpi·ISO 20022). "Swift 전문가"·"Swift 전문성"·"Swift 전문 지식" 은 iOS
            Map.entry("swift", Except.of(null, "\\s*(?:전문(?!가|성|\\s*지식)|망|mt(?:\\s?\\d{3})?(?![a-z0-9])|gpi|iso\\s?20022)")),
            Map.entry("unity", Except.of(null, "\\s*(?:catalog|카탈로그)")),           // Databricks Unity Catalog
            Map.entry("유니티", Except.of(null, "\\s*카탈로그")),
            Map.entry("스칼라", Except.of(null, "\\s*(?:연산|값|곱|량|함수|타입|형|변수)")),   // 수학의 스칼라
            Map.entry("rails", Except.of("(?:guard|safety|hand|side)[\\s-]?", null)),   // LLM guard rails·safety rails
            // 쿠버네티스·하둡·블록체인 노드: "worker node", "Edge/Node", "노드(Node)", "Kubernetes(Node/Pod)", "Pod, Node", "Node, Pod",
            // "Name Node", "Full Node", "Validator Node"
            Map.entry("node", Except.of("(?:(?:k8s|worker|master|edge|gpu|data|name|compute|control|kubernetes|쿠버네티스|full|validator|rpc"
                            + "|light)[ ]?[/(]?|(?:pods?|파드|노드)[ ]?[,/(·])[ ]?",
                    "\\s?(?:pool|group|selector|affinity|exporter)|[ ]?[,/·][ ]?(?:pods?|파드|service|kubelet)(?![a-z])")),
            // DB 커서: "(Procedure, Function, Cursor)", "Cursor, Trigger", "Explicit·Ref·MongoDB·DB Cursor", "커서(Cursor)", "Offset/Cursor",
            // 커서 기반 페이지네이션("Cursor 기반 페이지네이션·무한 스크롤", "cursor based pagination")
            Map.entry("cursor", Except.of(
                    "(?:(?:function|procedure|package|trigger|view|index|sequence|table|함수|프로시저|offset)\\s?[,/·(]\\s?"
                            + "|(?:explicit|implicit|ref|mongodb|db|커서|sql)[ ]?\\(?[ ]?)",
                    "\\s?[,/·]\\s?(?:function|procedure|package|trigger|view|index|sequence|offset)|[ ]?based"
                            + "|[^\\n]{0,20}(?:페이지네이션|페이징|pagination|무한[ ]?스크롤|infinite[ ]?scroll)")),
            Map.entry("r", Except.of(null, "\\s?(?:및|and)\\s?d(?![a-z])")),        // "R 및 D"(연구개발)
            Map.entry("aix", Except.of("(?:전환|transformation)\\s?\\(\\s?", "\\s*\\(\\s*ai(?![a-z])")),   // AIX(AI Transformation)
            // GO/NO-GO·Go/No Go·Go/Stop, (SAP·ERP) Go Live, Go To Market
            Map.entry("go", Except.of(null, "\\s?/\\s?no[\\s-]?go|[ ]?[/,][ ]?stop(?![a-z])|[ ]?(?:live(?![a-z])|to[ ]market)")));

    static Pattern compile(String alias) {
        String a = alias.toLowerCase(Locale.ROOT);
        // 앞: 영문 별칭은 앞이 영문·숫자면(mysql 안의 sql), 한글 별칭은 앞이 한글이어도(전자바우처) 인정 안 함
        String before = a.matches("[가-힣].*") ? "(?<![a-z0-9가-힣])" : "(?<![a-z0-9])";
        // 뒤는 영문만 막음: java8·자바를 은 잡고 javascript 는 안 잡음. 숫자로 끝나는 별칭은 숫자도 막음: s3 은 S32K(MCU)가 아니다
        String after = a.matches(".*[0-9]") ? "(?![a-z0-9])" : "(?![a-z])";
        Except e = EXCEPT.getOrDefault(a, NONE);
        return Pattern.compile(before + e.before() + Pattern.quote(a) + after + e.after());
    }

    /** 나열 기호로 쓰는 가운뎃점 여러 가지(ㆍ 아래아, ・･ 가타카나, • 글머리, ∙ ‧)를 · 하나로. ㆍ 는 NFKC 가 모음 자모로 바꾸므로 그 전에 */
    private static final Pattern DOTS = Pattern.compile("[\\u318D\\u30FB\\uFF65\\u2022\\u2219\\u2027]");

    /** 전각 글자(Ｊａｖａ)를 반각으로, 소문자로, 공백 여러 개·탭을 한 칸으로, 가운뎃점을 · 로. 별칭과 글에 똑같이 쓴다. */
    static String normalize(String s) {
        String n = Normalizer.normalize(DOTS.matcher(s).replaceAll("·"), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return n.replaceAll("[ \\t\\u00A0\\u3000]+", " ");
    }

    public Set<Integer> match(List<String> lines) {
        return match(String.join("\n", lines));
    }

    public Set<Integer> match(String rawText) {
        Set<Integer> found = new HashSet<>();
        String text = C_AND_CPP.matcher(normalize(rawText)).replaceAll("c언어, c++");
        boolean[] taken = new boolean[text.length()];
        for (Alias a : longestFirst) take(a, text, taken, found);
        for (Alias a : listOnly) take(a, text, taken, found);   // 긴 표기(golang·c언어·c++)가 차지한 뒤에 본다
        return found;
    }

    /** 스토리지 공고의 낱말 */
    private static final Pattern STORAGE = Pattern.compile(
            "(?<![a-z])(?:sata|nvme|ssd|hdd|scsi|hba|raid)(?![a-z])|스토리지|storage|펌웨어|firmware");
    /**
     * 통계 공고의 낱말. 그냥 "분석" 은 넣지 않는다: 스토리지 검증 공고도 "이슈 재현 및 분석"·"프로토콜 분석기"·"인터페이스 분석" 을 쓴다.
     */
    private static final Pattern STATISTICS = Pattern.compile(
            "spss|stata|통계|statistic|sas/stat|viya|데이터 ?분석|분석 ?도구|(?<![a-z])(?:base ?sas|sas ?base|sas ?macro|proc ?sql"
            + "|enterprise ?guide|e-?miner)(?![a-z])|(?<![a-z0-9])sas ?[,/·] ?r(?![a-z0-9&])|(?<![a-z0-9])r ?[,/·] ?sas(?![a-z])");

    /**
     * 공고 전체를 보고 빼는 것. 지금은 SAS 하나: 스토리지 낱말(SATA·NVMe·SSD·HDD·SCSI·HBA·RAID·스토리지·펌웨어)이 있고
     * 통계 낱말(SPSS·STATA·통계·데이터 분석·SAS/STAT·Viya·Base SAS·PROC SQL·"SAS, R" 등)이 없으면 SAS 는 Serial Attached SCSI 다.
     * 줄 단위 EXCEPT 가 못 막는 "SAS 드라이브 FW 이슈" 같은 줄도 같은 공고의 다른 줄로 가른다.
     * @param found        match() 가 찾은 기술(자격요건이나 우대사항 하나)
     * @param postingLines 그 공고의 자격요건과 우대사항 줄 모두
     * @return found 의 사본(SAS 를 뺐을 수 있음). 별칭 표에 "sas" 가 없으면 그대로의 사본
     */
    public Set<Integer> dropByContext(Set<Integer> found, List<String> postingLines) {
        Set<Integer> out = new HashSet<>(found);
        if (sasId == null || !out.contains(sasId)) return out;
        String text = normalize(String.join("\n", postingLines));
        if (STORAGE.matcher(text).find() && !STATISTICS.matcher(text).find()) out.remove(sasId);
        return out;
    }

    /**
     * 긴 표기가 이미 차지한 자리에 걸친 것은 버리고, 찾은 자리를 차지한다.
     * 글은 고치지 않는다: 예전처럼 찾은 자리를 쉼표로 지우면 "SAP go" 의 go 가 나열처럼 보였다.
     */
    private static void take(Alias a, String text, boolean[] taken, Set<Integer> found) {
        if (text.indexOf(a.alias()) < 0) return;   // 별칭은 글자 그대로 찾으므로 글에 없으면 정규식을 돌리지 않는다
        Matcher m = a.pattern().matcher(text);
        while (m.find()) {
            boolean free = true;
            for (int i = m.start(); i < m.end() && free; i++) free = !taken[i];
            if (!free) continue;
            Arrays.fill(taken, m.start(), m.end(), true);
            found.add(a.skillId());
        }
    }
}
