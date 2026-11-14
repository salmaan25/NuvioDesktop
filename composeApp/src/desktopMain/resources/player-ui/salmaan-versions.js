// salmaan25 fork: in-player Quality menu and the "Other languages" part of the Audio menu.
//
// Loaded after controls.js and uses its page-level helpers (state, send, openPlayerModal,
// closePlayerModal, modalByName, modalElements, activeModal, buildCheckIcon,
// appendEmptyTrackState, displayLanguageName). The app sends the choices as JSON text in
// state.salmaanVersions (see PlayerScreenRuntimeVersionChoices.kt) and receives
// "salmaanVersions" (find sources), "salmaanQuality" (resolution, e.g. 1080) and
// "salmaanLanguage" (language id). Everything is wrapped so a failure here never breaks the
// player's own controls.
(() => {
  "use strict";
  try {
    if (typeof send !== "function" || typeof openPlayerModal !== "function" || typeof modalByName !== "object") return;

    const SVG_NS = "http://www.w3.org/2000/svg";
    const audioButton = document.querySelector('.action-pill .action[data-command="audio"]');
    const audioModalElement = document.getElementById("audioModal");
    if (!audioButton || !audioModalElement) return;

    // Icon: Material "high quality" (Apache 2.0), drawn like the other filled icons.
    const sprite = document.querySelector("svg symbol") ? document.querySelector("svg symbol").parentNode : null;
    if (sprite && !document.getElementById("icon-salmaan-quality")) {
      const symbol = document.createElementNS(SVG_NS, "symbol");
      symbol.setAttribute("id", "icon-salmaan-quality");
      symbol.setAttribute("viewBox", "0 0 24 24");
      const path = document.createElementNS(SVG_NS, "path");
      path.setAttribute("d", "M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 11H9.5v-2h-2v2H6V9h1.5v2.5h2V9H11v6zm7-1c0 .55-.45 1-1 1h-.75v1.5h-1.5V15H14c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1h3c.55 0 1 .45 1 1v4zm-3.5-.5h2v-3h-2v3z");
      symbol.appendChild(path);
      sprite.appendChild(symbol);
    }

    const style = document.createElement("style");
    style.textContent = `
      .salmaan-other-languages { margin-top: 14px; }
      .salmaan-other-languages .track-list, #salmaanQualityList { max-height: 42vh; overflow-y: auto; }
      .salmaan-versions-hint { opacity: 0.6; font-size: 12px; line-height: 1.35; margin: -4px 4px 8px; }
    `;
    document.head.appendChild(style);

    // Quality button, right after Audio.
    const qualityButton = document.createElement("button");
    qualityButton.type = "button";
    qualityButton.className = "action optional";
    qualityButton.id = "salmaanQualityButton";
    qualityButton.dataset.command = "salmaanQuality";
    const qualityIcon = document.createElementNS(SVG_NS, "svg");
    const qualityUse = document.createElementNS(SVG_NS, "use");
    qualityUse.setAttribute("href", "#icon-salmaan-quality");
    qualityIcon.appendChild(qualityUse);
    const qualityLabel = document.createElement("span");
    qualityLabel.id = "salmaanQualityLabel";
    qualityLabel.textContent = "Quality";
    qualityButton.appendChild(qualityIcon);
    qualityButton.appendChild(qualityLabel);
    qualityButton.hidden = true;
    audioButton.insertAdjacentElement("afterend", qualityButton);

    // Quality menu, laid out like the Audio menu.
    const qualityModal = document.createElement("section");
    qualityModal.className = "modal-layer player-rail-modal";
    qualityModal.id = "salmaanQualityModal";
    qualityModal.setAttribute("aria-label", "Quality");
    qualityModal.hidden = true;
    const qualityPanel = document.createElement("div");
    qualityPanel.className = "track-panel audio-rail-panel";
    const qualityTitle = document.createElement("div");
    qualityTitle.className = "track-header";
    qualityTitle.textContent = "Quality";
    const qualityList = document.createElement("div");
    qualityList.className = "track-list";
    qualityList.id = "salmaanQualityList";
    const qualityHint = document.createElement("div");
    qualityHint.className = "salmaan-versions-hint";
    qualityPanel.appendChild(qualityTitle);
    qualityPanel.appendChild(qualityHint);
    qualityPanel.appendChild(qualityList);
    qualityModal.appendChild(qualityPanel);
    audioModalElement.insertAdjacentElement("afterend", qualityModal);
    modalByName.salmaanQuality = qualityModal;
    if (Array.isArray(modalElements)) modalElements.push(qualityModal);
    qualityModal.addEventListener("click", event => {
      event.stopPropagation();
      if (event.target === qualityModal) closePlayerModal(true);
    });

    // "Other languages" at the bottom of the Audio menu.
    const audioPanel = audioModalElement.querySelector(".track-panel");
    const languagesSection = document.createElement("div");
    languagesSection.className = "salmaan-other-languages";
    languagesSection.id = "salmaanOtherLanguages";
    const languagesTitle = document.createElement("div");
    languagesTitle.className = "track-header";
    languagesTitle.textContent = "Other languages";
    const languagesList = document.createElement("div");
    languagesList.className = "track-list";
    languagesList.id = "salmaanOtherLanguagesList";
    const languagesHint = document.createElement("div");
    languagesHint.className = "salmaan-versions-hint";
    languagesSection.appendChild(languagesTitle);
    languagesSection.appendChild(languagesHint);
    languagesSection.appendChild(languagesList);
    languagesSection.hidden = true;
    if (audioPanel) audioPanel.appendChild(languagesSection);

    let lastVersionsText = null;
    const versions = () => {
      const text = typeof state === "object" && state ? state.salmaanVersions : "";
      if (!text) return null;
      try {
        return JSON.parse(text);
      } catch (_) {
        return null;
      }
    };

    const buildRow = (name, detail, selected, onClick) => {
      const row = document.createElement("button");
      row.type = "button";
      row.className = `track-row audio-track-row${selected ? " selected" : ""}`;
      row.addEventListener("click", event => {
        event.stopPropagation();
        onClick();
      });
      const copy = document.createElement("span");
      copy.className = "audio-track-copy";
      const nameElement = document.createElement("span");
      nameElement.className = "audio-track-name";
      nameElement.textContent = name;
      copy.appendChild(nameElement);
      if (detail) {
        const detailElement = document.createElement("span");
        detailElement.className = "audio-track-language";
        detailElement.textContent = detail;
        copy.appendChild(detailElement);
      }
      row.appendChild(copy);
      if (typeof buildCheckIcon === "function") row.appendChild(buildCheckIcon());
      return row;
    };

    const showEmpty = (list, text) => {
      if (typeof appendEmptyTrackState === "function") {
        appendEmptyTrackState(list, text);
      } else {
        const empty = document.createElement("div");
        empty.textContent = text;
        list.appendChild(empty);
      }
    };

    const languageName = code => {
      if (typeof displayLanguageName === "function") {
        const name = displayLanguageName(code);
        if (name) return name;
      }
      return String(code || "").toUpperCase();
    };

    const renderQuality = () => {
      const data = versions();
      qualityTitle.textContent = (data && data.qualityLabel) || "Quality";
      qualityHint.textContent = (data && data.qualityHint) || "";
      qualityList.textContent = "";
      const rows = (data && Array.isArray(data.qualities)) ? data.qualities : [];
      rows.forEach(option => {
        qualityList.appendChild(buildRow(option.label, option.detail, Boolean(option.current), () => {
          if (!option.current) send("salmaanQuality", Number(option.id) || 0);
          window.setTimeout(closePlayerModal, 120);
        }));
      });
      const hasOtherChoice = rows.some(option => !option.current);
      if (!hasOtherChoice) {
        showEmpty(qualityList, data && data.loading
          ? (data.searchingLabel || "Searching sources…")
          : ((data && data.noOtherQualitiesLabel) || "No other qualities found"));
      }
    };

    const renderLanguages = () => {
      const data = versions();
      languagesSection.hidden = !data;
      if (!data) return;
      languagesTitle.textContent = data.otherLanguagesTitle || "Other languages";
      languagesHint.textContent = data.otherLanguagesHint || "";
      languagesList.textContent = "";
      const rows = Array.isArray(data.languages) ? data.languages : [];
      rows.forEach(option => {
        languagesList.appendChild(buildRow(languageName(option.code), option.detail, false, () => {
          send("salmaanLanguage", Number(option.id));
          window.setTimeout(closePlayerModal, 120);
        }));
      });
      if (rows.length === 0) {
        showEmpty(languagesList, data.loading
          ? (data.searchingLabel || "Searching sources…")
          : (data.noOtherLanguagesLabel || "No other languages found"));
      }
    };

    const renderButton = () => {
      const data = versions();
      qualityButton.hidden = !data;
      if (!data) return;
      qualityLabel.textContent = data.currentQuality || data.qualityLabel || "Quality";
    };

    const onVersionsChanged = () => {
      const text = typeof state === "object" && state ? (state.salmaanVersions || "") : "";
      if (text === lastVersionsText) return;
      lastVersionsText = text;
      renderButton();
      if (activeModal === "salmaanQuality") renderQuality();
      if (activeModal === "audio") renderLanguages();
    };

    qualityButton.addEventListener("click", event => {
      event.stopPropagation();
      if (typeof setFocusedActionButton === "function") setFocusedActionButton(qualityButton, { focus: false });
      if (typeof noteChromeActivity === "function") noteChromeActivity(true);
      renderQuality();
      openPlayerModal("salmaanQuality");
      send("salmaanVersions", 1);
    });

    // However the Audio menu opens (button or the A key), fill in Other languages and find sources.
    new MutationObserver(() => {
      if (!audioModalElement.hidden) {
        renderLanguages();
        send("salmaanVersions", 1);
      }
    }).observe(audioModalElement, { attributes: true, attributeFilter: ["hidden"] });

    const originalPlayerControls = window.playerControls;
    if (typeof originalPlayerControls === "function") {
      window.playerControls = nextState => {
        originalPlayerControls(nextState);
        try {
          onVersionsChanged();
        } catch (error) {
          console.error("salmaan-versions", error);
        }
      };
    }
    onVersionsChanged();
  } catch (error) {
    console.error("salmaan-versions setup failed", error);
  }
})();
