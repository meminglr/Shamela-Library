//
//  Bridge.js
//  FolioReader-Android
//
//  Created by Heberti Almeida on 06/05/15.
//  Copyright (c) 2015 Folio Reader. All rights reserved.
//
//  Cleaned: removed jQuery, jsface, Rangy, readium-cfi, and all dead-code functions
//  that referenced disconnected Java bridges (WebViewPager, FolioPageFragment, SSBridge,
//  LoadingView, EPUBcfi, Highlight). Only the API surface actually called from Kotlin is kept.
//

// ---------------------------------------------------------------------------
// Class manipulation utilities
// ---------------------------------------------------------------------------

function hasClass(ele, cls) {
    return !!ele.className.match(new RegExp('(\\s|^)' + cls + '(\\s|$)'));
}

function addClass(ele, cls) {
    if (!hasClass(ele, cls)) ele.className += " " + cls;
}

function removeClass(ele, cls) {
    if (hasClass(ele, cls)) {
        const reg = new RegExp('(\\s|^)' + cls + '(\\s|$)');
        ele.className = ele.className.replace(reg, ' ');
    }
}

// ---------------------------------------------------------------------------
// Media overlay (called via injected <script> tag from HtmlUtil)
// ---------------------------------------------------------------------------

function setMediaOverlayStyle(style) {
    document.documentElement.classList.remove(
        "mediaOverlayStyle0", "mediaOverlayStyle1", "mediaOverlayStyle2"
    );
    document.documentElement.classList.add(style);
}

// Called from HtmlUtil injected script: setMediaOverlayStyleColors('#C0ED72','#C0ED72')
function setMediaOverlayStyleColors(color, colorHighlight) {
    // Dynamic overlay colour rules are not applied in this build.
}

// ---------------------------------------------------------------------------
// Anchor navigation (used for in-book links)
// ---------------------------------------------------------------------------

function scrollAnchor(id) {
    window.location.hash = id;
}

// ---------------------------------------------------------------------------
// Text utilities
// ---------------------------------------------------------------------------

function getBodyText() {
    return document.body.innerText;
}

function getReadingTime() {
    const text = document.body.innerText;
    const totalWords = text.trim().split(/\s+/g).length;
    return Math.round(totalWords / 3); // 180 wpm → 3 words/sec
}

// ---------------------------------------------------------------------------
// Selection rect — called from CustomWebView via evaluateJavascript()
// Depends on RangeFix (rangefix.js) for accurate RTL bounding rect on WebKit.
// ---------------------------------------------------------------------------

function getSelectionRect(element) {
    let range;
    if (element !== undefined) {
        range = document.createRange();
        range.selectNodeContents(element);
    } else {
        range = window.getSelection().getRangeAt(0);
    }
    const rect = RangeFix.getBoundingClientRect(range);
    return {
        left:   rect.left,
        top:    rect.top,
        right:  rect.right,
        bottom: rect.bottom
    };
}

function clearSelection() {
    window.getSelection().removeAllRanges();
}

// ---------------------------------------------------------------------------
// Text selection popup actions — called from CustomWebView via loadUrl()
// ---------------------------------------------------------------------------

function onTextSelectionItemClicked(id) {
    const selectedText = window.getSelection().toString();
    FolioWebView.onTextSelectionItemClicked(id, selectedText);
}

// "Meaning": the selected word plus the text around it (its paragraph, trimmed),
// so the AI can pick the sense the word has in this passage.
function requestMeaning() {
    const selection = window.getSelection();
    const selectedText = selection.toString();
    let context = "";
    if (selection.rangeCount > 0) {
        let node = selection.getRangeAt(0).commonAncestorContainer;
        if (node.nodeType !== Node.ELEMENT_NODE) node = node.parentElement;
        const block = node && node.closest("p, div, li, blockquote, td, h1, h2, h3, h4, h5, h6");
        const text = ((block || node) ? (block || node).textContent : "").replace(/\s+/g, " ").trim();
        const at = text.indexOf(selectedText.trim());
        context = at < 0 ? text.slice(0, 400)
            : text.slice(Math.max(0, at - 200), at + selectedText.length + 200);
    }
    clearSelection();
    FolioWebView.onMeaningRequested(selectedText, context);
}

