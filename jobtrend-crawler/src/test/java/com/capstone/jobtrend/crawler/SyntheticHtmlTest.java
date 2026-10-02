package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.capstone.jobtrend.crawler.DetailPageParser.ParseFailedException;
import com.capstone.jobtrend.crawler.DetailPageParser.PostingDetail;
import com.capstone.jobtrend.crawler.DetailPageParser.Tag;
import com.capstone.jobtrend.crawler.ListPageParser.ListItem;
import com.capstone.jobtrend.crawler.ListPageParser.ListPage;

/**
 * 사람인 원문은 커밋하지 않는다(약관). 파서가 읽는 선택자만 흉내 낸 작은 HTML 로 ParserTest 와 같은 것을 본다.
 * 회사·공고 이름과 키는 지어낸 값이다.
 */
class SyntheticHtmlTest {

    private final ListPageParser listParser = new ListPageParser();
    private final DetailPageParser detailParser = new DetailPageParser();

    // ---------- 목록 ----------

    /** 목록 한 칸. company 는 .company_nm 안쪽 HTML */
    private static String item(String id, String titleLink, String company, String place, String career,
                               String edu, String date, String posted, String... sectors) {
        StringBuilder sec = new StringBuilder();
        for (String s : sectors) sec.append("<span>").append(s).append("</span>");
        return """
                <div id="rec-%s" class="list_item">
                  <div class="box_item">
                    <div class="col company_nm">%s</div>
                    <div class="col notification_info">
                      <div class="job_tit">%s</div>
                      <div class="job_meta"><span class="job_sector">%s</span></div>
                    </div>
                    <div class="col recruit_info"><ul>
                      <li><p class="work_place">%s</p></li>
                      <li><p class="career">%s</p></li>
                      <li><p class="education">%s</p></li>
                    </ul></div>
                    <div class="col support_info">
                      <p class="support_type"><button type="button">입사지원</button></p>
                      <p class="support_detail"><span class="date">%s</span><span class="deadlines">%s</span></p>
                    </div>
                  </div>
                </div>
                """.formatted(id, company, titleLink, sec, place, career, edu, date, posted);
    }

    private static Document listDoc(String totalCount, String... items) {
        String total = totalCount == null ? "" : "<div class=\"total_count\"><span>총 <em>" + totalCount + "</em>건</span></div>";
        return Jsoup.parse("<html><body><div class=\"content\">" + total
                + "<div class=\"list_body\">" + String.join("", items) + "</div></div></body></html>", SaraminClient.BASE);
    }

    private static final String FIRST = item("10000001",
            "<a href=\"/zf_user/jobs/relay/view?rec_idx=10000001\" title=\"[가나데이터] 2026년 신입/경력직 공개채용\">"
                    + "<span>[가나데이터] 2026년 신입/경력직 공개채용</span></a>",
            "<a class=\"str_tit\" href=\"/zf_user/company-info/view?csn=QUJDREVGR0g9PQ%3D%3D&amp;popup_yn=y\">(주)가나데이터</a>"
                    + "<span class=\"main_corp\">가나그룹</span><span class=\"info_stock\">코스닥</span>"
                    + "<button type=\"button\" class=\"interested_corp\" csn=\"QUJDREVGR0g9PQ==\">관심기업</button>",
            "서울 영등포구", "신입 · 경력 3년↑", "대학교(4년)↑", "~10.18(일)", "13일 전 등록",
            "백엔드/서버개발", "Java", "Spring Boot");

    private static final String NO_COMPANY_PAGE = item("10000002",
            "<a href=\"/zf_user/jobs/relay/view?rec_idx=10000002\" title=\"웹 개발자 채용\">웹 개발자 채용</a>",
            "<span class=\"str_tit\">(주)다라마</span><button type=\"button\" class=\"interested_corp\" csn=\"WFlaMTIzNDU2Nzg=\">관심기업</button>",
            "경기 성남시", "경력무관", "학력무관", "채용시", "3시간 전 수정",
            "프론트엔드");

    // title 속성은 두 번 이스케이프돼 있다("R&amp;amp;D" → 속성 값 "R&amp;D"). 글자에서 읽어야 "R&D"
    private static final String AMP_TITLE = item("10000003",
            "<a href=\"/zf_user/jobs/relay/view?rec_idx=10000003\" title=\"AI R&amp;amp;D 엔지니어\">AI R&amp;D 엔지니어</a>",
            "<a class=\"str_tit\" href=\"/zf_user/company-info/view?csn=SEhIMTIz\">바사아(주)</a>"
                    + "<span class=\"headhunting\">헤드헌팅</span>",
            "서울 서초구", "경력 3~5년", "대학교(2,3년)↑", "D-5", "방금 전 수정",
            "AI/ML");

