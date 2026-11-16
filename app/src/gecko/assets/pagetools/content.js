// GeckoView has no "evaluate JavaScript" call, so the app talks to this content script
// instead. It only ever runs the functions defined in lib.js, and only when the app asks.
(function () {
  var port = browser.runtime.connectNative('opc');
  port.onMessage.addListener(function (m) {
    var result = null;
    try {
      var fn = __opcTools[m.fn];
      result = m.arg === null || m.arg === undefined ? fn() : fn(m.arg);
    } catch (e) {
      result = null;
    }
    port.postMessage({ id: m.id, result: result === null ? null : String(result) });
  });
})();
