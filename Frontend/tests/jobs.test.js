import assert from "node:assert/strict";
import test from "node:test";
import { mockJobs } from "../src/data/mockJobs.js";
import { createJobsState, jobFilterGroups } from "../src/data/jobFilters.js";
import { filterJobs, isJobClosed, sortJobs } from "../src/utils/jobFilters.js";
import { calculateMatchRate } from "../src/utils/jobMatching.js";
import { getPageNumbers, paginate } from "../src/utils/pagination.js";

const emptyFilters = () => createJobsState().filters;
const today = "2026-10-18";

test("mock 공고에 고유 ID와 모든 필터 옵션이 포함된다", () => {
  assert.ok(mockJobs.length >= 16);
  assert.equal(new Set(mockJobs.map((job) => job.id)).size, mockJobs.length);
  for (const { key, options } of jobFilterGroups) {
    for (const option of options) {
      assert.ok(filterJobs(mockJobs, "", { ...emptyFilters(), [key]: [option] }).length > 0, `${key}: ${option}`);
    }
  }
});

test("검색은 공백과 영문 대소문자를 처리하고 모든 검색 대상 필드를 찾는다", () => {
  assert.equal(filterJobs(mockJobs, "   ", emptyFilters()).length, mockJobs.length);
  assert.deepEqual(filterJobs(mockJobs, " jAvA ", emptyFilters()), filterJobs(mockJobs, "Java", emptyFilters()));
  for (const query of ["네이버클라우드", "서버 개발자", "데이터 분석가", "TypeScript", "확장 가능한"]) {
    assert.ok(filterJobs(mockJobs, query, emptyFilters()).length > 0, query);
  }
  assert.equal(filterJobs(mockJobs, "존재하지않는공고", emptyFilters()).length, 0);
});

test("같은 직무 카테고리는 OR, 기술은 AND, 서로 다른 카테고리는 AND로 결합한다", () => {
  const roles = ["백엔드 개발자", "프론트엔드 개발자"];
  const roleResults = filterJobs(mockJobs, "", { ...emptyFilters(), roles });
  assert.equal(roleResults.length, mockJobs.filter((job) => roles.includes(job.role)).length);
  const filters = { ...emptyFilters(), roles: ["백엔드 개발자"], skills: ["Java", "Spring"], regions: ["인천"], employmentTypes: ["정규직"] };
  const matches = filterJobs(mockJobs, "", filters);
  assert.deepEqual(matches.map((job) => job.company), ["인천소프트"]);
  assert.deepEqual(filterJobs(mockJobs, "java", filters), matches);
  assert.equal(filterJobs(mockJobs, "React", filters).length, 0);
});

test("경력은 최소 연수 이상을 찾으며 경력 무관 공고를 포함한다", () => {
  for (const [label, years] of [["경력 1년 이상", 1], ["경력 3년 이상", 3], ["경력 5년 이상", 5]]) {
    const results = filterJobs(mockJobs, "", { ...emptyFilters(), experiences: [label] });
    assert.ok(results.every((job) => job.minExperience === null || job.minExperience >= years));
  }
  const results = filterJobs(mockJobs, "", { ...emptyFilters(), experiences: ["신입", "경력 5년 이상"] });
  assert.ok(results.every((job) => job.minExperience === null || job.minExperience === 0 || job.minExperience >= 5));
});

test("지역과 고용 형태의 복수 선택은 각각 OR로 동작한다", () => {
  const filters = { ...emptyFilters(), regions: ["부산", "대전"], employmentTypes: ["계약직", "인턴"] };
  const results = filterJobs(mockJobs, "", filters);
  assert.ok(results.length > 1);
  assert.ok(results.every((job) => filters.regions.includes(job.region) && filters.employmentTypes.includes(job.employmentType)));
});

test("기술 매칭은 대소문자를 무시하고 2/3은 67%이며 빈 기술을 처리한다", () => {
  assert.equal(calculateMatchRate(["Java", "Spring", "AWS"], ["java", "SPRING", "Git"]), 67);
  assert.equal(calculateMatchRate(["Java", "java"], ["Java"]), 100);
  assert.equal(calculateMatchRate([], ["Java"]), 0);
  assert.equal(calculateMatchRate(["Java"], []), 0);
});

test("정렬은 원본을 변경하지 않고 최신순과 마감순을 적용하며 마감 공고를 마지막에 둔다", () => {
  const original = mockJobs.map((job) => job.id);
  for (const sort of ["latest", "deadline", "matching"]) {
    const sorted = sortJobs(mockJobs, sort, ["Java", "Spring"], today);
    const open = sorted.filter((job) => !isJobClosed(job, today));
    assert.ok(sorted.slice(open.length).every((job) => isJobClosed(job, today)));
    if (sort === "latest") assert.deepEqual(open.map((job) => job.postedAt), open.map((job) => job.postedAt).sort().reverse());
    if (sort === "deadline") assert.deepEqual(open.map((job) => job.deadline), open.map((job) => job.deadline).sort());
  }
  assert.deepEqual(mockJobs.map((job) => job.id), original);
  assert.equal(isJobClosed({ deadline: today, status: "채용중" }, today), false);
  assert.equal(isJobClosed({ deadline: "2026-10-17", status: "채용중" }, today), true);
});

test("매칭순은 동률일 때 최신 공고를 우선하고 역량이 없어도 안전하다", () => {
  const sorted = sortJobs(mockJobs, "matching", ["Java", "Spring"], today).filter((job) => !isJobClosed(job, today));
  for (let index = 1; index < sorted.length; index += 1) {
    const previous = calculateMatchRate(sorted[index - 1].skills, ["Java", "Spring"]);
    const current = calculateMatchRate(sorted[index].skills, ["Java", "Spring"]);
    assert.ok(previous >= current);
    if (previous === current) assert.ok(sorted[index - 1].postedAt >= sorted[index].postedAt);
  }
  assert.deepEqual(sortJobs(mockJobs, "matching", [], today), sortJobs(mockJobs, "latest", [], today));
});

test("페이지 분할은 10/20개, 마지막 페이지, 빈 결과, 범위를 벗어난 페이지를 처리한다", () => {
  const first = paginate(mockJobs, 1, 10);
  assert.equal(first.items.length, 10);
  assert.equal(first.pageCount, 3);
  assert.equal(first.start, 1);
  assert.equal(first.end, 10);
  const last = paginate(mockJobs, 999, 10);
  assert.equal(last.page, 3);
  assert.equal(last.items.length, 4);
  assert.equal(last.start, 21);
  assert.equal(last.end, 24);
  assert.equal(paginate(mockJobs, 1, 20).items.length, 20);
  assert.equal(paginate(mockJobs, -2, 10).page, 1);
  assert.deepEqual(paginate([], 4, 10), { items: [], page: 1, pageCount: 0, start: 0, end: 0 });
});

test("페이지 번호는 양 끝과 주변 페이지를 표시하고 필요한 경우에만 생략한다", () => {
  assert.deepEqual(getPageNumbers(1, 0), []);
  assert.deepEqual(getPageNumbers(2, 3), [1, 2, 3]);
  assert.deepEqual(getPageNumbers(1, 42), [1, 2, 3, 4, "end-gap", 42]);
  assert.deepEqual(getPageNumbers(21, 42), [1, "start-gap", 20, 21, 22, "end-gap", 42]);
  assert.deepEqual(getPageNumbers(42, 42), [1, "start-gap", 39, 40, 41, 42]);
});
