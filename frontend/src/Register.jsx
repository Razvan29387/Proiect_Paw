import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import authService from './authService.jsx';
import './Register.css';

const Register = () => {
    const [userName, setUserName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate();

    const handleRegister = async (e) => {
        e.preventDefault();
        try {
            await authService.signup('', '', email, userName, password);
            alert('Registration successful! Please login.');
            navigate('/login');
        } catch (error) {
            console.error('Registration failed:', error);
            alert('Registration failed. Try another username.');
        }
    };

    return (
        <div className="signup-container">
            <h2>Create Account</h2>
            <form className="signup-form" onSubmit={handleRegister}>
                <input 
                    type="text" 
                    placeholder="Username" 
                    value={userName} 
                    onChange={(e) => setUserName(e.target.value)} 
                    required 
                />
                <input 
                    type="email" 
                    placeholder="Email Address" 
                    value={email} 
                    onChange={(e) => setEmail(e.target.value)} 
                    required 
                />
                <input 
                    type="password" 
                    placeholder="Password" 
                    value={password} 
                    onChange={(e) => setPassword(e.target.value)} 
                    required 
                />
                <button type="submit">Sign Up</button>
            </form>
        </div>
    );
};

export default Register;
