package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.junit.jupiter.api.Test;

import com.capstone.jobtrend.crawler.DetailPageParser.PostingDetail;
import com.capstone.jobtrend.crawler.DetailPageParser.Tag;
import com.capstone.jobtrend.crawler.ListPageParser.ListItem;
import com.capstone.jobtrend.crawler.ListPageParser.ListPage;

/** 저장한 실제 HTML 로 보는 시험(없으면 건너뜀, -PrequireSavedHtml 이면 실패). 같은 구조의 합성 HTML 시험은 SyntheticHtmlTest */
class ParserTest {

    private final ListPageParser listParser = new ListPageParser();
    private final DetailPageParser detailParser = new DetailPageParser();

    @Test
    void 목록_한_쪽() throws Exception {
        ListPage page = listParser.parse(SavedHtml.load("list-84-p1.html"));
        assertThat(page.totalCount()).isEqualTo(1747);
        assertThat(page.items()).hasSize(100);
        assertThat(page.rawItemCount()).isGreaterThanOrEqualTo(100);
        assertThat(page.items()).extracting(ListItem::postingId).doesNotHaveDuplicates();

        ListItem first = page.items().get(0);
        assertThat(first.postingId()).isEqualTo(55042336L);
        assertThat(first.title()).isEqualTo("[데이터유니버스] 2026년 신입/경력직 공개채용");
        assertThat(first.companyName()).isEqualTo("(주)데이터유니버스");
        assertThat(first.companyCsn()).isEqualTo("ZmYxbk5TQUIyTmpodWxxN0FmNys0Zz09");
        assertThat(first.sectorPreview()).contains("백엔드/서버개발");
        assertThat(first.region()).isEqualTo("서울 영등포구");
        assertThat(first.deadlineText()).isEqualTo("~10.18(일)");
        assertThat(first.postedText()).isEqualTo("13일 전 등록");
        assertThat(page.items()).allSatisfy(i -> {
            assertThat(i.title()).isNotBlank();
            assertThat(i.companyName()).isNotBlank();
            assertThat(i.companyCsn()).isNotBlank();
        });

        // 회사 페이지가 없는 회사: 이름은 span, 회사 키는 관심기업 버튼에서
        ListItem noLink = page.items().stream().filter(i -> i.postingId() == 55129718L).findFirst().orElseThrow();
        assertThat(noLink.companyName()).isEqualTo("(주)예스오예스");
        assertThat(noLink.companyCsn()).isEqualTo("RHlLQTdZeWFQNkt0R2ZUZ3NaRUhBUT09");

        // title 속성은 두 번 이스케이프돼 있다. 글자에서 읽어야 "R&D"
        assertThat(page.items()).noneMatch(i -> i.title().contains("&amp;"));
        assertThat(page.items().stream().filter(i -> i.postingId() == 54749084L).findFirst().orElseThrow().title())
                .contains("R&D");
    }

    @Test
    void 목록이_아닌_화면은_실패() {
        // 차단·점검 화면이나 잘린 본문을 빈 목록으로 넘기면 모든 공고가 마감 처리된다
        assertThatThrownBy(() -> listParser.parse(Jsoup.parse("<html><body>잠시 후 다시 시도</body></html>")))
                .isInstanceOf(DetailPageParser.ParseFailedException.class);
    }

    @Test
    void 목록_2쪽에는_총_건수가_없다() throws Exception {
        ListPage page = listParser.parse(SavedHtml.load("list-84-p2.html"));
        assertThat(page.totalCount()).isNull();
        assertThat(page.items()).hasSize(100);
    }

    @Test
    void 우대_항목_이름과_값이_붙지_않는다() throws Exception {
        PostingDetail d = detailParser.parse(SavedHtml.load("ajax-55134430.html"));
        assertThat(d.preferred()).noneMatch(l -> l.startsWith("전공컴퓨터"));
        assertThat(d.preferred()).anyMatch(l -> l.startsWith("전공: "));
    }

