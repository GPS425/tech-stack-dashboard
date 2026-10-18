export function calculateMatchRate(requiredSkills, userSkills) {
  if (!requiredSkills.length || !userSkills.length) return 0;
  const owned = new Set(userSkills.map((skill) => skill.trim().toLowerCase()));
  const required = [...new Set(requiredSkills.map((skill) => skill.trim().toLowerCase()))];
  return Math.round(required.filter((skill) => owned.has(skill)).length / required.length * 100);
}
