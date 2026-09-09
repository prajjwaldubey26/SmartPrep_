const Auth = {
  getUser() {
    const raw = localStorage.getItem("smartprep_user");
    return raw ? JSON.parse(raw) : null;
  },

  getToken() {
    return localStorage.getItem("smartprep_token");
  },

  requireAuth(redirectTo = "login.html") {
    if (!this.getToken()) {
      window.location.href = redirectTo;
      return false;
    }
    return true;
  },

  saveSession(payload) {
    localStorage.setItem("smartprep_token", payload.token);
    localStorage.setItem(
      "smartprep_user",
      JSON.stringify({
        id: payload.userId,
        name: payload.name,
        email: payload.email,
        targetRole: payload.targetRole,
      })
    );
  },

  logout() {
    localStorage.removeItem("smartprep_token");
    localStorage.removeItem("smartprep_user");
    window.location.href = "login.html";
  },

  async login(email, password) {
    const data = await Api.post("/auth/login", { email, password });
    this.saveSession(data);
    return data;
  },

  async register(body) {
    const data = await Api.post("/auth/register", body);
    this.saveSession(data);
    return data;
  },
};

document.addEventListener("DOMContentLoaded", () => {
  const logoutBtn = document.getElementById("logoutBtn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", () => Auth.logout());
  }
});
