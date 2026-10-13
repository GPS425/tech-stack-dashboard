function Header({ onBrowseJobs, onEditSkills, onViewAnalysis, onAccountAction, onHomeAction, onViewTrends, activePage }) {
  return (
    <header className="site-header">
      <div className="header-inner">
        <a
          className="brand"
          href="./"
          aria-label="JOBSTACK 메인 화면"
          onClick={(event) => {
            if (onHomeAction) { event.preventDefault(); onHomeAction(); }
          }}
        >JOBSTACK</a>
        <nav className="main-nav" aria-label="주요 메뉴">
          <button type="button" onClick={onBrowseJobs}>채용공고</button>
          <button type="button" onClick={onEditSkills} aria-current={activePage === "skills" ? "page" : undefined}>내 역량 입력</button>
          <button type="button" onClick={onViewAnalysis} aria-current={activePage === "analysis" ? "page" : undefined}>내 기술 분석</button>
          {onViewTrends
            ? <button type="button" onClick={onViewTrends} aria-current={activePage === "trends" ? "page" : undefined}>기술 트렌드</button>
            : <a href="?view=trends">기술 트렌드</a>}
        </nav>
        <div className="account-actions">
          <button className="login-button" type="button" onClick={() => onAccountAction("login")}>로그인</button>
          <button className="signup-button" type="button" onClick={() => onAccountAction("signup")}>회원가입</button>
        </div>
      </div>
    </header>
  );
}

export default Header;
