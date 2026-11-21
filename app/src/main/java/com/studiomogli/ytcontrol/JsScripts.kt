package com.studiomogli.ytcontrol

object JsScripts {

    val DOCUMENT_START_SCRIPT: String = """
        (function() {
            var host = (location && location.hostname ? location.hostname : '').toLowerCase();
            if (host.indexOf('youtube.com') === -1) return;
            if (window.__ytcDocStartInstalled) return;
            window.__ytcDocStartInstalled = true;

            // --- 1. Page Visibility API Spoof (Background & screen-off playback) ---
            try {
                Object.defineProperty(document, 'hidden', {
                    get: function() { return false; },
                    configurable: true
                });
                Object.defineProperty(document, 'webkitHidden', {
                    get: function() { return false; },
                    configurable: true
                });
                Object.defineProperty(document, 'visibilityState', {
                    get: function() { return 'visible'; },
                    configurable: true
                });
                Object.defineProperty(document, 'webkitVisibilityState', {
                    get: function() { return 'visible'; },
                    configurable: true
                });
            } catch (e) {}

            var blockedEvents = ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'freeze'];
            blockedEvents.forEach(function(evtName) {
                window.addEventListener(evtName, function(e) {
                    e.stopImmediatePropagation();
                }, true);
                document.addEventListener(evtName, function(e) {
                    e.stopImmediatePropagation();
                }, true);
            });

            // --- 2. Layer 2 Ad Blocker: JSON Response Pruning ---
            var AD_KEYS = [
                'adPlacements',
                'playerAds',
                'adSlots',
                'adBreakHeartbeatParams'
            ];

            function stripAds(obj) {
                if (!obj || typeof obj !== 'object') return obj;
                try {
                    for (var i = 0; i < AD_KEYS.length; i++) {
                        if (AD_KEYS[i] in obj) {
                            delete obj[AD_KEYS[i]];
                        }
                    }
                    if (obj.playerResponse && typeof obj.playerResponse === 'object') {
                        stripAds(obj.playerResponse);
                    }
                    if (Array.isArray(obj)) {
                        for (var j = 0; j < obj.length; j++) {
                            if (obj[j] && typeof obj[j] === 'object' && obj[j].playerResponse) {
                                stripAds(obj[j].playerResponse);
                            }
                        }
                    }
                } catch (e) {}
                return obj;
            }

            function isTargetApiUrl(url) {
                if (!url || typeof url !== 'string') return false;
                return url.indexOf('/youtubei/v1/player') !== -1 ||
                       url.indexOf('/youtubei/v1/next') !== -1;
            }

            try {
                var initialPlayerResp = window.ytInitialPlayerResponse
                    ? stripAds(window.ytInitialPlayerResponse)
                    : undefined;
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
                    try {
                        url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                    } catch (e) {}

                    var fetchPromise = origFetch.apply(this, arguments);
                    if (!isTargetApiUrl(url)) {
                        return fetchPromise;
                    }

                    return fetchPromise.then(function(response) {
                        if (!response || !response.ok) return response;
                        var cloned = response.clone();
                        return cloned.json().then(function(data) {
                            stripAds(data);
                            var headers = new Headers(response.headers);
                            headers.delete('content-length');
                            return new Response(JSON.stringify(data), {
                                status: response.status,
                                statusText: response.statusText,
                                headers: headers
                            });
                        }).catch(function() {
                            return response;
                        });
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

            // --- 1. Cosmetic CSS: Block Ads + Block All Shorts ---
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
                        '.ytc-hidden-card {',
                        '    display: none !important;',
                        '}'
                    ].join('\n');
                    (document.head || document.documentElement).appendChild(style);
                } catch (e) {}
            }
            injectCss();

            // --- 2. Shorts Navigation Guard & Tab Remover ---
            function enforceShortsBlock() {
                try {
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

            // --- 3. Channel Whitelist / Blacklist Filter ---
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
                    var cfg = window.__ytcConfig || { mode: 'BLACKLIST', channels: [] };
                    var mode = cfg.mode || 'BLACKLIST';
                    var list = cfg.channels || [];

                    // On a specific channel page, don't hide its own videos if allowed
                    var path = (location.pathname || '').toLowerCase();
                    var isOnChannelPage = path.indexOf('/@') === 0 || path.indexOf('/channel/') === 0 || path.indexOf('/c/') === 0;

                    var cards = document.querySelectorAll(
                        'ytm-rich-item-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-media-item'
                    );

                    for (var i = 0; i < cards.length; i++) {
                        var card = cards[i];
                        var links = card.querySelectorAll('a[href]');
                        // Also hide any card linking to /shorts/
                        var isShort = false;
                        for (var k = 0; k < links.length; k++) {
                            var h = (links[k].getAttribute('href') || '').toLowerCase();
                            if (h.indexOf('/shorts/') !== -1) {
                                isShort = true;
                                break;
                            }
                        }
                        if (isShort) {
                            card.classList.add('ytc-hidden-card');
                            continue;
                        }

                        if (list.length === 0) {
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
                                // Check if current channel URL is in whitelist
                                var pageAllowed = false;
                                for (var m = 0; m < list.length; m++) {
                                    var t = normalizeChannel(list[m]);
                                    if (t && path.indexOf(t) !== -1) {
                                        pageAllowed = true;
                                        break;
                                    }
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
                } catch (e) {}
            };

            // --- 4. Layer 3 Fallback Ad Skipper ---
            var adMuteActive = false;
            var wasMutedBeforeAd = false;

            function isAdPlaying() {
                try {
                    var player = document.querySelector('.html5-video-player, #movie_player');
                    if (!player) return false;
                    return player.classList.contains('ad-showing') ||
                           player.classList.contains('ad-interrupting');
                } catch (e) {
                    return false;
                }
            }

            function handleFallbackAd() {
                try {
                    var video = document.querySelector('video');
                    if (isAdPlaying()) {
                        if (video) {
                            if (!adMuteActive) {
                                wasMutedBeforeAd = !!video.muted;
                                adMuteActive = true;
                            }
                            video.muted = true;
                            if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.1) {
                                video.currentTime = video.duration;
                            }
                        }
                        var skipSelectors = [
                            '.ytp-skip-ad-button',
                            '.ytp-ad-skip-button',
                            '.ytp-ad-skip-button-modern',
                            '.ytp-ad-skip-button-slot button',
                            '.ytp-ad-overlay-close-button'
                        ];
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

            // --- 5. Observers & Loops ---
            function startObservers() {
                try {
                    if (!document.body) return;
                    var observer = new MutationObserver(function() {
                        enforceShortsBlock();
                        handleFallbackAd();
                        applyChannelFilter();
                    });
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true,
                        attributes: true,
                        attributeFilter: ['class']
                    });
                } catch (e) {}
            }

            if (document.body) {
                startObservers();
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    injectCss();
                    startObservers();
                });
            }

            setInterval(function() {
                enforceShortsBlock();
                handleFallbackAd();
                applyChannelFilter();
            }, 600);

            // --- 6. Remote Control Bridge ---
            window.__ytcControl = function(action) {
                try {
                    var video = document.querySelector('video');
                    if (!video) return;
                    if (action === 'play') video.play();
                    else if (action === 'pause') video.pause();
                    else if (action === 'toggle') {
                        if (video.paused) video.play();
                        else video.pause();
                    }
                } catch (e) {}
            };

            // --- 7. Sync Playback State to Android Foreground Service ---
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
