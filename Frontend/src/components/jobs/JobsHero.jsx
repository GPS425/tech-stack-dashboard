import { popularJobKeywords } from "../../data/jobFilters.js";

function JobsHero({ query, onQueryChange, onSearch }) {
  return (
    <section className="jobs-hero" aria-labelledby="jobs-title">
      <div className="content-container jobs-hero-inner">
        <div className="jobs-hero-copy">
          <h1 id="jobs-title">채용 공고</h1>
          <p>다양한 기업의 최신 채용공고를 확인하고<br />나에게 맞는 기회를 찾아보세요.</p>
        </div>
        <div className="jobs-hero-search">
          <form className="jobs-search-form" role="search" onSubmit={(event) => { event.preventDefault(); onSearch(query); }}>
            <label className="jobs-search-field">
              <span className="sr-only">채용공고 검색</span>
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg>
              <input type="search" placeholder="회사명, 직무명, 기술스택 등 키워드로 검색하세요." value={query} onChange={(event) => onQueryChange(event.target.value)} />
            </label>
            <button className="primary-button" type="submit">검색</button>
          </form>
          <div className="jobs-popular-keywords">
            <span>인기 검색어</span>
            {popularJobKeywords.map((keyword) => <button type="button" key={keyword} onClick={() => onSearch(keyword)}>{keyword}</button>)}
          </div>
        </div>
        <svg className="jobs-hero-illustration" viewBox="0 0 150 150" fill="none" aria-hidden="true">
          <path d="M38 33C62-7 125 5 127 44c1 28 27 27 13 59-16 35-89 33-111 8C9 87 20 63 38 33Z" fill="#ddf5e7" />
          <rect x="42" y="24" width="76" height="105" rx="5" fill="white" stroke="#bdecd8" strokeWidth="1.5" />
          <path d="M56 41h32" stroke="#b3eacd" strokeWidth="5" strokeLinecap="round" />
          <path d="M56 54h49M56 63h45M56 72h39" stroke="#e9f8f0" strokeWidth="4" />
          <rect x="55" y="87" width="20" height="24" rx="3" fill="#9de2bf" />
          <path d="M83 90h22M83 99h22M83 108h16" stroke="#e6f7ee" strokeWidth="4" />
          <circle cx="110" cy="84" r="19" stroke="#008864" strokeWidth="5" />
          <path d="m123 99 16 17" stroke="#54c990" strokeWidth="8" strokeLinecap="round" />
          <path d="m23 22 8 8m1-14 2 10m-19 7 12 5" stroke="#bff0d3" strokeWidth="2.5" strokeLinecap="round" />
        </svg>
      </div>
    </section>
  );
}

export default JobsHero;
