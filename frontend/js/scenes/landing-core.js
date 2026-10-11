import { canRender, createScene } from "./create-scene.js";

const stage = document.querySelector(".studio-stage");
if (stage && canRender() && !document.body.classList.contains("chat-stage")) {
  const canvas = document.createElement("canvas");
  canvas.className = "core-scene";
  stage.prepend(canvas);
  const world = createScene(canvas, { cameraZ: 5.2 });
  const { THREE, scene, renderer, camera, clock } = world;
  const core = new THREE.Mesh(
    new THREE.TorusKnotGeometry(0.85, 0.22, world.mobile ? 80 : 140, 16),
    new THREE.MeshPhysicalMaterial({
      color: 0x8b5cf6,
      roughness: 0.08,
      transmission: world.mobile ? 0 : 0.9,
      thickness: 1.2,
      iridescence: 1,
      clearcoat: 1,
      ior: 1.35,
    })
  );
  scene.add(core);
  const ring = new THREE.Mesh(
    new THREE.TorusGeometry(1.7, 0.015, 12, 80),
    new THREE.MeshBasicMaterial({ color: 0x22d3ee })
  );
  ring.rotation.x = 1.2;
  scene.add(ring);
  const count = world.mobile ? 180 : 600;
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    positions[i * 3] = (Math.random() - 0.5) * 8;
    positions[i * 3 + 1] = (Math.random() - 0.5) * 5;
    positions[i * 3 + 2] = (Math.random() - 0.5) * 4;
  }
  const pointsGeo = new THREE.BufferGeometry();
  pointsGeo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  scene.add(new THREE.Points(pointsGeo, new THREE.PointsMaterial({ color: 0xc4b5fd, size: 0.025 })));
  scene.add(new THREE.AmbientLight(0xffffff, 0.6));
  const key = new THREE.DirectionalLight(0x6366f1, 2);
  key.position.set(-2, 2, 3);
  scene.add(key);

  let mx = 0;
  let my = 0;
  window.addEventListener("pointermove", (event) => {
    mx = (event.clientX / window.innerWidth - 0.5) * 0.6;
    my = (event.clientY / window.innerHeight - 0.5) * 0.4;
  }, { passive: true });

  function frame() {
    requestAnimationFrame(frame);
    if (!world.shouldRender()) return;
    const t = clock.getElapsedTime();
    core.rotation.y = t * 0.25 + mx;
    core.rotation.x = my * 0.4;
    ring.rotation.z = t * 0.15;
    camera.position.x += (mx * 0.8 - camera.position.x) * 0.04;
    camera.lookAt(0, 0, 0);
    renderer.render(scene, camera);
  }
  (window.requestIdleCallback || ((fn) => fn()))(frame);
  window.addEventListener("pagehide", () => world.dispose());
}