    // 글자가 비면 title 속성으로
    private static final String EMPTY_TEXT_TITLE = item("10000004",
            "<a href=\"/zf_user/jobs/relay/view?rec_idx=10000004\" title=\"데이터 엔지니어 모집\"></a>",
            "<a class=\"str_tit\" href=\"/zf_user/company-info/view?csn=SUlJNDU2\">자차카</a>",
            "부산 해운대구", "신입", "고졸↑", "오늘마감", "어제 등록");

    // 광고 등 번호가 숫자가 아닌 칸
    private static final String AD = """
            <div id="rec-ad_banner" class="list_item"><div class="job_tit"><a>광고</a></div></div>
            """;

    @Test
    void 목록_1쪽() {
        ListPage page = listParser.parse(listDoc("1,747", FIRST, NO_COMPANY_PAGE, AMP_TITLE, EMPTY_TEXT_TITLE));
        assertThat(page.totalCount()).isEqualTo(1747);
        assertThat(page.items()).extracting(ListItem::postingId)
                .containsExactly(10000001L, 10000002L, 10000003L, 10000004L);
        assertThat(page.rawItemCount()).isEqualTo(4);

        ListItem first = page.items().get(0);
        assertThat(first.title()).isEqualTo("[가나데이터] 2026년 신입/경력직 공개채용");
        assertThat(first.companyName()).isEqualTo("(주)가나데이터");
        assertThat(first.companyCsn()).isEqualTo("QUJDREVGR0g9PQ==");   // href 의 %3D 를 풀어 버튼 값과 같다
        assertThat(first.groupName()).isEqualTo("가나그룹");
        assertThat(first.sizeLabel()).isEqualTo("코스닥");
        assertThat(first.headhunting()).isFalse();
        assertThat(first.sectorPreview()).containsExactly("백엔드/서버개발", "Java", "Spring Boot");
        assertThat(first.region()).isEqualTo("서울 영등포구");
        assertThat(first.careerText()).isEqualTo("신입 · 경력 3년↑");
        assertThat(first.educationText()).isEqualTo("대학교(4년)↑");
        assertThat(first.deadlineText()).isEqualTo("~10.18(일)");
        assertThat(first.postedText()).isEqualTo("13일 전 등록");

        // 회사 페이지가 없는 회사: 이름은 span, 회사 키는 관심기업 버튼에서
        ListItem noLink = page.items().get(1);
        assertThat(noLink.companyName()).isEqualTo("(주)다라마");
        assertThat(noLink.companyCsn()).isEqualTo("WFlaMTIzNDU2Nzg=");
        assertThat(noLink.groupName()).isNull();
        assertThat(noLink.sizeLabel()).isNull();
        assertThat(noLink.deadlineText()).isEqualTo("채용시");
        assertThat(noLink.postedText()).isEqualTo("3시간 전 수정");

        ListItem amp = page.items().get(2);
        assertThat(amp.title()).isEqualTo("AI R&D 엔지니어");
        assertThat(amp.headhunting()).isTrue();
        assertThat(amp.companyCsn()).isEqualTo("SEhIMTIz");

        assertThat(page.items().get(3).title()).isEqualTo("데이터 엔지니어 모집");
        assertThat(page.items().get(3).sectorPreview()).isEmpty();
        assertThat(page.items()).noneMatch(i -> i.title().contains("&amp;"));
    }

    @Test
    void 목록_2쪽에는_총_건수가_없다() {
        ListPage page = listParser.parse(listDoc(null, FIRST, NO_COMPANY_PAGE));
        assertThat(page.totalCount()).isNull();
        assertThat(page.items()).hasSize(2);
    }

    @Test
    void 광고를_걸러도_원래_칸_수는_남는다() {
        // 100칸 중 광고 하나를 걸러 99건이 돼도 마지막 쪽이 아니다. 쪽 끝 판단은 rawItemCount 로
        ListPage page = listParser.parse(listDoc(null, FIRST, AD, NO_COMPANY_PAGE));
        assertThat(page.items()).hasSize(2);
        assertThat(page.rawItemCount()).isEqualTo(3);
    }

