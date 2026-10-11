import { canRender, createScene } from "./create-scene.js";

const scoreEl = document.getElementById("overallScore");
if (scoreEl && canRender()) {
  const card = scoreEl.closest(".stat") || scoreEl.parentElement;
  if (card) {
    card.style.position = "relative";
    const canvas = document.createElement("canvas");
    canvas.className = "score-scene";
    canvas.style.opacity = "0.55";
    card.appendChild(canvas);
    const world = createScene(canvas, { cameraZ: 3.4 });
    const { THREE, scene, renderer, camera } = world;
    const read = () => {
      const n = Number.parseFloat(scoreEl.textContent);
      return Number.isFinite(n) ? Math.max(0, Math.min(10, n)) / 10 : 0.15;
    };
    const arc = new THREE.Mesh(
      new THREE.TorusGeometry(1, 0.08, 12, 64, Math.PI * 2 * Math.max(read(), 0.08)),
      new THREE.MeshBasicMaterial({ color: 0x34d399 })
    );
    scene.add(arc);
    function paint() {
      const amount = read();
      arc.geometry.dispose();
      arc.geometry = new THREE.TorusGeometry(1, 0.08, 12, 64, Math.max(0.2, Math.PI * 2 * amount));
      arc.material.color.set(amount < 0.5 ? 0xf87171 : amount < 0.7 ? 0xfbbf24 : 0x34d399);
      renderer.render(scene, camera);
    }
    const watch = new MutationObserver(paint);
    watch.observe(scoreEl, { childList: true, characterData: true, subtree: true });
    (window.requestIdleCallback || ((fn) => fn()))(paint);
    window.addEventListener("pagehide", () => {
      watch.disconnect();
      world.dispose();
    });
  }
}
