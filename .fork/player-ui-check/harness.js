(async () => {
  const out = [];
  const ok = (name, cond, extra = "") => out.push(`${cond ? "PASS" : "FAIL"} ${name}${extra ? " — " + extra : ""}`);
  const sent = () => window.__sent.map(m => `${m.type}:${m.value}`);
  const wait = ms => new Promise(r => setTimeout(r, ms));
  try {
    const versions = (extra) => JSON.stringify(Object.assign({
      loading: false, qualityLabel: "Quality", currentQuality: "1080p", qualityHint: "Switches and keeps your place.",
      otherLanguagesTitle: "Other languages", otherLanguagesHint: "Keeps your place.", searchingLabel: "Searching sources…",
      noOtherQualitiesLabel: "No other qualities found", noOtherLanguagesLabel: "No other languages found",
      qualities: [
        { id: 1080, label: "1080p", current: true, detail: "Playing" },
        { id: 2160, label: "4K", current: false, detail: "12.0 GB · 2 sources" },
        { id: 720, label: "720p", current: false, detail: "1.4 GB · cached" },
      ],
      languages: [{ id: 1, code: "ja", detail: "3 sources" }, { id: 2, code: "ko", detail: "1 source" }],
    }, extra || {}));
    const qb = document.getElementById("salmaanQualityButton");
    const audioBtn = document.querySelector('.action-pill .action[data-command="audio"]');
    ok("Quality button is created right after Audio", !!qb && audioBtn && audioBtn.nextElementSibling === qb);
    window.playerControls({ title: "Test", salmaanVersions: "" });
    ok("Quality button hidden without data", qb && qb.hidden === true);
    window.playerControls({ salmaanVersions: versions() });
    ok("Quality button shown with data", qb.hidden === false);
    ok("Quality button label is the current quality", document.getElementById("salmaanQualityLabel").textContent === "1080p");
    window.__sent.length = 0;
    qb.click(); await wait(50);
    const qm = document.getElementById("salmaanQualityModal");
    ok("Clicking Quality opens its menu", qm && !qm.hidden && activeModal === "salmaanQuality", `activeModal=${activeModal}`);
    ok("Opening Quality asks the app for sources", sent().includes("salmaanVersions:1"), sent().join(","));
    const rows = [...document.querySelectorAll("#salmaanQualityList .track-row")];
    ok("Quality menu lists 3 qualities", rows.length === 3, `${rows.length}`);
    ok("Current quality is marked", rows[0] && rows[0].classList.contains("selected") && !rows[1].classList.contains("selected"));
    ok("Quality hint shown", qm.textContent.includes("Switches and keeps your place."));
    window.__sent.length = 0;
    rows[1].click(); await wait(250);
    ok("Choosing 4K sends salmaanQuality:2160", sent().includes("salmaanQuality:2160"), sent().join(","));
    ok("Menu closes after choosing", qm.hidden === true || activeModal !== "salmaanQuality", `activeModal=${activeModal}`);
    window.__sent.length = 0;
    qb.click(); await wait(50);
    [...document.querySelectorAll("#salmaanQualityList .track-row")][0].click(); await wait(250);
    ok("Choosing the current quality sends nothing", !sent().some(s => s.startsWith("salmaanQuality:")), sent().join(","));
    window.playerControls({ salmaanVersions: versions({ loading: true, qualities: [{ id: 1080, label: "1080p", current: true }] }) });
    qb.click(); await wait(50);
    ok("While loading, Quality says Searching sources…", document.getElementById("salmaanQualityList").textContent.includes("Searching sources…"));
    window.playerControls({ salmaanVersions: versions({ loading: false, qualities: [{ id: 1080, label: "1080p", current: true }] }) });
    ok("Open menu updates live to No other qualities found", document.getElementById("salmaanQualityList").textContent.includes("No other qualities found"));
    closePlayerModal(); await wait(300);
    window.playerControls({ salmaanVersions: versions() });
    window.__sent.length = 0;
    audioBtn.click(); await wait(100);
    const am = document.getElementById("audioModal");
    const sec = document.getElementById("salmaanOtherLanguages");
    ok("Audio menu opens", !am.hidden, `activeModal=${activeModal}`);
    ok("Other languages section is inside the Audio menu", !!sec && am.contains(sec) && !sec.hidden);
    ok("Opening Audio asks the app for sources", sent().includes("salmaanVersions:1"), sent().join(","));
    const lrows = [...document.querySelectorAll("#salmaanOtherLanguagesList .track-row")];
    ok("Other languages lists 2 languages", lrows.length === 2, `${lrows.length}`);
    ok("Language names are readable", lrows[0] && /Japanese/i.test(lrows[0].textContent), lrows[0] && lrows[0].textContent.slice(0, 40));
    window.__sent.length = 0;
    lrows[1].click(); await wait(250);
    ok("Choosing Korean sends salmaanLanguage:2", sent().includes("salmaanLanguage:2"), sent().join(","));
    window.playerControls({ salmaanVersions: versions({ loading: true, languages: [] }) });
    audioBtn.click(); await wait(100);
    ok("While loading, languages say Searching sources…", document.getElementById("salmaanOtherLanguagesList").textContent.includes("Searching sources…"));
    closePlayerModal(); await wait(300);
    window.playerControls({ salmaanVersions: "" });
    ok("Quality button hides again when data goes away", qb.hidden === true);
    // Mouse wheel (official fix cherry-picked from Dev): over the video it changes volume, not position.
    window.playerControls({ salmaanVersions: versions(), volumeLevel: 0.5, durationMs: 3600000, positionMs: 600000 });
    window.__sent.length = 0;
    const target = document.getElementById("root") || document.body;
    target.dispatchEvent(new WheelEvent("wheel", { deltaY: -100, bubbles: true, cancelable: true }));
    await wait(100);
    const wheelMsgs = sent();
    ok("Wheel over the video does not seek", !wheelMsgs.some(s => /^(seek|fineSeek|seekTo)/i.test(s)), wheelMsgs.join(","));
  } catch (e) {
    out.push("FAIL harness error — " + (e && e.stack || e));
  }
  const pre = document.createElement("pre"); pre.id = "harnessResults"; pre.textContent = out.join("\n");
  document.body.appendChild(pre);
})();
