import api from './api.jsx';

const signup = (name, surname, email, userName, password) => {
    return api.post('/auth/signup', {
        name,
        surname,
        email,
        userName,
        password,
    });
};

const login = (userName, password) => {
    return api.post('/auth/signin', {
        userName,
        password,
    });
};

const logout = () => {
    return api.post('/auth/logout');
};

const authService = {
    signup,
    login,
    logout,
};

export default authService;
