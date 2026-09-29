package com.capstone.jobtrend.crawler;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 글에서는 안 찾는(text_match=0) 한두 글자 표기 가운데, 다른 기술과 쉼표·슬래시로 나열됐을 때만 인정하는 것.
     * "Java, Go, Kotlin", "C, Java" 는 잡고 "go 백엔드"·"A등급" 같은 일반 글은 안 잡는다(9/29 검수: Go 26건 중 20건을 놓침).
     */
    static final Set<String> LIST_ONLY = Set.of("go", "c", "r", "cursor");   // cursor 는 DB 커서와 겹쳐서 "Claude, Cursor" 처럼 나열될 때만

    /** "c/c++" 는 C 와 C++ 두 기술이다. 긴 표기부터 지우는 규칙 때문에 C++ 만 남던 것을 둘로 풀어 둔다. */
    private static final Pattern C_AND_CPP = Pattern.compile("(?<![a-z0-9])c\\s*/\\s*c\\+\\+");

    private final List<Alias> longestFirst;
    private final List<Alias> listOnly;

    /** ④를 돌릴 때마다 별칭 표를 DB에서 다시 읽어 새로 만든다. */
    public SkillMatcher(List<Alias> aliases) {
        this.longestFirst = aliases.stream()
                .filter(Alias::textMatch)                      // 뷰·깃·펄·노드·넥스트·다트, go·r·c·es 는 태그에서만
                .filter(a -> !a.alias().isBlank())             // 공백 별칭은 모든 공고에 걸린다
                .sorted(Comparator.comparingInt((Alias a) -> a.alias().length()).reversed())
                .toList();                                     // "자바스크립트"가 "자바"보다, "spring boot"가 "spring"보다 먼저
        this.listOnly = aliases.stream()
                .filter(a -> !a.textMatch() && LIST_ONLY.contains(a.alias()))
                .map(a -> new Alias(a.alias(), a.skillId(), false, compileListed(a.alias())))
                .toList();
    }

    /** 앞이 나열 기호(, / · 괄호)이거나, 뒤에 나열 기호가 올 때만. 뒤에 영문·숫자·+·# 가 붙으면 다른 낱말(golang, c++, c#). */
    static Pattern compileListed(String alias) {
        String a = Pattern.quote(alias);
        return Pattern.compile("(?:(?<=[,/·(]\\s?)" + a + "(?![a-z0-9+#])|(?<![a-z0-9가-힣])" + a + "(?=\\s?[,/·)]))");
    }

    /**
     * 뒤에 이 글이 오면 그 기술이 아닌 별칭. 자격증 줄의 "MCP (Microsoft Certified Professional)" 가 MCP(Model Context Protocol)로
     * 잡히던 것(9/29 검수). 늘어나면 별칭 표에 칸으로 옮길 것.
     */
    static final Map<String, String> NOT_BEFORE = Map.of("mcp", "\\s*\\(?\\s*microsoft");

    static Pattern compile(String alias) {
        String a = alias.toLowerCase(Locale.ROOT);
        // 앞: 영문 별칭은 앞이 영문·숫자면(mysql 안의 sql), 한글 별칭은 앞이 한글이어도(전자바우처) 인정 안 함
        String before = a.matches("[가-힣].*") ? "(?<![a-z0-9가-힣])" : "(?<![a-z0-9])";
        // 뒤는 영문만 막음: java8·자바를 은 잡고 javascript 는 안 잡음
        String except = NOT_BEFORE.containsKey(a) ? "(?!" + NOT_BEFORE.get(a) + ")" : "";
        return Pattern.compile(before + Pattern.quote(a) + "(?![a-z])" + except);
    }

    /** 전각 글자(Ｊａｖａ)를 반각으로, 소문자로, 공백 여러 개·탭을 한 칸으로. 별칭과 글에 똑같이 쓴다. */
    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[ \\t\\u00A0\\u3000]+", " ");
    }

    public Set<Integer> match(List<String> lines) {
        return match(String.join("\n", lines));
    }

    public Set<Integer> match(String rawText) {
        Set<Integer> found = new HashSet<>();
        String text = C_AND_CPP.matcher(normalize(rawText)).replaceAll("c언어, c++");
        for (Alias a : longestFirst) {
            Matcher m = a.pattern().matcher(text);
            if (m.find()) {
                found.add(a.skillId());
                // 찾은 자리를 쉼표로 지운다: 짧은 표기가 다시 안 걸리고, 나열 모양("java, go")도 남는다
                text = m.replaceAll(",");
            }
        }
        for (Alias a : listOnly) {                             // 긴 표기(golang·c언어·r언어)를 다 지운 뒤에 본다
            if (a.pattern().matcher(text).find()) found.add(a.skillId());
        }
        return found;
    }
}
