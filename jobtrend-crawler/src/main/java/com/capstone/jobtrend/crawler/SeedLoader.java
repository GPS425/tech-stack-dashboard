package com.capstone.jobtrend.crawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기준 데이터(job·skill·skill_alias)를 resources/seed 의 CSV 에 맞춘다. 수집을 시작할 때마다 부른다.
 * 있는 행은 고치고 없는 행은 넣는다. 지우지는 않는다(posting_skill 이 기술을 가리키고 있을 수 있다).
 */
@Component
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    record SkillRow(Integer code, String name, String category) {}

    record AliasRow(String alias, String skill, boolean textMatch) {}

    private final NamedParameterJdbcTemplate db;
    private final TransactionTemplate tx;

    public SeedLoader(NamedParameterJdbcTemplate db, TransactionTemplate tx) {
        this.db = db;
        this.tx = tx;
    }

    public void load(List<Integer> jobCodes) {
        Map<Integer, String> codeNames = readCodeNames();
        List<SkillRow> skills = readSkills();
        List<AliasRow> aliases = aliases(skills, readAliases());
        tx.executeWithoutResult(s -> {
            mergeJobs(jobCodes, codeNames);
            mergeSkills(skills);
            mergeAliases(aliases);
        });
        log.info("기준 데이터: 직무 {}개, 기술 {}개, 별칭 {}개", jobCodes.size(), skills.size(), aliases.size());
    }

    private void mergeJobs(List<Integer> jobCodes, Map<Integer, String> codeNames) {
        List<MapSqlParameterSource> rows = new ArrayList<>();
        for (int i = 0; i < jobCodes.size(); i++) {
            int code = jobCodes.get(i);
            String name = codeNames.get(code);
            if (name == null) throw new IllegalStateException("코드표에 없는 직무 코드: " + code);
            rows.add(new MapSqlParameterSource().addValue("code", code).addValue("name", name).addValue("ord", i + 1));
        }
        db.batchUpdate("""
                MERGE INTO job t
                USING (SELECT :code AS saramin_code, :name AS name, :ord AS sort_order FROM dual) s
                   ON (t.saramin_code = s.saramin_code)
                 WHEN MATCHED THEN UPDATE SET t.name = s.name, t.sort_order = s.sort_order
                 WHEN NOT MATCHED THEN INSERT (saramin_code, name, sort_order) VALUES (s.saramin_code, s.name, s.sort_order)
                """, rows.toArray(MapSqlParameterSource[]::new));
    }

    /** 코드가 있는 기술은 코드로 찾아 이름도 고친다(이름을 바꿔도 새 행이 안 생기게). 코드가 없는 기술은 이름으로 찾는다. */
    private void mergeSkills(List<SkillRow> skills) {
        // 코드 없이 들어가 있던 기술(예: FastAPI)에 CSV 에서 코드를 붙이면, 코드로 찾는 MERGE 가 못 찾고 새로 넣으려다
        // 이름 UNIQUE 에 걸린다. 먼저 이름으로 찾아 코드를 붙여 둔다(ON 에 쓴 칸은 MERGE 안에서 못 바꿔서 따로)
        db.batchUpdate("""
                UPDATE skill SET saramin_code = :code
                 WHERE name = :name AND saramin_code IS NULL
                   AND NOT EXISTS (SELECT 1 FROM skill s2 WHERE s2.saramin_code = :code)
                """, params(skills.stream().filter(r -> r.code() != null).toList()));
        db.batchUpdate("""
                MERGE INTO skill t
                USING (SELECT :code AS saramin_code, :name AS name, :category AS category FROM dual) s
                   ON (t.saramin_code = s.saramin_code)
                 WHEN MATCHED THEN UPDATE SET t.name = s.name, t.category = s.category
                 WHEN NOT MATCHED THEN INSERT (saramin_code, name, category) VALUES (s.saramin_code, s.name, s.category)
                """, params(skills.stream().filter(r -> r.code() != null).toList()));
        db.batchUpdate("""
                MERGE INTO skill t
                USING (SELECT :name AS name, :category AS category FROM dual) s
                   ON (t.name = s.name AND t.saramin_code IS NULL)
                 WHEN MATCHED THEN UPDATE SET t.category = s.category
                 WHEN NOT MATCHED THEN INSERT (name, category) VALUES (s.name, s.category)
                """, params(skills.stream().filter(r -> r.code() == null).toList()));
    }

    private static MapSqlParameterSource[] params(List<SkillRow> rows) {
        return rows.stream().map(r -> new MapSqlParameterSource()
                        .addValue("code", r.code(), java.sql.Types.INTEGER)
                        .addValue("name", r.name())
                        .addValue("category", r.category()))
                .toArray(MapSqlParameterSource[]::new);
    }

    private void mergeAliases(List<AliasRow> aliases) {
        db.batchUpdate("""
                MERGE INTO skill_alias t
                USING (SELECT :alias AS alias, (SELECT skill_id FROM skill WHERE name = :skill) AS skill_id,
                              :textMatch AS text_match FROM dual) s
                   ON (t.alias = s.alias)
                 WHEN MATCHED THEN UPDATE SET t.skill_id = s.skill_id, t.text_match = s.text_match
                 WHEN NOT MATCHED THEN INSERT (alias, skill_id, text_match) VALUES (s.alias, s.skill_id, s.text_match)
                """, aliases.stream().map(a -> new MapSqlParameterSource()
                        .addValue("alias", a.alias())
                        .addValue("skill", a.skill())
                        .addValue("textMatch", a.textMatch() ? 1 : 0))
                .toArray(MapSqlParameterSource[]::new));
        // CSV 에서 지운 별칭은 DB 에서도 지운다. 안 지우면 오탐 별칭이 계속 매칭된다(별칭을 가리키는 외래 키는 없음)
        // 오라클 IN 목록은 1,000개까지다. 별칭이 그보다 많아지면 임시 표로 바꿀 것
        if (aliases.size() > 1000) throw new IllegalStateException("별칭이 1,000개를 넘음: 지우기 쿼리를 바꿀 것");
        int removed = db.update("DELETE FROM skill_alias WHERE alias NOT IN (:keep)",
                new MapSqlParameterSource("keep", aliases.stream().map(AliasRow::alias).toList()));
        if (removed > 0) log.info("CSV 에 없는 별칭 {}개를 지움", removed);
    }

    /** 기술 이름(소문자)을 기본 별칭으로 두고 skill_alias.csv 가 덮는다. 없는 기술을 가리키면 여기서 멈춘다. */
    static List<AliasRow> aliases(List<SkillRow> skills, List<AliasRow> listed) {
        Set<String> names = skills.stream().map(SkillRow::name).collect(Collectors.toSet());
        Map<String, AliasRow> byAlias = new LinkedHashMap<>();
        for (SkillRow s : skills) {
            String a = SkillMatcher.normalize(s.name()).trim();
            byAlias.put(a, new AliasRow(a, s.name(), true));
        }
        for (AliasRow r : listed) {
            if (!names.contains(r.skill())) throw new IllegalStateException("skill_alias.csv: 없는 기술 " + r.skill() + " (" + r.alias() + ")");
            String a = SkillMatcher.normalize(r.alias()).trim();
            if (a.isBlank() || a.length() > 60) throw new IllegalStateException("skill_alias.csv: 별칭 길이 " + r.alias());
            byAlias.put(a, new AliasRow(a, r.skill(), r.textMatch()));
        }
        return new ArrayList<>(byAlias.values());
    }

    static List<SkillRow> readSkills() {
        List<SkillRow> out = new ArrayList<>();
        for (String[] f : rows("seed/skill.csv", 3)) {
            Integer code = f[0].isBlank() ? null : Integer.valueOf(f[0].trim());
            out.add(new SkillRow(code, f[1].trim(), f[2].trim()));
        }
        return out;
    }

    static List<AliasRow> readAliases() {
        List<AliasRow> out = new ArrayList<>();
        for (String[] f : rows("seed/skill_alias.csv", 3)) {
            out.add(new AliasRow(f[0], f[1].trim(), "1".equals(f[2].trim())));
        }
        return out;
    }

    static Map<Integer, String> readCodeNames() {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (String[] f : rows("seed/saramin_codes.csv", 2)) out.put(Integer.valueOf(f[0].trim()), f[1].trim());
        return out;
    }

    /** 첫 줄(머리)·빈 줄·# 줄을 뺀 쉼표 구분 행. 값 안에 쉼표는 없다. */
    private static List<String[]> rows(String path, int columns) {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            boolean header = true;
            for (String line; (line = r.readLine()) != null; ) {
                line = line.replace("﻿", "");
                if (line.isBlank() || line.startsWith("#")) continue;
                if (header) {
                    header = false;
                    continue;
                }
                String[] f = line.split(",", -1);
                if (f.length != columns) throw new IllegalStateException(path + ": 칸 수가 다름 → " + line);
                out.add(f);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
