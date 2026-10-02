package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.capstone.jobtrend.crawler.ListTexts.CareerType;
import com.capstone.jobtrend.crawler.ListTexts.DeadlineType;

class ListTextsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 4, 0);

    @Test
    void 마감_표기() {
        assertThat(ListTexts.deadline("~10.18(일)", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.DATE, LocalDate.of(2026, 10, 18), false));
        assertThat(ListTexts.deadline("~01.05(화)", LocalDate.of(2026, 12, 20)).date()).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(ListTexts.deadline("D-5", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(ListTexts.deadline("오늘마감", TODAY).date()).isEqualTo(TODAY);
        assertThat(ListTexts.deadline("내일마감", TODAY).date()).isEqualTo(TODAY.plusDays(1));
        assertThat(ListTexts.deadline("15시 마감", TODAY).date()).isEqualTo(TODAY);
        assertThat(ListTexts.deadline("채용시", TODAY).type()).isEqualTo(DeadlineType.UNTIL_FILLED);
        assertThat(ListTexts.deadline("상시채용", TODAY).type()).isEqualTo(DeadlineType.ALWAYS);
        assertThat(ListTexts.deadline("수시채용", TODAY).type()).isEqualTo(DeadlineType.ROLLING);
        assertThat(ListTexts.deadline("처음 보는 표기", TODAY).unknown()).isTrue();
    }

    @Test
    void 마감_해_넘김과_없는_날짜() {
        // 1월 1일에 본 어제 마감 공고는 작년, 2월에 본 9월 마감은 올해
        assertThat(ListTexts.deadline("~12.31(목)", LocalDate.of(2027, 1, 1)).date()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(ListTexts.deadline("~09.30(수)", LocalDate.of(2027, 2, 10)).date()).isEqualTo(LocalDate.of(2027, 9, 30));
        // 2027년은 윤년이 아니라 02.29 는 2028년
        assertThat(ListTexts.deadline("~02.29", LocalDate.of(2027, 11, 20)).date()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(ListTexts.deadline("~13.40", TODAY).unknown()).isTrue();
        assertThat(ListTexts.deadline("내일 18시 마감", TODAY).date()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void 등록_수정_시각() {
        assertThat(ListTexts.posted("13일 전 등록", NOW)).isEqualTo(new ListTexts.Posted(false, NOW.minusDays(13), Duration.ofDays(1)));
        assertThat(ListTexts.posted("3시간 전 수정", NOW)).isEqualTo(new ListTexts.Posted(true, NOW.minusHours(3), Duration.ofHours(1)));
        assertThat(ListTexts.posted("45분 전 수정", NOW).at()).isEqualTo(NOW.minusMinutes(45));
        assertThat(ListTexts.posted("입사지원", NOW)).isNull();
        assertThat(ListTexts.posted("방금 전 수정", NOW)).isEqualTo(new ListTexts.Posted(true, NOW, Duration.ofMinutes(1)));
        assertThat(ListTexts.posted("어제 등록", NOW)).isEqualTo(new ListTexts.Posted(false, NOW.minusDays(1), Duration.ofDays(1)));
        assertThat(ListTexts.career("경력 3년~5년")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 3, 5));
    }

    @Test
    void 마감_여러_표기() {
        assertThat(ListTexts.deadline("상시채용", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.ALWAYS, null, false));
        assertThat(ListTexts.deadline("채용시 마감", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.UNTIL_FILLED, null, false));
        assertThat(ListTexts.deadline("오늘마감", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.DATE, TODAY, false));
        assertThat(ListTexts.deadline("~10.05(일)", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("~ 10.05(일)", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("~10/05(일)", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("D-0", TODAY).date()).isEqualTo(TODAY);
        assertThat(ListTexts.deadline(null, TODAY).unknown()).isTrue();
    }

    @Test
    void 터무니없는_숫자는_예외가_아니라_모르는_표기() {
        // 예전에는 Integer.parseInt 가 NumberFormatException 을 던져 회차 전체가 멈췄다
        assertThat(ListTexts.deadline("D-99999999999", TODAY).unknown()).isTrue();
        assertThat(ListTexts.deadline("D-12345", TODAY).unknown()).isTrue();
        assertThat(ListTexts.deadline("~100.05", TODAY).unknown()).isTrue();
        assertThat(ListTexts.posted("99999999999999999999일 전 등록", NOW)).isNull();
        // 5자리 이상은 말이 안 되는 표기: DB TIMESTAMP 범위를 넘는 시각을 만들지 않고 모름(null)
        assertThat(ListTexts.posted("999999999분 전 수정", NOW)).isNull();
        assertThat(ListTexts.posted("99999일 전 등록", NOW)).isNull();
        assertThat(ListTexts.posted("9999일 전 등록", NOW).at()).isEqualTo(NOW.minusDays(9999));
        // 경력 연수도 넘치면 예외가 아니라 연수 없음(종류는 남긴다)
        assertThat(ListTexts.career("경력 99999999999년↑")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, null, null));
        assertThat(ListTexts.career("신입·경력 99999999999년 ↓")).isEqualTo(new ListTexts.Career(CareerType.NEW_OR_EXP, null, null));
        // 위 연수만 넘치면 아래 연수는 살린다
        assertThat(ListTexts.career("경력 3~99999999999년")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 3, null));
        assertThat(ListTexts.career("경력 123년↑").expMin()).isNull();
    }

    @Test
    void 등록_수정_시각의_단위() {
        assertThat(ListTexts.posted("45분 전 수정", NOW).precision()).isEqualTo(Duration.ofMinutes(1));
        assertThat(ListTexts.posted("3시간 전 수정", NOW).precision()).isEqualTo(Duration.ofHours(1));
        assertThat(ListTexts.posted("13일 전 등록", NOW).precision()).isEqualTo(Duration.ofDays(1));
        assertThat(ListTexts.posted("방금 전 등록", NOW).precision()).isEqualTo(Duration.ofMinutes(1));
        assertThat(ListTexts.posted("어제 수정", NOW).precision()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void 어제_23시간_전과_오늘_1일_전은_같은_수정() {
        // 실제 수정 시각은 (at - precision, at] 안에 있다. 두 범위가 겹치면 같은 수정으로 본다
        LocalDateTime yesterday = LocalDateTime.of(2026, 9, 28, 10, 0);
        LocalDateTime today = LocalDateTime.of(2026, 9, 29, 9, 0);
        ListTexts.Posted before = ListTexts.posted("23시간 전 수정", yesterday);
        ListTexts.Posted after = ListTexts.posted("1일 전 수정", today);
        assertThat(before.at()).isEqualTo(LocalDateTime.of(2026, 9, 27, 11, 0));
        assertThat(after.at()).isEqualTo(LocalDateTime.of(2026, 9, 28, 9, 0));
        Duration gap = Duration.between(before.at(), after.at()).abs();
        assertThat(gap).isGreaterThan(Duration.ofHours(1));     // 예전의 '1시간 이상 차이면 새 수정'으로는 새 수정이 된다
        assertThat(gap).isLessThan(after.precision());          // 단위만큼 봐주면 같은 수정

        // 정말 새로 고친 공고: 오늘 "3시간 전 수정"은 어제 저장한 시각과 단위 이상 떨어진다
        ListTexts.Posted again = ListTexts.posted("3시간 전 수정", today);
        assertThat(Duration.between(before.at(), again.at()).abs()).isGreaterThan(again.precision().plus(before.precision()));
    }

    @Test
    void 마감_날짜_뒤에_시각이_붙어도_날짜() {
        // 예전에는 띄어쓰기를 지운 "~10.0518:00"에서 일(05) 뒤 숫자 검사에 걸려 모르는 표기가 됐다
        assertThat(ListTexts.deadline("~10.05 18:00", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.DATE, LocalDate.of(2026, 10, 5), false));
        assertThat(ListTexts.deadline("~10.05 18시", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("~10/05 18:00", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("~ 10 . 05", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        // 날짜가 적혀 있으면 '18시 마감' 규칙(오늘)보다 날짜가 먼저
        assertThat(ListTexts.deadline("~10.05(일) 18시 마감", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        // D-day 도 원문에서: "D-7 18:00"이 D-718 이 되지 않게
        assertThat(ListTexts.deadline("D-7 18:00", TODAY).date()).isEqualTo(TODAY.plusDays(7));
        assertThat(ListTexts.deadline("D - 7", TODAY).date()).isEqualTo(TODAY.plusDays(7));
        // 날짜가 없으면 예전 규칙 그대로
        assertThat(ListTexts.deadline("내일 18시 마감", TODAY).date()).isEqualTo(TODAY.plusDays(1));
        assertThat(ListTexts.deadline("15시 마감", TODAY).date()).isEqualTo(TODAY);
    }

    @Test
    void 마감_D_day_대소문자와_상한() {
        assertThat(ListTexts.deadline("D-day", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.DATE, TODAY, false));
        assertThat(ListTexts.deadline("D-Day", TODAY).date()).isEqualTo(TODAY);
        assertThat(ListTexts.deadline("D-DAY", TODAY).date()).isEqualTo(TODAY);
        assertThat(ListTexts.deadline("d-3", TODAY).date()).isEqualTo(TODAY.plusDays(3));
        assertThat(ListTexts.deadline("마감 D-3", TODAY).date()).isEqualTo(TODAY.plusDays(3));
        assertThat(ListTexts.deadline("D-365", TODAY).date()).isEqualTo(TODAY.plusDays(365));
        assertThat(ListTexts.deadline("D-366", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.DATE, null, true));
        assertThat(ListTexts.deadline("D-9999", TODAY).unknown()).isTrue();
    }

    @Test
    void 마감_연도_붙은_날짜와_물결_없는_날짜() {
        assertThat(ListTexts.deadline("~2026.10.05", TODAY)).isEqualTo(new ListTexts.Deadline(DeadlineType.DATE, LocalDate.of(2026, 10, 5), false));
        assertThat(ListTexts.deadline("~2027.01.05(화)", TODAY).date()).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(ListTexts.deadline("~2026.13.05", TODAY).unknown()).isTrue();
        // ~ 없으면 바로 뒤에 '마감'이 올 때만 날짜
        assertThat(ListTexts.deadline("10.05 마감", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("10.05(일) 마감", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("10/05 18시 마감", TODAY).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ListTexts.deadline("10.05", TODAY).unknown()).isTrue();
        assertThat(ListTexts.deadline("2026.10.05 마감", TODAY).unknown()).isTrue();   // 연도 일부를 월로 읽지 않는다
        assertThat(ListTexts.deadline("18시 마감", TODAY).date()).isEqualTo(TODAY);
    }

    @Test
    void 경력_하이픈과_열린_범위() {
        assertThat(ListTexts.career("경력 3-5년")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 3, 5));
        assertThat(ListTexts.career("경력 1년~")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 1, null));
        assertThat(ListTexts.career("경력 1~100년")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 1, null));
        assertThat(ListTexts.career("경력 10년↑")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 10, null));
        assertThat(ListTexts.career("경력 100년")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, null, null));
        assertThat(ListTexts.career("경력 3~5")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, null, null));
    }

    @Test
    void 경력() {
        assertThat(ListTexts.career("신입·경력 3년 ↓")).isEqualTo(new ListTexts.Career(CareerType.NEW_OR_EXP, null, 3));
        assertThat(ListTexts.career("경력 3~5년")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 3, 5));
        assertThat(ListTexts.career("경력 5년↑")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 5, null));
        assertThat(ListTexts.career("경력무관").type()).isEqualTo(CareerType.ANY);
        assertThat(ListTexts.career("신입").type()).isEqualTo(CareerType.NEW);
    }

    @Test
    void 경력_목록의_실제_표기() {   // 9/29 목록 한 쪽에서 그대로 가져온 글
        assertThat(ListTexts.career("3 ~ 5년 · 정규직")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 3, 5));
        assertThat(ListTexts.career("경력(년수무관) · 정규직")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, null, null));
        assertThat(ListTexts.career("경력무관 · 교육생").type()).isEqualTo(CareerType.ANY);
        assertThat(ListTexts.career("경력무관(신입포함)").type()).isEqualTo(CareerType.ANY);
        assertThat(ListTexts.career("신입 · 경력 · 정규직 외").type()).isEqualTo(CareerType.NEW_OR_EXP);
        assertThat(ListTexts.career("경력 7년↑ · 정규직")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 7, null));
    }
}