// ---------------------------------------------------------------------------
// Tap detection — <html onclick="onClickHtml()"> injected by HtmlUtil
// ---------------------------------------------------------------------------

function onClickHtml() {
    const selection = document.getSelection().toString().trim();
    if (!selection) {
        CustomWebView.isTapped();
    }
}

// ---------------------------------------------------------------------------
// Search result highlighting — called via loadUrl() from FolioActivity
// ---------------------------------------------------------------------------

/**
 * Highlights a search match and returns its vertical position in CSS px (-1 if not found).
 * [before] is the text preceding the match in the result snippet; it picks the right occurrence
 * when the word appears several times on the page. The page is scrolled by the app (the WebView
 * sits inside a native scroll container), so this only reports the position.
 */
function highlightSearchLocator(text, before) {
    try {
        if (!text) return -1;
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
        const nodes = [];
        let full = '';
        let node;
        while ((node = walker.nextNode())) {
            nodes.push({ node: node, start: full.length });
            full += node.textContent;
        }
        let index = -1;
        const context = (before || '').replace(/^[.…\s]+/, '');
        for (let len = Math.min(24, context.length); index < 0 && len >= 4; len -= 4) {
            const tail = context.slice(-len);
            const i = full.indexOf(tail + text);
            if (i >= 0) index = i + tail.length;
        }
        if (index < 0) index = full.indexOf(text);
        if (index < 0) return -1;

        let entry = nodes[0];
        for (const n of nodes) {
            if (n.start <= index) entry = n; else break;
        }
        const offset = index - entry.start;
        const range = document.createRange();
        range.setStart(entry.node, offset);
        range.setEnd(entry.node, Math.min(offset + text.length, entry.node.textContent.length));

        const old = document.querySelectorAll('span.search-highlight');
        old.forEach(function (s) { s.replaceWith(document.createTextNode(s.textContent)); });

        const span = document.createElement('span');
        span.className = 'search-highlight';
        // !important so the reader settings stylesheet (which forces span colors) doesn't hide it.
        // Orange, so a search match stands apart from the user's (yellow) highlights.
        span.style.setProperty('background-color', '#FFB74D', 'important');
        span.style.setProperty('outline', '2px solid #E65100', 'important');
        span.style.setProperty('color', '#1C1B1F', 'important');
        span.style.setProperty('border-radius', '4px', 'important');
        range.surroundContents(span);
        return span.getBoundingClientRect().top + window.scrollY;
    } catch (e) {
        console.error("highlightSearchLocator: " + e);
        return -1;
    }
}

function highlightSelectedText() {
    const selection = window.getSelection();
    if (selection && !selection.isCollapsed) {
        const range = selection.getRangeAt(0);
        const span = document.createElement('span');
        span.style.setProperty('background-color', '#FFE082', 'important');
        span.style.setProperty('color', '#1C1B1F', 'important');
        range.surroundContents(span);
        selection.removeAllRanges();
        span.scrollIntoView({ behavior: 'auto', block: 'center' });
    }
}

// ---------------------------------------------------------------------------
// Selection change detection — notifies Android when user selects text
// ---------------------------------------------------------------------------

function detectSelection() {
    const selection = document.getSelection().toString();
    if (selection) {
        CustomWebView.textSelected(selection);
    }
}

function oneOffDebounce(func, wait) {
    let timeout;
    return function (...args) {
        if (timeout) clearTimeout(timeout);
        timeout = setTimeout(() => {
            timeout = null;
            func(...args);
        }, wait);
    };
}

document.addEventListener("selectionchange", oneOffDebounce(detectSelection, 1000));

