package com.capstone.jobtrend.crawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * 있는 행은 고치고 없는 행은 넣는다. 기술은 지우지 않고 CSV 에 없으면 is_active=0 으로 끈다(posting_skill 이 가리키고 있을 수 있다).
 * ④는 is_active=1 인 기술의 태그 코드만 읽는다.
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

    /** DB 의 기술 행(skill_id, 사람인 코드, 이름) */
    record DbSkill(int id, Integer code, String name) {}

    /** CSV 에 DB 를 맞추는 순서: 코드 비우기 → 고치기 → 넣기 → 끄기. */
    record SkillPlan(List<Integer> clearCode, Map<Integer, SkillRow> update, List<SkillRow> insert, List<Integer> deactivate) {}

    /**
     * 이름이 같은 행을 고친다(코드를 바꾸거나 지워도 새 행을 넣지 않게: 넣으면 이름 UNIQUE 에 걸려 수집 전체가 멈춘다).
     * 이름이 DB 에 없으면 같은 코드의 행을 새 이름으로 바꾼다(이름 고치기). 단 그 행의 이름이 CSV 에 남아 있으면 그 이름 것이라 새로 넣는다.
     * CSV 에 없는 기술은 지우지 않고 끈다(is_active=0, posting_skill 이 가리킴).
     * 코드가 옮겨 가는 행(235 를 Java 에서 다른 이름으로, 코드를 지운 행, 꺼지는 행이 쥔 CSV 코드)은 먼저 코드를 비워 코드 UNIQUE 에 안 걸리게 한다.
     */
    static SkillPlan plan(List<SkillRow> csv, List<DbSkill> current) {
        Map<String, DbSkill> byName = new HashMap<>();
        Map<Integer, DbSkill> byCode = new HashMap<>();
        for (DbSkill d : current) {
            byName.put(d.name(), d);
            if (d.code() != null) byCode.put(d.code(), d);
        }
        Set<String> csvNames = csv.stream().map(SkillRow::name).collect(Collectors.toSet());
        Set<Integer> csvCodes = csv.stream().map(SkillRow::code).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, SkillRow> update = new LinkedHashMap<>();
        List<SkillRow> insert = new ArrayList<>();
        for (SkillRow r : csv) {
            DbSkill t = byName.get(r.name());
            if (t == null && r.code() != null) {
                DbSkill c = byCode.get(r.code());
                if (c != null && !csvNames.contains(c.name())) t = c;
            }
            if (t == null) insert.add(r);
            else update.put(t.id(), r);
        }
        List<Integer> clearCode = new ArrayList<>();
        List<Integer> deactivate = new ArrayList<>();
        for (DbSkill d : current) {
            SkillRow r = update.get(d.id());
            if (r == null) deactivate.add(d.id());
            // 꺼지는 행은 CSV 가 쓰는 코드만 내놓는다. 안 쓰는 코드는 둬도 된다(④는 is_active=1 인 기술의 코드만 읽음)
            Integer next = r != null ? r.code() : csvCodes.contains(d.code()) ? null : d.code();
            if (d.code() != null && !d.code().equals(next)) clearCode.add(d.id());
        }
        return new SkillPlan(clearCode, update, insert, deactivate);
    }

    private void mergeSkills(List<SkillRow> skills) {
        List<DbSkill> current = db.query("SELECT skill_id, saramin_code, name FROM skill", (rs, i) -> {
            int code = rs.getInt(2);
            return new DbSkill(rs.getInt(1), rs.wasNull() ? null : code, rs.getString(3));
        });
        SkillPlan p = plan(skills, current);
        // 한 문장씩 UNIQUE 를 보므로 순서가 중요하다: 코드를 먼저 비워야 코드를 서로 바꾸거나 옮길 수 있다
        db.batchUpdate("UPDATE skill SET saramin_code = NULL WHERE skill_id = :id", ids(p.clearCode()));
        db.batchUpdate("""
                UPDATE skill SET saramin_code = :code, name = :name, category = :category, is_active = 1
                 WHERE skill_id = :id
                """, p.update().entrySet().stream()
                .map(e -> param(e.getValue()).addValue("id", e.getKey()))
                .toArray(MapSqlParameterSource[]::new));
        db.batchUpdate("INSERT INTO skill (saramin_code, name, category, is_active) VALUES (:code, :name, :category, 1)",
                p.insert().stream().map(SeedLoader::param).toArray(MapSqlParameterSource[]::new));
        db.batchUpdate("UPDATE skill SET is_active = 0 WHERE skill_id = :id", ids(p.deactivate()));
        if (!p.insert().isEmpty()) log.info("새 기술 {}개", p.insert().size());
        if (!p.deactivate().isEmpty()) log.info("CSV 에 없는 기술 {}개를 끔(is_active=0)", p.deactivate().size());
    }

    private static MapSqlParameterSource param(SkillRow r) {
        return new MapSqlParameterSource()
                .addValue("code", r.code(), java.sql.Types.INTEGER)
                .addValue("name", r.name())
                .addValue("category", r.category());
    }

    private static MapSqlParameterSource[] ids(List<Integer> ids) {
        return ids.stream().map(id -> new MapSqlParameterSource("id", id)).toArray(MapSqlParameterSource[]::new);
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

    /**
     * 기술 이름(소문자)을 기본 별칭으로 두고 skill_alias.csv 가 덮는다(같은 기술일 때만: windows 를 0 으로 등).
     * 없는 기술, 같은 별칭을 다르게 두 번, 다른 기술의 이름을 가리키는 별칭이면 여기서 멈춘다(뒤의 줄이 말없이 이기던 것).
     */
    static List<AliasRow> aliases(List<SkillRow> skills, List<AliasRow> listed) {
        Set<String> names = skills.stream().map(SkillRow::name).collect(Collectors.toSet());
        Map<String, AliasRow> byAlias = new LinkedHashMap<>();
        Map<String, String> byName = new HashMap<>();          // 이름 별칭 → 기술
        for (SkillRow s : skills) {
            String a = SkillMatcher.normalize(s.name()).trim();
            if (byName.put(a, s.name()) != null) throw new IllegalStateException("skill.csv: 소문자로 같은 이름 " + s.name());
            byAlias.put(a, new AliasRow(a, s.name(), true));
        }
        Map<String, AliasRow> seen = new HashMap<>();
        for (AliasRow r : listed) {
            if (!names.contains(r.skill())) throw new IllegalStateException("skill_alias.csv: 없는 기술 " + r.skill() + " (" + r.alias() + ")");
            String a = SkillMatcher.normalize(r.alias()).trim();
            if (a.isBlank() || a.length() > 60) throw new IllegalStateException("skill_alias.csv: 별칭 길이 " + r.alias());
            AliasRow row = new AliasRow(a, r.skill(), r.textMatch());
            AliasRow prev = seen.put(a, row);
            if (prev != null && !prev.equals(row))
                throw new IllegalStateException("skill_alias.csv: 같은 별칭이 다르게 두 번 " + a + " → " + prev.skill() + " / " + r.skill());
            String owner = byName.get(a);
            if (owner != null && !owner.equals(r.skill()))
                throw new IllegalStateException("skill_alias.csv: 기술 " + owner + " 의 이름과 같은 별칭이 " + r.skill() + " 를 가리킴");
            byAlias.put(a, row);
        }
        return new ArrayList<>(byAlias.values());
    }

    /** 이름·코드가 겹치면 멈춘다(plan 은 둘 다 하나씩이라고 본다). */
    static List<SkillRow> readSkills() {
        List<SkillRow> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<Integer> codes = new HashSet<>();
        for (String[] f : rows("seed/skill.csv", 3)) {
            Integer code = f[0].isBlank() ? null : Integer.valueOf(f[0].trim());
            SkillRow r = new SkillRow(code, f[1].trim(), f[2].trim());
            if (!names.add(r.name()) || (code != null && !codes.add(code)))
                throw new IllegalStateException("skill.csv: 이름이나 코드가 두 번 → " + String.join(",", f));
            out.add(r);
        }
        return out;
    }

    static List<AliasRow> readAliases() {
        return rows("seed/skill_alias.csv", 3).stream().map(SeedLoader::aliasRow).toList();
    }

    /** text_match 는 0·1 만. "1 " 이나 "true" 를 말없이 0 으로 읽던 것. */
    static AliasRow aliasRow(String[] f) {
        String t = f[2].trim();
        if (!t.equals("0") && !t.equals("1"))
            throw new IllegalStateException("skill_alias.csv: text_match 는 0 이나 1 → " + String.join(",", f));
        return new AliasRow(f[0], f[1].trim(), t.equals("1"));
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