    @Test
    void 빈_목록과_목록이_아닌_화면() {
        ListPage empty = listParser.parse(listDoc("0"));
        assertThat(empty.totalCount()).isZero();
        assertThat(empty.items()).isEmpty();
        assertThat(empty.rawItemCount()).isZero();
        assertThatThrownBy(() -> listParser.parse(Jsoup.parse("<html><body>잠시 후 다시 시도</body></html>")))
                .isInstanceOf(ParseFailedException.class);
    }

    @Test
    void 총_건수가_터무니없이_크면_모름() {
        assertThat(listParser.parse(listDoc("99,999,999,999", FIRST)).totalCount()).isNull();
    }

    @Test
    void 회사_키는_URL_인코딩을_풀어_버튼_값과_같게() {
        String withHref = item("10000005", "<a title=\"t\">t</a>",
                "<a class=\"str_tit\" href=\"/zf_user/company-info/view?csn=abc%2Bd%3D&amp;popup_yn=y\">가</a>",
                "", "", "", "", "");
        String withButton = item("10000006", "<a title=\"t\">t</a>",
                "<span class=\"str_tit\">가</span><button csn=\"abc+d=\">관심기업</button>", "", "", "", "", "");
        // base64 의 '+'는 그대로 둔다(폼 인코딩처럼 띄어쓰기로 바꾸지 않는다)
        String rawPlus = item("10000007", "<a title=\"t\">t</a>",
                "<a class=\"str_tit\" href=\"/zf_user/company-info/view?csn=abc+d=\">가</a>", "", "", "", "", "");
        // 다른 이름(pcsn)에 걸리지 않는다
        String other = item("10000008", "<a title=\"t\">t</a>",
                "<a class=\"str_tit\" href=\"/x?pcsn=zzz&amp;csn=Q1NO\">가</a>", "", "", "", "", "");
        List<ListItem> items = listParser.parse(listDoc(null, withHref, withButton, rawPlus, other)).items();
        assertThat(items).extracting(ListItem::companyCsn).containsExactly("abc+d=", "abc+d=", "abc+d=", "Q1NO");
        assertThat(ListPageParser.urlDecode("bad%zz")).isEqualTo("bad%zz");
    }

    // ---------- 상세(view-ajax) ----------

    private static final String SUMMARY = """
            <div class="jv_cont jv_summary">
              <h2 class="jv_title">핵심 정보</h2>
              <div class="cont">
                <div class="col">
                  <dl><dt>경력</dt><dd><strong>신입·경력 3년 ↓</strong></dd></dl>
                  <dl><dt>학력</dt><dd><strong>학력무관</strong></dd></dl>
                  <dl><dt>근무형태</dt><dd><strong>정규직, 계약직</strong></dd></dl>
                  <dl><dt>자격요건</dt><dd>
                    <div class="toolTipWrap"><button type="button" class="spr_jview btn_detail">상세보기</button>
                      <div class="toolTip" id="details-required-1"><div class="toolTipCont"><ul>
                        <li>Python 기반 데이터 처리 경험</li>
                        <li>Java<br>Spring Boot</li>
                      </ul></div></div>
                    </div></dd></dl>
                  <dl><dt>우대사항</dt><dd>
                    <div class="toolTipWrap"><button type="button" class="spr_jview btn_detail">상세보기</button>
                      <div class="toolTip" id="details-preferred-1"><div class="toolTipCont"><ul>
                        <li><span>전공</span>컴퓨터공학</li>
                        <li>RAG 또는 LLM 서비스 개발 경험</li>
                      </ul></div></div>
                    </div></dd></dl>
                </div>
                <div class="col">
                  <dl><dt>급여</dt><dd>면접 후 결정
                    <div class="toolTipWrap"><button type="button" class="spr_jview txt_tooltip"><span class="blind">설명</span></button>
                      <div class="toolTip"><strong>급여 안내</strong>회사 내규에 따름</div></div></dd></dl>
                  <dl><dt>근무일시</dt><dd><span>주 5일(월~금)</span></dd></dl>
                  <dl><dt>근무지역</dt><dd>서울 서초구 <a href="#" class="spr_jview txt_map">지도</a></dd></dl>
                </div>
              </div>
            </div>
            """;