// ---------------------------------------------------------------------------
// User highlights (colored marks with optional notes), stored by the app.
// A highlight is found again by its text plus the text just before it, so the right occurrence
// is marked even when the same words appear several times in a chapter.
// ---------------------------------------------------------------------------

/** The text nodes of the body with their offsets in the concatenated text. */
function collectTextNodes() {
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
    const nodes = [];
    let full = '';
    let node;
    while ((node = walker.nextNode())) {
        nodes.push({ node: node, start: full.length });
        full += node.textContent;
    }
    return { nodes: nodes, full: full };
}

/** Offset of [text] in [full], using [before] to pick the right occurrence; -1 if missing. */
function locateText(full, text, before) {
    const context = (before || '').replace(/^[.…\s]+/, '');
    for (let len = Math.min(40, context.length); len >= 4; len -= 4) {
        const tail = context.slice(-len);
        const i = full.indexOf(tail + text);
        if (i >= 0) return i + tail.length;
    }
    return full.indexOf(text);
}

/** Wraps the characters [start, end) of the body text in spans made by [makeSpan]. */
function wrapTextRange(info, start, end, makeSpan) {
    const spans = [];
    // Iterate over a copy: wrapping splits nodes.
    info.nodes.slice().forEach(function (entry) {
        const nodeStart = entry.start;
        const nodeEnd = nodeStart + entry.node.textContent.length;
        if (nodeEnd <= start || nodeStart >= end) return;
        if (!entry.node.textContent.trim()) return;
        const from = Math.max(start, nodeStart) - nodeStart;
        const to = Math.min(end, nodeEnd) - nodeStart;
        const range = document.createRange();
        range.setStart(entry.node, from);
        range.setEnd(entry.node, to);
        const span = makeSpan();
        try { range.surroundContents(span); spans.push(span); } catch (e) { }
    });
    return spans;
}

/** Removes every user highlight span, keeping its text. */
function clearUserHighlights() {
    document.querySelectorAll('span.user-highlight').forEach(function (s) {
        const parent = s.parentNode;
        while (s.firstChild) parent.insertBefore(s.firstChild, s);
        parent.removeChild(s);
        parent.normalize();
    });
}

/** Draws [list] = [{id, text, before, color, note}] on the page (replacing what was drawn). */
function applyUserHighlights(list) {
    try {
        clearUserHighlights();
        (list || []).forEach(function (h) {
            const info = collectTextNodes();
            const index = locateText(info.full, h.text, h.before);
            if (index < 0) return;
            wrapTextRange(info, index, index + h.text.length, function () {
                const span = document.createElement('span');
                span.className = 'user-highlight';
                span.setAttribute('data-hid', h.id);
                span.style.setProperty('background-color', h.color, 'important');
                span.style.setProperty('color', '#1C1B1F', 'important');
                span.style.setProperty('border-radius', '3px', 'important');
                if (h.note) {
                    span.style.setProperty('border-bottom', '2px dotted #5D4037', 'important');
                }
                span.onclick = function (event) {
                    event.stopPropagation();
                    FolioWebView.onHighlightClicked(h.id);
                };
                return span;
            });
        });
    } catch (e) {
        console.error('applyUserHighlights: ' + e);
    }
}

/** Vertical position (CSS px) of highlight [id], or -1. */
function scrollToUserHighlight(id) {
    const span = document.querySelector('span.user-highlight[data-hid="' + id + '"]');
    if (!span) return -1;
    return span.getBoundingClientRect().top + window.scrollY;
}

/** Sends the selected text and the text just before it to the app, to create a highlight. */
function highlightSelection() {
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed) return;
    const range = selection.getRangeAt(0);
    const pre = document.createRange();
    pre.setStart(document.body, 0);
    pre.setEnd(range.startContainer, range.startOffset);
    const before = pre.toString().slice(-40);
    const text = selection.toString();
    selection.removeAllRanges();
    FolioWebView.onHighlightSelection(text, before);
}
