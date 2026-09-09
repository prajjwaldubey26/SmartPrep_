const API_BASE =
  (typeof localStorage !== "undefined" && localStorage.getItem("smartprep_api_base")) ||
  (typeof window !== "undefined" && window.SMARTPREP_API_BASE) ||
  "https://smartprep-ai-2s20.onrender.com/api";

const Api = {
  async request(path, options = {}) {
    const headers = {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    };

    const token = localStorage.getItem("smartprep_token");
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    let response;
    try {
      response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers,
      });
    } catch (error) {
      throw new Error(
        `Cannot reach SMARTPREP API at ${API_BASE}. Check that the Render backend is awake and the URL is correct.`
      );
    }

    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = { message: text };
    }

    if (!response.ok) {
      throw new Error((data && (data.message || data.error)) || "Request failed");
    }

    return data;
  },

  get(path) {
    return this.request(path);
  },

  post(path, body) {
    return this.request(path, {
      method: "POST",
      body: JSON.stringify(body),
    });
  },
};
