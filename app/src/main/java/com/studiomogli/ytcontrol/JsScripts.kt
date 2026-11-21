package com.studiomogli.ytcontrol

object JsScripts {

    val DOCUMENT_START_SCRIPT: String = """
        (function() {
            var host = (location && location.hostname ? location.hostname : '').toLowerCase();
            if (host.indexOf('youtube.com') === -1) return;
            if (window.__ytcDocStartInstalled) return;
            window.__ytcDocStartInstalled = true;

            try {
                Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
            } catch (e) {}

            var blockedEvents = ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'freeze'];
            blockedEvents.forEach(function(evtName) {
                window.addEventListener(evtName, function(e) { e.stopImmediatePropagation(); }, true);
                document.addEventListener(evtName, function(e) { e.stopImmediatePropagation(); }, true);
            });

            var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
            function stripAds(obj) {
                if (!obj || typeof obj !== 'object') return obj;
                try {
                    for (var i = 0; i < AD_KEYS.length; i++) {
                        if (AD_KEYS[i] in obj) delete obj[AD_KEYS[i]];
                    }
                    if (obj.playerResponse && typeof obj.playerResponse === 'object') stripAds(obj.playerResponse);
                    if (Array.isArray(obj)) {
                        for (var j = 0; j < obj.length; j++) {
                            if (obj[j] && typeof obj[j] === 'object' && obj[j].playerResponse) stripAds(obj[j].playerResponse);
                        }
                    }
                } catch (e) {}
                return obj;
            }

            function isTargetApiUrl(url) {
                if (!url || typeof url !== 'string') return false;
                return url.indexOf('/youtubei/v1/player') !== -1 || url.indexOf('/youtubei/v1/next') !== -1;
            }

            try {
                var initialPlayerResp = window.ytInitialPlayerResponse ? stripAds(window.ytInitialPlayerResponse) : undefined;
                Object.defineProperty(window, 'ytInitialPlayerResponse', {
                    configurable: true,
                    get: function() { return initialPlayerResp; },
                    set: function(val) { initialPlayerResp = stripAds(val); }
                });
            } catch (e) {}

            if (typeof window.fetch === 'function') {
                var origFetch = window.fetch;
                window.fetch = function(input, init) {
                    var url = '';
                    try { url = typeof input === 'string' ? input : (input && input.url ? input.url : ''); } catch (e) {}
                    var fetchPromise = origFetch.apply(this, arguments);
                    if (!isTargetApiUrl(url)) return fetchPromise;
                    return fetchPromise.then(function(response) {
                        if (!response || !response.ok) return response;
                        return response.clone().json().then(function(data) {
                            stripAds(data);
                            var headers = new Headers(response.headers);
                            headers.delete('content-length');
                            return new Response(JSON.stringify(data), {
                                status: response.status,
                                statusText: response.statusText,
                                headers: headers
                            });
                        }).catch(function() { return response; });
                    });
                };
            }

            if (typeof window.XMLHttpRequest === 'function') {
                var origOpen = XMLHttpRequest.prototype.open;
                var responseTextDesc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
                var responseDesc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response');
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__ytcUrl = typeof url === 'string' ? url : '';
                    return origOpen.apply(this, arguments);
                };
                if (responseTextDesc && responseTextDesc.get) {
                    Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {
                        configurable: true,
                        get: function() {
                            var raw = responseTextDesc.get.call(this);
                            if (this.readyState === 4 && isTargetApiUrl(this.__ytcUrl) && raw) {
                                try {
                                    var parsed = JSON.parse(raw);
                                    stripAds(parsed);
                                    return JSON.stringify(parsed);
                                } catch (e) {}
                            }
                            return raw;
                        }
                    });
                }
                if (responseDesc && responseDesc.get) {
                    Object.defineProperty(XMLHttpRequest.prototype, 'response', {
                        configurable: true,
                        get: function() {
                            var raw = responseDesc.get.call(this);
                            if (this.readyState === 4 && isTargetApiUrl(this.__ytcUrl) && raw) {
                                try {
                                    if (typeof raw === 'string') {
                                        var parsedStr = JSON.parse(raw);
                                        stripAds(parsedStr);
                                        return JSON.stringify(parsedStr);
                                    } else if (typeof raw === 'object') {
                                        return stripAds(raw);
                                    }
                                } catch (e) {}
                            }
                            return raw;
                        }
                    });
                }
            }
        })();
    """.trimIndent()

