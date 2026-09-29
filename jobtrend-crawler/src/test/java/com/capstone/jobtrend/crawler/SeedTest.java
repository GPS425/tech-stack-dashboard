package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.capstone.jobtrend.crawler.DetailPageParser.PostingDetail;
import com.capstone.jobtrend.crawler.SeedLoader.AliasRow;
import com.capstone.jobtrend.crawler.SeedLoader.SkillRow;

/** resources/seed 의 CSV 가 DB 제약(UNIQUE·CHECK·길이)에 맞는지, 실제 공고에서 기대한 기술이 나오는지. */
class SeedTest {

    private static final Set<String> CATEGORIES =
            Set.of("LANGUAGE", "FRAMEWORK", "DATABASE", "CLOUD_INFRA", "DEVOPS_TOOL", "MOBILE", "AI_DATA", "ETC");

    private final List<SkillRow> skills = SeedLoader.readSkills();
    private final List<AliasRow> aliases = SeedLoader.aliases(skills, SeedLoader.readAliases());

    @Test
    void 기술_표가_제약에_맞는다() {
        assertThat(skills).extracting(SkillRow::name).doesNotHaveDuplicates().allMatch(n -> n.length() <= 50);
        assertThat(skills.stream().map(SkillRow::code).filter(c -> c != null).toList()).doesNotHaveDuplicates();
        assertThat(skills).allMatch(s -> CATEGORIES.contains(s.category()));
        // 기술 코드는 모두 코드표에 있다
        assertThat(SeedLoader.readCodeNames().keySet()).containsAll(
                skills.stream().map(SkillRow::code).filter(c -> c != null).toList());
    }

    @Test
    void 별칭은_소문자이고_짧은_표기는_글에서_안_찾는다() {
        assertThat(aliases).extracting(AliasRow::alias).doesNotHaveDuplicates()
                .allMatch(a -> a.equals(a.toLowerCase()) && a.length() <= 60 && !a.isBlank());
        Map<String, Boolean> text = aliases.stream().collect(Collectors.toMap(AliasRow::alias, AliasRow::textMatch));
        for (String a : List.of("go", "c", "r", "es", "뷰", "깃", "펄", "노드", "넥스트", "다트"))
            assertThat(text.get(a)).as(a).isFalse();
        assertThat(text.get("java")).isTrue();
    }

    @Test
    void 실제_공고에서_기술을_찾는다() throws Exception {
        Map<String, Integer> ids = new HashMap<>();
        for (int i = 0; i < skills.size(); i++) ids.put(skills.get(i).name(), i);
        SkillMatcher m = new SkillMatcher(aliases.stream()
                .map(a -> SkillMatcher.Alias.of(a.alias(), ids.get(a.skill()), a.textMatch())).toList());
        PostingDetail d = new DetailPageParser().parse(SavedHtml.load("ajax-54749084.html"));
        Set<String> found = m.match(d.required()).stream().map(i -> skills.get(i).name()).collect(Collectors.toSet());
        assertThat(found).contains("Python");
        assertThat(m.match("Java, Spring Boot, JPA 기반 서버 개발 · MySQL, Redis · AWS(EC2, S3), Docker, k8s")
                .stream().map(i -> skills.get(i).name()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("Java", "Spring Boot", "JPA", "MySQL", "Redis", "AWS", "Docker", "Kubernetes");
        assertThat(m.match("Apache Kafka, React Native 앱, 스프링 부트").stream().map(i -> skills.get(i).name()).toList())
                .containsExactlyInAnyOrder("Kafka", "React Native", "Spring Boot");
        // 9/29 검수: Claude Code 는 코딩 도구, 그냥 Claude·OpenAI 는 LLM, 자격증 MCP 는 제외
        assertThat(m.match("Claude Code, Cursor 등 AI 코딩 도구 활용 경험").stream().map(i -> skills.get(i).name()).toList())
                .containsExactly("AI 코딩 도구");
        assertThat(m.match("OpenAI, Claude 등 LLM API 사용 경험, RAG 구축").stream().map(i -> skills.get(i).name()).toList())
                .containsExactlyInAnyOrder("LLM", "RAG");
        assertThat(m.match("자격증: MCP (Microsoft Certified Professional)")).isEmpty();
        assertThat(m.match("MCP 서버 개발 경험").stream().map(i -> skills.get(i).name()).toList()).containsExactly("MCP");
    }
}
