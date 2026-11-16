// OldPhoneClaude page tools.
//
// The ONLY code this app ever runs inside the page. Three small helpers, each run on demand:
//   insertText  - dictation helper: types recognised speech into the message box
//   lastReply   - read-aloud helper: returns the text to speak (selection first)
//   probe       - blank-page check: returns counts only, never page content
//
// FRAGILE BY DESIGN: these rely on how the official page happens to be built today and can
// stop working whenever the site changes. They never hide or restyle anything, never read
// cookies or tokens, and nothing they return leaves the device. Both helpers that touch
// text can be switched off in the owner menu.
var __opcTools = (function () {
  function visible(el) {
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  function findInput() {
    var a = document.activeElement;
    if (a && (a.isContentEditable || a.tagName === 'TEXTAREA')) return a;
    var list = document.querySelectorAll('[contenteditable="true"], textarea');
    for (var i = list.length - 1; i >= 0; i--) {
      if (visible(list[i])) return list[i];
    }
    return null;
  }

  function insertText(text) {
    var el = findInput();
    if (!el) return 'no-field';
    el.focus();
    var editable = el.isContentEditable;
    var existing = editable ? (el.innerText || '') : (el.value || '');
    if (existing.replace(/\s+$/, '').length > 0 && !/\s$/.test(existing)) text = ' ' + text;
    var ok = false;
    try {
      if (editable) {
        var sel = window.getSelection();
        var range = document.createRange();
        range.selectNodeContents(el);
        range.collapse(false);
        sel.removeAllRanges();
        sel.addRange(range);
      } else {
        el.selectionStart = el.selectionEnd = el.value.length;
      }
      ok = document.execCommand('insertText', false, text);
    } catch (e) {
      ok = false;
    }
    if (!ok && !editable) {
      try {
        var setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
        setter.call(el, el.value + text);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        ok = true;
      } catch (e2) {
        ok = false;
      }
    }
    return ok ? 'ok' : 'failed';
  }

  function lastReply() {
    var picked = String(window.getSelection ? window.getSelection() : '').trim();
    if (picked) return picked.slice(0, 6000);
    var selectors = ['[data-is-streaming]', '.font-claude-response', '.font-claude-message',
      '[data-testid="assistant-message"]'];
    for (var s = 0; s < selectors.length; s++) {
      var nodes = document.querySelectorAll(selectors[s]);
      for (var i = nodes.length - 1; i >= 0; i--) {
        if (visible(nodes[i])) {
          var t = (nodes[i].innerText || '').trim();
          if (t) return t.slice(0, 6000);
        }
      }
    }
    return '';
  }

  function probe() {
    var body = document.body;
    var challenge = /^just a moment/i.test(document.title || '') ||
      !!document.querySelector('#challenge-form, #cf-wrapper, #challenge-error-text, iframe[src*="challenges.cloudflare.com"]');
    return JSON.stringify({
      text: body ? (body.innerText || '').trim().length : 0,
      nodes: document.getElementsByTagName('*').length,
      fields: document.querySelectorAll('input, textarea, [contenteditable="true"]').length,
      buttons: document.querySelectorAll('button, [role="button"]').length,
      challenge: challenge ? 1 : 0,
      state: document.readyState
    });
  }

  return { insertText: insertText, lastReply: lastReply, probe: probe };
})();
