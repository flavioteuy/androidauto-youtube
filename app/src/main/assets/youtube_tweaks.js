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

  /* ---------- La página cree que siempre está visible ----------
     Cuando Android Auto tapa la pantalla al manejar, YouTube pausaría el video; así el audio sigue
     (la imagen igual queda oculta por Android Auto). */
  try {
    var always = function (value) { return { configurable: true, get: function () { return value; } }; };
    Object.defineProperty(Document.prototype, 'hidden', always(false));
    Object.defineProperty(Document.prototype, 'visibilityState', always('visible'));
    Object.defineProperty(Document.prototype, 'webkitHidden', always(false));
    Object.defineProperty(Document.prototype, 'webkitVisibilityState', always('visible'));
    ['visibilitychange', 'webkitvisibilitychange'].forEach(function (ev) {
      window.addEventListener(ev, function (e) { e.stopImmediatePropagation(); }, true);
    });
  } catch (e) { /* sin Page Visibility API */ }

  /* ---------- Lo que suena, para el tablero del auto y los botones del volante ----------
     Se guardan las acciones que YouTube registra (siguiente, anterior...) para poder llamarlas. */
  var mediaHandlers = {};
  try {
    var ms = navigator.mediaSession;
    if (ms && ms.setActionHandler) {
      var originalSetHandler = ms.setActionHandler.bind(ms);
      ms.setActionHandler = function (action, handler) {
        mediaHandlers[action] = handler;
        try { return originalSetHandler(action, handler); } catch (e) { return undefined; }
      };
    }
  } catch (e) { /* sin Media Session */ }

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
    /* El reproductor se achica lo justo para que debajo entren los botones y los comentarios
       (alturas medidas en vivo: --av-meta-h y --av-extra-h), sin bajar de 42% de la pantalla. */
    '  --av-ph: max(42vh, min(calc(var(--av-pw) * 0.5625), calc(100vh - var(--av-meta-h, 0px) - var(--av-extra-h, 0px))));',
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
    '  display: flex !important; visibility: visible !important;',
    '  position: fixed !important; top: var(--av-ph) !important; right: 0 !important; left: auto !important;',
    '  width: var(--av-pw) !important; max-height: calc(100vh - var(--av-ph)) !important;',
    '  overflow-y: auto !important; box-sizing: border-box !important; z-index: 3 !important;',
    '  background: var(--av-bg) !important; padding-top: 4px !important;',
    '}',

    /* Tarjeta de Comentarios debajo de los botones (al tocarla, los comentarios se abren a la izquierda) */
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer ytm-item-section-renderer:has(yt-video-metadata-carousel-view-model, comments-entry-point-teaser-view-model) {',
    '  display: block !important; visibility: visible !important;',
    '  position: fixed !important; top: calc(var(--av-ph) + var(--av-meta-h, 0px)) !important; right: 0 !important; left: auto !important;',
    '  width: var(--av-pw) !important; max-height: calc(100vh - var(--av-ph) - var(--av-meta-h, 0px)) !important;',
    '  overflow: hidden !important; box-sizing: border-box !important; z-index: 3 !important;',
    '  background: var(--av-bg) !important; margin: 0 !important;',
    '}',

    /* YouTube a veces oculta o transforma la página del video (por ejemplo con un Mix abierto).
       Se fuerza visible y sin transformaciones para que lo de arriba quede siempre en su lugar. */
    'html[data-autovideo="watch"] .watch-below-the-player { display: block !important; }',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer { display: flex !important; }',
    'html[data-autovideo="watch"] ytm-app, html[data-autovideo="watch"] .page-container, html[data-autovideo="watch"] ytm-watch,',
    'html[data-autovideo="watch"] .watch-below-the-player, html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer {',
    '  transform: none !important; filter: none !important; contain: none !important; will-change: auto !important; perspective: none !important;',
    '}',

    /* Columna izquierda: la lista de videos sugeridos */
    'html[data-autovideo="watch"] ytm-watch { width: var(--av-list) !important; max-width: var(--av-list) !important; margin: 0 !important; }',
    /* Con un Mix abierto YouTube oculta la página del video; se mantiene visible para que el
       título y los botones sigan debajo del reproductor (el panel del Mix la tapa a la izquierda). */
    'html[data-autovideo="watch"] ytm-watch { display: block !important; }',
    'html[data-autovideo="watch"] .watch-below-the-player { padding-top: 52px !important; }',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer ytm-item-section-renderer:not(:has(yt-video-metadata-carousel-view-model, comments-entry-point-teaser-view-model, ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-compact-radio-renderer, ytm-compact-playlist-renderer, yt-lockup-view-model)),',
    'html[data-autovideo="watch"] ytm-reel-shelf-renderer { display: none !important; }',

    /* Encabezado de YouTube: su fondo quedaba como una franja sobre el video. */
    'html[data-autovideo="watch"] .mobile-topbar-header-background { display: none !important; }',
    /* Chips ("Todos", "Relacionados"...) de la lista de sugeridos: van fijos y taparían el video. */
    'html[data-autovideo="watch"] ytm-related-chip-cloud-renderer,',
    'html[data-autovideo="watch"] #related-chips-sentinel { display: none !important; }',
    /* Bloque que envuelve el título, los botones y los comentarios en algunas versiones de la página.
       YouTube lo desplaza con una animación (y lo oculta con algunas listas): debe quedar visible y quieto
       para no "encerrar" a los elementos fijos. */
    'html[data-autovideo="watch"] .related-chips-slot-wrapper {',
    '  display: block !important; contain: none !important; container-type: normal !important;',
    '  transform: none !important; transition: none !important; overflow: visible !important;',
    '}',
    /* Franja de fondo de la barra del Mix: solo en la columna izquierda y sin bloquear toques. */
    'html[data-autovideo="watch"] .playlist-entrypoint-background-protection {',
    '  left: 0 !important; right: auto !important; width: var(--av-list) !important;',
    '  background: none !important; pointer-events: none !important;',
    '}',
    'html[data-autovideo="watch"] ytm-playlist-panel-entry-point { pointer-events: auto !important; }',

    /* Filas compactas: miniatura a la izquierda, título a la derecha */
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer ytm-media-item {',
    '  display: flex !important; flex-direction: row !important; align-items: flex-start !important;',
    '  gap: 8px !important; padding: 6px 8px !important; margin: 0 !important; box-sizing: border-box !important;',
    '}',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer a.media-item-thumbnail-container {',
    '  flex: 0 0 46% !important; width: 46% !important; max-width: 46% !important; margin: 0 !important; padding: 0 !important;',
    '}',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer ytm-thumbnail-cover {',
    '  width: 100% !important; height: auto !important; aspect-ratio: 16 / 9 !important;',
    '}',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer .media-item-details {',
    '  flex: 1 1 auto !important; min-width: 0 !important; margin: 0 !important; padding: 0 !important;',
    '}',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer .media-channel,',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer ytm-bottom-sheet-renderer.media-item-menu { display: none !important; }',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer .media-item-headline {',
    '  font-size: 13px !important; line-height: 17px !important; max-height: 34px !important;',
    '  display: -webkit-box !important; -webkit-line-clamp: 2 !important; -webkit-box-orient: vertical !important; overflow: hidden !important;',
    '}',
    'html[data-autovideo="watch"] ytm-single-column-watch-next-results-renderer .media-item-metadata a > div { font-size: 11px !important; line-height: 14px !important; }',

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

    /* Filas compactas también en la lista del Mix y en los Mix sugeridos */
    'html[data-autovideo="watch"] :is(ytm-playlist-panel-video-renderer, ytm-single-column-watch-next-results-renderer) .YtmCompactMediaItemHost {',
    '  gap: 8px !important; padding: 6px 8px !important; margin: 0 !important; box-sizing: border-box !important;',
    '}',
    'html[data-autovideo="watch"] :is(ytm-playlist-panel-video-renderer, ytm-single-column-watch-next-results-renderer) a.YtmCompactMediaItemImage {',
    '  flex: 0 0 46% !important; width: 46% !important; max-width: 46% !important; height: auto !important; margin: 0 !important;',
    '}',
    'html[data-autovideo="watch"] :is(ytm-playlist-panel-video-renderer, ytm-single-column-watch-next-results-renderer) ytm-compact-thumbnail {',
    '  width: 100% !important; height: auto !important; aspect-ratio: 16 / 9 !important;',
    '}',
    'html[data-autovideo="watch"] :is(ytm-playlist-panel-video-renderer, ytm-single-column-watch-next-results-renderer) .YtmCompactMediaItemMetadata,',
    'html[data-autovideo="watch"] :is(ytm-playlist-panel-video-renderer, ytm-single-column-watch-next-results-renderer) .YtmCompactMediaItemMetadataContent { min-width: 0 !important; padding: 0 !important; }',
    'html[data-autovideo="watch"] :is(ytm-playlist-panel-video-renderer, ytm-single-column-watch-next-results-renderer) .YtmCompactMediaItemHeadline {',
    '  font-size: 13px !important; line-height: 17px !important; max-height: 34px !important; margin: 0 !important; padding: 0 !important;',
    '  display: -webkit-box !important; -webkit-line-clamp: 2 !important; -webkit-box-orient: vertical !important; overflow: hidden !important;',
    '}',
    'html[data-autovideo="watch"] :is(ytm-playlist-panel-video-renderer, ytm-single-column-watch-next-results-renderer) .YtmCompactMediaItemByline {',
    '  font-size: 11px !important; line-height: 14px !important; padding: 0 !important;',
    '}',

    /* Videos "Próximamente" (estrenos y transmisiones programadas): no hay video que mostrar y el
       recuadro quedaba negro. Se muestra la miniatura del video ENCIMA de todas las capas del
       reproductor (alguna de ellas es negra según la versión de YouTube), sin bloquear toques. */
    '#av-upcoming-thumb { display: none; }',
    'html[data-av-upcoming] #av-upcoming-thumb {',
    '  display: block !important; position: absolute !important; inset: 0 !important; z-index: 9999 !important;',
    '  visibility: visible !important; opacity: 1 !important;',
    '  background: #000 var(--av-thumb) center / cover no-repeat !important; pointer-events: none !important;',
    '}',
    'html[data-av-upcoming] #player-container-id #player,',
    'html[data-av-upcoming] #player-container-id .html5-video-player,',
    'html[data-av-upcoming] #player-container-id .html5-video-container,',
    'html[data-av-upcoming] #player-container-id .ytp-offline-slate,',
    'html[data-av-upcoming] #player-container-id .ytp-offline-slate-background { background-color: transparent !important; }',
    'html[data-av-upcoming] #player-container-id video { opacity: 0 !important; }',
    /* YouTube pone una imagen negra de relleno en el recuadro y oculta su propio aviso: se quita la
       imagen y se muestra el aviso del estreno ("Se estrena en..." y la fecha) sobre la miniatura. */
    'html[data-av-upcoming] #player-thumbnail-overlay { display: none !important; }',
    '#av-upcoming-thumb .av-upcoming-label:empty { display: none; }',
    '#av-upcoming-thumb .av-upcoming-label {',
    '  position: absolute; left: 10px; bottom: 10px; max-width: calc(100% - 20px); box-sizing: border-box;',
    '  padding: 6px 10px; border-radius: 8px; background: rgba(0, 0, 0, 0.72); color: #fff;',
    '  font: 600 15px/1.25 Roboto, Arial, sans-serif;',
    '}',
    '#av-upcoming-thumb .av-upcoming-sub { display: block; font-weight: 400; font-size: 13px; opacity: 0.9; }',
    '#av-upcoming-thumb .av-upcoming-sub:empty { display: none; }'
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

  /* ---------- Videos "Próximamente": miniatura en el recuadro del reproductor ---------- */
  var UPCOMING_TEXT = /se estrena|pr[oó]ximamente|programad[oa]|en vivo en|premieres?|upcoming|scheduled|live in/i;
  /* Debajo del título, los estrenos dicen "1 en espera" (personas esperando el estreno). */
  var WAITING_TEXT = /\ben espera\b|\besperando\b|\bwaiting\b/i;
  var META_INFO_SEL = 'ytm-slim-video-metadata-section-renderer, ytm-slim-video-information-renderer';

  function metadataSaysWaiting() {
    var meta = document.querySelector(META_INFO_SEL);
    return !!(meta && WAITING_TEXT.test(meta.innerText || ''));
  }

  function currentVideoId() {
    if (location.pathname.indexOf('/watch') !== 0) return null;
    try { return new URLSearchParams(location.search).get('v'); } catch (e) { return null; }
  }

  function responseSaysUpcoming(pr, id) {
    if (!pr) return null;
    var vd = pr.videoDetails || {}, ps = pr.playabilityStatus || {};
    if (vd.videoId && vd.videoId !== id) return null;            // respuesta de otro video
    return !!vd.isUpcoming || ps.status === 'LIVE_STREAM_OFFLINE';
  }

  function textOf(x) {
    if (!x) return '';
    if (x.simpleText) return x.simpleText;
    return (x.runs || []).map(function (r) { return r.text || ''; }).join('');
  }

  /* Aviso del estreno ("Se estrena en 70 minutos", "3 de octubre a las 20:30") y su miniatura. */
  function upcomingSlate(id) {
    var yt = ytPlayer(), prs = [];
    try { if (yt && yt.getPlayerResponse) prs.push(yt.getPlayerResponse()); } catch (e) { /* sin reproductor */ }
    prs.push(window.ytInitialPlayerResponse);
    for (var i = 0; i < prs.length; i++) {
      var pr = prs[i];
      if (!pr || !pr.videoDetails || pr.videoDetails.videoId !== id) continue;
      var ps = pr.playabilityStatus || {};
      var ls = ps.liveStreamability && ps.liveStreamability.liveStreamabilityRenderer;
      var slate = ls && ls.offlineSlate && ls.offlineSlate.liveStreamOfflineSlateRenderer;
      var thumbs = slate && slate.thumbnail && slate.thumbnail.thumbnails;
      return {
        main: (slate && textOf(slate.mainText)) || ps.reason || '',
        sub: (slate && textOf(slate.subtitleText)) || '',
        thumb: thumbs && thumbs.length ? thumbs[thumbs.length - 1].url : ''
      };
    }
    return null;
  }

  function isUpcoming(id) {
    var yt = ytPlayer(), r = null;
    try { r = responseSaysUpcoming(yt && yt.getPlayerResponse ? yt.getPlayerResponse() : null, id); } catch (e) { r = null; }
    if (r === null) { try { r = responseSaysUpcoming(window.ytInitialPlayerResponse, id); } catch (e) { r = null; } }
    if (r) return true;
    var slate = document.querySelector('#player-container-id .ytp-offline-slate');
    if (slate && slate.offsetWidth > 0 && getComputedStyle(slate).display !== 'none') return true;
    if (r === false) return false;
    // Sin datos del reproductor (por ejemplo, YouTube no lo arma para un estreno): "1 en espera".
    if (metadataSaysWaiting()) return true;
    // Último recurso: el aviso que YouTube pone en el reproductor ("Se estrena el...").
    var pc = document.getElementById('player-container-id');
    var v = mainVideo();
    var noVideo = !v || !(v.currentSrc || v.src || v.srcObject);
    return !!(pc && noVideo && UPCOMING_TEXT.test(pc.innerText || ''));
  }

  function updateUpcoming() {
    var root = document.documentElement;
    var id = currentVideoId();
    var upcoming = !!id && isUpcoming(id);
    if (upcoming) {
      var pc = document.getElementById('player-container-id');
      var thumb = document.getElementById('av-upcoming-thumb');
      if (pc && !thumb) {
        thumb = document.createElement('div');
        thumb.id = 'av-upcoming-thumb';
        // YouTube exige Trusted Types: nada de innerHTML, se arma elemento por elemento.
        var labelBox = document.createElement('div');
        labelBox.className = 'av-upcoming-label';
        thumb.appendChild(labelBox);
      }
      // Al final del reproductor (encima de sus capas); se vuelve a poner si YouTube lo rearma.
      if (pc && thumb && (thumb.parentNode !== pc || pc.lastElementChild !== thumb)) pc.appendChild(thumb);
      var info = upcomingSlate(id) || {};
      var img = /^https:\/\/i\d?\.ytimg\.com\//.test(info.thumb || '') ? info.thumb
        : 'https://i.ytimg.com/vi/' + encodeURIComponent(id) + '/hqdefault.jpg';
      setVar(root, '--av-thumb', 'url("' + img.replace(/["\\]/g, '') + '")');
      var label = thumb && thumb.querySelector('.av-upcoming-label');
      if (label) {
        var key = (info.main || '') + '|' + (info.sub || '');
        if (label.getAttribute('data-key') !== key) {
          label.setAttribute('data-key', key);
          label.textContent = info.main || '';
          if (info.sub) {
            var sub = document.createElement('span');
            sub.className = 'av-upcoming-sub';
            sub.textContent = info.sub;
            label.appendChild(sub);
          }
        }
      }
      if (!root.hasAttribute('data-av-upcoming')) root.setAttribute('data-av-upcoming', '');
    } else if (root.hasAttribute('data-av-upcoming')) {
      root.removeAttribute('data-av-upcoming');
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

  /* Mide la altura de los botones y de la tarjeta de comentarios que van debajo del reproductor,
     para achicar el reproductor lo justo y que todo entre en la columna derecha. */
  var META_SEL = 'ytm-slim-video-metadata-section-renderer';
  var EXTRA_SEL = 'ytm-single-column-watch-next-results-renderer ytm-item-section-renderer:has(yt-video-metadata-carousel-view-model, comments-entry-point-teaser-view-model)';
  var COMMENTS_MAX_PX = 64;
  var resizeObserver = window.ResizeObserver ? new ResizeObserver(function () { measureRight(); }) : null;
  var observed = [];

  function setVar(root, name, value) {
    if (root.style.getPropertyValue(name) !== value) root.style.setProperty(name, value);
  }

  function measureRight() {
    var root = document.documentElement;
    if (!root || root.getAttribute('data-autovideo') !== 'watch') return;
    var meta = document.querySelector(META_SEL);
    var extra = null;
    try { extra = document.querySelector(EXTRA_SEL); } catch (e) { /* navegador sin :has() */ }
    setVar(root, '--av-meta-h', (meta ? Math.ceil(meta.scrollHeight) : 0) + 'px');
    setVar(root, '--av-extra-h', (extra ? Math.min(Math.ceil(extra.scrollHeight), COMMENTS_MAX_PX) : 0) + 'px');
    [meta, extra].forEach(function (el) {
      if (el && resizeObserver && observed.indexOf(el) < 0) {
        resizeObserver.observe(el);
        observed.push(el);
      }
    });
    [meta, extra, document.getElementById('player-container-id'),
      document.querySelector('ytm-engagement-panel'),
      document.querySelector('ytm-playlist-panel-entry-point')].forEach(unclamp);
  }

  /* ---------- Contenedores que impiden "position: fixed" ----------
     Si un antepasado tiene transform, filter, contain o container-type, los elementos "fijos"
     quedan atados a ese antepasado en vez de a la pantalla. Se anulan esas propiedades. */
  var neutralized = [];
  var UNCLAMP = [['transform', 'none'], ['filter', 'none'], ['perspective', 'none'], ['contain', 'none'],
    ['container-type', 'normal'], ['will-change', 'auto'], ['backdrop-filter', 'none'], ['transition', 'none']];

  function clampsFixed(cs) {
    return cs.transform !== 'none' || cs.filter !== 'none' || cs.perspective !== 'none' ||
      (cs.contain && cs.contain !== 'none') || (cs.containerType && cs.containerType !== 'normal') ||
      /transform|filter|perspective/.test(cs.willChange || '') ||
      (cs.backdropFilter && cs.backdropFilter !== 'none');
  }

  function unclamp(el) {
    if (!el) return;
    for (var a = el.parentElement; a && a !== document.documentElement; a = a.parentElement) {
      if (!clampsFixed(getComputedStyle(a))) continue;
      UNCLAMP.forEach(function (p) { a.style.setProperty(p[0], p[1], 'important'); });
      var d = describe(a);
      if (neutralized.indexOf(d) < 0 && neutralized.length < 30) neutralized.push(d);
    }
  }

  /* ---------- Diagnóstico: cómo está armada la página (sin textos ni datos de la cuenta) ---------- */
  function describe(el) {
    var s = el.tagName.toLowerCase();
    if (el.id) s += '#' + el.id;
    var c = typeof el.className === 'string' ? el.className : (el.getAttribute('class') || '');
    c = c.trim();
    if (c) s += '.' + c.split(/\s+/).slice(0, 3).join('.');
    return s;
  }

  function boxOf(el) {
    var r = el.getBoundingClientRect();
    var cs = getComputedStyle(el);
    return '[' + Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' +
      Math.round(r.height) + ' ' + cs.display + ' ' + cs.position +
      (cs.visibility !== 'visible' ? ' ' + cs.visibility : '') + ']';
  }

  function containment(el) {
    var cs = getComputedStyle(el), out = [];
    if (cs.transform !== 'none') out.push('transform');
    if (cs.filter !== 'none') out.push('filter');
    if (cs.contain && cs.contain !== 'none') out.push('contain:' + cs.contain);
    if (cs.containerType && cs.containerType !== 'normal') out.push('container:' + cs.containerType);
    if (cs.willChange && cs.willChange !== 'auto') out.push('will-change:' + cs.willChange);
    if (cs.overflow !== 'visible') out.push('overflow:' + cs.overflow);
    return out.length ? ' {' + out.join(' ') + '}' : '';
  }

  var SKIP_TAGS = /^(script|style|link|meta|title|svg|path|template|noscript)$/;

  function tree(el, depth, maxDepth, maxKids, out) {
    if (!el || out.length > 500) return;
    var kids = [];
    for (var i = 0; i < el.children.length; i++) {
      if (!SKIP_TAGS.test(el.children[i].tagName.toLowerCase())) kids.push(el.children[i]);
    }
    out.push(new Array(depth + 2).join('  ') + describe(el) + ' ' + boxOf(el) + containment(el) +
      (kids.length > maxKids ? ' (+' + kids.length + ')' : ''));
    if (depth >= maxDepth) return;
    for (var j = 0; j < kids.length && j < maxKids; j++) tree(kids[j], depth + 1, maxDepth, maxKids, out);
  }

  /* Capas del reproductor: orden, tamaño, z-index y fondos (para ver qué tapa la miniatura). */
  function styleOf(el) {
    var cs = getComputedStyle(el), s = ' z=' + cs.zIndex;
    if (cs.opacity !== '1') s += ' op=' + cs.opacity;
    if (cs.backgroundColor && cs.backgroundColor !== 'rgba(0, 0, 0, 0)') s += ' bg=' + cs.backgroundColor;
    if (cs.backgroundImage && cs.backgroundImage !== 'none') s += ' bgimg';
    if (cs.pointerEvents === 'none') s += ' sin-toques';
    if (el.tagName === 'IMG') {
      s += ' img ' + el.naturalWidth + 'x' + el.naturalHeight + (/^data:/.test(el.currentSrc || el.src || '') ? ' (relleno)' : '');
    }
    return s;
  }

  function playerTree(el, depth, out) {
    if (!el || out.length > 700) return;
    out.push(new Array(depth + 2).join('  ') + describe(el) + ' ' + boxOf(el) + styleOf(el));
    if (depth >= 5) return;
    for (var i = 0, n = 0; i < el.children.length && n < 12; i++) {
      if (SKIP_TAGS.test(el.children[i].tagName.toLowerCase())) continue;
      n++;
      playerTree(el.children[i], depth + 1, out);
    }
  }

  /* Por qué se detecta (o no) un estreno, sin datos del video ni de la cuenta. */
  function upcomingDebug() {
    var id = currentVideoId(), yt = ytPlayer(), parts = [];
    parts.push('reproductor ' + (yt ? 'si' : (document.getElementById('movie_player') ? 'sin funciones' : 'no')));
    function whose(vid) { return vid === id ? 'de este video' : (vid ? 'de otro video' : 'sin id'); }
    try {
      var pr = yt && yt.getPlayerResponse ? yt.getPlayerResponse() : null;
      parts.push('respuesta ' + (pr ? whose((pr.videoDetails || {}).videoId) + ' ' + ((pr.playabilityStatus || {}).status || '?') +
        ((pr.videoDetails || {}).isUpcoming ? ' isUpcoming' : '') : 'ninguna'));
    } catch (e) { parts.push('respuesta error'); }
    var ip = window.ytInitialPlayerResponse;
    parts.push('inicial ' + (ip && ip.videoDetails ? whose(ip.videoDetails.videoId) : 'ninguna'));
    try {
      var vd = yt && yt.getVideoData ? yt.getVideoData() : null;
      if (vd) parts.push('datos ' + whose(vd.video_id) + (vd.isPremiere ? ' estreno' : '') + (vd.isLive ? ' vivo' : ''));
      if (yt) parts.push('estado ' + yt.getPlayerState());
    } catch (e) { parts.push('datos error'); }
    var v = mainVideo();
    parts.push('video ' + (v ? ((v.currentSrc || v.src || v.srcObject) ? 'con fuente' : 'sin fuente') : 'no'));
    var slate = document.querySelector('#player-container-id .ytp-offline-slate');
    parts.push('aviso ' + (slate ? (slate.offsetWidth > 0 ? 'visible' : 'oculto') : 'no'));
    parts.push('en espera ' + (metadataSaysWaiting() ? 'si' : 'no'));
    parts.push('texto ' + (UPCOMING_TEXT.test((document.getElementById('player-container-id') || {}).innerText || '') ? 'si' : 'no'));
    var th = document.getElementById('av-upcoming-thumb');
    parts.push('miniatura ' + (th ? boxOf(th) + styleOf(th) : 'no'));
    return parts.join(' | ');
  }

  function chain(el, n) {
    var out = [];
    for (var a = el; a && n-- > 0; a = a.parentElement) out.push('  ' + describe(a) + ' ' + boxOf(a) + containment(a));
    return out;
  }

  function findLeafByText(re) {
    var all = document.querySelectorAll('h2, h3, span, div');
    for (var i = 0; i < all.length; i++) {
      if (all[i].children.length === 0 && re.test((all[i].textContent || '').trim())) return all[i];
    }
    return null;
  }

  var DIAG_SELECTORS = ['#player-container-id', 'ytm-watch', '.watch-below-the-player',
    'ytm-single-column-watch-next-results-renderer', 'ytm-slim-video-metadata-section-renderer',
    'ytm-slim-video-information-renderer', 'ytm-slim-video-action-bar-renderer', 'ytm-item-section-renderer',
    'yt-video-metadata-carousel-view-model', 'comments-entry-point-teaser-view-model', '.related-items-container',
    'ytm-video-with-context-renderer', 'ytm-compact-video-renderer', 'yt-lockup-view-model',
    'ytm-engagement-panel', 'ytm-playlist-panel-entry-point', 'ytm-mobile-topbar-renderer',
    'ytm-pivot-bar-renderer', '#autovideo-style'];

  window.__avDiag = function () {
    var L = [], root = document.documentElement;
    L.push('ua: ' + navigator.userAgent);
    L.push('viewport: ' + innerWidth + 'x' + innerHeight + ' dpr ' + devicePixelRatio + ' scrollY ' + Math.round(scrollY));
    L.push('pagina: ' + location.pathname + (location.search.indexOf('list=') >= 0 ? ' (lista/mix)' : ''));
    L.push('modo: ' + root.getAttribute('data-autovideo') + ' | meta-h ' + root.style.getPropertyValue('--av-meta-h') +
      ' | extra-h ' + root.style.getPropertyValue('--av-extra-h'));
    L.push('html.class: ' + root.className + ' | body.class: ' + (document.body ? document.body.className : ''));
    L.push('neutralizados: ' + (neutralized.join(' , ') || '-'));
    L.push('');
    L.push('== Selectores ==');
    DIAG_SELECTORS.forEach(function (s) {
      var els = document.querySelectorAll(s);
      L.push(s + ' -> ' + els.length + (els.length ? ' ' + boxOf(els[0]) + containment(els[0]) : ''));
    });
    L.push('');
    L.push('== body ==');
    if (document.body) {
      for (var i = 0; i < document.body.children.length; i++) {
        var b = document.body.children[i];
        if (!SKIP_TAGS.test(b.tagName.toLowerCase())) L.push('  ' + describe(b) + ' ' + boxOf(b) + containment(b));
      }
    }
    L.push('');
    L.push('proximamente: ' + (root.hasAttribute('data-av-upcoming') ? 'si' : 'no'));
    try { L.push('deteccion: ' + upcomingDebug()); } catch (e) { L.push('deteccion: error ' + e); }
    var pcd = document.getElementById('player-container-id');
    if (pcd) {
      L.push('');
      L.push('== reproductor ==');
      playerTree(pcd, 0, L);
      try {
        var pr = pcd.getBoundingClientRect();
        var hits = document.elementsFromPoint(pr.left + pr.width / 2, pr.top + pr.height / 2) || [];
        L.push('encima, en el centro del reproductor:');
        for (var h = 0; h < hits.length && h < 6; h++) L.push('  ' + describe(hits[h]) + ' ' + boxOf(hits[h]) + styleOf(hits[h]));
      } catch (e) { /* sin elementsFromPoint */ }
    }
    L.push('');
    L.push('== arbol ytm-app ==');
    tree(document.querySelector('ytm-app') || document.body, 0, 9, 8, L);
    var panel = document.querySelector('ytm-engagement-panel');
    if (panel) {
      L.push('');
      L.push('== panel ==');
      tree(panel, 0, 6, 5, L);
    }
    var com = findLeafByText(/^(comentarios|comments)$/i);
    if (com) {
      L.push('');
      L.push('== antepasados de "Comentarios" ==');
      L.push.apply(L, chain(com, 14));
    }
    var like = document.querySelector('button[aria-label*="gusta" i], button[aria-label*="like" i]');
    if (like) {
      L.push('');
      L.push('== antepasados del boton Me gusta ==');
      L.push.apply(L, chain(like, 14));
    }
    return L.join('\n');
  };

  var pending = false;
  function schedule() {
    if (pending) return;
    pending = true;
    setTimeout(function () {
      pending = false;
      safely(updateMode, hideOpenApp, measureRight, updateUpcoming);
    }, 150);
  }

  /* Cada ajuste por separado: si uno falla en alguna versión de YouTube, los demás igual corren. */
  function safely() {
    for (var i = 0; i < arguments.length; i++) {
      try { arguments[i](); } catch (e) { /* se reintenta en la próxima vuelta */ }
    }
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
  window.addEventListener('resize', function () { measureRight(); });

  /* ---------- Barra de tiempo del reproductor ----------
     YouTube guarda dónde está la barra de tiempo y solo lo recalcula con el evento "resize" de la
     ventana. Al entrar o salir de pantalla completa la ventana no cambia de tamaño (en el auto ya
     ocupa toda la pantalla), así que la barra seguía con la posición del reproductor chico y al
     arrastrarla el video iba a otro punto (más a la izquierda). Se avisa "resize" cada vez que el
     reproductor cambia de tamaño. */
  function nudgeResize() {
    [0, 150, 500, 1200].forEach(function (t) {
      setTimeout(function () { window.dispatchEvent(new Event('resize')); }, t);
    });
  }
  ['fullscreenchange', 'webkitfullscreenchange'].forEach(function (ev) {
    document.addEventListener(ev, nudgeResize, true);
  });
  var playerSizeObserver = null, observedPlayer = null, lastPlayerSize = '', playerNudgeTimer = 0;
  function watchPlayerSize() {
    var pc = document.getElementById('player-container-id');
    if (!pc || pc === observedPlayer || !window.ResizeObserver) return;
    if (!playerSizeObserver) {
      playerSizeObserver = new ResizeObserver(function (entries) {
        var r = entries[entries.length - 1].contentRect;
        var size = Math.round(r.width) + 'x' + Math.round(r.height);
        if (size === lastPlayerSize) return;
        lastPlayerSize = size;
        clearTimeout(playerNudgeTimer);
        playerNudgeTimer = setTimeout(nudgeResize, 100);
      });
    }
    if (observedPlayer) playerSizeObserver.unobserve(observedPlayer);
    playerSizeObserver.observe(pc);
    observedPlayer = pc;
  }

  /* ---------- Reproducción: datos para el tablero y acciones del volante ---------- */
  function mainVideo() {
    return document.querySelector('.html5-main-video') || document.querySelector('video');
  }

  function clickFirst(selectors) {
    for (var i = 0; i < selectors.length; i++) {
      var b = document.querySelector(selectors[i]);
      if (b) { b.click(); return true; }
    }
    return false;
  }

  /* El reproductor de YouTube expone su estado (duración, tiempo, estado); se usa si está. */
  function ytPlayer() {
    var p = document.getElementById('movie_player');
    return p && typeof p.getPlayerState === 'function' ? p : null;
  }

  window.__avMediaAction = function (action, arg) {
    var v = mainVideo();
    var yt = ytPlayer();
    if (action === 'play') {
      if (yt && yt.playVideo) { yt.playVideo(); return; }
      if (v) { var p = v.play(); if (p && p.catch) p.catch(function () {}); }
      return;
    }
    if (action === 'pause') {
      if (yt && yt.pauseVideo) { yt.pauseVideo(); return; }
      if (v) v.pause();
      return;
    }
    if (action === 'seekto') {
      if (!isFinite(arg)) return;
      if (yt && yt.seekTo) { yt.seekTo(arg, true); return; }
      if (v) v.currentTime = arg;
      return;
    }
    var h = mediaHandlers[action];
    if (h) { try { h({ action: action }); return; } catch (e) { /* se prueba lo siguiente */ } }
    if (action === 'nexttrack') {
      clickFirst(['.ytp-next-button', 'button[aria-label*="iguiente"]', 'button[aria-label*="Next"]']);
    } else if (action === 'previoustrack') {
      if (v && v.currentTime > 3) v.currentTime = 0; else history.back();
    }
  };

  /* Respaldo de la pantalla completa (lo llama la app justo después de que Android Auto tapa la
     pantalla): si el video quedó en pausa sin que nadie tocara la pantalla, se reanuda. */
  var lastTouchAt = 0;
  ['touchstart', 'pointerdown', 'mousedown', 'keydown'].forEach(function (ev) {
    window.addEventListener(ev, function () { lastTouchAt = Date.now(); }, true);
  });
  window.__avResumeIfPaused = function () {
    if (Date.now() - lastTouchAt < 6000) return;      // lo pausó alguien desde la pantalla
    var v = mainVideo();
    if (v && v.paused && !v.ended && (v.currentSrc || v.src || v.srcObject)) window.__avMediaAction('play');
  };

  var lastMediaKey = '';
  var lastMediaForced = 0;
  function reportMedia(force) {
    var bridge = window.AutoVideoMedia;
    if (!bridge || !bridge.update) return;
    var v = mainVideo();
    var yt = ytPlayer();
    var data = null;
    try { data = yt && yt.getVideoData ? yt.getVideoData() : null; } catch (e) { data = null; }
    var md = navigator.mediaSession ? navigator.mediaSession.metadata : null;
    var title = (md && md.title) || (data && data.title) || '';
    var artist = (md && md.artist) || (data && data.author) || '';
    var art = '';
    if (md && md.artwork && md.artwork.length) art = md.artwork[md.artwork.length - 1].src || '';
    if (!art && data && data.video_id) art = 'https://i.ytimg.com/vi/' + data.video_id + '/hqdefault.jpg';
    if (!title && location.pathname.indexOf('/watch') === 0) {
      title = (document.title || '').replace(/\s*-\s*YouTube\s*$/, '');
    }

    // Tiempo y estado: del elemento de video si ya cargó; si no, del reproductor de YouTube.
    var duration = 0, position = 0, playing = false, buffering = false;
    if (v && v.readyState > 0) {
      duration = isFinite(v.duration) ? v.duration : 0;
      position = v.currentTime;
      playing = !v.paused && !v.ended;
      buffering = playing && v.readyState < 3;
    } else if (yt) {
      try {
        var st = yt.getPlayerState();       // 1 reproduciendo, 2 pausa, 3 cargando, 0 terminado
        duration = yt.getDuration() || 0;
        position = yt.getCurrentTime() || 0;
        playing = st === 1 || st === 3;
        buffering = st === 3;
      } catch (e) { /* reproductor no listo */ }
    }
    var hasMedia = !!(v && (v.currentSrc || v.src || v.srcObject)) || duration > 0;
    var state = {
      has: hasMedia && !!title,
      t: title,
      a: artist,
      art: art,
      d: Math.round(duration * 1000),
      p: Math.round(position * 1000),
      playing: playing,
      buf: buffering,
      r: v ? v.playbackRate : 1
    };
    var key = JSON.stringify([state.has, state.t, state.a, state.art, state.d, state.playing, state.buf, state.r]);
    var now = Date.now();
    // Sin cambios no se manda nada; cada 20 s se reenvía la posición por si el auto se desfasó.
    if (!force && key === lastMediaKey && now - lastMediaForced < 20000) return;
    lastMediaKey = key;
    lastMediaForced = now;
    try { bridge.update(JSON.stringify(state)); } catch (e) { /* la app no está */ }
  }

  var mediaPending = false;
  function scheduleMedia(force) {
    if (force) { reportMedia(true); return; }
    if (mediaPending) return;
    mediaPending = true;
    setTimeout(function () { mediaPending = false; reportMedia(false); }, 300);
  }

  ['play', 'playing', 'pause', 'waiting', 'seeked', 'ratechange', 'durationchange',
    'loadedmetadata', 'ended', 'emptied'].forEach(function (ev) {
    document.addEventListener(ev, function (e) {
      if (!e.target || e.target.tagName !== 'VIDEO') return;
      scheduleMedia(ev === 'seeked' || ev === 'playing' || ev === 'pause' || ev === 'ratechange');
    }, true);
  });

  function start() {
    safely(updateMode, hideOpenApp, measureRight);
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    setInterval(function () { safely(updateMode, measureRight, updateUpcoming, watchPlayerSize); }, 1000);
    setInterval(function () { reportMedia(false); }, 2000);
  }

  if (document.documentElement) {
    start();
  } else {
    document.addEventListener('DOMContentLoaded', start);
  }
})();
