package com.glownote.mobile.annotation

import com.glownote.mobile.data.HighlightRecord
import com.glownote.mobile.data.ReaderSettings
import com.glownote.mobile.data.glowJson
import kotlinx.serialization.builtins.ListSerializer

object AnnotationScript {
    /**
     * Runs inside the page's main world. The Android bridge only exposes the
     * callbacks needed for selection, local highlights, and browser window
     * handoff.
     */
    val bootstrap: String = """
        (function () {
          var viewport = document.querySelector('meta[name="viewport"]');
          if (!viewport) {
            viewport = document.createElement('meta');
            viewport.name = 'viewport';
            (document.head || document.documentElement).appendChild(viewport);
          }
          viewport.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
          document.documentElement.setAttribute('data-glownote-mobile', 'true');
          var glownoteHost = String(location.hostname || '').toLowerCase();
          if (glownoteHost === 'dg-ai-notes.pages.dev') {
            document.documentElement.setAttribute('data-glownote-site', 'dg-ai-notes');
          } else {
            document.documentElement.removeAttribute('data-glownote-site');
          }

          if (window.__glownoteInstalled) return;
          window.__glownoteInstalled = true;
          window.__glownoteRecords = [];

          function normalizeReaderBackground(name) {
            var value = String(name || 'warm');
            if (value === 'almond') return 'white';
            if (value === 'gray') return 'dark';
            return ['white', 'warm', 'green', 'dark', 'blue'].indexOf(value) >= 0 ? value : 'warm';
          }

          function readerPalette(name) {
            var palettes = {
              white: {
                background: '#ffffff',
                text: '#292929',
                border: '#dedede',
                quote: '#f5f5f5',
                code: '#f1f1f1',
                link: '#2b6f88'
              },
              warm: {
                background: '#fff8ed',
                text: '#2f302f',
                border: '#e9ddca',
                quote: '#fff2d7',
                code: '#f2ede4',
                link: '#2b6f88'
              },
              green: {
                background: '#eef5ec',
                text: '#29352d',
                border: '#d6e4d4',
                quote: '#e2efe1',
                code: '#e5eee4',
                link: '#35705a'
              },
              blue: {
                background: '#edf4f7',
                text: '#29343a',
                border: '#d6e2e7',
                quote: '#e4eff3',
                code: '#e4edf0',
                link: '#326d8a'
              },
              dark: {
                background: '#2f3133',
                text: '#ffffff',
                border: '#55595c',
                quote: '#3b3e40',
                code: '#3b3e40',
                link: '#a8d8ff'
              }
            };
            return palettes[normalizeReaderBackground(name)] || palettes.warm;
          }

          window.__glownoteSyncReaderSettingsMenu = function () {
            var menu = document.getElementById('glownote-reader-settings-menu');
            if (!menu) return;
            var settings = window.__glownoteReaderSettings || {};
            var fontInput = menu.querySelector('input[data-glownote-reader-control="fontSizeSp"]');
            var lineInput = menu.querySelector('input[data-glownote-reader-control="lineSpacing"]');
            var pageInput = menu.querySelector('input[data-glownote-reader-control="pageSpacingDp"]');
            var backgroundInput = menu.querySelector('[data-glownote-reader-control="background"]');
            var fontOutput = menu.querySelector('[data-glownote-reader-output="fontSizeSp"]');
            var lineOutput = menu.querySelector('[data-glownote-reader-output="lineSpacing"]');
            var pageOutput = menu.querySelector('[data-glownote-reader-output="pageSpacingDp"]');
            if (fontInput && isFinite(Number(settings.fontSizeSp))) fontInput.value = String(Math.round(Number(settings.fontSizeSp)));
            if (lineInput && isFinite(Number(settings.lineSpacing))) lineInput.value = String(Number(settings.lineSpacing));
            if (pageInput && isFinite(Number(settings.pageSpacingDp))) pageInput.value = String(Math.round(Number(settings.pageSpacingDp)));
            if (backgroundInput) {
              var background = normalizeReaderBackground(settings.background);
              var backgroundButtons = backgroundInput.querySelectorAll('button[data-glownote-reader-background]');
              for (var backgroundIndex = 0; backgroundIndex < backgroundButtons.length; backgroundIndex += 1) {
                var backgroundButton = backgroundButtons[backgroundIndex];
                var selected = backgroundButton.getAttribute('data-glownote-reader-background') === background;
                backgroundButton.setAttribute('aria-pressed', selected ? 'true' : 'false');
                backgroundButton.setAttribute('data-selected', selected ? 'true' : 'false');
              }
            }
            if (fontOutput) fontOutput.textContent = String(Math.round(Number(settings.fontSizeSp) || 18) + ' sp');
            if (lineOutput) lineOutput.textContent = (Number(settings.lineSpacing) || 1.8).toFixed(1) + ' 倍';
            if (pageOutput) pageOutput.textContent = String(Math.round(Number(settings.pageSpacingDp) || 20) + ' dp');
          };

          window.__glownoteApplyReaderSettings = function (settings) {
            var next = settings || {};
            var fontSize = Number(next.fontSizeSp);
            var lineSpacing = Number(next.lineSpacing);
            var pageSpacing = Number(next.pageSpacingDp);
            var background = normalizeReaderBackground(next.background);
            if (!isFinite(fontSize)) fontSize = 18;
            if (!isFinite(lineSpacing)) lineSpacing = 1.8;
            if (!isFinite(pageSpacing)) pageSpacing = 20;
            fontSize = Math.max(14, Math.min(28, fontSize));
            lineSpacing = Math.max(1.4, Math.min(2.4, lineSpacing));
            pageSpacing = Math.max(12, Math.min(48, pageSpacing));
            var palette = readerPalette(background);
            window.__glownoteReaderSettings = {
              fontSizeSp: fontSize,
              lineSpacing: lineSpacing,
              pageSpacingDp: pageSpacing,
              background: background
            };
            var targets = [document.documentElement, document.body, document.getElementById('glownote-reader-root')];
            for (var index = 0; index < targets.length; index += 1) {
              var target = targets[index];
              if (!target || !target.style) continue;
              target.style.setProperty('--glownote-reader-font-size', fontSize + 'px', 'important');
              target.style.setProperty('--glownote-reader-line-height', String(lineSpacing), 'important');
              target.style.setProperty('--glownote-reader-page-spacing', pageSpacing + 'px', 'important');
              target.style.setProperty('--glownote-reader-bg', palette.background, 'important');
              target.style.setProperty('--glownote-reader-text', palette.text, 'important');
              target.style.setProperty('--glownote-reader-border', palette.border, 'important');
              target.style.setProperty('--glownote-reader-quote', palette.quote, 'important');
              target.style.setProperty('--glownote-reader-code', palette.code, 'important');
              target.style.setProperty('--glownote-reader-link', palette.link, 'important');
            }
            if (window.__glownoteSyncReaderSettingsMenu) {
              window.__glownoteSyncReaderSettingsMenu();
            }
          };
          window.__glownoteApplyReaderSettings({
            fontSizeSp: 18,
            lineSpacing: 1.8,
            pageSpacingDp: 20,
            background: 'warm'
          });

          var style = document.getElementById('glownote-mobile-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'glownote-mobile-style';
            style.textContent = [
              'mark[data-glownote-id]{border-radius:3px;cursor:pointer;box-decoration-break:clone;-webkit-box-decoration-break:clone;}',
              'mark[data-glownote-id].glownote-jump-target{outline:3px solid #1e6fa8!important;outline-offset:3px;}',
              'html[data-glownote-mobile]{min-width:0!important;max-width:100%!important;overflow-x:hidden!important;}',
              'html[data-glownote-mobile] body{min-width:0!important;max-width:100%!important;}',
              'html[data-glownote-mobile] img,html[data-glownote-mobile] video,html[data-glownote-mobile] canvas,html[data-glownote-mobile] iframe{max-width:100%!important;height:auto;}',
              'html[data-glownote-mobile] pre,html[data-glownote-mobile] table{max-width:100%!important;overflow-x:auto!important;}',
              'html[data-glownote-mobile] h1,html[data-glownote-mobile] h2,html[data-glownote-mobile] h3{max-width:100%!important;overflow-wrap:anywhere!important;word-break:break-word!important;}',
              'html[data-glownote-mobile] p,html[data-glownote-mobile] li{overflow-wrap:anywhere;}',
              'html[data-glownote-site="dg-ai-notes"] #progress-rail{display:none!important;}',
              'html[data-glownote-site="dg-ai-notes"] body,html[data-glownote-site="dg-ai-notes"] .topbar{width:100%!important;min-width:0!important;max-width:100%!important;}',
              'html[data-glownote-site="dg-ai-notes"] .page-grid{display:block!important;width:100%!important;min-width:0!important;max-width:100%!important;grid-template-columns:none!important;column-gap:0!important;margin:0!important;}',
              'html[data-glownote-site="dg-ai-notes"] .toc-left,html[data-glownote-site="dg-ai-notes"] .outline-right{display:none!important;}',
              'html[data-glownote-site="dg-ai-notes"] .module-main,html[data-glownote-site="dg-ai-notes"] .prose,html[data-glownote-site="dg-ai-notes"] #module-content{width:100%!important;min-width:0!important;max-width:100%!important;margin-inline:0!important;}',
              'html[data-glownote-site="dg-ai-notes"] .prose h1,html[data-glownote-site="dg-ai-notes"] .prose h2,html[data-glownote-site="dg-ai-notes"] .prose h3,html[data-glownote-site="dg-ai-notes"] .prose p,html[data-glownote-site="dg-ai-notes"] .prose li{overflow-wrap:anywhere!important;word-break:break-word!important;}',
              'html[data-glownote-site="dg-ai-notes"] .topbar .left-cluster{min-width:0!important;max-width:48%!important;overflow:hidden!important;}',
              'html[data-glownote-site="dg-ai-notes"] .topbar .brand{min-width:0!important;overflow:hidden!important;text-overflow:ellipsis!important;}',
              'html[data-glownote-site="dg-ai-notes"] body.toc-open .toc-left{display:block!important;position:fixed!important;top:var(--topbar-h)!important;left:0!important;bottom:0!important;width:80%!important;max-width:320px!important;min-width:0!important;z-index:100!important;padding:var(--space-4)!important;box-shadow:var(--shadow-3)!important;overflow-y:auto!important;}',
              'html[data-glownote-site="dg-ai-notes"] body.toc-open .toc-left .toc{position:static!important;top:auto!important;max-height:none!important;overflow:visible!important;}',
              'html[data-glownote-site="dg-ai-notes"] body.toc-open .toc-left .toc-list{display:block!important;visibility:visible!important;}',
            ].join('');
            (document.head || document.documentElement).appendChild(style);
          }

          // Google renders its search categories as a horizontally scrollable
          // row, but some WebView builds let the flex items shrink to zero
          // after the mobile-width rules above are applied. That makes labels
          // such as Images/Shopping/Videos visually run into each other. Find
          // the compact category row by its visible labels and keep each item
          // at its content width.
          window.__glownoteFixSearchNavigation = function () {
            var labels = ['图片', '购物', '视频', '新闻', '短视频', '地图', '图书', 'Images', 'Shopping', 'Videos', 'News', 'Maps', 'Books'];
            var candidates = document.querySelectorAll('nav,[role="navigation"],[role="tablist"],div');
            var best = null;
            var bestScore = -1;
            for (var index = 0; index < candidates.length; index += 1) {
              var candidate = candidates[index];
              var rect = candidate.getBoundingClientRect();
              if (!rect.width || !rect.height || rect.height > 140) continue;
              var text = String(candidate.innerText || '').replace(/\s+/g, '');
              if (!text || text.length > 180) continue;
              var hits = 0;
              for (var labelIndex = 0; labelIndex < labels.length; labelIndex += 1) {
                if (text.indexOf(labels[labelIndex]) >= 0) hits += 1;
              }
              if (hits < 3) continue;
              var score = hits * 1000 - text.length * 2 - rect.height;
              if (score > bestScore) {
                best = candidate;
                bestScore = score;
              }
            }
            if (!best) return;
            best.setAttribute('data-glownote-search-nav', 'true');
            best.style.setProperty('display', 'flex', 'important');
            best.style.setProperty('flex-wrap', 'nowrap', 'important');
            best.style.setProperty('align-items', 'center', 'important');
            best.style.setProperty('justify-content', 'flex-start', 'important');
            best.style.setProperty('min-width', '0', 'important');
            best.style.setProperty('max-width', '100%', 'important');
            best.style.setProperty('overflow-x', 'auto', 'important');
            best.style.setProperty('overflow-y', 'hidden', 'important');
            best.style.setProperty('white-space', 'nowrap', 'important');
            best.style.setProperty('column-gap', '10px', 'important');
            var children = best.children;
            for (var childIndex = 0; childIndex < children.length; childIndex += 1) {
              var child = children[childIndex];
              child.style.setProperty('flex', '0 0 auto', 'important');
              child.style.setProperty('min-width', 'max-content', 'important');
              child.style.setProperty('max-width', 'max-content', 'important');
              child.style.setProperty('white-space', 'nowrap', 'important');
            }
            var items = best.querySelectorAll('a,button,[role="tab"]');
            for (var itemIndex = 0; itemIndex < items.length; itemIndex += 1) {
              var item = items[itemIndex];
              item.style.setProperty('flex', '0 0 auto', 'important');
              item.style.setProperty('min-width', 'max-content', 'important');
              item.style.setProperty('max-width', 'max-content', 'important');
              item.style.setProperty('white-space', 'nowrap', 'important');
            }
          };
          window.__glownoteFixSearchNavigation();
          setTimeout(window.__glownoteFixSearchNavigation, 300);
          setTimeout(window.__glownoteFixSearchNavigation, 900);

          function colorOf(name) {
            return {
              yellow: '#f5e2a4',
              red: '#ff8a80',
              blue: '#82b1ff',
              green: '#b9f6ca',
              orange: '#ffd180'
            }[name] || '#f5e2a4';
          }

          function bridge() {
            return window.AndroidWebViewBridge || null;
          }

          var lastEditableFocusTarget = null;
          var editableInteractionTarget = null;
          function editableTarget(node) {
            if (!node) return null;
            if (node.nodeType !== 1) node = node.parentElement;
            while (node && node !== document.documentElement) {
              if (node.matches && node.matches('input,textarea,[contenteditable]')) return node;
              node = node.parentElement;
            }
            return null;
          }

          function notifyEditableFocus(node) {
            var target = editableTarget(node);
            if (!target) return;
            var nativeBridge = bridge();
            if (!nativeBridge || !nativeBridge.onEditableFocus) return;
            // The synthetic touch used to establish WebView's IME connection
            // also produces touchend/click events. Keep this callback on the
            // real focus transition only, otherwise the keyboard is shown
            // again for every synthetic tap while the user edits the field.
            if (target === lastEditableFocusTarget) return;
            lastEditableFocusTarget = target;
            editableInteractionTarget = target;
            // Google can move its focused search field after applying the
            // #sbfbu fragment. Report the control's settled viewport rect so
            // Android can complete the same touch path at the new location.
            setTimeout(function () {
              if (!target || !target.getBoundingClientRect) return;
              var active = editableTarget(document.activeElement);
              if (active !== target) return;
              var rect = target.getBoundingClientRect();
              nativeBridge.onEditableFocus(JSON.stringify({
                left: Number(rect.left || 0),
                top: Number(rect.top || 0),
                width: Number(rect.width || 0),
                height: Number(rect.height || 0)
              }));
            }, 240);
          }

          document.addEventListener('focusin', function (event) {
            notifyEditableFocus(event && event.target);
          }, true);
          var lastHighlightClickId = '';
          var lastHighlightClickAt = 0;
          var lastPageTapAt = 0;
          function notifyHighlightClick(event, target) {
            if (!target) return;
            var id = target.getAttribute('data-glownote-id');
            var now = Date.now();
            if (!id || (id === lastHighlightClickId && now - lastHighlightClickAt < 500)) return;
            lastHighlightClickId = id;
            lastHighlightClickAt = now;
            if (event) {
                event.preventDefault();
                event.stopPropagation();
              if (event.stopImmediatePropagation) event.stopImmediatePropagation();
            }
            var nativeBridge = bridge();
            if (!nativeBridge || !nativeBridge.onHighlightClick) return;
            var rect = target.getBoundingClientRect();
            nativeBridge.onHighlightClick(JSON.stringify({
              id: id,
              selectionTop: rect ? Number(rect.top || 0) : 0,
              selectionBottom: rect ? Number(rect.bottom || 0) : 0
            }));
          }

          function hasLiveTextSelection() {
            var selection = window.getSelection();
            return !!(selection && selection.rangeCount && String(selection).trim().length);
          }

          function notifyPageTap(event) {
            // The browser emits touchend/pointerup after a long-press
            // selection. Treating that release as a page tap immediately
            // clears the selection that the annotation bridge just reported.
            if (hasLiveTextSelection()) return;
            var uiTarget = event && event.target;
            if (uiTarget && uiTarget.nodeType !== 1) uiTarget = uiTarget.parentElement;
            if (uiTarget && uiTarget.closest && uiTarget.closest('[data-glownote-ui]')) return;
            var target = highlightTarget(event);
            if (target) {
              notifyHighlightClick(event, target);
              return;
            }
            // Do not treat an HTML editor as a page tap. Google focuses its
            // results-page search field through a fragment such as
            // #sbfbu=1&pi=claude; dispatching the page-tap callback here would
            // trigger a Compose update while the WebView is establishing the
            // input connection and can make the focused field stop accepting
            // keyboard input.
            var editable = editableTarget(event && event.target);
            if (editable) {
              // focusin owns the one-time native IME handoff. Do not invoke
              // it again for the synthetic touch/click events from that handoff.
              notifyEditableFocus(editable);
              return;
            }
            // A real tap outside an editor starts a new annotation interaction
            // and allows the next editor focus to perform one IME handoff.
            lastEditableFocusTarget = null;
            editableInteractionTarget = null;
            var nativeBridge = bridge();
            if (!nativeBridge || !nativeBridge.onPageTap) return;
            var now = Date.now();
            if (now - lastPageTapAt < 500) return;
            lastPageTapAt = now;
            nativeBridge.onPageTap();
          }

          var lastNewWindowHref = '';
          var lastNewWindowAt = 0;
          function notifyNewWindow(event) {
            var target = event && event.target;
            if (target && target.nodeType !== 1) target = target.parentElement;
            var anchor = target && target.closest ? target.closest('a[target="_blank"]') : null;
            if (!anchor) return false;
            var href = String(anchor.href || anchor.getAttribute('href') || '').trim();
            if (!/^https?:\/\//i.test(href)) return false;
            var now = Date.now();
            if (href === lastNewWindowHref && now - lastNewWindowAt < 800) return true;
            lastNewWindowHref = href;
            lastNewWindowAt = now;
            var nativeBridge = bridge();
            if (!nativeBridge || !nativeBridge.onOpenNewWindow) return false;
            if (event) {
              event.preventDefault();
              event.stopPropagation();
              if (event.stopImmediatePropagation) event.stopImmediatePropagation();
            }
            nativeBridge.onOpenNewWindow(href);
            return true;
          }

          function highlightTargetAtPoint(clientX, clientY) {
            if (typeof clientX !== 'number' || typeof clientY !== 'number') return null;
            var target = document.elementFromPoint(clientX, clientY);
            while (target && target !== document.documentElement) {
              if (target.nodeType === 1 && target.matches && target.matches('mark[data-glownote-id]')) return target;
              target = target.parentElement;
            }
            return null;
          }

          function highlightTarget(event) {
            var target = event && event.target;
            if (target && target.nodeType !== 1) target = target.parentElement;
            var mark = target && target.closest ? target.closest('mark[data-glownote-id]') : null;
            if (mark) return mark;
            if (event && event.changedTouches && event.changedTouches.length) {
              var touch = event.changedTouches[0];
              return highlightTargetAtPoint(touch.clientX, touch.clientY);
            }
            if (event && typeof event.clientX === 'number') {
              return highlightTargetAtPoint(event.clientX, event.clientY);
            }
            return null;
          }

          function eligibleTextNode(node) {
            var parent = node.parentElement;
            if (!parent || !node.nodeValue) return false;
            if (parent.closest('script,style,noscript,textarea,input,select,button,[data-glownote-ui]')) return false;
            return true;
          }

          function annotationRoot() {
            var readerContent = document.getElementById('glownote-reader-content');
            if (readerContent && document.documentElement.getAttribute('data-glownote-reader') === 'true') {
              return readerContent;
            }
            var explicit = document.querySelector(
              '[itemprop="articleBody"],#cnblogs_post_body,#post_detail,#mainContent,.blogpost-body,.postBody,.article-content,.post-content,.entry-content,.markdown-body'
            );
            if (explicit) return explicit;

            // Some sites (including pi.dev) keep the real page content in a
            // plain div while using small footer cards as <article> nodes.
            // Choosing the first generic article can therefore make every
            // saved highlight search the wrong subtree. Prefer the largest
            // meaningful main/article container, then fall back to body.
            var candidates = Array.prototype.slice.call(
              document.querySelectorAll('main,article,[role="main"]')
            );
            var bodyText = String((document.body && (document.body.innerText || document.body.textContent)) || '').trim();
            var bodyLength = bodyText.length;
            var best = null;
            var bestLength = 0;
            for (var candidate of candidates) {
              var candidateText = String(candidate.innerText || candidate.textContent || '').trim();
              // A few home pages use small <article> cards for navigation or
              // footer links while keeping the real content in a plain div.
              // Do not treat those tiny cards as the annotation root.
              var isMeaningful = candidateText.length >= 120 &&
                (bodyLength < 600 || candidateText.length >= bodyLength * 0.12);
              if (isMeaningful && candidateText.length > bestLength) {
                best = candidate;
                bestLength = candidateText.length;
              }
            }
            return best || document.body;
          }

          function textIndex() {
            var walker = document.createTreeWalker(annotationRoot(), NodeFilter.SHOW_TEXT);
            var nodes = [];
            var text = '';
            var node;
            while ((node = walker.nextNode())) {
              if (!eligibleTextNode(node)) continue;
              nodes.push({ node: node, start: text.length, end: text.length + node.nodeValue.length });
              text += node.nodeValue;
            }
            return { nodes: nodes, text: text };
          }

          function offsetForRangeStart(range, info) {
            if (!range || !info) return -1;
            for (var item of info.nodes) {
              if (item.node === range.startContainer) {
                return item.start + range.startOffset;
              }
            }
            return -1;
          }

          function collapseWhitespace(value) {
            var output = '';
            var map = [];
            var space = false;
            for (var index = 0; index < value.length; index += 1) {
              var character = value[index];
              if (/\s/.test(character)) {
                if (!space) {
                  output += ' ';
                  map.push(index);
                }
                space = true;
              } else {
                output += character;
                map.push(index);
                space = false;
              }
            }
            return { text: output, map: map };
          }

          function normalizeForMatch(value) {
            return String(value || '')
              .replace(/\u00a0/g, ' ')
              .replace(/[\r\n\t ]+/g, ' ')
              .trim();
          }

          function boundaryForOffset(index, info, endBoundary) {
            for (var item of info.nodes) {
              if (index >= item.start && index < item.end) {
                return { node: item.node, offset: Math.max(0, Math.min(item.node.nodeValue.length, index - item.start)) };
              }
              if (endBoundary && index === item.end) {
                return { node: item.node, offset: item.node.nodeValue.length };
              }
            }
            var last = info.nodes[info.nodes.length - 1];
            return last ? { node: last.node, offset: last.node.nodeValue.length } : null;
          }

          function rangeForOffsets(start, end, info) {
            if (start < 0 || end <= start) return null;
            var from = boundaryForOffset(start, info, false);
            var to = boundaryForOffset(end, info, true);
            if (!from || !to) return null;
            var range = document.createRange();
            range.setStart(from.node, from.offset);
            range.setEnd(to.node, to.offset);
            return range;
          }

          function findRange(query, preferredOffset) {
            var info = textIndex();
            if (!info.text || !query) return null;
            if (typeof preferredOffset === 'number' && preferredOffset >= 0 &&
                preferredOffset + query.length <= info.text.length) {
              var anchoredText = info.text.slice(preferredOffset, preferredOffset + query.length);
              if (anchoredText === query || normalizeForMatch(anchoredText) === normalizeForMatch(query)) {
                var anchoredRange = rangeForOffsets(preferredOffset, preferredOffset + query.length, info);
                if (anchoredRange) return anchoredRange;
              }
            }
            var start = info.text.indexOf(query);
            var end = start < 0 ? -1 : start + query.length;
            if (start < 0) {
              var folded = collapseWhitespace(info.text);
              var needle = normalizeForMatch(query);
              start = folded.text.indexOf(needle);
              if (start >= 0 && needle) {
                var mappedStart = folded.map[start];
                var mappedEnd = folded.map[start + needle.length - 1];
                start = mappedStart;
                end = mappedEnd + 1;
              }
            }
            return rangeForOffsets(start, end, info);
          }

          function clearMarks() {
            var marks = document.querySelectorAll('mark[data-glownote-id]');
            for (var mark of marks) {
              var parent = mark.parentNode;
              if (!parent) continue;
              while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
              parent.removeChild(mark);
            }
          }

          function createMark(record) {
            var mark = document.createElement('mark');
            mark.setAttribute('data-glownote-id', record.id);
            mark.setAttribute('title', record.note || 'GlowNote highlight');
            mark.style.backgroundColor = colorOf(record.color);
            mark.style.color = '#17202a';
            mark.addEventListener('click', function (event) {
              notifyHighlightClick(event, mark);
            }, true);
            mark.addEventListener('touchend', function (event) {
              notifyHighlightClick(event, mark);
            }, true);
            mark.addEventListener('pointerup', function (event) {
              notifyHighlightClick(event, mark);
            }, true);
            return mark;
          }

          function wrapRange(range, record) {
            if (!range || !record || !record.id) return;
            var nodes = [];
            var walker = document.createTreeWalker(annotationRoot(), NodeFilter.SHOW_TEXT);
            var node;
            while ((node = walker.nextNode())) {
              if (!eligibleTextNode(node)) continue;
              try {
                if (range.intersectsNode(node)) nodes.push(node);
              } catch (ignored) {}
            }

            for (var textNode of nodes) {
              if (!textNode.parentNode || !textNode.nodeValue) continue;
              var startOffset = textNode === range.startContainer ? range.startOffset : 0;
              var endOffset = textNode === range.endContainer ? range.endOffset : textNode.nodeValue.length;
              startOffset = Math.max(0, Math.min(startOffset, textNode.nodeValue.length));
              endOffset = Math.max(startOffset, Math.min(endOffset, textNode.nodeValue.length));
              if (endOffset <= startOffset) continue;

              var selectedNode = textNode;
              if (startOffset > 0) selectedNode = textNode.splitText(startOffset);
              if (endOffset - startOffset < selectedNode.nodeValue.length) {
                selectedNode.splitText(endOffset - startOffset);
              }
              var mark = createMark(record);
              selectedNode.parentNode.insertBefore(mark, selectedNode);
              mark.appendChild(selectedNode);
            }
          }

          window.__glownoteApply = function (records) {
            window.__glownoteRecords = Array.isArray(records) ? records : [];
            clearMarks();
            for (var record of window.__glownoteRecords) {
              if (!record || !record.selectedText) continue;
              var preferredOffset = record.anchor && Number(record.anchor.textOffset);
              wrapRange(
                findRange(record.selectedText, isFinite(preferredOffset) ? preferredOffset : -1),
                record
              );
            }
          };

          window.__glownoteScrollToHighlight = function (id) {
            var marks = document.querySelectorAll('mark[data-glownote-id]');
            var target = null;
            for (var index = 0; index < marks.length; index += 1) {
              if (marks[index].getAttribute('data-glownote-id') === String(id)) {
                target = marks[index];
                break;
              }
            }
            if (!target) return false;
            target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
            target.classList.add('glownote-jump-target');
            setTimeout(function () {
              if (target) target.classList.remove('glownote-jump-target');
            }, 1400);
            return true;
          };

          window.__glownoteApplyAndScroll = function (records, id) {
            window.__glownoteApply(records);
            setTimeout(function () {
              window.__glownoteScrollToHighlight(id);
            }, 100);
          };

          window.__glownoteClearSelection = function () {
            var selection = window.getSelection();
            if (selection) selection.removeAllRanges();
          };

          var selectionTimer = null;
          document.addEventListener('selectionchange', function () {
            clearTimeout(selectionTimer);
            selectionTimer = setTimeout(function () {
              var selection = window.getSelection();
              if (!selection || !selection.rangeCount) return;
              // Text selection inside an HTML editor belongs to the page's
              // native editing flow, not GlowNote annotation. Google keeps a
              // live selection while its suggestions and keyboard are laid out.
              if (editableInteractionTarget || editableTarget(document.activeElement)) return;
              var text = selection.toString().trim();
              if (text.length < 2) return;
              var range = selection.getRangeAt(0);
              var selectionInfo = textIndex();
              var textOffset = offsetForRangeStart(range, selectionInfo);
              var rect = range.getBoundingClientRect();
              var rects = range.getClientRects();
              if ((!rect || rect.bottom <= 0) && rects.length) rect = rects[rects.length - 1];
              // Input/value selections do not expose a drawable range rect;
              // sending them to Compose creates a toolbar at the top of the
              // WebView and competes with the keyboard.
              if (!rect || rect.width <= 0 || rect.height <= 0) return;
              var node = selection.anchorNode && (selection.anchorNode.nodeType === 1
                ? selection.anchorNode
                : selection.anchorNode.parentElement);
              if (editableTarget(node)) return;
              if (node && node.closest && node.closest('mark[data-glownote-id],[data-glownote-ui]')) return;
              var nativeBridge = bridge();
              if (nativeBridge && nativeBridge.onSelection) {
                nativeBridge.onSelection(JSON.stringify({
                  text: text,
                  url: location.href,
                  title: document.title || '',
                  selectionTop: rect ? Number(rect.top || 0) : 0,
                  selectionBottom: rect ? Number(rect.bottom || 0) : 0,
                  textOffset: textOffset
                }));
              }
            }, 180);
          }, true);

          document.addEventListener('click', function (event) {
            if (notifyNewWindow(event)) return;
            notifyPageTap(event);
          }, true);
          document.addEventListener('touchend', function (event) {
            if (notifyNewWindow(event)) return;
            notifyPageTap(event);
          }, true);
          document.addEventListener('pointerup', function (event) {
            if (notifyNewWindow(event)) return;
            notifyPageTap(event);
          }, true);
        })();
    """.trimIndent()