    @Test
    void 상세_자격요건과_우대가_나뉜다() throws Exception {
        PostingDetail d = detailParser.parse(SavedHtml.load("ajax-54749084.html"));
        assertThat(d.title()).isEqualTo("로와이드 주식회사에서 AI R&D/풀스텍 주니어를 모십니다.");
        assertThat(d.tags()).extracting(Tag::code).containsExactly(92, 84, 87, 181, 142, 201, 221, 272, 236);
        assertThat(d.tags()).extracting(Tag::name).contains("백엔드/서버개발", "Python", "Javascript");
        assertThat(d.summaryValue("경력")).isEqualTo("신입·경력 3년 ↓");
        assertThat(d.summaryValue("학력")).isEqualTo("학력무관");
        assertThat(d.summaryValue("근무형태")).isEqualTo("정규직, 계약직");
        assertThat(d.summaryValue("근무지역")).isEqualTo("서울 서초구");
        assertThat(d.required()).anyMatch(l -> l.contains("Python 기반 데이터 처리"));
        assertThat(d.required()).noneMatch(l -> l.contains("RAG"));
        assertThat(d.preferred()).anyMatch(l -> l.contains("RAG 또는 LLM"));
        assertThat(d.startAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 11, 0));
        assertThat(d.endAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 23, 59));
    }

    @Test
    void 상세_대량공채는_자격요건_칸이_없고_태그가_많다() throws Exception {
        PostingDetail d = detailParser.parse(SavedHtml.load("ajax-55113543.html"));
        assertThat(d.required()).isEmpty();
        assertThat(d.preferred()).isEmpty();
        assertThat(d.tags().size()).isGreaterThan(50);
        assertThat(d.endAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 23, 59));
    }

    @Test
    void 상세_세번째_공고() throws Exception {
        PostingDetail d = detailParser.parse(SavedHtml.load("ajax-55134430.html"));
        assertThat(d.tags()).extracting(Tag::code).containsExactly(83, 84, 100, 235);
        assertThat(d.required()).isNotEmpty();
        assertThat(d.preferred()).isNotEmpty();
    }

    @Test
    void 요약이_없으면_실패() throws Exception {
        // view 페이지에는 요약이 없다(요약은 view-ajax 가 채운다). 이런 응답은 성공으로 치지 않는다
        assertThatThrownBy(() -> detailParser.parse(Jsoup.parse("<html><body>안내 페이지</body></html>")))
                .isInstanceOf(DetailPageParser.ParseFailedException.class);
        // 파일은 검사 밖에서 읽는다: 안에서 읽으면 파일이 없을 때 '건너뜀' 신호까지 잡혀 실패가 된다
        var viewPage = SavedHtml.load("detail-54749084.html");
        assertThatThrownBy(() -> detailParser.parse(viewPage))
                .isInstanceOf(DetailPageParser.ParseFailedException.class);
    }

    /** 예전 요약 값 읽기(strong, 없으면 dd 바로 아래 글). 새 valueOf 가 저장되는 칸 값을 바꾸지 않았는지 비교용 */
    private static String oldValue(Element dd) {
        Element strong = dd.selectFirst("strong");
        String v;
        if (strong != null) {
            v = strong.text();
        } else {
            StringBuilder sb = new StringBuilder();
            for (TextNode t : dd.textNodes()) sb.append(t.text());
            v = sb.toString();
        }
        return v.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String squash(String v) {
        return v == null ? null : v.replaceAll("\\s+", "");
    }

    @Test
    void 저장하는_요약_값은_예전_읽기와_같다() throws Exception {
        // 파일은 검사 밖에서 먼저 읽는다(없으면 건너뜀)
        List<String> names = List.of("ajax-54749084.html", "ajax-55113543.html", "ajax-55134430.html");
        List<Document> docs = new ArrayList<>();
        for (String f : names) docs.add(SavedHtml.load(f));
        Set<String> stored = Set.of("경력", "학력", "근무형태", "근무지역");   // CrawlService 가 DB 에 넣는 칸
        int compared = 0;
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            PostingDetail d = detailParser.parse(doc);
            for (Element dl : doc.select(".jv_summary dl")) {
                Element dt = dl.selectFirst("dt");
                Element dd = dl.selectFirst("dd");
                if (dt == null || dd == null || !stored.contains(dt.text().trim())) continue;
                // 예전 읽기는 <br> 앞뒤 글을 띄어쓰기 없이 붙였다("서울 강남구경기 성남시"). 띄어쓰기만 다른 것은 같은 값으로 본다
                String now = d.summaryValue(dt.text().trim());
                assertThat(squash(now)).as(names.get(i) + " " + dt.text() + ": 새 [" + now + "] / 예전 [" + oldValue(dd) + "]")
                        .isEqualTo(squash(oldValue(dd)));
                compared++;
            }
        }
        assertThat(compared).isGreaterThanOrEqualTo(docs.size());   // 선택자가 아무것도 못 찾고 통과하지 않게
    }
}
