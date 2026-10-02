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

    // 9자리까지만 코드로 본다(int 넘침). 더 길면 그 태그는 코드 없는 것으로 건너뛴다
    private static final Pattern TAG_CODE = Pattern.compile("cat_kewd=(\\d{1,9})(?!\\d)");
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
            summary.put(dt.text().trim(), valueOf(dd));
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

    // wholeText() 는 칸 경계를 모른다. <p>Python</p><p>Django</p> 가 "PythonDjango"로 붙으면 기술 이름 경계를 놓친다
    private static final String BLOCKS = "p, div, li, tr, dd, dt, h1, h2, h3, h4, h5, h6, ul, ol, table, section, article, blockquote, pre";
    private static final String CELLS = "td, th";

    /** 툴팁 글을 줄 단위로. br 과 블록 요소(p·div·li·tr …)를 줄바꿈, 표 칸은 띄어쓰기로 보고 빈 줄은 뺀다. */
    static List<String> lines(Element tip) {
        if (tip == null) return List.of();
        Element copy = tip.clone();
        copy.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
        // <li><span>전공</span>컴퓨터공학</li> 이 "전공컴퓨터공학"으로 붙으면 한글 별칭이 앞 글자에 막힌다
        copy.select("li > span:first-child").forEach(sp -> {
            if (sp.nextSibling() != null) sp.after(new TextNode(": "));
        });
        // 앞뒤 모두: "글<div>칸</div>글" 의 앞 글과도 떨어지게. 빈 줄은 아래에서 빠진다
        copy.select(BLOCKS).forEach(b -> b.prependText("\n").appendText("\n"));
        copy.select(CELLS).forEach(c -> c.appendText(" "));
        List<String> out = new ArrayList<>();
        for (String line : copy.wholeText().split("\n")) {
            String s = line.replace('\u00a0', ' ').trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    // 요약 값 옆에 붙는 단추·툴팁(근무지역의 "지도", 급여 설명 툴팁, 자격요건 칸의 상세 글 등)과 숨긴 레이어.
    // 실제 화면 구조를 다 확인할 수 없으니 좁게 뺀다: 값을 감싼 칸일 수 있는 요소(toolTipWrap, map·layer 클래스 칸)는
    // 숨겨져 있거나 단추 글("지도" 등)만 있을 때만 뺀다
    private static final String HARD_NOISE = "button, script, style, .blind, [id^=details-]";
    private static final String TOOLTIP = "[class*=tooltip]:not([class*=wrap])";   // 클래스 비교는 대소문자를 가리지 않는다
    private static final Pattern HIDDEN = Pattern.compile("(?i)display\\s*:\\s*none");
    // 단추 글. 띄어쓰기를 지우고 비교한다("지도 보기")
    private static final Pattern BUTTON_TEXT = Pattern.compile("지도|지도보기|상세보기|더보기|위치보기");
    // map·layer 로 시작하거나 끝나는 클래스 낱말(txt_map, btn_map, map_link, layer_map, layer_pop).
    // player·sitemap·mapping·roadmap 같은 낱말은 아니다
    private static final Pattern MAP_LAYER_CLASS = Pattern.compile("(?i)(map|layer)([_-].*)?|.*[_-](map|layer)");

    /**
     * 요약 칸 값. 단추·툴팁·숨긴 요소를 빼고 남은 글 전부(여러 줄은 띄어쓰기로). 굵은 글(strong)이 있으면 그것이 값.
     * 그렇게 해서 비면(값이 툴팁 모양 요소 안에만 있는 경우) 툴팁만 남기고 다시: strong, 아니면 dd 바로 아래 글, 아니면 남은 글 전부.
     */
    private static String valueOf(Element dd) {
        Element copy = dd.clone();
        removeNoise(copy, true);
        String v = strongOrText(copy);
        if (!v.isEmpty()) return v;

        Element loose = dd.clone();
        removeNoise(loose, false);                             // 숨긴 요소·단추는 여기서도 뺀다
        Element strong = loose.selectFirst("strong");
        if (strong != null) v = normalize(strong.text());
        if (v.isEmpty()) {
            StringBuilder own = new StringBuilder();
            for (TextNode t : loose.textNodes()) own.append(t.text()).append(' ');
            v = normalize(own.toString());
        }
        return v.isEmpty() ? normalize(text(loose)) : v;
    }

    // dd 자신(복사본의 맨 위)은 빼고 지운다
    private static void removeNoise(Element root, boolean tooltips) {
        List<Element> drop = new ArrayList<>();
        for (Element e : root.getAllElements()) {
            if (e == root) continue;
            if (e.is(HARD_NOISE) || HIDDEN.matcher(e.attr("style")).find() || isButtonLike(e)
                    || (tooltips && e.is(TOOLTIP)))
                drop.add(e);
        }
        drop.forEach(Element::remove);
    }

    /** "지도" 같은 단추 글만 있는 링크, 또는 map·layer 클래스 요소 중 비었거나 단추 글만 있는 것. */
    private static boolean isButtonLike(Element e) {
        String t = normalize(e.text()).replace(" ", "");
        boolean buttonText = BUTTON_TEXT.matcher(t).matches();
        if (e.nameIs("a") && buttonText) return true;
        if (!buttonText && !t.isEmpty()) return false;
        for (String c : e.classNames()) if (MAP_LAYER_CLASS.matcher(c).matches()) return true;
        return false;
    }

    private static String strongOrText(Element e) {
        Element strong = e.selectFirst("strong");
        return normalize(strong != null ? strong.text() : text(e));
    }

    private static String text(Element e) {
        e.select("br").forEach(br -> br.replaceWith(new TextNode(" ")));
        return e.text();
    }

    private static String normalize(String v) {
        return v.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
