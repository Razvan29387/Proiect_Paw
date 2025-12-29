import React, { useState } from 'react';
import { Routes, Route, Link, useNavigate, Navigate } from 'react-router-dom';
import Login from './Login.jsx';
import Register from './Register.jsx';
import Home from './Home.jsx';
import authService from './authService.jsx';
import Cookies from 'js-cookie';

// CORECȚIE: PrivateRoute primește acum starea de autentificare ca prop
const PrivateRoute = ({ children, isLoggedIn }) => {
    // Nu mai citește cookie-ul, ci folosește direct valoarea primită
    return isLoggedIn ? children : <Navigate to="/login" />;
};

function App() {
    const navigate = useNavigate();
    // Starea de autentificare este sursa de adevăr. Inițial, este falsă.
    const [isLoggedIn, setIsLoggedIn] = useState(false);

    const handleLogout = () => {
        authService.logout().finally(() => {
            setIsLoggedIn(false);
            navigate('/login');
        });
    };

    // Funcția care va fi apelată de componenta Login la succes
    const handleLoginSuccess = () => {
        setIsLoggedIn(true);
        navigate('/home');
    };

    return (
        <div>
            <nav>
                <ul>
                    {isLoggedIn && <li><Link to="/home">Home</Link></li>}
                    {!isLoggedIn && <li><Link to="/login">Login</Link></li>}
                    {!isLoggedIn && <li><Link to="/register">Sign Up</Link></li>}
                    {isLoggedIn && <li><button onClick={handleLogout}>Logout</button></li>}
                </ul>
            </nav>
            <hr />
            <Routes>
                <Route path="/login" element={<Login onLoginSuccess={handleLoginSuccess} />} />
                <Route path="/register" element={<Register />} />

                {/* CORECȚIE: Pasăm starea 'isLoggedIn' către PrivateRoute */}
                <Route
                    path="/home"
                    element={
                        <PrivateRoute isLoggedIn={isLoggedIn}>
                            <Home />
                        </PrivateRoute>
                    }
                />
                
                {/* Ruta implicită */}
                <Route path="/" element={<Navigate to="/login" />} />
            </Routes>
        </div>
    );
}

export default App;