    val normalizeMobileNavigation: String =
        "window.__glownoteFixSearchNavigation && window.__glownoteFixSearchNavigation();"

    /**
     * Builds a clean, Markdown-like reading surface from the page's main
     * content while keeping the original DOM in memory so the user can leave
     * reading mode without losing the page state.
     */
    val toggleReaderMode: String = """
        (function () {
          function bridge() {
            return window.AndroidWebViewBridge || null;
          }

          function notify(enabled) {
            var nativeBridge = bridge();
            if (nativeBridge && nativeBridge.onReaderModeChanged) {
              nativeBridge.onReaderModeChanged(enabled ? 'true' : 'false');
            }
          }

          function removeNode(node) {
            if (node && node.parentNode) node.parentNode.removeChild(node);
          }

          function readerSettingsFromState() {
            var current = window.__glownoteReaderSettings || {};
            return {
              fontSizeSp: Number(current.fontSizeSp) || 18,
              lineSpacing: Number(current.lineSpacing) || 1.8,
              pageSpacingDp: Number(current.pageSpacingDp) || 20,
              background: String(current.background || 'warm')
            };
          }

          function updateReaderSettingsFromMenu(menu, notifyNative) {
            var next = readerSettingsFromState();
            var fontInput = menu.querySelector('input[data-glownote-reader-control="fontSizeSp"]');
            var lineInput = menu.querySelector('input[data-glownote-reader-control="lineSpacing"]');
            var pageInput = menu.querySelector('input[data-glownote-reader-control="pageSpacingDp"]');
            var backgroundInput = menu.querySelector('[data-glownote-reader-control="background"]');
            if (fontInput) next.fontSizeSp = Number(fontInput.value);
            if (lineInput) next.lineSpacing = Number(lineInput.value);
            if (pageInput) next.pageSpacingDp = Number(pageInput.value);
            if (backgroundInput) {
              var selectedBackground = backgroundInput.querySelector('button[data-glownote-reader-background][aria-pressed="true"]');
              if (selectedBackground) {
                next.background = selectedBackground.getAttribute('data-glownote-reader-background') || next.background;
              }
            }
            if (window.__glownoteApplyReaderSettings) {
              window.__glownoteApplyReaderSettings(next);
            }
            if (notifyNative) {
              var nativeBridge = bridge();
              var saved = window.__glownoteReaderSettings || next;
              if (nativeBridge && nativeBridge.onReaderSettingsChanged) {
                nativeBridge.onReaderSettingsChanged(JSON.stringify(saved));
              }
            }
          }

          function installReaderSettingsMenu(header) {
            var actions = document.createElement('div');
            actions.setAttribute('data-glownote-reader-actions', 'true');
            actions.setAttribute('data-glownote-ui', 'reader-settings');

            var button = document.createElement('button');
            button.type = 'button';
            button.setAttribute('data-glownote-reader-settings-button', 'true');
            button.setAttribute('data-glownote-ui', 'reader-settings');
            button.setAttribute('aria-label', '阅读设置');
            button.setAttribute('aria-expanded', 'false');
            button.textContent = '⋮';

            var menu = document.createElement('div');
            menu.id = 'glownote-reader-settings-menu';
            menu.setAttribute('data-glownote-reader-settings-menu', 'true');
            menu.setAttribute('data-glownote-ui', 'reader-settings');
            menu.hidden = true;
            menu.innerHTML = [
              '<div class="glownote-reader-settings-title">阅读设置</div>',
              '<div class="glownote-reader-setting-row glownote-reader-background-row"><span>背景</span><div class="glownote-reader-background-swatches" data-glownote-reader-control="background" role="radiogroup" aria-label="阅读背景">' +
                '<button type="button" class="glownote-reader-background-dot glownote-reader-background-white" data-glownote-reader-background="white" aria-label="纯白" title="纯白" aria-pressed="false" data-selected="false"></button>' +
                '<button type="button" class="glownote-reader-background-dot glownote-reader-background-warm" data-glownote-reader-background="warm" aria-label="浅黄色" title="浅黄色" aria-pressed="false" data-selected="false"></button>' +
                '<button type="button" class="glownote-reader-background-dot glownote-reader-background-green" data-glownote-reader-background="green" aria-label="浅绿色" title="浅绿色" aria-pressed="false" data-selected="false"></button>' +
                '<button type="button" class="glownote-reader-background-dot glownote-reader-background-dark" data-glownote-reader-background="dark" aria-label="浅黑色" title="浅黑色" aria-pressed="false" data-selected="false"></button>' +
                '<button type="button" class="glownote-reader-background-dot glownote-reader-background-blue" data-glownote-reader-background="blue" aria-label="浅蓝色" title="浅蓝色" aria-pressed="false" data-selected="false"></button>' +
              '</div></div>',
              '<label class="glownote-reader-setting-row"><span>字体大小</span><output data-glownote-reader-output="fontSizeSp"></output><input type="range" data-glownote-reader-control="fontSizeSp" min="14" max="28" step="1"></label>',
              '<label class="glownote-reader-setting-row"><span>行间距</span><output data-glownote-reader-output="lineSpacing"></output><input type="range" data-glownote-reader-control="lineSpacing" min="1.4" max="2.4" step="0.1"></label>',
              '<label class="glownote-reader-setting-row"><span>页间距</span><output data-glownote-reader-output="pageSpacingDp"></output><input type="range" data-glownote-reader-control="pageSpacingDp" min="12" max="48" step="4"></label>'
            ].join('');

            actions.appendChild(button);
            actions.appendChild(menu);
            header.appendChild(actions);

            function closeMenu() {
              menu.hidden = true;
              menu.setAttribute('aria-hidden', 'true');
              button.setAttribute('aria-expanded', 'false');
            }

            button.addEventListener('click', function (event) {
              event.preventDefault();
              event.stopPropagation();
              var nextHidden = !menu.hidden;
              menu.hidden = nextHidden;
              menu.setAttribute('aria-hidden', nextHidden ? 'true' : 'false');
              button.setAttribute('aria-expanded', nextHidden ? 'false' : 'true');
              if (!nextHidden && window.__glownoteSyncReaderSettingsMenu) {
                window.__glownoteSyncReaderSettingsMenu();
              }
            });
            menu.addEventListener('click', function (event) {
              event.stopPropagation();
            });

            var controls = menu.querySelectorAll('input[data-glownote-reader-control]');
            for (var controlIndex = 0; controlIndex < controls.length; controlIndex += 1) {
              controls[controlIndex].addEventListener('input', function () {
                updateReaderSettingsFromMenu(menu, false);
              });
              controls[controlIndex].addEventListener('change', function () {
                updateReaderSettingsFromMenu(menu, true);
              });
            }

            var backgroundButtons = menu.querySelectorAll('button[data-glownote-reader-background]');
            for (var backgroundButtonIndex = 0; backgroundButtonIndex < backgroundButtons.length; backgroundButtonIndex += 1) {
              backgroundButtons[backgroundButtonIndex].addEventListener('click', function (event) {
                event.preventDefault();
                var buttons = menu.querySelectorAll('button[data-glownote-reader-background]');
                for (var index = 0; index < buttons.length; index += 1) {
                  buttons[index].setAttribute('aria-pressed', buttons[index] === event.currentTarget ? 'true' : 'false');
                  buttons[index].setAttribute('data-selected', buttons[index] === event.currentTarget ? 'true' : 'false');
                }
                updateReaderSettingsFromMenu(menu, true);
              });
            }

            var outsideClick = function (event) {
              var target = event && event.target;
              if (target && target.nodeType !== 1) target = target.parentElement;
              if (target && actions.contains(target)) return;
              closeMenu();
            };
            document.addEventListener('click', outsideClick, true);
            window.__glownoteReaderMenuCleanup = function () {
              document.removeEventListener('click', outsideClick, true);
              closeMenu();
              if (actions.parentNode) actions.parentNode.removeChild(actions);
              window.__glownoteReaderMenuCleanup = null;
            };
            if (window.__glownoteSyncReaderSettingsMenu) {
              window.__glownoteSyncReaderSettingsMenu();
            }
          }

          function readableText(node) {
            return String((node && (node.innerText || node.textContent)) || '')
              .replace(/\s+/g, ' ')
              .trim();
          }

          function unwrapMarks(root) {
            var marks = root.querySelectorAll('mark[data-glownote-id]');
            for (var index = 0; index < marks.length; index += 1) {
              var mark = marks[index];
              var parent = mark.parentNode;
              if (!parent) continue;
              while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
              parent.removeChild(mark);
            }
          }

          function pickMainContent() {
            var csdnContent = document.querySelector('#article_content,.article_content');
            if (csdnContent && readableText(csdnContent).length >= 80) {
              var csdnArticle = document.createElement('div');
              var csdnTitle = document.querySelector('#articleContentId,h1.title-article');
              if (csdnTitle && readableText(csdnTitle).length > 0) {
                csdnArticle.appendChild(csdnTitle.cloneNode(true));
              }
              csdnArticle.appendChild(csdnContent.cloneNode(true));
              return csdnArticle;
            }
            var candidates = document.querySelectorAll(
              'article,main,[role="main"],[itemprop="articleBody"],#js_content,.rich_media_content,.rich_media_content_inner,#cnblogs_post_body,#post_detail,#mainContent,.blogpost-body,.postBody,#article_content,.article_content,.article-content,.post-content,.entry-content,.markdown-body'
            );
            var best = document.body;
            var bestScore = Math.min(readableText(document.body).length, 12000);
            for (var index = 0; index < candidates.length; index += 1) {
              var candidate = candidates[index];
              var text = readableText(candidate);
              if (text.length < 120) continue;
              var marker = String(candidate.tagName || '') + ' ' +
                String(candidate.id || '') + ' ' + String(candidate.className || '');
              var score = Math.min(text.length, 12000);
              if (/^(ARTICLE|MAIN)$/i.test(candidate.tagName || '') ||
                  /article|content|post|entry|markdown/i.test(marker)) score += 15000;
              if (candidate.id === 'cnblogs_post_body' || /blogpost-body/i.test(marker)) score += 25000;
              if (/comment|sidebar|related|recommend|footer|header/i.test(marker)) score -= 7000;
              if (score > bestScore) {
                best = candidate;
                bestScore = score;
              }
            }
            return best;
          }

          function cleanClone(clone) {
            // Remove GlowNote marks before sanitizing attributes. The
            // sanitizer removes data-glownote-id, which would otherwise
            // leave a native <mark> behind with the browser's bright-yellow
            // default background in reading mode.
            unwrapMarks(clone);
            var removable = clone.querySelectorAll(
              'script,style,link,noscript,iframe,canvas,svg,form,nav,aside,button,input,select,textarea'
            );
            for (var index = 0; index < removable.length; index += 1) {
              removeNode(removable[index]);
            }

            function hiddenInSource(element) {
              if (element.hasAttribute('hidden')) return true;
              if (String(element.getAttribute('aria-hidden') || '').toLowerCase() === 'true') {
                return true;
              }
              var inlineStyle = String(element.getAttribute('style') || '');
              return /(?:^|;)\s*(?:display\s*:\s*none|visibility\s*:\s*hidden|content-visibility\s*:\s*hidden)/i.test(inlineStyle);
            }

            var junk = /(^|[-_ ]|\b)(ad|ads|advert|advertisement|banner|sponsor|cookie|consent|share|social|comment|related|recommend|newsletter|subscribe|popup|modal|breadcrumb|sidebar|paywall|login)([-_ ]|\b)/i;
            var elements = clone.querySelectorAll('*');
            for (var elementIndex = 0; elementIndex < elements.length; elementIndex += 1) {
              var element = elements[elementIndex];
              if (!element.parentNode) continue;
              if (hiddenInSource(element)) {
                removeNode(element);
                continue;
              }
              var marker = String(element.id || '') + ' ' + String(element.className || '') + ' ' +
                String(element.getAttribute('role') || '') + ' ' +
                String(element.getAttribute('aria-label') || '') + ' ' +
                String(element.getAttribute('data-testid') || '');
              if (junk.test(marker)) {
                removeNode(element);
                continue;
              }
              var attributes = element.attributes;
              for (var attributeIndex = attributes.length - 1; attributeIndex >= 0; attributeIndex -= 1) {
                var attribute = attributes[attributeIndex];
                if (attribute.name !== 'href' && attribute.name !== 'src' &&
                    attribute.name !== 'alt' && attribute.name !== 'title' &&
                    attribute.name !== 'colspan' && attribute.name !== 'rowspan') {
                  element.removeAttribute(attribute.name);
                }
              }
            }
          }

          function enterReaderMode() {
            if (typeof window.__glownoteReaderBackup === 'string') return true;
            var source = pickMainContent();
            if (!source || readableText(source).length < 80) return false;

            window.__glownoteReaderBackup = document.body.innerHTML;
            var clone = source.cloneNode(true);
            cleanClone(clone);

            var readerRoot = document.createElement('main');
            readerRoot.id = 'glownote-reader-root';
            var header = document.createElement('header');
            header.setAttribute('data-glownote-reader-header', 'true');
            readerRoot.appendChild(header);

            var content = document.createElement('article');
            content.id = 'glownote-reader-content';
            content.className = 'glownote-reader-markdown';
            content.innerHTML = clone.innerHTML || clone.textContent || '';
            readerRoot.appendChild(content);

            document.body.innerHTML = '';
            document.body.appendChild(readerRoot);
            document.documentElement.setAttribute('data-glownote-reader', 'true');
            installReaderSettingsMenu(header);

            var readerStyle = document.getElementById('glownote-reader-style');
            if (!readerStyle) {
              readerStyle = document.createElement('style');
              readerStyle.id = 'glownote-reader-style';
              readerStyle.textContent = [
                'html[data-glownote-reader],html[data-glownote-reader] body{background:var(--glownote-reader-bg,#fff8ed)!important;color:var(--glownote-reader-text,#2f302f)!important;margin:0!important;-webkit-user-select:text!important;user-select:text!important;}',
                '#glownote-reader-root{display:block!important;max-width:760px!important;margin:0 auto!important;padding:28px var(--glownote-reader-page-spacing,20px) 96px!important;font-family:Georgia,"Noto Serif SC",serif!important;font-size:var(--glownote-reader-font-size,18px)!important;line-height:var(--glownote-reader-line-height,1.8)!important;background:var(--glownote-reader-bg,#fff8ed)!important;color:var(--glownote-reader-text,#2f302f)!important;}',
                '#glownote-reader-root,#glownote-reader-root *{box-sizing:border-box!important;max-width:100%!important;-webkit-user-select:text!important;user-select:text!important;}',
                '#glownote-reader-root [data-glownote-reader-header]{position:relative!important;padding-right:0!important;}',
                '#glownote-reader-root [data-glownote-reader-actions]{position:fixed!important;top:12px!important;right:12px!important;z-index:20!important;font-family:ui-sans-serif,system-ui,sans-serif!important;}',
                '#glownote-reader-root [data-glownote-ui]{-webkit-user-select:none!important;user-select:none!important;}',
                '#glownote-reader-root [data-glownote-reader-settings-button]{width:40px!important;height:40px!important;margin:0!important;padding:0!important;border:1px solid var(--glownote-reader-border,#e9ddca)!important;border-radius:20px!important;background:var(--glownote-reader-quote,#fff2d7)!important;color:var(--glownote-reader-text,#2f302f)!important;font:700 26px/34px ui-sans-serif,system-ui,sans-serif!important;cursor:pointer!important;}',
                '#glownote-reader-root [data-glownote-reader-settings-menu]{position:absolute!important;top:46px!important;right:0!important;width:280px!important;max-width:calc(100vw - 32px)!important;padding:16px!important;border:1px solid var(--glownote-reader-border,#e9ddca)!important;border-radius:14px!important;background:var(--glownote-reader-bg,#fff8ed)!important;color:var(--glownote-reader-text,#2f302f)!important;box-shadow:0 8px 24px rgba(50,40,25,.18)!important;font:14px/1.4 ui-sans-serif,system-ui,sans-serif!important;}',
                '#glownote-reader-root [data-glownote-reader-settings-menu][hidden]{display:none!important;}',
                '#glownote-reader-root .glownote-reader-settings-title{margin:0 0 12px!important;font-size:16px!important;font-weight:800!important;}',
                '#glownote-reader-root .glownote-reader-setting-row{display:grid!important;grid-template-columns:1fr auto!important;gap:7px 10px!important;margin:0 0 14px!important;color:var(--glownote-reader-text,#2f302f)!important;font:14px/1.4 ui-sans-serif,system-ui,sans-serif!important;}',
                '#glownote-reader-root .glownote-reader-background-row{display:flex!important;align-items:center!important;justify-content:space-between!important;gap:12px!important;}',
                '#glownote-reader-root .glownote-reader-setting-row output{color:var(--glownote-reader-link,#2b6f88)!important;font-weight:700!important;}',
                '#glownote-reader-root .glownote-reader-setting-row input{grid-column:1 / -1!important;width:100%!important;margin:0!important;accent-color:var(--glownote-reader-link,#2b6f88)!important;}',
                '#glownote-reader-root .glownote-reader-background-swatches{display:flex!important;align-items:center!important;gap:9px!important;flex:0 0 auto!important;}',
                '#glownote-reader-root .glownote-reader-background-dot{width:27px!important;height:27px!important;min-width:27px!important;margin:0!important;padding:0!important;border:2px solid transparent!important;border-radius:50%!important;box-sizing:border-box!important;cursor:pointer!important;box-shadow:0 0 0 1px rgba(47,48,47,.18)!important;transition:transform .16s ease,box-shadow .16s ease!important;}',
                '#glownote-reader-root .glownote-reader-background-dot:active{transform:scale(.9)!important;}',
                '#glownote-reader-root .glownote-reader-background-dot[data-selected="true"]{box-shadow:0 0 0 2px var(--glownote-reader-bg,#fff8ed),0 0 0 4px var(--glownote-reader-link,#2b6f88)!important;}',
                '#glownote-reader-root .glownote-reader-background-white{background:#ffffff!important;border-color:#d8d8d8!important;}',
                '#glownote-reader-root .glownote-reader-background-warm{background:#fff8ed!important;}',
                '#glownote-reader-root .glownote-reader-background-green{background:#eef5ec!important;}',
                '#glownote-reader-root .glownote-reader-background-dark{background:#2f3133!important;}',
                '#glownote-reader-root .glownote-reader-background-blue{background:#edf4f7!important;}',
                '#glownote-reader-root h1{margin:0 0 8px!important;font-size:clamp(28px,9vw,54px)!important;line-height:1.25!important;font-weight:800!important;}',
                '#glownote-reader-root h2{margin:30px 0 12px!important;padding-bottom:6px!important;border-bottom:1px solid var(--glownote-reader-border,#e9ddca)!important;font-size:clamp(22px,6vw,40px)!important;line-height:1.35!important;}',
                '#glownote-reader-root h3{margin:24px 0 8px!important;font-size:clamp(19px,5vw,32px)!important;line-height:1.45!important;}',
                '#glownote-reader-content{font-size:var(--glownote-reader-font-size,18px)!important;line-height:var(--glownote-reader-line-height,1.8)!important;}',
                '#glownote-reader-content p,#glownote-reader-content li{margin:0 0 1.1em!important;font-size:var(--glownote-reader-font-size,18px)!important;line-height:var(--glownote-reader-line-height,1.8)!important;}',
                '#glownote-reader-root ul,#glownote-reader-root ol{padding-left:1.4em!important;margin:0 0 1.1em!important;}',
                '#glownote-reader-root blockquote{margin:18px 0!important;padding:8px 16px!important;border-left:4px solid var(--glownote-reader-border,#e9ddca)!important;color:var(--glownote-reader-text,#2f302f)!important;background:var(--glownote-reader-quote,#fff2d7)!important;}',
                '#glownote-reader-root pre{padding:14px!important;overflow-x:auto!important;border-radius:10px!important;background:var(--glownote-reader-code,#f2ede4)!important;color:var(--glownote-reader-text,#2f302f)!important;font:14px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace!important;}',
                '#glownote-reader-root code{padding:2px 5px!important;border-radius:4px!important;background:var(--glownote-reader-code,#f2ede4)!important;color:var(--glownote-reader-text,#2f302f)!important;font:0.88em ui-monospace,SFMono-Regular,Consolas,monospace!important;}',
                '#glownote-reader-root img{display:block!important;width:auto!important;height:auto!important;margin:18px auto!important;}',
                '#glownote-reader-root a{color:var(--glownote-reader-link,#2b6f88)!important;}',
                '#glownote-reader-root [data-glownote-reader-meta]{margin:0 0 28px!important;color:var(--glownote-reader-text,#2f302f)!important;opacity:.68!important;font:13px/1.4 ui-sans-serif,system-ui,sans-serif!important;}',
                '#glownote-reader-root table{display:block!important;overflow-x:auto!important;border-collapse:collapse!important;}',
                '#glownote-reader-root th,#glownote-reader-root td{padding:6px 9px!important;border:1px solid var(--glownote-reader-border,#e9ddca)!important;}',
              ].join('');
              (document.head || document.documentElement).appendChild(readerStyle);
            }
            if (window.__glownoteApplyReaderSettings) {
              window.__glownoteApplyReaderSettings(window.__glownoteReaderSettings || {});
            }
            if (window.__glownoteApply) window.__glownoteApply(window.__glownoteRecords || []);
            return true;
          }

          function exitReaderMode() {
            if (typeof window.__glownoteReaderBackup !== 'string') return false;
            if (window.__glownoteReaderMenuCleanup) {
              window.__glownoteReaderMenuCleanup();
            }
            document.body.innerHTML = window.__glownoteReaderBackup;
            window.__glownoteReaderBackup = null;
            var readerStyle = document.getElementById('glownote-reader-style');
            removeNode(readerStyle);
            document.documentElement.removeAttribute('data-glownote-reader');
            if (window.__glownoteApply) window.__glownoteApply(window.__glownoteRecords || []);
            return false;
          }

          var enabled = document.documentElement.getAttribute('data-glownote-reader') === 'true';
          var nextEnabled = enabled ? exitReaderMode() : enterReaderMode();
          notify(nextEnabled);
        })();
    """.trimIndent()

    fun apply(records: List<HighlightRecord>): String {
        val payload = glowJson.encodeToString(ListSerializer(HighlightRecord.serializer()), records)
        return "window.__glownoteApply && window.__glownoteApply($payload);"
    }

    fun applyAndScroll(records: List<HighlightRecord>, id: String): String {
        val payload = glowJson.encodeToString(ListSerializer(HighlightRecord.serializer()), records)
        val safeId = kotlinx.serialization.json.JsonPrimitive(id).toString()
        return "window.__glownoteApplyAndScroll && window.__glownoteApplyAndScroll($payload,$safeId);"
    }

    fun scrollToHighlight(id: String): String {
        val safeId = kotlinx.serialization.json.JsonPrimitive(id).toString()
        return "window.__glownoteScrollToHighlight && window.__glownoteScrollToHighlight($safeId);"
    }

    fun applyReaderSettings(settings: ReaderSettings): String {
        val payload = glowJson.encodeToString(ReaderSettings.serializer(), settings)
        return "window.__glownoteApplyReaderSettings && window.__glownoteApplyReaderSettings($payload);"
    }

    fun clearSelection(): String =
        "window.__glownoteClearSelection && window.__glownoteClearSelection();"
}
