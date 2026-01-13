// api.js

import axios from 'axios';

// Creăm o singură instanță 'api' pentru întreaga aplicație
const api = axios.create({
    // Setează URL-ul de bază pentru TOATE cererile
    baseURL: 'http://localhost:8080/api/v1',

    // Setează 'withCredentials' AICI, o singură dată
    withCredentials: true
});

export default api;