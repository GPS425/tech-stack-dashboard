package com.capstone.jobtrend.crawler;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(ListTexts.posted("13일 전 등록", NOW)).isEqualTo(new ListTexts.Posted(false, NOW.minusDays(13)));
        assertThat(ListTexts.posted("3시간 전 수정", NOW)).isEqualTo(new ListTexts.Posted(true, NOW.minusHours(3)));
        assertThat(ListTexts.posted("45분 전 수정", NOW).at()).isEqualTo(NOW.minusMinutes(45));
        assertThat(ListTexts.posted("입사지원", NOW)).isNull();
        assertThat(ListTexts.posted("방금 전 수정", NOW)).isEqualTo(new ListTexts.Posted(true, NOW));
        assertThat(ListTexts.posted("어제 등록", NOW)).isEqualTo(new ListTexts.Posted(false, NOW.minusDays(1)));
        assertThat(ListTexts.career("경력 3년~5년")).isEqualTo(new ListTexts.Career(CareerType.EXPERIENCED, 3, 5));
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
