import { canRender, createScene } from "./create-scene.js";

const host = document.getElementById("roomOrb");
if (host && canRender()) {
  const canvas = document.createElement("canvas");
  canvas.className = "orb-scene";
  host.appendChild(canvas);
  const world = createScene(canvas, { cameraZ: 3.2 });
  const { THREE, scene, camera, renderer, clock } = world;
  const geo = new THREE.IcosahedronGeometry(1, world.mobile ? 3 : 5);
  const mat = new THREE.MeshPhysicalMaterial({
    color: 0x6d6bff,
    roughness: 0.12,
    metalness: 0.05,
    transmission: world.mobile ? 0 : 0.86,
    thickness: 1.1,
    iridescence: 1,
    iridescenceIOR: 1.3,
    clearcoat: 1,
    emissive: 0x1d4ed8,
    emissiveIntensity: 0.25,
  });
  const orb = new THREE.Mesh(geo, mat);
  scene.add(orb);
  scene.add(new THREE.AmbientLight(0x9aa4ff, 0.7));
  const rim = new THREE.PointLight(0x22d3ee, 8, 12);
  rim.position.set(2, 1, 2);
  scene.add(rim);

  function frame() {
    requestAnimationFrame(frame);
    if (!world.shouldRender()) return;
    const t = clock.getElapsedTime();
    const speaking = host.classList.contains("orb-speaking");
    const listening = host.classList.contains("orb-listening");
    const amp = speaking ? 0.08 : listening ? 0.05 : 0.02;
    orb.rotation.y = t * 0.35;
    orb.rotation.x = Math.sin(t * 0.6) * 0.15;
    const s = 1 + Math.sin(t * (speaking ? 6 : 2)) * amp;
    orb.scale.setScalar(s);
    mat.emissive.set(listening ? 0x0891b2 : speaking ? 0x7c3aed : 0x312e81);
    renderer.render(scene, camera);
  }
  (window.requestIdleCallback || ((fn) => fn()))(frame);
  window.addEventListener("pagehide", () => world.dispose());
}
