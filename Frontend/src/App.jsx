import { useEffect, useState } from "react";
import BackendHealthCheck from "./components/BackendHealthCheck.jsx";
import DashboardPage from "./pages/DashboardPage.jsx";
import MySkillsPage from "./pages/MySkillsPage.jsx";
import SkillAnalysisPage from "./pages/SkillAnalysisPage.jsx";
import TechTrendPage from "./pages/TechTrendPage.jsx";
import JobsPage from "./pages/JobsPage.jsx";
import JobDetailPage from "./pages/JobDetailPage.jsx";
import { createJobsState } from "./data/jobFilters.js";
import "./App.css";

function readRoute() {
  const detail = window.location.pathname.match(/^\/jobs\/([^/]+)\/?$/);
  if (detail) return { view: "job-detail", jobId: detail[1] };
  if (/^\/jobs\/?$/.test(window.location.pathname)) return { view: "jobs" };
  const view = new URLSearchParams(window.location.search).get("view");
  return { view: ["health", "skills", "analysis", "trends", "jobs"].includes(view) ? view : "dashboard" };
}

function App() {
  const [route, setRoute] = useState(readRoute);
  // 기존 역량 입력 페이지와 state를 공유합니다. 계정/API 저장은 사용하지 않습니다.
  const [skillProfile, setSkillProfile] = useState({ roleId: "", skills: [] });
  const [jobsState, setJobsState] = useState(() => createJobsState(new URLSearchParams(window.location.search).get("q") || ""));
  const [dashboardSection, setDashboardSection] = useState(null);

  useEffect(() => {
    function handlePopState() {
      setRoute(readRoute());
      setDashboardSection(null);
    }
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  function navigate(view, { section = null, jobId, query } = {}) {
    const url = new URL(window.location.href);
    if (view === "jobs" || view === "job-detail") {
      url.pathname = view === "jobs" ? "/jobs" : `/jobs/${jobId}`;
      url.search = "";
      if (query !== undefined) setJobsState(createJobsState(query));
    } else {
      if (/^\/jobs(?:\/|$)/.test(url.pathname)) url.pathname = "/";
      url.search = "";
      if (view !== "dashboard") url.searchParams.set("view", view);
    }
    url.hash = section || "";
    if (url.href !== window.location.href) window.history.pushState(null, "", url);
    setRoute({ view, jobId });
    setDashboardSection(section);
    window.scrollTo(0, 0);
  }

  const navigation = {
    onBrowseJobs: () => navigate("jobs"),
    onEditSkills: () => navigate("skills"),
    onViewAnalysis: () => navigate("analysis"),
    onViewTrends: () => navigate("trends"),
    onHomeAction: () => navigate("dashboard"),
  };

  if (route.view === "health") return <BackendHealthCheck />;
  if (route.view === "jobs") {
    return <JobsPage {...navigation} profile={skillProfile} state={jobsState} onStateChange={setJobsState} onViewJob={(job) => navigate("job-detail", { jobId: job.id })} />;
  }
  if (route.view === "job-detail") {
    return <JobDetailPage {...navigation} jobId={route.jobId} profile={skillProfile} />;
  }
  if (route.view === "skills") {
    return <MySkillsPage {...navigation} profile={skillProfile} onProfileChange={setSkillProfile} onNavigateDashboard={navigation.onHomeAction} />;
  }
  if (route.view === "analysis") {
    return <SkillAnalysisPage {...navigation} profile={skillProfile} onNavigateDashboard={navigation.onHomeAction} />;
  }
  if (route.view === "trends") {
    return <TechTrendPage {...navigation} onNavigateDashboard={navigation.onHomeAction} />;
  }
  return <DashboardPage {...navigation} skillProfile={skillProfile} onSearchJobs={(query) => navigate("jobs", { query })} initialSection={dashboardSection} />;
}

export default App;