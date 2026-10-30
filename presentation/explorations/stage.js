/* Scale the fixed 1920×1080 stage to the window (letterbox, never reflow). */
(function () {
  const stage = document.getElementById('deckStage');
  function fit() {
    const f = Math.min(innerWidth / 1920, innerHeight / 1080);
    stage.style.transform = `translate(${(innerWidth - 1920 * f) / 2}px, ${(innerHeight - 1080 * f) / 2}px) scale(${f})`;
  }
  fit(); addEventListener('resize', fit);
  requestAnimationFrame(() => document.querySelector('.slide').classList.add('active', 'visible'));
})();
