import { useEffect, useState } from "react";
import BackendHealthCheck from "./components/BackendHealthCheck.jsx";
import DashboardPage from "./pages/DashboardPage.jsx";
import MySkillsPage from "./pages/MySkillsPage.jsx";
import SkillAnalysisPage from "./pages/SkillAnalysisPage.jsx";
import TechTrendPage from "./pages/TechTrendPage.jsx";
import "./App.css";

function readView() {
  const view = new URLSearchParams(window.location.search).get("view");
  return ["health", "skills", "analysis", "trends"].includes(view) ? view : "dashboard";
}

function App() {
  const [currentView, setCurrentView] = useState(readView);
  const [skillProfile, setSkillProfile] = useState({ roleId: "", skills: [] });
  const [dashboardIntent, setDashboardIntent] = useState({ dialog: null, section: null });

  useEffect(() => {
    function handlePopState() {
      setCurrentView(readView());
      setDashboardIntent({ dialog: null, section: null });
    }

    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  function navigate(view, { dialog = null, section = null } = {}) {
    const url = new URL(window.location.href);

    if (view === "dashboard") url.searchParams.delete("view");
    else url.searchParams.set("view", view);

    url.hash = section || "";
    if (url.href !== window.location.href) window.history.pushState(null, "", url);

    setCurrentView(view);
    setDashboardIntent({ dialog, section });
    window.scrollTo(0, 0);
  }

  if (currentView === "health") return <BackendHealthCheck />;

  if (currentView === "skills") {
    return (
      <MySkillsPage
        profile={skillProfile}
        onProfileChange={setSkillProfile}
        onViewAnalysis={() => navigate("analysis")}
        onViewTrends={() => navigate("trends")}
        onNavigateDashboard={(dialog, section) => navigate("dashboard", { dialog, section })}
      />
    );
  }

  if (currentView === "analysis") {
    return (
      <SkillAnalysisPage
        profile={skillProfile}
        onEditSkills={() => navigate("skills")}
        onViewTrends={() => navigate("trends")}
        onNavigateDashboard={(dialog, section) => navigate("dashboard", { dialog, section })}
      />
    );
  }

  if (currentView === "trends") {
    return (
      <TechTrendPage
        onEditSkills={() => navigate("skills")}
        onViewAnalysis={() => navigate("analysis")}
        onNavigateDashboard={(dialog) => navigate("dashboard", { dialog })}
      />
    );
  }

  return (
    <DashboardPage
      skillProfile={skillProfile}
      onEditSkills={() => navigate("skills")}
      onViewAnalysis={() => navigate("analysis")}
      onViewTrends={() => navigate("trends")}
      onHomeAction={() => navigate("dashboard")}
      initialDialog={dashboardIntent.dialog}
      initialSection={dashboardIntent.section}
    />
  );
}

export default App;
