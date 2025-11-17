import React, { useState } from 'react';
import authService from './authService.jsx';
import './Login.css';

// Componenta primește acum o funcție ca prop
const Login = ({ onLoginSuccess }) => {
    const [userName, setUserName] = useState('');
    const [password, setPassword] = useState('');

    const handleLogin = async (e) => {
        e.preventDefault();
        try {
            await authService.login(userName, password);
            // Apelează funcția din App.jsx pentru a notifica succesul
            onLoginSuccess();
        } catch (error) {
            console.error('Login failed:', error);
            alert('Login failed. Check your credentials.');
        }
    };

    return (
        <div className="login-container">
            <h2>Login</h2>
            <form className="login-form" onSubmit={handleLogin}>
                <input type="text" placeholder="Username" value={userName} onChange={(e) => setUserName(e.target.value)} required />
                <input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} required />
                <button type="submit">Login</button>
            </form>
        </div>
    );
};

export default Login;
