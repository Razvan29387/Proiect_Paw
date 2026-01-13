import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom'; // Pentru a redirecționa după înregistrare
import authService from './authService.jsx';
import './Login.css'; // Poți refolosi același CSS

const Register = () => {
    const [userName, setUserName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate(); // Hook pentru navigare

    const handleRegister = async (e) => {
        e.preventDefault();
        try {
            // CORECȚIE: Folosim 'signup' și ordinea corectă a argumentelor
            // Lăsăm nume/prenume goale pentru moment, deoarece nu sunt în formular
            await authService.signup('', '', email, userName, password);
            alert('Registration successful! Please login.');
            navigate('/login'); // Redirecționează către pagina de login
        } catch (error) {
            console.error('Registration failed:', error);
            alert('Registration failed. Try another username.');
        }
    };

    return (
        <div className="login-container">
            <h2>Register</h2>
            <form className="login-form" onSubmit={handleRegister}>
                <input type="text" placeholder="Username" value={userName} onChange={(e) => setUserName(e.target.value)} required />
                <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                <input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} required />
                <button type="submit">Register</button>
            </form>
        </div>
    );
};

export default Register;
