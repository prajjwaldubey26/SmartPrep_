/**
 * SMARTPREP Voice Coach
 * Priority: cloud TTS (ElevenLabs Sarah / OpenAI nova) -> best neural browser voice.
 * STT: Web Speech Recognition with interim results for natural dictation.
 */
const VoiceCoach = (() => {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

  let recognition = null;
  let listening = false;
  let cloudPreferred = false;
  let cloudProvider = "browser";
  let preferredVoice = null;
  let currentAudio = null;
  let speakToken = 0;

  const listeners = {
    onListeningChange: null,
    onTranscript: null,
    onPartial: null,
    onSpeakStart: null,
    onSpeakEnd: null,
    onError: null,
  };

  async function init() {
    pickBestVoice();
    if (window.speechSynthesis) {
      window.speechSynthesis.onvoiceschanged = pickBestVoice;
      // Chrome often loads voices async
      window.speechSynthesis.getVoices();
    }

    try {
      const status = await Api.get("/voice/status");
      cloudPreferred = !!status.cloudTts;
      cloudProvider = status.provider || "browser";
    } catch {
      cloudPreferred = false;
      cloudProvider = "browser";
    }

    return {
      sttSupported: !!SpeechRecognition,
      ttsSupported: !!(window.speechSynthesis || cloudPreferred),
      provider: cloudPreferred ? cloudProvider : "browser-neural",
      voiceName: preferredVoice ? preferredVoice.name : "System neural",
    };
  }

  function on(event, handler) {
    if (Object.prototype.hasOwnProperty.call(listeners, event)) {
      listeners[event] = handler;
    }
  }

  function pickBestVoice() {
    if (!window.speechSynthesis) return;
    const voices = window.speechSynthesis.getVoices() || [];
    if (!voices.length) return;

    const score = (v) => {
      const name = `${v.name} ${v.lang}`.toLowerCase();
      let s = 0;
      if (/^en(-|_|$)/i.test(v.lang)) s += 40;
      if (v.lang.toLowerCase().startsWith("en-us")) s += 20;
      if (v.lang.toLowerCase().startsWith("en-gb")) s += 12;
      if (name.includes("natural")) s += 50;
      if (name.includes("neural")) s += 48;
      if (name.includes("premium")) s += 40;
      if (name.includes("enhanced")) s += 30;
      if (name.includes("google")) s += 28;
      if (name.includes("microsoft")) s += 26;
      if (name.includes("samantha")) s += 24;
      if (name.includes("aria")) s += 22;
      if (name.includes("jenny")) s += 22;
      if (name.includes("guy")) s += 10;
      if (name.includes("female") || name.includes("woman")) s += 8;
      if (name.includes("compact") || name.includes("eloquence")) s -= 20;
      if (v.localService === false) s += 6; // often higher-quality cloud-backed OS voices
      return s;
    };

    preferredVoice = [...voices].sort((a, b) => score(b) - score(a))[0] || null;
  }

  function humanizeForSpeech(text) {
    return String(text || "")
      .replace(/[#*_`]+/g, " ")
      .replace(/\s+/g, " ")
      .replace(/([.!?])\s+/g, "$1 ... ")
      .trim();
  }

  async function speak(text, options = {}) {
    const cleaned = humanizeForSpeech(text);
    if (!cleaned) return;

    stopSpeaking();
    const token = ++speakToken;
    listeners.onSpeakStart && listeners.onSpeakStart();

    try {
      if (cloudPreferred) {
        const played = await speakCloud(cleaned, token);
        if (played) return;
      }
      await speakBrowser(cleaned, token, options);
    } catch (error) {
      listeners.onError && listeners.onError(error.message || "Voice playback failed");
      try {
        await speakBrowser(cleaned, token, options);
      } catch (fallbackError) {
        listeners.onError && listeners.onError(fallbackError.message || "Speech unavailable");
      }
    } finally {
      if (token === speakToken) {
        listeners.onSpeakEnd && listeners.onSpeakEnd();
      }
    }
  }

  async function speakCloud(text, token) {
    const tokenHeader = localStorage.getItem("smartprep_token");
    const response = await fetch(`${API_BASE}/voice/speak`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(tokenHeader ? { Authorization: `Bearer ${tokenHeader}` } : {}),
      },
      body: JSON.stringify({ text }),
    });

    if (!response.ok) {
      cloudPreferred = false;
      return false;
    }

    const blob = await response.blob();
    if (token !== speakToken) return true;

    const url = URL.createObjectURL(blob);
    await new Promise((resolve, reject) => {
      const audio = new Audio(url);
      currentAudio = audio;
      audio.onended = () => {
        URL.revokeObjectURL(url);
        currentAudio = null;
        resolve();
      };
      audio.onerror = () => {
        URL.revokeObjectURL(url);
        currentAudio = null;
        reject(new Error("Audio playback failed"));
      };
      audio.play().catch(reject);
    });
    return true;
  }

  function speakBrowser(text, token, options = {}) {
    return new Promise((resolve, reject) => {
      if (!window.speechSynthesis) {
        reject(new Error("Speech synthesis not supported"));
        return;
      }
      pickBestVoice();
      const utter = new SpeechSynthesisUtterance(text);
      if (preferredVoice) utter.voice = preferredVoice;
      utter.rate = options.rate ?? 0.92;
      utter.pitch = options.pitch ?? 1.02;
      utter.volume = 1;
      utter.lang = (preferredVoice && preferredVoice.lang) || "en-US";
      utter.onend = () => resolve();
      utter.onerror = (e) => reject(new Error(e.error || "Speech error"));
      if (token !== speakToken) {
        resolve();
        return;
      }
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utter);
    });
  }

  function stopSpeaking() {
    speakToken += 1;
    if (currentAudio) {
      try {
        currentAudio.pause();
      } catch {
        /* ignore */
      }
      currentAudio = null;
    }
    if (window.speechSynthesis) {
      window.speechSynthesis.cancel();
    }
    listeners.onSpeakEnd && listeners.onSpeakEnd();
  }

  function startListening() {
    if (!SpeechRecognition) {
      listeners.onError && listeners.onError("Voice input needs Chrome or Edge.");
      return false;
    }
    if (listening) return true;

    stopSpeaking();
    recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = "en-US";
    recognition.maxAlternatives = 1;

    let finalBuffer = "";

    recognition.onstart = () => {
      listening = true;
      listeners.onListeningChange && listeners.onListeningChange(true);
    };

    recognition.onresult = (event) => {
      let interim = "";
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const piece = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          finalBuffer = `${finalBuffer} ${piece}`.replace(/\s+/g, " ").trim();
          listeners.onTranscript && listeners.onTranscript(finalBuffer);
        } else {
          interim += piece;
        }
      }
      if (interim) {
        listeners.onPartial && listeners.onPartial(`${finalBuffer} ${interim}`.trim());
      }
    };

    recognition.onerror = (event) => {
      if (event.error === "aborted" || event.error === "no-speech") return;
      listeners.onError && listeners.onError(`Mic error: ${event.error}`);
    };

    recognition.onend = () => {
      listening = false;
      listeners.onListeningChange && listeners.onListeningChange(false);
      recognition = null;
    };

    try {
      recognition.start();
      return true;
    } catch (error) {
      listeners.onError && listeners.onError(error.message || "Could not start microphone");
      return false;
    }
  }

  function stopListening() {
    if (recognition) {
      try {
        recognition.stop();
      } catch {
        /* ignore */
      }
    }
    listening = false;
    listeners.onListeningChange && listeners.onListeningChange(false);
  }

  function isListening() {
    return listening;
  }

  function getProviderLabel() {
    if (cloudPreferred) {
      if (cloudProvider === "elevenlabs") return "ElevenLabs · Sarah";
      if (cloudProvider === "openai") return "OpenAI · Nova";
      return cloudProvider;
    }
    return preferredVoice ? `Neural · ${preferredVoice.name}` : "Browser neural voice";
  }

  return {
    init,
    on,
    speak,
    stopSpeaking,
    startListening,
    stopListening,
    isListening,
    getProviderLabel,
  };
})();
