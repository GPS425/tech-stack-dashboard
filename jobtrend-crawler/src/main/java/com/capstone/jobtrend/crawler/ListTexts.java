package com.capstone.jobtrend.crawler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 목록·상세에 사람이 읽기 좋게 줄여 쓴 글을 칸 값으로 바꾼다. */
public final class ListTexts {

    private ListTexts() {}

    // ---------- 마감 ----------

    public enum DeadlineType { DATE, UNTIL_FILLED, ALWAYS, ROLLING }

    /** date 는 목록에서 알 수 있을 때만. 정확한 날짜는 ③이 상세의 접수 기간으로 채운다. */
    public record Deadline(DeadlineType type, LocalDate date, boolean unknown) {}

    private static final Pattern MONTH_DAY = Pattern.compile("~\\s*(\\d{1,2})\\.(\\d{1,2})");
    private static final Pattern D_MINUS = Pattern.compile("D-(\\d+)");

    public static Deadline deadline(String text, LocalDate today) {
        if (text == null || text.isBlank()) return new Deadline(DeadlineType.DATE, null, true);
        String t = text.replace(" ", "");
        if (t.contains("채용시")) return new Deadline(DeadlineType.UNTIL_FILLED, null, false);
        if (t.contains("상시")) return new Deadline(DeadlineType.ALWAYS, null, false);
        if (t.contains("수시")) return new Deadline(DeadlineType.ROLLING, null, false);
        if (t.contains("내일")) return new Deadline(DeadlineType.DATE, today.plusDays(1), false);   // "내일 18시 마감"도
        if (t.contains("오늘마감") || t.matches(".*\\d+시마감.*")) return new Deadline(DeadlineType.DATE, today, false);
        Matcher d = D_MINUS.matcher(t);
        if (d.find()) return new Deadline(DeadlineType.DATE, today.plusDays(Integer.parseInt(d.group(1))), false);
        Matcher md = MONTH_DAY.matcher(t);
        if (md.find()) {
            LocalDate date = nearestYear(Integer.parseInt(md.group(1)), Integer.parseInt(md.group(2)), today);
            if (date != null) return new Deadline(DeadlineType.DATE, date, false);
        }
        return new Deadline(DeadlineType.DATE, null, true);    // 처음 보는 표기: DATE 로 넣고 로그에 남긴다
    }

    /**
     * 연도 없는 월·일에 해를 붙인다. 작년·올해·내년 중 '30일 전'보다 늦은 첫 날짜.
     * 12월에 본 "~01.05"는 내년, 1월 1일에 본 "~12.31"은 작년, 2월에 본 "~09.30"은 올해.
     * 없는 날짜(윤년 아닌 해의 02.29, 13.40)는 그 해를 건너뛰고, 셋 다 안 되면 null.
     */
    static LocalDate nearestYear(int month, int day, LocalDate today) {
        LocalDate floor = today.minusDays(30);
        for (int y = today.getYear() - 1; y <= today.getYear() + 1; y++) {
            try {
                LocalDate c = LocalDate.of(y, month, day);
                if (!c.isBefore(floor)) return c;
            } catch (java.time.DateTimeException e) {
                // 그 해엔 없는 날짜
            }
        }
        return null;
    }

    // ---------- 등록·수정 시각 ----------

    public record Posted(boolean modified, LocalDateTime at) {}

    private static final Pattern AGO = Pattern.compile("(\\d+)\\s*(분|시간|일)\\s*전\\s*(등록|수정)");
    private static final Pattern WORD_AGO = Pattern.compile("(방금\\s*전|어제)\\s*(등록|수정)");

    /** "13일 전 등록", "3시간 전 수정", "방금 전 수정", "어제 등록". 사람인이 버림으로 보여 주므로 실제보다 최대 한 단위 늦게 계산된다. */
    public static Posted posted(String text, LocalDateTime now) {
        if (text == null) return null;
        Matcher w = WORD_AGO.matcher(text);
        if (w.find()) return new Posted("수정".equals(w.group(2)), w.group(1).startsWith("어제") ? now.minusDays(1) : now);
        Matcher m = AGO.matcher(text);
        if (!m.find()) return null;
        long n = Long.parseLong(m.group(1));
        LocalDateTime at = switch (m.group(2)) {
            case "분" -> now.minusMinutes(n);
            case "시간" -> now.minusHours(n);
            default -> now.minusDays(n);
        };
        return new Posted("수정".equals(m.group(3)), at);
    }

    // ---------- 경력 ----------

    public enum CareerType { NEW, EXPERIENCED, NEW_OR_EXP, ANY }

    public record Career(CareerType type, Integer expMin, Integer expMax) {}

    private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*년?\\s*~\\s*(\\d+)\\s*년");   // "3~5년", "3년~5년"
    private static final Pattern AT_LEAST = Pattern.compile("(\\d+)\\s*년\\s*(↑|이상)");
    private static final Pattern AT_MOST = Pattern.compile("(\\d+)\\s*년\\s*(↓|이하)");

    /** 상세 요약의 경력 값. 예: "신입·경력 3년 ↓", "경력 3~5년", "경력 5년↑", "경력무관", "신입". */
    public static Career career(String text) {
        if (text == null || text.isBlank()) return new Career(null, null, null);
        String t = text.replace(" ", "");
        CareerType type;
        // "경력(년수무관)"은 경력직에 연수만 안 따지는 것이라 ANY 가 아니다
        if (t.contains("경력무관")) type = CareerType.ANY;
        else if (t.contains("신입") && t.contains("경력")) type = CareerType.NEW_OR_EXP;
        else if (t.contains("신입")) type = CareerType.NEW;
        else if (t.contains("경력")) type = CareerType.EXPERIENCED;
        else type = null;
        Integer min = null;
        Integer max = null;
        Matcher r = RANGE.matcher(t);
        if (r.find()) {
            min = Integer.parseInt(r.group(1));
            max = Integer.parseInt(r.group(2));
        } else {
            Matcher lo = AT_LEAST.matcher(t);
            if (lo.find()) min = Integer.parseInt(lo.group(1));
            Matcher hi = AT_MOST.matcher(t);
            if (hi.find()) max = Integer.parseInt(hi.group(1));
        }
        // 목록은 "3 ~ 5년 · 정규직"처럼 '경력' 없이 연수만 쓰기도 한다(9/29 목록 한 쪽에 15건)
        if (type == null && (min != null || max != null)) type = CareerType.EXPERIENCED;
        return new Career(type, min, max);
    }
}
