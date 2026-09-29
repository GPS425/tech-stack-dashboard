package com.capstone.jobtrend.crawler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/**
 * view-ajax 응답 → 공고 상세. 요청은 하지 않는다.
 * 요약(.jv_summary)이나 태그가 없으면 사이트 구조가 바뀐 것으로 보고 실패로 센다.
 */
public class DetailPageParser {

    private static final Pattern TAG_CODE = Pattern.compile("cat_kewd=(\\d+)");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    public record Tag(int code, String name) {}

    public record PostingDetail(
            String title,
            Map<String, String> summary,   // 경력, 학력, 근무형태, 급여, 근무지역 … (칸 이름 → 값)
            List<String> required,         // 자격요건 줄들, 칸이 없으면 빈 목록
            List<String> preferred,        // 우대사항 줄들, 칸이 없으면 빈 목록
            List<Tag> tags,                // 관련 태그 중 직무·기술 코드가 붙은 것
            LocalDateTime startAt,         // 접수 시작, 없으면 null
            LocalDateTime endAt            // 접수 마감, 없으면 null (채용시·상시 등)
    ) {
        public String summaryValue(String name) {
            return summary.get(name);
        }
    }

    public static class ParseFailedException extends RuntimeException {
        public ParseFailedException(String message) {
            super(message);
        }
    }

    public PostingDetail parse(Document doc) {
        Element summaryEl = doc.selectFirst(".jv_summary");
        if (summaryEl == null) throw new ParseFailedException(".jv_summary 없음");

        List<Tag> tags = new ArrayList<>();
        for (Element a : doc.select(".jv_footer .tags a[href*=cat_kewd]")) {
            Matcher m = TAG_CODE.matcher(a.attr("href"));
            if (m.find()) tags.add(new Tag(Integer.parseInt(m.group(1)), a.text().replaceFirst("^#", "").trim()));
        }
        if (tags.isEmpty()) throw new ParseFailedException("관련 태그 없음");

        Map<String, String> summary = new LinkedHashMap<>();
        for (Element dl : summaryEl.select("dl")) {
            Element dt = dl.selectFirst("dt");
            Element dd = dl.selectFirst("dd");
            if (dt == null || dd == null) continue;
            Element strong = dd.selectFirst("strong");
            String value = strong != null ? strong.text() : ownTextOf(dd);
            summary.put(dt.text().trim(), value.trim());
        }

        List<String> required = lines(summaryEl.selectFirst("[id^=details-required-]"));
        List<String> preferred = lines(summaryEl.selectFirst("[id^=details-preferred-]"));
        // 칸 이름은 있는데 글이 비었으면 그 칸의 선택자가 맞지 않은 것으로 본다(칸이 없는 공고는 정상)
        if (summary.containsKey("자격요건") && required.isEmpty())
            throw new ParseFailedException("자격요건 칸은 있는데 글을 찾지 못함");
        if (summary.containsKey("우대사항") && preferred.isEmpty())
            throw new ParseFailedException("우대사항 칸은 있는데 글을 찾지 못함");

        Element titleEl = doc.selectFirst("h1.tit_job");
        String title = titleEl == null ? null : titleEl.text().trim();

        LocalDateTime start = null;
        LocalDateTime end = null;
        Element period = doc.selectFirst(".jv_howto .info_period");
        if (period != null) {
            for (Element dt : period.select("dt")) {
                Element dd = dt.nextElementSibling();
                if (dd == null) continue;
                LocalDateTime at = parsePeriod(dd.text());
                if (dt.text().contains("시작")) start = at;
                else if (dt.text().contains("마감")) end = at;
            }
        }
        return new PostingDetail(title, summary, required, preferred, tags, start, end);
    }

    private static LocalDateTime parsePeriod(String text) {
        String t = text.trim();
        if (!t.matches(".*\\d.*")) return null;                // "채용시" 같은 글이면 날짜 없음
        try {
            return LocalDateTime.parse(t, PERIOD);
        } catch (RuntimeException e) {                         // 숫자가 있는데 못 읽으면 표기가 바뀐 것
            throw new ParseFailedException("접수 기간 형식이 다름: " + t);
        }
    }

    /** 툴팁 글을 줄 단위로. br 을 줄바꿈으로 보고 빈 줄은 뺀다. */
    static List<String> lines(Element tip) {
        if (tip == null) return List.of();
        Element copy = tip.clone();
        copy.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
        // <li><span>전공</span>컴퓨터공학</li> 이 "전공컴퓨터공학"으로 붙으면 한글 별칭이 앞 글자에 막힌다
        copy.select("li > span:first-child").forEach(sp -> {
            if (sp.nextSibling() != null) sp.after(new TextNode(": "));
        });
        copy.select("li").forEach(li -> li.appendText("\n"));
        List<String> out = new ArrayList<>();
        for (String line : copy.wholeText().split("\n")) {
            String s = line.replace(' ', ' ').trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static String ownTextOf(Element dd) {
        StringBuilder sb = new StringBuilder();
        for (TextNode t : dd.textNodes()) sb.append(t.text());
        return sb.toString();
    }
}
