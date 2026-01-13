import axios from 'axios';

// CONFIGURARE GLOBALĂ ESENȚIALĂ
// Spune lui axios să trimită cookie-uri la fiecare cerere cross-origin.
axios.defaults.withCredentials = true;

const API_URL = 'http://localhost:8080/api/v1/auth/';

const signup = (name, surname, email, userName, password) => {
    return axios.post(API_URL + 'signup', {
        name,
        surname,
        email,
        userName,
        password,
    });
};

// Funcția de login doar trimite cererea și returnează promisiunea
const login = (userName, password) => {
    return axios.post(API_URL + 'signin', {
        userName,
        password,
    });
};

// Funcția de logout apelează endpoint-ul din backend
const logout = () => {
    return axios.post(API_URL + 'logout');
};

const authService = {
    signup,
    login,
    logout,
};

export default authService;
