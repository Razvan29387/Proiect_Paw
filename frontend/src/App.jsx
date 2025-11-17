import React, { useState, useEffect } from 'react'; // Am adăugat useEffect
import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import Home from './Home';
import Login from './Login';
import Register from './Register';
import authService from './authService';
import './App.css';

function App() {
    // MODIFICARE: Inițializăm starea ca 'null' pentru a ști când se încarcă
    const [isAuthenticated, setIsAuthenticated] = useState(null);

    // MODIFICARE: Verificăm starea sesiunii la încărcarea aplicației
    useEffect(() => {
        // Încercăm să verificăm dacă există o sesiune activă (ex. un endpoint /me)
        // Deocamdată, vom presupune că nu e logat la încărcare.
        // Într-o aplicație reală, ai face un request aici să vezi dacă e logat.
        // Pentru a evita pagina albă, setăm o valoare inițială.
        setIsAuthenticated(false); // Presupunem 'false' inițial
    }, []);

    const handleLoginSuccess = () => {
        setIsAuthenticated(true);
    };

    const handleLogout = async () => {
        try {
            await authService.logout();
        } catch (error) {
            console.error('Logout error:', error);
        } finally {
            setIsAuthenticated(false);
        }
    };

    // MODIFICARE: Afișăm "Loading..." cât timp verificăm starea (cât isAuthenticated e null)
    if (isAuthenticated === null) {
        return <div>Loading...</div>; // Previne "flash-ul" paginii de login
    }

    return (
        <BrowserRouter>
            <nav className="navbar">
                <div className="nav-links">
                    {isAuthenticated && (
                        <Link to="/home">Home</Link>
                    )}
                    {!isAuthenticated && (
                        <Link to="/login">Login</Link>
                    )}
                    {!isAuthenticated && (
                        <Link to="/register">Register</Link>
                    )}
                </div>
                {isAuthenticated && (
                    <button onClick={handleLogout} className="logout-button">
                        Logout
                    </button>
                )}
            </nav>

            <div className="content">
                <Routes>
                    {/* MODIFICARE: Ruta rădăcină "/" */}
                    <Route
                        path="/"
                        element={<Navigate to={isAuthenticated ? "/home" : "/login"} replace />}
                    />

                    <Route
                        path="/login"
                        element={!isAuthenticated ? <Login onLoginSuccess={handleLoginSuccess} /> : <Navigate to="/home" replace />}
                    />

                    <Route
                        path="/register"
                        element={!isAuthenticated ? <Register /> : <Navigate to="/home" replace />}
                    />

                    <Route
                        path="/home"
                        element={isAuthenticated ? <Home /> : <Navigate to="/login" replace />}
                    />

                    {/* MODIFICARE: Wildcard-ul '*' redirecționează înapoi la rădăcină */}
                    <Route
                        path="*"
                        element={<Navigate to="/" replace />}
                    />
                </Routes>
            </div>
        </BrowserRouter>
    );
}

export default App;