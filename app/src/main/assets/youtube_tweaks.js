/*
 * AutoVideo: ajustes sobre m.youtube.com para que se vea como una app y no como una página.
 * - Oculta "Abrir app" y promociones de la app.
 * - Deja lugar en la barra de YouTube para los botones propios de AutoVideo (arriba a la izquierda).
 * - Mientras se ve un video: lista de videos a la izquierda y reproductor a la derecha,
 *   con el título y los botones debajo del reproductor.
 * Se puede inyectar varias veces: solo se inicializa una vez por página.
 */
(function () {
  if (window.__autoVideoTweaks) return;
  window.__autoVideoTweaks = true;

  var CSS = [
    /* ---------- General ---------- */
    'html[data-autovideo] { -webkit-tap-highlight-color: transparent; }',
    'html[data-autovideo] ::-webkit-scrollbar { display: none; }',
    'html[data-autovideo] a[href^="intent:"], html[data-autovideo] a[href^="vnd.youtube"],',
    'html[data-autovideo] ytm-mealbar-promo-renderer, html[data-autovideo] ytm-companion-slot { display: none !important; }',

    /* ---------- Barra de YouTube: lugar para los botones de AutoVideo, sin su buscador ---------- */
    'html[data-autovideo] header.mobile-topbar-header { padding-left: 140px !important; box-sizing: border-box !important; }',
    'html[data-autovideo] .topbar-button-search-button,',
    'html[data-autovideo] header.mobile-topbar-header yt-searchbox { display: none !important; }',

    /* ---------- Viendo un video: lista a la izquierda, reproductor a la derecha ---------- */
    'html[data-autovideo="watch"] {',
    '  --av-list: 38vw;',
    '  --av-pw: calc(100vw - var(--av-list));',
    '  --av-ph: min(calc(var(--av-pw) * 0.5625), 72vh);',
    '  --av-bg: var(--yt-spec-base-background, #0f0f0f);',
    '}',
    'html[data-autovideo="watch"] ytm-mobile-topbar-renderer#header-bar { display: none !important; }',
    'html[data-autovideo="watch"] ytm-app { padding-top: 0 !important; }',

    /* Reproductor fijo arriba a la derecha */
    'html[data-autovideo="watch"] #player-container-id {',
    '  position: fixed !important; top: 0 !important; right: 0 !important; left: auto !important; bottom: auto !important;',
    '  width: var(--av-pw) !important; height: var(--av-ph) !important;',
    '  transform: none !important; z-index: 3 !important; background: #000 !important;',
    '}',
    'html[data-autovideo="watch"] #player-container-id #player,',
    'html[data-autovideo="watch"] #player-container-id #movie_player,',
    'html[data-autovideo="watch"] #player-container-id .player-size {',
    '  width: 100% !important; height: 100% !important; max-height: none !important; padding: 0 !important; margin: 0 !important;',
    '}',
    'html[data-autovideo="watch"] #player-cinematics-container,',
    'html[data-autovideo="watch"] ytm-cinematic-container-renderer,',
    'html[data-autovideo="watch"] ytm-watch .player-placeholder { display: none !important; }',

    /* Título, canal y botones debajo del reproductor */
    'html[data-autovideo="watch"] ytm-slim-video-metadata-section-renderer {',
    '  position: fixed !important; top: var(--av-ph) !important; right: 0 !important; left: auto !important;',
    '  width: var(--av-pw) !important; max-height: calc(100vh - var(--av-ph)) !important;',
    '  overflow-y: auto !important; box-sizing: border-box !important; z-index: 3 !important;',
    '  background: var(--av-bg) !important; padding-top: 4px !important;',
    '}',

    /* Columna izquierda: solo la lista de videos */
    'html[data-autovideo="watch"] ytm-watch { width: var(--av-list) !important; max-width: var(--av-list) !important; margin: 0 !important; }',
    /* Con un Mix abierto YouTube oculta la página del video; se mantiene visible para que el
       título y los botones sigan debajo del reproductor (el panel del Mix la tapa a la izquierda). */
    'html[data-autovideo="watch"] ytm-watch { display: block !important; }',
    'html[data-autovideo="watch"] .watch-below-the-player { padding-top: 52px !important; }',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer > ytm-item-section-renderer,',
    'html[data-autovideo="watch"] ytm-reel-shelf-renderer { display: none !important; }',

    /* Filas compactas: miniatura a la izquierda, título a la derecha */
    'html[data-autovideo="watch"] .related-items-container ytm-media-item {',
    '  display: flex !important; flex-direction: row !important; align-items: flex-start !important;',
    '  gap: 8px !important; padding: 6px 8px !important; margin: 0 !important; box-sizing: border-box !important;',
    '}',
    'html[data-autovideo="watch"] .related-items-container a.media-item-thumbnail-container {',
    '  flex: 0 0 46% !important; width: 46% !important; max-width: 46% !important; margin: 0 !important; padding: 0 !important;',
    '}',
    'html[data-autovideo="watch"] .related-items-container ytm-thumbnail-cover {',
    '  width: 100% !important; height: auto !important; aspect-ratio: 16 / 9 !important;',
    '}',
    'html[data-autovideo="watch"] .related-items-container .media-item-details {',
    '  flex: 1 1 auto !important; min-width: 0 !important; margin: 0 !important; padding: 0 !important;',
    '}',
    'html[data-autovideo="watch"] .related-items-container .media-channel,',
    'html[data-autovideo="watch"] .related-items-container ytm-bottom-sheet-renderer.media-item-menu { display: none !important; }',
    'html[data-autovideo="watch"] .related-items-container .media-item-headline {',
    '  font-size: 13px !important; line-height: 17px !important; max-height: 34px !important;',
    '  display: -webkit-box !important; -webkit-line-clamp: 2 !important; -webkit-box-orient: vertical !important; overflow: hidden !important;',
    '}',
    'html[data-autovideo="watch"] .related-items-container .media-item-metadata a > div { font-size: 11px !important; line-height: 14px !important; }',

    /* Paneles de YouTube (lista de un Mix, descripción, comentarios): en la columna izquierda.
       Cuando se cierran, YouTube los quita de la página, así que no quedan tapando nada. */
    'html[data-autovideo="watch"] ytm-engagement-panel {',
    '  position: fixed !important; top: 0 !important; left: 0 !important; right: auto !important; bottom: 0 !important;',
    '  width: var(--av-list) !important; max-width: var(--av-list) !important; height: 100vh !important;',
    '  transform: none !important; z-index: 2 !important; overflow: hidden !important; background: var(--av-bg) !important;',
    '}',
    'html[data-autovideo="watch"] ytm-engagement-panel > *,',
    'html[data-autovideo="watch"] ytm-engagement-panel .engagement-panel-section-list-background,',
    'html[data-autovideo="watch"] ytm-engagement-panel .engagement-panel-container {',
    '  height: 100% !important; max-height: 100% !important; transform: none !important; background: transparent !important;',
    '}',
    'html[data-autovideo="watch"] ytm-engagement-panel .engagement-panel-container {',
    '  display: flex !important; flex-direction: column !important; padding-top: 48px !important; box-sizing: border-box !important;',
    '}',
    'html[data-autovideo="watch"] ytm-engagement-panel .engagement-panel-content-wrapper {',
    '  flex: 1 1 auto !important; min-height: 0 !important; height: auto !important; overflow-y: auto !important;',
    '}',

    /* Barra "a continuación" del Mix (panel cerrado): abajo de la columna izquierda */
    'html[data-autovideo="watch"] ytm-playlist-panel-entry-point {',
    '  left: 8px !important; right: auto !important; bottom: 8px !important;',
    '  width: calc(var(--av-list) - 16px) !important; max-width: calc(var(--av-list) - 16px) !important; box-sizing: border-box !important;',
    '}',
    'html[data-autovideo="watch"] .watch-below-the-player { padding-bottom: 72px !important; }',

    /* Filas compactas también en la lista del Mix */
    'html[data-autovideo="watch"] ytm-playlist-panel-video-renderer .YtmCompactMediaItemHost {',
    '  gap: 8px !important; padding: 6px 8px !important; margin: 0 !important; box-sizing: border-box !important;',
    '}',
    'html[data-autovideo="watch"] ytm-playlist-panel-video-renderer a.YtmCompactMediaItemImage {',
    '  flex: 0 0 46% !important; width: 46% !important; max-width: 46% !important; height: auto !important; margin: 0 !important;',
    '}',
    'html[data-autovideo="watch"] ytm-playlist-panel-video-renderer ytm-compact-thumbnail {',
    '  width: 100% !important; height: auto !important; aspect-ratio: 16 / 9 !important;',
    '}',
    'html[data-autovideo="watch"] ytm-playlist-panel-video-renderer .YtmCompactMediaItemMetadata,',
    'html[data-autovideo="watch"] ytm-playlist-panel-video-renderer .YtmCompactMediaItemMetadataContent { min-width: 0 !important; padding: 0 !important; }',
    'html[data-autovideo="watch"] ytm-playlist-panel-video-renderer .YtmCompactMediaItemHeadline {',
    '  font-size: 13px !important; line-height: 17px !important; max-height: 34px !important; margin: 0 !important; padding: 0 !important;',
    '  display: -webkit-box !important; -webkit-line-clamp: 2 !important; -webkit-box-orient: vertical !important; overflow: hidden !important;',
    '}',
    'html[data-autovideo="watch"] ytm-playlist-panel-video-renderer .YtmCompactMediaItemByline {',
    '  font-size: 11px !important; line-height: 14px !important; padding: 0 !important;',
    '}'
  ].join('\n');

  function addStyle() {
    if (document.getElementById('autovideo-style')) return;
    var s = document.createElement('style');
    s.id = 'autovideo-style';
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  /* "Abrir app" y similares: se ocultan por su texto, así funciona aunque YouTube cambie el diseño. */
  var OPEN_APP = /^(abrir( la)? (app|aplicaci[oó]n)|open( the)? app|usar la app|obtener la app|get the app|ver en la app)$/i;
  function hideOpenApp() {
    var scope = document.querySelectorAll(
      'ytm-mobile-topbar-renderer a, ytm-mobile-topbar-renderer button, ytm-mobile-topbar-renderer ytm-button-renderer,' +
      'ytm-mobile-topbar-renderer span, ytm-mobile-topbar-renderer div'
    );
    for (var i = 0; i < scope.length; i++) {
      var el = scope[i];
      var text = (el.textContent || '').trim();
      if (text.length > 0 && text.length < 30 && OPEN_APP.test(text)) {
        var target = el.closest('ytm-button-renderer, button, a') || el;
        target.style.setProperty('display', 'none', 'important');
      }
    }
  }

  var lastUrl = null;
  function updateMode() {
    var root = document.documentElement;
    if (!root) return;
    var mode = location.pathname.indexOf('/watch') === 0 ? 'watch' : 'browse';
    if (root.getAttribute('data-autovideo') !== mode) root.setAttribute('data-autovideo', mode);
    addStyle();
    if (location.href !== lastUrl) {
      lastUrl = location.href;
      /* El reproductor recalcula su tamaño con el evento resize. */
      [0, 300, 1000, 2500].forEach(function (t) {
        setTimeout(function () { window.dispatchEvent(new Event('resize')); }, t);
      });
    }
  }

  var pending = false;
  function schedule() {
    if (pending) return;
    pending = true;
    setTimeout(function () {
      pending = false;
      updateMode();
      hideOpenApp();
    }, 150);
  }

  ['pushState', 'replaceState'].forEach(function (name) {
    var original = history[name];
    history[name] = function () {
      var result = original.apply(this, arguments);
      schedule();
      return result;
    };
  });
  window.addEventListener('popstate', schedule);

  function start() {
    updateMode();
    hideOpenApp();
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    setInterval(updateMode, 1000);
  }

  if (document.documentElement) {
    start();
  } else {
    document.addEventListener('DOMContentLoaded', start);
  }
})();
