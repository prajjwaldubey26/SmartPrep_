(() => {
  const root = document.documentElement;
  const storedTheme = localStorage.getItem("smartprep_theme");
  const storedEffects = localStorage.getItem("smartprep_effects");
  const systemLight = window.matchMedia("(prefers-color-scheme: light)").matches;
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  root.dataset.theme = storedTheme || (systemLight ? "light" : "dark");
  if (storedEffects === "off" || reduce) root.dataset.effects = "off";

  function mountTools(host) {
    if (!host || host.querySelector(".sp-nav-tools")) return;
    const wrap = document.createElement("div");
    wrap.className = "sp-nav-tools";
    const theme = document.createElement("button");
    theme.type = "button";
    theme.className = "sp-theme-toggle";
    theme.textContent = root.dataset.theme === "light" ? "Dark" : "Light";
    theme.addEventListener("click", () => {
      const next = root.dataset.theme === "light" ? "dark" : "light";
      root.dataset.theme = next;
      localStorage.setItem("smartprep_theme", next);
      theme.textContent = next === "light" ? "Dark" : "Light";
    });
    const effects = document.createElement("button");
    effects.type = "button";
    effects.className = "sp-effects-toggle";
    effects.textContent = root.dataset.effects === "off" ? "Effects off" : "Reduce effects";
    effects.addEventListener("click", () => {
      const off = root.dataset.effects === "off";
      if (off) {
        delete root.dataset.effects;
        localStorage.setItem("smartprep_effects", "on");
        effects.textContent = "Reduce effects";
      } else {
        root.dataset.effects = "off";
        localStorage.setItem("smartprep_effects", "off");
        effects.textContent = "Effects off";
      }
    });
    wrap.append(theme, effects);
    host.appendChild(wrap);
  }

  document.querySelectorAll(".nav, .studio-nav-wrap").forEach(mountTools);

  const tiltOk = !reduce && root.dataset.effects !== "off" && matchMedia("(hover: hover) and (pointer: fine)").matches;
  if (!tiltOk) return;

  function bindTilt() {
  document.querySelectorAll(".stat, .form-card, .domain-card, .list-item, .liquid-plate").forEach((card) => {
    if (card.dataset.tiltBound) return;
    card.dataset.tiltBound = "1";
    card.setAttribute("data-tilt", "");
    let rx = 0;
    let ry = 0;
    let tx = 0;
    let ty = 0;
    let frame = 0;
    const paint = () => {
      rx += (tx - rx) * 0.12;
      ry += (ty - ry) * 0.12;
      card.style.transform = `perspective(1200px) rotateX(${rx}deg) rotateY(${ry}deg)`;
      frame = requestAnimationFrame(paint);
    };
    card.addEventListener("pointermove", (event) => {
      const box = card.getBoundingClientRect();
      const px = (event.clientX - box.left) / box.width - 0.5;
      const py = (event.clientY - box.top) / box.height - 0.5;
      ty = px * 8;
      tx = -py * 8;
      if (!frame) frame = requestAnimationFrame(paint);
    });
    card.addEventListener("pointerleave", () => {
      tx = 0;
      ty = 0;
    });
  });
  }

  bindTilt();
  document.addEventListener("DOMContentLoaded", () => setTimeout(bindTilt, 0));
})();
