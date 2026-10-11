import * as THREE from "three";

export function canRender() {
  const root = document.documentElement;
  if (root.dataset.effects === "off") return false;
  if (matchMedia("(prefers-reduced-motion: reduce)").matches) return false;
  try {
    const test = document.createElement("canvas");
    return Boolean(test.getContext("webgl2") || test.getContext("webgl"));
  } catch {
    return false;
  }
}

export function createScene(canvas, { cameraZ = 4 } = {}) {
  const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.setClearColor(0x000000, 0);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 40);
  camera.position.z = cameraZ;
  const mobile = window.innerWidth < 760;
  let running = true;
  const clock = new THREE.Clock();

  function resize() {
    const width = canvas.clientWidth || canvas.parentElement?.clientWidth || 300;
    const height = canvas.clientHeight || canvas.parentElement?.clientHeight || 300;
    if (!width || !height) return;
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
  }

  const observer = new IntersectionObserver((entries) => {
    running = entries.some((entry) => entry.isIntersecting);
  });
  observer.observe(canvas);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) running = false;
  });
  window.addEventListener("resize", resize);
  if (window.ResizeObserver) {
    new ResizeObserver(resize).observe(canvas.parentElement || canvas);
  }
  resize();

  return {
    THREE,
    renderer,
    scene,
    camera,
    clock,
    mobile,
    resize,
    shouldRender: () => running && document.visibilityState !== "hidden",
    dispose() {
      observer.disconnect();
      renderer.dispose();
      scene.traverse((obj) => {
        if (obj.geometry) obj.geometry.dispose();
        if (obj.material) {
          const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
          mats.forEach((mat) => mat.dispose());
        }
      });
    },
  };
}
