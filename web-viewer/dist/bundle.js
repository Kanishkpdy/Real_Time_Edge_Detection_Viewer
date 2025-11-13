"use strict";
const resEl = document.getElementById("res");
const fpsEl = document.getElementById("fps");
const frameEl = document.getElementById("frame");
// Rename to avoid conflict with built-in 'frames'
let frameCount = 0;
let lastTime = performance.now();
function update() {
    frameCount++;
    const now = performance.now();
    if (now - lastTime >= 1000) {
        fpsEl.textContent = frameCount.toString();
        frameCount = 0;
        lastTime = now;
    }
    requestAnimationFrame(update);
}
update();
// Update resolution when sample image loads
frameEl.onload = () => {
    resEl.textContent = `${frameEl.naturalWidth}×${frameEl.naturalHeight}`;
};