    val RUNTIME_SCRIPT: String = """
        (function() {
            var host = (location && location.hostname ? location.hostname : '').toLowerCase();
            if (host.indexOf('youtube.com') === -1) return;
            if (window.__ytcRuntimeInstalled) return;
            window.__ytcRuntimeInstalled = true;

            window.__ytcConfig = window.__ytcConfig || { mode: 'BLACKLIST', channels: [] };

            function injectCss() {
                try {
                    if (document.getElementById('ytc-control-css')) return;
                    var style = document.createElement('style');
                    style.id = 'ytc-control-css';
                    style.textContent = [
                        'ytm-promoted-sparkles-web-renderer,',
                        'ytm-ad-slot-renderer,',
                        'ytd-ad-slot-renderer,',
                        'ytd-promoted-sparkles-web-renderer,',
                        'ytm-mealbar-promo-renderer,',
                        'ytm-statement-banner-renderer,',
                        '.ytp-ad-overlay-container,',
                        '.ytp-ad-module,',
                        '.ytp-ad-text-overlay,',
                        '.ytp-ad-image-overlay,',
                        '#masthead-ad,',
                        '.advertisement,',
                        'ytm-reel-shelf-renderer,',
                        'ytd-reel-shelf-renderer,',
                        'ytm-shorts-lockup-view-model,',
                        'reel-shelf-renderer,',
                        'ytm-pivot-bar-item-renderer:has(.pivot-shorts),',
                        'a[href^="/shorts/"] {',
                        '    display: none !important;',
                        '    visibility: hidden !important;',
                        '    height: 0 !important;',
                        '    min-height: 0 !important;',
                        '    pointer-events: none !important;',
                        '}',
                        '.ytc-hidden-card { display: none !important; }',
                        '.ytc-outlier-badge {',
                        '    display: inline-block;',
                        '    background: #10B981;',
                        '    color: #FFFFFF;',
                        '    font-size: 10px;',
                        '    font-weight: 800;',
                        '    padding: 2px 6px;',
                        '    border-radius: 4px;',
                        '    margin: 4px 0 4px 8px;',
                        '    letter-spacing: 0.4px;',
                        '}',
                        '.ytc-xray-bar {',
                        '    display: flex;',
                        '    align-items: center;',
                        '    gap: 8px;',
                        '    background: #181818;',
                        '    border: 1px solid #2E2E2E;',
                        '    border-radius: 8px;',
                        '    padding: 8px 10px;',
                        '    margin: 8px 12px;',
                        '    font-size: 11px;',
                        '    color: #E5E5E5;',
                        '    flex-wrap: wrap;',
                        '}',
                        '.ytc-xray-btn {',
                        '    background: #FF0033;',
                        '    color: #FFFFFF;',
                        '    border: none;',
                        '    border-radius: 6px;',
                        '    padding: 5px 10px;',
                        '    font-size: 11px;',
                        '    font-weight: 700;',
                        '    text-decoration: none;',
                        '}',
                        '.ytc-xray-btn-dark {',
                        '    background: #2A2A2A;',
                        '    color: #FFFFFF;',
                        '    border: 1px solid #444;',
                        '    border-radius: 6px;',
                        '    padding: 5px 10px;',
                        '    font-size: 11px;',
                        '    font-weight: 600;',
                        '    text-decoration: none;',
                        '}'
                    ].join('\n');
                    (document.head || document.documentElement).appendChild(style);
                } catch (e) {}
            }
            injectCss();

            function enforceShortsBlock() {
                try {
                    if (location.hostname.indexOf('studio.youtube.com') !== -1) return;
                    if (location.pathname && location.pathname.indexOf('/shorts') === 0) {
                        location.replace('https://m.youtube.com/');
                        return;
                    }
                    var navItems = document.querySelectorAll('ytm-pivot-bar-item-renderer');
                    for (var i = 0; i < navItems.length; i++) {
                        var txt = (navItems[i].textContent || '').trim().toLowerCase();
                        if (txt === 'shorts' || navItems[i].querySelector('.pivot-shorts')) {
                            navItems[i].style.display = 'none';
                        }
                    }
                } catch (e) {}
            }

            function parseViewCount(text) {
                if (!text) return 0;
                var m = text.toLowerCase().match(/([0-9]+(?:\.[0-9]+)?)\s*([kmb])?\s*views/);
                if (!m) return 0;
                var num = parseFloat(m[1]);
                var suffix = m[2] || '';
                if (suffix === 'k') num *= 1000;
                else if (suffix === 'm') num *= 1000000;
                else if (suffix === 'b') num *= 1000000000;
                return isNaN(num) ? 0 : num;
            }

            function runOutlierDetector() {
                try {
                    var cfg = window.__ytcConfig || { mode: 'BLACKLIST' };
                    var existing = document.querySelectorAll('.ytc-outlier-badge');
                    if (cfg.mode !== 'CREATOR') {
                        for (var r = 0; r < existing.length; r++) existing[r].remove();
                        return;
                    }
                    if (location.hostname.indexOf('studio.youtube.com') !== -1) return;

                    var cards = document.querySelectorAll('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer');
                    var counts = [];
                    var cardViews = [];
                    for (var i = 0; i < cards.length; i++) {
                        var v = parseViewCount(cards[i].textContent || '');
                        cardViews.push(v);
                        if (v > 0) counts.push(v);
                    }
                    if (counts.length < 3) return;
                    counts.sort(function(a, b) { return a - b; });
                    var median = counts[Math.floor(counts.length / 2)];
                    if (median <= 0) return;

                    for (var j = 0; j < cards.length; j++) {
                        var card = cards[j];
                        var vCount = cardViews[j];
                        var ratio = vCount / median;
                        var badge = card.querySelector('.ytc-outlier-badge');
                        if (ratio >= 2.5) {
                            var label = ratio.toFixed(1) + 'x OUTLIER';
                            if (!badge) {
                                badge = document.createElement('span');
                                badge.className = 'ytc-outlier-badge';
                                var target = card.querySelector('.details, ytm-media-item, h3, h4') || card;
                                target.insertBefore(badge, target.firstChild);
                            }
                            if (badge.textContent !== label) badge.textContent = label;
                        } else if (badge) {
                            badge.remove();
                        }
                    }
                } catch (e) {}
            }

            function runCompetitorXRay() {
                try {
                    var cfg = window.__ytcConfig || { mode: 'BLACKLIST' };
                    var oldBar = document.getElementById('ytc-xray-bar');
                    if (cfg.mode !== 'CREATOR' || location.pathname !== '/watch') {
                        if (oldBar) oldBar.remove();
                        return;
                    }
                    var params = new URLSearchParams(location.search);
                    var vid = params.get('v');
                    if (!vid) return;
                    if (oldBar && oldBar.getAttribute('data-vid') === vid) return;
                    if (oldBar) oldBar.remove();

                    var anchor = document.querySelector('ytm-slim-video-metadata-section-renderer, .watch-below-the-player');
                    if (!anchor) return;

                    var bar = document.createElement('div');
                    bar.id = 'ytc-xray-bar';
                    bar.className = 'ytc-xray-bar';
                    bar.setAttribute('data-vid', vid);

                    var label = document.createElement('span');
                    label.style.fontWeight = '700';
                    label.textContent = 'CREATOR X-RAY';
                    bar.appendChild(label);

                    var thumbLink = document.createElement('a');
                    thumbLink.className = 'ytc-xray-btn-dark';
                    thumbLink.textContent = 'HD Thumb';
                    thumbLink.href = 'https://i.ytimg.com/vi/' + vid + '/maxresdefault.jpg';
                    thumbLink.target = '_blank';
                    bar.appendChild(thumbLink);

                    var adaptBtn = document.createElement('button');
                    adaptBtn.className = 'ytc-xray-btn';
                    adaptBtn.textContent = 'Adapt This (AI)';
                    adaptBtn.onclick = function() {
                        if (window.AndroidBridge && window.AndroidBridge.onCreatorActionRequested) {
                            window.AndroidBridge.onCreatorActionRequested('ADAPT_VIDEO', window.__ytcScrapeContext());
                        }
                    };
                    bar.appendChild(adaptBtn);

                    anchor.parentNode.insertBefore(bar, anchor);
                } catch (e) {}
            }

            window.__ytcScrapeContext = function() {
                try {
                    var title = (document.title || '').replace(' - YouTube', '').trim();
                    var url = location.href || '';
                    var snippetParts = [];
                    var cards = document.querySelectorAll('ytm-rich-item-renderer, ytm-video-with-context-renderer, ytcp-video-row, h1, h2, h3');
                    for (var i = 0; i < Math.min(cards.length, 20); i++) {
                        var t = (cards[i].innerText || '').replace(/\s+/g, ' ').trim();
                        if (t.length > 12) snippetParts.push(t.slice(0, 160));
                    }
                    var commentParts = [];
                    var commentNodes = document.querySelectorAll('ytm-comment-renderer .comment-text, .comment-content, #content-text');
                    for (var j = 0; j < Math.min(commentNodes.length, 30); j++) {
                        var ct = (commentNodes[j].innerText || '').replace(/\s+/g, ' ').trim();
                        if (ct.length > 5) commentParts.push('- ' + ct.slice(0, 180));
                    }
                    return JSON.stringify({
                        title: title,
                        url: url,
                        snippets: snippetParts.join('\n'),
                        comments: commentParts.join('\n')
                    });
                } catch (e) {
                    return JSON.stringify({ title: document.title || '', url: location.href || '', snippets: '', comments: '' });
                }
            };

            function normalizeChannel(s) {
                return (s || '').toLowerCase().replace(/^@/, '').trim();
            }

            function matchesChannelList(cardText, cardLinks, list) {
                if (!list || list.length === 0) return false;
                var lowerText = (cardText || '').toLowerCase();
                for (var i = 0; i < list.length; i++) {
                    var target = normalizeChannel(list[i]);
                    if (!target) continue;
                    if (lowerText.indexOf(target) !== -1) return true;
                    for (var j = 0; j < cardLinks.length; j++) {
                        var href = (cardLinks[j].getAttribute('href') || '').toLowerCase();
                        if (href.indexOf(target) !== -1) return true;
                    }
                }
                return false;
            }

            function applyChannelFilter() {
                try {
                    if (location.hostname.indexOf('studio.youtube.com') !== -1) return;
                    var cfg = window.__ytcConfig || { mode: 'BLACKLIST', channels: [] };
                    var mode = cfg.mode || 'BLACKLIST';
                    var list = cfg.channels || [];

                    var path = (location.pathname || '').toLowerCase();
                    var isOnChannelPage = path.indexOf('/@') === 0 || path.indexOf('/channel/') === 0 || path.indexOf('/c/') === 0;

                    var cards = document.querySelectorAll(
                        'ytm-rich-item-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-media-item'
                    );

                    for (var i = 0; i < cards.length; i++) {
                        var card = cards[i];
                        var links = card.querySelectorAll('a[href]');
                        var isShort = false;
                        for (var k = 0; k < links.length; k++) {
                            var h = (links[k].getAttribute('href') || '').toLowerCase();
                            if (h.indexOf('/shorts/') !== -1) { isShort = true; break; }
                        }
                        if (isShort) {
                            card.classList.add('ytc-hidden-card');
                            continue;
                        }

                        if (mode === 'CREATOR' || list.length === 0) {
                            card.classList.remove('ytc-hidden-card');
                            continue;
                        }

                        var metaEl = card.querySelector('.ytm-badge-and-byline-renderer, .subhead, ytm-channel-name, .details');
                        var cardText = metaEl ? metaEl.textContent : card.textContent;
                        var matched = matchesChannelList(cardText, links, list);

                        if (mode === 'BLACKLIST') {
                            if (matched) card.classList.add('ytc-hidden-card');
                            else card.classList.remove('ytc-hidden-card');
                        } else if (mode === 'WHITELIST') {
                            if (isOnChannelPage) {
                                var pageAllowed = false;
                                for (var m = 0; m < list.length; m++) {
                                    var t = normalizeChannel(list[m]);
                                    if (t && path.indexOf(t) !== -1) { pageAllowed = true; break; }
                                }
                                if (pageAllowed) {
                                    card.classList.remove('ytc-hidden-card');
                                    continue;
                                }
                            }
                            if (matched) card.classList.remove('ytc-hidden-card');
                            else card.classList.add('ytc-hidden-card');
                        }
                    }
                } catch (e) {}
            }

            window.__ytcApplyConfig = function(cfgJson) {
                try {
                    window.__ytcConfig = typeof cfgJson === 'string' ? JSON.parse(cfgJson) : cfgJson;
                    applyChannelFilter();
                    runOutlierDetector();
                    runCompetitorXRay();
                } catch (e) {}
            };

            var adMuteActive = false;
            var wasMutedBeforeAd = false;
            function isAdPlaying() {
                try {
                    var player = document.querySelector('.html5-video-player, #movie_player');
                    if (!player) return false;
                    return player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
                } catch (e) { return false; }
            }

            function handleFallbackAd() {
                try {
                    var video = document.querySelector('video');
                    if (isAdPlaying()) {
                        if (video) {
                            if (!adMuteActive) { wasMutedBeforeAd = !!video.muted; adMuteActive = true; }
                            video.muted = true;
                            if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.1) {
                                video.currentTime = video.duration;
                            }
                        }
                        var skipSelectors = ['.ytp-skip-ad-button', '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-ad-skip-button-slot button', '.ytp-ad-overlay-close-button'];
                        for (var i = 0; i < skipSelectors.length; i++) {
                            var btn = document.querySelector(skipSelectors[i]);
                            if (btn && btn.offsetParent !== null) btn.click();
                        }
                    } else if (adMuteActive) {
                        if (video) video.muted = wasMutedBeforeAd;
                        adMuteActive = false;
                    }
                } catch (e) {}
            }

            function startObservers() {
                try {
                    if (!document.body) return;
                    var observer = new MutationObserver(function() {
                        enforceShortsBlock();
                        handleFallbackAd();
                        applyChannelFilter();
                        runOutlierDetector();
                        runCompetitorXRay();
                    });
                    observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
                } catch (e) {}
            }

            if (document.body) startObservers();
            else document.addEventListener('DOMContentLoaded', function() { injectCss(); startObservers(); });

            setInterval(function() {
                enforceShortsBlock();
                handleFallbackAd();
                applyChannelFilter();
                runOutlierDetector();
                runCompetitorXRay();
            }, 800);

            window.__ytcControl = function(action) {
                try {
                    var video = document.querySelector('video');
                    if (!video) return;
                    if (action === 'play') video.play();
                    else if (action === 'pause') video.pause();
                    else if (action === 'toggle') { if (video.paused) video.play(); else video.pause(); }
                } catch (e) {}
            };

            var lastState = '';
            function syncPlaybackState() {
                try {
                    if (!window.AndroidBridge || !window.AndroidBridge.onPlaybackStateChanged) return;
                    if (isAdPlaying()) return;
                    var video = document.querySelector('video');
                    var isPlaying = !!(video && !video.paused && !video.ended && video.readyState > 1);
                    var title = (document.title || 'YT Control').replace(' - YouTube', '').trim();
                    var artist = 'YT Control';
                    if (navigator.mediaSession && navigator.mediaSession.metadata) {
                        if (navigator.mediaSession.metadata.title) title = navigator.mediaSession.metadata.title;
                        if (navigator.mediaSession.metadata.artist) artist = navigator.mediaSession.metadata.artist;
                    }
                    var signature = isPlaying + '|' + title + '|' + artist;
                    if (signature !== lastState) {
                        lastState = signature;
                        window.AndroidBridge.onPlaybackStateChanged(isPlaying, title, artist);
                    }
                } catch (e) {}
            }

            setInterval(syncPlaybackState, 1500);
            document.addEventListener('play', syncPlaybackState, true);
            document.addEventListener('pause', syncPlaybackState, true);
        })();
    """.trimIndent()
}
