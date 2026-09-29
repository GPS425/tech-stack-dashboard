package com.capstone.jobtrend.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 목록 HTML → 총 건수와 공고 목록. 요청은 하지 않는다. */
public class ListPageParser {

    private static final Pattern CSN = Pattern.compile("csn=([^&\"]+)");

    public record ListItem(
            long postingId,
            String title,
            String companyName,
            String companyCsn,      // 없으면 null
            String groupName,       // span.main_corp, 없으면 null
            String sizeLabel,       // span.info_stock, 없으면 null
            boolean headhunting,
            List<String> sectorPreview,
            String region,
            String careerText,
            String educationText,
            String deadlineText,    // span.date  예: ~10.18(일), D-5, 채용시
            String postedText       // span.deadlines  예: 13일 전 등록, 3시간 전 수정
    ) {}

    public record ListPage(Integer totalCount, List<ListItem> items) {}

    public ListPage parse(Document doc) {
        Integer total = null;
        Element totalEl = doc.selectFirst(".total_count em");
        if (totalEl != null) {
            String digits = totalEl.text().replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) total = Integer.parseInt(digits);
        }
        // 목록 칸이 없으면 목록이 아닌 화면(차단·점검·잘린 본문)이다. 빈 목록으로 넘기면 전부 마감 처리된다
        // 총 건수(.total_count)는 1쪽에만 있다(9/29 확인). 2쪽부터는 null
        if (doc.selectFirst(".list_body") == null)
            throw new DetailPageParser.ParseFailedException("목록 칸(.list_body) 없음");
        List<ListItem> items = new ArrayList<>();
        for (Element it : doc.select(".list_item[id^=rec-]")) {
            // 번호가 숫자가 아닌 항목(광고 등) 하나 때문에 회차 전체가 실패하지 않게 그 항목만 건너뛴다
            if (!it.id().substring("rec-".length()).matches("\\d{1,18}")) continue;
            items.add(item(it));
        }
        return new ListPage(total, items);
    }

    private ListItem item(Element it) {
        long id = Long.parseLong(it.id().substring("rec-".length()));
        Element titleEl = it.selectFirst(".job_tit a");
        // title 속성은 두 번 이스케이프돼 "R&amp;D" 로 나온다. 글자를 쓰고 비었을 때만 속성으로
        String title = titleEl == null ? "" : titleEl.text().trim();
        if (title.isEmpty() && titleEl != null) title = titleEl.attr("title").trim();

        Element company = it.selectFirst(".company_nm");
        // 회사 페이지가 있으면 a.str_tit, 없으면 span.str_tit 로 이름만 나온다. 회사 키는 관심기업 버튼에도 있다
        Element nameEl = company == null ? null : company.selectFirst(".str_tit");
        String companyName = nameEl == null ? "" : nameEl.text().trim();
        String csn = null;
        if (nameEl != null && nameEl.hasAttr("href")) {
            Matcher m = CSN.matcher(nameEl.attr("href"));
            if (m.find()) csn = m.group(1);
        }
        if (csn == null && company != null) {
            Element btn = company.selectFirst("button[csn]");
            if (btn != null && !btn.attr("csn").isBlank()) csn = btn.attr("csn");
        }
        String group = textOrNull(company, "span.main_corp");
        String size = textOrNull(company, "span.info_stock");
        boolean headhunting = company != null && company.text().contains("헤드헌팅");

        List<String> sectors = it.select(".job_sector > span").eachText();

        return new ListItem(id, title, companyName, csn, group, size, headhunting, sectors,
                textOrNull(it, "p.work_place"), textOrNull(it, "p.career"), textOrNull(it, "p.education"),
                textOrNull(it, ".support_detail .date"), textOrNull(it, ".support_detail .deadlines"));
    }

    private static String textOrNull(Element root, String css) {
        if (root == null) return null;
        Element e = root.selectFirst(css);
        return e == null ? null : e.text().trim();
    }
}