    private static final String TAGS = """
            <div class="jv_cont jv_footer"><div class="tags"><ul>
              <li><a href="/zf_user/search?cat_kewd=92&amp;searchType=recently">#백엔드/서버개발</a></li>
              <li><a href="/zf_user/search?cat_kewd=84">#Python</a></li>
              <li><a href="/zf_user/search?cat_kewd=87">#Javascript</a></li>
              <li><a href="/zf_user/search?searchword=%EC%9E%AC%ED%83%9D">#재택근무</a></li>
            </ul></div></div>
            """;

    private static final String PERIOD = """
            <div class="jv_cont jv_howto"><dl class="info_period">
              <dt>시작일</dt><dd>2026.08.14 11:00</dd>
              <dt class="end">마감일</dt><dd>2026.10.13 23:59</dd>
            </dl></div>
            """;

    private static Document ajax(String... parts) {
        return Jsoup.parse("<div class=\"wrap_jview\"><section class=\"jview\">"
                + "<div class=\"wrap_jv_header\"><h1 class=\"tit_job\">가나에서 AI R&amp;D/풀스택 주니어를 모십니다.</h1></div>"
                + String.join("", parts) + "</section></div>", SaraminClient.BASE);
    }

    @Test
    void 상세_자격요건과_우대가_나뉜다() {
        PostingDetail d = detailParser.parse(ajax(SUMMARY, TAGS, PERIOD));
        assertThat(d.title()).isEqualTo("가나에서 AI R&D/풀스택 주니어를 모십니다.");
        assertThat(d.tags()).containsExactly(new Tag(92, "백엔드/서버개발"), new Tag(84, "Python"), new Tag(87, "Javascript"));
        assertThat(d.summaryValue("경력")).isEqualTo("신입·경력 3년 ↓");
        assertThat(d.summaryValue("학력")).isEqualTo("학력무관");
        assertThat(d.summaryValue("근무형태")).isEqualTo("정규직, 계약직");
        assertThat(d.summaryValue("근무지역")).isEqualTo("서울 서초구");          // "지도" 단추는 뺀다
        assertThat(d.summaryValue("급여")).isEqualTo("면접 후 결정");             // 툴팁 안의 strong 을 값으로 잡지 않는다
        assertThat(d.summaryValue("근무일시")).isEqualTo("주 5일(월~금)");       // span 안의 글도 값
        assertThat(d.required()).containsExactly("Python 기반 데이터 처리 경험", "Java", "Spring Boot");
        assertThat(d.preferred()).containsExactly("전공: 컴퓨터공학", "RAG 또는 LLM 서비스 개발 경험");
        assertThat(d.startAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 11, 0));
        assertThat(d.endAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 23, 59));
    }

    @Test
    void 상세_자격요건_칸이_없는_공고와_채용시_마감() {
        String summary = """
                <div class="jv_cont jv_summary"><div class="cont"><div class="col">
                  <dl><dt>경력</dt><dd><strong>경력무관</strong></dd></dl>
                </div></div></div>
                """;
        String period = """
                <div class="jv_cont jv_howto"><dl class="info_period">
                  <dt>시작일</dt><dd>2026.09.01 09:00</dd><dt class="end">마감일</dt><dd>채용시</dd>
                </dl></div>
                """;
        PostingDetail d = detailParser.parse(ajax(summary, TAGS, period));
        assertThat(d.required()).isEmpty();
        assertThat(d.preferred()).isEmpty();
        assertThat(d.startAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 9, 0));
        assertThat(d.endAt()).isNull();
        // 접수 기간이 없는 공고
        assertThat(detailParser.parse(ajax(summary, TAGS)).endAt()).isNull();
    }

    @Test
    void 요약이_없으면_실패() {
        assertThatThrownBy(() -> detailParser.parse(Jsoup.parse("<html><body>안내 페이지</body></html>")))
                .isInstanceOf(ParseFailedException.class);
        // view 페이지(요약은 view-ajax 가 채운다): 제목·태그는 있어도 요약이 없다
        assertThatThrownBy(() -> detailParser.parse(ajax(TAGS, PERIOD)))
                .isInstanceOf(ParseFailedException.class).hasMessageContaining("jv_summary");
    }

    @Test
    void 태그가_없거나_자격요건_글을_못_찾으면_실패() {
        assertThatThrownBy(() -> detailParser.parse(ajax(SUMMARY, PERIOD)))
                .isInstanceOf(ParseFailedException.class).hasMessageContaining("태그");
        String noText = SUMMARY.replace("details-required-1", "something-else");
        assertThatThrownBy(() -> detailParser.parse(ajax(noText, TAGS)))
                .isInstanceOf(ParseFailedException.class).hasMessageContaining("자격요건");
        String badPeriod = PERIOD.replace("2026.10.13 23:59", "2026-10-13");
        assertThatThrownBy(() -> detailParser.parse(ajax(SUMMARY, TAGS, badPeriod)))
                .isInstanceOf(ParseFailedException.class).hasMessageContaining("접수 기간");
    }

    @Test
    void 태그_코드가_int_를_넘으면_예외_없이_그_태그만_뺀다() {
        String tags = TAGS.replace("cat_kewd=87", "cat_kewd=99999999999");
        PostingDetail d = detailParser.parse(ajax(SUMMARY, tags));
        assertThat(d.tags()).extracting(Tag::code).containsExactly(92, 84);
        // 전부 넘치면 코드 붙은 태그가 없는 것: RuntimeException 이 아니라 ParseFailed
        String allBad = "<div class=\"jv_cont jv_footer\"><div class=\"tags\"><a href=\"/s?cat_kewd=12345678901\">#X</a></div></div>";
        assertThatThrownBy(() -> detailParser.parse(ajax(SUMMARY, allBad)))
                .isInstanceOf(ParseFailedException.class);
    }

    // ---------- 줄 나누기·요약 값 ----------

    private static List<String> lines(String html) {
        return DetailPageParser.lines(Jsoup.parse("<div id=\"t\">" + html + "</div>").getElementById("t"));
    }

    @Test
    void 블록_요소는_줄을_나눈다() {
        assertThat(lines("<p>Python</p><p>Django</p><div>Java 경험</div><div>Spring</div>"))
                .containsExactly("Python", "Django", "Java 경험", "Spring");
        assertThat(lines("앞 글<div>칸</div>뒤 글")).containsExactly("앞 글", "칸", "뒤 글");
        assertThat(lines("<h3>필수</h3><ul><li>Go</li></ul><ol><li>Rust</li></ol><dl><dt>언어</dt><dd>Kotlin</dd></dl>"))
                .containsExactly("필수", "Go", "Rust", "언어", "Kotlin");
        assertThat(lines("<p>Java <b>Spring</b> 경험</p>")).containsExactly("Java Spring 경험");   // 인라인은 그대로
    }

    @Test
    void 섹션_인용_pre_도_줄을_나눈다() {
        assertThat(lines("<section>Go</section><article>Rust</article><blockquote>C#</blockquote><pre>Ruby</pre>"))
                .containsExactly("Go", "Rust", "C#", "Ruby");
    }

    @Test
    void 표_칸은_띄어_쓰고_줄은_나눈다() {
        assertThat(lines("<table><tr><td>Java</td><td>Kotlin</td></tr><tr><th>DB</th><td>MySQL</td></tr></table>"))
                .containsExactly("Java Kotlin", "DB MySQL");
    }

    @Test
    void 요약_값은_여러_줄과_안쪽_글을_살린다() {
        String html = """
                <div class="jv_cont jv_summary"><div class="cont"><div class="col">
                  <dl><dt>근무지역</dt><dd>서울 강남구<br>경기 성남시 <a href="#">지도</a></dd></dl>
                  <dl><dt>급여</dt><dd><span>면접 후 결정</span></dd></dl>
                  <dl><dt>직급/직책</dt><dd>사원&nbsp;&nbsp;대리 <a href="/link">팀원</a></dd></dl>
                </div></div></div>
                """;
        PostingDetail d = detailParser.parse(ajax(html, TAGS));
        assertThat(d.summaryValue("근무지역")).isEqualTo("서울 강남구 경기 성남시");
        assertThat(d.summaryValue("급여")).isEqualTo("면접 후 결정");
        assertThat(d.summaryValue("직급/직책")).isEqualTo("사원 대리 팀원");   // 알려진 단추가 아닌 링크 글은 값
    }

    private String value(String dd) {
        return detailParser.parse(ajax("<div class=\"jv_cont jv_summary\"><dl><dt>X</dt>" + dd + "</dl></div>", TAGS))
                .summaryValue("X");
    }

    @Test
    void 요약_값에서_숨긴_레이어와_지도_표시는_뺀다() {
        // 숨긴 주소 레이어(display:none)와 layer 클래스는 값이 아니다
        assertThat(value("<dd>서울 강남구 <a class=\"spr_jview txt_map\" href=\"#\">지도</a>"
                + "<div class=\"layer_map\" style=\"display:none\"><p>서울특별시 강남구 테헤란로 1</p></div></dd>"))
                .isEqualTo("서울 강남구");
        assertThat(value("<dd>서울 강남구<div style=\"DISPLAY: none\">숨김</div></dd>")).isEqualTo("서울 강남구");
        assertThat(value("<dd>서울 강남구<div class=\"layer_pop\" style=\"display: none;\">팝업</div></dd>")).isEqualTo("서울 강남구");
        // a·button 이 아닌 지도 표시도 뺀다. 글이 없는 지도 아이콘도
        assertThat(value("<dd>서울 서초구 <span class=\"spr_jview txt_map\">지도</span></dd>")).isEqualTo("서울 서초구");
        assertThat(value("<dd>서울 서초구 <span class=\"ico_map\"></span><a href=\"#\">위치 보기</a></dd>")).isEqualTo("서울 서초구");
    }

    @Test
    void 요약_값을_감싼_map_layer_칸은_지우지_않는다() {
        // 값을 감싼 칸(wrap_map) 은 남기고, 그 안의 지도 단추와 숨긴 주소 레이어만 뺀다
        assertThat(value("<dd><div class=\"wrap_map\">서울 강남구 <a class=\"txt_map\" href=\"#\">지도</a>"
                + "<div class=\"layer_map\" style=\"display:none\"><p>서울특별시 강남구 테헤란로 1 상세주소</p></div></div></dd>"))
                .isEqualTo("서울 강남구");
        // map·layer 로 시작하거나 끝나는 클래스 낱말만 본다: player·sitemap·mapping·roadmap 은 아니다
        assertThat(value("<dd>서울 <span class=\"player\">마포구</span></dd>")).isEqualTo("서울 마포구");
        assertThat(value("<dd><span class=\"sitemap\">서울 강남구</span></dd>")).isEqualTo("서울 강남구");
        assertThat(value("<dd><strong class=\"mapping\">정규직</strong></dd>")).isEqualTo("정규직");
        assertThat(value("<dd>정규직 <span class=\"roadmap\">계약직</span></dd>")).isEqualTo("정규직 계약직");
        // map·layer 낱말이어도 값 글을 품고 있으면 남긴다
        assertThat(value("<dd><span class=\"layer_value\">대졸↑</span> 이상</dd>")).isEqualTo("대졸↑ 이상");
        assertThat(value("<dd>서울 <span class=\"map_area\"><strong>강남구</strong></span></dd>")).isEqualTo("강남구");
        assertThat(value("<dd>서울 강남구<div class=\"layer_pop\">팝업</div></dd>")).isEqualTo("서울 강남구 팝업");   // 숨기지 않은 레이어는 값으로 둔다(보수적으로)
    }

    @Test
    void 대체_경로에서도_숨긴_요소와_지도_단추는_뺀다() {
        // 툴팁 안에만 값이 있어 대체 경로로 가도, 숨긴 레이어·지도 단추는 값에 섞이지 않는다
        assertThat(value("<dd><div class=\"toolTip\">서울 강남구 <a href=\"#\">지도</a>"
                + "<div style=\"display:none\">숨긴 주소</div></div></dd>")).isEqualTo("서울 강남구");
    }

    @Test
    void 요약_값이_툴팁_모양_요소_안에만_있으면_예전처럼_살린다() {
        // 잡음만 빼면 비는 경우: strong → dd 바로 아래 글 → 툴팁 글 순으로
        assertThat(value("<dd><span class=\"tooltip_txt\">3,000만원</span></dd>")).isEqualTo("3,000만원");
        assertThat(value("<dd><div class=\"toolTip\"><strong>회사내규</strong> 설명</div></dd>")).isEqualTo("회사내규");
        assertThat(value("<dd>면접 후 <span class=\"tooltip\">x</span></dd>")).isEqualTo("면접 후");
        // 단추·상세 글만 있는 칸(자격요건)은 여전히 빈 값
        assertThat(value("<dd><div class=\"toolTipWrap\"><button>상세보기</button>"
                + "<div class=\"toolTip\" id=\"details-required-9\"><ul><li>Java</li></ul></div></div></dd>")).isEmpty();
    }
}
