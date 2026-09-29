package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

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
