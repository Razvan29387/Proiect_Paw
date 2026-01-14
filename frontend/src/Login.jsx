import React, { useState } from 'react';
import authService from './authService.jsx';
import './Login.css';

const Login = ({ onLoginSuccess }) => {
    const [userName, setUserName] = useState('');
    const [password, setPassword] = useState('');

    const handleLogin = async (e) => {
        e.preventDefault();
        try {
            await authService.login(userName, password);
            onLoginSuccess();
        } catch (error) {
            console.error('Login failed:', error);
            alert('Login failed. Check your credentials.');
        }
    };

    return (
        <div className="login-container">
            <h2>Welcome Back</h2>
            <form className="login-form" onSubmit={handleLogin}>
                <input 
                    type="text" 
                    placeholder="Username" 
                    value={userName} 
                    onChange={(e) => setUserName(e.target.value)} 
                    required 
                />
                <input 
                    type="password" 
                    placeholder="Password" 
                    value={password} 
                    onChange={(e) => setPassword(e.target.value)} 
                    required 
                />
                <button type="submit">Log In</button>
            </form>
        </div>
    );
};

export default Login;
