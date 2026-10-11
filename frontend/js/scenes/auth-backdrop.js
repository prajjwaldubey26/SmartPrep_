import { canRender, createScene } from "./create-scene.js";

const shell = document.querySelector(".page-shell");
const authPage = document.getElementById("loginForm") || document.getElementById("registerForm");
if (shell && authPage && canRender()) {
    const canvas = document.createElement("canvas");
    canvas.className = "auth-scene";
    shell.prepend(canvas);
    const world = createScene(canvas, { cameraZ: 4.5 });
    const { THREE, scene, renderer, camera, clock } = world;
    const sphere = new THREE.Mesh(
      new THREE.SphereGeometry(1.3, 32, 20),
      new THREE.MeshBasicMaterial({ color: 0x8b5cf6, wireframe: true, transparent: true, opacity: 0.35 })
    );
    scene.add(sphere);
    function frame() {
      requestAnimationFrame(frame);
      if (!world.shouldRender()) return;
      sphere.rotation.y = clock.getElapsedTime() * 0.12;
      sphere.rotation.x = 0.4;
      renderer.render(scene, camera);
    }
    (window.requestIdleCallback || ((fn) => fn()))(frame);
    window.addEventListener("pagehide", () => world.dispose());
}
