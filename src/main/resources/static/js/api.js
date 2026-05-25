/**
 * SafeMap — api.js
 * Módulo central de comunicação com o backend Spring Boot.
 * Inclua este script ANTES de login.js, cadastro.js e relatar.js.
 *
 * Em dev local (mvn spring-boot:run): API_BASE_URL = ''  (mesmo host)
 * Em produção (Vercel + backend separado): troque pela URL do backend.
 */

const API_BASE_URL = '';  // vazio = mesmo host (Spring Boot serve o front)

// ── Sessão ────────────────────────────────────────────────────

function smGetToken()   { return localStorage.getItem('safemap_token'); }
function smGetUser()    { const r = localStorage.getItem('safemap_user'); return r ? JSON.parse(r) : null; }

function smSalvarSessao(res) {
    localStorage.setItem('safemap_token', res.token);
    localStorage.setItem('safemap_user', JSON.stringify({ userId: res.userId, nome: res.nome, email: res.email }));
    // mantém compatibilidade com o isLoggedIn que o resto do código usa
    localStorage.setItem('isLoggedIn', 'true');
}

function smLimparSessao() {
    localStorage.removeItem('safemap_token');
    localStorage.removeItem('safemap_user');
    localStorage.removeItem('isLoggedIn');
}

// ── HTTP helper ───────────────────────────────────────────────

async function smFetch(endpoint, options = {}, comAuth = false) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (comAuth) {
        const token = smGetToken();
        if (!token) { smLimparSessao(); window.location.href = 'login.html'; throw new Error('Sessão expirada.'); }
        headers['Authorization'] = 'Bearer ' + token;
    }
    const res = await fetch(API_BASE_URL + endpoint, { ...options, headers });
    let data;
    try { data = await res.json(); } catch { data = null; }
    if (!res.ok) throw new Error(data?.mensagem || data?.message || 'Erro inesperado. Tente novamente.');
    return data;
}

// ── Auth ──────────────────────────────────────────────────────

async function smLogin(email, senha) {
    const data = await smFetch('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, senha }) });
    smSalvarSessao(data);
    return data;
}

async function smCadastrar(nome, email, senha, confirmarSenha, telefone) {
    const data = await smFetch('/api/auth/cadastro', {
        method: 'POST',
        body: JSON.stringify({ nome, email, senha, confirmarSenha, telefone: telefone || '' })
    });
    smSalvarSessao(data);
    return data;
}

function smLogout() {
    smLimparSessao();
    window.location.href = 'login.html';
}

// ── Ocorrências ───────────────────────────────────────────────

async function smListarOcorrencias() {
    return await smFetch('/api/ocorrencias', { method: 'GET' });
}

async function smCriarOcorrencia(payload) {
    return await smFetch('/api/ocorrencias', { method: 'POST', body: JSON.stringify(payload) }, true);
}

// ── Expõe globalmente ─────────────────────────────────────────
window.SafeMapAPI = { smLogin, smCadastrar, smLogout, smGetToken, smGetUser, smListarOcorrencias, smCriarOcorrencia };
